package com.mtkresearch.breezeapp.router.runner.mock

import android.util.Log
import com.mtkresearch.breezeapp.router.data.runner.MockLLMRunner
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import com.mtkresearch.breezeapp.router.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.router.domain.model.InferenceResult
import com.mtkresearch.breezeapp.router.domain.model.ModelConfig
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MockLLMRunnerTest {

    private lateinit var runner: MockLLMRunner
    private val defaultConfig = ModelConfig(
        modelName = "mock-llm",
        modelPath = "/dev/null"
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        runner = MockLLMRunner()
        runner.load(defaultConfig)
    }

    @After
    fun tearDown() {
        runner.unload()
        unmockkStatic(Log::class)
    }

    @Test
    fun `getRunnerInfo returns correct information`() {
        val info = runner.getRunnerInfo()
        assertEquals("MockLLMRunner", info.name)
        assertEquals("1.0.0", info.version)
        assertTrue(info.capabilities.contains(CapabilityType.LLM))
        assertTrue(info.isMock)
    }

    @Test
    fun `load returns true and sets model as loaded`() {
        assertTrue(runner.load(defaultConfig))
        assertTrue(runner.isLoaded())
    }

    @Test
    fun `unload sets model as not loaded`() {
        runner.unload()
        assertFalse(runner.isLoaded())
    }

    @Test
    fun `run returns a non-empty result`() = runTest {
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello, world!")
        )
        val result = runner.run(request)
        assertNotNull(result)
        assertNull(result.error)
        val outputText = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
        assertFalse(outputText.isNullOrEmpty())
    }

    @Test
    fun `runAsFlow returns a multi-part flow`() = runTest {
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "Tell me a story")
        )
        val results = runner.runAsFlow(request).toList()

        assertTrue("Flow should have multiple parts", results.size > 1)

        results.forEachIndexed { index, result ->
            assertNotNull(result)
            assertNull(result.error)
            val outputText = result.outputs[InferenceResult.OUTPUT_TEXT] as? String
            assertFalse(outputText.isNullOrEmpty())

            val isFinalResult = index == results.size - 1
            assertEquals("Final result 'partial' flag should be false", !isFinalResult, result.partial)
        }
    }

    @Test
    fun `runAsFlow ends with a non-partial result`() = runTest {
        val request = InferenceRequest(
            sessionId = "test-session",
            inputs = mapOf(InferenceRequest.INPUT_TEXT to "test")
        )
        val lastResult = runner.runAsFlow(request).last()
        assertFalse(lastResult.partial)
    }
} 