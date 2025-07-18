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
import kotlinx.coroutines.isActive
import android.os.RemoteCallbackList
import com.mtkresearch.breezeapp.router.error.RequestProcessingHelper
import com.mtkresearch.breezeapp.router.core.ServiceNotificationManager
import com.mtkresearch.breezeapp.router.core.RouterStatusManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import com.mtkresearch.breezeapp.router.system.GlobalLibraryTracker
import com.mtkresearch.breezeapp.router.system.ResourceHealthMonitor
import com.mtkresearch.breezeapp.router.system.NativeLibraryGuardian

/**
 * AIRouterService - Foreground Service for AI Processing
 *
 * This service exposes the IAIRouterService AIDL interface for IPC and runs as a foreground
 * service to ensure reliable AI processing capabilities. It enforces signature-level permission,
 * provides transparent status updates via notifications, and delegates business logic 
 * to AIEngineManager (use case layer) with Mock Runner support.
 * 
 * ## Architecture
 * Follows Clean Architecture principles:
 * - Service layer (Framework) -> Use Case layer -> Domain layer
 * - Single Responsibility: Service lifecycle + IPC interface
 * - Dependency Inversion: Depends on abstractions (AIEngineManager, RouterStatusManager)
 * - Open/Closed: Extensible through dependency injection
 * 
 * ## Foreground Service Benefits
 * - Protected from aggressive system kills
 * - Transparent user communication via notifications
 * - Reliable availability for client wake-up scenarios
 * - Consistent performance for long-running AI operations
 * 
 * ## Usage
 * Clients bind to this service using AIDL interface and call methods like:
 * - [sendChatRequest] for text generation
 * - [sendTTSRequest] for text-to-speech
 * - [sendASRRequest] for speech-to-text
 * 
 * @see IAIRouterService AIDL interface definition
 * @see AIEngineManager for business logic implementation
 */
class AIRouterService : Service() {
    companion object {
        private const val TAG = "AIRouterService"
        private const val PERMISSION = "com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE"
        private const val API_VERSION = 1
        
        // Performance and resource management constants
        private const val MAX_TRACKED_REQUESTS = 100
        private const val REQUEST_RETENTION_TIME_MS = 5 * 60 * 1000L // 5 minutes
        private const val MEMORY_CHECK_INTERVAL_MS = 30 * 1000L // 30 seconds
    }

    // Coroutine scope for background work
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    // Robust listener management with death monitoring
    private val listeners = RemoteCallbackList<IAIRouterListener>()
    
    // Active request tracking for status management with thread-safe operations
    private val activeRequestCount = AtomicInteger(0)
    private val requestTracker = ConcurrentHashMap<String, Long>()
    
    // Client timeout management for resource cleanup
    private val lastClientActivity = AtomicLong(System.currentTimeMillis())
    private var clientTimeoutJob: Job? = null
    
    // Resource health monitoring
    private var healthMonitoringJob: Job? = null
    
    // Broadcast receiver for notification actions
    private val stopServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            if (intent?.action == ServiceNotificationManager.ACTION_STOP_SERVICE) {
                Log.i(TAG, "Stop service requested by user via notification")
                
                // Update notification to show stopping state
                statusManager.setError("Service shutting down")
                
                // Immediately release all resources before stopping
                releaseAllResourcesImmediately()
                
                // Stop the service
                stopSelf()
            }
        }
    }

    // Core components following dependency injection principles
    private lateinit var engineManager: AIEngineManager
    private lateinit var notificationManager: ServiceNotificationManager
    private lateinit var statusManager: RouterStatusManager
    private lateinit var resourceHealthMonitor: com.mtkresearch.breezeapp.router.system.ResourceHealthMonitor
    
    // Lazy initialization to avoid dependency order issues
    private val requestHelper by lazy {
        RequestProcessingHelper(
            engineManager = engineManager,
            statusManager = statusManager,
            activeRequestCount = activeRequestCount,
            requestTracker = requestTracker,
            updateStatusAfterRequestCompletion = ::updateStatusAfterRequestCompletion,
            notifyError = ::notifyError
        )
    }
    
    // --- Binder Stub Implementation ---
    private val binder = object : IAIRouterService.Stub() {
        /**
         * Returns the current API version of the AI Router Service.
         * 
         * Clients can use this to verify compatibility and adapt their behavior
         * based on the available features in this version.
         * 
         * @return Current API version number
         */
        override fun getApiVersion(): Int {
            Log.i(TAG, "getApiVersion() called")
            return API_VERSION
        }

        // === SIMPLIFIED API METHODS ===
        
        /**
         * Processes a chat completion request asynchronously.
         * 
         * Supports both streaming and non-streaming responses. For streaming requests,
         * partial responses will be delivered via the registered listener as they become
         * available. The final response will have isComplete=true.
         * 
         * @param requestId Unique identifier for tracking this request. Used for cancellation
         *                  and correlating responses with requests.
         * @param request Chat request containing messages, model preferences, and parameters.
         *                Supports streaming via request.stream flag.
         * 
         * @see ChatRequest for request format details
         * @see AIResponse for response format details
         * @see registerListener to receive responses
         */
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
        
        /**
         * Converts text to speech and returns audio data.
         * 
         * Processes the input text using the specified voice model and returns
         * the generated audio data in the response. The audio format depends on
         * the responseFormat parameter in the request.
         * 
         * @param requestId Unique identifier for tracking this request
         * @param request TTS request containing text, voice model, and audio parameters
         * 
         * @see TTSRequest for request format details
         * @see AIResponse.audioData for audio output format
         */
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
        
        /**
         * Converts speech audio to text transcription.
         * 
         * Processes the provided audio data and returns a text transcription.
         * Supports various audio formats and languages depending on the model used.
         * 
         * @param requestId Unique identifier for tracking this request
         * @param request ASR request containing audio data, model, and transcription parameters
         * 
         * @see ASRRequest for request format details
         * @see AIResponse.text for transcription output
         */
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

        /**
         * Cancels an active request by its ID.
         * 
         * Attempts to cancel the specified request if it's currently being processed.
         * This will stop any ongoing computation and update the service status accordingly.
         * 
         * @param requestId The ID of the request to cancel
         * @return true if the request was successfully cancelled, false if the request
         *         was not found or could not be cancelled
         */
        override fun cancelRequest(requestId: String?): Boolean {
            Log.i(TAG, "cancelRequest() called: $requestId")
            return if (requestId != null) {
                val cancelled = engineManager.cancelRequest(requestId)
                if (cancelled && requestTracker.containsKey(requestId)) {
                    // If request was actively tracked, decrement counter
                    requestTracker.remove(requestId)
                    val remainingRequests = activeRequestCount.decrementAndGet()
                    Log.d(TAG, "Cancelled active request $requestId (remaining: $remainingRequests)")
                    updateStatusAfterRequestCompletion(remainingRequests)
                }
                cancelled
            } else {
                Log.w(TAG, "cancelRequest received null requestId")
                false
            }
        }

        /**
         * Registers a listener to receive AI processing responses.
         * 
         * Clients must register a listener to receive responses from AI requests.
         * Multiple listeners can be registered, and all will receive responses.
         * The service automatically handles listener lifecycle and cleanup.
         * 
         * @param listener The listener implementation to receive AI responses
         * @see IAIRouterListener for callback interface details
         * @see unregisterListener to remove the listener
         */
        override fun registerListener(listener: IAIRouterListener?) {
            Log.i(TAG, "registerListener() called: $listener")
            listener?.let { 
                listeners.register(it)
                lastClientActivity.set(System.currentTimeMillis())
                cancelClientTimeout()
                updateClientCountNotification()
                Log.d(TAG, "Client listener registered successfully")
            }
        }

        /**
         * Unregisters a previously registered listener.
         * 
         * Removes the listener from receiving further AI responses. This should
         * be called when the client no longer needs to receive responses, typically
         * in the client's onDestroy() or similar cleanup method.
         * 
         * @param listener The listener to unregister
         * @see registerListener to add a listener
         */
        override fun unregisterListener(listener: IAIRouterListener?) {
            Log.i(TAG, "unregisterListener() called: $listener")
            listener?.let { 
                listeners.unregister(it)
                scheduleClientTimeoutIfNeeded()
                updateClientCountNotification()
                Log.d(TAG, "Client listener unregistered successfully")
            }
        }

        /**
         * Checks if the service supports a specific capability.
         * 
         * Clients can query for specific features before making requests to ensure
         * compatibility and graceful degradation when features are not available.
         * 
         * @param capabilityName The capability to check for. Supported capabilities:
         *                       - "binary_data": Support for binary audio/image data
         *                       - "streaming": Support for streaming responses
         *                       - "image_processing": Support for image/vision models
         *                       - "audio_processing": Support for TTS/ASR
         *                       - "mock_runners": Support for mock/testing runners
         * @return true if the capability is supported, false otherwise
         */
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

        // Note: JVM shutdown hooks don't work with Android Studio force-stop
        // Using alternative approach with service lifecycle and native cleanup

        // Initialize resource monitoring FIRST (required for health-aware notifications)
        initializeResourceMonitoring()
        
        // Initialize notification system with health monitor (required for foreground service)
        initializeNotificationSystem()
        
        // Start as foreground service immediately
        startForegroundService()

        // Initialize core AI components
        initializeAIComponents()
        
        // Register broadcast receiver for notification actions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopServiceReceiver, IntentFilter(ServiceNotificationManager.ACTION_STOP_SERVICE), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stopServiceReceiver, IntentFilter(ServiceNotificationManager.ACTION_STOP_SERVICE))
        }

        Log.i(TAG, "AIRouterService created and running in foreground")
    }
    
    /**
     * Initialize resource monitoring systems.
     */
    private fun initializeResourceMonitoring() {
        try {
            // Initialize global library tracker
            Log.d(TAG, "Initializing global library state tracking...")
            GlobalLibraryTracker.initialize(this)
            val diagnosticInfo = GlobalLibraryTracker.getDiagnosticInfo()
            Log.i(TAG, "Global library state: $diagnosticInfo")
            
            // Initialize resource health monitor
            Log.d(TAG, "Initializing resource health monitor...")
            resourceHealthMonitor = ResourceHealthMonitor(this)
            val healthReport = resourceHealthMonitor.performHealthCheck(forceCheck = true)
            Log.i(TAG, "Initial health status: ${healthReport.getSummary()}")
            
            if (healthReport.isCritical()) {
                Log.w(TAG, "CRITICAL HEALTH ISSUES DETECTED: ${healthReport.criticalIssues.joinToString(", ")}")
                Log.w(TAG, "Recommendations: ${healthReport.recommendations.joinToString(", ")}")
                
                // Perform preventive maintenance if recommended
                if (resourceHealthMonitor.shouldPerformPreventiveMaintenance()) {
                    Log.w(TAG, "Performing preventive maintenance due to health issues...")
                    performPreventiveMaintenance()
                }
            }
            
            // Start periodic health monitoring
            startPeriodicHealthMonitoring()
            
            Log.d(TAG, "Resource monitoring initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing resource monitoring", e)
            // Continue with service initialization even if monitoring fails
        }
    }
    
    /**
     * Start periodic health monitoring in background.
     */
    private fun startPeriodicHealthMonitoring() {
        healthMonitoringJob = serviceScope.launch {
            try {
                while (isActive) { // Check if coroutine is still active
                    delay(MEMORY_CHECK_INTERVAL_MS) // 30 seconds
                    
                    if (!isActive) break // Exit if cancelled
                    
                    if (::resourceHealthMonitor.isInitialized) {
                        val healthReport = resourceHealthMonitor.performHealthCheck()
                        
                        // Log health status periodically
                        Log.v(TAG, "Periodic health check: ${healthReport.getSummary()}")
                        
                        // Handle critical issues
                        if (healthReport.isCritical()) {
                            Log.w(TAG, "CRITICAL HEALTH ISSUES: ${healthReport.criticalIssues.joinToString(", ")}")
                            
                            // Perform automatic maintenance for critical issues
                            if (resourceHealthMonitor.shouldPerformPreventiveMaintenance()) {
                                Log.w(TAG, "Performing automatic maintenance due to critical health issues...")
                                performPreventiveMaintenance()
                            }
                        }
                        
                        // Update notification with health status if needed
                        updateNotificationWithHealthStatus(healthReport)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Normal cancellation - don't log as error
                Log.d(TAG, "Health monitoring cancelled (normal shutdown)")
            } catch (e: Exception) {
                Log.e(TAG, "Error during periodic health monitoring", e)
            }
        }
        
        Log.d(TAG, "Periodic health monitoring started")
    }
    
    /**
     * Update notification with health status information.
     */
    private fun updateNotificationWithHealthStatus(healthReport: com.mtkresearch.breezeapp.router.system.ResourceHealthMonitor.HealthReport) {
        try {
            // Only update notification if there are warnings or critical issues
            if (healthReport.hasWarnings()) {
                val currentState = when {
                    activeRequestCount.get() > 0 -> ServiceState.Processing(activeRequestCount.get())
                    else -> ServiceState.Ready
                }
                
                // Update status manager (this will trigger notification update)
                statusManager.updateState(currentState)
                
                Log.d(TAG, "Updated notification with health status: ${healthReport.overallHealth}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification with health status", e)
        }
    }
    
    /**
     * Perform preventive maintenance based on health monitor recommendations.
     */
    private fun performPreventiveMaintenance() {
        try {
            Log.i(TAG, "Starting preventive maintenance...")
            
            // Reset service state counters
            activeRequestCount.set(0)
            requestTracker.clear()
            lastClientActivity.set(System.currentTimeMillis())
            
            // Force garbage collection
            System.gc()
            Thread.sleep(100)
            
            Log.i(TAG, "Preventive maintenance completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during preventive maintenance", e)
        }
    }
    
    /**
     * Handle health view request from notification.
     */
    private fun handleHealthViewRequest() {
        try {
            if (::resourceHealthMonitor.isInitialized) {
                val healthReport = resourceHealthMonitor.performHealthCheck(forceCheck = true)
                Log.i(TAG, "=== HEALTH REPORT ===")
                Log.i(TAG, "Overall Health: ${healthReport.overallHealth}")
                Log.i(TAG, "Memory Health: ${healthReport.memoryHealth}")
                Log.i(TAG, "Native Library Health: ${healthReport.nativeLibraryHealth}")
                Log.i(TAG, "Service Health: ${healthReport.serviceHealth}")
                
                if (healthReport.criticalIssues.isNotEmpty()) {
                    Log.w(TAG, "Critical Issues: ${healthReport.criticalIssues.joinToString(", ")}")
                }
                
                if (healthReport.recommendations.isNotEmpty()) {
                    Log.i(TAG, "Recommendations: ${healthReport.recommendations.joinToString(", ")}")
                }
                
                Log.i(TAG, "Diagnostic Info: ${healthReport.diagnosticInfo}")
                Log.i(TAG, "==================")
                
                // Update notification to show latest health status
                statusManager.updateState(statusManager.getCurrentState())
            } else {
                Log.w(TAG, "Health monitor not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling health view request", e)
        }
    }
    
    /**
     * Initializes the notification system following clean architecture principles.
     * Separates infrastructure concerns from business logic.
     */
    private fun initializeNotificationSystem() {
        // Always create notification manager with health monitoring
        notificationManager = ServiceNotificationManager(applicationContext, resourceHealthMonitor)
        
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
        Log.d(TAG, "Notification system initialized with health monitoring")
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
        
        // RequestProcessingHelper is now lazy-initialized when first accessed
        
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
        
        // Clear notification immediately to avoid confusion
        try {
            if (::notificationManager.isInitialized) {
                notificationManager.clearNotification()
                Log.d(TAG, "Notification cleared during service destruction")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing notification", e)
        }
        
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
            
            // Clear request tracking
            requestTracker.clear()
            activeRequestCount.set(0)
            
            // Cleanup AI engine resources
            if (::engineManager.isInitialized) {
                engineManager.cleanup()
            }
            
            // Cancel all coroutines
            serviceJob.cancel()
            
            // Cancel health monitoring
            healthMonitoringJob?.cancel()
            healthMonitoringJob = null
            
            // Unregister broadcast receiver
            try {
                unregisterReceiver(stopServiceReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering stop service receiver", e)
            }
            
            Log.d(TAG, "Resource cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during resource cleanup", e)
        }
    }
    
    /**
     * Updates service status after request completion with defensive programming.
     * Ensures notification count never gets stuck or becomes negative.
     */
    private fun updateStatusAfterRequestCompletion(remainingRequests: Int) {
        // Defensive programming: ensure count never goes negative
        val actualRemaining = maxOf(0, remainingRequests)
        
        // Double-check with tracker size for consistency
        val trackedRequests = requestTracker.size
        if (actualRemaining != trackedRequests) {
            Log.w(TAG, "Request count mismatch: counter=$actualRemaining, tracker=$trackedRequests. Syncing...")
            activeRequestCount.set(trackedRequests)
        }
        
        // Update status based on actual remaining requests
        val finalCount = maxOf(actualRemaining, trackedRequests)
        if (finalCount <= 0) {
            statusManager.setReady()
            Log.d(TAG, "All requests completed - service ready")
        } else {
            statusManager.setProcessing(finalCount)
            Log.d(TAG, "Still processing $finalCount requests")
        }
    }
    
    /**
     * Cancels any pending client timeout job.
     */
    private fun cancelClientTimeout() {
        clientTimeoutJob?.cancel()
        clientTimeoutJob = null
    }
    
    /**
     * Schedules model unloading if no clients are connected.
     */
    private fun scheduleClientTimeoutIfNeeded() {
        if (listeners.registeredCallbackCount == 0) {
            clientTimeoutJob = serviceScope.launch {
                delay(REQUEST_RETENTION_TIME_MS) // 使用現有常數
                Log.i(TAG, "No clients for ${REQUEST_RETENTION_TIME_MS / 1000 / 60} minutes - unloading models to save memory")
                try {
                    engineManager.unloadAllModels()
                    statusManager.setReady()
                } catch (e: Exception) {
                    Log.e(TAG, "Error during client timeout cleanup", e)
                }
            }
        }
    }
    
    /**
     * Updates notification with current client count information.
     */
    private fun updateClientCountNotification() {
        val clientCount = listeners.registeredCallbackCount
        val currentState = when {
            clientCount == 0 -> ServiceState.Ready
            activeRequestCount.get() > 0 -> ServiceState.Processing(activeRequestCount.get())
            else -> ServiceState.Ready
        }
        
        // Update status manager with client count info
        statusManager.updateWithClientCount(currentState, clientCount)
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
        // Synchronize access to RemoteCallbackList to prevent concurrent broadcast issues
        synchronized(listeners) {
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
     * Process ChatRequest directly (simplified API) with unified error handling.
     */
    private suspend fun processChatRequest(request: ChatRequest, requestId: String) {
        val inferenceRequest = InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to buildChatPrompt(request.messages)),
            params = buildMap {
                put("model_name", request.model)
                request.temperature?.let { put("temperature", it) }
                request.maxCompletionTokens?.let { put("max_tokens", it) }
            },
            timestamp = System.currentTimeMillis()
        )
            
            if (request.stream == true) {
                // Process as streaming request using helper
                requestHelper.processStreamingRequest(
                    requestId, inferenceRequest, CapabilityType.LLM, "Chat"
                ) { result ->
                    val response = convertToAIResponse(requestId, result)
                    notifyListeners(response)
                }
                return // Exit early - streaming cleanup handled by helper
            } else {
                // Process as non-streaming request using helper
                val result = requestHelper.processNonStreamingRequest(
                    requestId, inferenceRequest, CapabilityType.LLM, "Chat"
                )
                
                result?.let {
                    val response = convertToAIResponse(requestId, it)
                    notifyListeners(response)
                }
                return // Exit early - cleanup handled by helper
            }
    }
    
    /**
     * Process TTSRequest directly (simplified API) with unified error handling.
     */
    private suspend fun processTTSRequest(request: TTSRequest, requestId: String) {
        val inferenceRequest = InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(InferenceRequest.INPUT_TEXT to request.input),
            params = buildMap {
                put("model_name", request.model)
                put("voice", request.voice)
                request.speed?.let { put("speed", it) }
                request.responseFormat?.let { put("format", it) }
            },
            timestamp = System.currentTimeMillis()
        )
        
        val result = requestHelper.processNonStreamingRequest(
            requestId, inferenceRequest, CapabilityType.TTS, "TTS"
        )
        
        result?.let {
            val response = convertToAIResponse(requestId, it)
            notifyListeners(response)
        }
    }
    
    /**
     * Process ASRRequest directly (simplified API) with unified error handling.
     */
    private suspend fun processASRRequest(request: ASRRequest, requestId: String) {
        val inferenceRequest = InferenceRequest(
            sessionId = requestId,
            inputs = mapOf(InferenceRequest.INPUT_AUDIO to request.file),
            params = buildMap {
                put("model_name", request.model)
                request.language?.let { put("language", it) }
                request.responseFormat?.let { put("format", it) }
                request.temperature?.let { put("temperature", it) }
            },
            timestamp = System.currentTimeMillis()
        )
        
        val result = requestHelper.processNonStreamingRequest(
            requestId, inferenceRequest, CapabilityType.ASR, "ASR"
        )
        
        result?.let {
            val response = convertToAIResponse(requestId, it)
            notifyListeners(response)
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

    /**
     * Immediately releases all resources and unloads models.
     * This is called when the user explicitly requests to stop the service.
     */
    private fun releaseAllResourcesImmediately() {
        Log.i(TAG, "Releasing all resources immediately due to user stop request.")
        try {
            // Update status to indicate shutdown
            if (::statusManager.isInitialized) {
                statusManager.setError("Service shutting down", false)
            }
            
            // Clear request tracking
            requestTracker.clear()
            activeRequestCount.set(0)
            
            // Cancel all coroutines
            serviceJob.cancel()
            
            // Cancel health monitoring
            healthMonitoringJob?.cancel()
            healthMonitoringJob = null
            
            // Unload all models
            if (::engineManager.isInitialized) {
                engineManager.unloadAllModels()
            }
            
            // Clear notification
            if (::notificationManager.isInitialized) {
                notificationManager.clearNotification()
            }
            
            // Unregister broadcast receiver
            try {
                unregisterReceiver(stopServiceReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering stop service receiver during immediate release", e)
            }
            
            Log.d(TAG, "All resources released immediately.")
        } catch (e: Exception) {
            Log.e(TAG, "Error during immediate resource release", e)
        }
    }
}