#include "mtk_mllm.h"

#include "backend/backend.h"
#include "common/dump.h"
#include "common/logging.h"
#include "common/thread_pool.h"
#include "common/timer.h"
#include "embedding_producer.h"
#include "executor/executor_factory.h"
#include "executor/shared_weights.h"
#include "image_transform.h"
#include "llm_helper/include/rotary_embedding.h"
#include "llm_helper/include/token_embedding.h"
#include "llm_helper/include/utils.h"
#include "tokenizer/tokenizer.h"

#include <algorithm>
#include <regex>
#include <string>
#include <string_view>
#include <thread>

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

#ifdef ALLOW_MLLM_LEFT_PADDING
static constexpr bool kAllowLeftPadding = true;
#else
static constexpr bool kAllowLeftPadding = false;
#endif

using LlmDlaExecutor = GetExecutorClass(Llm);
using ClipEmbDlaExecutor = GetExecutorClass(Neuron);
using LmHeadDlaExecutor = GetExecutorClass(Neuron);
using PatchEmbTfliteExecutor = GetExecutorClass(TFLite);

using TokenType = mtk::Tokenizer::TokenType;

using mtk::llm_helper::RotaryEmbeddingMasterLut;
using mtk::llm_helper::TokenEmbeddingLut;

using SharedWeightsHandleImpl = mtk::SharedWeightsHandle;

struct MllmRuntime {
    std::vector<mtk::Executor*> dlaExecutors;
    mtk::Executor* dlaLmHeadExecutor = nullptr;
    RotaryEmbeddingMasterLut* rotEmbMasterLut = nullptr;
    PatchEmbTfliteExecutor* clipPatchEmbExecutor = nullptr;
    ClipEmbDlaExecutor* clipExecutor = nullptr;
    TokenEmbeddingLut* tokenEmbLut = nullptr;
    MllmRuntimeOptions options;
    const SharedWeightsHandleImpl* sharedWeightsHandle = nullptr; // Will be used if not preloaded
};

// Helper functions
inline std::vector<std::pair<size_t, size_t>>
subtoken_delimit(const std::vector<TokenType>& inputTokens, const TokenType delimiter,
                 const bool preserveDelimiter = true) {
    std::vector<std::pair<size_t, size_t>> result; // Intervals

    auto appendResult = [&result](const auto start, const auto end) {
        if (start != end)
            result.push_back({start, end});
    };

    auto findDelimIdx = [&](const size_t startIndex) {
        const auto firstIt = inputTokens.begin();
        const auto lastIt = inputTokens.end();
        return std::find(firstIt + startIndex, lastIt, delimiter) - firstIt;
    };

    size_t start = 0;
    size_t delimIdx = findDelimIdx(0);

    while (delimIdx < inputTokens.size()) {
        appendResult(start, delimIdx);
        if (preserveDelimiter) {
            appendResult(delimIdx, delimIdx + 1);
        }
        start = delimIdx + 1;
        delimIdx = findDelimIdx(start);
    }
    appendResult(start, delimIdx);
    return result;
}

inline size_t getNumChunks(const MllmRuntimeOptions& runtimeOptions) {
    std::unordered_set<size_t> numChunkSet;
    for (const auto& [_, files] : runtimeOptions.dlaFiles) {
        numChunkSet.insert(files.size());
    }
    CHECK_LE(numChunkSet.size(), 1) << "Inconsistent number of dla chunks found in runtimeOptions.";
    return numChunkSet.empty() ? 0 : *numChunkSet.cbegin();
}

bool LLM_API mtk_mllm_init(void** runtime, const LlmModelOptions& modelOptions,
                           const MllmRuntimeOptions& runtimeOptions,
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

    // Create mllm runtime
    MllmRuntime* mllmRuntime = new MllmRuntime;
    mllmRuntime->options = runtimeOptions;

    // Use preloaded shared weights if available, otherwise create one without preload.
    const SharedWeightsHandleImpl* sharedWeightsHandle = nullptr;
    if (preloadedSharedWeights != nullptr) {
        sharedWeightsHandle =
            reinterpret_cast<const SharedWeightsHandleImpl*>(preloadedSharedWeights);
    } else if (numSharedWeightsFiles > 0) {
        sharedWeightsHandle =
            new SharedWeightsHandleImpl(runtimeOptions.sharedWeightsFiles, numChunk);
        // Store the handle if it's created and owned by this llmRuntime instance
        mllmRuntime->sharedWeightsHandle = sharedWeightsHandle;
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

    const size_t numCache = 2 * modelOptions.numLayer / numChunk; // Split cache
    CHECK_EQ(modelOptions.numLayer % numChunk, 0) << "Requires each DLA chunk to contain equal "
                                                  << "number of layers.";
    LOG(DEBUG) << "Number of cache per dla: " << numCache;

    // Initialize and prepare rotary embedding master lookup-table
    const size_t rotEmbDim = modelOptions.hiddenSize / modelOptions.numHead;
    mllmRuntime->rotEmbMasterLut =
        new RotaryEmbeddingMasterLut(modelOptions.rotEmbType, modelOptions.maxTokenLength,
                                     rotEmbDim, modelOptions.rotEmbBase, modelOptions.ntkScale);
    mllmRuntime->rotEmbMasterLut->generate();

    constexpr size_t numRotEmbInputs = 1;

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

    for (size_t chunkIdx = 0; chunkIdx < numChunk; ++chunkIdx) {
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
        auto dlaExec = new LlmDlaExecutor(
            runtimeInfos, sharedWeights, modelOptions.maxTokenLength, modelOptions.cacheSize,
            numCache, modelOptions.cacheType, modelOptions.maskType, mllmRuntime->rotEmbMasterLut,
            numRotEmbInputs, {}, "", 0, getCacheFile(chunkIdx), runtimeOptions.startTokenIndex);
        mllmRuntime->dlaExecutors.push_back(dlaExec);
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
        mllmRuntime->tokenEmbLut = new TokenEmbeddingLut(
            runtimeOptions.tokenEmbFile, modelOptions.modelInputType, modelOptions.hiddenSize);
        LOG(DEBUG) << "Initialized input token embedding lookup table.";
    };

    auto initClipExecutors = [&] {
        const auto& patchEmbFile = runtimeOptions.patchEmbFile;
        mllmRuntime->clipPatchEmbExecutor = new PatchEmbTfliteExecutor(patchEmbFile);
        mllmRuntime->clipPatchEmbExecutor->initialize();

        // Load CLIP Model
        LOG(DEBUG) << "Loading CLIP DLA: " << runtimeOptions.clipFile;
        mllmRuntime->clipExecutor = new ClipEmbDlaExecutor(runtimeOptions.clipFile);

        mllmRuntime->clipExecutor->setModelInput(mllmRuntime->clipPatchEmbExecutor->getOutput());
        mllmRuntime->clipExecutor->setNumInputs(1);
        mllmRuntime->clipExecutor->setNumOutputs(1);
        mllmRuntime->clipExecutor->initialize();
        mllmRuntime->clipExecutor->registerRuntimeIO();

        LOG(DEBUG) << "Initialized CLIP DLA";
    };

    for (size_t chunkIdx = 0; chunkIdx < numChunk; chunkIdx++) {
        // Initialize after reserving the input buffer so that the buffer allocator doesn't need to
        // allocate for inputs that are using an existing buffer created elsewhere.
        auto dlaExec = mllmRuntime->dlaExecutors[chunkIdx];
        LOG(DEBUG) << "Initializing DLA " << chunkIdx;
        if (chunkIdx > 0)
            dlaExec->reserveInputBuffer(); // Prevent allocation of buffer for input 0
        initExecutor(dlaExec);
    }
    threadPool.emplace_back(initTokenEmbLut);
    threadPool.emplace_back(initClipExecutors);

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
        return mllmRuntime->dlaExecutors[chunkIdx - 1]->getOutput();
    };

    for (size_t chunkIdx = 0; chunkIdx < numChunk; chunkIdx++) {
        // Initialize after setModelInput so that the buffer allocator doesn't need to allocate for
        // inputs that are using an existing buffer.
        auto dlaExec = mllmRuntime->dlaExecutors[chunkIdx];
        if (chunkIdx > 0)
            dlaExec->setModelInput(getPrevChunkOutput(chunkIdx));
        dlaExec->updateModelIO(); // Ensure IO sizes are correct, esp when using prev chunk buffer
        dlaExec->registerRuntimeIO(); // Attach allocated buffers to model IO
    }
    // Link first chunk emb input to token emb lut output
    const auto& tokenEmbInput = mllmRuntime->dlaExecutors.front()->getInput();
    mllmRuntime->tokenEmbLut->setOutput(tokenEmbInput.buffer, tokenEmbInput.sizeBytes);

    LOG(DEBUG) << "Done model chunks IO chaining";

    *runtime = mllmRuntime;
    return true;
}

void* LLM_API mtk_mllm_inference_once(void* runtime, const size_t leftPadSize,
                                      const size_t rightPadSize, const void* inputEmb,
                                      const LogitsKind outputKind) {
    DCHECK(leftPadSize == 0 || rightPadSize == 0)
        << "Invalid padding: Both both left and right padding are set.";

    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);

    const auto firstExecutor = static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors.front());
    const auto modelTokenSize = firstExecutor->getModelTokenSize();

    if (inputEmb != nullptr) {
        // Manually provided input embedding
        const auto inputEmbSize = firstExecutor->getModelInputSizeBytes();
        firstExecutor->setModelInput(inputEmb, inputEmbSize);
        firstExecutor->registerRuntimeIO();
    }

    // Set padding
    for (auto dlaExec : mllmRuntime->dlaExecutors) {
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(dlaExec);
        if (leftPadSize > 0)
            llmDlaExec->setLeftPadding(leftPadSize);
        else if (rightPadSize > 0)
            llmDlaExec->setRightPadding(rightPadSize);
    }

    static size_t inferenceStep = 0;
    SET_DUMP_INDEX(inferenceStep++);

    const auto numChunk = mllmRuntime->dlaExecutors.size();
    auto getLlmDlaExec = [&](const int chunkIdx) -> LlmDlaExecutor* {
        if (chunkIdx < 0 || chunkIdx >= numChunk)
            return nullptr;
        return static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors[chunkIdx]);
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

    // Return logits
    const auto finalExecutor = static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors.back());
    auto logitsBuffer = finalExecutor->getOutputBuffer();
    size_t offset = 0;
    if (outputKind == LogitsKind::LAST && modelTokenSize > 1) {
        const auto logitsSize = finalExecutor->getModelOutputSizeBytes();
        offset = (logitsSize / modelTokenSize) * (modelTokenSize - 1 - rightPadSize);
        DCHECK_LE(offset, logitsSize);
    }
    return reinterpret_cast<char*>(logitsBuffer) + offset;
}

void LLM_API mtk_mllm_release(void* runtime) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);
    for (auto dlaExec : mllmRuntime->dlaExecutors) {
        delete dlaExec;
    };
    mllmRuntime->dlaExecutors.clear();
    delete mllmRuntime->dlaLmHeadExecutor;
    delete mllmRuntime->tokenEmbLut;
    delete mllmRuntime->rotEmbMasterLut;

    delete mllmRuntime->clipExecutor;
    delete mllmRuntime->clipPatchEmbExecutor;
    delete mllmRuntime->sharedWeightsHandle;
    delete mllmRuntime;
}

size_t LLM_API mtk_mllm_get_per_token_logits_size(void* runtime) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);
    const auto finalExecutor = static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors.back());
    const auto modelBatchSize = finalExecutor->getBatchSize();
    const auto modelTokenSize = finalExecutor->getModelTokenSize();
    const auto numTotalTokens = modelTokenSize * modelBatchSize;
    if (mllmRuntime->dlaLmHeadExecutor == nullptr) {
        const auto logitsSize = finalExecutor->getModelOutputSizeBytes();
        return logitsSize / numTotalTokens;
    } else {
        const auto perTokenHiddenStateSize =
            finalExecutor->getModelOutputSizeBytes() / numTotalTokens;
        const auto lmHeadExecutor = static_cast<LmHeadDlaExecutor*>(mllmRuntime->dlaLmHeadExecutor);
        const auto lmHeadNumTotalTokens =
            lmHeadExecutor->getModelInputSizeBytes() / perTokenHiddenStateSize;
        const auto logitsSize = lmHeadExecutor->getModelOutputSizeBytes();
        return logitsSize / lmHeadNumTotalTokens;
    }
}

void* LLM_API mtk_mllm_consume_prompt(void* runtime, const std::vector<TokenType>& tokens,
                                      const std::vector<std::string>& imagePaths,
                                      size_t* numPromptToken, const LogitsKind outputKind) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);

    // Get target consumer buffer
    const auto firstExecutor = static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors.front());
    const auto targetBuffer = firstExecutor->getInputBuffer();
    const auto targetSize = firstExecutor->getModelInputSizeBytes();

    // Prepare information for embedding producers
    const auto imageTokenSize = mllmRuntime->options.imageTokenSize;
    const auto singleEmbSize = mllmRuntime->tokenEmbLut->getEmbSizeBytes();

    auto isImageToken = [&tokens](const auto start, const auto end) {
        return (end - start == 1) && (tokens[start] == kImagePlaceholderToken);
    };

    auto loadImgEmb = [runtime](const std::string& imagePath) {
        int imageSizeBytes = 0;
        using namespace mtk::image_utils;
        const auto image = clip_preprocess(imagePath, imageSizeBytes, kImgSize, kCropSize, kScale);
        return mtk_mllm_get_clip_embedding(runtime, image.data, imageSizeBytes);
    };

    // Initialize the embedding producers
    const auto subtokenIntervals = subtoken_delimit(tokens, kImagePlaceholderToken, true);
    const auto numPromptSections = subtokenIntervals.size();

    std::vector<std::unique_ptr<mtk::EmbeddingProducer>> embProducerQueue;
    embProducerQueue.reserve(numPromptSections);

    *numPromptToken = 0; // Reset

    size_t imageIdx = 0;
    for (const auto& [start, end] : subtokenIntervals) {
        std::unique_ptr<mtk::EmbeddingProducer> curEmbProducer;
        if (isImageToken(start, end)) { // Image token
            CHECK_LT(imageIdx, imagePaths.size())
                << "Detected more image tokens than the number of given images.";
            curEmbProducer = std::make_unique<mtk::ImageEmbeddingProducer>(
                imagePaths[imageIdx++], imageTokenSize, loadImgEmb, singleEmbSize);
            *numPromptToken += imageTokenSize;
        } else { // Text token
            const auto subTokens = std::vector(tokens.begin() + start, tokens.begin() + end);
            curEmbProducer = std::make_unique<mtk::TextEmbeddingProducer>(
                subTokens, mllmRuntime->tokenEmbLut, singleEmbSize);
            *numPromptToken += subTokens.size();
        }
        DCHECK(!curEmbProducer->isEmpty());
        curEmbProducer->setConsumer(targetBuffer, targetSize);
        embProducerQueue.emplace_back(std::move(curEmbProducer));
    }
    const auto& imageTokenCount = imageIdx; // For readability in logging
    CHECK_EQ(imageTokenCount, imagePaths.size())
        << "The number of image tokens in the prompt does not match then number of given images.";

    // Begin consuming the prompt chunk by chunk
    auto curEmbProdIt = embProducerQueue.begin();
    auto hasProducer = [&]() { return curEmbProdIt != embProducerQueue.end(); };
    void* logitsBuffer = nullptr;
    const auto modelTokenSize = firstExecutor->getModelTokenSize();
    const auto padSize = modelTokenSize - (*numPromptToken % modelTokenSize);

    auto getLeftPadding = [&] {
        if (kAllowLeftPadding && firstExecutor->getTokenIndex() == 0)
            return padSize;
        return 0UL;
    };

    while (hasProducer()) {
        // Fill modelTokenSize number of embeddings, or break if no embedding left to consume
        const auto leftPadSize = getLeftPadding();
        size_t demandRemain = modelTokenSize - leftPadSize;
        while (demandRemain > 0 && hasProducer()) {
            const auto numProduced = (*curEmbProdIt)->produceEmbedding(demandRemain);
            DCHECK_LE(numProduced, demandRemain);
            demandRemain -= numProduced;
            if ((*curEmbProdIt)->isEmpty()) {
                ++curEmbProdIt; // Move to the next producer
            }
        }
        // Only the last prompt step needs logits
        const auto logitsKind = hasProducer() ? LogitsKind::NONE : outputKind;
        const auto rightPadSize = demandRemain;
        logitsBuffer =
            mtk_mllm_inference_once(runtime, leftPadSize, rightPadSize, nullptr, logitsKind);
    }
    return logitsBuffer;
}

void* LLM_API mtk_mllm_consume_emb(void* runtime, const char* embBuffer, const size_t embBufferSize,
                                   const LogitsKind outputKind) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);

    const auto singleEmbSize = mllmRuntime->tokenEmbLut->getEmbSizeBytes();
    const auto numTokens = embBufferSize / singleEmbSize;

    // Get target consumer buffer
    const auto firstExecutor = static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors.front());
    const auto targetBuffer = reinterpret_cast<char*>(firstExecutor->getInputBuffer());
    const auto targetSize = firstExecutor->getModelInputSizeBytes();

    // Begin consuming the prompt embedding chunk by chunk
    size_t numTokensRemain = numTokens;
    void* logitsBuffer = nullptr;

    const auto modelTokenSize = firstExecutor->getModelTokenSize();
    const auto padSize = modelTokenSize - (numTokens % modelTokenSize);

    auto getPadding = [&]() -> std::pair<size_t, size_t> {
        const bool isCacheEmpty = (firstExecutor->getTokenIndex() == 0);
        const auto leftPadSize = (kAllowLeftPadding && isCacheEmpty) ? padSize : 0;
        const auto rightPadSize =
            (leftPadSize == 0 && numTokensRemain == modelTokenSize - padSize) ? padSize : 0;
        return {leftPadSize, rightPadSize};
    };

    // Input of model with token size T must fit T tokens worth of embeddings
    DCHECK_GE(targetSize, modelTokenSize * singleEmbSize);

    while (numTokensRemain > 0) {
        const auto [leftPadSize, rightPadSize] = getPadding();
        const size_t writeOffset = leftPadSize * singleEmbSize;
        const size_t readOffset = (numTokens - numTokensRemain) * singleEmbSize;
        const size_t numTokensCopy = modelTokenSize - leftPadSize - rightPadSize;
        const size_t copySize = numTokensCopy * singleEmbSize;
        DCHECK_LE(numTokensCopy, numTokensRemain);
        std::memcpy(targetBuffer + writeOffset, embBuffer + readOffset, copySize);
        logitsBuffer =
            mtk_mllm_inference_once(runtime, leftPadSize, rightPadSize, nullptr, outputKind);
        numTokensRemain -= numTokensCopy;
    }
    return logitsBuffer;
}

size_t LLM_API mtk_mllm_get_token_index(void* runtime) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);
    const auto firstExecutor = static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors.front());
    return firstExecutor->getTokenIndex();
}

void LLM_API mtk_mllm_rollback(void* runtime, const size_t rollbackCount) {
    if (rollbackCount == 0)
        return;
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);
    const auto finalExecutor = static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors.back());
    const auto& modelTokenSize = finalExecutor->getModelTokenSize();
    for (auto& dlaExec : mllmRuntime->dlaExecutors) {
        // align tokenindex and rollback cache
        auto llmDlaExec = static_cast<LlmDlaExecutor*>(dlaExec);
        llmDlaExec->alignInputTokens(modelTokenSize - rollbackCount);
    }
}

void* LLM_API mtk_mllm_get_text_embedding(void* runtime, const std::vector<TokenType>& inputTokens,
                                          void* inputTextEmbCopy) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);
    const auto firstExecutor = static_cast<LlmDlaExecutor*>(mllmRuntime->dlaExecutors.front());
    const auto modelTokenSize = firstExecutor->getModelTokenSize();

    // Error checking
    if (inputTokens.size() > modelTokenSize) {
        LOG(FATAL) << "The required input token length (" << inputTokens.size() << ") "
                   << "exceeds what the model can take in (" << modelTokenSize << ")";
    }
    mllmRuntime->tokenEmbLut->lookupEmbedding(inputTokens);

    const auto inputEmbBuffer = firstExecutor->getInputBuffer();
    const auto perTokenEmbSizeBytes = firstExecutor->getModelInputSizeBytes() / modelTokenSize;
    const auto textEmbSizeBytes = perTokenEmbSizeBytes * inputTokens.size();

    if (inputTextEmbCopy != nullptr) {
        std::memcpy(inputTextEmbCopy, inputEmbBuffer, textEmbSizeBytes);
        return inputTextEmbCopy;
    }
    return inputEmbBuffer;
}

void* LLM_API mtk_mllm_get_clip_embedding(void* runtime, void* imageBuffer,
                                          const size_t imageBufferSize) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);

    // assume image is already preprocessed
    Timer patchEmbTimer, clipDLATimer, quantTimer;
    patchEmbTimer.start();
    mllmRuntime->clipPatchEmbExecutor->runInference(imageBuffer, imageBufferSize);
    double patchEmbTimeTaken = patchEmbTimer.reset();
    LOG(INFO) << "Patch embedding takes: " << patchEmbTimeTaken << "s";

    clipDLATimer.start();
    mllmRuntime->clipExecutor->runInference();
    double clipDLATimeTaken = clipDLATimer.reset();
    LOG(INFO) << "Done CLIP dla inference in: " << clipDLATimeTaken << "s";
    const auto clipEmbBuffer = mllmRuntime->clipExecutor->getOutputBuffer();

    return clipEmbBuffer;
}

size_t LLM_API mtk_mllm_get_input_emb_size_bytes(void* runtime) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);
    return mllmRuntime->dlaExecutors.front()->getModelInputSizeBytes();
}

void LLM_API mtk_mllm_reset(void* runtime, const bool resetCache) {
    auto mllmRuntime = reinterpret_cast<MllmRuntime*>(runtime);
    for (auto dlaExec : mllmRuntime->dlaExecutors) {
        auto mllmDlaExec = static_cast<LlmDlaExecutor*>(dlaExec);
        if (resetCache) {
            // Reset cache and token index, resetTokenIndex() will be called
            mllmDlaExec->initCache();
        } else {
            // Reset token index without resetting cache
            mllmDlaExec->resetTokenIndex();
        }
    }
}