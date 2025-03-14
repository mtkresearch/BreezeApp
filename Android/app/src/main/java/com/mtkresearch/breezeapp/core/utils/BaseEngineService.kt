package com.mtkresearch.breezeapp.core.utils

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.mtkresearch.breezeapp.core.utils.AppConstants.BACKEND_CPU
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Base service class for all AI engine services (LLM, VLM, ASR, TTS)
 * Provides common functionality and initialization patterns
 */
abstract class BaseEngineService : Service() {
    companion object {
        const val TAG = "BaseEngineService"
        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_BACKEND = "backend"
    }

    // Service state
    protected var backend = BACKEND_CPU
    protected var isInitialized = false
    protected var modelPath: String? = null
    
    // State observables
    private val _serviceState = MutableStateFlow<ServiceState>(ServiceState.Uninitialized)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    /**
     * Service binder for clients to connect
     */
    inner class LocalBinder : Binder() {
        fun getService(): BaseEngineService = this@BaseEngineService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created: ${javaClass.simpleName}")
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "Service bound: ${javaClass.simpleName}")
        handleIntent(intent)
        return LocalBinder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started: ${javaClass.simpleName}")
        intent?.let { handleIntent(it) }
        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent) {
        backend = intent.getStringExtra(EXTRA_BACKEND) ?: BACKEND_CPU
        modelPath = intent.getStringExtra(EXTRA_MODEL_PATH)
        Log.d(TAG, "Service configured with backend: $backend, model: $modelPath")
    }

    /**
     * Initialize the engine asynchronously
     */
    abstract suspend fun initialize(): Boolean

    /**
     * Release resources when the service is no longer needed
     */
    abstract suspend fun releaseResources()

    /**
     * Get the display name of the currently loaded model
     */
    abstract fun getModelName(): String

    /**
     * Check if the engine is ready for use
     */
    fun isReady(): Boolean {
        return isInitialized
    }

    protected fun updateState(state: ServiceState) {
        Log.d(TAG, "Service state updated to $state")
        _serviceState.value = state
        
        // Update initialized flag based on state
        isInitialized = state is ServiceState.Ready
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed: ${javaClass.simpleName}")
        super.onDestroy()
    }
}

/**
 * Sealed class representing the possible states of an AI engine service
 */
sealed class ServiceState {
    data object Uninitialized : ServiceState()
    data object Initializing : ServiceState()
    data class Ready(val modelName: String) : ServiceState()
    data class Error(val message: String) : ServiceState()
} 