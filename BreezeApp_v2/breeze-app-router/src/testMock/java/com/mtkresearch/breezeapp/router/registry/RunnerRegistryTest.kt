package com.mtkresearch.breezeapp.router.registry

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.RunnerInfo
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import com.mtkresearch.breezeapp.router.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.router.domain.model.InferenceResult
import com.mtkresearch.breezeapp.router.domain.model.ModelConfig
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import io.mockk.every
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

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        registry = RunnerRegistry.getInstance()
        registry.clear() // Ensure clean state for each test
    }

    @After
    fun tearDown() {
        registry.clear()
        unmockkStatic(Log::class)
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
        assertEquals("llm_test", llmRunners.first())

        val asrRunners = registry.getRunnersForCapability(CapabilityType.ASR)
        assertEquals(1, asrRunners.size)
        assertEquals("asr_test", asrRunners.first())
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