package com.mtkresearch.breezeapp.router.client

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterService
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

/**
 * ViewModel for the Breeze App Router Client.
 *
 * This ViewModel follows modern architectural patterns (MVVM + Repository). It has one
 * single responsibility: to manage and expose UI state. It is completely decoupled from
* the service connection logic, delegating all data operations to the [RouterRepository].
 *
 * It observes data flows from the repository and transforms them into a [UiState]
 * object that the View can reactively render.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // The ViewModel now has a single dependency: the Repository.
    private val repository: RouterRepository

    // Keep a direct reference to the service for non-repository functions (e.g., getApiVersion)
    // This is a pragmatic choice to avoid boilerplate in the repository for simple, direct calls.
    private var routerService: IAIRouterService? = null
    private val TAG = "BreezeAppRouterClient"

    init {
        // Instantiate the repository, which in turn instantiates the client.
        val client = AIRouterClient(application)
        repository = RouterRepository(client, viewModelScope)

        // Collect connection state from the repository
        viewModelScope.launch {
            repository.connectionState.collect { state ->
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
        
        // Collect the raw service object from the client for direct calls.
        viewModelScope.launch {
            client.routerService.collect { service ->
                routerService = service
            }
        }

        // Collect responses from the repository's hot flow
        viewModelScope.launch {
            repository.responses.collect { response ->
                handleResponse(response)
            }
        }
    }

    private fun handleResponse(response: AIResponse) {
        val state = if (response.isComplete) "Completed" else "Streaming"
        logMessage("✅ Response [${state}]: ${response.text}")
        response.metadata?.let { logMessage("   └── Metadata: $it") }
    }

    fun connectToService() {
        logMessage("🔄 Connecting to AI Router Service...")
        repository.connect()
    }

    fun disconnectFromService() {
        repository.disconnect()
    }

    fun sendLLMRequest(prompt: String, isStreaming: Boolean) {
        val payload = RequestPayload.TextChat(
            prompt = prompt,
            modelName = "mock-llm",
            streaming = isStreaming
        )
        val requestId = repository.sendRequest(payload)
        val requestType = if (isStreaming) "Streaming" else "Chat"
        logMessage("🚀 $requestType request sent with ID: $requestId")
    }

    fun analyzeImage(prompt: String, imageUri: Uri) {
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

    fun transcribeAudio(audioFile: java.io.File) {
        val audioBytes = audioFile.readBytes()
        val payload = RequestPayload.AudioTranscription(
            audio = audioBytes,
            modelName = "mock-asr",
            language = "en-US"
        )
        val requestId = repository.sendRequest(payload)
        logMessage("🚀 Audio request sent with ID: $requestId")
    }

    fun sendTTSRequest(text: String) {
        val payload = RequestPayload.SpeechSynthesis(text = text, modelName = "mock-tts")
        val requestId = repository.sendRequest(payload)
        logMessage("🚀 TTS request sent with ID: $requestId")
    }

    fun sendGuardrailRequest(text: String) {
        val payload = RequestPayload.ContentModeration(text = text, checkType = "safety")
        val requestId = repository.sendRequest(payload)
        logMessage("🚀 Guardrail request sent with ID: $requestId")
    }

    fun getApiVersion() {
        if (!_uiState.value.isConnected) return
        val version = routerService?.apiVersion ?: -1
        logMessage("📋 API Version: $version")
    }

    fun checkCapabilities() {
        if (!_uiState.value.isConnected) return
        logMessage("🔍 Checking capabilities...")
        val capabilities = listOf("streaming", "image_processing", "audio_processing")
        capabilities.forEach { capability ->
            val hasCap = routerService?.hasCapability(capability) ?: false
            logMessage("   └── $capability: $hasCap")
        }
    }

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
