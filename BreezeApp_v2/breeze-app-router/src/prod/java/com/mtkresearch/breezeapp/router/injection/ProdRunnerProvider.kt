package com.mtkresearch.breezeapp.router.injection

import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import android.util.Log

/**
 * [RunnerProvider] implementation for the 'prod' build flavor.
 * This provider will register the real, production-ready runners.
 */
class ProdRunnerProvider : RunnerProvider {
    override fun registerRunners(registry: RunnerRegistry) {
        // TODO: Register real runners here
        // e.g., registry.register("ExecuTorchLLMRunner") { ExecuTorchLLMRunner() }
        Log.d("ProdRunnerProvider", "Production runners would be registered here.")
    }
} 