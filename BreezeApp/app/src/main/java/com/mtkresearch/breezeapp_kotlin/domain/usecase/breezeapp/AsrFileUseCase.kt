package com.mtkresearch.breezeapp_kotlin.domain.usecase.breezeapp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.asrRequest
import com.mtkresearch.breezeapp.edgeai.ASRResponse
import com.mtkresearch.breezeapp.edgeai.EdgeAIException
import com.mtkresearch.breezeapp.edgeai.InvalidInputException
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import com.mtkresearch.breezeapp_kotlin.domain.model.breezeapp.BreezeAppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

/**
 * UseCase for handling file-based speech recognition
 * 
 * Responsibilities:
 * - Process ASR requests from audio files
 * - Handle ASR-specific error scenarios
 * - Provide unified error handling
 * - Return transcription results
 * 
 * This UseCase follows Clean Architecture principles by:
 * - Being independent of external frameworks
 * - Having a single responsibility
 * - Being easily testable
 */
class AsrFileUseCase @Inject constructor() {
    
    companion object {
        private const val TAG = "AsrFileUseCase"
    }
    
    /**
     * Execute a file-based speech recognition request
     * 
     * @param audioBytes The audio file bytes to transcribe
     * @param language The language code for recognition
     * @return Flow of ASRResponse from BreezeApp Engine
     */
    suspend fun execute(
        audioBytes: ByteArray,
        language: String = "en"
    ): Flow<ASRResponse> {
        
        Log.d(TAG, "Executing ASR file request with ${audioBytes.size} bytes")
        
        val request = asrRequest(
            audioBytes = audioBytes,
            language = language
        )
        
        return EdgeAI.asr(request)
            .catch { e ->
                Log.e(TAG, "ASR file request failed: ${e.message}")
                when (e) {
                    is InvalidInputException -> throw BreezeAppError.AsrError.AudioFileNotFound(e.message ?: "Audio file not found")
                    is ServiceConnectionException -> throw BreezeAppError.ConnectionError.ServiceDisconnected(e.message ?: "Connection error")
                    is EdgeAIException -> throw BreezeAppError.AsrError.RecognitionFailed(e.message ?: "Recognition failed")
                    else -> throw BreezeAppError.UnknownError(e.message ?: "Unexpected error")
                }
            }
    }
}