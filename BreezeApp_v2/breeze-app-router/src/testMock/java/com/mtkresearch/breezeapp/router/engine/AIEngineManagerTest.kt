package com.mtkresearch.breezeapp.router.engine

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.router.domain.model.InferenceResult
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.FlowStreamingRunner
import com.mtkresearch.breezeapp.router.domain.model.ModelConfig
import com.mtkresearch.breezeapp.router.domain.interfaces.RunnerInfo
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry.RunnerRegistration
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AIEngineManagerTest {

    private lateinit var engineManager: AIEngineManager
    private lateinit var runnerRegistry: RunnerRegistry
    private lateinit var mockLLMRunner: FlowStreamingRunner
    private lateinit var mockASRRunner: BaseRunner

    private val dummyRequest = InferenceRequest("session-123", mapOf("text" to "Hello"))
    private val successResult = InferenceResult.success(outputs = mapOf("text" to "success"))

    @Before
    fun setUp() {
        // Mock Android's Log class for local unit tests
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        runnerRegistry = RunnerRegistry.getInstance() // Use real registry for integration
        engineManager = AIEngineManager(runnerRegistry)

        mockLLMRunner = mockk<FlowStreamingRunner>(relaxed = true) {
            every { getCapabilities() } returns listOf(CapabilityType.LLM)
            every { getRunnerInfo() } returns RunnerInfo("MockLLMRunner", "1.0", listOf(CapabilityType.LLM))
            every { load(any()) } returns true
            coEvery { run(any(), any()) } returns successResult
            coEvery { runAsFlow(any()) } returns flowOf(successResult)
        }

        mockASRRunner = mockk<BaseRunner>(relaxed = true) {
            every { getCapabilities() } returns listOf(CapabilityType.ASR)
            every { getRunnerInfo() } returns RunnerInfo("MockASRRunner", "1.0", listOf(CapabilityType.ASR))
            every { load(any()) } returns true
            coEvery { run(any(), any()) } returns successResult
            every { unload() } just Runs
        }

        // Use the explicit RunnerRegistration to avoid the simplified register method's side effects
        runnerRegistry.register(RunnerRegistration(
            name = "llm_runner",
            factory = { mockLLMRunner },
            capabilities = listOf(CapabilityType.LLM)
        ))
        runnerRegistry.register(RunnerRegistration(
            name = "asr_runner",
            factory = { mockASRRunner },
            capabilities = listOf(CapabilityType.ASR)
        ))
    }

    @After
    fun tearDown() {
        runnerRegistry.clear()
        engineManager.cleanup()
        // Clean up static mocks after all other cleanup
        unmockkStatic(Log::class)
    }

    @Test
    fun `process with default runner succeeds`() = runTest {
        // Given
        engineManager.setDefaultRunners(mapOf(CapabilityType.LLM to "llm_runner"))

        // When
        val result = engineManager.process(dummyRequest, CapabilityType.LLM)

        // Then
        coVerify { mockLLMRunner.load(any()) }
        coVerify { mockLLMRunner.run(dummyRequest, false) }
        assertEquals(successResult.outputs["text"], result.outputs["text"])
        assertNull(result.error)
    }

    @Test
    fun `process with preferred runner succeeds`() = runTest {
        // When
        val result = engineManager.process(dummyRequest, CapabilityType.LLM, "llm_runner")

        // Then
        coVerify { mockLLMRunner.load(any()) }
        coVerify { mockLLMRunner.run(dummyRequest, false) }
        assertEquals(successResult.outputs["text"], result.outputs["text"])
    }

    @Test
    fun `process fails when no runner for capability`() = runTest {
        // When
        val result = engineManager.process(dummyRequest, CapabilityType.TTS)

        // Then
        assertNotNull(result.error)
        assertEquals("E404", result.error?.code)
    }

    @Test
    fun `process fails when preferred runner does not exist`() = runTest {
        // When
        val result = engineManager.process(dummyRequest, CapabilityType.LLM, "non_existent_runner")

        // Then
        assertNotNull(result.error)
        assertEquals("E404", result.error?.code)
    }

    @Test
    fun `process fails when preferred runner does not support capability`() = runTest {
        // When
        val result = engineManager.process(dummyRequest, CapabilityType.LLM, "asr_runner")

        // Then
        assertNotNull(result.error)
        assertEquals("E405", result.error?.code)
    }

    @Test
    fun `process fails when runner fails to load`() = runTest {
        // Given
        coEvery { mockLLMRunner.load(any()) } returns false
        engineManager.setDefaultRunners(mapOf(CapabilityType.LLM to "llm_runner"))

        // When
        val result = engineManager.process(dummyRequest, CapabilityType.LLM)

        // Then
        assertNotNull(result.error)
        assertEquals("E501", result.error?.code)
        coVerify(exactly = 0) { mockLLMRunner.run(any(), any()) } // Should not attempt to run
    }

    @Test
    fun `process stream succeeds and returns flow`() = runTest {
        // Given
        engineManager.setDefaultRunners(mapOf(CapabilityType.LLM to "llm_runner"))
        val streamingResponse = flow {
            emit(InferenceResult(outputs = mapOf("text" to "part 1"), partial = true))
            emit(InferenceResult(outputs = mapOf("text" to "part 2")))
        }
        coEvery { mockLLMRunner.runAsFlow(any()) } returns streamingResponse

        // When
        val resultFlow = engineManager.processStream(dummyRequest, CapabilityType.LLM)
        val firstResult = resultFlow.first()

        // Then
        coVerify { mockLLMRunner.load(any()) }
        coVerify { mockLLMRunner.runAsFlow(dummyRequest) }
        assertEquals("part 1", firstResult.outputs["text"])
        assertTrue(firstResult.partial)
    }

    @Test
    fun `process stream fails for non-streaming runner`() = runTest {
        // Given
        engineManager.setDefaultRunners(mapOf(CapabilityType.ASR to "asr_runner"))

        // When
        val resultFlow = engineManager.processStream(dummyRequest, CapabilityType.ASR)
        val result = resultFlow.first()

        // Then
        assertNotNull(result.error)
        assertEquals("E406", result.error?.code) // Not a streaming runner
    }

    @Test
    fun `cleanup unloads all loaded runners`() = runTest {
        // Given: Load both runners
        engineManager.setDefaultRunners(
            mapOf(
                CapabilityType.LLM to "llm_runner",
                CapabilityType.ASR to "asr_runner"
            )
        )
        engineManager.process(dummyRequest, CapabilityType.LLM)
        engineManager.process(dummyRequest, CapabilityType.ASR)

        // When
        engineManager.cleanup()

        // Then
        coVerify { mockLLMRunner.unload() }
        coVerify { mockASRRunner.unload() }
    }

    @Test
    fun `cleanup only unloads loaded runners`() = runTest {
        // Given: Load only the LLM runner
        engineManager.setDefaultRunners(mapOf(CapabilityType.LLM to "llm_runner"))
        engineManager.process(dummyRequest, CapabilityType.LLM)

        // When
        engineManager.cleanup()

        // Then
        coVerify(exactly = 1) { mockLLMRunner.unload() }
        coVerify(exactly = 0) { mockASRRunner.unload() } // ASR runner was never loaded
    }
} 