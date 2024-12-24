#pragma once
#include "common/logging.h"
#include "executor/llm_executor.h"
#include "executor/llm_medusa_executor.h"
#include "executor/llm_ringbuffer_executor.h"
#include "executor/neuron_executor.h"
#include "executor/neuron_usdk_executor.h"
#include "executor/tflite_executor.h"

#include <type_traits>

namespace mtk {

#ifdef USE_USDK_BACKEND
using NeuronModelExecutor = NeuronUsdkExecutor;
#else
using NeuronModelExecutor = NeuronExecutor;
#endif

#ifdef DISABLE_RING_BUFFER
using LlmModelExecutor = LlmExecutor;
#else
using LlmModelExecutor = LlmRingBufferExecutor;
#endif

using LlmMedusaModelExecutor = LlmMedusaExecutor;

using TFLiteModelExecutor = TfliteExecutor;

#define GetExecutorClass(ExecType) mtk::ExecType##ModelExecutor

enum class ExecutorType {
    Neuron,
    TFLite,
    Llm,
    LlmMedusa
};

class ExecutorFactory {
public:
    explicit ExecutorFactory(const ExecutorType executorType = ExecutorType::Llm)
        : mExecutorType(executorType) {}

    ExecutorFactory& setType(const ExecutorType executorType) {
        mExecutorType = executorType;
        return *this;
    }

    template <typename... Args>
    Executor* create(Args&&... args) const {
#define __DECL__(ExecType)                                                                         \
    case ExecutorType::ExecType: {                                                                 \
        auto executor = create<ExecType##ModelExecutor>(std::forward<Args>(args)...);              \
        DCHECK(executor != nullptr) << "Unable to create '" #ExecType "' executor with the given " \
                                    << sizeof...(Args) << " arguments.";                           \
        return executor;                                                                           \
    }

        switch (mExecutorType) {
            __DECL__(Neuron)
            __DECL__(TFLite)
            __DECL__(Llm)
            __DECL__(LlmMedusa)
        }

#undef __DECL__
    }

    // Can be constructed with the provided arguments
    template <typename ExecutorClass, typename... Args>
    static std::enable_if_t<std::is_constructible_v<ExecutorClass, Args...>, Executor*>
    create(Args&&... args) {
        return new ExecutorClass(std::forward<Args>(args)...);
    }

    // Cannot be constructed with the provided arguments
    template <typename ExecutorClass, typename... Args>
    static std::enable_if_t<!std::is_constructible_v<ExecutorClass, Args...>, Executor*>
    create(Args&&... args) {
        return nullptr;
    }

private:
    ExecutorType mExecutorType;
};

} // namespace mtk