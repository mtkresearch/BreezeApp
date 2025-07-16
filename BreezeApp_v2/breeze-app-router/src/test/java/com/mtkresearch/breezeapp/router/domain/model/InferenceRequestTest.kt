package com.mtkresearch.breezeapp.router.domain.model

import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for InferenceRequest domain model.
 * 
 * Tests request creation, input validation, parameter handling,
 * and constant definitions to ensure correct domain logic.
 */
class InferenceRequestTest {

    @Test
    fun `should create basic inference request with required parameters`() {
        // Given
        val sessionId = "test-session-123"
        val inputs = mapOf("text" to "Hello world")
        
        // When
        val request = InferenceRequest(sessionId, inputs)
        
        // Then
        assertEquals(sessionId, request.sessionId)
        assertEquals(inputs, request.inputs)
        assertTrue("Params should be empty by default", request.params.isEmpty())
        assertTrue("Timestamp should be recent", 
            System.currentTimeMillis() - request.timestamp < 1000)
    }

    @Test
    fun `should create inference request with custom parameters`() {
        // Given
        val sessionId = "chat-456"
        val inputs = mapOf(InferenceRequest.INPUT_TEXT to "Generate a story")
        val params = mapOf(
            InferenceRequest.PARAM_TEMPERATURE to 0.7f,
            InferenceRequest.PARAM_MAX_TOKENS to 150,
            "custom_param" to "custom_value"
        )
        val customTimestamp = 1234567890L
        
        // When
        val request = InferenceRequest(sessionId, inputs, params, customTimestamp)
        
        // Then
        assertEquals(sessionId, request.sessionId)
        assertEquals(inputs, request.inputs)
        assertEquals(params, request.params)
        assertEquals(customTimestamp, request.timestamp)
    }

    @Test
    fun `should handle different input types correctly`() {
        // Given
        val textInput = "Hello AI"
        val audioData = byteArrayOf(1, 2, 3, 4, 5)
        val imageData = byteArrayOf(10, 20, 30)
        val audioId = "audio-ref-123"
        
        // When
        val textRequest = InferenceRequest(
            "text-req",
            mapOf(InferenceRequest.INPUT_TEXT to textInput)
        )
        
        val audioRequest = InferenceRequest(
            "audio-req", 
            mapOf(InferenceRequest.INPUT_AUDIO to audioData)
        )
        
        val imageRequest = InferenceRequest(
            "image-req",
            mapOf(InferenceRequest.INPUT_IMAGE to imageData)
        )
        
        val audioIdRequest = InferenceRequest(
            "audio-id-req",
            mapOf(InferenceRequest.INPUT_AUDIO_ID to audioId)
        )
        
        // Then
        assertEquals(textInput, textRequest.inputs[InferenceRequest.INPUT_TEXT])
        assertArrayEquals(audioData, audioRequest.inputs[InferenceRequest.INPUT_AUDIO] as ByteArray)
        assertArrayEquals(imageData, imageRequest.inputs[InferenceRequest.INPUT_IMAGE] as ByteArray)
        assertEquals(audioId, audioIdRequest.inputs[InferenceRequest.INPUT_AUDIO_ID])
    }

    @Test
    fun `should handle multiple inputs in single request`() {
        // Given
        val inputs = mapOf(
            InferenceRequest.INPUT_TEXT to "Describe this image",
            InferenceRequest.INPUT_IMAGE to byteArrayOf(1, 2, 3),
            "context" to "Additional context"
        )
        
        // When
        val request = InferenceRequest("multi-input-req", inputs)
        
        // Then
        assertEquals(3, request.inputs.size)
        assertEquals("Describe this image", request.inputs[InferenceRequest.INPUT_TEXT])
        assertTrue("Should contain image data", request.inputs.containsKey(InferenceRequest.INPUT_IMAGE))
        assertEquals("Additional context", request.inputs["context"])
    }

    @Test
    fun `should handle standard parameters correctly`() {
        // Given
        val params = mapOf(
            InferenceRequest.PARAM_TEMPERATURE to 0.8f,
            InferenceRequest.PARAM_MAX_TOKENS to 200,
            InferenceRequest.PARAM_LANGUAGE to "en"
        )
        
        // When
        val request = InferenceRequest(
            "param-test",
            mapOf(InferenceRequest.INPUT_TEXT to "test"),
            params
        )
        
        // Then
        assertEquals(0.8f, request.params[InferenceRequest.PARAM_TEMPERATURE])
        assertEquals(200, request.params[InferenceRequest.PARAM_MAX_TOKENS])
        assertEquals("en", request.params[InferenceRequest.PARAM_LANGUAGE])
    }

    @Test
    fun `should handle empty and null inputs gracefully`() {
        // Given & When
        val emptyInputs = InferenceRequest("empty-req", emptyMap())
        val emptyParams = InferenceRequest("empty-params", mapOf("key" to "value"), emptyMap())
        
        // Then
        assertTrue("Empty inputs should be allowed", emptyInputs.inputs.isEmpty())
        assertTrue("Empty params should be allowed", emptyParams.params.isEmpty())
        assertEquals(1, emptyParams.inputs.size)
    }

    @Test
    fun `should validate input constants are correctly defined`() {
        // Then
        assertEquals("text", InferenceRequest.INPUT_TEXT)
        assertEquals("audio", InferenceRequest.INPUT_AUDIO)
        assertEquals("image", InferenceRequest.INPUT_IMAGE)
        assertEquals("audio_id", InferenceRequest.INPUT_AUDIO_ID)
    }

    @Test
    fun `should validate parameter constants are correctly defined`() {
        // Then
        assertEquals("temperature", InferenceRequest.PARAM_TEMPERATURE)
        assertEquals("max_tokens", InferenceRequest.PARAM_MAX_TOKENS)
        assertEquals("language", InferenceRequest.PARAM_LANGUAGE)
    }

    @Test
    fun `should create realistic chat request`() {
        // Given
        val sessionId = "chat-session-789"
        val chatText = "What is the capital of France?"
        val params = mapOf(
            InferenceRequest.PARAM_TEMPERATURE to 0.7f,
            InferenceRequest.PARAM_MAX_TOKENS to 100,
            "model" to "llama-3.2-1b"
        )
        
        // When
        val chatRequest = InferenceRequest(
            sessionId,
            mapOf(InferenceRequest.INPUT_TEXT to chatText),
            params
        )
        
        // Then
        assertEquals(sessionId, chatRequest.sessionId)
        assertEquals(chatText, chatRequest.inputs[InferenceRequest.INPUT_TEXT])
        assertEquals(0.7f, chatRequest.params[InferenceRequest.PARAM_TEMPERATURE])
        assertEquals(100, chatRequest.params[InferenceRequest.PARAM_MAX_TOKENS])
        assertEquals("llama-3.2-1b", chatRequest.params["model"])
    }

    @Test
    fun `should create realistic TTS request`() {
        // Given
        val sessionId = "tts-session-456"
        val text = "Hello, this is a text-to-speech test"
        val params = mapOf(
            "voice" to "alloy",
            "speed" to 1.0f,
            "format" to "mp3"
        )
        
        // When
        val ttsRequest = InferenceRequest(
            sessionId,
            mapOf(InferenceRequest.INPUT_TEXT to text),
            params
        )
        
        // Then
        assertEquals(sessionId, ttsRequest.sessionId)
        assertEquals(text, ttsRequest.inputs[InferenceRequest.INPUT_TEXT])
        assertEquals("alloy", ttsRequest.params["voice"])
        assertEquals(1.0f, ttsRequest.params["speed"])
        assertEquals("mp3", ttsRequest.params["format"])
    }

    @Test
    fun `should create realistic ASR request`() {
        // Given
        val sessionId = "asr-session-123"
        val audioData = ByteArray(1024) { it.toByte() } // Mock audio data
        val params = mapOf(
            InferenceRequest.PARAM_LANGUAGE to "en",
            "format" to "wav",
            "sample_rate" to 16000
        )
        
        // When
        val asrRequest = InferenceRequest(
            sessionId,
            mapOf(InferenceRequest.INPUT_AUDIO to audioData),
            params
        )
        
        // Then
        assertEquals(sessionId, asrRequest.sessionId)
        assertArrayEquals(audioData, asrRequest.inputs[InferenceRequest.INPUT_AUDIO] as ByteArray)
        assertEquals("en", asrRequest.params[InferenceRequest.PARAM_LANGUAGE])
        assertEquals("wav", asrRequest.params["format"])
        assertEquals(16000, asrRequest.params["sample_rate"])
    }

    @Test
    fun `should handle edge cases for session IDs`() {
        // Given
        val edgeCaseIds = listOf(
            "",
            "   ",
            "very-long-session-id-with-many-characters-and-numbers-123456789",
            "special-chars-!@#$%^&*()",
            "unicode-测试-🚀"
        )
        
        // When & Then
        edgeCaseIds.forEach { sessionId ->
            val request = InferenceRequest(sessionId, mapOf("test" to "value"))
            assertEquals("Session ID should be preserved as-is", sessionId, request.sessionId)
        }
    }

    @Test
    fun `should handle complex nested parameters`() {
        // Given
        val complexParams = mapOf(
            "simple_string" to "value",
            "number" to 42,
            "float" to 3.14f,
            "boolean" to true,
            "list" to listOf("a", "b", "c"),
            "map" to mapOf("nested" to "value")
        )
        
        // When
        val request = InferenceRequest(
            "complex-req",
            mapOf(InferenceRequest.INPUT_TEXT to "test"),
            complexParams
        )
        
        // Then
        assertEquals(6, request.params.size)
        assertEquals("value", request.params["simple_string"])
        assertEquals(42, request.params["number"])
        assertEquals(3.14f, request.params["float"])
        assertEquals(true, request.params["boolean"])
        assertEquals(listOf("a", "b", "c"), request.params["list"])
        assertEquals(mapOf("nested" to "value"), request.params["map"])
    }
}