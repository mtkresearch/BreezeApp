LOCAL_PATH := $(call my-dir)
USER_LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES)

include $(CLEAR_VARS)
LOCAL_MODULE := llm_prebuilt
LOCAL_SRC_FILES := libmtk_llm.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := common
LOCAL_SRC_FILES := libcommon.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := tokenizer
LOCAL_SRC_FILES := libtokenizer.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := yaml_cpp
LOCAL_SRC_FILES := third_party/lib/libyaml-cpp.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH) \
                           $(LOCAL_PATH)/third_party/include
include $(PREBUILT_SHARED_LIBRARY)

LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)