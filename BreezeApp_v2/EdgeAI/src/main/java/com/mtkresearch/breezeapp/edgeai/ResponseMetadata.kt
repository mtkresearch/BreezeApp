package com.mtkresearch.breezeapp.edgeai.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A sealed interface representing the metadata associated with an AI response.
 * This provides a type-safe way to access response-specific details.
 */
@Parcelize
sealed interface ResponseMetadata : Parcelable {

    /**
     * Standard metadata included in most responses.
     *
     * @param modelName The name of the model that processed the request.
     * @param processingTimeMs The time taken to process the request, in milliseconds.
     * @param backend The hardware backend used for inference (e.g., "CPU", "GPU").
     */
    @Parcelize
    data class Standard(
        val modelName: String,
        val processingTimeMs: Long,
        val backend: String? = null
    ) : ResponseMetadata

    /**
     * Metadata specific to text generation (LLM) responses.
     *
     * @param standard The common metadata.
     * @param tokenCount The number of tokens in the generated response.
     */
    @Parcelize
    data class TextGeneration(
        val standard: Standard,
        val tokenCount: Int
    ) : ResponseMetadata

    /**
     * Metadata specific to audio transcription (ASR) responses.
     *
     * @param standard The common metadata.
     * @param confidence The confidence score of the transcription (0.0 to 1.0).
     * @param audioDurationMs The duration of the processed audio, in milliseconds.
     */
    @Parcelize
    data class AudioTranscription(
        val standard: Standard,
        val confidence: Float,
        val audioDurationMs: Long
    ) : ResponseMetadata
    
    /**
     * Metadata specific to text-to-speech (TTS) responses.
     *
     * @param standard The common metadata.
     * @param audioDurationMs The duration of the synthesized audio, in milliseconds.
     * @param voiceId The voice ID used for synthesis.
     */
    @Parcelize
    data class SpeechSynthesis(
        val standard: Standard,
        val audioDurationMs: Long,
        val voiceId: String?
    ) : ResponseMetadata
} 