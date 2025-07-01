package com.mtkresearch.breezeapp.router.injection

import android.content.Intent
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.shared.contracts.model.Configuration
import kotlinx.coroutines.CoroutineScope
import java.util.ServiceLoader

/**
 * An interface for providing dependencies, allowing for different implementations
 * in `mock` and `prod` source sets.
 */
interface DependencyProvider {

    fun getAIEngineManager(): AIEngineManager

    fun initializeRunners(config: Configuration)

    fun handleTestIntent(intent: Intent, scope: CoroutineScope)

    companion object {
        @Volatile
        private var instance: DependencyProvider? = null

        fun getInstance(): DependencyProvider {
            return instance ?: synchronized(this) {
                instance ?: loadProvider().also { instance = it }
            }
        }
        
        private fun loadProvider(): DependencyProvider {
            // Use ServiceLoader to find the concrete implementation from the build variant.
            // This is a robust way to achieve DI without reflection.
            return ServiceLoader.load(DependencyProvider::class.java).firstOrNull()
                ?: throw IllegalStateException("No DependencyProvider implementation found. Ensure one is present in the 'mock' or 'prod' source set.")
        }
    }
} 