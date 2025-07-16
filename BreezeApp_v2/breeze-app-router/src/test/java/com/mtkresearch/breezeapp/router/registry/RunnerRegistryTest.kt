package com.mtkresearch.breezeapp.router.registry

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.RunnerInfo
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import com.mtkresearch.breezeapp.router.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.router.domain.model.InferenceResult
import com.mtkresearch.breezeapp.router.domain.model.ModelConfig
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import com.mtkresearch.breezeapp.router.domain.usecase.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RunnerRegistryTest {

    private lateinit var registry: RunnerRegistry
    private lateinit var mockLogger: Logger

    // A minimal valid runner for testing
    abstract class TestRunner(
        private val name: String,
        private val capabilities: List<CapabilityType>
    ) : BaseRunner {
        override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
            return InferenceResult(outputs = mapOf("text" to "$name output"))
        }
        override fun getRunnerInfo(): RunnerInfo {
            return RunnerInfo(name, "1.0", capabilities, "A test runner", false)
        }
        override fun getCapabilities(): List<CapabilityType> = capabilities
        override fun load(config: ModelConfig): Boolean = true
        override fun unload() {}
        override fun isLoaded(): Boolean = true
    }

    class TestLLMRunner : TestRunner("TestLLMRunner", listOf(CapabilityType.LLM))
    class TestASRRunner : TestRunner("TestASRRunner", listOf(CapabilityType.ASR))
    class AnotherLLMRunner : TestRunner("AnotherLLMRunner", listOf(CapabilityType.LLM))

    @Before
    fun setUp() {
        // Mock the logger to avoid real logging during tests
        mockLogger = mockk(relaxed = true)

        // Instantiate the registry with the mock logger
        registry = RunnerRegistry(mockLogger)
    }

    @After
    fun tearDown() {
        // No static mocks to unmockk
    }

    @Test
    fun `register and create runner`() {
        registry.register("llm_test", ::TestLLMRunner)
        val runner = registry.createRunner("llm_test")
        assertNotNull(runner)
        assertTrue(runner is TestLLMRunner)
    }

    @Test
    fun `create non-existent runner returns null`() {
        val runner = registry.createRunner("non_existent")
        assertNull(runner)
    }

    @Test
    fun `get runners by capability`() {
        registry.register("llm_test", ::TestLLMRunner)
        registry.register("asr_test", ::TestASRRunner)

        val llmRunners = registry.getRunnersForCapability(CapabilityType.LLM)
        assertEquals(1, llmRunners.size)
        assertEquals("llm_test", llmRunners.first().name)

        val asrRunners = registry.getRunnersForCapability(CapabilityType.ASR)
        assertEquals(1, asrRunners.size)
        assertEquals("asr_test", asrRunners.first().name)
    }

    @Test
    fun `getRunners by capability returns runners sorted by priority`() {
        // Register two LLM runners with different priorities
        registry.register("high_priority_llm", ::AnotherLLMRunner, priority = 10)
        registry.register("low_priority_llm", ::TestLLMRunner, priority = 100)

        // Get runners for LLM capability
        val llmRunners = registry.getRunnersForCapability(CapabilityType.LLM)

        // Check that they are sorted by priority (lower number first)
        assertEquals(2, llmRunners.size)
        assertEquals("high_priority_llm", llmRunners[0].name)
        assertEquals(10, llmRunners[0].priority)
        assertEquals("low_priority_llm", llmRunners[1].name)
        assertEquals(100, llmRunners[1].priority)

        // Check that getRunnerForCapability returns the highest priority one
        val bestRunner = registry.getRunnerForCapability(CapabilityType.LLM)
        assertNotNull(bestRunner)
        assertEquals("AnotherLLMRunner", bestRunner?.getRunnerInfo()?.name)
    }

    @Test
    fun `get runners for an unsupported capability returns empty list`() {
        registry.register("llm_test", ::TestLLMRunner)
        val ttsRunners = registry.getRunnersForCapability(CapabilityType.TTS)
        assertTrue(ttsRunners.isEmpty())
    }

    @Test
    fun `registering with same name overwrites previous`() {
        registry.register("conflicting_name", ::TestASRRunner)
        val firstRunner = registry.createRunner("conflicting_name")
        assertTrue(firstRunner is TestASRRunner)

        registry.register("conflicting_name", ::TestLLMRunner)
        val secondRunner = registry.createRunner("conflicting_name")
        assertTrue(secondRunner is TestLLMRunner)
    }

    @Test
    fun `clear removes all registered runners`() {
        registry.register("llm_test", ::TestLLMRunner)
        registry.register("asr_test", ::TestASRRunner)
        assertNotNull(registry.createRunner("llm_test"))
        assertNotNull(registry.createRunner("asr_test"))

        registry.clear()

        assertNull(registry.createRunner("llm_test"))
        assertNull(registry.createRunner("asr_test"))
        assertTrue(registry.getRunnersForCapability(CapabilityType.LLM).isEmpty())
    }

    @Test
    fun `unregister removes a specific runner`() {
        registry.register("llm_test", ::TestLLMRunner)
        registry.register("asr_test", ::TestASRRunner)

        registry.unregister("llm_test")

        assertNull(registry.createRunner("llm_test"))
        assertNotNull(registry.createRunner("asr_test"))
        assertTrue(registry.getRunnersForCapability(CapabilityType.LLM).isEmpty())
        assertEquals(1, registry.getRunnersForCapability(CapabilityType.ASR).size)
    }
} 