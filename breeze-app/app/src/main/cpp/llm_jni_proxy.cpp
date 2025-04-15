#include <jni.h>
#include "llm_jni_original.h"

extern "C" {

// Proxy for: nativeInitLlm
JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeInitLlm(JNIEnv* env, jobject thiz, jstring yamlConfigPath, jboolean preloadSharedWeights) {
    return Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeInitLlm(env, thiz, yamlConfigPath, preloadSharedWeights);
}

// Proxy for: nativeInference
JNIEXPORT jstring JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeInference(JNIEnv* env, jobject thiz, jstring inputString, jint maxResponse, jboolean parsePromptTokens) {
    return Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeInference(env, thiz, inputString, maxResponse, parsePromptTokens);
}

// Proxy for: nativeStreamingInference
JNIEXPORT jstring JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeStreamingInference(JNIEnv* env, jobject thiz, jstring inputString, jint maxResponse, jboolean parsePromptTokens, jobject callback) {
    return Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeStreamingInference(env, thiz, inputString, maxResponse, parsePromptTokens, callback);
}

// Proxy for: nativeReleaseLlm
JNIEXPORT void JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeReleaseLlm(JNIEnv* env, jobject thiz) {
    Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeReleaseLlm(env, thiz);
}

// Proxy for: nativeResetLlm
JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeResetLlm(JNIEnv* env, jobject thiz) {
    return Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeResetLlm(env, thiz);
}

// Proxy for: nativeSwapModel
JNIEXPORT jboolean JNICALL
Java_com_mtkresearch_breeze_1app_service_LLMEngineService_nativeSwapModel(JNIEnv* env, jobject thiz, jint tokenSize) {
    return Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeSwapModel(env, thiz, tokenSize);
}

}