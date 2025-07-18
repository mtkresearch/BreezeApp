package com.mtkresearch.breezeapp.router.core

import android.app.Service
import android.util.Log
import com.mtkresearch.breezeapp.router.domain.model.ServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Central status manager for the AI Router Service following Clean Architecture principles.
 * 
 * Responsibilities:
 * - Maintain single source of truth for service state
 * - Coordinate between domain state and UI notifications
 * - Provide reactive state updates via StateFlow
 * - Handle foreground service notification updates
 * 
 * This class acts as the Application Service layer, orchestrating between
 * domain models (ServiceState) and infrastructure concerns (notifications).
 */
class RouterStatusManager(
    private val service: Service,
    private val notificationManager: ServiceNotificationManager
) {
    
    companion object {
        private const val TAG = "RouterStatusManager"
        
        // Notification constants
        const val FOREGROUND_NOTIFICATION_ID = 1001
        private const val STATUS_UPDATE_DEBOUNCE_MS = 100L
    }
    
    // State management following reactive principles
    private val _currentState = MutableStateFlow<ServiceState>(ServiceState.Ready)
    val currentState: StateFlow<ServiceState> = _currentState.asStateFlow()
    
    /**
     * Updates the service state and triggers all necessary side effects.
     * 
     * This method ensures consistency between:
     * - Internal state tracking
     * - Foreground service notifications
     * - Logging for debugging
     * 
     * @param newState The new service state from domain layer
     */
    fun updateState(newState: ServiceState) {
        val previousState = _currentState.value
        
        Log.d(TAG, "State transition: ${previousState::class.simpleName} -> ${newState::class.simpleName}")
        Log.v(TAG, "New state details: ${newState.getDisplayText()}")
        
        // Update internal state
        _currentState.value = newState
        
        // Update foreground notification
        updateForegroundNotification(newState)
        
        // Log state-specific information
        logStateTransition(previousState, newState)
    }
    
    /**
     * Gets the current service state synchronously.
     * Useful for immediate state checks without reactive subscription.
     */
    fun getCurrentState(): ServiceState = _currentState.value
    
    /**
     * Checks if the service is currently performing active work.
     */
    fun isActivelyWorking(): Boolean = _currentState.value.isActive()
    
    /**
     * Updates the foreground service notification based on current state.
     * 
     * @param state Current service state
     */
    private fun updateForegroundNotification(state: ServiceState) {
        try {
            // For foreground services, we need to update the notification properly
            val notification = notificationManager.createNotification(state)
            val systemNotificationManager = service.getSystemService(android.app.NotificationManager::class.java)
            systemNotificationManager.notify(FOREGROUND_NOTIFICATION_ID, notification)
            Log.v(TAG, "Foreground notification updated for state: ${state::class.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update foreground notification", e)
        }
    }
    
    /**
     * Logs detailed information about state transitions for debugging.
     */
    private fun logStateTransition(previous: ServiceState, new: ServiceState) {
        when {
            previous is ServiceState.Ready && new is ServiceState.Processing -> {
                Log.i(TAG, "Service became active: processing ${(new as ServiceState.Processing).activeRequests} requests")
            }
            previous is ServiceState.Processing && new is ServiceState.Ready -> {
                Log.i(TAG, "Service became idle: all requests completed")
            }
            previous is ServiceState.Ready && new is ServiceState.Downloading -> {
                val downloading = new as ServiceState.Downloading
                Log.i(TAG, "Model download started: ${downloading.modelName}")
            }
            previous is ServiceState.Downloading && new is ServiceState.Ready -> {
                Log.i(TAG, "Model download completed")
            }
            new is ServiceState.Error -> {
                Log.w(TAG, "Service error state: ${new.message} (recoverable: ${new.isRecoverable})")
            }
            previous is ServiceState.Error && new !is ServiceState.Error -> {
                Log.i(TAG, "Service recovered from error state")
            }
        }
    }
    
    /**
     * Convenience methods for common state updates
     */
    
    fun setReady() = updateState(ServiceState.Ready)
    
    fun setProcessing(activeRequests: Int) = updateState(ServiceState.Processing(activeRequests))
    
    fun setDownloading(modelName: String, progress: Int, totalSize: String? = null) = 
        updateState(ServiceState.Downloading(modelName, progress, totalSize))
    
    fun setError(message: String, isRecoverable: Boolean = true) = 
        updateState(ServiceState.Error(message, isRecoverable))
    
    /**
     * Updates status with client count information for enhanced notification.
     */
    fun updateWithClientCount(state: ServiceState, clientCount: Int) {
        val enhancedState = when (state) {
            is ServiceState.Ready -> ServiceState.ReadyWithClients(clientCount)
            is ServiceState.Processing -> ServiceState.ProcessingWithClients(state.activeRequests, clientCount)
            else -> state
        }
        updateState(enhancedState)
    }
}