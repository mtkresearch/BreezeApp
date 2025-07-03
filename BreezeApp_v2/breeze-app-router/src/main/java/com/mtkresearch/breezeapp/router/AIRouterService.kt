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
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.router.injection.RouterConfigurator
import com.mtkresearch.breezeapp.router.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import android.os.RemoteCallbackList
import java.util.concurrent.ConcurrentHashMap

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
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Robust listener management with death monitoring
    private val listeners = object : RemoteCallbackList<IAIRouterListener>() {}
    
    // Core components are now tied to the service's lifecycle.
    private lateinit var engineManager: AIEngineManager
    
    // --- Binder Stub Implementation ---
    private val binder = object : IAIRouterService.Stub() {
        override fun getApiVersion(): Int {
            Log.i(TAG, "getApiVersion() called")
            return API_VERSION
        }

        override fun sendMessage(request: AIRequest?) {
            Log.i(TAG, "sendMessage() called: $request")
            if (request == null) {
                Log.w(TAG, "sendMessage received a null request.")
                return
            }
            
            // Offload to coroutine for non-blocking processing
            val requestJob = serviceScope.launch {
                processAIRequest(request)
            }
            
            // Track the request for potential cancellation
            engineManager.let { manager ->
                // Store the job for potential cancellation
                manager.javaClass.getDeclaredField("activeRequests").apply {
                    isAccessible = true
                    (get(manager) as ConcurrentHashMap<String, Job>)[request.id] = requestJob
                }
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
            if (listener != null) listeners.register(listener)
        }

        override fun unregisterListener(listener: IAIRouterListener?) {
            Log.i(TAG, "unregisterListener() called: $listener")
            if (listener != null) listeners.unregister(listener)
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
        // Headless testing can be handled by directly interacting with the manager or runners
        // if needed, but the explicit handleTestIntent is removed for simplification.
        Log.d(TAG, "onStartCommand received, for now it does nothing.")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        engineManager.cleanup()
        serviceJob.cancel()
        Log.i(TAG, "AIRouterService destroyed")
    }
    
    /**
     * Process AI request through AIEngineManager
     */
    private suspend fun processAIRequest(request: AIRequest) {
        try {
            // Convert AIRequest to InferenceRequest with proper data mapping
            val inferenceRequest = createInferenceRequest(request)
            
            // Determine capability based on request type
            val capability = determineCapability(request)
            
            // Check if streaming is requested
            val isStreamingRequest = request.options["streaming"] == "true"
            
            if (isStreamingRequest) {
                // Process as streaming request
                engineManager.processStream(inferenceRequest, capability)
                    .catch { error ->
                        Log.e(TAG, "Stream processing error", error)
                        notifyError(request.id, error.message ?: "Stream processing failed")
                    }
                    .collect { result ->
                        val response = convertToAIResponse(request.id, result)
                        notifyListeners(response)
                    }
            } else {
                // Process as regular request
                val result = engineManager.process(inferenceRequest, capability)
                val response = convertToAIResponse(request.id, result)
                notifyListeners(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing AI request", e)
            notifyError(request.id, e.message ?: "Unknown processing error")
        }
    }
    
    /**
     * Create InferenceRequest from AIRequest with proper data type mapping
     */
    private fun createInferenceRequest(request: AIRequest): InferenceRequest {
        // Helper to read from options, with fallback for Bundle from AIDL
        fun getOption(key: String): String? {
            return request.options[key] ?: (request.options as? android.os.Bundle)?.getString(key)
        }
        
        val inputs = mutableMapOf<String, Any>()
        
        // Always include text input
        inputs[InferenceRequest.INPUT_TEXT] = request.text
        
        // Handle capability-specific data from options
        val requestType = getOption("request_type") ?: getOption(AIRequest.OptionKeys.REQUEST_TYPE)
        
        when (requestType) {
            "image_analysis" -> {
                // Extract base64 image data and decode it
                val imageDataBase64 = getOption("image_data")
                if (imageDataBase64 != null) {
                    try {
                        val imageBytes = android.util.Base64.decode(imageDataBase64, android.util.Base64.DEFAULT)
                        inputs[InferenceRequest.INPUT_IMAGE] = imageBytes
                        Log.d(TAG, "Image data converted: ${imageBytes.size} bytes")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode image data", e)
                    }
                } else {
                    Log.w(TAG, "No image_data found in request options")
                }
            }
            "speech_recognition" -> {
                // Extract base64 audio data and decode it
                val audioDataBase64 = getOption("audio_data")
                if (audioDataBase64 != null) {
                    try {
                        val audioBytes = android.util.Base64.decode(audioDataBase64, android.util.Base64.DEFAULT)
                        inputs[InferenceRequest.INPUT_AUDIO] = audioBytes
                        Log.d(TAG, "Audio data converted: ${audioBytes.size} bytes")
                        
                        // Add audio format info if available
                        val audioFormat = getOption("audio_format")
                        if (audioFormat != null) {
                            inputs[InferenceRequest.INPUT_AUDIO_ID] = "format_$audioFormat"
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to decode audio data", e)
                    }
                } else {
                    Log.w(TAG, "No audio_data found in request options")
                }
            }
            "speech_synthesis" -> {
                // TTS only needs text input, which is already added
                inputs["voice"] = getOption("voice") ?: "default"
                inputs["speed"] = getOption("speed") ?: "1.0"
            }
            "content_moderation" -> {
                // Guardrail only needs text input, which is already added
                inputs["check_type"] = getOption("check_type") ?: "safety"
            }
        }
        
        // Convert options map for params, handling Bundle type
        val params = mutableMapOf<String, Any>()
        request.options.forEach { (key, value) ->
            params[key] = value?.toString() ?: ""
        }
        
        return InferenceRequest(
            sessionId = request.sessionId,
            inputs = inputs,
            params = params,
            timestamp = request.timestamp
        )
    }
    
    private fun determineCapability(request: AIRequest): CapabilityType {
        return when (request.options["request_type"]) {
            "image_analysis" -> CapabilityType.VLM
            "speech_recognition" -> CapabilityType.ASR
            "speech_synthesis" -> CapabilityType.TTS
            "content_moderation" -> CapabilityType.GUARDIAN
            else -> CapabilityType.LLM // Default to LLM for text chat
        }
    }
    
    private fun convertToAIResponse(requestId: String, result: InferenceResult): AIResponse {
        val stringMetadata = result.metadata.mapValues { it.value.toString() }
        return if (result.error != null) {
            AIResponse(
                requestId = requestId,
                text = "",
                isComplete = true,
                state = AIResponse.ResponseState.ERROR,
                apiVersion = API_VERSION,
                binaryAttachments = emptyList(),
                metadata = stringMetadata,
                error = result.error.message
            )
        } else {
            AIResponse(
                requestId = requestId,
                text = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "",
                isComplete = !result.partial,
                state = if (result.partial) AIResponse.ResponseState.STREAMING else AIResponse.ResponseState.COMPLETED,
                apiVersion = API_VERSION,
                binaryAttachments = emptyList(),
                metadata = stringMetadata
            )
        }
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
        notifyListeners(errorResponse)
    }
} 