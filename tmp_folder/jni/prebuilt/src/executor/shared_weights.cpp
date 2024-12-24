#include "common/file_source.h"
#include "common/thread_pool.h"
#include "executor/allocator.h"
#include "executor/neuron_executor.h"
#include "executor/neuron_usdk_executor.h"

#include <vector>

namespace mtk {

#ifdef USE_USDK_BACKEND
using ExecutorBackend = NeuronUsdkExecutor;
#else
using ExecutorBackend = NeuronExecutor;
#endif

using MemoryAllocator = ExecutorBackend::MemoryAllocator;

//===------------------------===//
// SharedWeights (Per DLA)
//===------------------------===//

SharedWeights::operator bool() const {
    return !empty();
}

bool SharedWeights::empty() const {
    return size() == 0;
}

size_t SharedWeights::size() const {
    const auto numSwFiles = files.size();
    const auto numSwBuffers = buffers.size();
    DCHECK(numSwBuffers == 0 || numSwBuffers == numSwFiles);
    return numSwFiles;
}

bool SharedWeights::isPreloaded() const {
    return !buffers.empty();
}

//===------------------------===//
// SharedWeightsHandle (Global)
//===------------------------===//

SharedWeightsHandle::~SharedWeightsHandle() {
    wait(); // Ensure the thread pool is clear
    if (mAllocator)
        mAllocator->releaseAll();
}

void SharedWeightsHandle::preload(const bool async) {
    if (!mSharedWeightsBuffers.empty() || !mThreadPool.empty() || kSharedWeightsFiles.empty()) {
        return; // Already preloaded (or preloading) or no shared weights to use
    }

    LOG(DEBUG) << "Preloading shared weights" << (async ? " with async" : "");

    DCHECK(!mAllocator);
    mAllocator = std::make_unique<MemoryAllocator>();

    const auto numSwFiles = kSharedWeightsFiles.size();
    mSharedWeightsBuffers.resize(numSwFiles);

    auto allocForSharedWeights = [this](const size_t swIndex) {
        auto& swBuffer = mSharedWeightsBuffers[swIndex];
        DCHECK(!swBuffer.isAllocated());
        const auto swSize = kSharedWeightsFiles[swIndex].getSize();
        swBuffer = mAllocator->allocate(swSize);
    };

    // Load single shared weights, and allocate buffer for it if needed.
    auto loadSharedWeights = [this](const size_t swIndex) {
        const auto [swData, swSize] = kSharedWeightsFiles[swIndex].get();
        auto& swBuffer = mSharedWeightsBuffers[swIndex];
        if (!swBuffer) {
            swBuffer = mAllocator->allocate(swSize);
        }
        std::memcpy(swBuffer.buffer, swData, swSize);
    };

    auto loadAllSequentially = [=] {
        for (size_t swIndex = 0; swIndex < numSwFiles; swIndex++) {
            loadSharedWeights(swIndex);
        }
    };

    auto loadAllConcurrently = [=] {
        for (size_t swIndex = 0; swIndex < numSwFiles; swIndex++) {
            mThreadPool.push(loadSharedWeights, swIndex);
        }
    };

    // If async is true, wait for buffer allocations then perform memcpy in the background.
    // Otherwise, perform all buffer allocations and memcpy before return.
    if (async) {
        // Allocate shared weights buffers
        for (size_t swIndex = 0; swIndex < numSwFiles; swIndex++) {
            mThreadPool.push(allocForSharedWeights, swIndex);
        }
        mThreadPool.joinAll(); // Wait for buffer allocations only
        // Load shared weights sequentially in the background.
        // Sequential loading is faster in async mode because DRAM load is lower.
        mThreadPool.push(loadAllSequentially);
    } else {
        // Load all shared weights concurrently before return, i.e. need to join before return.
        // Faster in non-async mode, but causes higher DRAM load, making async model loading slower.
        loadAllConcurrently();
        mThreadPool.joinAll();
    }
}

bool SharedWeightsHandle::loaded() const {
    return (!mSharedWeightsBuffers.empty() && mThreadPool.empty());
}

void SharedWeightsHandle::wait() const {
    mThreadPool.joinAll();
}

SharedWeights SharedWeightsHandle::getSharedWeights(const size_t dlaChunkIndex) const {
    CHECK_LT(dlaChunkIndex, kNumDlaChunks);

    const auto numSwFiles = kSharedWeightsFiles.size();
    CHECK_EQ(numSwFiles % kNumDlaChunks, 0)
        << "The number of shared weights files used per DLA must be same for all DLA files.";

    const bool preloaded = !mSharedWeightsBuffers.empty();

    SharedWeights swChunk;
    const auto numSwPerDla = numSwFiles / kNumDlaChunks;
    const size_t startSwIdx = dlaChunkIndex * numSwPerDla;

    auto& swChunkFiles = swChunk.files; // Always assigned regardless preloaded or not
    auto& swChunkBuffers = swChunk.buffers;

    swChunkFiles.reserve(numSwPerDla);

    if (preloaded) {
        swChunkBuffers.reserve(numSwPerDla);
    }

    for (size_t i = startSwIdx; i < startSwIdx + numSwPerDla; i++) {
        swChunkFiles.push_back(kSharedWeightsFiles[i]);
        if (preloaded) {
            swChunkBuffers.push_back(mSharedWeightsBuffers[i]);
        }
    }
    return swChunk;
}

} // namespace mtk