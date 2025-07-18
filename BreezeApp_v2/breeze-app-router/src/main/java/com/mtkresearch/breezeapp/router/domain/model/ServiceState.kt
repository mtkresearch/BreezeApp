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
 * States automatically determine their notification behavior, progress display,
 * and visual representation.
 * 
 * ## Usage Example
 * ```kotlin
 * when (val state = statusManager.getCurrentState()) {
 *     is ServiceState.Ready -> showReadyUI()
 *     is ServiceState.Processing -> showProgress(state.activeRequests)
 *     is ServiceState.Downloading -> showDownload(state.progress)
 *     is ServiceState.Error -> showError(state.message)
 * }
 * ```
 * 
 * @see RouterStatusManager for state management
 * @see ServiceNotificationManager for notification integration
 */
sealed class ServiceState {
    
    /**
     * Service is ready and waiting for requests.
     * 
     * This is the default idle state when no AI operations are in progress.
     * The service is fully initialized and ready to accept new requests.
     */
    object Ready : ServiceState() {
        override fun getDisplayText(): String = "AI Router Ready"
        override fun getIcon(): Int = R.drawable.ic_home
        override fun isOngoing(): Boolean = true
        override fun showProgress(): Boolean = false
    }
    
    /**
     * Service is actively processing AI requests.
     * 
     * Indicates that one or more AI operations (chat, TTS, ASR) are currently
     * in progress. The notification will show an indeterminate progress indicator.
     * 
     * @param activeRequests Number of concurrent requests being processed (must be > 0)
     */
    data class Processing(val activeRequests: Int) : ServiceState() {
        override fun getDisplayText(): String = 
            "Processing $activeRequests AI request${if (activeRequests != 1) "s" else ""}"
        override fun getIcon(): Int = R.drawable.ic_refresh
        override fun isOngoing(): Boolean = true
        override fun showProgress(): Boolean = true
        override fun getProgressValue(): Int = 0 // Indeterminate progress
        override fun isIndeterminate(): Boolean = true
    }
    
    /**
     * Service is downloading AI models.
     * 
     * Indicates that a model download is in progress. The notification will show
     * a determinate progress bar with the current download percentage.
     * 
     * @param modelName Name of the model being downloaded (e.g., "llama-3.2-1b")
     * @param progress Download progress percentage (0-100)
     * @param totalSize Optional total size for display (e.g., "2.1 GB")
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
     * Service encountered an error.
     * 
     * Indicates that an error occurred during AI processing or service operation.
     * The notification will display the error message with high priority to ensure
     * user visibility. Recoverable errors allow the service to continue operating.
     * 
     * @param message Human-readable error description for display
     * @param isRecoverable Whether the service can recover from this error.
     *                      If false, the service may need to be restarted.
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
    
    /**
     * Service is ready with client count information.
     */
    data class ReadyWithClients(val clientCount: Int) : ServiceState() {
        override fun getDisplayText(): String = when (clientCount) {
            0 -> "AI Router Ready - No clients connected"
            1 -> "AI Router Ready - 1 client connected"
            else -> "AI Router Ready - $clientCount clients connected"
        }
        override fun getIcon(): Int = R.drawable.ic_home
        override fun isOngoing(): Boolean = true
        override fun showProgress(): Boolean = false
    }
    
    /**
     * Service is processing with client count information.
     */
    data class ProcessingWithClients(
        val activeRequests: Int,
        val clientCount: Int
    ) : ServiceState() {
        override fun getDisplayText(): String {
            val requestText = "Processing $activeRequests AI request${if (activeRequests != 1) "s" else ""}"
            val clientText = when (clientCount) {
                0 -> "No clients"
                1 -> "1 client"
                else -> "$clientCount clients"
            }
            return "$requestText - $clientText connected"
        }
        override fun getIcon(): Int = R.drawable.ic_refresh
        override fun isOngoing(): Boolean = true
        override fun showProgress(): Boolean = true
    }
    
    // Abstract methods that all states must implement
    
    /** Returns human-readable text for display in notifications and UI */
    abstract fun getDisplayText(): String
    
    /** Returns the drawable resource ID for the state icon */
    @DrawableRes
    abstract fun getIcon(): Int
    
    /** Returns true if this state represents an ongoing operation */
    abstract fun isOngoing(): Boolean
    
    /** Returns true if this state should show a progress indicator */
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
        is ReadyWithClients -> false
        is Processing -> true
        is ProcessingWithClients -> true
        is Downloading -> true
        is Error -> false
    }
    
    /**
     * Gets the priority level for notification importance
     */
    fun getNotificationPriority(): NotificationPriority = when (this) {
        is Ready -> NotificationPriority.LOW
        is ReadyWithClients -> NotificationPriority.LOW
        is Processing -> NotificationPriority.DEFAULT
        is ProcessingWithClients -> NotificationPriority.DEFAULT
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