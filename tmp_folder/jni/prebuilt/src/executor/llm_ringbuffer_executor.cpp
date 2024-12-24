#include "executor/llm_ringbuffer_executor.h"

#include "backend/backend.h"
#include "common/logging.h"
#include "common/scope_profiling.h"
#include "executor/io_buffer_inflator.h"
#include "llm_helper/include/ringbuffer_cache_manager.h"

#include <algorithm>
#include <cmath>
#include <string>
#include <vector>

namespace mtk {

using mtk::llm_helper::RingBufferCacheManager;

void LlmRingBufferExecutor::runInferencePrologue() {
    // NOTE: Set offset has to happen before inferenceEnqueue is called
    setOffsetedCacheInputs();

    if (mCacheUpdateMode == CacheUpdateMode::Inplace) {
        setOffsetedCacheOutputs();
    }

    LlmExecutor::runInferencePrologue();
}

void LlmRingBufferExecutor::runInferenceEpilogue() {
    // Append cache ring buffer
    const bool isCacheEmpty = (this->mCurrentTokenIndex == 0);

    if (mCacheUpdateMode == CacheUpdateMode::Copy) {
        // Manually append KV cache via memory copy
        mRingBufferCacheManager.appendInOutCaches(this->getBatchSize(), this->getModelTokenSize(),
                                                  this->getCacheLength(), this->getLeftPadding(),
                                                  this->getRightPadding(), isCacheEmpty);
    }

    // Advance ring buffer offsets by copy size. Also, skip offset for the first pass.
    if (!isCacheEmpty) {
        mRingBufferCacheManager.advanceRingOffset(this->getValidModelNumInputToken());
    }

    LlmExecutor::runInferenceEpilogue();
}

void LlmRingBufferExecutor::initCache() {
    LlmExecutor::initCache();
    mRingBufferCacheManager.resetRingOffset();
}

void LlmRingBufferExecutor::setOffsetedCacheInputs() {
    DLOG_FUNC_LATENCY(ms)
    const auto ringOffsetBytes = mRingBufferCacheManager.getRingOffset();
    for (const auto i : this->getCacheInputIdxs()) {
        this->setRuntimeOffsetedInput(i, ringOffsetBytes);
    }
}

void LlmRingBufferExecutor::setOffsetedCacheOutputs() {
    DLOG_FUNC_LATENCY(ms)
    DCHECK(mCacheUpdateMode == CacheUpdateMode::Inplace)
        << "setOffsetedCacheOutputs is only used for 'inplace' cache update mode";

    const auto numSeenToken = this->mCurrentTokenIndex;
    const auto cacheLength = this->getCacheLength();
    const auto modelTokenSize = this->getModelTokenSize();
    const auto rightPadSize = this->getRightPadding();
    const bool isCacheEmpty = (numSeenToken == 0);

    // Can ignore left pad because it caon only happen when cache is empty
    const size_t numValidInputTokens = modelTokenSize - rightPadSize;
    const size_t cacheEmptyTokenSpace = cacheLength - numSeenToken;

    // NOTE: If it needs right padding, update write will overflow the right padded regions to the
    // beginning of the next rows, and might overwrite valid cache values.
    if (numValidInputTokens <= cacheEmptyTokenSpace && modelTokenSize > cacheEmptyTokenSpace) {
        LOG(WARN) << "Right padded tokens will overwrite existing cache values.";
    }

    // Ensure it has enough space to update to prevent writing past the ring buffer.
    // Ignore padLength because the actual write size can't be changed to skip the padded regions.
    mRingBufferCacheManager.ensureHasSpaceToUpdate(modelTokenSize, 0, isCacheEmpty);

    // Ignore left padding because we can't skip writing the left padded region.
    // Right padded region is flushed to the beginning of the next row.
    const auto writeOffset = mRingBufferCacheManager.getWriteOffset(
        modelTokenSize, cacheLength, rightPadSize, isCacheEmpty);
    for (const auto i : this->getCacheOutputIdxs()) {
        this->setRuntimeOffsetedOutput(i, writeOffset);
    }
}

void LlmRingBufferExecutor::linkCacheIOs() {
    DCHECK(mCacheUpdateMode != CacheUpdateMode::Undefined);
    if (mCacheUpdateMode == CacheUpdateMode::Inplace) {
        LOG(DEBUG) << "Linking cache IOs for 'inplace' cache update mode";
        LlmExecutor::linkCacheIOs();
    }
}

void LlmRingBufferExecutor::preInitBufferProcess() {
    if (mDoneInitRingBuffer) {
        // Do nothing, especially after model swap.
        return;
    }

    LlmExecutor::preInitBufferProcess();

    // Prepare cache shapes
    std::vector<RingBufferCacheManager::ShapeType> cacheShapesForRB;
    for (const auto& cacheShape : mCacheShapes) {
        cacheShapesForRB.emplace_back(cacheShape.begin(), cacheShape.end());
    }

    mRingBufferCacheManager.initialize(cacheShapesForRB, kRingConcatDim, this->kCacheTypeSize,
                                       this->kInitTokenIndex, this->kMaxTokenLength);

    // Expand the required size for each inputs
    const auto ringBufferOverheadSizeBytes = mRingBufferCacheManager.getOverheadSizeBytes();
    DCHECK_GT(ringBufferOverheadSizeBytes, 0);
    for (const auto cacheIdx : this->getCacheInputIdxs()) {
        this->getInput(cacheIdx).sizeBytes += ringBufferOverheadSizeBytes;
    }

    DCHECK(mCacheUpdateMode != CacheUpdateMode::Undefined);
    if (mCacheUpdateMode == CacheUpdateMode::Inplace) {
        // Cache IOs will link in 'inplace' cache update mode, so we need to align their sizes.
        for (const auto cacheIdx : this->getCacheOutputIdxs()) {
            this->getOutput(cacheIdx).sizeBytes += ringBufferOverheadSizeBytes;
        }
    }

    mDoneInitRingBuffer = true;
}

void LlmRingBufferExecutor::postInitBufferProcess() {
    // Prepare input/output cache buffers
    const auto inputCacheIdxes = this->getCacheInputIdxs();
    const auto outputCacheIdxes = this->getCacheOutputIdxs();
    DCHECK_EQ(inputCacheIdxes.size(), inputCacheIdxes.size());
    const auto numCaches = inputCacheIdxes.size();
    std::vector<void*> inputCaches(numCaches), outputCaches(numCaches);
    for (size_t i = 0; i < numCaches; i++) {
        inputCaches[i] = this->getInputBuffer(inputCacheIdxes[i]);
        outputCaches[i] = this->getOutputBuffer(outputCacheIdxes[i]);
    }
    mRingBufferCacheManager.setIoCacheBuffers(inputCaches, outputCaches);
}

void LlmRingBufferExecutor::inputCacheDupAllBatches() {
    const auto modelBatchSize = this->getBatchSize();
    const auto ringOffset = mRingBufferCacheManager.getRingOffset();
    for (const auto inputIdx : this->getCacheInputIdxs()) {
        const auto sizePerBatch = this->getModelInputSizeBytes(inputIdx) / modelBatchSize;
        auto cacheBuffer = reinterpret_cast<char*>(this->getInputBuffer(inputIdx)) + ringOffset;
        for (size_t batch = 1; batch < modelBatchSize; batch++) {
            const size_t offset = sizePerBatch * batch;
            std::memcpy(cacheBuffer + offset, cacheBuffer, sizePerBatch);
        }
    }
}

void LlmRingBufferExecutor::reorderCacheInputs(const size_t newCacheLength) {
    const size_t oldCacheLength = this->getCacheLength();
    if (oldCacheLength == newCacheLength) {
        return; // Do nothing
    }
    if (oldCacheLength > newCacheLength) {
        // NOTE: Assuming will reset cache after swapping to smaller cache size.
        LOG(DEBUG) << "Skip cache reordering to smaller cache size.";
        return;
    }

    const auto ringOffset = mRingBufferCacheManager.getRingOffset();

    // Add ringbuffer offset to cache input buffer addresses
    for (const auto inputIdx : this->getCacheInputIdxs()) {
        auto& cacheInput = this->getInput(inputIdx);
        cacheInput.buffer = reinterpret_cast<char*>(cacheInput.buffer) + ringOffset;
    }

    // Reorder on offseted cache input buffers
    LlmExecutor::reorderCacheInputs(newCacheLength);

    // Restore original cache input buffer addresses
    for (const auto inputIdx : this->getCacheInputIdxs()) {
        auto& cacheInput = this->getInput(inputIdx);
        cacheInput.buffer = reinterpret_cast<char*>(cacheInput.buffer) - ringOffset;
    }
}

void LlmRingBufferExecutor::initCacheUpdateMode() {
    // This needs to be called before calling linkCacheIOs() and before assignBufferSizesToMax()
    // attempts to adjust output cache buffer allocation sizes for ring buffer append.

    DCHECK_NE(getNumRuntimes(), 0) << "Runtime is not initialized yet.";

    // Use 'inplace' cache update mode if all cache IO pair share the same shape
    bool hasSameShape = false;
    bool hasDiffShape = false;

    const auto& cacheInputIdxs = this->getCacheInputIdxs();
    const auto& cacheOutputIdxs = this->getCacheOutputIdxs();
    const auto numInputCaches = cacheInputIdxs.size();
    const auto numOutputCaches = cacheOutputIdxs.size();
    DCHECK_EQ(numInputCaches, numOutputCaches);

    ShapeType inputCacheShape, outputCacheShape;
    for (size_t i = 0; i < numOutputCaches; i++) {
        this->getRuntimeInputShape(cacheInputIdxs[i], inputCacheShape.data());
        this->getRuntimeOutputShape(cacheOutputIdxs[i], outputCacheShape.data());
        if (inputCacheShape == outputCacheShape)
            hasSameShape = true;
        else
            hasDiffShape = true;
    }

    CHECK(!hasSameShape || !hasDiffShape)
        << "Invalid cache IO shapes. Either every corresponding KV cache input/output pairs have "
           "the same shape or have different shapes.";

    // Here `hasSameShape` is equivalent to "allSameShape", indicating whether all cache IO pairs
    // have the same shape.
    mCacheUpdateMode = hasSameShape ? CacheUpdateMode::Inplace : CacheUpdateMode::Copy;

    LOG(DEBUG) << "KV cache update mode: " << (hasSameShape ? "Inplace" : "Copy");
}

void LlmRingBufferExecutor::assignBufferSizesToMax() {
    LlmExecutor::assignBufferSizesToMax();

    initCacheUpdateMode();

    if (mCacheUpdateMode == CacheUpdateMode::Inplace) {
        // Cache output will link to cache input in inplace update mode
        return;
    }

    DCHECK(mCacheUpdateMode == CacheUpdateMode::Copy);
    LOG(DEBUG) << "Overwriting allocation sizes for output caches for 'copy' update mode.";

    // Ringbuffer cache outputs that use 'copy' update depend on TokenSize instead of CacheSize
    const auto curBatchSize = this->getBatchSize();
    const auto curTokenSize = this->getModelTokenSize();
    const auto curCacheSize = this->getCacheLength();

    IoBufferInflator ioBufferInflator(
        this->kRuntimeInfos, curBatchSize, curTokenSize, curCacheSize);

    // Cache outputs: BatchSize & TokenSize
    ioBufferInflator.useBatchSize().useTokenSize();
    ioBufferInflator.findMaxSizeScenario();
    for (const auto cacheOutputIdx : this->getCacheOutputIdxs()) {
        ioBufferInflator.inflate(this->getOutput(cacheOutputIdx));
    }
    ioBufferInflator.resetUses();
}

void LlmRingBufferExecutor::rollbackCache(const size_t tokenCount) {
    const auto modelBatchSize = this->getBatchSize();
    const auto cacheLength = this->getCacheLength();
    if (!mRingBufferCacheManager.rollback(tokenCount, modelBatchSize, cacheLength)) {
        // Fallback to use the naive rollbackCache approach.
        LlmExecutor::rollbackCache(tokenCount);
    }
}

std::vector<char*> LlmRingBufferExecutor::getCacheBuffers() {
    const auto& inputCacheIdxs = this->getCacheInputIdxs();
    const size_t numInputCaches = inputCacheIdxs.size();
    std::vector<char*> cacheBuffers(numInputCaches);
    const auto ringOffsetBytes = mRingBufferCacheManager.getRingOffset();
    for (size_t i = 0; i < numInputCaches; i++) {
        const auto inputCacheIdx = inputCacheIdxs[i];
        const auto cacheRingBuffer = reinterpret_cast<char*>(this->getInputBuffer(inputCacheIdx));
        cacheBuffers[i] = cacheRingBuffer + ringOffsetBytes;
    }
    return cacheBuffers;
}

} // namespace mtk