package com.mtkresearch.breezeapp.router.injection

import android.content.Context
import com.mtkresearch.breezeapp.router.config.ConfigurationManager
import com.mtkresearch.breezeapp.router.domain.usecase.Logger
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry

/**
 * The main dependency container for the AI Router.
 *
 * This class is responsible for instantiating and wiring up all the core components
 * of the router, including the logger, registry, engine manager, and configuration manager.
 * It is instantiated by the [AIRouterService] and its lifecycle is tied to the service.
 */
class RouterConfigurator(context: Context) {

    // --- Core Dependencies ---
    // The order of initialization matters.

    /** Provides logging capabilities throughout the router. */
    val logger: Logger = AndroidLogger()

    /** Manages the registration and lifecycle of all runners. */
    val runnerRegistry: RunnerRegistry = RunnerRegistry(logger)

    /** The central use case for processing AI requests. */
    val engineManager: AIEngineManager = AIEngineManager(runnerRegistry, logger)
    
    /** Manages loading runner configurations from external files. */
    private val configurationManager: ConfigurationManager = ConfigurationManager(context.applicationContext, logger)

    init {
        // This is the final step: load configurations and register all runners.
        configurationManager.loadAndRegisterRunners(runnerRegistry)
    }
} 