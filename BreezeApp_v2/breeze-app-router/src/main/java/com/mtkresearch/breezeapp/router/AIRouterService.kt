package com.mtkresearch.breezeapp.router

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.IAIRouterService
import com.mtkresearch.breezeapp.edgeai.IAIRouterListener
import com.mtkresearch.breezeapp.edgeai.model.AIRequest
import com.mtkresearch.breezeapp.edgeai.model.AIResponse
import com.mtkresearch.breezeapp.edgeai.model.Configuration
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.router.injection.RouterConfigurator
import com.mtkresearch.breezeapp.router.domain.model.*
import com.mtkresearch.breezeapp.edgeai.model.RequestPayload
import com.mtkresearch.breezeapp.edgeai.model.ResponseMetadata
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
            
            // Determine if streaming is appropriate based on the client's preference in the payload.
            val isStreamingRequest = when (val payload = request.payload) {
                is RequestPayload.TextChat -> payload.streaming
                is RequestPayload.ImageAnalysis -> payload.streaming
                is RequestPayload.AudioTranscription -> payload.streaming
                is RequestPayload.SpeechSynthesis -> payload.streaming
                else -> false // Guardian is non-streaming.
            }
            
            if (isStreamingRequest) {
                // Process as streaming request
                engineManager.processStream(inferenceRequest, capability)
                    .catch { error ->
                        Log.e(TAG, "Stream processing error", error)
                        notifyError(request.id, error.message ?: "Stream processing failed")
                    }
                    .collect { result ->
                        val response = convertToAIResponse(request.id, result, capability)
                        notifyListeners(response)
                    }
            } else {
                // Process as regular request
                val result = engineManager.process(inferenceRequest, capability)
                val response = convertToAIResponse(request.id, result, capability)
                notifyListeners(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing AI request", e)
            notifyError(request.id, e.message ?: "Unknown processing error")
        }
    }
    
    /**
     * Create InferenceRequest from the type-safe AIRequest.payload
     */
    private fun createInferenceRequest(request: AIRequest): InferenceRequest {
        val inputs = mutableMapOf<String, Any>()
        val params = mutableMapOf<String, Any>()

        when (val payload = request.payload) {
            is RequestPayload.TextChat -> {
                inputs[InferenceRequest.INPUT_TEXT] = payload.prompt
                payload.modelName?.let { params["model_name"] = it }
                payload.temperature?.let { params["temperature"] = it }
                payload.maxTokens?.let { params["max_tokens"] = it }
            }
            is RequestPayload.ImageAnalysis -> {
                inputs[InferenceRequest.INPUT_IMAGE] = payload.image
                payload.prompt?.let { inputs[InferenceRequest.INPUT_TEXT] = it }
                payload.modelName?.let { params["model_name"] = it }
            }
            is RequestPayload.AudioTranscription -> {
                inputs[InferenceRequest.INPUT_AUDIO] = payload.audio
                payload.language?.let { params["language"] = it }
                payload.modelName?.let { params["model_name"] = it }
            }
            is RequestPayload.SpeechSynthesis -> {
                inputs[InferenceRequest.INPUT_TEXT] = payload.text
                payload.voiceId?.let { params["voice"] = it }
                payload.speed?.let { params["speed"] = it }
            }
            is RequestPayload.ContentModeration -> {
                inputs[InferenceRequest.INPUT_TEXT] = payload.text
                payload.checkType?.let { params["check_type"] = it }
            }
        }
        
        return InferenceRequest(
            sessionId = request.sessionId,
            inputs = inputs,
            params = params,
            timestamp = request.timestamp
        )
    }
    
    private fun determineCapability(request: AIRequest): CapabilityType {
        return when (request.payload) {
            is RequestPayload.TextChat -> CapabilityType.LLM
            is RequestPayload.ImageAnalysis -> CapabilityType.VLM
            is RequestPayload.AudioTranscription -> CapabilityType.ASR
            is RequestPayload.SpeechSynthesis -> CapabilityType.TTS
            is RequestPayload.ContentModeration -> CapabilityType.GUARDIAN
        }
    }
    
    private fun convertToAIResponse(requestId: String, result: InferenceResult, capability: CapabilityType): AIResponse {
        if (result.error != null) {
            return AIResponse(
                requestId = requestId,
                text = "",
                isComplete = true,
                state = AIResponse.ResponseState.ERROR,
                apiVersion = API_VERSION,
                metadata = null,
                error = result.error.message
            )
        }

        // Build the metadata object based on the capability
        val responseMetadata = createResponseMetadata(result.metadata, capability)

        return AIResponse(
            requestId = requestId,
            text = result.outputs[InferenceResult.OUTPUT_TEXT] as? String ?: "",
            isComplete = !result.partial,
            state = if (result.partial) AIResponse.ResponseState.STREAMING else AIResponse.ResponseState.COMPLETED,
            apiVersion = API_VERSION,
            metadata = responseMetadata
        )
    }

    private fun createResponseMetadata(metadata: Map<String, Any>, capability: CapabilityType): ResponseMetadata? {
        val modelName = metadata["model_name"] as? String ?: return null
        val processingTimeMs = (metadata["processing_time_ms"] as? Number)?.toLong() ?: return null
        val backend = metadata["runtime_backend"] as? String

        val standardMetadata = ResponseMetadata.Standard(
            modelName = modelName,
            processingTimeMs = processingTimeMs,
            backend = backend
        )

        return when (capability) {
            CapabilityType.LLM -> {
                val tokenCount = (metadata["token_count"] as? Number)?.toInt()
                if (tokenCount != null) {
                    ResponseMetadata.TextGeneration(standardMetadata, tokenCount)
                } else {
                    standardMetadata
                }
            }
            CapabilityType.ASR -> {
                val confidence = (metadata["confidence"] as? Number)?.toFloat()
                val audioDurationMs = (metadata["audio_duration_ms"] as? Number)?.toLong()
                if (confidence != null && audioDurationMs != null) {
                    ResponseMetadata.AudioTranscription(standardMetadata, confidence, audioDurationMs)
                } else {
                    standardMetadata
                }
            }
            CapabilityType.TTS -> {
                val audioDurationMs = (metadata["audio_duration_ms"] as? Number)?.toLong()
                val voiceId = metadata["voice_id"] as? String
                if (audioDurationMs != null) {
                    ResponseMetadata.SpeechSynthesis(standardMetadata, audioDurationMs, voiceId)
                } else {
                    standardMetadata
                }
            }
            else -> standardMetadata
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
            metadata = null,
            error = errorMessage
        )
        notifyListeners(errorResponse)
    }
} 