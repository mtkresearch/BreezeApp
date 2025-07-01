package com.mtkresearch.breezeapp.router.domain.model

/**
 * 推論結果格式
 * 統一的推論回應資料結構
 */
data class InferenceResult(
    val outputs: Map<String, Any>,           // 輸出結果
    val metadata: Map<String, Any> = emptyMap(), // 中繼資料 (如置信度、延遲等)
    val error: RunnerError? = null,          // 錯誤資訊
    val partial: Boolean = false             // 是否為部分結果 (streaming)
) {
    companion object {
        // 常用輸出 key 定義
        const val OUTPUT_TEXT = "text"
        const val OUTPUT_AUDIO = "audio"
        const val OUTPUT_IMAGE = "image"
        const val OUTPUT_CONFIDENCE = "confidence"
        
        // 常用中繼資料 key 定義
        const val META_PROCESSING_TIME_MS = "processing_time_ms"
        const val META_MODEL_NAME = "model"
        const val META_CONFIDENCE = "confidence"
        const val META_TOKEN_COUNT = "tokens"
        const val META_SEGMENT_INDEX = "segment_index"
        const val META_PARTIAL_TOKENS = "partial_tokens"
        const val META_SESSION_ID = "session_id"
        const val META_STREAM_MODE = "stream_mode"
        
        // 快速建立成功結果的工廠方法
        fun success(
            outputs: Map<String, Any>,
            metadata: Map<String, Any> = emptyMap(),
            partial: Boolean = false
        ) = InferenceResult(outputs, metadata, null, partial)
        
        // 快速建立錯誤結果的工廠方法
        fun error(error: RunnerError) = InferenceResult(
            emptyMap(), emptyMap(), error, false
        )
        
        // 快速建立文字輸出結果
        fun textOutput(
            text: String,
            metadata: Map<String, Any> = emptyMap(),
            partial: Boolean = false
        ) = success(mapOf(OUTPUT_TEXT to text), metadata, partial)
    }
} 