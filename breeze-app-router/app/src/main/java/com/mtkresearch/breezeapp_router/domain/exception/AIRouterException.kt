package com.mtkresearch.breezeapp_router.domain.exception

/**
 * AI Router 相關異常 - Router App 內部使用
 */
sealed class AIRouterException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    
    /**
     * 服務連接錯誤
     */
    class ServiceConnectionException(
        message: String = "AI Router Service 連接失敗",
        cause: Throwable? = null
    ) : AIRouterException(message, cause)
    
    /**
     * AI 引擎錯誤
     */
    class AIEngineException(
        message: String = "AI 引擎處理錯誤",
        cause: Throwable? = null
    ) : AIRouterException(message, cause)
    
    /**
     * 請求超時錯誤
     */
    class TimeoutException(
        message: String = "AI 請求處理超時",
        cause: Throwable? = null
    ) : AIRouterException(message, cause)
    
    /**
     * 無效請求錯誤
     */
    class InvalidRequestException(
        message: String = "無效的 AI 請求",
        cause: Throwable? = null
    ) : AIRouterException(message, cause)
} 