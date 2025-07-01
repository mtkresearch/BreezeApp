package com.mtkresearch.breezeapp.router.injection

/**
 * The dependency provider for the 'prod' build flavor.
 * It supplies the [ProdRunnerProvider] for the production-ready application.
 */
object DependencyProvider {
    fun getRunnerProvider(): RunnerProvider {
        return ProdRunnerProvider()
    }
} 