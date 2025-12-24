package com.mtkresearch.breezeapp.presentation.settings.test

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp.domain.model.breezeapp.ConnectionState
import com.mtkresearch.breezeapp.domain.usecase.breezeapp.*
import com.mtkresearch.breezeapp.domain.model.breezeapp.BreezeAppError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

enum class AudioSource {
    FILE,
    MICROPHONE
}

/**
 * Metrics for LLM test results
 */
data class LlmMetrics(
    val totalLatencyMs: Long = 0,
    val timeToFirstTokenMs: Long? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val responseLength: Int = 0,
    val isStreaming: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Metrics for TTS test results
 */
data class TtsMetrics(
    val totalLatencyMs: Long = 0,
    val timeToFirstAudioMs: Long? = null,
    val success: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Metrics for ASR test results
 */
data class AsrMetrics(
    val totalLatencyMs: Long = 0,
    val transcriptionLength: Int = 0,
    val language: String? = null,
    val confidence: Float? = null,
    val success: Boolean = false,
    val errorMessage: String? = null
)

data class UiState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val connectionStatus: String = "Disconnected",
    val llmResponse: String = "",
    val llmMetrics: LlmMetrics? = null,
    val ttsMetrics: TtsMetrics? = null,
    val asrResponse: String = "",
    val asrMetrics: AsrMetrics? = null,
    val logMessages: List<String> = emptyList(),
    val selectedImageUri: Uri? = null,
    val selectedAudioFileUri: Uri? = null,
    val selectedAudioSource: AudioSource = AudioSource.FILE,
    val isRecording: Boolean = false,
    val isLlmLoading: Boolean = false,
    val isTtsLoading: Boolean = false,
    val isAsrLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadMessage: String? = null
)

@HiltViewModel
class FunctionalityTestViewModel @Inject constructor(
    application: Application,
    private val connectionUseCase: ConnectionUseCase,
    private val chatUseCase: ChatUseCase,
    private val streamingChatUseCase: StreamingChatUseCase,
    private val ttsUseCase: TtsUseCase,
    private val asrMicrophoneUseCase: AsrMicrophoneUseCase
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val TAG = "FunctionalityTestVM"

    private var microphoneStreamingJob: Job? = null

    init {
        initializeConnection()
    }

    private fun initializeConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true) }
            connectionUseCase.initialize().collect { state ->
                when (state) {
                    is ConnectionState.Initializing -> {
                        logMessage("🚀 Initializing EdgeAI SDK...")
                        _uiState.update { it.copy(
                            connectionStatus = "Initializing...",
                            isConnected = false,
                            isConnecting = true
                        ) }
                    }
                    is ConnectionState.Connected -> {
                        logMessage("✅ EdgeAI SDK connected")
                        _uiState.update { it.copy(
                            connectionStatus = "Connected",
                            isConnected = true,
                            isConnecting = false
                        ) }
                    }
                    is ConnectionState.Disconnected -> {
                        logMessage("🔄 EdgeAI SDK disconnected")
                        _uiState.update { it.copy(
                            connectionStatus = "Disconnected",
                            isConnected = false,
                            isConnecting = false
                        ) }
                    }
                    is ConnectionState.Failed -> {
                        logMessage("❌ Connection failed: ${state.message}")
                        _uiState.update { it.copy(
                            connectionStatus = "Failed",
                            isConnected = false,
                            isConnecting = false
                        ) }
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                connectionUseCase.disconnect().collect { }
            } catch (e: Exception) {
                Log.w(TAG, "Disconnect warning: ${e.message}")
            }
        }
    }

    fun connectToService() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true) }
            if (connectionUseCase.isConnected()) {
                logMessage("✅ Already connected")
                _uiState.update { it.copy(
                    connectionStatus = "Connected",
                    isConnected = true,
                    isConnecting = false
                ) }
            } else {
                logMessage("🔄 Connecting...")
                connectionUseCase.connect().collect { state ->
                    when (state) {
                        is ConnectionState.Connected -> {
                            logMessage("✅ Connected")
                            _uiState.update { it.copy(
                                connectionStatus = "Connected",
                                isConnected = true,
                                isConnecting = false
                            ) }
                        }
                        is ConnectionState.Failed -> {
                            logMessage("❌ Connection failed: ${state.message}")
                            _uiState.update { it.copy(
                                connectionStatus = "Failed",
                                isConnected = false,
                                isConnecting = false
                            ) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun disconnectFromService() {
        viewModelScope.launch {
            try {
                connectionUseCase.disconnect().collect { state ->
                    when (state) {
                        is ConnectionState.Disconnected -> {
                            _uiState.update { it.copy(
                                connectionStatus = "Disconnected",
                                isConnected = false
                            ) }
                            logMessage("🔄 Disconnected")
                        }
                        is ConnectionState.Failed -> {
                            logMessage("⚠️ Disconnect error: ${state.message}")
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                logMessage("⚠️ Disconnect error: ${e.message}")
            }
        }
    }

    fun sendLLMRequest(prompt: String, isStreaming: Boolean) {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Not connected")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    llmResponse = "",
                    llmMetrics = null,
                    isLlmLoading = true
                ) }

                val startTime = System.currentTimeMillis()
                var firstTokenTime: Long? = null
                var accumulatedContent = StringBuilder()
                var promptTokens: Int? = null
                var completionTokens: Int? = null
                var totalTokens: Int? = null

                logMessage("🚀 LLM ${if (isStreaming) "streaming" else "request"}...")

                if (isStreaming) {
                    streamingChatUseCase.execute(prompt).collect { response ->
                        response.choices.forEach { choice ->
                            if (choice.finishReason == null) {
                                choice.delta?.content?.let { content ->
                                    if (firstTokenTime == null) {
                                        firstTokenTime = System.currentTimeMillis() - startTime
                                    }
                                    accumulatedContent.append(content)
                                    _uiState.update { it.copy(llmResponse = accumulatedContent.toString()) }
                                }
                            }
                        }

                        response.usage?.let { usage ->
                            promptTokens = usage.promptTokens
                            completionTokens = usage.completionTokens
                            totalTokens = usage.totalTokens
                        }
                    }
                } else {
                    val response = chatUseCase.execute(prompt)
                    response.choices.forEach { choice ->
                        choice.message?.content?.let { content ->
                            accumulatedContent.append(content)
                            _uiState.update { it.copy(llmResponse = content) }
                        }
                    }

                    response.usage?.let { usage ->
                        promptTokens = usage.promptTokens
                        completionTokens = usage.completionTokens
                        totalTokens = usage.totalTokens
                    }
                }

                val totalLatency = System.currentTimeMillis() - startTime
                val metrics = LlmMetrics(
                    totalLatencyMs = totalLatency,
                    timeToFirstTokenMs = firstTokenTime,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    totalTokens = totalTokens,
                    responseLength = accumulatedContent.length,
                    isStreaming = isStreaming,
                    success = true
                )

                _uiState.update { it.copy(
                    llmMetrics = metrics,
                    isLlmLoading = false
                ) }

                logMessage("✅ LLM done - ${totalLatency}ms" +
                    (firstTokenTime?.let { ", TTFT: ${it}ms" } ?: "") +
                    (totalTokens?.let { ", tokens: $it" } ?: ""))

            } catch (e: BreezeAppError.ChatError) {
                val errorMessage = "Chat error: ${e.message}"
                logMessage("❌ $errorMessage")
                _uiState.update { it.copy(
                    llmResponse = errorMessage,
                    llmMetrics = LlmMetrics(success = false, errorMessage = e.message),
                    isLlmLoading = false
                ) }
            } catch (e: Exception) {
                val errorMessage = "Error: ${e.message}"
                logMessage("❌ $errorMessage")
                _uiState.update { it.copy(
                    llmResponse = errorMessage,
                    llmMetrics = LlmMetrics(success = false, errorMessage = e.message),
                    isLlmLoading = false
                ) }
            }
        }
    }

    fun sendTTSRequest(text: String) {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Not connected")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(ttsMetrics = null, isTtsLoading = true) }

                logMessage("🚀 TTS starting...")
                val startTime = System.currentTimeMillis()
                var firstChunkTime: Long? = null

                var remoteTtfa: Long? = null
                
                ttsUseCase.execute(text).collect { response ->
                    if (firstChunkTime == null) {
                        firstChunkTime = System.currentTimeMillis() - startTime
                    }
                    
                    // Capture TTFA from metrics if available in this chunk
                    response.metrics?.get("timeToFirstAudio")?.toLongOrNull()?.let { 
                        remoteTtfa = it
                    }
                    
                    if (response.isLastChunk == true) {
                        val totalLatency = System.currentTimeMillis() - startTime
                        val finalTtfa = remoteTtfa ?: firstChunkTime
                        
                        val metrics = TtsMetrics(
                            totalLatencyMs = totalLatency,
                            timeToFirstAudioMs = finalTtfa,
                            success = true
                        )

                        _uiState.update { it.copy(
                            ttsMetrics = metrics,
                            isTtsLoading = false
                        ) }

                        logMessage("✅ TTS done - ${totalLatency}ms" +
                            (finalTtfa?.let { ", TTFA: ${it}ms" } ?: ""))
                    }
                }
            } catch (e: Exception) {
                logMessage("❌ TTS failed: ${e.message}")
                _uiState.update { it.copy(
                    ttsMetrics = TtsMetrics(success = false, errorMessage = e.message),
                    isTtsLoading = false
                ) }
            }
        }
    }

    fun transcribeAudio() {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Not connected")
            return
        }

        val audioUri = _uiState.value.selectedAudioFileUri
        if (audioUri == null) {
            logMessage("⚠️ No audio file selected")
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(
                    asrResponse = "",
                    asrMetrics = null,
                    isAsrLoading = true
                ) }

                logMessage("🚀 ASR starting...")
                val startTime = System.currentTimeMillis()

                // TODO: Implement ASR use case when available
                // For now, show placeholder
                logMessage("⚠️ ASR not yet implemented via UseCase")

                _uiState.update { it.copy(
                    asrResponse = "ASR not yet implemented",
                    asrMetrics = AsrMetrics(success = false, errorMessage = "Not implemented"),
                    isAsrLoading = false
                ) }

            } catch (e: Exception) {
                logMessage("❌ ASR failed: ${e.message}")
                _uiState.update { it.copy(
                    asrResponse = "Error: ${e.message}",
                    asrMetrics = AsrMetrics(success = false, errorMessage = e.message),
                    isAsrLoading = false
                ) }
            }
        }
    }

    fun startMicrophoneStreaming() {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Not connected")
            return
        }
        if (_uiState.value.isRecording) return

        microphoneStreamingJob = viewModelScope.launch {
            _uiState.update { it.copy(isRecording = true, asrResponse = "", asrMetrics = null, isAsrLoading = true) }
            logMessage("🎤 Microphone streaming started...")

            val startTime = System.currentTimeMillis()
            var firstTokenTime: Long? = null
            var accumulatedTranscription = StringBuilder()

            try {
                asrMicrophoneUseCase.execute().collect { response ->
                    if (firstTokenTime == null && response.text.isNotEmpty()) {
                        firstTokenTime = System.currentTimeMillis() - startTime
                    }

                    accumulatedTranscription.append(response.text)

                    val currentLatency = System.currentTimeMillis() - startTime
                    val metrics = AsrMetrics(
                        totalLatencyMs = currentLatency,
                        transcriptionLength = accumulatedTranscription.length,
                        language = response.language,
                        success = true
                    )

                    _uiState.update { state ->
                        state.copy(
                            asrResponse = accumulatedTranscription.toString(),
                            asrMetrics = metrics,
                            isAsrLoading = response.isChunk // ASR is loading if it's a chunk
                        )
                    }

                    if (!response.isChunk) {
                        logMessage("✅ ASR final result: ${response.text}")
                        logMessage("✅ ASR done - ${currentLatency}ms")
                        _uiState.update { it.copy(isRecording = false, isAsrLoading = false) }
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val errorMessage = "ASR microphone failed: ${e.message}"
                    logMessage("❌ $errorMessage")
                    _uiState.update { it.copy(
                        asrResponse = "Error: ${e.message}",
                        asrMetrics = AsrMetrics(success = false, errorMessage = e.message),
                        isRecording = false,
                        isAsrLoading = false
                    ) }
                } else {
                    logMessage("⏹️ Microphone streaming cancelled.")
                    _uiState.update { it.copy(isRecording = false, isAsrLoading = false) }
                }
            }
        }
    }

    fun stopMicrophoneStreaming() {
        logMessage("⏹️ Microphone stopped")
        
        // For offline ASR mode, stop recording to trigger audio processing
        asrMicrophoneUseCase.stopRecording()
        
        // Update UI state to show we're no longer recording, but keep isAsrLoading=true
        // The job will complete naturally when ASR results arrive
        _uiState.update { it.copy(isRecording = false) }
        
        // NOTE: We intentionally DO NOT cancel microphoneStreamingJob here
        // Reason: The job is still collecting ASR results from the engine
        // Cancelling it would prevent us from receiving the transcription result
        // The job will complete naturally when the ASR flow completes
    }

    fun cancelRequest() {
        logMessage("⚠️ Cancel not implemented")
    }

    fun logMessage(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val fullMessage = "[$timestamp] $message"
        Log.d(TAG, fullMessage)
        _uiState.update {
            it.copy(logMessages = it.logMessages + fullMessage)
        }
    }

    fun copyLLMResponse() {
        val response = _uiState.value.llmResponse
        if (response.isNotBlank()) {
            try {
                val clipboardManager = getApplication<Application>()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboardManager.setPrimaryClip(ClipData.newPlainText("LLM Response", response))
                logMessage("✅ Copied to clipboard")
            } catch (e: Exception) {
                logMessage("❌ Copy failed: ${e.message}")
            }
        }
    }

    fun clearLogs() = _uiState.update { it.copy(logMessages = emptyList()) }
    fun setSelectedImageUri(uri: Uri?) = _uiState.update { it.copy(selectedImageUri = uri) }
    fun setAudioSource(source: AudioSource) {
        _uiState.update {
            it.copy(
                selectedAudioSource = source,
                selectedAudioFileUri = null,
                asrResponse = "",
                asrMetrics = null
            )
        }
    }
    fun setSelectedAudioFileUri(uri: Uri?) {
        _uiState.update { it.copy(selectedAudioFileUri = uri) }
        if (uri != null) {
            logMessage("📁 Audio file selected")
        }
    }

    /**
     * Update download state - called by Activity when download broadcasts are received
     */
    fun setDownloadingState(isDownloading: Boolean, message: String? = null) {
        _uiState.update { it.copy(
            isDownloading = isDownloading,
            downloadMessage = message
        ) }
        if (isDownloading && message != null) {
            logMessage("📥 $message")
        }
    }
}
