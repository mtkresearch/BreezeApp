#include "mtk_llm.h"

#include "backend/backend.h"
#include "common/dump.h"
#include "common/logging.h"
#include "common/thread_pool.h"
#include "executor/allocator.h"
#include "executor/executor_factory.h"
#include "executor/shared_weights.h"
#include "llm_helper/include/rotary_embedding.h"
#include "llm_helper/include/token_embedding.h"
#include "llm_helper/include/utils.h"
#include "tokenizer/tokenizer.h"

#include <regex>
#include <thread>
#include <unordered_set>

#define LLM_API __attribute__((visibility("default")))

#ifdef USE_USDK_BACKEND
static constexpr bool kUseUsdkBackend = true;
#else
static constexpr bool kUseUsdkBackend = false;
#endif

#ifdef DISABLE_MULTITHREAD_MODEL_LOAD
static constexpr bool kUseMultiThreadedLoad = false;
#else
static constexpr bool kUseMultiThreadedLoad = true;
#endif

#ifdef DISABLE_INFERENCE_PIPELINING
static constexpr bool kUseInferencePipelining = false;
#else
static constexpr bool kUseInferencePipelining = true;
#endif

using LlmDlaExecutor = GetExecutorClass(Llm);
using LlmMedusaDlaExecutor = GetExecutorClass(LlmMedusa);
using LmHeadDlaExecutor = GetExecutorClass(Neuron);
using MedusaHeadsDlaExecutor = GetExecutorClass(Neuron);

using TokenType = mtk::Tokenizer::TokenType;

using mtk::llm_helper::RotaryEmbeddingMasterLut;
using mtk::llm_helper::TokenEmbeddingLut;

using SharedWeightsHandleImpl = mtk::SharedWeightsHandle;

struct LlmRuntime {
    std::vector<mtk::Executor*> dlaExecutors;
    mtk::Executor* dlaLmHeadExecutor = nullptr;
    mtk::Executor* dlaMedusaHeadsExecutor = nullptr;
    TokenEmbeddingLut* tokenEmbLut = nullptr;
    RotaryEmbeddingMasterLut* rotEmbMasterLut = nullptr;
    LlmRuntimeOptions options;
    const SharedWeightsHandleImpl* sharedWeightsHandle = nullptr; // Will be used if not preloaded
};

// Helper functions
inline size_t getNumChunks(const LlmRuntimeOptions& runtimeOptions) {
    std::unordered_set<size_t> numChunkSet;
    for (const auto& [_, files] : runtimeOptions.dlaFiles) {
        numChunkSet.insert(files.size());
    }
    CHECK_LE(numChunkSet.size(), 1) << "Inconsistent number of dla chunks found in runtimeOptions.";
    return numChunkSet.empty() ? 0 : *numChunkSet.cbegin();
}

void LLM_API mtk_llm_preload_shared_weights(SharedWeightsHandle** sharedWeightsHandle,
                                            const LlmRuntimeOptions& runtimeOptions) {
    // Error checking
    const size_t numChunk = getNumChunks(runtimeOptions);
    const auto numSwFiles = runtimeOptions.sharedWeightsFiles.size();

    if (numSwFiles > 0 && numChunk != numSwFiles) {
        LOG(ERROR) << "Mismatch chunk count!";
        *sharedWeightsHandle = nullptr;
        return;
    }

    auto swHandle = new SharedWeightsHandleImpl(runtimeOptions.sharedWeightsFiles, numChunk);
    swHandle->preload();

    *sharedWeightsHandle = reinterpret_cast<SharedWeightsHandle*>(swHandle);
}

void LLM_API mtk_llm_free_preloaded_shared_weights(SharedWeightsHandle* sharedWeightsHandle) {
    delete reinterpret_cast<SharedWeightsHandleImpl*>(sharedWeightsHandle);
}

bool LLM_API mtk_llm_init(void** runtime, const LlmModelOptions& modelOptions,
                          const LlmRuntimeOptions& runtimeOptions,
                          const SharedWeightsHandle* preloadedSharedWeights) {
    if constexpr (kUseUsdkBackend) {
        LOG(DEBUG) << "Using NeuronUsdk (NeuronAdapter)";
    } else {
        LOG(DEBUG) << "Using Neuron Runtime";
        if (!mtk::backend::neuron_api::load_library()) {
            LOG(ERROR) << "Failed to initialize runtime library.";
            *runtime = nullptr;
            return false;
        }
    }

    const size_t numChunk = getNumChunks(runtimeOptions);

    // External cache loading shared weights loading
    const auto numSharedWeightsFiles = runtimeOptions.sharedWeightsFiles.size();
    const auto numCacheFiles = runtimeOptions.cacheFiles.size();
    if ((numCacheFiles > 0 && numChunk != numCacheFiles)
        || (numSharedWeightsFiles > 0 && numSharedWeightsFiles % numChunk != 0)) {
        // Mismatch chunk count
        LOG(ERROR) << "Mismatch chunk count!";
        *runtime = nullptr;
        return false;
    }

    // Create llm runtime
    LlmRuntime* llmRuntime = new LlmRuntime;
    llmRuntime->options = runtimeOptions;

    // Use preloaded shared weights if available, otherwise create one without preload.
    const SharedWeightsHandleImpl* sharedWeightsHandle = nullptr;
    if (preloadedSharedWeights != nullptr) {
        sharedWeightsHandle =
            reinterpret_cast<const SharedWeightsHandleImpl*>(preloadedSharedWeights);
    } else if (numSharedWeightsFiles > 0) {
        sharedWeightsHandle =
            new SharedWeightsHandleImpl(runtimeOptions.sharedWeightsFiles, numChunk);
        // Store the handle if it's created and owned by this llmRuntime instance
        llmRuntime->sharedWeightsHandle = sharedWeightsHandle;
    }

    // Per-chunk file getter helpers
    auto getCacheFile = [&](const size_t chunkIdx) -> FileSource {
        if (numCacheFiles > 0)
            return runtimeOptions.cacheFiles[chunkIdx];
        return {};
    };
    auto getSharedWeights = [&](const size_t chunkIdx) -> mtk::SharedWeights {
        if (sharedWeightsHandle == nullptr) {
            return {};
        }
        return sharedWeightsHandle->getSharedWeights(chunkIdx);
    };
    auto getLoraWeightsfileMap = [&](const size_t chunkIdx) {
        std::unordered_map<LoraKey, FileSource> loraWeightsFileMap;
        if (!runtimeOptions.loraWeightsFiles.empty()) {
            for (const auto& [loraKey, loraChunkFiles] : runtimeOptions.loraWeightsFiles) {
                CHECK_EQ(loraChunkFiles.size(), numChunk)
                    << "Invalid LoRA input weights chunk count for '" << loraKey << "'";
                const auto& loraChunkFile = loraChunkFiles[chunkIdx];
                loraWeightsFileMap.emplace(loraKey, loraChunkFile);
            }
        }
        return loraWeightsFileMap;
    };

    // Get number of caches
    const size_t numCache = 2 * modelOptions.numLayer / numChunk; // Split cache
    CHECK_EQ(modelOptions.numLayer % numChunk, 0)
        << "Requires each DLA chunk to contain equal number of layers.";
    LOG(DEBUG) << "Number of cache per dla: " << numCache;

    // Initialize and prepare rotary embedding master lookup-table
    const size_t rotEmbDim = modelOptions.hiddenSize / modelOptions.numHead;
    llmRuntime->rotEmbMasterLut =
        new RotaryEmbeddingMasterLut(modelOptions.rotEmbType, modelOptions.maxTokenLength,
                                     rotEmbDim, modelOptions.rotEmbBase, modelOptions.ntkScale);
    llmRuntime->rotEmbMasterLut->generate();

    constexpr size_t numRotEmbInputs = 1;

    const mtk::ExecutorType llmExecType =
        (modelOptions.numMedusaHeads > 0) ? mtk::ExecutorType::LlmMedusa : mtk::ExecutorType::Llm;
    mtk::ExecutorFactory llmExecFactory(llmExecType);

    auto parseModelConfig = [&](const auto& modelConfig) -> std::tuple<size_t, size_t, size_t> {
        const std::regex tokenSizePat("([0-9]+)[tT]");
        const std::regex cacheSizePat("([0-9]+)[cC]");
        size_t tokenSize = 0, cacheSize = 0;
        std::smatch match;

        // Parse token size, and must be provided.
        if (std::regex_search(modelConfig, match, tokenSizePat))
            tokenSize = std::stoi(match[0].str());
        else
            LOG(FATAL) << "Token size is not provided in 'dlaPaths' model config.";

        // Parse cache size, will take from modelOptions if not provided.
        if (std::regex_search(modelConfig, match, cacheSizePat))
            cacheSize = std::stoi(match[0].str());
        else
            cacheSize = modelOptions.cacheSize;

        // Only support literal batch size for 1t gen model.
        // For folded batch gen mode, batchSize value is set to 1, and tokenSize is interpreted as
        // "effective batch size" during inference.
        const size_t batchSize = (tokenSize == 1) ? modelOptions.genModelBatchSize : 1;
        return {batchSize, tokenSize, cacheSize};
    };

    for (int chunkIdx = 0; chunkIdx < numChunk; ++chunkIdx) {
        std::vector<mtk::LlmExecutor::RuntimeInfo> runtimeInfos;
        for (const auto& [modelConfig, files] : runtimeOptions.dlaFiles) {
            DCHECK_GT(files.size(), chunkIdx);
            const auto& file = files[chunkIdx];
            const auto [batchSize, tokenSize, cacheSize] = parseModelConfig(modelConfig);
            runtimeInfos.push_back({file, batchSize, tokenSize, cacheSize});
            LOG(DEBUG) << "Added runtimeInfo" << "(batchSize=" << batchSize
                       << ", tokenSize=" << tokenSize << ", cacheSize=" << cacheSize
                       << "): " << file.getName();
        }

        const auto& sharedWeights = getSharedWeights(chunkIdx);
        LOG(DEBUG) << "Loading DLA " << chunkIdx;

        auto dlaExec = llmExecFactory.create(
            runtimeInfos, sharedWeights, modelOptions.maxTokenLength, modelOptions.cacheSize,
            numCache, modelOptions.cacheType, modelOptions.maskType, llmRuntime->rotEmbMasterLut,
            numRotEmbInputs, getLoraWeightsfileMap(chunkIdx), runtimeOptions.initWithLoraKey,
            runtimeOptions.loraInputCount, getCacheFile(chunkIdx), runtimeOptions.startTokenIndex);
        llmRuntime->dlaExecutors.push_back(dlaExec);
    }

    mtk::ExecutorFactory neuronExecFactory(mtk::ExecutorType::Neuron);

    if (!runtimeOptions.dlaLmHeadFile.empty()) {
        LOG(DEBUG) << "Loading and initializing Executor for LM Head.";
        llmRuntime->dlaLmHeadExecutor = neuronExecFactory.create(runtimeOptions.dlaLmHeadFile);
        llmRuntime->dlaLmHeadExecutor->setNumInputs(1);
        llmRuntime->dlaLmHeadExecutor->setNumOutputs(1);
        llmRuntime->dlaLmHeadExecutor->initialize();
        llmRuntime->dlaLmHeadExecutor->registerRuntimeIO();
    }

    if (!runtimeOptions.dlaMedusaHeadsFile.empty()) {
        LOG(DEBUG) << "Loading and initializing Executor for Medusa Heads.";
        llmRuntime->dlaMedusaHeadsExecutor =
            neuronExecFactory.create(runtimeOptions.dlaMedusaHeadsFile);
        llmRuntime->dlaLmHeadExecutor->setNumInputs(1);
        llmRuntime->dlaLmHeadExecutor->setNumOutputs(1);
        llmRuntime->dlaMedusaHeadsExecutor->initialize();
        llmRuntime->dlaMedusaHeadsExecutor->registerRuntimeIO();
    }

    // Use multi-threading to speedup model loading
    std::vector<std::thread> threadPool;

    auto initExecutor = [&](const auto dlaExec) {
        if constexpr (kUseMultiThreadedLoad)
            threadPool.emplace_back(&mtk::Executor::initialize, dlaExec);
        else
            dlaExec->initialize();
    };

    auto initTokenEmbLut = [&] {
        // NOTE: Token embedding lookup-table type must match the model input type
        llmRuntime->tokenEmbLut = new TokenEmbeddingLut(
            runtimeOptions.tokenEmbFile, modelOptions.modelInputType, modelOptions.hiddenSize);
        LOG(DEBUG) << "Initialized input token embedding lookup table.";
    };

    for (size_t chunkIdx = 0; chunkIdx < numChunk; chunkIdx++) {
        // Initialize after reserving the input buffer so that the buffer allocator doesn't need to
        // allocate for inputs that are using an existing buffer created elsewhere.
        auto dlaExec = llmRuntime->dlaExecutors[chunkIdx];
        LOG(DEBUG) << "Initializing DLA " << chunkIdx;
        if (chunkIdx > 0)
            dlaExec->reserveInputBuffer(); // Prevent allocation of buffer for input 0
        initExecutor(dlaExec);
    }
    threadPool.emplace_back(initTokenEmbLut);

    // Wait for model to finish loading
    for (auto& thread : threadPool) {
        thread.join();
    }
    LOG(DEBUG) << "Done initializing DLAs";

    // Ensure all shared weights have been fully preloaded (if any)
    if (sharedWeightsHandle)
        sharedWeightsHandle->wait();

    // Chain the IO between the runtime chunks:
    // InputToken -> [EmbeddingLut -> DlaChunk1 -> DlaChunk2 -> ... -> DlaChunkN]-> Output
    auto getPrevChunkOutput = [&](const int chunkIdx) -> const mtk::IOBuffer& {
        DCHECK_GE(chunkIdx, 1);
        return llmRuntime->dlaExecutors[chunkIdx - 1]->getOutput();
    };

    for (size_t chunkIdx = 0; chunkIdx < numChunk; chunkIdx++) {
        // Initialize after setModelInput so that the buffer allocator doesn't need to allocate for
        // inputs that are using an existing buffer.
        auto dlaExec = llmRuntime->dlaExecutors[chunkIdx];
        if (chunkIdx > 0)
            dlaExec->setModelInput(getPrevChunkOutput(chunkIdx));
        dlaExec->updateModelIO(); // Ensure IO sizes are correct, esp when using prev chunk buffer
        dlaExec->registerRuntimeIO(); // Attach allocated buffers to model IO
    }
    // Link first chunk emb input to token emb lut output
    const auto& tokenEmbInput = llmRuntime->dlaExecutors.front()->getInput();
    llmRuntime->tokenEmbLut->setOutput(tokenEmbInput.buffer, tokenEmbInput.sizeBytes);

    LOG(DEBUG) << "Done model chunks IO chaining";

    *runtime = llmRuntime;
    return true;
}

void LLM_API mtk_llm_swap_model(void* runtime, const size_t tokenSize, const size_t cacheSize) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    const auto numDlaChunk = llmRuntime->dlaExecutors.size();

    // Use multi-threading to speedup model swapping (if necessary)
    std::vector<std::thread> threadPool;

    auto swapModel = [&](const auto chunkIdx) {
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors[chunkIdx]);
        if (!llmDlaExec->hotSwapModel(tokenSize, cacheSize))
            LOG(ERROR) << "Hot swapping failed on chunk " << chunkIdx;
    };

    for (size_t chunkIdx = 0; chunkIdx < numDlaChunk; chunkIdx++) {
        if constexpr (!kUseMultiThreadedLoad)
            swapModel(chunkIdx);
        else
            threadPool.emplace_back(swapModel, chunkIdx);
    }

    // Wait for model swapping threads to finish
    for (auto& thread : threadPool) {
        thread.join();
    }
}

size_t LLM_API mtk_llm_advance_cache_size(void* runtime) {
    // 1. Find next larger cache size
    // 2. Call mtk_llm_swap_model(curTokenSize, nextCacheSize)
    // 3. Return new cache size
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    const auto firstExecutor = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors.front());
    const auto curTokenSize = firstExecutor->getModelTokenSize();
    const auto curCacheSize = firstExecutor->getCacheLength();
    const auto nextLargerCacheSize = firstExecutor->getNextAvailCacheSize();
    if (nextLargerCacheSize > curCacheSize) {
        Timer cacheAdvanceTimer;
        cacheAdvanceTimer.start();
        mtk_llm_swap_model(runtime, curTokenSize, nextLargerCacheSize);
        LOG(DEBUG) << "Advancing " << curTokenSize << "t model cache size from " << curCacheSize
                   << " to " << nextLargerCacheSize << " took " << cacheAdvanceTimer.reset() * 1000
                   << " ms";
    }
    return nextLargerCacheSize;
}

void LLM_API mtk_llm_release(void* runtime) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    for (auto dlaExec : llmRuntime->dlaExecutors) {
        delete dlaExec;
    };
    llmRuntime->dlaExecutors.clear();

    delete llmRuntime->dlaLmHeadExecutor;
    delete llmRuntime->dlaMedusaHeadsExecutor;
    delete llmRuntime->tokenEmbLut;
    delete llmRuntime->rotEmbMasterLut;
    delete llmRuntime->sharedWeightsHandle;
    delete llmRuntime;
}

void LLM_API mtk_llm_set_medusa_tree_attn(void* runtime, const std::vector<std::vector<int>>& mask,
                                          const std::vector<size_t>& positions) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    for (auto dlaExec : llmRuntime->dlaExecutors) {
        static_cast<LlmMedusaDlaExecutor*>(dlaExec)->setMedusaTreeAttn(mask, positions);
    }
}

void LLM_API mtk_llm_use_prompt_as_batch_gen(void* runtime) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    for (auto dlaExec : llmRuntime->dlaExecutors) {
        static_cast<LlmDlaExecutor*>(dlaExec)->enterFoldedGenBatchMode();
    }
}

void* LLM_API mtk_llm_inference_once(void* runtime, const std::vector<TokenType>& inputTokens,
                                     const LogitsKind outputKind) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    const auto firstExecutor = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors.front());

    const size_t effectiveBatchSize = [&] {
        if (firstExecutor->isFoldedGenBatchMode())
            return firstExecutor->getModelTokenSize();
        return firstExecutor->getBatchSize();
    }();

    // Duplicate (broadcast) to all batches
    const auto batchInputTokens = std::vector(effectiveBatchSize, inputTokens);

    // Return the first batch logits
    const auto batchLogits = mtk_llm_inference_batch(runtime, batchInputTokens, outputKind);
    DCHECK_GE(batchLogits.size(), 1);
    return batchLogits[0];
}

Batched<void*>
    LLM_API mtk_llm_inference_batch(void* runtime,
                                    const Batched<std::vector<TokenType>>& batchInputTokens,
                                    const LogitsKind outputKind) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    const auto firstExecutor = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors.front());
    const auto currentTokenIndex = firstExecutor->getTokenIndex();
    const auto modelTokenSize = firstExecutor->getModelTokenSize();
    const auto modelBatchSize = firstExecutor->getBatchSize();
    const auto numTotalTokens = modelTokenSize * modelBatchSize;

    // Folded gen batch mode
    const bool isFoldedGenBatchMode = firstExecutor->isFoldedGenBatchMode();
    const size_t effectiveBatchSize = isFoldedGenBatchMode ? modelTokenSize : modelBatchSize;
    const size_t effectiveTokenSize = isFoldedGenBatchMode ? 1 : modelTokenSize;

    auto allSameSize = [](const auto& vec) {
        return mtk::llm_helper::allSame(vec, [](const auto& item) { return item.size(); });
    };

    // Error checking
    CHECK_EQ(batchInputTokens.size(), effectiveBatchSize)
        << "Provided batch size does not match model batch size.";
    CHECK(allSameSize(batchInputTokens)) << "All batches should contain the same number of tokens.";

    const auto inputTokenSize = batchInputTokens[0].size(); // Per batch
    CHECK_LE(inputTokenSize, effectiveTokenSize)
        << "The required per-batch input token length (" << inputTokenSize << ") exceeds what the "
        << "model can take in (" << effectiveTokenSize << ")";

    // Flatten batch input tokens
    std::vector<TokenType> flattenInputTokens;

    const bool isLeftPadAllowed = (currentTokenIndex == 0);
    const size_t padSize = effectiveTokenSize - inputTokenSize;
    constexpr TokenType padToken = 0; // By right any token should work.

    for (auto inputTokens : batchInputTokens) {
        // Use left-padding if possible as it has lower overhead than right-padding.
        // Right-padding involves cache shifting (for non-ring buffer) which incurs extra overhead.
        if (padSize > 0) {
            if (isLeftPadAllowed) {
                // Pad left since the cache is fresh new.
                inputTokens.insert(inputTokens.begin(), padSize, padToken);
                LOG(DEBUG) << "Padding left by " << padSize;
            } else {
                // Pad right since left side of cache is occupied either by loaded cache or previous
                // inference pass.
                inputTokens.insert(inputTokens.end(), padSize, padToken);
                LOG(DEBUG) << "Padding right by " << padSize;
            }
        }
        CHECK_EQ(effectiveTokenSize, inputTokens.size());

        // Append inputTokens to flattenInputTokens
        flattenInputTokens.insert(flattenInputTokens.end(), inputTokens.begin(), inputTokens.end());
    }

    // Set padding
    for (auto dlaExec : llmRuntime->dlaExecutors) {
        auto llmExecutor = static_cast<LlmDlaExecutor*>(dlaExec);
        if (isLeftPadAllowed)
            llmExecutor->setLeftPadding(padSize);
        else
            llmExecutor->setRightPadding(padSize);
    }

    static size_t inferenceStep = 0;
    SET_DUMP_INDEX(inferenceStep++);

    // Try advance to a larger cache size if the current input tokens will overload the cache
    const auto curCacheSize = firstExecutor->getCacheLength();
    const auto minRequiredCacheSize = currentTokenIndex + modelTokenSize;
    if (curCacheSize < minRequiredCacheSize) {
        const auto newCacheSize = mtk_llm_advance_cache_size(runtime);
        if (newCacheSize > curCacheSize) {
            LOG(DEBUG) << "Advanced cache size from " << curCacheSize << " to " << newCacheSize;
        } else {
            LOG(WARN) << "Failed to advance to a larger cache size. Current cache size "
                      << "(" << curCacheSize << ") is insufficient for the current inference step.";
        }
    }

    llmRuntime->tokenEmbLut->lookupEmbedding(flattenInputTokens);
    LOG(DEBUG) << "Emb Lut output buf[0] = "
               << reinterpret_cast<const int16_t*>(firstExecutor->getInputBuffer())[0];

    const auto numChunk = llmRuntime->dlaExecutors.size();

    auto getLlmDlaExec = [&](const int chunkIdx) -> LlmDlaExecutor* {
        if (chunkIdx < 0 || chunkIdx >= numChunk)
            return nullptr;
        return static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors[chunkIdx]);
    };

    BasicThreadPool inferenceThreadPool;

    auto dispatchThread = [&](const auto llmDlaExec, void (mtk::Executor::*func)()) {
        if (llmDlaExec == nullptr)
            return;
        if constexpr (kUseInferencePipelining)
            inferenceThreadPool.push(func, llmDlaExec);
        else
            std::invoke(func, llmDlaExec);
    };

    for (size_t chunkIdx = 0; chunkIdx < numChunk; chunkIdx++) {
        auto llmDlaExec = getLlmDlaExec(chunkIdx);
        auto prevLlmDlaExec = getLlmDlaExec(chunkIdx - 1);
        auto nextLlmDlaExec = getLlmDlaExec(chunkIdx + 1);

        // First chunk prologue cannot be hidden
        if (chunkIdx == 0) {
            llmDlaExec->runInferencePrologue();
        }

        dispatchThread(prevLlmDlaExec, &mtk::Executor::runInferenceEpilogue);
        dispatchThread(nextLlmDlaExec, &mtk::Executor::runInferencePrologue);
        llmDlaExec->runInference();

        inferenceThreadPool.joinAll();

        // Last chunk epilogue cannot be hidden
        if (chunkIdx == numChunk - 1) {
            llmDlaExec->runInferenceEpilogue();
        }

        SET_DUMP_CHUNK_INDEX(chunkIdx);

        // Dump chunk output
        const auto chunkOutputBuffer = llmDlaExec->getOutputBuffer();
        const auto chunkOutputSize = llmDlaExec->getModelOutputSizeBytes();
        DUMP(CHUNK_OUT).fromBinary("output", chunkOutputBuffer, chunkOutputSize);

        // Dump chunk cache outputs
        if (SHOULD_DUMP(CACHE)) {
            std::vector<char*> cacheBuffers;
            size_t sizePerCache;
            llmDlaExec->getCacheBuffersWithSize(cacheBuffers, sizePerCache);
            for (size_t i = 0; i < cacheBuffers.size(); i++) {
                DUMP(CACHE).fromBinary("cache_" + std::to_string(i), cacheBuffers[i], sizePerCache);
            }
        }
    }

    const size_t rightPadSize = !isLeftPadAllowed * padSize;

    auto getLogitsBuffer = [=](const auto executor, const auto execNumTokens) {
        auto logitsBuffer = reinterpret_cast<char*>(executor->getOutputBuffer());
        const size_t logitsSizePerToken = executor->getModelOutputSizeBytes() / execNumTokens;
        const size_t logitsSizePerBatch = logitsSizePerToken * effectiveTokenSize;
        DCHECK_GE(effectiveTokenSize, rightPadSize);
        // Use min() to handle the case where lmHead size < hiddenState size
        const auto numValidTokensPerBatch =
            std::min(effectiveTokenSize - rightPadSize, execNumTokens);
        DCHECK_GE(numValidTokensPerBatch, 1);
        size_t logitsOffset = 0;
        if (outputKind == LogitsKind::LAST) {
            logitsOffset = logitsSizePerToken * (numValidTokensPerBatch - 1);
            DCHECK_LE(logitsOffset, logitsSizePerBatch);
        }
        Batched<void*> logits(effectiveBatchSize);
        for (size_t batch = 0; batch < effectiveBatchSize; batch++) {
            auto curLogitsBuffer = logitsBuffer + logitsSizePerBatch * batch;
            logits[batch] = curLogitsBuffer + logitsOffset;
        }
        return logits;
    };

    // Clipped subtraction between unsigned values
    auto max0Subtract = [](const auto lhs, const auto rhs) -> decltype(lhs) {
        if (lhs < rhs)
            return 0;
        return lhs - rhs;
    };

    const auto finalExecutor = llmRuntime->dlaExecutors.back();
    const auto lmHeadExecutor = llmRuntime->dlaLmHeadExecutor;

    if (!lmHeadExecutor) {
        // No separated LM head, return the logits from the final executor directly.
        return getLogitsBuffer(finalExecutor, numTotalTokens);
    }

    if (outputKind == LogitsKind::NONE) {
        // Logits is not required, so no need to run lmHead.
        return std::vector<void*>(effectiveBatchSize, nullptr);
    }

    // Execute the LM head on the hidden state generated from the last chunk of decoder layers.
    const auto hiddenStateSize = finalExecutor->getModelOutputSizeBytes();
    const auto lmHeadInputSize = lmHeadExecutor->getModelInputSizeBytes();
    const auto perTokenHiddenStateSize = hiddenStateSize / numTotalTokens;
    const auto lmHeadNumTotalTokens = lmHeadInputSize / perTokenHiddenStateSize;

    // Ensure LM Head input token size is large enough to process all batches (during batch mode)
    if (effectiveBatchSize > 1) {
        CHECK_LE(hiddenStateSize, lmHeadInputSize)
            << "Batch model requires LM Head with sufficient size: Batch model output hidden size "
            << "(" << hiddenStateSize << ") > LM Head input size (" << lmHeadInputSize << ")";
    }

    // The input hidden state to LM Head is left-flushed so only the last N non-padded tokens are
    // processed, where N is the LM Head token size.
    const size_t hiddenStateTokenOffset =
        max0Subtract(numTotalTokens, lmHeadNumTotalTokens + rightPadSize);

    const size_t hiddenStateOffset = hiddenStateTokenOffset * perTokenHiddenStateSize;
    DCHECK_LE(hiddenStateOffset, hiddenStateSize);
    auto hiddenStateBuffer = reinterpret_cast<char*>(finalExecutor->getOutputBuffer());
    lmHeadExecutor->runInference(hiddenStateBuffer + hiddenStateOffset, lmHeadInputSize);

    // Return logits from LM head output
    if (outputKind == LogitsKind::FULL) {
        // If the logits of all the input tokens are expected, the token size of LM-Head chunk
        // must be large enough for the total token size of the currently used chunks.
        DCHECK_LE(numTotalTokens, lmHeadNumTotalTokens);
    }
    return getLogitsBuffer(lmHeadExecutor, lmHeadNumTotalTokens);
}

std::tuple<void*, void*> // logits, last_hidden_states
    LLM_API mtk_llm_inference_once_return_hidden(void* runtime,
                                                 const std::vector<TokenType>& inputTokens,
                                                 const LogitsKind outputKind) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    if (llmRuntime->dlaLmHeadExecutor == nullptr) {
        LOG(WARN) << "Separated LM Head is not used, so the last hidden states is equivalent to the"
                     " full logits.";
    }
    auto logits = mtk_llm_inference_once(runtime, inputTokens, outputKind);
    const auto finalExecutor = llmRuntime->dlaExecutors.back();
    auto hiddenStateBuffer = reinterpret_cast<char*>(finalExecutor->getOutputBuffer());
    return {logits, hiddenStateBuffer};
}

// Return medusa logits
void* LLM_API neuron_medusa_heads_inference_once(void* runtime, void* hiddenState) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    // Error checking
    if (llmRuntime->dlaMedusaHeadsExecutor == nullptr) {
        LOG(FATAL) << "Medusa Heads is necessary for Medusa inference.";
    }

    const auto medusaExecutor =
        static_cast<MedusaHeadsDlaExecutor*>(llmRuntime->dlaMedusaHeadsExecutor);

    medusaExecutor->runInference(
        reinterpret_cast<char*>(hiddenState), medusaExecutor->getModelInputSizeBytes());
    auto logitsBuffer = medusaExecutor->getOutputBuffer();

    return reinterpret_cast<char*>(logitsBuffer);
}

void LLM_API mtk_llm_apply_lora(void* runtime, const LoraKey& loraKey) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    for (auto dlaExec : llmRuntime->dlaExecutors) {
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(dlaExec);
        llmDlaExec->applyLoraWeights(loraKey);
    }
}

void LLM_API mtk_llm_apply_lora_from_buffer(void* runtime,
                                            const std::vector<const char*>& loraWeightBuffers,
                                            const std::vector<size_t>& sizes) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);

    const auto& loraInputCount = llmRuntime->options.loraInputCount; // Per chunk
    const auto& chunkCount = llmRuntime->dlaExecutors.size();

    // Verify arguments
    CHECK_EQ(loraWeightBuffers.size(), sizes.size());
    CHECK_EQ(chunkCount * loraInputCount, loraWeightBuffers.size())
        << "The provided number of LoRA weights buffers does not match the total number of "
           "LoRA inputs";

    auto getSubsetForChunk = [&](const size_t chunkIdx, const auto& vec) {
        const size_t start = chunkIdx * loraInputCount;
        const size_t end = start + loraInputCount;
        return std::vector(vec.begin() + start, vec.begin() + end);
    };

    // Chunk the LoRA weight buffers and feed into each DLA chunk.
    for (size_t chunkIdx = 0; chunkIdx < chunkCount; chunkIdx++) {
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors[chunkIdx]);
        const auto& loraWeightForChunk = getSubsetForChunk(chunkIdx, loraWeightBuffers);
        const auto& sizesForChunk = getSubsetForChunk(chunkIdx, sizes);
        llmDlaExec->applyLoraWeights(loraWeightForChunk, sizesForChunk);
    }
}

void LLM_API mtk_llm_remove_lora(void* runtime) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    for (auto dlaExec : llmRuntime->dlaExecutors) {
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(dlaExec);
        llmDlaExec->removeLoraWeights();
    }
}

void LLM_API mtk_llm_get_caches(void* runtime, std::vector<std::vector<char*>>& caches,
                                size_t& byteSizePerCache) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    for (auto dlaExec : llmRuntime->dlaExecutors) {
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(dlaExec);
        std::vector<char*> chunkCaches;
        llmDlaExec->getCacheBuffersWithSize(chunkCaches, byteSizePerCache);
        caches.emplace_back(chunkCaches);
    }
}

void LLM_API mtk_llm_reset(void* runtime, const bool resetCache) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    for (auto dlaExec : llmRuntime->dlaExecutors) {
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(dlaExec);
        if (resetCache) {
            // Reset cache and token index, resetTokenIndex() will be called
            llmDlaExec->initCache();
        } else {
            // Reset token index without resetting cache
            llmDlaExec->resetTokenIndex();
        }
    }
}

size_t LLM_API mtk_llm_get_per_token_logits_size(void* runtime) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    const auto finalExecutor = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors.back());
    const auto modelBatchSize = finalExecutor->getBatchSize();
    const auto modelTokenSize = finalExecutor->getModelTokenSize();
    const auto numTotalTokens = modelTokenSize * modelBatchSize;
    if (llmRuntime->dlaLmHeadExecutor == nullptr) {
        const auto logitsSize = finalExecutor->getModelOutputSizeBytes();
        return logitsSize / numTotalTokens;
    } else {
        const auto perTokenHiddenStateSize =
            finalExecutor->getModelOutputSizeBytes() / numTotalTokens;
        const auto lmHeadExecutor = static_cast<LmHeadDlaExecutor*>(llmRuntime->dlaLmHeadExecutor);
        const auto lmHeadNumTotalTokens =
            lmHeadExecutor->getModelInputSizeBytes() / perTokenHiddenStateSize;
        const auto logitsSize = lmHeadExecutor->getModelOutputSizeBytes();
        return logitsSize / lmHeadNumTotalTokens;
    }
}

size_t LLM_API mtk_llm_get_per_token_hidden_states_size(void* runtime) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    // Error checking
    if (llmRuntime->dlaLmHeadExecutor == nullptr) {
        LOG(FATAL) << "Separated LM Head is necessary for calculating the size of hidden states.";
    }
    const auto finalExecutor = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors.back());
    const auto modelTokenSize = finalExecutor->getModelTokenSize();
    const auto modelBatchSize = finalExecutor->getBatchSize();
    const auto hiddenStateSize = finalExecutor->getModelOutputSizeBytes();
    return hiddenStateSize / modelTokenSize / modelBatchSize;
}

size_t LLM_API mtk_llm_get_token_index(void* runtime) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    const auto firstExecutor = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors.front());
    return firstExecutor->getTokenIndex();
}

void LLM_API mtk_llm_rollback(void* runtime, const size_t rollbackCount) {
    if (rollbackCount == 0)
        return;
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    const auto finalExecutor = static_cast<LlmDlaExecutor*>(llmRuntime->dlaExecutors.back());
    const auto& modelTokenSize = finalExecutor->getModelTokenSize();
    for (auto& dlaExec : llmRuntime->dlaExecutors) {
        // align tokenindex and rollback cache
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(dlaExec);
        llmDlaExec->alignInputTokens(modelTokenSize - rollbackCount);
    }
}

void LLM_API mtk_llm_medusa_rollback(void* runtime, const std::vector<size_t>& acceptedIndices) {
    auto llmRuntime = reinterpret_cast<LlmRuntime*>(runtime);
    for (auto& dlaExec : llmRuntime->dlaExecutors) {
        auto llmMedusaDlaExec = static_cast<LlmMedusaDlaExecutor*>(dlaExec);
        llmMedusaDlaExec->rollbackTreeCache(acceptedIndices);
        llmMedusaDlaExec->alignInputTokens(acceptedIndices.size());
    }
}
