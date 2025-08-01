package com.mtkresearch.breezeapp_kotlin.domain.usecase.breezeapp

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import com.mtkresearch.breezeapp_kotlin.domain.model.breezeapp.ConnectionState as BreezeAppConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * UseCase for managing BreezeApp Engine connection
 * 
 * Responsibilities:
 * - Initialize EdgeAI SDK
 * - Manage connection state
 * - Handle connection retries
 * - Provide connection status updates
 * 
 * This UseCase follows Clean Architecture principles by:
 * - Being independent of external frameworks
 * - Having a single responsibility
 * - Being easily testable
 */
class ConnectionUseCase @Inject constructor(
    private val application: Application
) {
    
    companion object {
        private const val TAG = "ConnectionUseCase"
        private const val MAX_RETRY_COUNT = 3
        private const val INITIALIZATION_TIMEOUT_MS = 8000L
    }
    
    private var initializationRetryCount = 0
    private var isInitializing = false
    
    /**
     * Initialize BreezeApp Engine with retry logic
     */
    suspend fun initialize(): Flow<BreezeAppConnectionState> = flow {
        emit(BreezeAppConnectionState.Initializing)
        
        if (isInitializing) {
            emit(BreezeAppConnectionState.Initializing)
            return@flow
        }
        
        isInitializing = true
        initializationRetryCount++
        
        try {
            Log.d(TAG, "Initializing BreezeApp Engine (attempt $initializationRetryCount/$MAX_RETRY_COUNT)")
            
            // Try to wake up the service first
            tryWakeUpService()
            
            // Initialize EdgeAI SDK
            EdgeAI.initializeAndWait(application, INITIALIZATION_TIMEOUT_MS)
            
            // Verify connection is established
            if (EdgeAI.isReady()) {
                Log.i(TAG, "BreezeApp Engine initialized successfully!")
                initializationRetryCount = 0
                isInitializing = false
                emit(BreezeAppConnectionState.Connected)
            } else {
                throw ServiceConnectionException("SDK reported success but not ready")
            }
            
        } catch (e: ServiceConnectionException) {
            Log.e(TAG, "BreezeApp Engine initialization failed: ${e.message}")
            handleInitializationFailure(e)
            emit(BreezeAppConnectionState.Failed(e.message ?: "Connection failed"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during initialization: ${e.message}")
            handleInitializationFailure(e)
            emit(BreezeAppConnectionState.Failed(e.message ?: "Unknown error"))
        } finally {
            isInitializing = false
        }
    }
    
    /**
     * Disconnect from BreezeApp Engine
     */
    suspend fun disconnect(): Flow<BreezeAppConnectionState> = flow {
        emit(BreezeAppConnectionState.Disconnecting)
        
        try {
            EdgeAI.shutdown()
            Log.d(TAG, "BreezeApp Engine disconnected")
            emit(BreezeAppConnectionState.Disconnected)
        } catch (e: Exception) {
            Log.w(TAG, "Disconnect error: ${e.message}")
            emit(BreezeAppConnectionState.Failed(e.message ?: "Disconnect failed"))
        }
    }
    
    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean = EdgeAI.isReady()
    
    /**
     * Manual connection attempt
     */
    suspend fun connect(): Flow<BreezeAppConnectionState> = flow {
        if (EdgeAI.isReady()) {
            emit(BreezeAppConnectionState.Connected)
            return@flow
        }
        
        // Reset retry count for manual attempts
        initializationRetryCount = 0
        initialize().collect { state ->
            emit(state)
        }
    }
    
    private suspend fun tryWakeUpService() {
        try {
            Log.d(TAG, "Attempting to wake up BreezeApp Engine Service...")
            val intent = Intent().apply {
                component = ComponentName(
                    "com.mtkresearch.breezeapp.engine",
                    "com.mtkresearch.breezeapp.engine.BreezeAppEngineService"
                )
            }
            
            application.startService(intent)
            Log.d(TAG, "Service wake-up signal sent")
            
            // Give the service time to start up
            delay(1000)
        } catch (e: Exception) {
            Log.w(TAG, "Could not wake up service: ${e.message}")
        }
    }
    
    private suspend fun handleInitializationFailure(error: Throwable) {
        if (initializationRetryCount < MAX_RETRY_COUNT) {
            val retryDelay = initializationRetryCount * 2000L // 2s, 4s, 6s
            Log.d(TAG, "Retrying in ${retryDelay / 1000}s... (${initializationRetryCount}/$MAX_RETRY_COUNT)")
            
            delay(retryDelay)
            initialize().collect { /* Re-emit states */ }
        } else {
            Log.e(TAG, "Failed to initialize after $MAX_RETRY_COUNT attempts")
            
            // Get diagnostic message
            val diagnosticMessage = try {
                com.mtkresearch.breezeapp.edgeai.EdgeAIDebug.getDiagnosticMessage(application)
            } catch (e: Exception) {
                "Unable to connect to BreezeApp Engine. Please try again or restart the app."
            }
            
            Log.d(TAG, "Diagnostic: $diagnosticMessage")
        }
    }
}