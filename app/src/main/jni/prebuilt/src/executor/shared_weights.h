#pragma once

#include "common/file_source.h"
#include "common/thread_pool.h"
#include "executor/allocator.h"

#include <vector>

namespace mtk {

// Shared weights used by a single model chunk.
struct SharedWeights {
    std::vector<FileSource> files;
    std::vector<IOBuffer> buffers;

    // Helper functions
    explicit operator bool() const;

    bool empty() const;

    size_t size() const;

    bool isPreloaded() const;
};

// A global shared weights handle that can exist outside of LLM Runtime
class SharedWeightsHandle {
public:
    explicit SharedWeightsHandle(const std::vector<FileSource>& sharedWeightsFiles,
                                 const size_t numDlaChunks = 1)
        : kSharedWeightsFiles(sharedWeightsFiles), kNumDlaChunks(numDlaChunks) {}

    ~SharedWeightsHandle();

    void preload(const bool async = false);

    bool loaded() const;

    void wait() const;

    SharedWeights getSharedWeights(const size_t dlaChunkIndex) const;

private:
    const size_t kNumDlaChunks;
    std::unique_ptr<Allocator> mAllocator;
    std::vector<IOBuffer> mSharedWeightsBuffers;
    std::vector<FileSource> kSharedWeightsFiles;
    mutable BasicThreadPool mThreadPool;
};

} // namespace mtk