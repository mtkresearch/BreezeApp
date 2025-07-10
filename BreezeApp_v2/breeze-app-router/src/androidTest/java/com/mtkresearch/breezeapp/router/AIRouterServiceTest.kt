package com.mtkresearch.breezeapp.router

import android.content.Intent
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.mtkresearch.breezeapp.edgeai.IAIRouterListener
import com.mtkresearch.breezeapp.edgeai.IAIRouterService
import com.mtkresearch.breezeapp.edgeai.AIResponse
import com.mtkresearch.breezeapp.edgeai.ChatRequest
import com.mtkresearch.breezeapp.edgeai.TTSRequest
import com.mtkresearch.breezeapp.edgeai.ASRRequest
import com.mtkresearch.breezeapp.edgeai.ChatMessage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.system.measureTimeMillis

/**
 * Professional integration tests for AIRouterService
 * Tests the complete AIDL interface and service lifecycle
 */
@RunWith(AndroidJUnit4::class)
class AIRouterServiceTest {

    @get:Rule
    val serviceTestRule = ServiceTestRule()

    private lateinit var service: IAIRouterService

    @Before
    fun setUp() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), AIRouterService::class.java)
        val binder = serviceTestRule.bindService(intent)
        service = IAIRouterService.Stub.asInterface(binder)
    }

    @Test
    fun serviceBinding_returnsValidBinder() {
        assertNotNull("Service should bind successfully", service)
        val apiVersion = service.apiVersion
        assertTrue("API version should be greater than 0", apiVersion > 0)
    }

    @Test
    fun serviceCapabilities_returnsExpectedCapabilities() {
        // Test core capabilities
        assertTrue("Should support binary_data", service.hasCapability("binary_data"))
        assertTrue("Should support streaming", service.hasCapability("streaming"))
        assertTrue("Should support audio_processing", service.hasCapability("audio_processing"))
        assertTrue("Should support mock_runners", service.hasCapability("mock_runners"))
        
        // Test unknown capability
        assertFalse("Should not support unknown capability", service.hasCapability("unknown_feature"))
    }

    @Test
    fun chatRequest_receivesResponse_andUnregistersCorrectly() {
        val responseLatch = CountDownLatch(1)
        val responseCounter = AtomicInteger(0)
        var receivedResponse: AIResponse? = null

        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                receivedResponse = response
                responseCounter.incrementAndGet()
                responseLatch.countDown()
            }
        }

        // 1. Register listener and send a chat request via the simplified AIDL interface
        service.registerListener(listener)
        val chatRequest = ChatRequest(
            messages = listOf(
                ChatMessage(role = "user", content = "Test prompt for integration test")
            ),
            model = "mock-llm",
            stream = false,
            temperature = 0.7f
        )
        val requestId = "test-chat-request-1"
        service.sendChatRequest(requestId, chatRequest)

        // 2. Verify the listener received the response
        assertTrue("Listener should receive a response within 5 seconds", responseLatch.await(5, TimeUnit.SECONDS))
        assertNotNull("Received response should not be null", receivedResponse)
        assertEquals("Response should have the correct request ID", requestId, receivedResponse?.requestId)
        assertEquals("Response counter should be 1", 1, responseCounter.get())
        assertTrue("Response should be marked as complete", receivedResponse?.isComplete == true)
        assertNotNull("Response should have text content", receivedResponse?.text)
        assertTrue("Response text should not be empty", !receivedResponse?.text.isNullOrEmpty())

        // 3. Unregister the listener
        service.unregisterListener(listener)

        // 4. Send another request and verify the listener is NOT called again
        val secondChatRequest = ChatRequest(
            messages = listOf(
                ChatMessage(role = "user", content = "Second prompt - should not trigger old listener")
            ),
            model = "mock-llm",
            stream = false
        )
        val secondRequestId = "test-chat-request-2"
        service.sendChatRequest(secondRequestId, secondChatRequest)

        // Give time for potential message processing
        SystemClock.sleep(2000)

        // The counter should remain 1, proving the listener was not called a second time
        assertEquals("Response counter should still be 1 after unregistering", 1, responseCounter.get())
    }

    @Test
    fun ttsRequest_receivesAudioResponse() {
        val responseLatch = CountDownLatch(1)
        var receivedResponse: AIResponse? = null

        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                receivedResponse = response
                responseLatch.countDown()
            }
        }

        service.registerListener(listener)
        
        val ttsRequest = TTSRequest(
            input = "Hello world, this is a TTS integration test.",
            model = "tts-1",
            voice = "alloy",
            responseFormat = "mp3",
            speed = 1.0f
        )
        
        val requestId = "test-tts-request-1"
        service.sendTTSRequest(requestId, ttsRequest)

        // Verify TTS response
        assertTrue("Should receive TTS response within 5 seconds", responseLatch.await(5, TimeUnit.SECONDS))
        assertNotNull("TTS response should not be null", receivedResponse)
        assertEquals("TTS response should have correct request ID", requestId, receivedResponse?.requestId)
        assertTrue("TTS response should be complete", receivedResponse?.isComplete == true)
        assertNotNull("TTS response should have audio data", receivedResponse?.audioData)
        assertTrue("Audio data should not be empty", receivedResponse?.audioData?.isNotEmpty() == true)

        service.unregisterListener(listener)
    }

    @Test
    fun asrRequest_receivesTranscriptionResponse() {
        val responseLatch = CountDownLatch(1)
        var receivedResponse: AIResponse? = null

        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                receivedResponse = response
                responseLatch.countDown()
            }
        }

        service.registerListener(listener)
        
        // Create mock audio data (simulate a short audio clip)
        val mockAudioData = ByteArray(1024) { (it % 256).toByte() }
        val asrRequest = ASRRequest(
            file = mockAudioData,
            model = "whisper-1",
            language = "en",
            responseFormat = "json",
            temperature = 0.3f
        )
        
        val requestId = "test-asr-request-1"
        service.sendASRRequest(requestId, asrRequest)

        // Verify ASR response
        assertTrue("Should receive ASR response within 5 seconds", responseLatch.await(5, TimeUnit.SECONDS))
        assertNotNull("ASR response should not be null", receivedResponse)
        assertEquals("ASR response should have correct request ID", requestId, receivedResponse?.requestId)
        assertTrue("ASR response should be complete", receivedResponse?.isComplete == true)
        assertNotNull("ASR response should have transcription text", receivedResponse?.text)
        assertTrue("Transcription should not be empty", !receivedResponse?.text.isNullOrEmpty())

        service.unregisterListener(listener)
    }

    @Test
    fun concurrentRequests_handledCorrectly() {
        val numRequests = 5
        val responseLatch = CountDownLatch(numRequests)
        val responses = ConcurrentLinkedQueue<AIResponse>()

        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                response?.let { responses.offer(it) }
                responseLatch.countDown()
            }
        }

        service.registerListener(listener)

        // Send multiple concurrent requests
        val startTime = System.currentTimeMillis()
        repeat(numRequests) { index ->
            val chatRequest = ChatRequest(
                messages = listOf(
                    ChatMessage(role = "user", content = "Concurrent request $index")
                ),
                model = "mock-llm",
                stream = false
            )
            service.sendChatRequest("concurrent-request-$index", chatRequest)
        }

        // Wait for all responses
        assertTrue(
            "Should receive all $numRequests responses within 10 seconds",
            responseLatch.await(10, TimeUnit.SECONDS)
        )
        val endTime = System.currentTimeMillis()

        // Verify all responses received
        assertEquals("Should receive exactly $numRequests responses", numRequests, responses.size)
        
        // Verify each response has unique request ID
        val requestIds = responses.map { it.requestId }.toSet()
        assertEquals("All request IDs should be unique", numRequests, requestIds.size)

        // Performance check
        val duration = endTime - startTime
        assertTrue("Concurrent requests should complete within reasonable time", duration < 8000)

        service.unregisterListener(listener)
    }

    @Test
    fun streamingChat_receivesMultipleResponses() {
        val responses = mutableListOf<AIResponse>()
        val finalResponseLatch = CountDownLatch(1)

        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                response?.let { 
                    responses.add(it)
                    if (it.isComplete) {
                        finalResponseLatch.countDown()
                    }
                }
            }
        }

        service.registerListener(listener)

        val streamingRequest = ChatRequest(
            messages = listOf(
                ChatMessage(role = "user", content = "Please provide a streaming response")
            ),
            model = "mock-llm",
            stream = true
        )
        
        service.sendChatRequest("streaming-test-1", streamingRequest)

        // Wait for streaming to complete
        assertTrue(
            "Should receive final response within 10 seconds",
            finalResponseLatch.await(10, TimeUnit.SECONDS)
        )

        // Verify streaming behavior
        assertTrue("Should receive multiple streaming responses", responses.size > 1)
        
        // Check that all but the last are partial
        responses.dropLast(1).forEach { response ->
            assertFalse("Non-final responses should not be complete", response.isComplete)
            assertEquals("Non-final responses should be in streaming state", 
                        AIResponse.ResponseState.STREAMING, response.state)
        }

        // Check that the last response is complete
        val finalResponse = responses.last()
        assertTrue("Final response should be complete", finalResponse.isComplete)
        assertEquals("Final response should be in completed state",
                    AIResponse.ResponseState.COMPLETED, finalResponse.state)

        service.unregisterListener(listener)
    }

    @Test
    fun errorHandling_invalidParameters() {
        val responseLatch = CountDownLatch(1)
        var receivedResponse: AIResponse? = null

        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                receivedResponse = response
                responseLatch.countDown()
            }
        }

        service.registerListener(listener)

        // Send request with potentially invalid parameters
        try {
            val invalidRequest = ChatRequest(
                messages = listOf(), // Empty messages might cause error
                model = "non-existent-model"
            )
            service.sendChatRequest("error-test-1", invalidRequest)

            // Wait for potential error response
            responseLatch.await(5, TimeUnit.SECONDS)

            // Service should handle errors gracefully (not crash)
            if (receivedResponse != null) {
                // If we get a response, it might be an error response
                if (receivedResponse?.state == AIResponse.ResponseState.ERROR) {
                    assertNotNull("Error response should have error message", receivedResponse?.error)
                }
            }
        } catch (e: RemoteException) {
            fail("Service should handle invalid parameters gracefully, not throw RemoteException")
        }

        service.unregisterListener(listener)
    }

    @Test
    fun memoryManagement_multipleListenerCycles() {
        // Test multiple register/unregister cycles to check for memory leaks
        repeat(10) { cycle ->
            val responseLatch = CountDownLatch(1)
            
            val listener = object : IAIRouterListener.Stub() {
                override fun onResponse(response: AIResponse?) {
                    responseLatch.countDown()
                }
            }

            // Register
            service.registerListener(listener)

            // Send request
            val request = ChatRequest(
                messages = listOf(
                    ChatMessage(role = "user", content = "Memory test cycle $cycle")
                ),
                model = "mock-llm"
            )
            service.sendChatRequest("memory-test-$cycle", request)

            // Wait for response
            assertTrue(
                "Cycle $cycle should receive response",
                responseLatch.await(3, TimeUnit.SECONDS)
            )

            // Unregister
            service.unregisterListener(listener)

            // Small delay between cycles
            SystemClock.sleep(100)
        }
        
        // If we reach here without OutOfMemoryError, the test passes
        assertTrue("Memory management test completed successfully", true)
    }

    @Test
    fun performanceBenchmark_responseTime() {
        val numRequests = 20
        val responseTimes = mutableListOf<Long>()
        val responseLatch = CountDownLatch(numRequests)

        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse?) {
                responseLatch.countDown()
            }
        }

        service.registerListener(listener)

        // Measure response times
        repeat(numRequests) { index ->
            val requestTime = measureTimeMillis {
                val request = ChatRequest(
                    messages = listOf(
                        ChatMessage(role = "user", content = "Performance test $index")
                    ),
                    model = "mock-llm"
                )
                service.sendChatRequest("perf-test-$index", request)
            }
            responseTimes.add(requestTime)
        }

        // Wait for all responses
        assertTrue(
            "All responses should be received within 15 seconds",
            responseLatch.await(15, TimeUnit.SECONDS)
        )

        // Performance analysis
        val avgResponseTime = responseTimes.average()
        val maxResponseTime = responseTimes.maxOrNull() ?: 0L

        // Log performance metrics
        println("Performance Metrics:")
        println("Average request send time: ${avgResponseTime}ms")
        println("Max request send time: ${maxResponseTime}ms")
        println("Total requests: $numRequests")

        // Basic performance assertions
        assertTrue("Average request time should be reasonable", avgResponseTime < 100) // 100ms threshold
        assertTrue("Max request time should be reasonable", maxResponseTime < 500) // 500ms threshold

        service.unregisterListener(listener)
    }
} 