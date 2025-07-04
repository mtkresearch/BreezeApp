package com.mtkresearch.breezeapp.router.client

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterService
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
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
 * This ViewModel handles all business logic for interacting with the AI Router Service by
 * delegating tasks to the appropriate layers (Repository for data, Client for connection).
 * It is responsible for preparing data for the UI and handling user actions.
 *
 * The ViewModel exposes a [StateFlow] of [UiState] for reactive UI updates.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // The ViewModel now depends on the Repository and the Client.
    private val airouterClient = AIRouterClient(application)
    private lateinit var repository: RouterRepository // Delay initialization
    private var routerService: IAIRouterService? = null
    private val TAG = "BreezeAppRouterClientViewModel"

    init {
        // Collect connection state from the client to update UI
        viewModelScope.launch {
            airouterClient.connectionState.collect { state ->
                val (status, isConnected) = when (state) {
                    ConnectionState.DISCONNECTED -> "Disconnected" to false
                    ConnectionState.CONNECTING -> "Connecting..." to false
                    ConnectionState.CONNECTED -> "Connected" to true
                    ConnectionState.ERROR -> "Connection Error" to false
                }
                _uiState.update { it.copy(connectionStatus = status, isConnected = isConnected) }
                if (state != ConnectionState.CONNECTING) logMessage("ℹ️ Connection state: $state")
            }
        }

        // Collect the raw service object for direct, non-repository calls if needed
        viewModelScope.launch {
            airouterClient.routerService.collect { service ->
                if (service != null && routerService == null) {
                    routerService = service
                    // Now that we have the service, initialize the repository
                    repository = RouterRepository(airouterClient, viewModelScope)
                    // Launch a new coroutine to collect responses from the now-initialized repository
                    viewModelScope.launch {
                        repository.responses.collect { response ->
                            handleResponse(response)
                        }
                    }
                } else if (service == null) {
                    routerService = null
                }
            }
        }
    }

    /**
     * Handles incoming AIResponses from the repository's flow.
     */
    private fun handleResponse(response: AIResponse) {
        val state = if (response.isComplete) "Completed" else "Streaming"
        logMessage("✅ Response [${state}]: ${response.text}")
        response.metadata?.let { logMessage("   └── Metadata: $it") }
    }

    /**
     * Initiates connection to the AI Router Service.
     */
    fun connectToService() {
        logMessage("🔄 Connecting to AI Router Service...")
        airouterClient.connect()
    }

    /**
     * Disconnects from the AI Router Service.
     */
    fun disconnectFromService() {
        airouterClient.disconnect()
    }

    /**
     * Sends a text generation request via the repository.
     */
    fun sendLLMRequest(prompt: String) {
        if (!::repository.isInitialized) {
            logMessage("❌ Error: Repository not ready. Please connect to the service first.")
            return
        }
        val payload = RequestPayload.TextChat(prompt = prompt, modelName = "mock-llm")
        val requestId = repository.sendRequest(payload)
        logMessage("🚀 LLM request sent with ID: $requestId")
    }

    /**
     * Sends an image analysis request via the repository.
     */
    fun analyzeImage(prompt: String, imageUri: Uri) {
        if (!::repository.isInitialized) {
            logMessage("❌ Error: Repository not ready. Please connect to the service first.")
            return
        }
        viewModelScope.launch {
            val imageBytes = getImageBytes(imageUri)
            if (imageBytes != null) {
                val payload = RequestPayload.ImageAnalysis(
                    prompt = prompt,
                    image = imageBytes,
                    modelName = "mock-vlm"
                )
                val requestId = repository.sendRequest(payload)
                logMessage("🚀 Image request sent with ID: $requestId")
            }
        }
    }

    /**
     * Sends an audio transcription request via the repository.
     */
    fun transcribeAudio(audioFile: java.io.File) {
        if (!::repository.isInitialized) {
            logMessage("❌ Error: Repository not ready. Please connect to the service first.")
            return
        }
        val audioBytes = audioFile.readBytes()
        val payload = RequestPayload.AudioTranscription(
            audio = audioBytes,
            modelName = "mock-asr",
            language = "en-US"
        )
        val requestId = repository.sendRequest(payload)
        logMessage("🚀 Audio request sent with ID: $requestId")
    }

    /**
     * Sends a text-to-speech synthesis request via the repository.
     */
    fun sendTTSRequest(text: String) {
        if (!::repository.isInitialized) {
            logMessage("❌ Error: Repository not ready. Please connect to the service first.")
            return
        }
        val payload = RequestPayload.SpeechSynthesis(text = text, modelName = "mock-tts")
        val requestId = repository.sendRequest(payload)
        logMessage("🚀 TTS request sent with ID: $requestId")
    }

    /**
     * Sends a content moderation request via the repository.
     */
    fun sendGuardrailRequest(text: String) {
        if (!::repository.isInitialized) {
            logMessage("❌ Error: Repository not ready. Please connect to the service first.")
            return
        }
        val payload = RequestPayload.ContentModeration(text = text, checkType = "safety")
        val requestId = repository.sendRequest(payload)
        logMessage("🚀 Guardrail request sent with ID: $requestId")
    }

    /**
     * Queries the API version from the AI Router Service.
     */
    fun getApiVersion() {
        if (!_uiState.value.isConnected) return
        val version = routerService?.apiVersion ?: -1
        logMessage("📋 API Version: $version")
    }

    /**
     * Checks which capabilities are supported by the AI Router Service.
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
     */
    fun cancelRequest() {
        if (!_uiState.value.isConnected) return
        val result = routerService?.cancelRequest("some-request-id") ?: false
        logMessage("🚫 Cancel request result: $result")
    }

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

    fun logMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val fullMessage = "$timestamp: $message"
        Log.d(TAG, fullMessage)
        _uiState.update {
            it.copy(logMessages = it.logMessages + fullMessage)
        }
    }

    fun clearLogs() = _uiState.update { it.copy(logMessages = emptyList()) }

    fun setSelectedImageUri(uri: Uri?) = _uiState.update { it.copy(selectedImageUri = uri) }

    fun setRecordingState(isRecording: Boolean) = _uiState.update { it.copy(isRecording = isRecording) }

    fun setHasRecordedAudio(hasAudio: Boolean) = _uiState.update { it.copy(hasRecordedAudio = hasAudio) }

    override fun onCleared() {
        super.onCleared()
        disconnectFromService()
    }
}
