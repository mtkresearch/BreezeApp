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
import com.mtkresearch.breezeapp.router.notification.ServiceNotificationManager
import com.mtkresearch.breezeapp.router.status.RouterStatusManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import android.os.RemoteCallbackList
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.atomic.AtomicInteger
import com.mtkresearch.breezeapp.router.BuildConfig

/**
 * AIRouterService - Foreground Service for AI Processing
 *
 * This service exposes the IAIRouterService AIDL interface for IPC and runs as a foreground
 * service to ensure reliable AI processing capabilities. It enforces signature-level permission,
 * provides transparent status updates via notifications, and delegates business logic 
 * to AIEngineManager (use case layer) with Mock Runner support.
 * 
 * Follows Clean Architecture principles:
 * - Service layer (Framework) -> Use Case layer -> Domain layer
 * - Single Responsibility: Service lifecycle + IPC interface
 * - Dependency Inversion: Depends on abstractions (AIEngineManager, RouterStatusManager)
 * - Open/Closed: Extensible through dependency injection
 * 
 * Foreground Service Benefits:
 * - Protected from aggressive system kills
 * - Transparent user communication via notifications
 * - Reliable availability for client wake-up scenarios
 * - Consistent performance for long-running AI operations
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
    
    // Active request tracking for status management
    private val activeRequestCount = AtomicInteger(0)

    // Core components following dependency injection principles
    private lateinit var engineManager: AIEngineManager
    private lateinit var notificationManager: ServiceNotificationManager
    private lateinit var statusManager: RouterStatusManager
    
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
                Log.d(TAG, "Client listener registered successfully")
            }
        }

        override fun unregisterListener(listener: IAIRouterListener?) {
            Log.i(TAG, "unregisterListener() called: $listener")
            listener?.let { 
                listeners.unregister(it)
                Log.d(TAG, "Client listener unregistered successfully")
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
        Log.d(TAG, "AIRouterService creating as foreground service...")

        // Initialize notification system first (required for foreground service)
        initializeNotificationSystem()
        
        // Start as foreground service immediately
        startForegroundService()

        // Initialize core AI components
        initializeAIComponents()

        Log.i(TAG, "AIRouterService created and running in foreground")
    }
    
    /**
     * Initializes the notification system following clean architecture principles.
     * Separates infrastructure concerns from business logic.
     */
    private fun initializeNotificationSystem() {
        notificationManager = ServiceNotificationManager(applicationContext)
        notificationManager.createNotificationChannel()
        
        // Check if notifications are enabled and log guidance for users
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled for BreezeApp Router")
            Log.i(TAG, "To see service status, enable notifications in:")
            Log.i(TAG, "   Settings > Apps > BreezeApp Router > Notifications")
        } else {
            Log.d(TAG, "Notifications are enabled - service status will be visible")
        }
        
        statusManager = RouterStatusManager(this, notificationManager)
        Log.d(TAG, "Notification system initialized")
    }
    
    /**
     * Starts the service in foreground mode with initial notification.
     * This ensures the service is protected from system kills.
     */
    private fun startForegroundService() {
        val initialNotification = notificationManager.createNotification(ServiceState.Ready)
        startForeground(RouterStatusManager.FOREGROUND_NOTIFICATION_ID, initialNotification)
        Log.i(TAG, "Service promoted to foreground with notification")
    }
    
    /**
     * Initializes AI engine components using dependency injection.
     * Follows single responsibility principle by separating concerns.
     */
    private fun initializeAIComponents() {
        val configurator = RouterConfigurator(applicationContext)
        engineManager = configurator.engineManager
        
        // Set service to ready state
        statusManager.setReady()
        Log.d(TAG, "AI components initialized successfully")
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
        // Foreground service should persist and restart if killed by system
        // This ensures reliable AI service availability for client applications
        val startReason = intent?.getStringExtra("start_reason") ?: "unknown"
        Log.i(TAG, "onStartCommand received - reason: $startReason, maintaining foreground service")
        
        // Ensure we're in foreground mode (defensive programming)
        if (!::notificationManager.isInitialized || !::statusManager.isInitialized) {
            Log.w(TAG, "Service components not initialized in onStartCommand, initializing now...")
            initializeNotificationSystem()
            startForegroundService()
            initializeAIComponents()
        } else {
            Log.d(TAG, "Service components already initialized")
        }
        
        return START_STICKY  // Service restarts if killed by system
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "AIRouterService destroying...")
        
        // Clean shutdown following proper order
        cleanupResources()
        
        Log.i(TAG, "AIRouterService destroyed")
    }
    
    /**
     * Performs clean resource cleanup following proper shutdown order.
     * Ensures graceful service termination.
     */
    private fun cleanupResources() {
        try {
            // Update status to indicate shutdown
            if (::statusManager.isInitialized) {
                statusManager.setError("Service shutting down", false)
            }
            
            // Cleanup AI engine resources
            if (::engineManager.isInitialized) {
                engineManager.cleanup()
            }
            
            // Cancel all coroutines
            serviceJob.cancel()
            
            Log.d(TAG, "Resource cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource cleanup", e)
        }
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
     * Process ChatRequest directly (simplified API) with status management.
     */
    private suspend fun processChatRequest(request: ChatRequest, requestId: String) {
        val currentActiveRequests = activeRequestCount.incrementAndGet()
        statusManager.setProcessing(currentActiveRequests)
        
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
            statusManager.setError("Chat processing failed: ${e.message}")
            notifyError(requestId, e.message ?: "Unknown chat processing error")
        } finally {
            val remainingRequests = activeRequestCount.decrementAndGet()
            if (remainingRequests <= 0) {
                statusManager.setReady()
            } else {
                statusManager.setProcessing(remainingRequests)
            }
        }
    }
    
    /**
     * Process TTSRequest directly (simplified API) with status management.
     */
    private suspend fun processTTSRequest(request: TTSRequest, requestId: String) {
        val currentActiveRequests = activeRequestCount.incrementAndGet()
        statusManager.setProcessing(currentActiveRequests)
        
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
            statusManager.setError("TTS processing failed: ${e.message}")
            notifyError(requestId, e.message ?: "Unknown TTS processing error")
        } finally {
            val remainingRequests = activeRequestCount.decrementAndGet()
            if (remainingRequests <= 0) {
                statusManager.setReady()
            } else {
                statusManager.setProcessing(remainingRequests)
            }
        }
    }
    
    /**
     * Process ASRRequest directly (simplified API) with status management.
     */
    private suspend fun processASRRequest(request: ASRRequest, requestId: String) {
        val currentActiveRequests = activeRequestCount.incrementAndGet()
        statusManager.setProcessing(currentActiveRequests)
        
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
            statusManager.setError("ASR processing failed: ${e.message}")
            notifyError(requestId, e.message ?: "Unknown ASR processing error")
        } finally {
            val remainingRequests = activeRequestCount.decrementAndGet()
            if (remainingRequests <= 0) {
                statusManager.setReady()
            } else {
                statusManager.setProcessing(remainingRequests)
            }
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
}