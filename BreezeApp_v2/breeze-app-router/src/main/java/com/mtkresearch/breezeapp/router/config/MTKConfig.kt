package com.mtkresearch.breezeapp.router.config

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * MTK Runner 配置數據類別
 * 包含所有 MTK NPU 推論相關的配置參數
 * 
 * 改進點：
 * - 移除硬編碼常數，改為可配置參數
 * - 支援 Parcelable 序列化
 * - 提供預設值和驗證邏輯
 * - 支援 YAML 配置文件載入
 */
@Parcelize
data class MTKConfig(
    // 模型相關配置
    val modelPath: String,
    val configPath: String? = null,
    val preloadSharedWeights: Boolean = true,
    
    // 初始化配置
    val maxInitAttempts: Int = 5,
    val initDelayMs: Long = 200,
    val initTimeoutMs: Long = 30000,
    
    // 資源管理配置
    val cleanupTimeoutMs: Long = 5000,
    val maxRetryAttempts: Int = 3,
    val retryDelayMs: Long = 1000,
    
    // 推論參數預設值
    val defaultTemperature: Float = 0.8f,
    val defaultTopK: Int = 40,
    val defaultTopP: Float = 0.9f,
    val defaultRepetitionPenalty: Float = 1.1f,
    val defaultMaxTokens: Int = 2048,
    
    // 串流配置
    val enableStreaming: Boolean = true,
    val streamingBufferSize: Int = 1024,
    val streamingTimeoutMs: Long = 30000,
    
    // 快取配置
    val enableModelCache: Boolean = true,
    val modelTokenSize: Int = 128, // MB
    val enableSwapModel: Boolean = true,
    
    // 記憶體管理
    val maxMemoryUsageMB: Int = 1024,
    val enableMemoryOptimization: Boolean = true,
    val gcThresholdMB: Int = 512,
    
    // 除錯配置
    val enableDebugLog: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val enablePerformanceMetrics: Boolean = false
) : Parcelable {
    
    /**
     * 日誌級別枚舉
     */
    enum class LogLevel {
        VERBOSE, DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * 驗證配置參數的有效性
     * 
     * @return ConfigValidationResult 驗證結果
     */
    fun validate(): ConfigValidationResult {
        val errors = mutableListOf<String>()
        
        // 驗證模型路徑
        if (modelPath.isBlank()) {
            errors.add("Model path cannot be empty")
        }
        
        // 驗證初始化參數
        if (maxInitAttempts <= 0) {
            errors.add("Max init attempts must be positive")
        }
        
        if (initDelayMs < 0) {
            errors.add("Init delay cannot be negative")
        }
        
        if (initTimeoutMs <= 0) {
            errors.add("Init timeout must be positive")
        }
        
        // 驗證推論參數
        if (defaultTemperature < 0.0f || defaultTemperature > 2.0f) {
            errors.add("Temperature must be between 0.0 and 2.0")
        }
        
        if (defaultTopK <= 0) {
            errors.add("TopK must be positive")
        }
        
        if (defaultTopP < 0.0f || defaultTopP > 1.0f) {
            errors.add("TopP must be between 0.0 and 1.0")
        }
        
        if (defaultRepetitionPenalty < 0.0f) {
            errors.add("Repetition penalty cannot be negative")
        }
        
        if (defaultMaxTokens <= 0) {
            errors.add("Max tokens must be positive")
        }
        
        // 驗證記憶體配置
        if (maxMemoryUsageMB <= 0) {
            errors.add("Max memory usage must be positive")
        }
        
        if (modelTokenSize <= 0) {
            errors.add("Model cache size must be positive")
        }
        
        if (gcThresholdMB <= 0) {
            errors.add("GC threshold must be positive")
        }
        
        // 驗證串流配置
        if (streamingBufferSize <= 0) {
            errors.add("Streaming buffer size must be positive")
        }
        
        if (streamingTimeoutMs <= 0) {
            errors.add("Streaming timeout must be positive")
        }
        
        return if (errors.isEmpty()) {
            ConfigValidationResult.Valid
        } else {
            ConfigValidationResult.Invalid(errors)
        }
    }
    
    /**
     * 建立推論參數
     * 
     * @param temperature 溫度參數，null 使用預設值
     * @param topK TopK 參數，null 使用預設值
     * @param topP TopP 參數，null 使用預設值
     * @param repetitionPenalty 重複懲罰參數，null 使用預設值
     * @param maxTokens 最大 token 數，null 使用預設值
     * @return InferenceParameters 推論參數
     */
    fun createInferenceParameters(
        temperature: Float? = null,
        topK: Int? = null,
        topP: Float? = null,
        repetitionPenalty: Float? = null,
        maxTokens: Int? = null
    ): InferenceParameters {
        return InferenceParameters(
            temperature = temperature ?: defaultTemperature,
            topK = topK ?: defaultTopK,
            topP = topP ?: defaultTopP,
            repetitionPenalty = repetitionPenalty ?: defaultRepetitionPenalty,
            maxTokens = maxTokens ?: defaultMaxTokens
        )
    }
    
    /**
     * 建立除錯配置
     * 
     * @return DebugConfig 除錯配置
     */
    fun createDebugConfig(): DebugConfig {
        return DebugConfig(
            enableDebugLog = enableDebugLog,
            logLevel = logLevel,
            enablePerformanceMetrics = enablePerformanceMetrics
        )
    }
    
    companion object {
        /**
         * 建立預設配置
         * 
         * @param modelPath 模型路徑
         * @return MTKConfig 預設配置
         */
        fun createDefault(modelPath: String): MTKConfig {
            return MTKConfig(modelPath = modelPath)
        }
        
        /**
         * 建立測試配置
         * 
         * @param modelPath 模型路徑
         * @return MTKConfig 測試配置
         */
        fun createForTesting(modelPath: String): MTKConfig {
            return MTKConfig(
                modelPath = modelPath,
                maxInitAttempts = 1,
                initDelayMs = 0,
                initTimeoutMs = 5000,
                cleanupTimeoutMs = 1000,
                enableDebugLog = true,
                logLevel = LogLevel.DEBUG
            )
        }
    }
}

/**
 * 推論參數數據類別
 */
@Parcelize
data class InferenceParameters(
    val temperature: Float,
    val topK: Int,
    val topP: Float,
    val repetitionPenalty: Float,
    val maxTokens: Int
) : Parcelable {
    
    /**
     * 驗證參數有效性
     */
    fun validate(): Boolean {
        return temperature >= 0.0f && temperature <= 2.0f &&
                topK > 0 &&
                topP >= 0.0f && topP <= 1.0f &&
                repetitionPenalty >= 0.0f &&
                maxTokens > 0
    }
}

/**
 * 除錯配置數據類別
 */
@Parcelize
data class DebugConfig(
    val enableDebugLog: Boolean,
    val logLevel: MTKConfig.LogLevel,
    val enablePerformanceMetrics: Boolean
) : Parcelable

/**
 * 配置驗證結果密封類
 */
sealed class ConfigValidationResult {
    object Valid : ConfigValidationResult()
    data class Invalid(val errors: List<String>) : ConfigValidationResult()
    
    fun isValid(): Boolean = this is Valid
    fun isInvalid(): Boolean = this is Invalid
    
    // 移除 getErrors() 明確函式
    // 直接用 errors 屬性即可
} 