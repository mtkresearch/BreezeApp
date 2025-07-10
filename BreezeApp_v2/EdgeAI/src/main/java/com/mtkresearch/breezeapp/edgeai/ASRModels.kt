package com.mtkresearch.breezeapp.edgeai

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.io.File

/**
 * Request for OpenAI-compatible Automatic Speech Recognition
 * Based on: https://platform.openai.com/docs/api-reference/audio/createTranscription
 */
@Parcelize
data class ASRRequest(
    /**
     * The audio file to be transcribed. Supported formats: flac, mp3, mp4, mpeg, mpga, m4a, ogg, wav, webm.
     */
    val file: ByteArray,
    
    /**
     * The model ID to use for transcription
     */
    val model: String,
    
    /**
     * The language of the input audio (ISO-639-1 code, e.g., "en"). 
     * Specifying this can improve accuracy and speed.
     */
    val language: String? = null,
    
    /**
     * Optional prompt text to guide the model's style or to continue from previous audio.
     * Must match the audio language.
     */
    val prompt: String? = null,
    
    /**
     * Output format. Options: json, text, srt, verbose_json, vtt.
     * Default: json
     */
    val responseFormat: String? = "json",
    
    /**
     * Specify additional information to include in the response.
     * "logprobs" returns log probabilities for each token (only supported by gpt-4o-transcribe/mini with response_format=json).
     */
    val include: List<String>? = null,
    
    /**
     * Whether to return data as a stream (Server-Sent Events).
     * Default: false
     */
    val stream: Boolean? = false,
    
    /**
     * Sampling temperature, range 0~1. Higher values (e.g., 0.8) produce more random output,
     * lower values (e.g., 0.2) are more deterministic. When set to 0, the model will automatically adjust the temperature.
     * Default: 0
     */
    val temperature: Float? = 0f,
    
    /**
     * Specify the granularity of timestamps to generate. Must be used with response_format=verbose_json.
     * Options: word, segment. "word" increases latency.
     * Default: segment
     */
    val timestampGranularities: List<String>? = listOf("segment")
) : Parcelable {
    
    init {
        val supportedFormats = listOf("json", "text", "srt", "verbose_json", "vtt")
        require(responseFormat == null || responseFormat in supportedFormats) {
            "Response format must be one of: ${supportedFormats.joinToString()}"
        }
        
        require(temperature == null || temperature in 0f..1f) {
            "Temperature must be between 0.0 and 1.0"
        }
        
        val supportedGranularities = listOf("word", "segment")
        timestampGranularities?.forEach { granularity ->
            require(granularity in supportedGranularities) {
                "Timestamp granularity must be one of: ${supportedGranularities.joinToString()}"
            }
        }
        
        if (timestampGranularities?.contains("word") == true) {
            require(responseFormat == "verbose_json") {
                "Timestamp granularity 'word' requires response_format='verbose_json'"
            }
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as ASRRequest
        
        if (!file.contentEquals(other.file)) return false
        if (model != other.model) return false
        if (language != other.language) return false
        if (prompt != other.prompt) return false
        if (responseFormat != other.responseFormat) return false
        if (include != other.include) return false
        if (stream != other.stream) return false
        if (temperature != other.temperature) return false
        if (timestampGranularities != other.timestampGranularities) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = file.contentHashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + (language?.hashCode() ?: 0)
        result = 31 * result + (prompt?.hashCode() ?: 0)
        result = 31 * result + (responseFormat?.hashCode() ?: 0)
        result = 31 * result + (include?.hashCode() ?: 0)
        result = 31 * result + (stream?.hashCode() ?: 0)
        result = 31 * result + (temperature?.hashCode() ?: 0)
        result = 31 * result + (timestampGranularities?.hashCode() ?: 0)
        return result
    }
}

/**
 * Response from OpenAI-compatible speech recognition
 * The exact structure depends on the responseFormat parameter
 */
@Parcelize
data class ASRResponse(
    /**
     * The transcribed text (always present regardless of format)
     */
    val text: String,
    
    /**
     * Detailed segments (only present in verbose_json format)
     */
    val segments: List<TranscriptionSegment>? = null,
    
    /**
     * Detected language (only present in verbose_json format)
     */
    val language: String? = null,
    
    /**
     * Raw response in the requested format (text, srt, vtt, etc.)
     */
    val rawResponse: String? = null,
    
    /**
     * Whether this is a streaming chunk (true) or final response (false)
     */
    val isChunk: Boolean = false
) : Parcelable

/**
 * Transcription segment for verbose_json format
 */
@Parcelize
data class TranscriptionSegment(
    /**
     * Segment identifier
     */
    val id: Int,
    
    /**
     * Seek position
     */
    val seek: Int,
    
    /**
     * Start time in seconds
     */
    val start: Float,
    
    /**
     * End time in seconds
     */
    val end: Float,
    
    /**
     * Transcribed text for this segment
     */
    val text: String,
    
    /**
     * Token IDs
     */
    val tokens: List<Int>? = null,
    
    /**
     * Temperature used for this segment
     */
    val temperature: Float? = null,
    
    /**
     * Average log probability
     */
    val avgLogprob: Float? = null,
    
    /**
     * Compression ratio
     */
    val compressionRatio: Float? = null,
    
    /**
     * No speech probability
     */
    val noSpeechProb: Float? = null,
    
    /**
     * Word-level timestamps (only when timestampGranularities includes "word")
     */
    val words: List<WordTimestamp>? = null
) : Parcelable

/**
 * Word-level timestamp information
 */
@Parcelize
data class WordTimestamp(
    /**
     * The word
     */
    val word: String,
    
    /**
     * Start time of the word in seconds
     */
    val start: Float,
    
    /**
     * End time of the word in seconds
     */
    val end: Float
) : Parcelable 