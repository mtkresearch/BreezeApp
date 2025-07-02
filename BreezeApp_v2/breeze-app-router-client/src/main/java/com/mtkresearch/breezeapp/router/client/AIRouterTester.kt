package com.mtkresearch.breezeapp.router.client

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterService
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Utility class for testing AI Router Service integration.
 * 
 * This class provides methods to test various aspects of the AI Router Service:
 * - Connection establishment
 * - Service initialization
 * - API version verification
 * - Capability checking
 * - Text generation
 * - Image analysis
 * - Speech recognition
 * - Text-to-speech
 * - Content moderation
 * 
 * Usage:
 * ```kotlin
 * val tester = AIRouterTester(context)
 * tester.runFullTest { results ->
 *     // Process test results
 * }
 * ```
 */
class AIRouterTester(private val context: Context) {

    private var routerService: IAIRouterService? = null
    private val isConnected = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private val testScope = CoroutineScope(Dispatchers.Default)
    private val testResults = mutableListOf<TestResult>()
    
    /**
     * Represents the result of a single test.
     */
    data class TestResult(
        val testName: String,
        val success: Boolean,
        val message: String,
        val durationMs: Long = 0
    )
    
    /**
     * Connection to the AI Router Service.
     */
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            routerService = IAIRouterService.Stub.asInterface(service)
            routerService?.registerListener(responseListener)
            isConnected.set(true)
            Log.d(TAG, "✅ Connected to AI Router Service: ${name?.className}")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            routerService = null
            isConnected.set(false)
            isInitialized.set(false)
            Log.d(TAG, "❌ Disconnected from AI Router Service")
        }
    }
    
    /**
     * Listener for receiving responses from the AI Router Service.
     */
    private val responseListener = object : IAIRouterListener.Stub() {
        override fun onResponse(response: AIResponse) {
            Log.d(TAG, "📩 Response received: ${response.text.take(50)}${if (response.text.length > 50) "..." else ""}")
            lastResponse.set(response)
            responseLatch.countDown()
        }
    }
    
    // For synchronizing async responses
    private var responseLatch = CountDownLatch(1)
    private val lastResponse = AtomicReference<AIResponse>()
    
    companion object {
        private const val TAG = "AIRouterTester"
        private const val RESPONSE_TIMEOUT_MS = 10000L
    }
    
    /**
     * Runs a comprehensive test of all AI Router Service functionality.
     * 
     * @param callback Function to receive test results
     */
    fun runFullTest(callback: (List<TestResult>) -> Unit) {
        testScope.launch {
            testResults.clear()
            
            // Connection tests
            val connectionSuccess = testConnection()
            if (!connectionSuccess) {
                callback(testResults)
                return@launch
            }
            
            // Initialization test
            val initSuccess = testInitialization()
            if (!initSuccess) {
                disconnectService()
                callback(testResults)
                return@launch
            }
            
            // API and capability tests
            testApiVersion()
            testCapabilities()
            
            // Functional tests
            testTextGeneration()
            testImageAnalysis()
            testSpeechRecognition()
            testTextToSpeech()
            testContentModeration()
            
            // Cleanup
            disconnectService()
            
            // Return results
            withContext(Dispatchers.Main) {
                callback(testResults)
            }
        }
    }
    
    /**
     * Tests connection to the AI Router Service.
     * 
     * @return True if connection was successful
     */
    fun testConnection(): Boolean {
        val startTime = System.currentTimeMillis()
        
        try {
            // First try debug version
            var intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService").apply {
                setPackage("com.mtkresearch.breezeapp.router.debug")
            }
            
            var success = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            
            if (!success) {
                // Try production version
                intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService").apply {
                    setPackage("com.mtkresearch.breezeapp.router")
                }
                success = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            }
            
            // Wait for connection to complete
            var attempts = 0
            while (!isConnected.get() && attempts < 10) {
                Thread.sleep(300)
                attempts++
            }
            
            val duration = System.currentTimeMillis() - startTime
            val result = isConnected.get()
            
            val message = if (result) {
                "Successfully connected to AI Router Service in ${duration}ms"
            } else {
                "Failed to connect to AI Router Service after ${duration}ms"
            }
            
            testResults.add(TestResult("Connection", result, message, duration))
            return result
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Connection", false, "Exception: ${e.message}", duration))
            return false
        }
    }
    
    /**
     * Tests initialization of the AI Router Service.
     * 
     * @return True if initialization was successful
     */
    fun testInitialization(): Boolean {
        if (!isConnected.get()) return false
        
        val startTime = System.currentTimeMillis()
        
        try {
            val config = Configuration(
                apiVersion = 1,
                logLevel = 3,
                timeoutMs = 30000L,
                maxTokens = 1024,
                temperature = 0.7f,
                languagePreference = "en-US",
                preferredRuntime = Configuration.RuntimeBackend.AUTO,
                runnerConfigurations = mapOf(
                    Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.MOCK,
                    Configuration.AITaskType.IMAGE_ANALYSIS to Configuration.RunnerType.MOCK,
                    Configuration.AITaskType.SPEECH_RECOGNITION to Configuration.RunnerType.MOCK,
                    Configuration.AITaskType.SPEECH_SYNTHESIS to Configuration.RunnerType.MOCK,
                    Configuration.AITaskType.CONTENT_MODERATION to Configuration.RunnerType.MOCK
                )
            )
            
            routerService?.initialize(config)
            isInitialized.set(true)
            
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Initialization", true, "Service initialized in ${duration}ms", duration))
            return true
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Initialization", false, "Exception: ${e.message}", duration))
            return false
        }
    }
    
    /**
     * Tests getting the API version from the service.
     */
    fun testApiVersion() {
        if (!isInitialized.get()) return
        
        val startTime = System.currentTimeMillis()
        
        try {
            val apiVersion = routerService?.apiVersion ?: -1
            val success = apiVersion > 0
            val duration = System.currentTimeMillis() - startTime
            
            testResults.add(TestResult(
                "API Version", 
                success, 
                if (success) "API Version: $apiVersion" else "Failed to get API version",
                duration
            ))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("API Version", false, "Exception: ${e.message}", duration))
        }
    }
    
    /**
     * Tests checking capabilities of the service.
     */
    fun testCapabilities() {
        if (!isInitialized.get()) return
        
        val startTime = System.currentTimeMillis()
        val capabilities = mapOf(
            "text_generation" to false,
            "image_processing" to false,
            "audio_processing" to false,
            "streaming" to false
        ).toMutableMap()
        
        try {
            capabilities.keys.forEach { capability ->
                capabilities[capability] = routerService?.hasCapability(capability) ?: false
            }
            
            val success = capabilities.values.any { it }
            val duration = System.currentTimeMillis() - startTime
            
            val message = capabilities.entries.joinToString("\n") { (cap, supported) ->
                "$cap: ${if (supported) "✓" else "✗"}"
            }
            
            testResults.add(TestResult("Capabilities", success, message, duration))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Capabilities", false, "Exception: ${e.message}", duration))
        }
    }
    
    /**
     * Tests text generation functionality.
     */
    fun testTextGeneration() {
        if (!isInitialized.get()) return
        
        val startTime = System.currentTimeMillis()
        resetResponseWait()
        
        try {
            val request = AIRequest(
                id = UUID.randomUUID().toString(),
                text = "Hello, world!",
                sessionId = "test-session",
                timestamp = System.currentTimeMillis(),
                options = mapOf(
                    "request_type" to "text_generation",
                    "model_name" to "mock-llm"
                )
            )
            
            routerService?.sendMessage(request)
            
            val responseReceived = responseLatch.await(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val response = lastResponse.get()
            
            val success = responseReceived && response != null
            val duration = System.currentTimeMillis() - startTime
            
            val message = if (success) {
                "Response: ${response?.text?.take(50)}${if ((response?.text?.length ?: 0) > 50) "..." else ""}"
            } else {
                "No response received within timeout"
            }
            
            testResults.add(TestResult("Text Generation", success, message, duration))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Text Generation", false, "Exception: ${e.message}", duration))
        }
    }
    
    /**
     * Tests image analysis functionality.
     */
    fun testImageAnalysis() {
        if (!isInitialized.get()) return
        
        val startTime = System.currentTimeMillis()
        resetResponseWait()
        
        try {
            // Create a small dummy image for testing
            val dummyImageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
            
            val request = AIRequest(
                id = UUID.randomUUID().toString(),
                text = "What's in this image?",
                sessionId = "test-session",
                timestamp = System.currentTimeMillis(),
                options = mapOf(
                    "request_type" to "image_analysis",
                    "model_name" to "mock-vlm",
                    "image_data" to dummyImageBase64
                )
            )
            
            routerService?.sendMessage(request)
            
            val responseReceived = responseLatch.await(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val response = lastResponse.get()
            
            val success = responseReceived && response != null
            val duration = System.currentTimeMillis() - startTime
            
            val message = if (success) {
                "Response: ${response?.text?.take(50)}${if ((response?.text?.length ?: 0) > 50) "..." else ""}"
            } else {
                "No response received within timeout"
            }
            
            testResults.add(TestResult("Image Analysis", success, message, duration))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Image Analysis", false, "Exception: ${e.message}", duration))
        }
    }
    
    /**
     * Tests speech recognition functionality.
     */
    fun testSpeechRecognition() {
        if (!isInitialized.get()) return
        
        val startTime = System.currentTimeMillis()
        resetResponseWait()
        
        try {
            // Create dummy audio data for testing
            val dummyAudioBase64 = "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA="
            
            val request = AIRequest(
                id = UUID.randomUUID().toString(),
                text = "Transcribe this audio",
                sessionId = "test-session",
                timestamp = System.currentTimeMillis(),
                options = mapOf(
                    "request_type" to "speech_recognition",
                    "model_name" to "mock-asr",
                    "audio_data" to dummyAudioBase64,
                    "audio_format" to "wav"
                )
            )
            
            routerService?.sendMessage(request)
            
            val responseReceived = responseLatch.await(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val response = lastResponse.get()
            
            val success = responseReceived && response != null
            val duration = System.currentTimeMillis() - startTime
            
            val message = if (success) {
                "Response: ${response?.text}"
            } else {
                "No response received within timeout"
            }
            
            testResults.add(TestResult("Speech Recognition", success, message, duration))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Speech Recognition", false, "Exception: ${e.message}", duration))
        }
    }
    
    /**
     * Tests text-to-speech functionality.
     */
    fun testTextToSpeech() {
        if (!isInitialized.get()) return
        
        val startTime = System.currentTimeMillis()
        resetResponseWait()
        
        try {
            val request = AIRequest(
                id = UUID.randomUUID().toString(),
                text = "This is a test for text-to-speech synthesis.",
                sessionId = "test-session",
                timestamp = System.currentTimeMillis(),
                options = mapOf(
                    "request_type" to "speech_synthesis",
                    "model_name" to "mock-tts"
                )
            )
            
            routerService?.sendMessage(request)
            
            val responseReceived = responseLatch.await(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val response = lastResponse.get()
            
            val success = responseReceived && response != null
            val duration = System.currentTimeMillis() - startTime
            
            // TTS response should contain binary audio data
            val hasBinaryData = response?.binaryAttachments?.isNotEmpty() ?: false
            
            val message = if (success) {
                if (hasBinaryData) {
                    "Response contains audio data"
                } else {
                    "Response received but no audio data found"
                }
            } else {
                "No response received within timeout"
            }
            
            testResults.add(TestResult("Text-to-Speech", success && hasBinaryData, message, duration))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Text-to-Speech", false, "Exception: ${e.message}", duration))
        }
    }
    
    /**
     * Tests content moderation functionality.
     */
    fun testContentModeration() {
        if (!isInitialized.get()) return
        
        val startTime = System.currentTimeMillis()
        resetResponseWait()
        
        try {
            val request = AIRequest(
                id = UUID.randomUUID().toString(),
                text = "This is a test for content moderation.",
                sessionId = "test-session",
                timestamp = System.currentTimeMillis(),
                options = mapOf(
                    "request_type" to "content_moderation",
                    "model_name" to "mock-guardrail"
                )
            )
            
            routerService?.sendMessage(request)
            
            val responseReceived = responseLatch.await(RESPONSE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            val response = lastResponse.get()
            
            val success = responseReceived && response != null
            val duration = System.currentTimeMillis() - startTime
            
            val message = if (success) {
                "Response: ${response?.text}"
            } else {
                "No response received within timeout"
            }
            
            testResults.add(TestResult("Content Moderation", success, message, duration))
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            testResults.add(TestResult("Content Moderation", false, "Exception: ${e.message}", duration))
        }
    }
    
    /**
     * Checks if the AI Router Service is healthy.
     * 
     * @return True if the service is connected, initialized, and responding
     */
    fun checkRouterHealth(): Boolean {
        if (!isConnected.get() || !isInitialized.get() || routerService == null) return false
        
        try {
            // Basic API version check should always work if service is healthy
            val apiVersion = routerService?.apiVersion ?: -1
            if (apiVersion <= 0) return false
            
            // Check essential capabilities
            val hasTextCapability = routerService?.hasCapability("text_generation") ?: false
            
            return hasTextCapability
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            return false
        }
    }
    
    /**
     * Resets the response wait mechanism.
     */
    private fun resetResponseWait() {
        responseLatch = CountDownLatch(1)
        lastResponse.set(null)
    }
    
    /**
     * Disconnects from the AI Router Service.
     */
    fun disconnectService() {
        if (isConnected.get()) {
            try {
                routerService?.unregisterListener(responseListener)
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting from service", e)
            }
            isConnected.set(false)
            isInitialized.set(false)
            routerService = null
        }
    }
} 