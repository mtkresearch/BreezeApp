package com.mtkresearch.breezeapp.router.injection

import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry

/**
 * An interface for providing AI runners.
 * This abstraction allows for different sets of runners to be provided
 * for different build variants (e.g., mock vs. production).
 */
interface RunnerProvider {
    /**
     * Registers all available runners for the current build variant.
     *
     * @param registry The RunnerRegistry instance to register runners with.
     */
    fun registerRunners(registry: RunnerRegistry)
} 