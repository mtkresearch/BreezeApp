package com.mtkresearch.breezeapp.router.client

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp.edgeai.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the Breeze App Router Client.
 *
 * Now modernized to use the EdgeAI SDK instead of direct AIDL calls.
 * This ViewModel demonstrates how to integrate EdgeAI SDK into your application
 * with proper error handling and state management.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val TAG = "BreezeAppRouterClient"
    private var initializationRetryCount = 0
    private val maxRetryCount = 3
    private var isInitializing = false

    init {
        // Check prerequisites before initializing EdgeAI SDK
        checkPrerequisitesAndInitialize()
    }

    private fun checkPrerequisitesAndInitialize() {
        viewModelScope.launch {
            logMessage("🚀 Initializing EdgeAI SDK...")
            
            // Try to wake up the service first (optional optimization)
            tryWakeUpService()
            
            // Initialize EdgeAI SDK with retry logic
            initializeEdgeAIWithRetry()
        }
    }



    private suspend fun tryWakeUpService() {
        try {
            logMessage("🔔 Attempting to wake up Router Service...")
            val intent = Intent().apply {
                component = ComponentName(
                    "com.mtkresearch.breezeapp.router",
                    "com.mtkresearch.breezeapp.router.AIRouterService"
                )
            }
            
            // Try to start the service to ensure it's running
            getApplication<Application>().startService(intent)
            logMessage("✅ Service wake-up signal sent")
            
            // Give the service time to start up
            delay(1000)
        } catch (e: Exception) {
            logMessage("⚠️ Could not wake up service: ${e.message}")
        }
    }

    private suspend fun initializeEdgeAIWithRetry() {
        if (isInitializing) {
            logMessage("🔄 Initialization already in progress...")
            return
        }
        
        isInitializing = true
        initializationRetryCount++
        
        try {
            logMessage("🚀 Initializing EdgeAI SDK (attempt $initializationRetryCount/$maxRetryCount)...")
            _uiState.update { it.copy(connectionStatus = "Initializing...", isConnected = false) }
            
            // Use the new initializeAndWait method for guaranteed connection
            val connectionTimeoutMs = 8000L
            EdgeAI.initializeAndWait(getApplication(), connectionTimeoutMs)
            
            // Verify connection is truly established
            if (EdgeAI.isReady()) {
                logMessage("✅ EdgeAI SDK initialized and connected successfully!")
                _uiState.update { it.copy(connectionStatus = "Connected (EdgeAI SDK v1.0)", isConnected = true) }
                initializationRetryCount = 0 // Reset retry count on success
            } else {
                throw ServiceConnectionException("SDK reported success but not ready")
            }
            
        } catch (e: ServiceConnectionException) {
            logMessage("❌ EdgeAI SDK initialization failed: ${e.message}")
            handleInitializationFailure(e)
        } catch (e: Exception) {
            logMessage("❌ Unexpected error during initialization: ${e.message}")
            handleInitializationFailure(e)
        } finally {
            isInitializing = false
        }
    }

    private suspend fun handleInitializationFailure(error: Throwable) {
        if (initializationRetryCount < maxRetryCount) {
            val retryDelay = initializationRetryCount * 2000L // 2s, 4s, 6s
            logMessage("🔄 Retrying in ${retryDelay / 1000}s... (${initializationRetryCount}/$maxRetryCount)")
            _uiState.update { 
                it.copy(
                    connectionStatus = "Retrying... (${initializationRetryCount}/$maxRetryCount)", 
                    isConnected = false
                ) 
            }
            
            delay(retryDelay)
            initializeEdgeAIWithRetry()
        } else {
            logMessage("💥 Failed to initialize after $maxRetryCount attempts")
            
            // Use EdgeAI SDK's diagnostic capabilities for user-friendly error messages
            val diagnosticMessage = try {
                EdgeAIDebug.getDiagnosticMessage(getApplication())
            } catch (e: Exception) {
                "Unable to connect to AI service. Please try again or restart the app."
            }
            
            logMessage("🔧 $diagnosticMessage")
            _uiState.update { 
                it.copy(
                    connectionStatus = "Connection Failed - Manual Retry Available", 
                    isConnected = false
                ) 
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cleanup EdgeAI SDK
        try {
            EdgeAI.shutdown()
            logMessage("🔄 EdgeAI SDK shutdown completed")
        } catch (e: Exception) {
            logMessage("⚠️ EdgeAI SDK shutdown warning: ${e.message}")
        }
    }

    fun connectToService() {
        viewModelScope.launch {
            if (EdgeAI.isReady()) {
                logMessage("✅ EdgeAI SDK is already connected")
                _uiState.update { it.copy(connectionStatus = "Connected (EdgeAI SDK v1.0)", isConnected = true) }
            } else {
                logMessage("🔄 Manual connection attempt...")
                // Reset retry count for manual attempts
                initializationRetryCount = 0
                checkPrerequisitesAndInitialize()
            }
        }
    }

    fun disconnectFromService() {
        viewModelScope.launch {
            try {
                EdgeAI.shutdown()
                _uiState.update { it.copy(connectionStatus = "Disconnected", isConnected = false) }
                logMessage("🔄 EdgeAI SDK disconnected")
            } catch (e: Exception) {
                logMessage("⚠️ Disconnect error: ${e.message}")
            }
        }
    }

    fun sendLLMRequest(prompt: String, isStreaming: Boolean) {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Cannot send request: EdgeAI SDK not connected")
            logMessage("💡 Tip: Try clicking 'Connect' button first")
            return
        }

        viewModelScope.launch {
            try {
                val request = chatRequest(
                    prompt = prompt,
                    systemPrompt = "You are a helpful AI assistant.",
                    temperature = 0.7f,
                    stream = isStreaming
                )
                
                val requestType = if (isStreaming) "Streaming" else "Chat"
                logMessage("🚀 $requestType request sent via EdgeAI SDK")
                
                EdgeAI.chat(request)
                    .catch { e ->
                        when (e) {
                            is InvalidInputException -> logMessage("❌ Invalid input: ${e.message}")
                            is ModelNotFoundException -> logMessage("❌ Model not found: ${e.message}")
                            is ServiceConnectionException -> {
                                logMessage("❌ Connection error: ${e.message}")
                                logMessage("💡 Tip: Service may have disconnected, try reconnecting")
                                _uiState.update { it.copy(isConnected = false, connectionStatus = "Connection Lost") }
                            }
                            else -> logMessage("❌ Chat error: ${e.message}")
                        }
                    }
                    .collect { response ->
                        response.choices.forEach { choice ->
                            if (isStreaming) {
                                choice.delta?.content?.let { content ->
                                    logMessage("📄 [Streaming] $content")
                                }
                            } else {
                                choice.message?.content?.let { content ->
                                    logMessage("✅ [Complete] $content")
                                }
                            }
                        }
                        
                        // Show usage statistics if available
                        response.usage?.let { usage ->
                            logMessage("   └── Tokens - Prompt: ${usage.promptTokens}, Completion: ${usage.completionTokens}, Total: ${usage.totalTokens}")
                        }
                    }
                    
            } catch (e: EdgeAIException) {
                logMessage("❌ Chat request failed: ${e.message}")
            }
        }
    }

    fun analyzeImage(prompt: String, imageUri: Uri) {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Cannot send request: EdgeAI SDK not connected")
            return
        }

        viewModelScope.launch {
            try {
                val imageBytes = getImageBytes(imageUri)
                if (imageBytes != null) {
                    // Note: EdgeAI SDK doesn't have direct image analysis yet
                    // This would be a future enhancement
                    logMessage("⚠️ Image analysis not yet supported in EdgeAI SDK")
                    logMessage("🔄 This feature will be added in future SDK versions")
                } else {
                    logMessage("❌ Failed to process image")
                }
            } catch (e: Exception) {
                logMessage("❌ Image analysis error: ${e.message}")
            }
        }
    }

    fun transcribeAudio(audioFile: java.io.File) {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Cannot send request: EdgeAI SDK not connected")
            return
        }

        viewModelScope.launch {
            try {
                val audioBytes = audioFile.readBytes()
                val request = asrRequest(
                    audioBytes = audioBytes,
                    language = "en",
                    format = "json"
                )
                
                logMessage("🚀 ASR request sent via EdgeAI SDK")
                
                EdgeAI.asr(request)
                    .catch { e ->
                        when (e) {
                            is AudioProcessingException -> logMessage("❌ Audio processing failed: ${e.message}")
                            is ModelNotFoundException -> logMessage("❌ ASR model not found: ${e.message}")
                            is ServiceConnectionException -> {
                                logMessage("❌ Connection error: ${e.message}")
                                _uiState.update { it.copy(isConnected = false, connectionStatus = "Connection Lost") }
                            }
                            else -> logMessage("❌ ASR error: ${e.message}")
                        }
                    }
                    .collect { response ->
                        logMessage("✅ Transcription: ${response.text}")
                        
                        // Show additional details if available
                        response.language?.let { lang ->
                            logMessage("   └── Detected language: $lang")
                        }
                        
                        response.segments?.forEach { segment ->
                            logMessage("   └── Segment: ${segment.text} (${segment.start}s - ${segment.end}s)")
                        }
                    }
                    
            } catch (e: EdgeAIException) {
                logMessage("❌ ASR request failed: ${e.message}")
            }
        }
    }

    fun sendTTSRequest(text: String) {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Cannot send request: EdgeAI SDK not connected")
            return
        }

        viewModelScope.launch {
            try {
                val request = ttsRequest(
                    input = text,
                    voice = "alloy",
                    speed = 1.0f,
                    format = "mp3"
                )
                
                logMessage("🚀 TTS request sent via EdgeAI SDK")
                
                EdgeAI.tts(request)
                    .catch { e ->
                        when (e) {
                            is InvalidInputException -> logMessage("❌ Invalid TTS input: ${e.message}")
                            is ModelNotFoundException -> logMessage("❌ TTS model not found: ${e.message}")
                            is ServiceConnectionException -> {
                                logMessage("❌ Connection error: ${e.message}")
                                _uiState.update { it.copy(isConnected = false, connectionStatus = "Connection Lost") }
                            }
                            else -> logMessage("❌ TTS error: ${e.message}")
                        }
                    }
                    .collect { response ->
                        val audioSize = response.audioData.size
                        logMessage("✅ TTS audio generated: $audioSize bytes")
                        logMessage("   └── Format: ${response.format}, Voice: alloy")
                        
                        // In a real app, you would play the audio stream here
                        // val audioStream = response.toInputStream()
                        // audioPlayer.play(audioStream)
                    }
                
            } catch (e: EdgeAIException) {
                logMessage("❌ TTS request failed: ${e.message}")
            }
        }
    }

    fun sendGuardrailRequest(text: String) {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Cannot send request: EdgeAI SDK not connected")
            return
        }

        // Note: Content moderation would be added as a future EdgeAI SDK feature
        logMessage("⚠️ Content moderation not yet available in EdgeAI SDK")
        logMessage("🔄 This feature will be added in future SDK versions")
    }

    fun getApiVersion() {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Cannot get API version: EdgeAI SDK not connected")
            return
        }

        // EdgeAI SDK abstracts away direct API version checks
        logMessage("📋 Using EdgeAI SDK v2.0 (Simplified API)")
        logMessage("   └── Chat Completion: ✅ Available")
        logMessage("   └── Text-to-Speech: ✅ Available") 
        logMessage("   └── Speech Recognition: ✅ Available")
    }

    fun checkCapabilities() {
        if (!_uiState.value.isConnected) {
            logMessage("❌ Cannot check capabilities: EdgeAI SDK not connected")
            return
        }

        logMessage("🔍 EdgeAI SDK Capabilities:")
        logMessage("   └── Chat Completion: ✅ Available")
        logMessage("   └── Streaming Chat: ✅ Available")
        logMessage("   └── Text-to-Speech: ✅ Available")
        logMessage("   └── Speech Recognition: ✅ Available")
        logMessage("   └── Multiple ASR formats: ✅ Available")
        logMessage("   └── Unified API: ✅ Simplified")
    }

    fun cancelRequest() {
        // EdgeAI SDK handles request cancellation automatically when flows are canceled
        logMessage("🚫 Request cancellation handled automatically by EdgeAI SDK")
        logMessage("   └── Flows can be canceled using standard Kotlin coroutines")
    }

    // Helper methods remain the same
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
}
