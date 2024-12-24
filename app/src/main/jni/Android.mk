ROOT_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_C_INCLUDES += $(ROOT_PATH)

PREBUILT_ROOT := $(ROOT_PATH)/prebuilt
include $(PREBUILT_ROOT)/Android.mk

UTILS_ROOT := $(ROOT_PATH)/utils
include $(UTILS_ROOT)/Android.mk

MAIN_ROOT := $(ROOT_PATH)/main
include $(MAIN_ROOT)/Android.mk