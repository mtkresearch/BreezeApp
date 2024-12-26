ROOT_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(ROOT_PATH)

# First include prebuilt libcommon
include $(CLEAR_VARS)
LOCAL_MODULE := common
LOCAL_PATH := $(ROOT_PATH)
LOCAL_SRC_FILES := ../libcommon.so
LOCAL_EXPORT_C_INCLUDES := $(ROOT_PATH)
include $(PREBUILT_SHARED_LIBRARY)

# Then build the necessary components
BACKEND_ROOT := $(ROOT_PATH)/backend
include $(BACKEND_ROOT)/Android.mk

EXECUTOR_ROOT := $(ROOT_PATH)/executor
include $(EXECUTOR_ROOT)/Android.mk

LLM_HELPER_ROOT := $(ROOT_PATH)/llm_helper
include $(LLM_HELPER_ROOT)/Android.mk

# Finally build the .so
include $(CLEAR_VARS)
LOCAL_PATH := $(ROOT_PATH)
LOCAL_MODULE := mtk_llm
LOCAL_SRC_FILES := mtk_llm.cpp
LOCAL_STATIC_LIBRARIES += backend executor llm_helper
LOCAL_SHARED_LIBRARIES += common
LOCAL_LDLIBS := -llog -landroid
LOCAL_C_INCLUDES += $(ROOT_PATH)
include $(BUILD_SHARED_LIBRARY)