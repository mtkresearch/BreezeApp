package com.mtkresearch.breezeapp.router.injection

import android.content.Intent
import android.util.Log
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import kotlinx.coroutines.CoroutineScope

/**
 * PRODUCTION implementation of the DependencyProvider.
 * This class is responsible for setting up the dependency graph with REAL runners
 * for the production build.
 */
class ProdDependencyProvider : DependencyProvider {

    private val runnerRegistry by lazy {
        RunnerRegistry.getInstance().also { registerProdRunners(it) }
    }

    private val aiEngineManager by lazy {
        AIEngineManager(runnerRegistry)
    }

    override fun getAIEngineManager(): AIEngineManager {
        return aiEngineManager
    }

    override fun initializeRunners(config: Configuration) {
        Log.d(TAG, "Initializing Production Runners with config: $config")
        // Here you would parse the config and set the default runners
        // for the production environment.
        // val defaultMappings = ...
        // aiEngineManager.setDefaultRunners(defaultMappings)
    }

    override fun handleTestIntent(intent: Intent, scope: CoroutineScope) {
        // No-op in production. We do not handle test intents.
        Log.w(TAG, "Test intent received and ignored in production build.")
    }

    private fun registerProdRunners(registry: RunnerRegistry) {
        // TODO: Register real, production-ready runners here
        // e.g., registry.register(RunnerRegistry.RunnerRegistration("ExecuTorchLLMRunner", ::ExecuTorchLLMRunner, ...))
        Log.i(TAG, "Production runners will be registered here.")
    }

    companion object {
        private const val TAG = "ProdDependencyProvider"
    }
} 