package com.mtkresearch.breezeapp.router

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterService
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.security.MessageDigest
import android.os.RemoteCallbackList

/**
 * AIRouterService
 *
 * This service exposes the IAIRouterService AIDL interface for IPC.
 * It enforces signature-level permission, logs all calls, and delegates business logic to AIEngineManager (use case layer).
 * All long-running work is offloaded to a coroutine scope.
 *
 * Clean Architecture: This class is strictly a framework adapter. No business logic here.
 */
class AIRouterService : Service() {
    companion object {
        private const val TAG = "AIRouterService"
        private const val PERMISSION = "com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE"
        private const val API_VERSION = 1
    }

    // Coroutine scope for background work
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Robust listener management with death monitoring
    private val listeners = object : RemoteCallbackList<IAIRouterListener>() {}

    // Service configuration (set via initialize)
    @Volatile
    private var configuration: Configuration? = null

    // --- Binder Stub Implementation ---
    private val binder = object : IAIRouterService.Stub() {
        override fun getApiVersion(): Int {
            Log.i(TAG, "getApiVersion() called")
            return API_VERSION
        }

        override fun initialize(config: Configuration?) {
            Log.i(TAG, "initialize() called: $config")
            if (config == null) {
                Log.e(TAG, "Configuration is null. Initialization failed.")
                return
            }
            // Validate configuration fields
            val valid = validateConfiguration(config)
            if (!valid) {
                Log.e(TAG, "Configuration validation failed. Initialization aborted.")
                return
            }
            configuration = config
            // TODO: Pass config to AIEngineManager (use case)
        }

        override fun sendMessage(request: AIRequest?) {
            Log.i(TAG, "sendMessage() called: $request")
            if (request == null) return
            // Offload to coroutine for non-blocking
            serviceScope.launch {
                // TODO: Delegate to AIEngineManager (use case)
                // For now, mock a response
                val response = AIResponse(
                    requestId = request.id,
                    text = "[MOCK] Hello, this is a mock response for request ${request.id}",
                    isComplete = true,
                    state = AIResponse.ResponseState.COMPLETED,
                    apiVersion = API_VERSION,
                    binaryAttachments = emptyList(),
                    metadata = emptyMap(),
                    error = null
                )
                notifyListeners(response)
            }
        }

        override fun cancelRequest(requestId: String?): Boolean {
            Log.i(TAG, "cancelRequest() called: $requestId")
            // TODO: Implement cancellation logic in AIEngineManager
            return false // Mock: always fail
        }

        override fun registerListener(listener: IAIRouterListener?) {
            Log.i(TAG, "registerListener() called: $listener")
            if (listener != null) listeners.register(listener)
        }

        override fun unregisterListener(listener: IAIRouterListener?) {
            Log.i(TAG, "unregisterListener() called: $listener")
            if (listener != null) listeners.unregister(listener)
        }

        override fun hasCapability(capabilityName: String?): Boolean {
            Log.i(TAG, "hasCapability() called: $capabilityName")
            // TODO: Query AIEngineManager for real capabilities
            return when (capabilityName) {
                "binary_data" -> true
                "streaming" -> false
                "image_processing" -> true
                "audio_processing" -> false
                else -> false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Enforce signature-level permission
        if (checkCallingOrSelfPermission(PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Denied binding: missing signature permission")
            return null
        }
        Log.i(TAG, "Client bound to AIRouterService")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        Log.i(TAG, "AIRouterService destroyed")
    }

    // Notify all registered listeners with a response (thread-safe)
    private fun notifyListeners(response: AIResponse) {
        val n = listeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                listeners.getBroadcastItem(i).onResponse(response)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to notify listener: ${listeners.getBroadcastItem(i)}", e)
            }
        }
        listeners.finishBroadcast()
    }

    // Optionally, add a method to notify error responses
    private fun notifyError(requestId: String, errorMessage: String) {
        val errorResponse = AIResponse(
            requestId = requestId,
            text = "",
            isComplete = true,
            state = AIResponse.ResponseState.ERROR,
            apiVersion = API_VERSION,
            binaryAttachments = emptyList(),
            metadata = emptyMap(),
            error = errorMessage
        )
        val n = listeners.beginBroadcast()
        for (i in 0 until n) {
            try {
                listeners.getBroadcastItem(i).onResponse(errorResponse)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to notify listener: ${listeners.getBroadcastItem(i)}", e)
            }
        }
        listeners.finishBroadcast()
    }

    // Configuration validation logic
    private fun validateConfiguration(config: Configuration): Boolean {
        // Basic checks for required fields and value ranges
        if (config.apiVersion <= 0) return false
        if (config.logLevel !in 0..5) return false
        if (config.timeoutMs < 0) return false
        if (config.maxTokens <= 0) return false
        if (config.temperature !in 0.0f..1.0f) return false
        if (config.languagePreference.isBlank()) return false
        // Validate enums (should always be valid due to type safety, but double-check)
        try {
            Configuration.RuntimeBackend.valueOf(config.preferredRuntime.name)
            config.runnerConfigurations.forEach { (task, runner) ->
                Configuration.AITaskType.valueOf(task.name)
                Configuration.RunnerType.valueOf(runner.name)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Invalid enum in configuration: ${e.message}")
            return false
        }
        // Additional checks can be added here
        return true
    }
} 