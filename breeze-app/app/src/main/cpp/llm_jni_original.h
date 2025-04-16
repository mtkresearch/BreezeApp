#pragma once

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeInitLlm(JNIEnv *, jobject, jstring, jboolean);
JNIEXPORT jstring JNICALL Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeInference(JNIEnv *, jobject, jstring, jint, jboolean);
JNIEXPORT jstring JNICALL Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeStreamingInference(JNIEnv *, jobject, jstring, jint, jboolean, jobject);
JNIEXPORT void JNICALL Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeReleaseLlm(JNIEnv *, jobject);
JNIEXPORT jboolean JNICALL Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeResetLlm(JNIEnv *, jobject);
JNIEXPORT jboolean JNICALL Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeSwapModel(JNIEnv *, jobject, jint);

#ifdef __cplusplus
}
#endif