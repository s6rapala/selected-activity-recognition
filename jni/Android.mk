LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := edu.mainf.cs.testrunscollector
LOCAL_SRC_FILES := edu.mainf.cs.testrunscollector.cpp

include $(BUILD_SHARED_LIBRARY)
