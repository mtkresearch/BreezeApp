package com.mtkresearch.breezeapp.router

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.IAIRouterService
import com.mtkresearch.breezeapp.edgeai.IAIRouterListener
import com.mtkresearch.breezeapp.edgeai.ChatRequest
import com.mtkresearch.breezeapp.edgeai.TTSRequest
import com.mtkresearch.breezeapp.edgeai.ASRRequest
import com.mtkresearch.breezeapp.edgeai.AIResponse
import com.mtkresearch.breezeapp.edgeai.ChatMessage
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.router.injection.RouterConfigurator
import com.mtkresearch.breezeapp.router.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import android.os.RemoteCallbackList
import kotlinx.coroutines.SupervisorJob

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/**
 * AIRouterService
 *
 * This service exposes the IAIRouterService AIDL interface for IPC.
 * It enforces signature-level permission, logs all calls, and delegates business logic 
 * to AIEngineManager (use case layer) with Mock Runner support.
 * 
 * Follows Clean Architecture principles:
 * - Service layer (Framework) -> Use Case layer -> Domain layer
 * - Mock-First development approach for testing and validation
 */
class AIRouterService : Service() {
    companion object {
        private const val TAG = "AIRouterService"
        private const val PERMISSION = "com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE"
        private const val API_VERSION = 1
    }

    // Coroutine scope for background work
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Robust listener management with death monitoring
    private val listeners = RemoteCallbackList<IAIRouterListener>()
    
    // Client tracking for resource management
    @Volatile
    private var clientCount = 0
    private val clientCountMutex = Mutex()

    // Core components are now tied to the service's lifecycle.
    private lateinit var engineManager: AIEngineManager
    
    // --- Binder Stub Implementation ---
    private val binder = object : IAIRouterService.Stub() {
        override fun getApiVersion(): Int {
            Log.i(TAG, "getApiVersion() called")
            return API_VERSION
        }

        // === SIMPLIFIED API METHODS ===
        
        override fun sendChatRequest(requestId: String?, request: ChatRequest?) {
            Log.i(TAG, "[NEW] sendChatRequest() called: requestId=$requestId, request=$request")
            if (requestId == null || request == null) {
                Log.w(TAG, "sendChatRequest received null parameters: requestId=$requestId, request=$request")
                return
            }
            
            // Offload to coroutine for non-blocking processing
            serviceScope.launch {
                processChatRequest(request, requestId)
            }
        }
        
        override fun sendTTSRequest(requestId: String?, request: TTSRequest?) {
            Log.i(TAG, "[NEW] sendTTSRequest() called: requestId=$requestId, request=$request")
            if (requestId == null || request == null) {
                Log.w(TAG, "sendTTSRequest received null parameters: requestId=$requestId, request=$request")
                return
            }
            
            // Offload to coroutine for non-blocking processing
            serviceScope.launch {
                processTTSRequest(request, requestId)
            }
        }
        
        override fun sendASRRequest(requestId: String?, request: ASRRequest?) {
            Log.i(TAG, "[NEW] sendASRRequest() called: requestId=$requestId, request=$request")
            if (requestId == null || request == null) {
                Log.w(TAG, "sendASRRequest received null parameters: requestId=$requestId, request=$request")
                return
            }
            
            // Offload to coroutine for non-blocking processing
            serviceScope.launch {
                processASRRequest(request, requestId)
            }
        }

        override fun cancelRequest(requestId: String?): Boolean {
            Log.i(TAG, "cancelRequest() called: $requestId")
            return if (requestId != null) {
                engineManager.cancelRequest(requestId)
            } else {
                Log.w(TAG, "cancelRequest received null requestId")
                false
            }
        }

        override fun registerListener(listener: IAIRouterListener?) {
            Log.i(TAG, "registerListener() called: $listener")
            listener?.let { 
                listeners.register(it)
                // Increment client count
                CoroutineScope(Dispatchers.Main + serviceJob).launch {
                    clientCountMutex.withLock {
                        clientCount++
                        Log.d(TAG, "Client registered. Total clients: $clientCount")
                    }
                }
            }
        }

        override fun unregisterListener(listener: IAIRouterListener?) {
            Log.i(TAG, "unregisterListener() called: $listener")
            listener?.let { 
                listeners.unregister(it)
                // Decrement client count and check if we should stop
                CoroutineScope(Dispatchers.Main + serviceJob).launch {
                    clientCountMutex.withLock {
                        clientCount--
                        Log.d(TAG, "Client unregistered. Remaining clients: $clientCount")
                        
                        // If no clients remain, schedule service stop
                        if (clientCount <= 0) {
                            Log.i(TAG, "No clients remaining, scheduling service cleanup...")
                            // Give a small delay to handle any pending operations
                            delay(1000)
                            stopSelfIfNoClients()
                        }
                    }
                }
            }
        }

        override fun hasCapability(capabilityName: String?): Boolean {
            Log.i(TAG, "hasCapability() called: $capabilityName")
            return when (capabilityName) {
                "binary_data" -> true
                "streaming" -> true
                "image_processing" -> true
                "audio_processing" -> true
                "mock_runners" -> true
                else -> false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AIRouterService creating...")

        // Instantiate the dependency graph. The RouterConfigurator will handle
        // all the setup, including reading the config and registering runners.
        val configurator = RouterConfigurator(applicationContext)
        this.engineManager = configurator.engineManager

        Log.d(TAG, "AIRouterService created and configured.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Enforce signature-level permission (skip in debug builds for testing)
        if (!BuildConfig.DEBUG && checkCallingOrSelfPermission(PERMISSION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Denied binding: missing signature permission")
            return null
        }
        Log.i(TAG, "Client bound to AIRouterService (debug=${BuildConfig.DEBUG})")
        return binder
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // For AI Router Service, we want it to stop when no clients are bound
        // This ensures proper resource cleanup when not in use
        Log.d(TAG, "onStartCommand received, service will stop when no clients bound")
        return START_NOT_STICKY  // Service stops when no clients bound and process killed
    }

    override fun onDestroy() {
        super.onDestroy()
        engineManager.cleanup()
        serviceJob.cancel()
        Log.i(TAG, "AIRouterService destroyed")
    }
    
    // === RESPONSE CONVERSION & NOTIFICATION ===
    
    private fun convertToAIResponse(requestId: String, result: InferenceResult): AIResponse {
        if (result.error != null) {
            return AIResponse(
                requestId = requestId,
                text = "",
                isComplete = true,
                state = AIResponse.ResponseState.ERROR,
                error = result.error.message
            )
        }

        return AIResponse(
            requestId = requestId,
            text = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "",
            isComplete = !result.partial,
            state = if (result.partial) AIResponse.ResponseState.STREAMING else AIResponse.ResponseState.COMPLETED,
            audioData = result.outputs[InferenceResult.OUTPUT_AUDIO] as? ByteArray  // Extract audio data for TTS
        )
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
            error = errorMessage
        )
        notifyListeners(errorResponse)
    }
    
    // === SIMPLIFIED API PROCESSING METHODS ===
    
    /**
     * Process ChatRequest directly (simplified API)
     */
    private suspend fun processChatRequest(request: ChatRequest, requestId: String) {
        try {
            val inferenceRequest = InferenceRequest(
                sessionId = requestId,  // Use generated requestId as sessionId for tracking
                inputs = mapOf(InferenceRequest.INPUT_TEXT to buildChatPrompt(request.messages)),
                params = buildMap {
                    put("model_name", request.model)
                    request.temperature?.let { put("temperature", it) }
                    request.maxCompletionTokens?.let { put("max_tokens", it) }
                },
                timestamp = System.currentTimeMillis()
            )
            
            if (request.stream == true) {
                // Process as streaming request
                engineManager.processStream(inferenceRequest, CapabilityType.LLM)
                    .catch { error ->
                        Log.e(TAG, "Chat stream processing error", error)
                        notifyError(requestId, error.message ?: "Chat stream processing failed")
                    }
                    .collect { result ->
                        val response = convertToAIResponse(requestId, result)
                        notifyListeners(response)
                    }
            } else {
                // Process as regular request
                val result = engineManager.process(inferenceRequest, CapabilityType.LLM)
                val response = convertToAIResponse(requestId, result)
                notifyListeners(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing chat request", e)
            notifyError(requestId, e.message ?: "Unknown chat processing error")
        }
    }
    
    /**
     * Process TTSRequest directly (simplified API)
     */
    private suspend fun processTTSRequest(request: TTSRequest, requestId: String) {
        try {
            val inferenceRequest = InferenceRequest(
                sessionId = requestId,  // Use generated requestId as sessionId for tracking
                inputs = mapOf(InferenceRequest.INPUT_TEXT to request.input),
                params = buildMap {
                    put("model_name", request.model)
                    put("voice", request.voice)
                    request.speed?.let { put("speed", it) }
                    request.responseFormat?.let { put("format", it) }
                },
                timestamp = System.currentTimeMillis()
            )
            
            val result = engineManager.process(inferenceRequest, CapabilityType.TTS)
            val response = convertToAIResponse(requestId, result)
            notifyListeners(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing TTS request", e)
            notifyError(requestId, e.message ?: "Unknown TTS processing error")
        }
    }
    
    /**
     * Process ASRRequest directly (simplified API)
     */
    private suspend fun processASRRequest(request: ASRRequest, requestId: String) {
        try {
            val inferenceRequest = InferenceRequest(
                sessionId = requestId,  // Use generated requestId as sessionId for tracking
                inputs = mapOf(InferenceRequest.INPUT_AUDIO to request.file),
                params = buildMap {
                    put("model_name", request.model)
                    request.language?.let { put("language", it) }
                    request.responseFormat?.let { put("format", it) }
                    request.temperature?.let { put("temperature", it) }
                },
                timestamp = System.currentTimeMillis()
            )
            
            val result = engineManager.process(inferenceRequest, CapabilityType.ASR)
            val response = convertToAIResponse(requestId, result)
            notifyListeners(response)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing ASR request", e)
            notifyError(requestId, e.message ?: "Unknown ASR processing error")
        }
    }
    
    // === HELPER METHODS ===
    
    /**
     * Convert chat messages to a simple prompt string
     */
    private fun buildChatPrompt(messages: List<ChatMessage>): String {
        return messages.joinToString("\n") { message ->
            "${message.role}: ${message.content}"
        }
    }

    private fun stopSelfIfNoClients() {
        Log.i(TAG, "stopSelfIfNoClients() called. Current client count: $clientCount")
        if (clientCount <= 0) {
            Log.i(TAG, "No clients remaining, stopping service.")
            stopSelf()
        } else {
            Log.i(TAG, "Clients still remaining, not stopping service.")
        }
    }
} 
