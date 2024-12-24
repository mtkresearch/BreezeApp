LOCAL_PATH := $(call my-dir)
USER_LOCAL_C_INCLUDES := $(LOCAL_C_INCLUDES)

include $(CLEAR_VARS)
LOCAL_MODULE := common
LOCAL_SRC_FILES := dump.cpp logging.cpp file_mem_mapper.cpp file_source.cpp scope_profiling.cpp
LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)
LOCAL_LDLIBS := -llog
LOCAL_CFLAGS += -fvisibility=default
include $(BUILD_SHARED_LIBRARY)

LOCAL_C_INCLUDES := $(USER_LOCAL_C_INCLUDES)