package com.mtkresearch.breezeapp.router.injection

/**
 * The dependency provider for the 'mock' build flavor.
 * It supplies the [MockRunnerProvider] for development and testing.
 */
object DependencyProvider {
    fun getRunnerProvider(): RunnerProvider {
        return MockRunnerProvider()
    }
} 