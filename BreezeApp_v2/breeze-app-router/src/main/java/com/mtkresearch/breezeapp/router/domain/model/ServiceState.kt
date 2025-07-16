package com.mtkresearch.breezeapp.router.domain.model

import androidx.annotation.DrawableRes
import com.mtkresearch.breezeapp.router.R

/**
 * Domain model representing the current state of the AI Router Service.
 * 
 * This sealed class follows Clean Architecture principles by:
 * - Encapsulating state logic in the domain layer
 * - Providing type-safe state representation
 * - Abstracting UI concerns from business logic
 * 
 * Each state provides display information while maintaining separation of concerns.
 */
sealed class ServiceState {
    
    /**
     * Service is ready and waiting for requests
     */
    object Ready : ServiceState() {
        override fun getDisplayText(): String = "AI Router Ready"
        override fun getIcon(): Int = R.drawable.ic_home
        override fun isOngoing(): Boolean = true
        override fun showProgress(): Boolean = false
    }
    
    /**
     * Service is actively processing AI requests
     * @param activeRequests Number of concurrent requests being processed
     */
    data class Processing(val activeRequests: Int) : ServiceState() {
        override fun getDisplayText(): String = 
            "Processing $activeRequests AI request${if (activeRequests > 1) "s" else ""}"
        override fun getIcon(): Int = R.drawable.ic_refresh
        override fun isOngoing(): Boolean = true
        override fun showProgress(): Boolean = true
        override fun getProgressValue(): Int = 0 // Indeterminate progress
        override fun isIndeterminate(): Boolean = true
    }
    
    /**
     * Service is downloading AI models
     * @param modelName Name of the model being downloaded
     * @param progress Download progress percentage (0-100)
     * @param totalSize Optional total size for display
     */
    data class Downloading(
        val modelName: String,
        val progress: Int,
        val totalSize: String? = null
    ) : ServiceState() {
        override fun getDisplayText(): String {
            val sizeInfo = totalSize?.let { " ($it)" } ?: ""
            return "Downloading $modelName: $progress%$sizeInfo"
        }
        override fun getIcon(): Int = R.drawable.ic_cloud_off
        override fun isOngoing(): Boolean = true
        override fun showProgress(): Boolean = true
        override fun getProgressValue(): Int = progress
        override fun getProgressMax(): Int = 100
        override fun isIndeterminate(): Boolean = false
    }
    
    /**
     * Service encountered an error
     * @param message Error description
     * @param isRecoverable Whether the error can be recovered from
     */
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true
    ) : ServiceState() {
        override fun getDisplayText(): String = "AI Router Error: $message"
        override fun getIcon(): Int = R.drawable.ic_error
        override fun isOngoing(): Boolean = false
        override fun showProgress(): Boolean = false
    }
    
    // Abstract methods that all states must implement
    abstract fun getDisplayText(): String
    @DrawableRes
    abstract fun getIcon(): Int
    abstract fun isOngoing(): Boolean
    abstract fun showProgress(): Boolean
    
    // Optional methods with default implementations
    open fun getProgressValue(): Int = 0
    open fun getProgressMax(): Int = 100
    open fun isIndeterminate(): Boolean = false
    
    /**
     * Determines if this state represents an active operation
     */
    fun isActive(): Boolean = when (this) {
        is Ready -> false
        is Processing -> true
        is Downloading -> true
        is Error -> false
    }
    
    /**
     * Gets the priority level for notification importance
     */
    fun getNotificationPriority(): NotificationPriority = when (this) {
        is Ready -> NotificationPriority.LOW
        is Processing -> NotificationPriority.DEFAULT
        is Downloading -> NotificationPriority.DEFAULT
        is Error -> NotificationPriority.HIGH
    }
}

/**
 * Notification priority levels following Android guidelines
 */
enum class NotificationPriority {
    LOW,      // Ready state - minimal interruption
    DEFAULT,  // Normal operations - standard visibility
    HIGH      // Errors - requires user attention
}