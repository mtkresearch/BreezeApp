package com.mtkresearch.breezeapp.router.domain.model

/**
 * Unified inference request data structure for all AI operations.
 * 
 * This class represents a standardized format for AI inference requests across
 * different capabilities (LLM, TTS, ASR, VLM). It encapsulates all necessary
 * information for processing while maintaining type safety and extensibility.
 * 
 * ## Usage Examples
 * ```kotlin
 * // Text generation request
 * val chatRequest = InferenceRequest(
 *     sessionId = "chat-123",
 *     inputs = mapOf(INPUT_TEXT to "Hello, how are you?"),
 *     params = mapOf("temperature" to 0.7, "max_tokens" to 150)
 * )
 * 
 * // TTS request
 * val ttsRequest = InferenceRequest(
 *     sessionId = "tts-456", 
 *     inputs = mapOf(INPUT_TEXT to "Hello world"),
 *     params = mapOf("voice" to "alloy", "speed" to 1.0)
 * )
 * 
 * // ASR request
 * val asrRequest = InferenceRequest(
 *     sessionId = "asr-789",
 *     inputs = mapOf(INPUT_AUDIO to audioByteArray),
 *     params = mapOf("language" to "en")
 * )
 * ```
 * 
 * @param sessionId Unique identifier for tracking this request across the system
 * @param inputs Map of input data using predefined keys (INPUT_TEXT, INPUT_AUDIO, etc.)
 * @param params Optional inference parameters (temperature, max_tokens, voice, etc.)
 * @param timestamp Request creation timestamp for logging and debugging
 * 
 * @see INPUT_TEXT for text input key
 * @see INPUT_AUDIO for audio input key  
 * @see INPUT_IMAGE for image input key
 */
data class InferenceRequest(
    val sessionId: String,
    val inputs: Map<String, Any>,
    val params: Map<String, Any> = emptyMap(),
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        // Standard input keys for different data types
        
        /** Key for text input data (String) */
        const val INPUT_TEXT = "text"
        
        /** Key for audio input data (ByteArray) */
        const val INPUT_AUDIO = "audio"
        
        /** Key for image input data (ByteArray or encoded string) */
        const val INPUT_IMAGE = "image"
        
        /** Key for audio ID reference (String) */
        const val INPUT_AUDIO_ID = "audio_id"
        
        // Standard parameter keys for inference configuration
        
        /** Temperature parameter for controlling randomness (Float, 0.0-2.0) */
        const val PARAM_TEMPERATURE = "temperature"
        
        /** Maximum tokens to generate (Int) */
        const val PARAM_MAX_TOKENS = "max_tokens"
        
        /** Language code for processing (String, e.g., "en", "zh") */
        const val PARAM_LANGUAGE = "language"
    }
} 