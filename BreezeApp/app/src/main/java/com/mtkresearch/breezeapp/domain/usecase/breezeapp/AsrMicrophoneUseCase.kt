package com.mtkresearch.breezeapp.domain.usecase.breezeapp

import android.content.Context
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.asrRequest
import com.mtkresearch.breezeapp.edgeai.ASRResponse
import com.mtkresearch.breezeapp.edgeai.EdgeAIException
import com.mtkresearch.breezeapp.edgeai.InvalidInputException
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import com.mtkresearch.breezeapp.domain.model.breezeapp.BreezeAppError
import com.mtkresearch.breezeapp.edgeai.AudioProcessingException
import com.mtkresearch.breezeapp.edgeai.ModelNotFoundException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

/**
 * UseCase for handling microphone-based speech recognition
 * 
 * Responsibilities:
 * - Process ASR requests from microphone input
 * - Handle ASR-specific error scenarios
 * - Provide unified error handling
 * - Return real-time transcription results
 * 
 * This UseCase follows Clean Architecture principles by:
 * - Being independent of external frameworks
 * - Having a single responsibility
 * - Being easily testable
 */
class AsrMicrophoneUseCase @Inject constructor() {
    
    companion object {
        private const val TAG = "AsrMicrophoneUseCase"
    }
    
    /**
     * Execute a microphone-based speech recognition request
     * 
     * Note: This is a placeholder implementation since EdgeAI SDK
     * doesn't currently support direct microphone input. In a real
     * implementation, you would need to:
     * 1. Record audio from microphone
     * 2. Convert to ByteArray
     * 3. Pass to ASR
     * 
     * @param language The language code for recognition
     * @return Flow of ASRResponse from BreezeApp Engine
     */
    fun execute(
        language: String = "en",
        format: String = "json"
    ): Flow<ASRResponse> {
        
        Log.d(TAG, "Executing microphone ASR request with language: $language")
        
        // For microphone mode, we send empty audio bytes
        // The engine will handle microphone recording directly
        val request = asrRequest(
            audioBytes = byteArrayOf(), // Empty for microphone mode
            language = language,
            format = format,
            stream = true // Enable streaming for real-time processing
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
}