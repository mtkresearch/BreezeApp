LOCAL_PATH := $(call my-dir)
USER_LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES)

define MAKE_EXECUTABLE
    include $(CLEAR_VARS)
    LOCAL_MODULE := $1
    LOCAL_SRC_FILES := $1.cpp
    LOCAL_STATIC_LIBRARIES += utils
    LOCAL_SHARED_LIBRARIES += llm_prebuilt
    LOCAL_SHARED_LIBRARIES += mllm_prebuilt yaml_cpp common tokenizer
    LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)
    LOCAL_LDLIBS := -llog
    include $(BUILD_EXECUTABLE)
endef

# Build the below executable files
FILES := main_llava main_llava_spec_dec
$(foreach item,$(FILES),$(eval $(call MAKE_EXECUTABLE,$(item))))