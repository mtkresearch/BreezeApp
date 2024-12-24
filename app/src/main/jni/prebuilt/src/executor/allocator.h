#pragma once

#include <cstddef>
#include <unordered_map>

struct AHardwareBuffer;
struct NeuronMemory;

namespace mtk {

struct IOBuffer {
    void* buffer = nullptr;
    int fd = -1;
    size_t sizeBytes = 0;
    size_t usedSizeBytes = 0;

    // Optionally used by AHWB
    AHardwareBuffer* ahwbHandle = nullptr;

    // Optionally used by USDK
    NeuronMemory* neuronMemory = nullptr;

    // Helper functions
    explicit operator bool() const { return isAllocated(); }
    bool isAllocated() const { return buffer != nullptr && sizeBytes != 0; }
};

class Allocator {
public:
    virtual ~Allocator();

    IOBuffer allocate(const size_t size);
    bool release(void* addr);

    void releaseAll();

    virtual bool allocateMemory(IOBuffer& ioBuffer) = 0;
    virtual bool releaseMemory(IOBuffer& ioBuffer) = 0;

protected:
    // A record of buffers allocated by this allocator instance, using the mapped addr as key.
    std::unordered_map<void*, IOBuffer> mAllocatedBuffers;
};

class DmaBufferAllocator final : public Allocator {
public:
    virtual ~DmaBufferAllocator() override;
    virtual bool allocateMemory(IOBuffer& ioBuffer) override;
    virtual bool releaseMemory(IOBuffer& ioBuffer) override;
};

class AhwBufferAllocator final : public Allocator {
public:
    virtual ~AhwBufferAllocator() override;
    virtual bool allocateMemory(IOBuffer& ioBuffer) override;
    virtual bool releaseMemory(IOBuffer& ioBuffer) override;
};

} // namespace mtk