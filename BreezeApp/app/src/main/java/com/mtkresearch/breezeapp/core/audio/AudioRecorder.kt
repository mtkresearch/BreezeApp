package com.mtkresearch.breezeapp.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Audio recording utility for offline ASR mode
 * 
 * Handles microphone recording and provides audio data as ByteArray
 * for processing through AsrFileUseCase
 */
class AudioRecorder {
    
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000 // 16kHz sample rate
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }
    
    private var audioRecord: AudioRecord? = null
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * BUFFER_SIZE_FACTOR
    
    // Add manual stop flag for controlled termination
    @Volatile
    private var isManualStop = false
    
    /**
     * Record audio and return as Flow of recording status and final audio data
     * ROBUST VERSION: Guarantees final result emission even under cancellation
     */
    fun recordAudio(maxDurationMs: Long = 60000): Flow<AudioRecordingResult> = flow {
        val outputStream = ByteArrayOutputStream()
        var finalResultEmitted = false
        
        try {
            // Initialize AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                emit(AudioRecordingResult.Error("Failed to initialize audio recorder"))
                return@flow
            }
            
            // Start recording
            audioRecord?.startRecording()
            emit(AudioRecordingResult.Started)
            
            val buffer = ShortArray(bufferSize / 2) // Divide by 2 because we're using Short (2 bytes)
            val startTime = System.currentTimeMillis()
            var wasNaturallyCompleted = false
            
            // Record audio until cancelled, manually stopped, or max duration reached
            while (coroutineContext.isActive && !isManualStop) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - startTime > maxDurationMs) {
                    Log.d(TAG, "Max recording duration reached: ${maxDurationMs}ms")
                    wasNaturallyCompleted = true
                    break
                }
                
                val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readBytes > 0) {
                    // Convert Short array to ByteArray
                    val byteBuffer = ByteArray(readBytes * 2)
                    for (i in 0 until readBytes) {
                        val sample = buffer[i].toInt()
                        byteBuffer[i * 2] = (sample and 0xff).toByte()
                        byteBuffer[i * 2 + 1] = ((sample shr 8) and 0xff).toByte()
                    }
                    outputStream.write(byteBuffer)
                    
                    // Emit progress
                    val progress = (currentTime - startTime).toFloat() / maxDurationMs.toFloat()
                    emit(AudioRecordingResult.Recording(progress.coerceIn(0f, 1f)))
                }
            }
            
            // ROBUST: Always emit final result before any exception handling
            val audioData = outputStream.toByteArray()
            if (wasNaturallyCompleted) {
                // Recording completed naturally (max duration reached)
                Log.d(TAG, "Recording completed naturally: ${audioData.size} bytes")
                emit(AudioRecordingResult.Completed(audioData))
                finalResultEmitted = true
            } else if (isManualStop) {
                // Recording was manually stopped by user
                Log.d(TAG, "Recording manually stopped by user: ${audioData.size} bytes recorded")
                emit(AudioRecordingResult.Cancelled(audioData))
                finalResultEmitted = true
            } else {
                // Recording was cancelled by coroutine cancellation
                Log.d(TAG, "Recording cancelled by coroutine: ${audioData.size} bytes recorded")
                emit(AudioRecordingResult.Cancelled(audioData))
                finalResultEmitted = true
            }
            
        } catch (e: kotlinx.coroutines.CancellationException) {
            // ROBUST: Ensure result is emitted even if cancellation happens during recording
            if (!finalResultEmitted) {
                val audioData = outputStream.toByteArray()
                Log.d(TAG, "Recording cancelled via exception: ${audioData.size} bytes recorded")
                emit(AudioRecordingResult.Cancelled(audioData))
                finalResultEmitted = true
            }
            // DON'T re-throw - let the flow complete normally
        } catch (e: Exception) {
            // ROBUST: Emit error result if no final result was emitted yet
            if (!finalResultEmitted) {
                Log.e(TAG, "Recording failed: ${e.message}")
                emit(AudioRecordingResult.Error(e.message ?: "Unknown recording error"))
                finalResultEmitted = true
            }
        } finally {
            // ROBUST: Final safety net - ensure some result is always emitted
            if (!finalResultEmitted) {
                val audioData = outputStream.toByteArray()
                Log.w(TAG, "Emergency result emission: ${audioData.size} bytes")
                emit(AudioRecordingResult.Cancelled(audioData))
            }
            stopRecording()
            outputStream.close()
            isManualStop = false // Reset flag for next recording
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Stop recording manually - triggers controlled termination
     */
    fun stopRecording() {
        Log.d(TAG, "Manual stop recording requested")
        isManualStop = true
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Audio recording stopped and released")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording: ${e.message}")
        }
    }
}

/**
 * Sealed class representing different states of audio recording
 */
sealed class AudioRecordingResult {
    object Started : AudioRecordingResult()
    data class Recording(val progress: Float) : AudioRecordingResult()
    data class Completed(val audioData: ByteArray) : AudioRecordingResult()
    data class Cancelled(val partialAudioData: ByteArray) : AudioRecordingResult()
    data class Error(val message: String) : AudioRecordingResult()
}