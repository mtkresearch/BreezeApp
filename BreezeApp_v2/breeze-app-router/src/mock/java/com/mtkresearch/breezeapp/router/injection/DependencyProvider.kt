package com.mtkresearch.breezeapp.router.injection

import android.content.Intent
import android.util.Log
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import com.mtkresearch.breezeapp.router.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import com.mtkresearch.breezeapp.router.data.runner.MockASRRunner
import com.mtkresearch.breezeapp.router.data.runner.MockGuardrailRunner
import com.mtkresearch.breezeapp.router.data.runner.MockLLMRunner
import com.mtkresearch.breezeapp.router.data.runner.MockTTSRunner
import com.mtkresearch.breezeapp.router.data.runner.MockVLMRunner
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * MOCK implementation of the DependencyProvider.
 * This class is responsible for setting up the dependency graph with MOCK runners
 * for local development and testing.
 */
class MockDependencyProvider : DependencyProvider {

    private val runnerRegistry by lazy {
        RunnerRegistry.getInstance().also { registerMockRunners(it) }
    }

    private val aiEngineManager by lazy {
        AIEngineManager(runnerRegistry)
    }

    override fun getAIEngineManager(): AIEngineManager {
        return aiEngineManager
    }

    override fun initializeRunners(config: Configuration) {
        Log.d(TAG, "Initializing Mock Runners with config: $config")
        // Set up default runner mappings based on config
        val defaultMappings = mutableMapOf<CapabilityType, String>()
        config.runnerConfigurations.forEach { (taskType, runnerType) ->
            val capability = mapTaskTypeToCapability(taskType)
            // In mock build, we always use a mock runner
            val runnerName = getMockRunnerForCapability(capability)
            defaultMappings[capability] = runnerName
        }
        aiEngineManager.setDefaultRunners(defaultMappings)
        Log.d(TAG, "Mock runners initialized and defaults set: $defaultMappings")
    }

    override fun handleTestIntent(intent: Intent, scope: CoroutineScope) {
        when (intent.action) {
            "com.mtkresearch.breezeapp.TEST_MOCK_RUNNERS" -> {
                Log.d(TAG, "Test command received via ADB in MockDependencyProvider")
                scope.launch {
                    performHeadlessTest()
                }
            }
        }
    }

    private fun registerMockRunners(registry: RunnerRegistry) {
        registry.register(RunnerRegistry.RunnerRegistration("MockLLMRunner", ::MockLLMRunner, listOf(CapabilityType.LLM)))
        registry.register(RunnerRegistry.RunnerRegistration("MockASRRunner", ::MockASRRunner, listOf(CapabilityType.ASR)))
        registry.register(RunnerRegistry.RunnerRegistration("MockVLMRunner", ::MockVLMRunner, listOf(CapabilityType.VLM)))
        registry.register(RunnerRegistry.RunnerRegistration("MockTTSRunner", ::MockTTSRunner, listOf(CapabilityType.TTS)))
        registry.register(RunnerRegistry.RunnerRegistration("MockGuardrailRunner", ::MockGuardrailRunner, listOf(CapabilityType.GUARDIAN)))
        Log.d(TAG, "All mock runners registered: ${registry.getRegisteredRunners()}")
    }

    private suspend fun performHeadlessTest() {
        Log.d(TAG, "=== Starting Headless Mock Runner Test (from MockDependencyProvider) ===")
        try {
            // Test LLM Runner
            val llmRequest = InferenceRequest(
                sessionId = "test_session_llm",
                inputs = mapOf(InferenceRequest.INPUT_TEXT to "Test LLM message"),
                timestamp = System.currentTimeMillis()
            )
            val llmResult = aiEngineManager.process(llmRequest, CapabilityType.LLM)
            Log.d(TAG, "LLM Test Result: ${llmResult.outputs}")

            // Test ASR Runner
            val asrRequest = InferenceRequest(
                sessionId = "test_session_asr",
                inputs = mapOf(
                    InferenceRequest.INPUT_AUDIO to ByteArray(1000) { it.toByte() }
                ),
                timestamp = System.currentTimeMillis()
            )
            val asrResult = aiEngineManager.process(asrRequest, CapabilityType.ASR)
            Log.d(TAG, "ASR Test Result: ${asrResult.outputs}")

            Log.d(TAG, "=== Headless Test Completed Successfully ===")
        } catch (e: Exception) {
            Log.e(TAG, "=== Headless Test Failed ===", e)
        }
    }

    private fun mapTaskTypeToCapability(taskType: Configuration.AITaskType): CapabilityType {
        return when (taskType) {
            Configuration.AITaskType.TEXT_GENERATION -> CapabilityType.LLM
            Configuration.AITaskType.IMAGE_ANALYSIS -> CapabilityType.VLM
            Configuration.AITaskType.SPEECH_RECOGNITION -> CapabilityType.ASR
            Configuration.AITaskType.SPEECH_SYNTHESIS -> CapabilityType.TTS
            Configuration.AITaskType.CONTENT_MODERATION -> CapabilityType.GUARDIAN
        }
    }

    private fun getMockRunnerForCapability(capability: CapabilityType): String {
        return when (capability) {
            CapabilityType.LLM -> "MockLLMRunner"
            CapabilityType.VLM -> "MockVLMRunner"
            CapabilityType.ASR -> "MockASRRunner"
            CapabilityType.TTS -> "MockTTSRunner"
            CapabilityType.GUARDIAN -> "MockGuardrailRunner"
        }
    }

    companion object {
        private const val TAG = "MockDependencyProvider"
    }
} 