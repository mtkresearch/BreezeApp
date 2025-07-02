package com.mtkresearch.breezeapp.router.client

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterService
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
/**
 * ViewModel for the Breeze App Router Client.
 * 
 * This ViewModel handles all business logic for interacting with the AI Router Service, including:
 * - Service connection management
 * - Service initialization
 * - Request creation and submission
 * - Response handling
 * - UI state management
 *
 * The ViewModel exposes a [StateFlow] of [UiState] for reactive UI updates.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var routerService: IAIRouterService? = null
    private val TAG = "BreezeAppRouterClientViewModel"

    /**
     * ServiceConnection implementation for binding to the AI Router Service.
     * Handles connection callbacks and listener registration.
     */
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            logMessage("✅ Service connected successfully")
            logMessage("   Component: ${name?.packageName}.${name?.className}")
            routerService = IAIRouterService.Stub.asInterface(service)
            try {
                routerService?.registerListener(callback)
                logMessage("   ✅ Listener registered successfully")
            } catch (e: Exception) {
                logMessage("   ❌ Failed to register listener: ${e.message}")
            }
            _uiState.update { it.copy(connectionStatus = "Connected", isConnected = true) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logMessage("❌ Service disconnected")
            logMessage("   Component: ${name?.packageName}.${name?.className}")
            routerService = null
            _uiState.update { it.copy(connectionStatus = "Disconnected", isConnected = false, isInitialized = false) }
        }
        
        override fun onBindingDied(name: ComponentName?) {
            logMessage("💀 Service binding died")
            logMessage("   Component: ${name?.packageName}.${name?.className}")
            routerService = null
            _uiState.update { it.copy(connectionStatus = "Binding Died", isConnected = false, isInitialized = false) }
        }
        
        override fun onNullBinding(name: ComponentName?) {
            logMessage("❌ Service returned null binding")
            logMessage("   Component: ${name?.packageName}.${name?.className}")
            _uiState.update { it.copy(connectionStatus = "Null Binding", isConnected = false) }
        }
    }

    /**
     * Callback implementation for receiving responses from the AI Router Service.
     * All callbacks are executed on a background thread.
     */
    private val callback = object : IAIRouterListener.Stub() {
        override fun onResponse(response: AIResponse) {
            val state = if(response.isComplete) "Completed" else "Streaming"
            logMessage("✅ Response [${state}]: ${response.text}")
        }
    }

    /**
     * Initiates connection to the AI Router Service.
     * 
     * First attempts to connect to the debug version of the service.
     * If that fails, tries the production version.
     * 
     * Example usage:
     * ```
     * viewModel.connectToService()
     * // Then observe the uiState.isConnected property for connection status
     * ```
     */
    fun connectToService() {
        logMessage("🔄 Connecting to AI Router Service...")
        
        // 首先嘗試連接 debug 版本 (if installed)
        var intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService").apply {
            setPackage("com.mtkresearch.breezeapp.router.debug")
        }
        
        var success = getApplication<Application>().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        
        if (!success) {
            logMessage("⚠️ Debug service not found, trying production version...")
            // 如果 debug 版本不存在，嘗試 production 版本
            intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService").apply {
                setPackage("com.mtkresearch.breezeapp.router")
            }
            success = getApplication<Application>().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
        
        if (!success) {
            logMessage("❌ Failed to bind to AI Router Service")
            _uiState.update { it.copy(connectionStatus = "Connection Failed") }
        } else {
            logMessage("🔄 Service binding initiated...")
            _uiState.update { it.copy(connectionStatus = "Connecting...") }
        }
    }

    /**
     * Disconnects from the AI Router Service.
     * 
     * Unregisters the listener and unbinds from the service.
     */
    fun disconnectFromService() {
        if (_uiState.value.isConnected) {
            routerService?.unregisterListener(callback)
            getApplication<Application>().unbindService(connection)
            _uiState.update { it.copy(isConnected = false, isInitialized = false, connectionStatus = "Disconnected") }
            logMessage("🔌 Disconnected from service")
        }
    }

    /**
     * Initializes the AI Router Service with default configuration.
     * 
     * Must be called after a successful connection before sending requests.
     * 
     * Example usage:
     * ```
     * if (viewModel.uiState.value.isConnected && !viewModel.uiState.value.isInitialized) {
     *     viewModel.initializeService()
     * }
     * ```
     */
    fun initializeService() {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Service not connected")
            return
        }
        logMessage("🔧 Initializing service...")
        val config = Configuration(
            apiVersion = 1,
            logLevel = 3,
            timeoutMs = 30000L,
            maxTokens = 1024,
            temperature = 0.7f,
            languagePreference = "en-US",
            preferredRuntime = Configuration.RuntimeBackend.AUTO,
            runnerConfigurations = mapOf(
                Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.MOCK,
                Configuration.AITaskType.IMAGE_ANALYSIS to Configuration.RunnerType.MOCK,
                Configuration.AITaskType.SPEECH_RECOGNITION to Configuration.RunnerType.MOCK,
                Configuration.AITaskType.SPEECH_SYNTHESIS to Configuration.RunnerType.MOCK,
                Configuration.AITaskType.CONTENT_MODERATION to Configuration.RunnerType.MOCK
            )
        )
        routerService?.initialize(config)
        _uiState.update { it.copy(isInitialized = true) }
        logMessage("✅ Service initialized")
    }

    /**
     * Sends a text generation request to the AI Router Service.
     * 
     * @param prompt The text prompt to send to the LLM
     * @param isStreaming Whether to stream the response token by token
     */
    fun sendLLMRequest(prompt: String, isStreaming: Boolean) {
        val options = mutableMapOf(
            "request_type" to "text_generation",
            "model_name" to "mock-llm",
            "streaming" to isStreaming.toString()
        )
        sendRequest(prompt, options)
    }

    /**
     * Sends an image analysis request to the AI Router Service.
     * 
     * @param prompt Text prompt describing what to analyze in the image
     * @param imageUri URI of the image to analyze
     */
    fun analyzeImage(prompt: String, imageUri: Uri) {
        viewModelScope.launch {
            val base64Image = encodeImageToBase64(imageUri)
            if (base64Image.isNotEmpty()) {
                val options = mapOf(
                    "request_type" to "image_analysis",
                    "model_name" to "mock-vlm",
                    "image_data" to base64Image
                )
                sendRequest(prompt, options)
            }
        }
    }
    
    /**
     * Sends an audio transcription request to the AI Router Service.
     * 
     * @param audioFile File containing the audio to transcribe
     */
    fun transcribeAudio(audioFile: java.io.File) {
        val audioBytes = audioFile.readBytes()
        val base64Audio = Base64.encodeToString(audioBytes, Base64.DEFAULT)
        val options = mapOf(
            "request_type" to "speech_recognition",
            "model_name" to "mock-asr",
            "audio_data" to base64Audio,
            "audio_format" to "3gp"
        )
        sendRequest("Transcribe the audio", options)
    }
    
    /**
     * Sends a text-to-speech synthesis request to the AI Router Service.
     * 
     * @param text The text to convert to speech
     */
    fun sendTTSRequest(text: String) {
        val options = mapOf("request_type" to "speech_synthesis", "model_name" to "mock-tts")
        sendRequest(text, options)
    }

    /**
     * Sends a content moderation request to the AI Router Service.
     * 
     * @param text The text to check for policy violations
     */
    fun sendGuardrailRequest(text: String) {
        val options = mapOf("request_type" to "content_moderation", "model_name" to "mock-guardrail")
        sendRequest(text, options)
    }

    /**
     * Generic method to send a request to the AI Router Service.
     * 
     * @param text The text content of the request
     * @param options Map of options specific to the request type
     */
    private fun sendRequest(text: String, options: Map<String, String>) {
        if (!_uiState.value.isInitialized) {
            logMessage("❌ Service not initialized")
            return
        }
        val request = AIRequest(
            id = UUID.randomUUID().toString(),
            text = text,
            sessionId = "session-${options["request_type"]}",
            timestamp = System.currentTimeMillis(),
            options = options
        )
        routerService?.sendMessage(request)
        logMessage("🚀 Request sent: ${options["request_type"]}")
    }

    /**
     * Queries the API version from the AI Router Service.
     * 
     * @return The API version number (via log message)
     */
    fun getApiVersion() {
        if (!_uiState.value.isInitialized) return
        val version = routerService?.apiVersion ?: -1
        logMessage("📋 API Version: $version")
    }
    
    /**
     * Checks which capabilities are supported by the AI Router Service.
     * 
     * @return List of supported capabilities (via log messages)
     */
    fun checkCapabilities() {
        if (!_uiState.value.isInitialized) return
        logMessage("🔍 Checking capabilities...")
        val capabilities = listOf("streaming", "image_processing", "audio_processing")
        capabilities.forEach { capability ->
            val hasCap = routerService?.hasCapability(capability) ?: false
            logMessage("   └── $capability: $hasCap")
        }
    }

    /**
     * Attempts to cancel an in-progress request.
     * 
     * @return Whether cancellation was successful (via log message)
     */
    fun cancelRequest() {
        if (!_uiState.value.isInitialized) return
        val result = routerService?.cancelRequest("some-request-id") ?: false
        logMessage("🚫 Cancel request result: $result")
    }

    /**
     * Encodes an image from a URI to Base64 format for transmission.
     * 
     * @param uri URI of the image to encode
     * @return Base64-encoded string representation of the image
     */
    private fun encodeImageToBase64(uri: Uri): String {
        return try {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val resizedBitmap = resizeBitmap(bitmap, 800)
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            logMessage("❌ Image encoding failed: ${e.message}")
            ""
        }
    }
    
    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int): Bitmap {
        if (bitmap.width <= maxWidth) return bitmap
        val ratio = maxWidth.toFloat() / bitmap.width
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true)
    }

    /**
     * Adds a log message to the UI state.
     * 
     * @param message The message to log
     */
    fun logMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val fullMessage = "$timestamp: $message"
        Log.d(TAG, fullMessage)
        _uiState.update {
            it.copy(logMessages = it.logMessages + fullMessage)
        }
    }
    
    /**
     * Clears all log messages from the UI state.
     */
    fun clearLogs() = _uiState.update { it.copy(logMessages = emptyList()) }

    /**
     * Sets the selected image URI in the UI state.
     * 
     * @param uri URI of the selected image
     */
    fun setSelectedImageUri(uri: Uri?) = _uiState.update { it.copy(selectedImageUri = uri) }

    /**
     * Updates the recording state in the UI state.
     * 
     * @param isRecording Whether audio recording is in progress
     */
    fun setRecordingState(isRecording: Boolean) = _uiState.update { it.copy(isRecording = isRecording) }

    fun setHasRecordedAudio(hasAudio: Boolean) = _uiState.update { it.copy(hasRecordedAudio = hasAudio) }

    /**
     * Cleans up resources when the ViewModel is destroyed.
     * 
     * Disconnects from the service if still connected.
     */
    override fun onCleared() {
        super.onCleared()
        disconnectFromService()
    }
}
