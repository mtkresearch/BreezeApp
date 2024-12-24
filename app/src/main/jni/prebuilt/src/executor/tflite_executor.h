#pragma once

#include "common/file_source.h"
#include "executor/executor.h"

namespace mtk {

// Executor class that allows user to access one input and one output
class TfliteExecutor : public Executor {
public:
    explicit TfliteExecutor(const FileSource& tfliteFile, const size_t batchSize = 1,
                            const size_t maxTokenSize = 1,          // Should be >= 1
                            const float outputDequantFp16Scale = 0) // Will dequant if scale > 0
        : Executor(tfliteFile, {}, batchSize), // Shared weights for TFLite is not implemented
          kMaxTokenSize(maxTokenSize),         // Workaround for manual loop-based dynamic shape
          kOutputDequantFp16Scale(outputDequantFp16Scale) {}

    virtual ~TfliteExecutor() override { release(); }

    using Executor::runInference;
    virtual void runInference(const void* input, size_t inputSize) override;

private: // Force user to call runInference(input...).
    virtual void runInferenceImpl() override;

protected:
    virtual void* createRuntime(FileSource modelFile) override;
    virtual void releaseRuntime(void* runtime) override;

    virtual void preInitBufferProcess() override;

    virtual void registerRuntimeInputsImpl() override;
    virtual void registerRuntimeOutputsImpl() override { return; } // output is set during inference

    virtual void setRuntimeOffsetedInput(const size_t index, const size_t offset) override;
    virtual void setRuntimeOffsetedOutput(const size_t index, const size_t offset) override;

    virtual size_t getRuntimeNumInputs() const override;
    virtual size_t getRuntimeNumOutputs() const override;

    virtual size_t getRuntimeInputSizeBytes(const size_t index) const override;
    virtual size_t getRuntimeOutputSizeBytes(const size_t index) const override;

    virtual void getRuntimeInputShape(const uint64_t index, uint32_t* shape) const override;
    virtual void getRuntimeOutputShape(const uint64_t index, uint32_t* shape) const override;

private:
    virtual std::unique_ptr<Allocator> createMemoryAllocator() override;

    void setTfliteOptions();
    void dequantToFp16(int16_t* buffer_int16, const size_t numValToDequant);

private:
    // Workaround for manual loop-based dynamic shape IO
    const size_t kMaxTokenSize;
    std::vector<size_t> mInputBufferOffsetBytes;
    std::vector<size_t> mOutputBufferOffsetBytes;

    const float kOutputDequantFp16Scale;

    void* mOptions = nullptr;
};

} // namespace mtk