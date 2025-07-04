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
import android.os.RemoteException
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterService
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import com.mtkresearch.breezeapp.shared.contracts.model.RequestPayload
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
            routerService = IAIRouterService.Stub.asInterface(service)
            _uiState.update { it.copy(connectionStatus = "Connected", isConnected = true) }
            logMessage("✅ Service connected")
            try {
                routerService?.registerListener(callback)
            } catch (e: RemoteException) {
                logMessage("❌ Error registering listener: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            logMessage("ℹ️ Service disconnected")
            _uiState.update { it.copy(connectionStatus = "Disconnected", isConnected = false) }
            routerService = null
        }

        override fun onBindingDied(name: ComponentName?) {
            logMessage("❌ Binding Died! Service may have crashed.")
            _uiState.update { it.copy(connectionStatus = "Binding Died", isConnected = false) }
            routerService = null
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
            response.metadata?.let { logMessage("   └── Metadata: $it") }
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

        val intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService")
        intent.setPackage("com.mtkresearch.breezeapp.router")
        try {
            val bound = getApplication<Application>().bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (bound) {
                logMessage("Binding to service...")
            } else {
                logMessage("❌ Failed to bind to service. Is the router app installed?")
                _uiState.update { it.copy(isConnected = false, connectionStatus = "Bind Failed") }
            }
        } catch (e: SecurityException) {
            logMessage("❌ SecurityException: Check if client app has the necessary permissions or is signed correctly.")
            _uiState.update { it.copy(isConnected = false, connectionStatus = "Permission Denied") }
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
            _uiState.update { it.copy(isConnected = false, connectionStatus = "Disconnected") }
            routerService = null
            logMessage("🔌 Disconnected from service")
        }
    }

    /**
     * Sends a text generation request to the AI Router Service.
     * 
     * @param prompt The text prompt to send to the LLM
     * @param isStreaming Whether to stream the response token by token
     */
    fun sendLLMRequest(prompt: String, isStreaming: Boolean) {
        val payload = RequestPayload.TextChat(
            prompt = prompt,
            modelName = "mock-llm"
        )
        sendRequest(payload)
    }

    /**
     * Sends an image analysis request to the AI Router Service.
     * 
     * @param prompt Text prompt describing what to analyze in the image
     * @param imageUri URI of the image to analyze
     */
    fun analyzeImage(prompt: String, imageUri: Uri) {
        viewModelScope.launch {
            val imageBytes = getImageBytes(imageUri)
            if (imageBytes != null) {
                val payload = RequestPayload.ImageAnalysis(
                    prompt = prompt,
                    image = imageBytes,
                    modelName = "mock-vlm"
                )
                sendRequest(payload)
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
        val payload = RequestPayload.AudioTranscription(
            audio = audioBytes,
            modelName = "mock-asr",
            language = "en-US"
        )
        sendRequest(payload)
    }
    
    /**
     * Sends a text-to-speech synthesis request to the AI Router Service.
     * 
     * @param text The text to convert to speech
     */
    fun sendTTSRequest(text: String) {
        val payload = RequestPayload.SpeechSynthesis(
            text = text,
            modelName = "mock-tts"
        )
        sendRequest(payload)
    }

    /**
     * Sends a content moderation request to the AI Router Service.
     * 
     * @param text The text to check for policy violations
     */
    fun sendGuardrailRequest(text: String) {
        val payload = RequestPayload.ContentModeration(
            text = text,
            checkType = "safety"
        )
        sendRequest(payload)
    }

    /**
     * Generic method to send a request to the AI Router Service.
     * 
     * @param payload The type-safe payload for the request
     */
    private fun sendRequest(payload: RequestPayload) {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Service not connected")
            return
        }
        val sessionType = payload::class.java.simpleName
        val request = AIRequest(
            id = UUID.randomUUID().toString(),
            sessionId = "session-$sessionType",
            timestamp = System.currentTimeMillis(),
            payload = payload
        )
        routerService?.sendMessage(request)
        logMessage("🚀 Request sent: $sessionType")
    }

    /**
     * Queries the API version from the AI Router Service.
     * 
     * @return The API version number (via log message)
     */
    fun getApiVersion() {
        if (!_uiState.value.isConnected) return
        val version = routerService?.apiVersion ?: -1
        logMessage("📋 API Version: $version")
    }
    
    /**
     * Checks which capabilities are supported by the AI Router Service.
     * 
     * @return List of supported capabilities (via log messages)
     */
    fun checkCapabilities() {
        if (!_uiState.value.isConnected) return
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
        if (!_uiState.value.isConnected) return
        val result = routerService?.cancelRequest("some-request-id") ?: false
        logMessage("🚫 Cancel request result: $result")
    }

    /**
     * Gets the byte array of an image from a URI, with resizing.
     *
     * @param uri URI of the image to encode
     * @return Byte array of the processed image, or null on failure.
     */
    private fun getImageBytes(uri: Uri): ByteArray? {
        return try {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val resizedBitmap = resizeBitmap(bitmap, 800)
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            logMessage("❌ Image processing failed: ${e.message}")
            null
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
