package com.mtkresearch.breezeapp.edgeai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Request for OpenAI-compatible Text-to-Speech generation
 * Based on: https://platform.openai.com/docs/api-reference/audio/createSpeech
 */
@Parcelize
data class TTSRequest(
    /**
     * The text to be converted to speech. Maximum length: 4096 characters.
     */
    val input: String,
    
    /**
     * TTS model name to use for generation
     */
    val model: String,
    
    /**
     * Voice style. Supported values: alloy, ash, ballad, coral, echo, fable, onyx, nova, sage, shimmer, verse.
     */
    val voice: String,
    
    /**
     * Additional instructions to control voice style. 
     * Supported only by gpt-4o-mini-tts; not supported by tts-1/tts-1-hd.
     */
    val instructions: String? = null,
    
    /**
     * Output audio format. Supported values: mp3, opus, aac, flac, wav, pcm.
     * Default: mp3
     */
    val responseFormat: String? = "mp3",
    
    /**
     * Playback speed, range: 0.25~4.0, default is 1.0
     */
    val speed: Float? = 1.0f
) : Parcelable {
    
    init {
        require(input.length <= 4096) { "Input text must not exceed 4096 characters" }
        require(speed == null || speed in 0.25f..4.0f) { "Speed must be between 0.25 and 4.0" }
        
        val supportedVoices = listOf("alloy", "ash", "ballad", "coral", "echo", "fable", "onyx", "nova", "sage", "shimmer", "verse")
        require(voice in supportedVoices) { "Voice must be one of: ${supportedVoices.joinToString()}" }
        
        val supportedFormats = listOf("mp3", "opus", "aac", "flac", "wav", "pcm")
        require(responseFormat == null || responseFormat in supportedFormats) { 
            "Response format must be one of: ${supportedFormats.joinToString()}" 
        }
    }
} 