#pragma once

#include "backend/api/neuron/Types.h"
#include "common/file_source.h"
#include "executor/neuron_executor.h"
#include "executor/neuron_usdk_executor.h"
#include "executor/shared_weights.h"
#include "llm_helper/include/lora_weights_loader.h"
#include "llm_helper/include/mask_builder.h"
#include "llm_helper/include/rotary_embedding.h"
#include "mtk_llm.h"
#include "mtk_llm_types.h"

#include <array>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

namespace mtk {

using LoraWeightsFileMap = std::unordered_map<LoraKey, FileSource>;

#ifdef USE_USDK_BACKEND
using ExecutorBackend = NeuronUsdkExecutor;
#else
using ExecutorBackend = NeuronExecutor;
#endif

class LlmExecutor : public ExecutorBackend {
public:
    using ShapeValueType = std::remove_extent_t<decltype(RuntimeAPIDimensions::dimensions)>;
    using ShapeType = std::array<ShapeValueType, kDimensionSize>;

    // Dimension where the cache length can be found from the input cache shape
    static constexpr size_t kCacheLengthDim = 2;

    struct RuntimeInfo {
        FileSource modelFile;
        size_t batchSize = 1;
        size_t tokenSize = 1; // E.g. prompt model is 32, whereas generative mode is 1
        size_t cacheSize = 0; // For FMS-based dynamic shape cache
    };

private:
    static constexpr size_t kUnusedSize = 0;

public:
    // clang-format off
    explicit LlmExecutor(const std::vector<RuntimeInfo>& runtimeInfos,
                         const SharedWeights& sharedWeights,
                         const size_t maxTokenLength,
                         const size_t cacheLength,
                         const size_t cacheCount,
                         const LLMType cacheType,
                         const LLMType maskType,
                         // Rotary Embedding Lut
                         const llm_helper::RotaryEmbeddingMasterLut* rotEmbMasterLut,
                         const size_t rotEmbInputCount,
                         // Lora
                         const LoraWeightsFileMap& loraWeightsFileMap,
                         const LoraKey& initWithLoraKey,
                         const size_t loraInputCount,
                         // Init cache files
                         const FileSource& initCacheFile,
                         const size_t initTokenIndex = 0,
                         // Inputs
                         const size_t maskInputIndex = 1,
                         const size_t rotEmbInputIndex = 2)
        : ExecutorBackend(getModelFiles(runtimeInfos), sharedWeights),
          kRuntimeInfos(runtimeInfos),
          // Llm specific options
          kMaxTokenLength(maxTokenLength),
          mCacheLength(cacheLength),
          kCacheCount(cacheCount),
          kCacheTypeSize(getLLMTypeSize(cacheType)),
          kMaskType(maskType),
          kMaskTypeSize(getLLMTypeSize(kMaskType)),
          kInitTokenIndex(initTokenIndex),
          kInitCacheFile(initCacheFile),
          mRotEmbMasterLut(rotEmbMasterLut),
          kRotEmbInputCount(rotEmbInputCount),
          // Lora Input Weights, infer the number of Lora inputs from bin header if not specified.
          kLoraWeightsFileMap(loraWeightsFileMap),
          kDefaultLoraKey(initWithLoraKey),
          kLoraInputCount(loraInputCount ? loraInputCount : getLoraInputCount(kLoraWeightsFileMap)),
          // Llm specific IO indexes
          kMaskInputIndex(maskInputIndex),
          kRotEmbInputIndexes(getIndexRange(rotEmbInputIndex, rotEmbInputCount)),
          kCacheInputIndexes(getIndexRange(rotEmbInputIndex + rotEmbInputCount, cacheCount)),
          kCacheOutputIndexes(getIndexRange(1, cacheCount)),
          kLoraWeightsInputIndexes(getIndexRange(kCacheInputIndexes.back() + 1, kLoraInputCount)) {}

    // clang-format on

    ~LlmExecutor() {}

    // Initialization
    virtual void initialize() override;
    virtual void preInitBufferProcess() override;
    virtual void assignBufferSizesToMax() override;

    void setNumIOs();

    virtual void runInferencePrologue() override;

    virtual void runInferenceEpilogue() override;

    // Hot-swap to model with tokenSize and cacheSize if available.
    // Returns true if swap successfully, false if otherwise.
    bool hotSwapModel(const size_t tokenSize, const size_t cacheSize = kUnusedSize);

    // Get the next available larger cache size given a token size.
    // Or return the smallest cache size if all cache sizes under given token size are smaller than
    // the current one, assuming the cache will be reset.
    size_t getNextAvailCacheSize(const size_t tokenSize);

    // Get the next available larger cache size, under the same token size.
    size_t getNextAvailCacheSize();

    // Caches
    virtual void initCache();
    // Get cache buffers
    void getCacheBuffersWithSize(std::vector<char*>& cacheBuffers, size_t& byteSizePerCache);

    // Token index
    virtual void resetTokenIndex();
    void setTokenIndex(const size_t index); // NOTE: Need to modify cache if token index was not 0
    void advanceTokenIndex();
    size_t getTokenIndex() const;
    size_t getEffectiveTokenIndex() const;
    size_t getEffectiveTokenIndex(const size_t tokenIndex) const;

    // Align the model state (cache & token index) with the current input. Used for >1t model.
    // Returns the number of tokens being shifted/rolledback
    int alignInputTokens(const size_t numInputToken);

    void updatePosEmbAndMask(const size_t numInputToken = 1);

    // Padding
    void setLeftPadding(const size_t leftPadSize);
    void setRightPadding(const size_t rightPadSize);
    void paddingPostprocess(); // General padding postprocessing and will call L/R specific routine

    // Get expected input token count from the model
    size_t getModelTokenSize() const { return mModelTokenSize; }
    // Get expected input token count excluding padded tokens from the model
    size_t getValidModelNumInputToken() const { return mModelTokenSize - getPadSize(); }

    // LoRA-as-inputs
    // Apply Lora based on predefined Lora Key. Empty key will remove Lora and use base weights only
    void applyLoraWeights(const LoraKey& loraKey = "");

    // Apply Lora based on provided Lora weights, will override/bypass any predefined Lora keys.
    void applyLoraWeights(const std::vector<const char*>& loraWeights,
                          const std::vector<size_t>& sizes);

    // Remove Lora and use base weights only
    void removeLoraWeights();

    size_t getCacheLength() const { return mCacheLength; }

    // Check if the executor is in folded gen batch mode.
    bool isFoldedGenBatchMode() const;

    // Enter folded gen batch mode by setting folded batch token size
    // The folded gen batch mode can only be exited/disabled by calling `resetTokenIndex()`.
    void enterFoldedGenBatchMode();

protected:
    // clang-format off
    const size_t& getMaskInputIdx()                 const { return kMaskInputIndex; }
    const std::vector<size_t>& getRotEmbInputIdxs() const { return kRotEmbInputIndexes; }
    const std::vector<size_t>& getCacheInputIdxs()  const { return kCacheInputIndexes; }
    const std::vector<size_t>& getCacheOutputIdxs() const { return kCacheOutputIndexes; }
    // clang-format on

    size_t getPadSize() const { return mCurrentPadSize; }
    size_t getLeftPadding() const;
    size_t getRightPadding() const;

    // Cache post-processing specific to left/right padding
    virtual void leftPaddingCachePostprocess();
    virtual void rightPaddingCachePostprocess();

    virtual void linkCacheIOs();

    virtual void rollbackCache(const size_t tokenCount);

    virtual std::vector<char*> getCacheBuffers();

    // Duplicate input caches to all batches during model swap to larger batch size
    virtual void inputCacheDupAllBatches();

    // Override the input batch dim. LLM model input 0 batch dim is 1.
    virtual size_t getInputBatchDim() const override { return 1; }

    // In-place reshape cache inputs from [..., oldCacheLength, ...] to [..., newCacheLength, ...]
    virtual void reorderCacheInputs(const size_t newCacheLength);

    // Helper functions

    // Returns the number of rows per cache input. The batch dimension is included in the calc.
    size_t getCacheNumRows(const size_t index) const;

    // Returns the cache stride size (aka headDim) in bytes, and assumed to be same across caches.
    size_t getCacheStrideSize() const;

private:
    size_t getExpectedNumInputs() const {
        return 2 + kRotEmbInputCount + kCacheCount + kLoraInputCount + this->numSharedWeightsUsed();
    }
    size_t getExpectedNumOutputs() const { return 1 + kCacheCount; }

    void initMaskBuilder();

    virtual void setPosEmbed(const size_t tokenIndex);

    // Build the mapping from model info (token size, cache size) to runtime index
    void buildRuntimeIdxMap();

    // Select the model with largest token size with smallest cache size
    void setDefaultModel();

    static std::vector<size_t> getIndexRange(const size_t startIndex, const size_t count) {
        std::vector<size_t> indexes(count);
        size_t counter = startIndex;
        for (auto& idx : indexes) {
            idx = counter++;
        }
        return indexes;
    }

    static std::vector<FileSource> getModelFiles(const std::vector<RuntimeInfo>& runtimeInfos) {
        std::vector<FileSource> modelFiles;
        for (const auto& runtimeInfo : runtimeInfos) {
            modelFiles.push_back(runtimeInfo.modelFile);
        }
        return modelFiles;
    }

    static size_t getLoraInputCount(const LoraWeightsFileMap& loraWeightsFileMap) {
        std::unordered_set<size_t> loraInputsCountSet;
        for (const auto& [loraKey, loraFile] : loraWeightsFileMap) {
            CHECK(loraFile.valid());
            const auto numLoraInputs = llm_helper::LoraWeightsLoader(loraFile).getNumLoraInputs();
            LOG(DEBUG) << " Lora weights '" << loraKey << "' has " << numLoraInputs << " inputs.";
            loraInputsCountSet.insert(numLoraInputs);
        }
        if (loraInputsCountSet.size() > 1) {
            LOG(ERROR) << "Unsupported: Different Lora weight input count found across Lora "
                       << "weights bin files.";
        }
        return loraInputsCountSet.empty() ? 0 : *loraInputsCountSet.cbegin();
    }

protected:
    // The number of input tokens the the fixed-shape model takes
    size_t mModelTokenSize = 1;

    const std::vector<RuntimeInfo> kRuntimeInfos;

    // Map [tokenSize][cacheSize] -> runtime index
    std::unordered_map<int, std::unordered_map<int, size_t>> mRuntimeIdxMap;

    // Map tokenSize -> batchSize // NOTE: Assume batch size depends on token size.
    std::unordered_map<size_t, size_t> mBatchSizeMap;

    // Num prompt token recorded for folded gen batch mode
    size_t mGenBatchNumPromptTokens = 0;

    // Cache
    std::vector<ShapeType> mCacheShapes;
    size_t mCacheLength;
    const size_t kMaxTokenLength;
    const size_t kCacheCount;
    const size_t kCacheTypeSize; // bytes

    // Mask
    const LLMType kMaskType;
    const size_t kMaskTypeSize; // bytes

    enum class PaddingMode {
        LEFT,
        RIGHT
    };

    // Padding
    size_t mCurrentPadSize = 0;
    PaddingMode mPaddingMode = PaddingMode::RIGHT;

    const size_t kInitTokenIndex = 0;

    const FileSource kInitCacheFile;

    // Master lookup table for rotary embedding
    const llm_helper::RotaryEmbeddingMasterLut* mRotEmbMasterLut;
    const size_t kRotEmbInputCount;

    // Mask builder
    std::unique_ptr<llm_helper::MaskBuilder> mMaskBuilder;

    size_t mCurrentTokenIndex = 0; // Default init from 0, also can be numSeenToken

    // Will be set to false during init and after model swap
    bool mIsMaskUpdatable = false;

    // LoRA-as-inputs
    const LoraWeightsFileMap kLoraWeightsFileMap;
    const size_t kLoraInputCount = 0;
    const LoraKey kDefaultLoraKey;
    LoraKey mCurrentLoraKey;

    // IO Indexes
    const size_t kMaskInputIndex;
    const std::vector<size_t> kRotEmbInputIndexes;
    const std::vector<size_t> kCacheInputIndexes;
    const std::vector<size_t> kCacheOutputIndexes;
    const std::vector<size_t> kLoraWeightsInputIndexes;
};

} // namespace mtk