LOCAL_PATH := $(call my-dir)
USER_LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES)

# === Add by Muxi 20241212
# First, create a static library from main.cpp
include $(CLEAR_VARS)
LOCAL_MODULE := main_llm
LOCAL_SRC_FILES := main.cpp
LOCAL_STATIC_LIBRARIES := utils
LOCAL_SHARED_LIBRARIES := llm_prebuilt common tokenizer
LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)
LOCAL_LDLIBS := -llog
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)
include $(BUILD_SHARED_LIBRARY)
# === Add by Muxi 20241212

define MAKE_EXECUTABLE
    include $(CLEAR_VARS)
    LOCAL_MODULE := $1
    LOCAL_SRC_FILES := $1.cpp
    LOCAL_STATIC_LIBRARIES += utils
    LOCAL_SHARED_LIBRARIES += llm_prebuilt common tokenizer
    LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)
    LOCAL_LDLIBS := -llog
    include $(BUILD_EXECUTABLE)
endef

# Build the below executable files
FILES := main main_batch_gen main_spec_dec main_medusa
$(foreach item,$(FILES),$(eval $(call MAKE_EXECUTABLE,$(item))))