package com.mtkresearch.breezeapp.router.config

import android.content.Context
import com.mtkresearch.breezeapp.router.data.runner.MTKLLMRunner
import com.mtkresearch.breezeapp.router.domain.usecase.Logger
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import com.mtkresearch.breezeapp.router.domain.usecase.RunnerRegistry
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.File
import org.json.JSONObject

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

        // --- 這裡是修正重點 ---
        val factory = when (definition.className) {
            "com.mtkresearch.breezeapp.router.data.runner.MTKLLMRunner" -> {
                {
                    val modelId = definition.modelId ?: "Breeze2-3B-8W16A-250630-npu"
                    val entryPointPath = getLocalEntryPointPath(context, modelId)
                        ?: throw IllegalStateException("Model entry point not found for $modelId")
                    MTKLLMRunner.create(context, MTKConfig.createDefault(entryPointPath))
                }
            }
            else -> {
                { runnerClass.getConstructor().newInstance() as BaseRunner }
            }
        }
        // --- 修正結束 ---

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

    fun getLocalEntryPointPath(context: Context, modelId: String): String? {
        val file = File(context.filesDir, "downloadedModelList.json")
        if (!file.exists()) return null
        val json = file.readText()
        val modelList = JSONObject(json).getJSONArray("models")
        for (i in 0 until modelList.length()) {
            val model = modelList.getJSONObject(i)
            if (model.getString("id") == modelId) {
                val entryPoint = model.getString("entryPointValue")
                return File(context.filesDir, "models/$modelId/$entryPoint").absolutePath
            }
        }
        return null
    }
} 