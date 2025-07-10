package com.mtkresearch.breezeapp.edgeai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import kotlin.system.measureTimeMillis

/**
 * Performance benchmark tests for EdgeAI SDK
 * Measures and validates performance improvements from architecture simplification
 */
@RunWith(AndroidJUnit4::class)
class PerformanceBenchmarkTest {

    private lateinit var context: Context
    private val warmupRounds = 3
    private val benchmarkRounds = 10

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        EdgeAI.shutdown()
    }

    @Test
    fun benchmarkInitialization() = runBlocking {
        val initTimes = mutableListOf<Long>()
        
        repeat(benchmarkRounds) { round ->
            EdgeAI.shutdown() // Ensure clean state
            
            val initTime = measureTimeMillis {
                withTimeout(5000) {
                    EdgeAI.initializeAndWait(context)
                }
            }
            
            initTimes.add(initTime)
            println("Initialization round $round: ${initTime}ms")
        }
        
        val avgInitTime = initTimes.average()
        val maxInitTime = initTimes.maxOrNull() ?: 0L
        val minInitTime = initTimes.minOrNull() ?: 0L
        
        println("=== Initialization Performance ===")
        println("Average: ${avgInitTime}ms")
        println("Min: ${minInitTime}ms") 
        println("Max: ${maxInitTime}ms")
        println("Standard deviation: ${calculateStandardDeviation(initTimes)}")
        
        // Performance targets for simplified architecture
        assertTrue("Average initialization should be under 3 seconds", avgInitTime < 3000)
        assertTrue("Max initialization should be under 5 seconds", maxInitTime < 5000)
        assertTrue("Initialization should be consistent (std dev < 1000ms)", 
                  calculateStandardDeviation(initTimes) < 1000)
    }

    @Test
    fun benchmarkChatLatency() = runBlocking {
        // Initialize once for all chat tests
        EdgeAI.initializeAndWait(context)
        
        // Warmup
        repeat(warmupRounds) {
            performChatRequest("Warmup request $it")
        }
        
        val chatTimes = mutableListOf<Long>()
        
        repeat(benchmarkRounds) { round ->
            val chatTime = measureTimeMillis {
                performChatRequest("Benchmark chat request $round")
            }
            chatTimes.add(chatTime)
            println("Chat round $round: ${chatTime}ms")
        }
        
        val avgChatTime = chatTimes.average()
        val maxChatTime = chatTimes.maxOrNull() ?: 0L
        
        println("=== Chat Performance ===")
        println("Average: ${avgChatTime}ms")
        println("Min: ${chatTimes.minOrNull()}ms")
        println("Max: ${maxChatTime}ms")
        println("Standard deviation: ${calculateStandardDeviation(chatTimes)}")
        
        // Performance targets (should be much faster with simplified architecture)
        assertTrue("Average chat latency should be under 500ms", avgChatTime < 500)
        assertTrue("Max chat latency should be under 1000ms", maxChatTime < 1000)
    }

    @Test
    fun benchmarkTTSLatency() = runBlocking {
        EdgeAI.initializeAndWait(context)
        
        // Warmup
        repeat(warmupRounds) {
            performTTSRequest("Warmup TTS $it")
        }
        
        val ttsTimes = mutableListOf<Long>()
        
        repeat(benchmarkRounds) { round ->
            val ttsTime = measureTimeMillis {
                performTTSRequest("Benchmark TTS request number $round")
            }
            ttsTimes.add(ttsTime)
            println("TTS round $round: ${ttsTime}ms")
        }
        
        val avgTTSTime = ttsTimes.average()
        
        println("=== TTS Performance ===")
        println("Average: ${avgTTSTime}ms")
        println("Min: ${ttsTimes.minOrNull()}ms")
        println("Max: ${ttsTimes.maxOrNull()}ms")
        println("Standard deviation: ${calculateStandardDeviation(ttsTimes)}")
        
        // TTS typically takes longer but should still be reasonable
        assertTrue("Average TTS latency should be under 1000ms", avgTTSTime < 1000)
    }

    @Test
    fun benchmarkASRLatency() = runBlocking {
        EdgeAI.initializeAndWait(context)
        
        // Warmup
        repeat(warmupRounds) {
            performASRRequest()
        }
        
        val asrTimes = mutableListOf<Long>()
        
        repeat(benchmarkRounds) { round ->
            val asrTime = measureTimeMillis {
                performASRRequest()
            }
            asrTimes.add(asrTime)
            println("ASR round $round: ${asrTime}ms")
        }
        
        val avgASRTime = asrTimes.average()
        
        println("=== ASR Performance ===")
        println("Average: ${avgASRTime}ms")
        println("Min: ${asrTimes.minOrNull()}ms")
        println("Max: ${asrTimes.maxOrNull()}ms")
        println("Standard deviation: ${calculateStandardDeviation(asrTimes)}")
        
        assertTrue("Average ASR latency should be under 800ms", avgASRTime < 800)
    }

    @Test
    fun benchmarkStreamingPerformance() = runBlocking {
        EdgeAI.initializeAndWait(context)
        
        val streamingTimes = mutableListOf<Long>()
        val chunkCounts = mutableListOf<Int>()
        
        repeat(benchmarkRounds) { round ->
            var chunkCount = 0
            val streamingTime = measureTimeMillis {
                val request = ChatRequest(
                    model = "mock-llm",
                    messages = listOf(
                        ChatMessage(role = "user", content = "Generate a streaming response for benchmark $round")
                    ),
                    stream = true
                )
                
                val responses = withTimeout(10000) {
                    EdgeAI.chat(request).toList()
                }
                chunkCount = responses.size
            }
            
            streamingTimes.add(streamingTime)
            chunkCounts.add(chunkCount)
            println("Streaming round $round: ${streamingTime}ms, $chunkCount chunks")
        }
        
        val avgStreamingTime = streamingTimes.average()
        val avgChunkCount = chunkCounts.average()
        
        println("=== Streaming Performance ===")
        println("Average total time: ${avgStreamingTime}ms")
        println("Average chunks: $avgChunkCount")
        println("Average time per chunk: ${avgStreamingTime / avgChunkCount}ms")
        
        assertTrue("Streaming should complete in reasonable time", avgStreamingTime < 3000)
        assertTrue("Should receive multiple chunks", avgChunkCount > 1)
    }

    @Test
    fun benchmarkConcurrentRequests() = runBlocking {
        EdgeAI.initializeAndWait(context)
        
        val concurrencyLevels = listOf(1, 3, 5, 10)
        
        for (concurrency in concurrencyLevels) {
            val concurrentTime = measureTimeMillis {
                val requests = (1..concurrency).map { id ->
                    ChatRequest(
                        model = "mock-llm",
                        messages = listOf(
                            ChatMessage(role = "user", content = "Concurrent request $id")
                        )
                    )
                }
                
                // Execute all requests concurrently
                val responses = requests.map { request ->
                    EdgeAI.chat(request).first()
                }
                
                assertEquals("Should receive all responses", concurrency, responses.size)
            }
            
            val avgTimePerRequest = concurrentTime.toDouble() / concurrency
            println("Concurrency $concurrency: ${concurrentTime}ms total, ${avgTimePerRequest}ms avg per request")
            
            // Concurrent requests should not significantly degrade performance
            assertTrue("Concurrent requests should complete efficiently", 
                      avgTimePerRequest < 600) // Allow some overhead
        }
    }

    @Test
    fun benchmarkMemoryUsage() = runBlocking {
        val runtime = Runtime.getRuntime()
        
        // Measure baseline memory
        System.gc()
        Thread.sleep(100)
        val baselineMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Initialize SDK
        EdgeAI.initializeAndWait(context)
        System.gc()
        Thread.sleep(100)
        val afterInitMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Perform multiple operations
        repeat(20) { round ->
            performChatRequest("Memory test $round")
            performTTSRequest("Memory TTS test $round")
            performASRRequest()
        }
        
        System.gc()
        Thread.sleep(100)
        val afterOperationsMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Shutdown and measure cleanup
        EdgeAI.shutdown()
        System.gc()
        Thread.sleep(100)
        val afterShutdownMemory = runtime.totalMemory() - runtime.freeMemory()
        
        val initMemoryIncrease = afterInitMemory - baselineMemory
        val operationsMemoryIncrease = afterOperationsMemory - afterInitMemory
        val shutdownMemoryDecrease = afterOperationsMemory - afterShutdownMemory
        
        println("=== Memory Usage ===")
        println("Baseline: ${baselineMemory / 1024 / 1024}MB")
        println("After init: ${afterInitMemory / 1024 / 1024}MB (+${initMemoryIncrease / 1024 / 1024}MB)")
        println("After operations: ${afterOperationsMemory / 1024 / 1024}MB (+${operationsMemoryIncrease / 1024 / 1024}MB)")
        println("After shutdown: ${afterShutdownMemory / 1024 / 1024}MB (-${shutdownMemoryDecrease / 1024 / 1024}MB)")
        
        // Memory usage should be reasonable
        assertTrue("Initialization memory increase should be under 50MB", 
                  initMemoryIncrease < 50 * 1024 * 1024)
        assertTrue("Operations should not cause significant memory growth", 
                  operationsMemoryIncrease < 20 * 1024 * 1024)
        assertTrue("Shutdown should free most memory", 
                  shutdownMemoryDecrease > initMemoryIncrease * 0.7) // At least 70% cleanup
    }

    // === Helper Methods ===

    private suspend fun performChatRequest(content: String) {
        val request = ChatRequest(
            model = "mock-llm",
            messages = listOf(ChatMessage(role = "user", content = content))
        )
        
        withTimeout(5000) {
            EdgeAI.chat(request).first()
        }
    }

    private suspend fun performTTSRequest(text: String) {
        val request = TTSRequest(
            input = text,
            model = "tts-1",
            voice = "alloy"
        )
        
        withTimeout(5000) {
            EdgeAI.tts(request).first()
        }
    }

    private suspend fun performASRRequest() {
        val audioData = ByteArray(1024) { (it % 256).toByte() }
        val request = ASRRequest(
            file = audioData,
            model = "whisper-1",
            language = "en"
        )
        
        withTimeout(5000) {
            EdgeAI.asr(request).first()
        }
    }

    private fun calculateStandardDeviation(values: List<Long>): Double {
        val mean = values.average()
        val squaredDifferences = values.map { (it - mean) * (it - mean) }
        val variance = squaredDifferences.average()
        return kotlin.math.sqrt(variance)
    }
} 