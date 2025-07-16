package com.mtkresearch.breezeapp.router.domain.usecase

/**
 * An abstract logging interface to decouple the domain layer from any specific
 * logging framework (e.g., android.util.Log).
 *
 * This allows the core business logic to be tested in a pure JVM environment.
 */
interface Logger {
    /**
     * Log a debug message.
     */
    fun d(tag: String, message: String)

    /**
     * Log a warning message.
     */
    fun w(tag: String, message: String)

    /**
     * Log an error message with an optional throwable.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null)
} 