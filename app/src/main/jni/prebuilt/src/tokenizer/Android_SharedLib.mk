LOCAL_PATH := $(call my-dir)
USER_LOCAL_PATH := $(LOCAL_PATH)
USER_ROOT_PATH := $(LOCAL_PATH)/..
USER_LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES)

# Include libcommon
include $(CLEAR_VARS)
LOCAL_MODULE := common
LOCAL_PATH := $(USER_LOCAL_PATH)
LOCAL_SRC_FILES := ../../libcommon.so
LOCAL_EXPORT_C_INCLUDES := $(ROOT_PATH)
include $(PREBUILT_SHARED_LIBRARY)


# Include dependent third party shared libraries
include $(CLEAR_VARS)
LOCAL_MODULE := yaml_cpp
LOCAL_SRC_FILES := ../../third_party/lib/libyaml-cpp.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../ \
                           $(LOCAL_PATH)/../../third_party/include
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := sentencepiece
LOCAL_SRC_FILES := ../../third_party/lib/libsentencepiece.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := hf_tokenizer
LOCAL_SRC_FILES := ../../third_party/lib/libhf-tokenizer.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := re2
LOCAL_SRC_FILES := ../../third_party/lib/libre2.so
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/../../ \
                           $(LOCAL_PATH)/../../third_party/include
include $(PREBUILT_SHARED_LIBRARY)


# Build tokenizer shared library
include $(CLEAR_VARS)
LOCAL_PATH := $(USER_LOCAL_PATH)
LOCAL_MODULE := tokenizer
LOCAL_SRC_FILES := tokenizer.cpp sentencepiece_tokenizer.cpp huggingface_tokenizer.cpp tiktoken_tokenizer.cpp
LOCAL_SHARED_LIBRARIES += common yaml_cpp re2 sentencepiece hf_tokenizer
LOCAL_C_INCLUDES := $(USER_ROOT_PATH)
LOCAL_CFLAGS += -fexceptions # For yaml-cpp
LOCAL_CFLAGS += -fvisibility=default  # Default export all symbols
LOCAL_LDLIBS := -llog
include $(BUILD_SHARED_LIBRARY)

LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)