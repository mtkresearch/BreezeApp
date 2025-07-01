package com.mtkresearch.breezeapp.router.domain.model

/**
 * 推論請求格式
 * 統一的推論請求資料結構
 */
data class InferenceRequest(
    val sessionId: String,                    // 會話唯一識別碼
    val inputs: Map<String, Any>,            // 輸入資料 (文字、音訊、圖片等)
    val params: Map<String, Any> = emptyMap(), // 推論參數
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // 常用輸入 key 定義
        const val INPUT_TEXT = "text"
        const val INPUT_AUDIO = "audio"
        const val INPUT_IMAGE = "image"
        const val INPUT_AUDIO_ID = "audio_id"
        
        // 常用參數 key 定義
        const val PARAM_TEMPERATURE = "temperature"
        const val PARAM_MAX_TOKENS = "max_tokens"
        const val PARAM_LANGUAGE = "language"
    }
} 