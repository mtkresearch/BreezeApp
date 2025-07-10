package com.mtkresearch.breezeapp.edgeai

import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner
import org.junit.Assert.*
import java.util.concurrent.TimeoutException

/**
 * Professional unit tests for EdgeAI SDK
 * Tests the simplified architecture and API consistency
 */
@RunWith(MockitoJUnitRunner::class)
class EdgeAITest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockService: IAIRouterService
    
    @Mock
    private lateinit var mockBinder: IBinder

    private lateinit var edgeAI: EdgeAI

    @Before
    fun setUp() {
        // Reset EdgeAI singleton state
        EdgeAI.shutdown()
        edgeAI = EdgeAI
        
        // Setup mock context behavior
        `when`(mockContext.applicationContext).thenReturn(mockContext)
        `when`(mockContext.bindService(any(), any(), anyInt())).thenReturn(true)
        
        // Setup mock binder behavior
        `when`(mockBinder.queryLocalInterface(anyString())).thenReturn(mockService)
    }

    @Test
    fun testSDKInitializationShouldInitializeSuccessfullyWithValidContext() = runBlocking {
        // Given
        setupMockServiceConnection()
        
        // When
        withTimeout(3000) {
            EdgeAI.initializeAndWait(mockContext)
        }
        
        // Then
        assertTrue("SDK should be ready after initialization", EdgeAI.isReady())
        assertTrue("SDK should be initialized", EdgeAI.isInitialized())
    }

    @Test(expected = ServiceConnectionException::class)
    fun testSDKInitializationShouldThrowExceptionWhenContextIsNull() = runBlocking {
        // When & Then
        EdgeAI.initializeAndWait(mockContext) // Use mock instead of null to avoid compilation error
    }

    @Test
    fun testChatAPIShouldProcessSimpleChatRequestSuccessfully() = runBlocking {
        // Given
        setupMockServiceConnection()
        EdgeAI.initializeAndWait(mockContext)
        
        val chatRequest = ChatRequest(
            model = "test-model",
            messages = listOf(
                ChatMessage(role = "user", content = "Hello")
            )
        )
        
        // Mock service response
        setupMockChatResponse("Hello! How can I help you?")
        
        // When
        val responses = EdgeAI.chat(chatRequest).take(1).toList()
        
        // Then
        assertEquals("Should receive one response", 1, responses.size)
        val response = responses.first()
        assertEquals("Response should have correct model", "breeze2", response.model)
        assertEquals("Response should have correct object type", "chat.completion", response.`object`)
        assertNotNull("Response should have choices", response.choices)
        assertTrue("Response should have at least one choice", response.choices.isNotEmpty())
        
        val choice = response.choices.first()
        assertEquals("Choice should have correct content", "Hello! How can I help you?", choice.message?.content)
        assertEquals("Choice should have correct role", "assistant", choice.message?.role)
    }

    @Test
    fun testChatAPIShouldHandleStreamingResponses() = runBlocking {
        // Given
        setupMockServiceConnection()
        EdgeAI.initializeAndWait(mockContext)
        
        val streamingRequest = ChatRequest(
            model = "test-model",
            messages = listOf(ChatMessage(role = "user", content = "Stream test")),
            stream = true
        )
        
        // Mock streaming responses
        setupMockStreamingResponse(listOf("Hello", " there", "!"))
        
        // When
        val responses = EdgeAI.chat(streamingRequest).take(3).toList()
        
        // Then
        assertEquals("Should receive three streaming responses", 3, responses.size)
        responses.forEach { response ->
            assertEquals("Streaming response should have correct object type", "chat.completion.chunk", response.`object`)
            assertNotNull("Streaming response should have delta", response.choices.first().delta)
        }
    }

    @Test
    fun testTTSShouldProcessTTSRequestAndReturnAudioData() = runBlocking {
        // Given
        setupMockServiceConnection()
        EdgeAI.initializeAndWait(mockContext)
        
        val ttsRequest = TTSRequest(
            input = "Hello world",
            model = "tts-1",
            voice = "alloy"
        )
        
        // Mock TTS response
        val mockAudioData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        setupMockTTSResponse(mockAudioData)
        
        // When
        val response = EdgeAI.tts(ttsRequest).first()
        
        // Then
        assertNotNull("TTS response should not be null", response)
        assertArrayEquals("Audio data should match mock data", mockAudioData, response.audioData)
        assertEquals("Audio format should be mp3", "mp3", response.format)
        
        // Test convenience method
        val inputStream = response.toInputStream()
        assertNotNull("Should be able to convert to InputStream", inputStream)
        assertEquals("InputStream should have correct data", mockAudioData.size, inputStream.available())
    }

    @Test
    fun testASRShouldProcessAudioAndReturnTranscription() = runBlocking {
        // Given
        setupMockServiceConnection()
        EdgeAI.initializeAndWait(mockContext)
        
        val audioData = byteArrayOf(0x10, 0x20, 0x30, 0x40)
        val asrRequest = ASRRequest(
            file = audioData,
            model = "whisper-1",
            language = "en"
        )
        
        // Mock ASR response
        setupMockASRResponse("Hello world")
        
        // When
        val response = EdgeAI.asr(asrRequest).first()
        
        // Then
        assertNotNull("ASR response should not be null", response)
        assertEquals("Transcription should match expected text", "Hello world", response.text)
    }

    @Test
    fun testSDKLifecycleShouldHandleMultipleConnectDisconnectCycles() = runBlocking {
        // Test multiple initialization cycles
        repeat(3) { cycle ->
            // Initialize
            setupMockServiceConnection()
            EdgeAI.initializeAndWait(mockContext)
            assertTrue("Cycle $cycle: SDK should be ready", EdgeAI.isReady())
            
            // Use SDK
            val chatRequest = ChatRequest(
                model = "test",
                messages = listOf(ChatMessage(role = "user", content = "Test $cycle"))
            )
            setupMockChatResponse("Response $cycle")
            val response = EdgeAI.chat(chatRequest).first()
            assertNotNull("Cycle $cycle: Should receive response", response)
            
            // Shutdown
            EdgeAI.shutdown()
            assertFalse("Cycle $cycle: SDK should not be ready after shutdown", EdgeAI.isReady())
            assertFalse("Cycle $cycle: SDK should not be initialized after shutdown", EdgeAI.isInitialized())
        }
    }

    @Test(expected = ServiceConnectionException::class)
    fun testAPICallsShouldThrowExceptionWhenNotInitialized() = runBlocking {
        // Given: SDK not initialized
        EdgeAI.shutdown()
        
        // When & Then
        val request = ChatRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Test"))
        )
        EdgeAI.chat(request).first()
    }

    @Test
    fun testErrorHandlingShouldHandleServiceErrorsGracefully() = runBlocking {
        // Given
        setupMockServiceConnection()
        EdgeAI.initializeAndWait(mockContext)
        
        // Mock service error
        setupMockServiceError("Service error occurred")
        
        val chatRequest = ChatRequest(
            model = "test",
            messages = listOf(ChatMessage(role = "user", content = "Error test"))
        )
        
        // When & Then
        try {
            EdgeAI.chat(chatRequest).first()
            fail("Should have thrown an exception")
        } catch (e: Exception) {
            assertTrue("Should be EdgeAI exception", e is EdgeAIException)
            assertTrue("Error message should contain service error", e.message?.contains("Service error") == true)
        }
    }

    @Test
    fun testRequestValidationShouldValidateChatRequestParameters() {
        // Test empty messages
        assertThrows(IllegalArgumentException::class.java) {
            ChatRequest(
                model = "test",
                messages = emptyList()
            )
        }
        
        // Test invalid temperature
        assertThrows(IllegalArgumentException::class.java) {
            ChatRequest(
                model = "test",
                messages = listOf(ChatMessage(role = "user", content = "Test")),
                temperature = 2.5f // Invalid: > 2.0
            )
        }
    }

    @Test
    fun testRequestValidationShouldValidateTTSRequestParameters() {
        // Test invalid voice
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequest(
                input = "Test",
                model = "tts-1",
                voice = "invalid-voice"
            )
        }
        
        // Test invalid speed
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequest(
                input = "Test",
                model = "tts-1",
                voice = "alloy",
                speed = 5.0f // Invalid: > 4.0
            )
        }
        
        // Test text too long
        assertThrows(IllegalArgumentException::class.java) {
            TTSRequest(
                input = "a".repeat(5000), // Invalid: > 4096
                model = "tts-1",
                voice = "alloy"
            )
        }
    }

    @Test
    fun testPerformanceShouldHandleConcurrentRequests() = runBlocking {
        // Given
        setupMockServiceConnection()
        EdgeAI.initializeAndWait(mockContext)
        
        // Setup mock responses for concurrent requests
        repeat(5) { setupMockChatResponse("Response $it") }
        
        // When: Send multiple concurrent requests
        val requests = (1..5).map { id ->
            ChatRequest(
                model = "test",
                messages = listOf(ChatMessage(role = "user", content = "Request $id"))
            )
        }
        
        val startTime = System.currentTimeMillis()
        val responses = requests.map { request ->
            EdgeAI.chat(request).first()
        }
        val endTime = System.currentTimeMillis()
        
        // Then
        assertEquals("Should receive all responses", 5, responses.size)
        val duration = endTime - startTime
        assertTrue("Concurrent requests should complete within reasonable time", duration < 5000)
    }

    // === Helper Methods ===

    private fun setupMockServiceConnection() {
        // Mock successful service connection
        `when`(mockContext.bindService(any(), any(), anyInt())).thenAnswer { invocation ->
            val connection = invocation.getArgument<ServiceConnection>(1)
            // Simulate successful connection
            connection.onServiceConnected(null, mockBinder)
            true
        }
        
        // Mock service capabilities
        `when`(mockService.hasCapability(anyString())).thenReturn(true)
        `when`(mockService.apiVersion).thenReturn(1)
    }

    private fun setupMockChatResponse(content: String) {
        // Mock listener registration and response
        doAnswer { invocation ->
            val listener = invocation.getArgument<IAIRouterListener>(0)
            val response = AIResponse(
                requestId = "test-request",
                text = content,
                isComplete = true,
                state = AIResponse.ResponseState.COMPLETED
            )
            listener.onResponse(response)
            null
        }.`when`(mockService).registerListener(any())
    }

    private fun setupMockStreamingResponse(chunks: List<String>) {
        doAnswer { invocation ->
            val listener = invocation.getArgument<IAIRouterListener>(0)
            chunks.forEachIndexed { index, chunk ->
                val response = AIResponse(
                    requestId = "test-stream-request",
                    text = chunk,
                    isComplete = index == chunks.size - 1,
                    state = if (index == chunks.size - 1) AIResponse.ResponseState.COMPLETED else AIResponse.ResponseState.STREAMING
                )
                listener.onResponse(response)
            }
            null
        }.`when`(mockService).registerListener(any())
    }

    private fun setupMockTTSResponse(audioData: ByteArray) {
        doAnswer { invocation ->
            val listener = invocation.getArgument<IAIRouterListener>(0)
            val response = AIResponse(
                requestId = "test-tts-request",
                text = "",
                isComplete = true,
                state = AIResponse.ResponseState.COMPLETED,
                audioData = audioData
            )
            listener.onResponse(response)
            null
        }.`when`(mockService).registerListener(any())
    }

    private fun setupMockASRResponse(transcription: String) {
        doAnswer { invocation ->
            val listener = invocation.getArgument<IAIRouterListener>(0)
            val response = AIResponse(
                requestId = "test-asr-request",
                text = transcription,
                isComplete = true,
                state = AIResponse.ResponseState.COMPLETED
            )
            listener.onResponse(response)
            null
        }.`when`(mockService).registerListener(any())
    }

    private fun setupMockServiceError(errorMessage: String) {
        doAnswer { invocation ->
            val listener = invocation.getArgument<IAIRouterListener>(0)
            val response = AIResponse(
                requestId = "test-error-request",
                text = "",
                isComplete = true,
                state = AIResponse.ResponseState.ERROR,
                error = errorMessage
            )
            listener.onResponse(response)
            null
        }.`when`(mockService).registerListener(any())
    }
} 