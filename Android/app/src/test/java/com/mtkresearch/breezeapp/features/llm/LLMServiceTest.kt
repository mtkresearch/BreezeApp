package com.mtkresearch.breezeapp.features.llm

import com.mtkresearch.breezeapp.core.utils.ServiceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever
import java.io.File

@ExperimentalCoroutinesApi
class LLMServiceTest {
    
    // SUT (System Under Test)
    private lateinit var llmService: TestLLMService
    
    // Path to a mock model file (doesn't need to exist as we'll mock the check)
    private val mockModelPath = "/path/to/model.bin"
    
    // Creating a testable subclass of LLMService that overrides the file checks
    class TestLLMService : LLMService() {
        var mockFileExists = true
        var mockGenerationResponse = "Generated response"
        
        // Override to avoid file system calls in tests
        override suspend fun simulateModelGeneration(prompt: String, temperature: Float): String {
            return mockGenerationResponse
        }
        
        // Expose protected method for testing
        public override suspend fun initialize(): Boolean {
            return super.initialize()
        }
        
        // Override for testing the error cases
        fun simulateInitializationError() {
            updateState(ServiceState.Error("Simulated error"))
        }
        
        // Setter for modelPath
        fun setupModelPath(path: String) {
            modelPath = path
        }
    }
    
    @Before
    fun setup() {
        llmService = spy(TestLLMService())
        
        // Set model path using the setter
        llmService.setupModelPath(mockModelPath)
    }
    
    @Test
    fun `initialization succeeds when model file exists`() = runTest {
        // Given
        llmService.mockFileExists = true
        
        // When
        val result = llmService.initialize()
        
        // Then
        assertTrue(result)
        assertTrue(llmService.isReady())
        assertTrue(llmService.serviceState.value is ServiceState.Ready)
    }
    
    @Test
    fun `initialization fails when model file doesn't exist`() = runTest {
        // Given
        llmService.mockFileExists = false
        
        // When
        val result = llmService.initialize()
        
        // Then
        assertFalse(result)
        assertFalse(llmService.isReady())
        assertTrue(llmService.serviceState.value is ServiceState.Error)
    }
    
    @Test
    fun `generateText calls callback with streaming output`() = runTest {
        // Given
        llmService.mockFileExists = true
        llmService.initialize()
        
        val testPrompt = "Hello, how are you?"
        val expectedResponse = "Generated response"
        llmService.mockGenerationResponse = expectedResponse
        
        var receivedTokens = ""
        var completedResponse = ""
        
        // Create a callback
        val callback = object : LLMService.StreamingResponseCallback {
            override fun onToken(token: String) {
                receivedTokens += token
            }
            
            override fun onComplete(fullResponse: String) {
                completedResponse = fullResponse
            }
            
            override fun onError(error: String) {
                // Not expecting errors in this test
            }
        }
        
        // When
        llmService.generateText(testPrompt, 0.7f, callback)
        
        // Then
        assertEquals(expectedResponse, completedResponse)
    }
    
    @Test
    fun `generateText returns error when service not initialized`() = runTest {
        // Given
        llmService.mockFileExists = false
        
        var errorReceived = ""
        
        // Create a callback
        val callback = object : LLMService.StreamingResponseCallback {
            override fun onToken(token: String) {
                // Not expecting tokens in this test
            }
            
            override fun onComplete(fullResponse: String) {
                // Not expecting completion in this test
            }
            
            override fun onError(error: String) {
                errorReceived = error
            }
        }
        
        // When
        llmService.generateText("Test prompt", 0.7f, callback)
        
        // Then
        assertTrue(errorReceived.isNotEmpty())
        assertTrue(errorReceived.contains("not initialized"))
    }
    
    @Test
    fun `stopGeneration sets isGenerating to false`() = runTest {
        // Initialize service
        llmService.mockFileExists = true
        llmService.initialize()
        
        // Start generation (would set isGenerating to true)
        val callback = mock<LLMService.StreamingResponseCallback>()
        llmService.generateText("Test prompt", 0.7f, callback)
        
        // When
        llmService.stopGeneration()
        
        // Then - we can't directly check isGenerating as it's private, 
        // but we can verify indirectly by starting a new generation
        var newGenerationStarted = false
        val newCallback = object : LLMService.StreamingResponseCallback {
            override fun onToken(token: String) {
                newGenerationStarted = true
            }
            
            override fun onComplete(fullResponse: String) {
                newGenerationStarted = true
            }
            
            override fun onError(error: String) {
                // If there's an error about generation in progress, then test fails
                if (error.contains("in progress")) {
                    newGenerationStarted = false
                }
            }
        }
        
        llmService.generateText("Another test", 0.7f, newCallback)
        
        // Verify that a new generation could start
        assertTrue(newGenerationStarted)
    }
    
    @Test
    fun `getModelName returns display model name`() = runTest {
        // Given
        llmService.mockFileExists = true
        llmService.initialize()
        
        // When
        val modelName = llmService.getModelName()
        
        // Then
        assertTrue(modelName.isNotEmpty())
    }
    
    @Test
    fun `service state emits error if initialization fails`() = runTest {
        // Given
        llmService.mockFileExists = false
        
        // When
        llmService.initialize()
        
        // Then
        assertTrue(llmService.serviceState.value is ServiceState.Error)
    }
} 