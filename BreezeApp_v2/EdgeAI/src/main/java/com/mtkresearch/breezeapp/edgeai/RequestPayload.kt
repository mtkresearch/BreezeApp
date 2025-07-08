package com.mtkresearch.breezeapp.edgeai.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A sealed interface representing the specific payload for an AI request.
 * Using a sealed interface ensures type safety and makes it easy to handle different
 * kinds of requests in a structured way.
 */
@Parcelize
sealed interface RequestPayload : Parcelable {

    /**
     * Payload for a text-based chat or instruction request (LLM).
     *
     * @param prompt The primary text input for the model.
     * @param modelName Optional: The specific model to target.
     * @param temperature Optional: The creativity of the response (e.g., 0.0 to 1.0).
     * @param maxTokens Optional: The maximum number of tokens for the response.
     */
    @Parcelize
    data class TextChat(
        val prompt: String,
        val modelName: String? = null,
        val temperature: Float? = null,
        val maxTokens: Int? = null,
        val streaming: Boolean = false
    ) : RequestPayload

    /**
     * Payload for an image analysis request (VLM).
     *
     * @param image The image data as a byte array.
     * @param prompt Optional: A question or instruction related to the image.
     * @param modelName Optional: The specific model to target.
     */
    @Parcelize
    data class ImageAnalysis(
        val image: ByteArray,
        val prompt: String? = null,
        val modelName: String? = null,
        val streaming: Boolean = false
    ) : RequestPayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ImageAnalysis
            if (!image.contentEquals(other.image)) return false
            if (prompt != other.prompt) return false
            if (modelName != other.modelName) return false
            return true
        }

        override fun hashCode(): Int {
            var result = image.contentHashCode()
            result = 31 * result + (prompt?.hashCode() ?: 0)
            result = 31 * result + (modelName?.hashCode() ?: 0)
            return result
        }
    }

    /**
     * Payload for an audio transcription request (ASR).
     *
     * @param audio The audio data as a byte array.
     * @param language Optional: The language of the audio (e.g., "en-US").
     * @param modelName Optional: The specific model to target.
     */
    @Parcelize
    data class AudioTranscription(
        val audio: ByteArray,
        val language: String? = null,
        val modelName: String? = null,
        val streaming: Boolean = false
    ) : RequestPayload {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as AudioTranscription
            if (!audio.contentEquals(other.audio)) return false
            if (language != other.language) return false
            if (modelName != other.modelName) return false
            return true
        }

        override fun hashCode(): Int {
            var result = audio.contentHashCode()
            result = 31 * result + (language?.hashCode() ?: 0)
            result = 31 * result + (modelName?.hashCode() ?: 0)
            return result
        }
    }
    
    /**
     * Payload for a text-to-speech synthesis request (TTS).
     *
     * @param text The text to be converted to speech.
     * @param voiceId Optional: The ID of the voice to use.
     * @param speed Optional: The speaking rate (e.g., 1.0 for normal).
     */
    @Parcelize
    data class SpeechSynthesis(
        val text: String,
        val voiceId: String? = null,
        val speed: Float? = null,
        val modelName: String? = null,
        val streaming: Boolean = false
    ) : RequestPayload

    /**
     * Payload for a content moderation request (Guardian).
     *
     * @param text The text to be analyzed for safety.
     * @param checkType Optional: The type of safety check to perform.
     */
    @Parcelize
    data class ContentModeration(
        val text: String,
        val checkType: String? = null
    ) : RequestPayload
} 