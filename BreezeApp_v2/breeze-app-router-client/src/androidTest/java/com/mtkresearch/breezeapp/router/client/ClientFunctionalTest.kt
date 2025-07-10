package com.mtkresearch.breezeapp.router.client

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.ChatRequest
import com.mtkresearch.breezeapp.edgeai.TTSRequest
import com.mtkresearch.breezeapp.edgeai.ASRRequest
import com.mtkresearch.breezeapp.edgeai.ChatMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * End-to-end functional tests for the client application
 * Tests the complete flow from client to service and back
 */
@RunWith(AndroidJUnit4::class)
class ClientFunctionalTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        EdgeAI.shutdown()
    }

    @Test
    fun endToEndChatFlow() = runBlocking {
        // Test complete chat flow
        withTimeout(10000) {
            EdgeAI.initializeAndWait(context)
        }
        assertTrue("EdgeAI should be ready", EdgeAI.isReady())

        // Test simple chat
        val chatRequest = ChatRequest(
            model = "mock-llm",
            messages = listOf(
                ChatMessage(role = "user", content = "Hello, this is an end-to-end test")
            ),
            temperature = 0.7f
        )

        val response = withTimeout(10000) {
            EdgeAI.chat(chatRequest).first()
        }

        assertNotNull("Chat response should not be null", response)
        assertEquals("Response should have correct model", "breeze2", response.model)
        assertNotNull("Response should have choices", response.choices)
        assertTrue("Response should have at least one choice", response.choices.isNotEmpty())
        
        val choice = response.choices.first()
        assertNotNull("Choice should have message", choice.message)
        assertNotNull("Message should have content", choice.message?.content)
        assertTrue("Content should not be empty", !choice.message?.content.isNullOrEmpty())
    }

    @Test
    fun endToEndStreamingChatFlow() = runBlocking {
        EdgeAI.initializeAndWait(context)

        val streamingRequest = ChatRequest(
            model = "mock-llm",
            messages = listOf(
                ChatMessage(role = "user", content = "Generate a streaming response for testing")
            ),
            stream = true
        )

        val responses = withTimeout(15000) {
            EdgeAI.chat(streamingRequest).take(5).toList()
        }

        assertTrue("Should receive multiple streaming responses", responses.size > 1)
        
        responses.forEach { response ->
            assertEquals("Streaming response should have correct object type", 
                        "chat.completion.chunk", response.`object`)
            assertNotNull("Streaming response should have choices", response.choices)
            assertTrue("Streaming response should have at least one choice", response.choices.isNotEmpty())
        }
    }

    @Test
    fun endToEndTTSFlow() = runBlocking {
        EdgeAI.initializeAndWait(context)

        val ttsRequest = TTSRequest(
            input = "This is an end-to-end TTS test",
            model = "tts-1",
            voice = "alloy",
            responseFormat = "mp3"
        )

        val response = withTimeout(10000) {
            EdgeAI.tts(ttsRequest).first()
        }

        assertNotNull("TTS response should not be null", response)
        assertNotNull("TTS response should have audio data", response.audioData)
        assertTrue("Audio data should not be empty", response.audioData.isNotEmpty())
        assertEquals("Audio format should be mp3", "mp3", response.format)

        // Test convenience method
        val inputStream = response.toInputStream()
        assertTrue("InputStream should have available data", inputStream.available() > 0)
    }

    @Test
    fun endToEndASRFlow() = runBlocking {
        EdgeAI.initializeAndWait(context)

        // Create mock audio data
        val audioData = ByteArray(2048) { (it % 256).toByte() }
        val asrRequest = ASRRequest(
            file = audioData,
            model = "whisper-1",
            language = "en",
            responseFormat = "json"
        )

        val response = withTimeout(10000) {
            EdgeAI.asr(asrRequest).first()
        }

        assertNotNull("ASR response should not be null", response)
        assertNotNull("ASR response should have text", response.text)
        assertTrue("Transcription should not be empty", response.text.isNotEmpty())
    }

    @Test
    fun clientLifecycleManagement() = runBlocking {
        // Test multiple connect/disconnect cycles
        repeat(3) { cycle ->
            // Initialize
            withTimeout(10000) {
                EdgeAI.initializeAndWait(context)
            }
            assertTrue("Cycle $cycle: Should be ready after init", EdgeAI.isReady())

            // Use the service
            val chatRequest = ChatRequest(
                model = "mock-llm",
                messages = listOf(
                    ChatMessage(role = "user", content = "Lifecycle test cycle $cycle")
                )
            )
            
            val response = withTimeout(5000) {
                EdgeAI.chat(chatRequest).first()
            }
            assertNotNull("Cycle $cycle: Should receive response", response)

            // Shutdown
            EdgeAI.shutdown()
            assertFalse("Cycle $cycle: Should not be ready after shutdown", EdgeAI.isReady())
            assertFalse("Cycle $cycle: Should not be initialized after shutdown", EdgeAI.isInitialized())
        }
    }

    @Test
    fun errorRecovery() = runBlocking {
        EdgeAI.initializeAndWait(context)

        // Test error handling with invalid request (if service supports validation)
        try {
            val invalidRequest = ChatRequest(
                model = "non-existent-model",
                messages = listOf(
                    ChatMessage(role = "user", content = "This should cause an error")
                )
            )
            
            // This might succeed with mock service, but should not crash
            val response = withTimeout(5000) {
                EdgeAI.chat(invalidRequest).first()
            }
            
            // If we get here, the service handled the invalid request gracefully
            assertNotNull("Service should handle invalid requests gracefully", response)
            
        } catch (e: Exception) {
            // Expected for some types of validation errors
            assertTrue("Exception should be an EdgeAI exception", 
                      e.message?.contains("EdgeAI") == true || 
                      e::class.simpleName?.contains("EdgeAI") == true)
        }

        // Verify service is still functional after error
        val validRequest = ChatRequest(
            model = "mock-llm",
            messages = listOf(
                ChatMessage(role = "user", content = "Recovery test")
            )
        )
        
        val recoveryResponse = withTimeout(5000) {
            EdgeAI.chat(validRequest).first()
        }
        assertNotNull("Service should work after error", recoveryResponse)
    }

    @Test
    fun performanceUnderLoad() = runBlocking {
        EdgeAI.initializeAndWait(context)

        val numRequests = 10
        val startTime = System.currentTimeMillis()

        // Send multiple requests in sequence
        repeat(numRequests) { index ->
            val request = ChatRequest(
                model = "mock-llm",
                messages = listOf(
                    ChatMessage(role = "user", content = "Load test request $index")
                )
            )
            
            val response = withTimeout(10000) {
                EdgeAI.chat(request).first()
            }
            assertNotNull("Request $index should receive response", response)
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val avgTimePerRequest = totalTime.toDouble() / numRequests

        println("Performance under load:")
        println("Total time: ${totalTime}ms")
        println("Average per request: ${avgTimePerRequest}ms")
        println("Requests per second: ${1000.0 / avgTimePerRequest}")

        // Performance assertions
        assertTrue("Load test should complete in reasonable time", totalTime < 30000) // 30 seconds max
        assertTrue("Average request time should be reasonable", avgTimePerRequest < 3000) // 3 seconds max per request
    }

    @Test
    fun serviceResourceCleanup() = runBlocking {
        // Test that the service properly cleans up resources when client disconnects
        EdgeAI.initializeAndWait(context)

        // Send a few requests to activate the service
        repeat(3) { index ->
            val request = ChatRequest(
                model = "mock-llm",
                messages = listOf(
                    ChatMessage(role = "user", content = "Cleanup test $index")
                )
            )
            EdgeAI.chat(request).first()
        }

        // Shutdown and verify clean disconnection
        EdgeAI.shutdown()
        
        // Wait a moment for cleanup
        kotlinx.coroutines.delay(2000)

        // Try to use service after shutdown (should fail gracefully)
        assertFalse("Should not be ready after shutdown", EdgeAI.isReady())
        assertFalse("Should not be initialized after shutdown", EdgeAI.isInitialized())

        // Reinitialize should work
        withTimeout(10000) {
            EdgeAI.initializeAndWait(context)
        }
        assertTrue("Should be ready after reinitialization", EdgeAI.isReady())
    }
} 