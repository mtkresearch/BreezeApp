LOCAL_PATH := $(call my-dir)
USER_LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES)

include $(CLEAR_VARS)
LOCAL_MODULE := executor
LOCAL_SRC_FILES := allocator.cpp             \
                   shared_weights.cpp        \
                   multi_runtime_handler.cpp \
                   executor.cpp              \
                   tflite_executor.cpp       \
                   neuron_executor.cpp       \
                   neuron_usdk_executor.cpp  \
                   llm_executor.cpp        \
                   llm_medusa_executor.cpp \
                   llm_ringbuffer_executor.cpp
LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)
LOCAL_STATIC_LIBRARIES += llm_helper
LOCAL_SHARED_LIBRARIES += common
include $(BUILD_STATIC_LIBRARY)

LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)