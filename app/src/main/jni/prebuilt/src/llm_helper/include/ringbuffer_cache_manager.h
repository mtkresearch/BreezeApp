#pragma once

#include <vector>

namespace mtk::llm_helper {

class RingBufferCacheContext;

class RingBufferCacheManager {
public:
    using ShapeType = std::vector<size_t>;

public:
    explicit RingBufferCacheManager();

    ~RingBufferCacheManager();

    void initialize(const std::vector<ShapeType>& cacheShapes, const size_t cacheConcatDim,
                    const size_t cacheTypeSizeBytes, const size_t initTokenIndex,
                    const size_t maxTokenLength);

    void setIoCacheBuffers(const std::vector<void*>& inputCacheRingBuffers,
                           const std::vector<void*>& outputCacheBuffers);

public:
    // Query ring buffer overhead size after initialization
    size_t getOverheadSizeBytes() const;

    // Query the current ring buffer offset in bytes
    size_t getRingOffset() const;

    // Reset ring buffer offset to 0 without modifying the cache buffers
    void resetRingOffset();

    // Advance ring buffer offset by token count
    void advanceRingOffset(const size_t tokenCount);

    // Ensure the ring buffer has enough space to update/append by calling resetRingBuffer if needed
    void ensureHasSpaceToUpdate(const size_t modelTokenSize, const size_t padLength,
                                const bool isCacheEmpty);

    // Calculate the write offset of the ring buffer in bytes
    size_t getWriteOffset(const size_t modelTokenSize, const size_t cacheLength,
                          const size_t padLength, const bool isCacheEmpty) const;

    // Append output cache to input cache ring bfufer
    void appendInOutCaches(const size_t modelBatchSize, const size_t modelTokenSize,
                           const size_t cacheLength, const size_t leftPadLength,
                           const size_t rightPadLength, const bool isCacheEmpty);

    // Ring buffer reset to start from top again
    void resetRingBuffer();

    // Returns true if rollback ring buffer is successful, false if otherwise.
    bool rollback(const size_t tokenCount, const size_t modelBatchSize, const size_t cacheLength);

private:
    void ensureInit() const;

private:
    RingBufferCacheContext* mCtx = nullptr;

    bool mIsInitialized = false;
};

} // namespace mtk::llm_helper