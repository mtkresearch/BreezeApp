package com.mtkresearch.breezeapp.router.domain.model

/**
 * 模型配置格式
 * 定義 Runner 載入模型時的配置資訊
 */
data class ModelConfig(
    val modelName: String,                   // 模型名稱
    val modelPath: String? = null,          // 模型檔案路徑 (可選)
    val files: Map<String, String> = emptyMap(), // 相關檔案路徑 (如 tokenizer, config)
    val parameters: Map<String, Any> = emptyMap(), // 模型參數
    val metadata: Map<String, Any> = emptyMap()   // 額外中繼資料
) {
    companion object {
        // 常用檔案 key 定義
        const val FILE_MODEL = "model"
        const val FILE_TOKENIZER = "tokenizer"
        const val FILE_CONFIG = "config"
        const val FILE_VOCAB = "vocab"
        
        // 常用參數 key 定義
        const val PARAM_MAX_CONTEXT_LENGTH = "max_context_length"
        const val PARAM_VOCAB_SIZE = "vocab_size"
        const val PARAM_EMBEDDING_DIM = "embedding_dim"
        const val PARAM_NUM_LAYERS = "num_layers"
        
        // Mock 專用配置
        fun createMockConfig(
            modelName: String,
            responseDelay: Long = 100L,
            predefinedResponses: List<String> = emptyList()
        ) = ModelConfig(
            modelName = modelName,
            parameters = mapOf(
                "response_delay_ms" to responseDelay,
                "predefined_responses" to predefinedResponses,
                "is_mock" to true
            )
        )
    }
} 