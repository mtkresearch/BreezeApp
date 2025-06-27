package com.mtkresearch.breezeapp_router.domain.model

/**
 * AI Router 回應數據模型
 */
data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean = false,
    val state: ResponseState = ResponseState.PROCESSING,
    val metadata: Map<String, Any> = emptyMap()
) {
    enum class ResponseState {
        PROCESSING,  // 處理中
        STREAMING,   // 串流回應中
        COMPLETED,   // 完成
        ERROR       // 錯誤
    }
}

data class AIRouterStatus(
    val isRunning: Boolean,
    val availableEngines: List<String>,
    val currentModel: String?,
    val memoryUsage: Long,
    val processCount: Int
)

enum class AICapability {
    TEXT_GENERATION,  // 文字生成 (LLM)
    IMAGE_ANALYSIS,   // 圖像分析 (VLM)
    SPEECH_TO_TEXT,   // 語音識別 (ASR)
    TEXT_TO_SPEECH    // 語音合成 (TTS)
}