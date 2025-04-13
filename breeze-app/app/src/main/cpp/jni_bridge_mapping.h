#ifndef JNI_BRIDGE_MAPPING_H
#define JNI_BRIDGE_MAPPING_H

// Map nativeInitLlm
#define Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeInitLlm \
        Java_com_mtkresearch_breeze_1app_service_bridge_LLMEngineService_nativeInitLlm

// Map nativeInference
#define Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeInference \
        Java_com_mtkresearch_breeze_1app_service_bridge_LLMEngineService_nativeInference

// Map nativeStreamingInference
#define Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeStreamingInference \
        Java_com_mtkresearch_breeze_1app_service_bridge_LLMEngineService_nativeStreamingInference

// Map nativeReleaseLlm
#define Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeReleaseLlm \
        Java_com_mtkresearch_breeze_1app_service_bridge_LLMEngineService_nativeReleaseLlm

// Map nativeResetLlm
#define Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeResetLlm \
        Java_com_mtkresearch_breeze_1app_service_bridge_LLMEngineService_nativeResetLlm

// Map nativeSwapModel
#define Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeSwapModel \
        Java_com_mtkresearch_breeze_1app_service_bridge_LLMEngineService_nativeSwapModel

// Map nativeSetTokenSize
#define Java_com_mtkresearch_gai_1android_service_LLMEngineService_nativeSetTokenSize \
        Java_com_mtkresearch_breeze_1app_service_bridge_LLMEngineService_nativeSetTokenSize

// Map registerInstance
#define Java_com_mtkresearch_gai_1android_service_LLMEngineService_registerInstance \
        Java_com_mtkresearch_breeze_1app_service_bridge_LLMEngineService_registerInstance

#endif // JNI_BRIDGE_MAPPING_H 