package com.mtkresearch.breezeapp.router.injection

import com.mtkresearch.breezeapp.router.data.runner.*
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry

/**
 * [RunnerProvider] implementation for the 'mock' build flavor.
 * This provider registers all mock runners for development and testing.
 */
class MockRunnerProvider : RunnerProvider {
    override fun registerRunners(registry: RunnerRegistry) {
        registry.register("MockLLMRunner") { MockLLMRunner() }
        registry.register("MockASRRunner") { MockASRRunner() }
        registry.register("MockTTSRunner") { MockTTSRunner() }
        registry.register("MockVLMRunner") { MockVLMRunner() }
        registry.register("MockGuardrailRunner") { MockGuardrailRunner() }
    }
} 