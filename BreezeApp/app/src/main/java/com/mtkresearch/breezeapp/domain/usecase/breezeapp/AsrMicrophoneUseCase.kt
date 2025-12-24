package com.mtkresearch.breezeapp.domain.usecase.breezeapp

import android.util.Log
import com.mtkresearch.breezeapp.core.audio.AudioRecorder
import com.mtkresearch.breezeapp.core.audio.AudioRecordingResult
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.asrRequest
import com.mtkresearch.breezeapp.edgeai.AudioProcessingException
import com.mtkresearch.breezeapp.edgeai.EdgeAIException
import com.mtkresearch.breezeapp.edgeai.ModelNotFoundException
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import com.mtkresearch.breezeapp.edgeai.ASRResponse
import com.mtkresearch.breezeapp.domain.model.breezeapp.BreezeAppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * UseCase for handling microphone-based speech recognition
 * 
 * Responsibilities:
 * - Process ASR requests from microphone input
 * - Adapt behavior based on runner capabilities (streaming vs offline)
 * - Handle ASR-specific error scenarios
 * - Provide unified error handling
 * - Return real-time or final transcription results
 * 
 * This UseCase follows Clean Architecture principles by:
 * - Being independent of external frameworks
 * - Having a single responsibility
 * - Being easily testable
 */
class AsrMicrophoneUseCase @Inject constructor() {
    
    companion object {
        private const val TAG = "AsrMicrophoneUseCase"
        private const val MAX_RECORDING_DURATION_MS = 60000L // 60 seconds
        private const val MIN_AUDIO_SIZE_BYTES = 32000 // ~1 second at 16kHz mono 16-bit
    }
    
    private val audioRecorder = AudioRecorder()
    
    /**
     * Execute a microphone-based speech recognition request
     * 
     * This method adapts its behavior based on the selected runner's capabilities:
     * - **Streaming runners**: Enable microphone in engine, stream audio in real-time
     * - **Offline runners**: Record audio locally, send complete buffer when done
     * 
     * @param language The language code for recognition
     * @param format The response format (default: "json")
     * @return Flow of ASRResponse from BreezeApp Engine
     */
    suspend fun execute(
        language: String = "en",
        format: String = "json"
    ): Flow<ASRResponse> {
        
        Log.d(TAG, "Executing microphone ASR request with language: $language")
        
        // Query runner capabilities to determine mode
        val runnerInfo = try {
            EdgeAI.getSelectedRunnerInfo("ASR")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query runner info, defaulting to streaming mode: ${e.message}")
            null
        }
        
        val supportsStreaming = runnerInfo?.supportsStreaming ?: true
        Log.i(TAG, "Selected ASR runner: ${runnerInfo?.name ?: "unknown"}, supportsStreaming=$supportsStreaming")
        
        return if (supportsStreaming) {
            // Streaming mode: Engine handles microphone directly
            executeStreamingMode(language, format)
        } else {
            // Offline mode: Record audio locally, then send buffer
            executeOfflineMode(language, format)
        }
    }
    
    /**
     * Execute ASR in streaming mode (real-time microphone)
     * Engine handles microphone recording and streams results
     */
    private fun executeStreamingMode(
        language: String,
        format: String
    ): Flow<ASRResponse> {
        Log.d(TAG, "Using streaming mode for microphone ASR")
        
        val request = asrRequest(
            audioBytes = byteArrayOf(), // Empty for microphone mode
            language = language,
            format = format,
            metadata = mapOf("microphone_mode" to "true")
        )
        
        return EdgeAI.asr(request)
            .catch { e ->
                // Handle cancellation (both direct and EdgeAI-wrapped)
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Microphone ASR request cancelled - propagating to ViewModel")
                    throw e // Re-throw cancellation to be handled by ViewModel
                }
                
                // Handle EdgeAI wrapped cancellation - convert back to CancellationException
                if (e.message?.contains("was cancelled", ignoreCase = true) == true) {
                    Log.d(TAG, "Microphone ASR request cancelled (EdgeAI wrapped) - converting to CancellationException")
                    throw kotlinx.coroutines.CancellationException("User cancelled microphone recording")
                }
                
                Log.e(TAG, "Microphone ASR request failed: ${e.message}")
                when (e) {
                    is AudioProcessingException -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "Audio processing failed")
                    is ModelNotFoundException -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "ASR model not found")
                    is ServiceConnectionException -> throw BreezeAppError.ConnectionError.ServiceDisconnected(e.message ?: "ASR service connection error")
                    is EdgeAIException -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "EdgeAI error")
                    else -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "Unexpected ASR error")
                }
            }
    }
    
    /**
     * Execute ASR in offline mode (record-then-send)
     * Client records audio locally, then sends complete buffer to offline runner
     */
    private fun executeOfflineMode(
        language: String,
        format: String
    ): Flow<ASRResponse> = flow {
        Log.d(TAG, "Using offline mode for microphone ASR - recording audio locally")
        
        var audioData: ByteArray? = null
        var recordingError: String? = null
        var audioProcessed = false
        
        try {
            // Step 1: Record audio locally
            audioRecorder.recordAudio(MAX_RECORDING_DURATION_MS).collect { result ->
                when (result) {
                    is AudioRecordingResult.Started -> {
                        Log.d(TAG, "Audio recording started")
                    }
                    is AudioRecordingResult.Recording -> {
                        Log.d(TAG, "Recording progress: ${(result.progress * 100).toInt()}%")
                    }
                    is AudioRecordingResult.Completed -> {
                        Log.d(TAG, "Audio recording completed: ${result.audioData.size} bytes")
                        audioData = result.audioData
                    }
                    is AudioRecordingResult.Cancelled -> {
                        Log.d(TAG, "Audio recording cancelled: ${result.partialAudioData.size} bytes")
                        audioData = result.partialAudioData
                    }
                    is AudioRecordingResult.Error -> {
                        Log.e(TAG, "Audio recording error: ${result.message}")
                        recordingError = result.message
                    }
                }
            }
            
            // Step 2: Process audio if we have valid data
            if (recordingError == null && audioData != null && audioData!!.size >= MIN_AUDIO_SIZE_BYTES) {
                processAudioData(audioData!!, language, format, this)
                audioProcessed = true
            }
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            // CRITICAL: Process audio even if flow was cancelled
            Log.d(TAG, "Flow cancelled, but attempting to process recorded audio")
            if (!audioProcessed && audioData != null && audioData!!.size >= MIN_AUDIO_SIZE_BYTES) {
                try {
                    processAudioData(audioData!!, language, format, this)
                    audioProcessed = true
                    // Emit a status message to indicate processing was initiated
                    // The actual result will be logged by processAudioData if emission fails
                    Log.i(TAG, "Audio processing initiated despite flow cancellation")
                } catch (processError: Exception) {
                    Log.e(TAG, "Failed to process audio after cancellation: ${processError.message}")
                }
            }
            // IMPORTANT: Don't re-throw CancellationException
            // We've processed the audio, so this is not an error condition
            // The result will be logged even if it can't be emitted to the cancelled flow
            return@flow  // Exit the flow builder normally, not with an exception
        }
        
        // Step 3: Validate and throw errors if needed
        if (!audioProcessed) {
            if (recordingError != null) {
                throw BreezeAppError.AsrError.RecognitionFailed("Audio recording failed: $recordingError")
            }
            if (audioData == null || audioData!!.size < MIN_AUDIO_SIZE_BYTES) {
                throw BreezeAppError.AsrError.RecognitionFailed(
                    "Insufficient audio data recorded (${audioData?.size ?: 0} bytes). Please speak longer."
                )
            }
        }
    }
    
    /**
     * Helper function to process audio data and emit ASR results
     * Uses supervisorScope to ensure processing completes even if parent is cancelled
     */
    private suspend fun processAudioData(
        audioData: ByteArray,
        language: String,
        format: String,
        flowCollector: kotlinx.coroutines.flow.FlowCollector<ASRResponse>
    ) {
        Log.d(TAG, "Sending ${audioData.size} bytes to offline ASR runner")
        
        // Use supervisorScope to protect from parent cancellation while maintaining same context
        kotlinx.coroutines.supervisorScope {
            try {
                val request = asrRequest(
                    audioBytes = audioData,
                    language = language,
                    format = format,
                    metadata = mapOf("microphone_mode" to "false") // Offline mode
                )
                
                // Collect and emit ASR results
                EdgeAI.asr(request)
                    .catch { e ->
                        Log.e(TAG, "Offline ASR processing failed: ${e.message}")
                        when (e) {
                            is AudioProcessingException -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "Audio processing failed")
                            is ModelNotFoundException -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "ASR model not found")
                            is ServiceConnectionException -> throw BreezeAppError.ConnectionError.ServiceDisconnected(e.message ?: "Service connection error")
                            is CancellationException -> {
                                // Don't re-throw cancellation - allow processing to complete
                                // The audio was already recorded, we should try to transcribe it
                                Log.w(TAG, "ASR request was cancelled, but attempting to complete processing")
                                // Don't throw - just log and continue
                            }
                            is EdgeAIException -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "EdgeAI error")
                            else -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "Unexpected ASR error")
                        }
                    }
                    .collect { response ->
                        try {
                            flowCollector.emit(response)
                        } catch (e: CancellationException) {
                            // Parent flow was cancelled, but we still got a result!
                            // Log it so it's not lost
                            Log.i(TAG, "✅ ASR result received (flow cancelled): ${response.text}")
                            Log.i(TAG, "Note: Result was not delivered to UI because flow was cancelled")
                            // Don't re-throw - we've logged the result
                        }
                    }
            } catch (e: CancellationException) {
                // Log but don't throw - allow graceful completion
                Log.d(TAG, "ASR processing cancelled, but audio was sent")
            }
        }
    }
    
    /**
     * Stop ongoing audio recording (for offline mode)
     */
    fun stopRecording() {
        Log.d(TAG, "Stopping audio recording")
        audioRecorder.stopRecording()
    }
}