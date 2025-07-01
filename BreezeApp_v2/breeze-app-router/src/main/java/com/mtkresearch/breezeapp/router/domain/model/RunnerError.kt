package com.mtkresearch.breezeapp.router.domain.model

/**
 * Runner 錯誤處理格式
 * 統一的錯誤資訊結構
 */
data class RunnerError(
    val code: String,                        // 錯誤碼 (如 E101, E201)
    val message: String,                     // 錯誤描述
    val recoverable: Boolean = false,        // 是否可重試
    val cause: Throwable? = null            // 原始異常
) {
    companion object {
        // 常用錯誤碼定義
        const val ERROR_MODEL_NOT_LOADED = "E001"
        const val ERROR_INVALID_INPUT = "E401"
        const val ERROR_RUNTIME_EXCEPTION = "E101"
        const val ERROR_TIMEOUT = "E301"
        const val ERROR_MEMORY_INSUFFICIENT = "E201"
        const val ERROR_MODEL_INCOMPATIBLE = "E002"
        
        // 快速建立常用錯誤的工廠方法
        fun modelNotLoaded(message: String = "Model not loaded") = RunnerError(
            ERROR_MODEL_NOT_LOADED, message, true
        )
        
        fun invalidInput(message: String) = RunnerError(
            ERROR_INVALID_INPUT, message, false
        )
        
        fun runtimeError(message: String, cause: Throwable? = null) = RunnerError(
            ERROR_RUNTIME_EXCEPTION, message, true, cause
        )
        
        fun timeout(message: String = "Request timeout") = RunnerError(
            ERROR_TIMEOUT, message, true
        )
    }
} 