#include "executor/llm_executor.h"

#include "common/logging.h"
#include "common/scope_profiling.h"
#include "common/thread_pool.h"
#include "executor/io_buffer_inflator.h"
#include "llm_helper/include/rotary_embedding.h"
#include "llm_helper/include/utils.h"

#include <stdint.h>

#include <algorithm>
#include <iterator>
#include <numeric>
#include <set>
#include <thread>

namespace mtk {

// clang-format off

void LlmExecutor::initialize() {
    DLOG_FUNC_LATENCY(s);

    buildRuntimeIdxMap();
    setDefaultModel();
    setNumIOs();

    if (isSharedWeightsUsed()) {
        const auto numSharedWeights = numSharedWeightsUsed();
        // Load shared weights and model in parallel
        const auto firstSharedWeightsInputIdx = getExpectedNumInputs() - numSharedWeights;
        // Reserve to prevent `initBuffer()` from allocating in case it is called first
        for (size_t counter = 0; counter < numSharedWeights; counter++) {
            reserveInputBuffer(firstSharedWeightsInputIdx + counter);
        }
        initAllocator(); // Allocator must be ready before loadSharedWeights invoked
        std::thread loadSharedWeightsThread([&] {
            ExecutorBackend::loadSharedWeights(firstSharedWeightsInputIdx);
        });
        std::thread initializeThread([&] {
            ExecutorBackend::initialize();
        });
        loadSharedWeightsThread.join();
        initializeThread.join();
    } else {
        ExecutorBackend::initialize(); // Not using shared weights
    }

    initMaskBuilder();
    initCache();
    applyLoraWeights(kDefaultLoraKey);
}

// clang-format on

void LlmExecutor::setNumIOs() {
    // Used because Neuron Adapter requires number of IO before runtime init
    this->setNumInputs(getExpectedNumInputs());
    this->setNumOutputs(getExpectedNumOutputs());
}

void LlmExecutor::buildRuntimeIdxMap() {
    size_t runtimeIdx = 0;
    for (const auto& runtimeInfo : kRuntimeInfos) {
        const auto& tokenSize = runtimeInfo.tokenSize;
        const auto& cacheSize = runtimeInfo.cacheSize;
        mRuntimeIdxMap[tokenSize][cacheSize] = runtimeIdx++;

        // Build batch size map
        const auto& batchSize = runtimeInfo.batchSize;
        mBatchSizeMap[tokenSize] = batchSize;
    }
}

void LlmExecutor::setDefaultModel() {
    // Select the model with largest token size with smallest cache size

    auto keyLessThan = [](const auto& pairA, const auto& pairB) {
        return pairA.first < pairB.first;
    };
    auto getMaxKey = [&](const auto& map) {
        return std::max_element(map.begin(), map.end(), keyLessThan)->first;
    };
    auto getMinKey = [&](const auto& map) {
        return std::min_element(map.begin(), map.end(), keyLessThan)->first;
    };

    const auto maxTokenSize = getMaxKey(mRuntimeIdxMap);
    const auto minCacheSize = getMinKey(mRuntimeIdxMap[maxTokenSize]);
    const auto defaultRuntimeIndex = mRuntimeIdxMap[maxTokenSize][minCacheSize];
    this->setDefaultRuntimeIndex(defaultRuntimeIndex);

    mModelTokenSize = maxTokenSize;
    mCacheLength = minCacheSize;

    this->mModelBatchSize = mBatchSizeMap[maxTokenSize];
    LOG(DEBUG) << "Default model batch size = " << this->mModelBatchSize;
}

void LlmExecutor::applyLoraWeights(const LoraKey& loraKey) {
    if (mCurrentLoraKey == loraKey) {
        return; // Already applied
    } else if (loraKey.empty()) {
        removeLoraWeights(); // Empty key, so clear Lora weights to zeros to use base weights
        return;
    } else if (kLoraWeightsFileMap.find(loraKey) == kLoraWeightsFileMap.end()) {
        LOG(ERROR) << "Invalid LoraKey: " << loraKey;
        return;
    }
    std::vector<void*> loraInputBuffers;
    std::vector<size_t> loraInputBufferSizes;
    for (const auto loraInputIdx : kLoraWeightsInputIndexes) {
        const auto& input = this->getInput(loraInputIdx);
        loraInputBuffers.push_back(input.buffer);
        loraInputBufferSizes.push_back(input.usedSizeBytes);
    }
    CHECK_EQ(kLoraInputCount, loraInputBuffers.size());
    llm_helper::LoraWeightsLoader loader(kLoraWeightsFileMap.at(loraKey));
    loader.loadLoraWeights(loraInputBuffers, loraInputBufferSizes);

    mCurrentLoraKey = loraKey;
    LOG(DEBUG) << "Successfully applied Lora weights with key: " << loraKey;
}

void LlmExecutor::applyLoraWeights(const std::vector<const char*>& loraWeights,
                                   const std::vector<size_t>& sizes) {
    CHECK_EQ(kLoraInputCount, loraWeights.size());
    CHECK_EQ(sizes.size(), loraWeights.size());
    for (size_t i = 0; i < kLoraInputCount; i++) {
        const auto loraInputIdx = kLoraWeightsInputIndexes[i];
        auto& input = this->getInput(loraInputIdx);
        const auto loraWeight = loraWeights[i];
        const auto loraWeightSize = sizes[i];
        CHECK_LE(loraWeightSize, input.sizeBytes)
            << "Insufficient buffer allocation (size=" << input.sizeBytes << ") to load Lora input "
            << i << " weights (size=" << loraWeightSize << ")";
        if (loraWeightSize != input.usedSizeBytes) {
            LOG(WARN) << "Expected Lora input " << i << " size by model (" << input.usedSizeBytes
                      << ") != " << "provided Lora weights size (" << loraWeightSize << ")";
        }
        std::memcpy(input.buffer, loraWeight, loraWeightSize);
    }
    mCurrentLoraKey = ""; // Not using any predefined Lora keys
    LOG(DEBUG) << "Successfully applied Lora weights from user provided buffers";
}

void LlmExecutor::removeLoraWeights() {
    // Memset Lora input buffers to zeros
    for (const auto idx : kLoraWeightsInputIndexes) {
        auto& input = this->getInput(idx);
        std::memset(input.buffer, 0, input.usedSizeBytes);
    }
    mCurrentLoraKey = "";
    LOG(DEBUG) << "Removed Lora weights";
}

void LlmExecutor::preInitBufferProcess() {
    ExecutorBackend::preInitBufferProcess();

    // Get input cache shape and ensure cache size is correct
    const auto& cacheInputIdxs = this->getCacheInputIdxs();
    const auto numInputCaches = cacheInputIdxs.size();
    DCHECK_GT(numInputCaches, 0);
    DCHECK_EQ(numInputCaches, kCacheCount);
    mCacheShapes.resize(numInputCaches);
    for (size_t i = 0; i < numInputCaches; i++) {
        auto& cacheShape = mCacheShapes[i];
        this->getRuntimeInputShape(cacheInputIdxs[i], cacheShape.data());
        CHECK_EQ(cacheShape[kCacheLengthDim], getCacheLength())
            << "Please ensure the cache size option is set correctly.";
    }

    // Ensure all stride sizes are the same across cache inputs
    auto getStrideSize = [this](const auto& cacheShape) {
        return llm_helper::reduce_prod(
            cacheShape.begin() + kCacheLengthDim + 1, cacheShape.end(), kCacheTypeSize);
    };
    const auto firstStrideSize = getStrideSize(mCacheShapes[0]);
    for (const auto& cacheShape : mCacheShapes) {
        CHECK_EQ(firstStrideSize, getStrideSize(cacheShape))
            << "Different stride size across caches are not supported.";
    }

    // Verify cache type size using the first cache
    const auto inputCacheSizeBytes = this->getModelInputSizeBytes(cacheInputIdxs[0]);
    const auto inputCacheSize = llm_helper::reduce_prod(mCacheShapes[0]);
    const auto modelCacheTypeSize = inputCacheSizeBytes / inputCacheSize;
    CHECK_EQ(kCacheTypeSize, modelCacheTypeSize)
        << "Mismatch between user provided cache type size (" << kCacheTypeSize << ") "
        << "and actual model cache type size (" << modelCacheTypeSize << ")";

    // Check number of IOs
    CHECK_EQ(getExpectedNumInputs(), this->getRuntimeNumInputs())
        << "Number of inputs does not match, please ensure the model is correct.";
    CHECK_EQ(getExpectedNumOutputs(), this->getRuntimeNumOutputs())
        << "Number of outputs does not match, please ensure the model is correct.";

    // Link cache IOs
    linkCacheIOs();
}

void LlmExecutor::initMaskBuilder() {
    const auto maskBuffer = this->getInputBuffer(getMaskInputIdx());
    const auto maskSizeBytes = this->getModelInputSizeBytes(getMaskInputIdx());
    mMaskBuilder = std::make_unique<llm_helper::MaskBuilder>(
        maskBuffer, maskSizeBytes / this->getBatchSize(), kMaskType, getCacheLength());
    mMaskBuilder->buildMask(mModelTokenSize, mCurrentTokenIndex);
    // Duplicate masks for all batches
    this->inputDupAllBatches(getMaskInputIdx());
}

void LlmExecutor::assignBufferSizesToMax() {
    // =============================================================
    //         Input/            |        Size Dependencies
    //         Output            | BatchSize | TokenSize | CacheSize
    // ==========================+===========+===========+===========
    //  Embedding input          |     Y     |     Y     |
    //  Mask input               | (Non-standard size calculation)
    //  Rotary Embdding inputs   |     Y     |     Y     |
    //  Cache inputs             |     Y     |           |     Y
    //  Embedding/Logits output  |     Y     |     Y     |
    //  Cache outputs            |     Y     |           |     Y
    //  Cache outputs (w/ RB)    |     Y     |     Y     |
    // =============================================================

    const auto curBatchSize = this->getBatchSize();
    const auto curTokenSize = this->getModelTokenSize();
    const auto curCacheSize = this->getCacheLength();

    IoBufferInflator ioBufferInflator(kRuntimeInfos, curBatchSize, curTokenSize, curCacheSize);

    auto binary_max = [](const auto& lhs, const auto& rhs) { return std::max(lhs, rhs); };

    auto getMaskSize = [&](const auto& runtimeInfo) -> size_t {
        const auto batchSize = runtimeInfo.batchSize;
        const auto tokenSize = runtimeInfo.tokenSize;
        const auto cacheSize = runtimeInfo.cacheSize;
        // Ensure 16 bytes alignment in dim C
        constexpr size_t nBytesAlign = 16;
        const size_t dimC = std::ceil(float(cacheSize + tokenSize) * kMaskTypeSize / nBytesAlign)
                            * nBytesAlign / kMaskTypeSize;
        return batchSize * tokenSize * dimC * kMaskTypeSize;
    };

    // Embedding input: BatchSize & TokenSize
    LOG(DEBUG) << "Finding max buffer size for Embedding input";
    ioBufferInflator.useBatchSize().useTokenSize();
    ioBufferInflator.findMaxSizeScenario();
    ioBufferInflator.inflate(this->getInput(0));
    ioBufferInflator.resetUses();

    // Mask input: Special Handling
    LOG(DEBUG) << "Finding max buffer size for Mask input";
    auto& maskInput = this->getInput(getMaskInputIdx());
    auto maxMaskSize = std::transform_reduce(
        kRuntimeInfos.begin(), kRuntimeInfos.end(), 0UL, binary_max, getMaskSize);
    const auto oldSize = maskInput.usedSizeBytes;
    if (oldSize < maxMaskSize) {
        maskInput.sizeBytes = maxMaskSize;
        LOG(DEBUG) << "Reassigned required allocation size: " << oldSize << " -> " << maxMaskSize;
    }

    // Rotary Embdding inputs: BatchSize & TokenSize
    LOG(DEBUG) << "Finding max buffer size for Rotary Embedding input";
    ioBufferInflator.useBatchSize().useTokenSize();
    ioBufferInflator.findMaxSizeScenario();
    for (const auto rotEmbInputIdx : getRotEmbInputIdxs()) {
        ioBufferInflator.inflate(this->getInput(rotEmbInputIdx));
    }
    ioBufferInflator.resetUses();

    // Cache inputs: BatchSize & CacheSize
    LOG(DEBUG) << "Finding max buffer size for Cache input";
    ioBufferInflator.useBatchSize().useCacheSize();
    ioBufferInflator.findMaxSizeScenario();
    for (const auto cacheInputIdx : getCacheInputIdxs()) {
        ioBufferInflator.inflate(this->getInput(cacheInputIdx));
    }
    ioBufferInflator.resetUses();

    // Embedding/Logits output: BatchSize & TokenSize
    LOG(DEBUG) << "Finding max buffer size for Embedding/Logits output";
    ioBufferInflator.useBatchSize().useTokenSize();
    ioBufferInflator.findMaxSizeScenario();
    ioBufferInflator.inflate(this->getOutput(0));
    ioBufferInflator.resetUses();

    // Cache outputs: BatchSize & CacheSize
    LOG(DEBUG) << "Finding max buffer size for Cache output";
    ioBufferInflator.useBatchSize().useCacheSize();
    ioBufferInflator.findMaxSizeScenario();
    for (const auto cacheOutputIdx : getCacheOutputIdxs()) {
        ioBufferInflator.inflate(this->getOutput(cacheOutputIdx));
    }
    ioBufferInflator.resetUses();
}

bool LlmExecutor::hotSwapModel(const size_t tokenSize, const size_t cacheSize) {
    DLOG_FUNC_LATENCY(ms)

    // Save old values
    const auto oldRuntimeIdx = this->getRuntimeIndex();
    const size_t oldNumInputToken = mModelTokenSize;

    auto mapHasKey = [](const auto& map, const auto& key) { return map.find(key) != map.end(); };

    if (!mapHasKey(mRuntimeIdxMap, tokenSize)) {
        LOG(ERROR) << "Model swap: No model with tokenSize=" << tokenSize << " is available";
        return false;
    }
    // Search for suitable runtime matching the requirements (token size & cache size)
    const auto& cacheSizeRuntimeMap = mRuntimeIdxMap[tokenSize];
    if (cacheSize != kUnusedSize && !mapHasKey(cacheSizeRuntimeMap, cacheSize)) {
        LOG(ERROR) << "Model swap: No model with tokenSize=" << tokenSize
                   << " has cacheSize=" << cacheSize;
        return false;
    }
    // Maintain the current (old) cache size if not specified in the argument
    const auto oldCacheSize = getCacheLength();
    size_t newCacheSize = (cacheSize == kUnusedSize) ? oldCacheSize : cacheSize;
    if (!mapHasKey(cacheSizeRuntimeMap, newCacheSize)) {
        const auto availableCacheSize = getNextAvailCacheSize(tokenSize);
        LOG(DEBUG) << "The cache size " << newCacheSize << " is not available when switching to "
                   << "token size " << tokenSize
                   << ". Selecting the first available cache size: " << availableCacheSize;
        newCacheSize = availableCacheSize;
    }

    const auto runtimeIdx = cacheSizeRuntimeMap.at(newCacheSize);
    if (runtimeIdx == oldRuntimeIdx) {
        LOG(DEBUG) << "Model swapping to itself.";
        return true;
    }

    this->selectRuntime(runtimeIdx);

    const auto newRuntimeIdx = this->getRuntimeIndex();
    if (oldRuntimeIdx == newRuntimeIdx) {
        LOG(WARN) << "Failed to switch to model with tokenSize=" << tokenSize
                  << " and cacheSize=" << cacheSize << ". Model currently remains at "
                  << "(tokenSize=" << oldNumInputToken << ", cacheSize=" << oldCacheSize
                  << "): " << this->getModelName();
        return false;
    }

    // Update model variables
    // Mask length = cache size (length) + num input token
    mModelTokenSize = tokenSize;

    // Update cache size
    if (oldCacheSize != newCacheSize) {
        LOG(DEBUG) << "Updating cache size from " << oldCacheSize << " to " << newCacheSize;
        reorderCacheInputs(newCacheSize); // Will read `mCacheLength` as current cache size
        mCacheLength = newCacheSize;
        for (auto& cacheShape : mCacheShapes) {
            DCHECK_LT(kCacheLengthDim, cacheShape.size());
            cacheShape[kCacheLengthDim] = newCacheSize;
        }
        mMaskBuilder->updateCacheLength(newCacheSize);
    }

    // Update batch size. Currently it is tied to the token size (via mBatchSizeMap).
    const auto oldBatchSize = this->mModelBatchSize;
    const auto newBatchSize = mBatchSizeMap[tokenSize];
    if (oldBatchSize != newBatchSize) {
        LOG(DEBUG) << "Updating batch size from " << oldBatchSize << " to " << newBatchSize;
        this->mModelBatchSize = newBatchSize;
        this->verifyBatchSize();
        // Update cacheShape batch dim (0)
        for (auto& cacheShape : mCacheShapes) {
            DCHECK_EQ(cacheShape.size(), 4);
            cacheShape[0] = newBatchSize;
        }
    }

    this->updateModelIO();
    this->registerRuntimeIO(); // Attach IO buffers to model runtime

    // If batch size increases, duplicate input caches
    if (newBatchSize > oldBatchSize) {
        inputCacheDupAllBatches();
    }

    // Rebuild mask because different token/cache size values will produce different mask shapes
    mMaskBuilder->markMaskDirty();

    // Update mask size
    const auto newMaskSizeBytes = this->getModelInputSizeBytes(getMaskInputIdx());
    mMaskBuilder->updateMaskSize(newMaskSizeBytes / newBatchSize); // Per batch

    return true;
}

size_t LlmExecutor::getNextAvailCacheSize(const size_t tokenSize) {
    CHECK(mRuntimeIdxMap.find(tokenSize) != mRuntimeIdxMap.end())
        << "The provided token size " << tokenSize << " is not valid.";
    const auto curCacheSize = getCacheLength();
    const auto& cacheSizeRuntimeMap = mRuntimeIdxMap[tokenSize];
    DCHECK(!cacheSizeRuntimeMap.empty());

    std::set<size_t> availableCacheSizes; // Ordered set
    for (const auto& [cacheSize, _] : cacheSizeRuntimeMap) {
        availableCacheSizes.insert(cacheSize);
    }
    DCHECK(!availableCacheSizes.empty());

    LOG(DEBUG) << "Available cache sizes for " << getModelTokenSize() << "t model: "
               << std::vector(availableCacheSizes.begin(), availableCacheSizes.end());

    // Return the min available cache size if even the max cache size is smaller than current one.
    // This happens when resetting to prompt mode.
    const size_t minCacheSize = *availableCacheSizes.cbegin();
    const size_t maxCacheSize = *availableCacheSizes.crbegin();
    if (maxCacheSize < curCacheSize) {
        return minCacheSize;
    }

    // Otherwise, return the next larger cache size that is larger than current one and large enough
    // for the next inference step.
    const size_t minRequiredCacheSize = getTokenIndex() + tokenSize;
    size_t nextLargerCacheSize = curCacheSize;
    for (const auto cacheSize : availableCacheSizes) {
        if (cacheSize > curCacheSize && cacheSize >= minRequiredCacheSize) {
            nextLargerCacheSize = cacheSize;
            break;
        }
    }
    return nextLargerCacheSize;
}

size_t LlmExecutor::getNextAvailCacheSize() {
    return getNextAvailCacheSize(getModelTokenSize());
}

void LlmExecutor::reorderCacheInputs(const size_t newCacheLength) {
    DLOG_FUNC_LATENCY(ms)

    const size_t curCacheLength = getCacheLength();
    if (curCacheLength == newCacheLength) {
        return; // Do nothing
    }
    if (curCacheLength > newCacheLength) {
        // NOTE: Assuming will reset cache after swapping to smaller cache size.
        LOG(DEBUG) << "Skip cache reordering to smaller cache size.";
        return;
    }

    const size_t numCacheInputs = kCacheInputIndexes.size();
    DCHECK_EQ(mCacheShapes.size(), numCacheInputs);

    auto reorderCache = [&](const size_t index) {
        auto& cacheInput = this->getInput(kCacheInputIndexes[index]);
        const auto numRows = getCacheNumRows(index);
        const auto strideSize = getCacheStrideSize();
        DCHECK_GE(numRows, 1);

        // Check size availability
        const size_t sizeRequired = numRows * newCacheLength * strideSize;
        const size_t sizeAllocated = cacheInput.sizeBytes;
        if (sizeAllocated < sizeRequired) {
            LOG(ERROR) << "New cache length of " << newCacheLength << " requires buffer size of "
                       << sizeRequired << " but only " << sizeAllocated << " is allocated.";
        }

        const auto cacheBuffer = reinterpret_cast<char*>(cacheInput.buffer);
        const auto numSeenToken = std::min(getTokenIndex(), curCacheLength);
        const size_t copySize = numSeenToken * strideSize;

        const size_t oldRowSize = curCacheLength * strideSize;
        const size_t newRowSize = newCacheLength * strideSize;
        DCHECK_GT(newCacheLength, curCacheLength);

        // Move in reverse order of rows to prevent overwriting data that has yet to move
        // because the destination address is always greter than source address.
        for (int64_t rowIdx = numRows - 1; rowIdx >= 0; rowIdx--) {
            const size_t oldOffset = oldRowSize - copySize;
            const size_t newOffset = newRowSize - copySize;
            const auto oldCacheBufferRow = cacheBuffer + rowIdx * oldRowSize;
            auto newCacheBufferRow = cacheBuffer + rowIdx * newRowSize;

            // Move cache data to new location
            const auto src = oldCacheBufferRow + oldOffset;
            auto dst = newCacheBufferRow + newOffset;
            std::memmove(dst, src, copySize);

            // Clear off data from original location
            auto clearStart = src;
            const auto clearEnd = std::min(src + copySize, dst);
            std::memset(clearStart, 0, clearEnd - clearStart);

            // Optional full zeroize approach, but will memset on more values
            // std::memset(newCacheBufferRow, 0, newOffset);
        }
    };

    BasicThreadPool threadPool;
    for (size_t i = 0; i < numCacheInputs; i++)
        threadPool.push(reorderCache, i);
    threadPool.joinAll();
}

void LlmExecutor::inputCacheDupAllBatches() {
    for (const auto cacheInputIdx : getCacheInputIdxs()) {
        this->inputDupAllBatches(cacheInputIdx);
    }
}

bool LlmExecutor::isFoldedGenBatchMode() const {
    return mGenBatchNumPromptTokens != 0;
}

void LlmExecutor::enterFoldedGenBatchMode() {
    if (mModelTokenSize == 1) {
        LOG(DEBUG) << "Ignore setting folded gen batch mode on 1t model.";
        return;
    }
    mGenBatchNumPromptTokens = getTokenIndex();
    mMaskBuilder->enterFoldedGenBatchMode(mGenBatchNumPromptTokens);
}

void LlmExecutor::linkCacheIOs() {
    const size_t numCacheIn = kCacheInputIndexes.size();
    for (size_t i = 0; i < numCacheIn; i++) {
        this->linkModelIO(kCacheInputIndexes[i], kCacheOutputIndexes[i]);
    }
}

void LlmExecutor::resetTokenIndex() {
    mMaskBuilder->reset();
    mGenBatchNumPromptTokens = 0;
    setTokenIndex(kInitTokenIndex);
}

void LlmExecutor::setTokenIndex(const size_t index) {
    const auto effectiveTokenIndex = getEffectiveTokenIndex(index);
    if (effectiveTokenIndex >= kMaxTokenLength) {
        LOG(FATAL) << "Attempting to set token index (" << effectiveTokenIndex
                   << ") exceeding the supported max token length (" << kMaxTokenLength << ")";
        return;
    }
    mCurrentTokenIndex = index;
}

void LlmExecutor::advanceTokenIndex() {
    setTokenIndex(mCurrentTokenIndex + mModelTokenSize);
}

size_t LlmExecutor::getTokenIndex() const {
    return mCurrentTokenIndex;
}

size_t LlmExecutor::getEffectiveTokenIndex() const {
    return getEffectiveTokenIndex(mCurrentTokenIndex);
}

size_t LlmExecutor::getEffectiveTokenIndex(const size_t tokenIndex) const {
    if (isFoldedGenBatchMode()) {
        // In folded gen batch mode
        CHECK_GE(tokenIndex, mGenBatchNumPromptTokens);
        const auto numGenTokens = tokenIndex - mGenBatchNumPromptTokens;
        CHECK_EQ(numGenTokens % mModelTokenSize, 0);
        const size_t decodingStep = numGenTokens / mModelTokenSize;
        return mGenBatchNumPromptTokens + decodingStep;
    }
    return tokenIndex;
}

int LlmExecutor::alignInputTokens(const size_t numInputToken) {
    int rollbackCount = mModelTokenSize - numInputToken;
    if (rollbackCount > 0) {
        CHECK_GE(mCurrentTokenIndex, rollbackCount) << "Total tok count < model input tok count";
        rollbackCache(rollbackCount);
        LOG(DEBUG) << "Tokens/Caches alignment rollback count = " << rollbackCount;

        // rollbackCache() requires original mCurrentTokenIndex value so only modify after the call
        mCurrentTokenIndex -= rollbackCount;

        // Rebuild mask as updateMask requires mCurrentTokenIndex to be monotonically increasing
        mMaskBuilder->markMaskDirty();
    }
    return rollbackCount;
}

void LlmExecutor::runInferencePrologue() {
    ExecutorBackend::runInferencePrologue();

    // NOTE: Padding need to be set before this is called
    updatePosEmbAndMask(mModelTokenSize);
}

void LlmExecutor::runInferenceEpilogue() {
    // Advance token index by the actual number that the model input requires.
    advanceTokenIndex();

    // Perform any necessary adjustments when padding is used
    paddingPostprocess();

    ExecutorBackend::runInferenceEpilogue();
}

// Also updates the token index
void LlmExecutor::updatePosEmbAndMask(const size_t numInputToken) {
    const auto effectiveTokenIndex = getEffectiveTokenIndex(mCurrentTokenIndex + numInputToken);
    if (effectiveTokenIndex > kMaxTokenLength) {
        LOG(FATAL) << "Attempting to generate tokens exceeding the supported max token length ("
                   << kMaxTokenLength << ")";
    }
    if (mCurrentTokenIndex > 0 && getLeftPadding() > 0) {
        LOG(FATAL) << "Left-padding is only allowed in the first prompt pass.";
    }
    mMaskBuilder->updateMask(mModelTokenSize, mCurrentTokenIndex, numInputToken);

    // Duplicate masks for all batches
    this->inputDupAllBatches(getMaskInputIdx());

    setPosEmbed(mCurrentTokenIndex);
    DLOG_FUNC_EXIT
}

void LlmExecutor::setPosEmbed(const size_t tokenIndex) {
    // Cut the array from master
    const auto effectiveTokenIndex = getEffectiveTokenIndex(tokenIndex);
    if (effectiveTokenIndex >= kMaxTokenLength) {
        LOG(FATAL) << "Attempting to set rotaty embedding using index exceeding the supported "
                   << "max token length (" << kMaxTokenLength << ")";
    }

    DLOG_FUNC_LATENCY(ms)

    const auto& rotEmbInputIdxes = getRotEmbInputIdxs();
    DCHECK_EQ(rotEmbInputIdxes.size(), kRotEmbInputCount);

    auto getRotEmbInputs = [&]() {
        std::vector<void*> rotEmbInputs(kRotEmbInputCount);
        for (size_t i = 0; i < kRotEmbInputCount; i++)
            rotEmbInputs[i] = this->getInputBuffer(rotEmbInputIdxes[i]);
        return rotEmbInputs;
    };
    if (isFoldedGenBatchMode()) {
        // In folded gen batch mode
        DCHECK_EQ(getLeftPadding(), 0);
        DCHECK_EQ(getRightPadding(), 0);

        // Get the actual token index
        const auto& realTokenIndex = effectiveTokenIndex;

        // Positions are all zeros (relative to current token index) because they are of the same
        // token position, just in different batches.
        const std::vector<size_t> positions(mModelTokenSize, 0);

        mRotEmbMasterLut->setEmbed(getRotEmbInputs(), realTokenIndex, positions);
    } else {
        mRotEmbMasterLut->setEmbed(
            getRotEmbInputs(), tokenIndex, mModelTokenSize, getLeftPadding(), getRightPadding());
    }

    // Duplicate for all batches
    for (const auto inputIdx : rotEmbInputIdxes)
        this->inputDupAllBatches(inputIdx);
}

size_t LlmExecutor::getLeftPadding() const {
    return (mPaddingMode == PaddingMode::LEFT) ? mCurrentPadSize : 0;
}

size_t LlmExecutor::getRightPadding() const {
    return (mPaddingMode == PaddingMode::RIGHT) ? mCurrentPadSize : 0;
}

void LlmExecutor::setLeftPadding(const size_t leftPadSize) {
    mCurrentPadSize = leftPadSize;
    mPaddingMode = PaddingMode::LEFT;

    // Notify mask builder about padding
    mMaskBuilder->notifyLeftPadding(leftPadSize);
}

void LlmExecutor::setRightPadding(const size_t rightPadSize) {
    mCurrentPadSize = rightPadSize;
    mPaddingMode = PaddingMode::RIGHT;

    // Notify mask builder about padding
    mMaskBuilder->notifyRightPadding(rightPadSize);
}

void LlmExecutor::paddingPostprocess() {
    if (mCurrentPadSize == 0) {
        return;
    }

    if (mPaddingMode == PaddingMode::RIGHT) {
        rightPaddingCachePostprocess();
    } else if (mPaddingMode == PaddingMode::LEFT) {
        leftPaddingCachePostprocess();
    }

    // Rollback by padding size
    setTokenIndex(mCurrentTokenIndex - mCurrentPadSize);

    // Reset padding size
    mCurrentPadSize = 0;
}

void LlmExecutor::leftPaddingCachePostprocess() {
    // NOTE: This part might not actually be needed

    // Stride size is same across caches
    const size_t strideSizeBytes = getCacheStrideSize();
    const size_t rowSize = getCacheLength() * strideSizeBytes;
    const size_t offset = (getCacheLength() - getModelTokenSize()) * strideSizeBytes;
    const size_t zeroCount = getLeftPadding() * strideSizeBytes;

    // Fill padded sections with zeros
    size_t cacheCounter = 0;
    for (const auto cacheInputIdx : getCacheInputIdxs()) {
        auto cacheBuffer = reinterpret_cast<char*>(this->getInputBuffer(cacheInputIdx));
        const size_t numRows = getCacheNumRows(cacheCounter++);
        for (size_t rowIdx = 0; rowIdx < numRows; rowIdx++) {
            auto cacheBufRow = cacheBuffer + rowIdx * rowSize; // Pointer pointing to start of row
            std::memset(cacheBufRow + offset, 0, zeroCount);
        }
    }
}

void LlmExecutor::rightPaddingCachePostprocess() {
    // advanceTokenIndex() has to be called first to set mCurrentTokenIndex for rollbackCache()
    rollbackCache(mCurrentPadSize);
}

size_t LlmExecutor::getCacheNumRows(const size_t index) const {
    CHECK_GT(mCacheShapes.size(), 0) << "Cache shapes have not been initialized.";
    CHECK_LT(index, mCacheShapes.size());
    const auto& cacheShape = mCacheShapes[index];
    // NOTE: cacheShape[0] is the batch dim
    return llm_helper::reduce_prod(cacheShape.begin(), cacheShape.begin() + kCacheLengthDim);
}

size_t LlmExecutor::getCacheStrideSize() const {
    CHECK_GT(mCacheShapes.size(), 0) << "Cache shapes have not been initialized.";
    const auto& cacheShape = mCacheShapes[0];
    return llm_helper::reduce_prod(
        cacheShape.begin() + kCacheLengthDim + 1, cacheShape.end(), kCacheTypeSize);
}

void LlmExecutor::initCache() {
    DLOG_FUNC_LATENCY(ms)
    resetTokenIndex();
    if (!kInitCacheFile) {
        // Use default zero initialization if no cache file provided
        for (const auto cacheIdx : getCacheInputIdxs()) {
            auto& inputCache = this->getInput(cacheIdx);
            char* cacheBuffer = reinterpret_cast<char*>(inputCache.buffer);
            const size_t cacheSizeBytes = inputCache.sizeBytes;
            std::memset(cacheBuffer, 0, cacheSizeBytes);
        }
        LOG(DEBUG) << "initCache: zero initialization";
        return;
    }

    LOG(DEBUG) << "initCache: precomputed cache initialization";

    if (!kInitCacheFile.valid()) {
        LOG(FATAL) << "Unable to load init cache file: " << kInitCacheFile;
    }
    const auto [cacheFileData, cacheFileSize] = kInitCacheFile.get();

    const auto& cacheInputIdxs = getCacheInputIdxs();
    DCHECK_EQ(cacheInputIdxs.size(), kCacheCount);
    for (size_t i = 0; i < kCacheCount; i++) {
        const auto cacheInputIdx = cacheInputIdxs[i];
        const auto cacheSizeBytes = this->getModelInputSizeBytes(cacheInputIdx);
        auto cacheBuffer = this->getInputBuffer(cacheInputIdx);
        std::memcpy(reinterpret_cast<char*>(cacheBuffer), cacheFileData, cacheFileSize);
        if (cacheFileSize != cacheSizeBytes) {
            LOG(WARN) << "Expected cache[" << i << "] size=" << cacheSizeBytes << ", but "
                      << "actual size read from file is " << cacheFileSize;
        }
    }
}

std::vector<char*> LlmExecutor::getCacheBuffers() {
    const auto& cacheInputIdxs = getCacheInputIdxs();
    const size_t numCacheInputs = cacheInputIdxs.size();
    std::vector<char*> cacheBuffers(numCacheInputs);
    for (size_t i = 0; i < numCacheInputs; i++) {
        cacheBuffers[i] = reinterpret_cast<char*>(this->getInputBuffer(cacheInputIdxs[i]));
    }
    return cacheBuffers;
}

void LlmExecutor::getCacheBuffersWithSize(std::vector<char*>& cacheBuffers,
                                          size_t& byteSizePerCache) {
    cacheBuffers = getCacheBuffers();
    byteSizePerCache = this->getModelInputSizeBytes(getCacheInputIdxs()[0]);
}

void LlmExecutor::rollbackCache(const size_t tokenCount) {
    if (tokenCount == 0) {
        return; // do nothing
    }
    DLOG_FUNC_LATENCY(ms)

    // View cache buffer of shape [..., mCacheLength, ...] as:
    //   [numRows, (mCacheLength, strideSizeBytes)]
    //    <----->  <----------------------------->
    //      row                   col

    const size_t strideSizeBytes = getCacheStrideSize();
    const size_t rowSize = mCacheLength * strideSizeBytes;
    const size_t firstNonEmptyIdx = mCacheLength - std::min(mCurrentTokenIndex, mCacheLength);

    auto cacheBuffers = getCacheBuffers();

    // Shift right and truncate tokenCount, then fill left with zeros
    size_t cacheCounter = 0;
    for (auto cacheBuffer : cacheBuffers) {
        const size_t numRows = getCacheNumRows(cacheCounter++);
        for (size_t rowIdx = 0; rowIdx < numRows; rowIdx++) {
            auto cacheBufRow = cacheBuffer + rowIdx * rowSize; // Pointer pointing to start of row
            // Copy from back until srcOffset reaches empty segment in the cache
            for (size_t tokenIdx = mCacheLength - 1; tokenIdx >= firstNonEmptyIdx + tokenCount;
                 tokenIdx--) {
                const size_t dstOffset = tokenIdx * strideSizeBytes;
                const size_t srcOffset = (tokenIdx - tokenCount) * strideSizeBytes;
                std::memcpy(cacheBufRow + dstOffset, cacheBufRow + srcOffset, strideSizeBytes);
            }
            const size_t offset = firstNonEmptyIdx * strideSizeBytes;
            const size_t zeroCount = tokenCount * strideSizeBytes;
            std::memset(cacheBufRow + offset, 0, zeroCount);
        }
    }
}

} // namespace mtk