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
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import com.mtkresearch.breezeapp.router.domain.model.*
import com.mtkresearch.breezeapp.router.data.runner.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import android.os.RemoteCallbackList

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

    // Service configuration (set via initialize)
    @Volatile
    private var configuration: Configuration? = null
    
    // Use Case layer dependencies
    private val aiEngineManager = AIEngineManager()
    private val runnerRegistry = RunnerRegistry.getInstance()
    
    // Service initialization flag
    @Volatile
    private var isInitialized = false

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
            
            // Initialize Mock Runners based on configuration
            serviceScope.launch {
                initializeMockRunners(config)
                isInitialized = true
                Log.i(TAG, "AIRouterService initialized successfully with Mock Runners")
            }
        }

        override fun sendMessage(request: AIRequest?) {
            Log.i(TAG, "sendMessage() called: $request")
            if (request == null) return
            
            if (!isInitialized) {
                Log.w(TAG, "Service not initialized, sending error response")
                notifyError(request.id, "Service not initialized. Please call initialize() first.")
                return
            }
            
            // Offload to coroutine for non-blocking processing
            serviceScope.launch {
                processAIRequest(request)
            }
        }

        override fun cancelRequest(requestId: String?): Boolean {
            Log.i(TAG, "cancelRequest() called: $requestId")
            // TODO: Implement cancellation logic in AIEngineManager
            return false // Mock: always fail for now
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
        Log.d(TAG, "AIRouterService created")
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
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Support for headless testing via ADB
        intent?.let { testIntent ->
            when (testIntent.action) {
                "com.mtkresearch.breezeapp.TEST_MOCK_RUNNERS" -> {
                    Log.d(TAG, "Test command received via ADB")
                    serviceScope.launch {
                        performHeadlessTest()
                    }
                }

                else -> {}
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        aiEngineManager.cleanup()
        serviceJob.cancel()
        Log.i(TAG, "AIRouterService destroyed")
    }
    
    /**
     * Initialize Mock Runners based on configuration
     */
    private suspend fun initializeMockRunners(config: Configuration) {
        try {
            Log.d(TAG, "Initializing Mock Runners...")
            
            // Register Mock Runners
            runnerRegistry.register("MockLLMRunner") { MockLLMRunner() }
            runnerRegistry.register("MockASRRunner") { MockASRRunner() }
            runnerRegistry.register("MockTTSRunner") { MockTTSRunner() }
            runnerRegistry.register("MockVLMRunner") { MockVLMRunner() }
            runnerRegistry.register("MockGuardrailRunner") { MockGuardrailRunner() }
            
            // Set up default runner mappings based on config
            val defaultMappings = mutableMapOf<CapabilityType, String>()
            config.runnerConfigurations.forEach { (taskType, runnerType) ->
                val capability = mapTaskTypeToCapability(taskType)
                val runnerName = when (runnerType) {
                    Configuration.RunnerType.MOCK -> getMockRunnerForCapability(capability)
                    else -> getMockRunnerForCapability(capability) // Default to mock for now
                }
                defaultMappings[capability] = runnerName
            }
            
            aiEngineManager.setDefaultRunners(defaultMappings)
            
            Log.d(TAG, "Mock Runners initialized successfully: ${runnerRegistry.getRegisteredRunners()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Mock Runners", e)
        }
    }
    
    /**
     * Process AI request through AIEngineManager
     */
    private suspend fun processAIRequest(request: AIRequest) {
        try {
            // Convert AIRequest to InferenceRequest
            val inferenceRequest = InferenceRequest(
                sessionId = request.sessionId,
                inputs = mapOf(InferenceRequest.INPUT_TEXT to request.text),
                params = request.options,
                timestamp = request.timestamp
            )
            
            // Determine capability based on request type
            val capability = determineCapability(request)
            
            // Check if streaming is requested
            val isStreamingRequest = request.options["streaming"] == "true"
            
            if (isStreamingRequest) {
                // Process as streaming request
                aiEngineManager.processStream(inferenceRequest, capability)
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
                val result = aiEngineManager.process(inferenceRequest, capability)
                val response = convertToAIResponse(request.id, result)
                notifyListeners(response)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing AI request", e)
            notifyError(request.id, e.message ?: "Unknown processing error")
        }
    }
    
    /**
     * Perform headless test for ADB validation
     */
    private suspend fun performHeadlessTest() {
        Log.d(TAG, "=== Starting Headless Mock Runner Test ===")
        
        try {
            // Test LLM Runner
            val llmRequest = InferenceRequest(
                sessionId = "test_session_llm",
                inputs = mapOf(InferenceRequest.INPUT_TEXT to "Test LLM message"),
                timestamp = System.currentTimeMillis()
            )
            
            val llmResult = aiEngineManager.process(llmRequest, CapabilityType.LLM)
            Log.d(TAG, "LLM Test Result: ${llmResult.outputs}")
            
            // Test ASR Runner
            val asrRequest = InferenceRequest(
                sessionId = "test_session_asr",
                inputs = mapOf(
                    InferenceRequest.INPUT_AUDIO to ByteArray(1000) { it.toByte() },
                    InferenceRequest.INPUT_AUDIO_ID to "test_audio_1"
                ),
                timestamp = System.currentTimeMillis()
            )
            
            val asrResult = aiEngineManager.process(asrRequest, CapabilityType.ASR)
            Log.d(TAG, "ASR Test Result: ${asrResult.outputs}")
            
            Log.d(TAG, "=== Headless Test Completed Successfully ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== Headless Test Failed ===", e)
        }
    }

    // Utility methods...
    
    private fun mapTaskTypeToCapability(taskType: Configuration.AITaskType): CapabilityType {
        return when (taskType) {
            Configuration.AITaskType.TEXT_GENERATION -> CapabilityType.LLM
            Configuration.AITaskType.IMAGE_ANALYSIS -> CapabilityType.VLM
            Configuration.AITaskType.SPEECH_RECOGNITION -> CapabilityType.ASR
            Configuration.AITaskType.SPEECH_SYNTHESIS -> CapabilityType.TTS
            Configuration.AITaskType.CONTENT_MODERATION -> CapabilityType.GUARDIAN
            else -> {
                Log.w(TAG, "Unsupported AITaskType '$taskType', falling back to LLM.")
                CapabilityType.LLM
            }
        }
    }
    
    private fun getMockRunnerForCapability(capability: CapabilityType): String {
        return when (capability) {
            CapabilityType.LLM -> "MockLLMRunner"
            CapabilityType.VLM -> "MockVLMRunner"
            CapabilityType.ASR -> "MockASRRunner"
            CapabilityType.TTS -> "MockTTSRunner"
            CapabilityType.GUARDIAN -> "MockGuardrailRunner"
        }
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