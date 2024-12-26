#pragma once

#include "executor/llm_executor.h"
#include "llm_helper/include/ringbuffer_cache_manager.h"

#include <string>
#include <vector>

namespace mtk {

// Use ring buffer for caches.
class LlmRingBufferExecutor : public LlmExecutor {
private:
    static constexpr size_t kRingConcatDim = LlmExecutor::kCacheLengthDim;

public:
    // Inherit parent class constructor
    using LlmExecutor::LlmExecutor;

    virtual void runInferencePrologue() override;

    virtual void runInferenceEpilogue() override;

    // For cache reset usage
    virtual void initCache() override;

private:
    // Only link the cache IOs if cache update mode is 'inplace'
    virtual void linkCacheIOs() override;

    // Init cache update mode based on the model cache IO shapes
    void initCacheUpdateMode();

    // Call Neuron API to read inputs with offsets
    void setOffsetedCacheInputs();

    // Call Neuron API to write outputs with offsets. Only used for 'inplace' cache update mode
    void setOffsetedCacheOutputs();

    // Initialize ring buffer related variables and constants
    virtual void preInitBufferProcess() override;
    virtual void postInitBufferProcess() override;

    // Set ring buffer cache outputs to their possible max size
    virtual void assignBufferSizesToMax() override;

    // Duplicate input caches to all batches during model swap to larger batch size
    virtual void inputCacheDupAllBatches() override;

    // In-place reshape cache inputs from [..., oldCacheLength, ...] to [..., newCacheLength, ...]
    virtual void reorderCacheInputs(const size_t newCacheLength) override;

    // Cache post-processing is not needed due to the padding-aware ring append
    virtual void leftPaddingCachePostprocess() override {}  // Do nothing
    virtual void rightPaddingCachePostprocess() override {} // Do nothing

    virtual void rollbackCache(const size_t tokenCount) override;

protected:
    // Return offseted cache buffers
    virtual std::vector<char*> getCacheBuffers() override;

private:
    using LlmExecutor::mCacheShapes;

    llm_helper::RingBufferCacheManager mRingBufferCacheManager;

    bool mDoneInitRingBuffer = false;

    enum class CacheUpdateMode {
        Undefined = 0,
        Copy,
        Inplace
    };
    CacheUpdateMode mCacheUpdateMode = CacheUpdateMode::Undefined;
};

} // namespace mtk