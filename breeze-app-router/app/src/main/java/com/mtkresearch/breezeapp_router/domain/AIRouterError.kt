package com.mtkresearch.breezeapp_router.domain

/**
 * AI Router 錯誤類別
 */
sealed class AIRouterError : Exception() {
    data class ConnectionError(override val message: String) : AIRouterError()
    data class ServiceError(override val message: String, val code: Int) : AIRouterError()
    data class EngineError(override val message: String, val engine: String) : AIRouterError()
    data class ModelError(override val message: String, val model: String) : AIRouterError()
}

class AIRouterException(message: String, cause: Throwable? = null) : Exception(message, cause) 