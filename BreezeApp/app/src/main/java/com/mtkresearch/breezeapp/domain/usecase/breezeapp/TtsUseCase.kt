package com.mtkresearch.breezeapp.domain.usecase.breezeapp

import android.util.Log
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.ttsRequest
import com.mtkresearch.breezeapp.edgeai.TTSResponse
import com.mtkresearch.breezeapp.edgeai.EdgeAIException
import com.mtkresearch.breezeapp.edgeai.InvalidInputException
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import com.mtkresearch.breezeapp.domain.model.breezeapp.BreezeAppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

/**
 * UseCase for handling text-to-speech requests
 * 
 * Responsibilities:
 * - Process TTS requests
 * - Handle TTS-specific error scenarios
 * - Provide unified error handling
 * - Return audio data
 * 
 * This UseCase follows Clean Architecture principles by:
 * - Being independent of external frameworks
 * - Having a single responsibility
 * - Being easily testable
 */
class TtsUseCase @Inject constructor() {
    
    companion object {
        private const val TAG = "TtsUseCase"
    }
    
    /**
     * Execute a text-to-speech request
     * 
     * @param text The text to convert to speech
     * @param voice The voice to use for synthesis
     * @param speed The speed of speech (0.5 to 2.0)
     * @return Flow of TTSResponse from BreezeApp Engine
     */
    fun execute(
        text: String,
        voice: String = "alloy",
        speed: Float = 1.0f,
        format: String = "pcm"
    ): Flow<TTSResponse> {
        
        Log.d(TAG, "Executing TTS request: '$text' with voice: $voice")
        
        val request = ttsRequest(
            input = text,
            voice = voice,
            speed = speed,
            format = format
        )
        
        return EdgeAI.tts(request)
            .catch { e ->
                Log.e(TAG, "TTS request failed: ${e.message}")
                when (e) {
                    is InvalidInputException -> throw BreezeAppError.TtsError.InvalidText(e.message ?: "Invalid text")
                    is ServiceConnectionException -> throw BreezeAppError.ConnectionError.ServiceDisconnected(e.message ?: "Connection error")
                    is EdgeAIException -> throw BreezeAppError.TtsError.AudioGenerationFailed(e.message ?: "Audio generation failed")
                    else -> throw BreezeAppError.UnknownError(e.message ?: "Unexpected error")
                }
            }
    }
}