#include "llm_helper/include/ringbuffer_cache_manager.h"

#include "common/logging.h"
#include "llm_helper/include/utils.h"

#include <vector>

#define NO_EXPORT __attribute__((visibility("hidden")))

namespace mtk::llm_helper {

class NO_EXPORT RingBufferCacheContext {
public:
    explicit RingBufferCacheContext(const std::vector<size_t>& modelInputCacheSizesBytes,
                                    const std::vector<size_t>& cachesNumRowsPerBatch,
                                    const size_t strideSizeBytes, const size_t overheadSizeBytes)
        : kModelInputCacheSizesBytes(modelInputCacheSizesBytes),
          kCachesNumRowsPerBatch(cachesNumRowsPerBatch),
          kStrideSizeBytes(strideSizeBytes),
          kOverheadSizeBytes(overheadSizeBytes) {
        DCHECK_EQ(kModelInputCacheSizesBytes.size(), kCachesNumRowsPerBatch.size());
    }

    ~RingBufferCacheContext() {}

    void setIoCacheBuffers(const std::vector<void*>& inputCacheRingBuffers,
                           const std::vector<void*>& outputCacheBuffers) {
        DCHECK_EQ(inputCacheRingBuffers.size(), outputCacheBuffers.size());
        DCHECK_EQ(inputCacheRingBuffers.size(), kCachesNumRowsPerBatch.size());
        mInputCacheRingBuffers = inputCacheRingBuffers;
        mOutputCacheBuffers = outputCacheBuffers;
    }

    // Get cache append stride size in bytes
    size_t getStrideSize() const { return kStrideSizeBytes; }

    // Get input ringbuffer cache row size in bytes
    size_t getRowSize(const size_t cacheLength) const { return cacheLength * getStrideSize(); }

    // Get output cache row size in bytes
    size_t getOutCacheRowSize(const size_t modelTokenSize) const {
        return modelTokenSize * getStrideSize();
    }

    // Get copy size in bytes
    size_t getCopySize(const size_t modelTokenSize, const size_t padLength) const {
        DCHECK_GE(modelTokenSize, padLength);
        return (modelTokenSize - padLength) * getStrideSize();
    }

    size_t getOverheadSizeBytes() const { return kOverheadSizeBytes; }

    size_t getRingOffset() const { return mRingBufferOffsetBytes; }

    size_t getNumRows(const size_t index, const size_t batchSize) const {
        return kCachesNumRowsPerBatch[index] * batchSize;
    }

    size_t getNumCaches() const { return mInputCacheRingBuffers.size(); }

    void setRingOffset(const size_t offsetBytes) { mRingBufferOffsetBytes = offsetBytes; }

    void addRingOffset(const size_t sizeBytes) { mRingBufferOffsetBytes += sizeBytes; }

    size_t getModelInputCacheSizeBytes(const size_t index) const {
        return kModelInputCacheSizesBytes[index];
    }

    char* getInputCacheRingBuffer(const size_t index) {
        return reinterpret_cast<char*>(mInputCacheRingBuffers[index]);
    }

    char* getOutputCacheBuffer(const size_t index) {
        return reinterpret_cast<char*>(mOutputCacheBuffers[index]);
    }

private:
    // Constants

    // The input cache size that the model actually sees
    const std::vector<size_t> kModelInputCacheSizesBytes;

    // Applies for both input and output caches
    const std::vector<size_t> kCachesNumRowsPerBatch;

    const size_t kStrideSizeBytes;
    const size_t kOverheadSizeBytes;

    // Variables
    size_t mRingBufferOffsetBytes = 0;

    std::vector<void*> mInputCacheRingBuffers;
    std::vector<void*> mOutputCacheBuffers;
};

RingBufferCacheManager::RingBufferCacheManager() {}

RingBufferCacheManager::~RingBufferCacheManager() {
    delete mCtx;
}

void RingBufferCacheManager::initialize(const std::vector<ShapeType>& cacheShapes,
                                        const size_t cacheConcatDim,
                                        const size_t cacheTypeSizeBytes,
                                        const size_t initTokenIndex, const size_t maxTokenLength) {
    DCHECK_GT(cacheShapes.size(), 0);

    // Get cache length, assume same for all caches
    const auto& firstCacheShape = cacheShapes[0];
    DCHECK_LT(cacheConcatDim, firstCacheShape.size());

    // Compute size of each cache input used by the model
    std::vector<size_t> inputCacheSizesBytes;
    for (const auto& cacheShape : cacheShapes) {
        const auto cacheSizeBytes = reduce_prod(cacheShape, cacheTypeSizeBytes);
        inputCacheSizesBytes.push_back(cacheSizeBytes);
    }

    // Compute stride size, assume same for all caches
    const size_t strideSizeBytes = reduce_prod(
        firstCacheShape.begin() + cacheConcatDim + 1, firstCacheShape.end(), cacheTypeSizeBytes);

    // Compute num rows (per-batch) for each cache
    std::vector<size_t> cachesNumRowsPerBatch;
    for (const auto& cacheShape : cacheShapes) {
        // NOTE: Ignore the batch dim (0) when calculating per-batch num rows
        const auto numRows =
            reduce_prod(cacheShape.begin() + 1, cacheShape.begin() + cacheConcatDim);
        cachesNumRowsPerBatch.push_back(numRows);
    }

    // Compute overhead size, assume same for all caches
    const size_t firstRowUsage = std::max(1UL, initTokenIndex);
    const size_t overheadSizeBytes = (maxTokenLength - firstRowUsage) * strideSizeBytes;

    DCHECK_EQ(mCtx, nullptr);
    mCtx = new RingBufferCacheContext(
        inputCacheSizesBytes, cachesNumRowsPerBatch, strideSizeBytes, overheadSizeBytes);
    mIsInitialized = true;
}

void RingBufferCacheManager::setIoCacheBuffers(const std::vector<void*>& inputCacheRingBuffers,
                                               const std::vector<void*>& outputCacheBuffers) {
    DCHECK_EQ(inputCacheRingBuffers.size(), outputCacheBuffers.size());
    mCtx->setIoCacheBuffers(inputCacheRingBuffers, outputCacheBuffers);
}

size_t RingBufferCacheManager::getOverheadSizeBytes() const {
    return mCtx->getOverheadSizeBytes();
}

// Ring offset public interface
size_t RingBufferCacheManager::getRingOffset() const {
    ensureInit();
    const auto ringBufferOffsetBytes = mCtx->getRingOffset();
    DCHECK_LE(ringBufferOffsetBytes, mCtx->getOverheadSizeBytes());
    return ringBufferOffsetBytes;
}

void RingBufferCacheManager::resetRingOffset() {
    ensureInit();
    mCtx->setRingOffset(0);
}

void RingBufferCacheManager::advanceRingOffset(const size_t tokenCount) {
    ensureInit();
    mCtx->addRingOffset(tokenCount * mCtx->getStrideSize());
    CHECK_LE(mCtx->getRingOffset(), mCtx->getOverheadSizeBytes()) << "Ring buffer offset overflow.";
}

void RingBufferCacheManager::ensureHasSpaceToUpdate(const size_t modelTokenSize,
                                                    const size_t padLength,
                                                    const bool isCacheEmpty) {
    if (isCacheEmpty) {
        return;
    }
    const auto copySizeBytes = mCtx->getCopySize(modelTokenSize, padLength);
    const bool hasEnoughSpaceToAppend = [&]() {
        const auto extraNeededSizeBytes = mCtx->getRingOffset() + copySizeBytes;
        return extraNeededSizeBytes <= mCtx->getOverheadSizeBytes();
    }();
    if (!hasEnoughSpaceToAppend) {
        resetRingBuffer(); // Will change the output of getRingOffset()
    }
}

size_t RingBufferCacheManager::getWriteOffset(const size_t modelTokenSize, const size_t cacheLength,
                                              const size_t padLength,
                                              const bool isCacheEmpty) const {
    const auto copySizeBytes = mCtx->getCopySize(modelTokenSize, padLength);
    const auto icRowSizeBytes = mCtx->getRowSize(cacheLength); // Input cache row size
    return isCacheEmpty ? icRowSizeBytes - copySizeBytes // Fill the end of row (aka last N cols)
                        : icRowSizeBytes + mCtx->getRingOffset();
}

// Ring buffer append
void RingBufferCacheManager::appendInOutCaches(const size_t modelBatchSize,
                                               const size_t modelTokenSize,
                                               const size_t cacheLength, const size_t leftPadLength,
                                               const size_t rightPadLength,
                                               const bool isCacheEmpty) {
    // View cache buffer of shape [..., ringConcatDim, ...] as:
    //   [mNumRows, (ringConcatDim, strideSize)]
    //    <------>  <------------------------->
    //       row                col
    // Write strideSize number of values, then jump by rowSize.
    //
    // If init from zero, it will append to the last col of the first row and ring buffer offset
    // will remain at 0. Otherwise, it will start appending from the second row onwards and
    // ring buffer offset will begin to take effect.

    ensureInit();

    const size_t padLength = leftPadLength + rightPadLength;
    const auto copySizeBytes =
        mCtx->getCopySize(modelTokenSize, padLength); // Padding is exclulded from copy
    ensureHasSpaceToUpdate(modelTokenSize, padLength, isCacheEmpty);

    // Append ring buffer
    const auto icRowSizeBytes = mCtx->getRowSize(cacheLength);            // Input cache row size
    const auto ocRowSizeBytes = mCtx->getOutCacheRowSize(modelTokenSize); // Output cache row size
    const auto padOffset = leftPadLength * mCtx->getStrideSize();
    const size_t startOffset =
        isCacheEmpty ? icRowSizeBytes - copySizeBytes // Fill the end of row (aka last N cols)
                     : icRowSizeBytes + mCtx->getRingOffset();

    auto appendSingleRBCache = [&](const size_t index) {
        auto inputCacheBuffer = mCtx->getInputCacheRingBuffer(index) + startOffset;
        const auto outputCacheBuffer = mCtx->getOutputCacheBuffer(index);
        const auto numRows = mCtx->getNumRows(index, modelBatchSize);
        for (size_t rowIdx = 0; rowIdx < numRows; rowIdx++) {
            std::memcpy(inputCacheBuffer + rowIdx * icRowSizeBytes,
                        outputCacheBuffer + rowIdx * ocRowSizeBytes + padOffset, copySizeBytes);
        }
    };
    for (size_t i = 0; i < mCtx->getNumCaches(); i++) {
        appendSingleRBCache(i);
    }
}

void RingBufferCacheManager::resetRingBuffer() {
    const auto ringOffsetBytes = mCtx->getRingOffset();
    if (ringOffsetBytes == 0) {
        return; // No need to reset
    }
    for (size_t i = 0; i < mCtx->getNumCaches(); i++) {
        auto cacheRingBuffer = mCtx->getInputCacheRingBuffer(i);
        const auto inputCacheSizeBytes = mCtx->getModelInputCacheSizeBytes(i);
        std::memcpy(cacheRingBuffer, cacheRingBuffer + ringOffsetBytes, inputCacheSizeBytes);
    }
    resetRingOffset(); // Reset ring buffer offsets to 0
    LOG(DEBUG) << "Done reset ring buffer";
}

// Returns true if rollback ring buffer is successful, false if otherwise.
bool RingBufferCacheManager::rollback(const size_t tokenCount, const size_t modelBatchSize,
                                      const size_t cacheLength) {
    ensureInit();
    const size_t rollbackSizeBytes = tokenCount * mCtx->getStrideSize();
    const auto ringOffsetBytes = mCtx->getRingOffset();

    // Rollback size is greater than the current ring offset
    if (ringOffsetBytes < rollbackSizeBytes) {
        return false;
    }

    const auto rowSizeBytes = mCtx->getRowSize(cacheLength);
    const size_t startClearOffset = ringOffsetBytes - rollbackSizeBytes;
    for (size_t i = 0; i < mCtx->getNumCaches(); i++) {
        auto inputCacheBuffer = mCtx->getInputCacheRingBuffer(i);
        const auto numRows = mCtx->getNumRows(i, modelBatchSize);
        for (size_t rowIdx = 0; rowIdx < numRows; rowIdx++) {
            auto curInputCacheBuffer = inputCacheBuffer + rowIdx * rowSizeBytes;
            std::memset(curInputCacheBuffer + startClearOffset, 0, rollbackSizeBytes);
        }
    }
    mCtx->addRingOffset(-rollbackSizeBytes);
    return true;
}

void RingBufferCacheManager::ensureInit() const {
    CHECK(mIsInitialized) << "Attempting to use RingBufferCacheManager without initialization.";
    CHECK_GT(mCtx->getNumCaches(), 0)
        << "Attempting to use RingBufferCacheManager without any cache buffers.";
}

} // namespace mtk::llm_helper