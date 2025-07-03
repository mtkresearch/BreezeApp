package com.mtkresearch.breezeapp.router.config

import android.content.Context
import com.mtkresearch.breezeapp.router.domain.common.Logger
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import kotlinx.serialization.json.Json
import java.io.IOException

private const val TAG = "ConfigManager"

/**
 * Manages loading and registering runners from an external configuration file.
 * This class decouples the runner registration logic from the application's source code,
 * allowing for dynamic configuration without recompiling the app.
 */
class ConfigurationManager(
    private val context: Context,
    private val logger: Logger
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Reads the runner configuration file from assets, parses it, and registers
     * the runners with the provided [RunnerRegistry].
     *
     * @param registry The [RunnerRegistry] to register runners with.
     */
    fun loadAndRegisterRunners(registry: RunnerRegistry) {
        try {
            val jsonString = readConfigFileFromAssets()
            val configFile = json.decodeFromString<RunnerConfigFile>(jsonString)

            configFile.runners.forEach { definition ->
                try {
                    registerRunnerFromDefinition(definition, registry)
                } catch (e: Exception) {
                    logger.e(TAG, "Failed to register runner '${definition.name}': ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to load or parse runner configuration: ${e.message}", e)
        }
    }

    private fun registerRunnerFromDefinition(definition: RunnerDefinition, registry: RunnerRegistry) {
        val runnerClass = Class.forName(definition.className)

        // For "real" runners, check if the device supports them before registering.
        if (definition.isReal) {
            try {
                val isSupportedMethod = runnerClass.getMethod("isSupported")
                val isSupported = isSupportedMethod.invoke(null) as? Boolean ?: false
                if (!isSupported) {
                    logger.d(TAG, "Skipping unsupported real runner: ${definition.name}")
                    return
                }
            } catch (e: NoSuchMethodException) {
                logger.w(TAG, "Runner '${definition.name}' is marked as real but has no static isSupported() method. Skipping.")
                return
            }
        }

        // Create a factory lambda using reflection.
        val factory = {
            runnerClass.getConstructor().newInstance() as BaseRunner
        }

        // Convert capability strings to Enum types.
        val capabilities = definition.capabilities.mapNotNull {
            try {
                CapabilityType.valueOf(it)
            } catch (e: IllegalArgumentException) {
                logger.w(TAG, "Unknown capability '${it}' for runner '${definition.name}'. Ignoring.")
                null
            }
        }

        if (capabilities.isEmpty()) {
            logger.w(TAG, "Runner '${definition.name}' has no valid capabilities. Skipping registration.")
            return
        }

        registry.register(
            RunnerRegistry.RunnerRegistration(
                name = definition.name,
                factory = factory,
                capabilities = capabilities,
                priority = definition.priority,
                // Description and version could be added to JSON if needed
            )
        )
    }

    private fun readConfigFileFromAssets(): String {
        try {
            return context.assets.open("runner_config.json").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            logger.e(TAG, "Could not read runner_config.json from assets.", e)
            throw e
        }
    }
} 