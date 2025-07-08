package com.mtkresearch.breezeapp.edgeai.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * Configuration settings for the AI Router Service.
 *
 * @param apiVersion The API version to use (default: 1).
 * @param logLevel Controls the verbosity of logging (0=OFF, 1=ERROR, 2=WARN, 3=INFO, 4=DEBUG, 5=VERBOSE).
 * @param preferredRuntime The preferred runtime backend to use (CPU, GPU, NPU).
 * @param runnerConfigurations Map of AI task types to their runner implementations.
 * @param defaultModelName The default model to use if not specified in requests.
 * @param languagePreference The preferred language for responses.
 * @param timeoutMs Request timeout in milliseconds (0 means no timeout).
 * @param maxTokens Maximum number of tokens to generate in responses.
 * @param temperature Controls randomness in generation (0.0-1.0, lower is more deterministic).
 * @param additionalSettings Additional configuration settings as key-value pairs.
 */
@Parcelize
@TypeParceler<Configuration.RuntimeBackend, RuntimeBackendParceler>()
@TypeParceler<Configuration.AITaskType, AITaskTypeParceler>()
@TypeParceler<Configuration.RunnerType, RunnerTypeParceler>()
data class Configuration(
    val apiVersion: Int = 1,
    val logLevel: Int = 0,
    val preferredRuntime: RuntimeBackend = RuntimeBackend.AUTO,
    val runnerConfigurations: Map<AITaskType, RunnerType> = mapOf(
        AITaskType.TEXT_GENERATION to RunnerType.DEFAULT,
        AITaskType.IMAGE_ANALYSIS to RunnerType.DEFAULT,
        AITaskType.SPEECH_RECOGNITION to RunnerType.DEFAULT
    ),
    val defaultModelName: String = "",
    val languagePreference: String = "en",
    val timeoutMs: Long = 30000,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val additionalSettings: Map<String, String> = emptyMap()
) : Parcelable {

    /**
     * Available runtime backends for AI processing.
     */
    enum class RuntimeBackend {
        /** Automatically select the best available runtime */
        AUTO,
        
        /** Force CPU execution */
        CPU,
        
        /** Use GPU acceleration if available */
        GPU,
        
        /** Use Neural Processing Unit if available */
        NPU
    }
    
    /**
     * Types of AI tasks that can be performed by the service.
     */
    enum class AITaskType {
        /** Text generation (chat, completion) */
        TEXT_GENERATION,
        
        /** Image analysis (vision) */
        IMAGE_ANALYSIS,
        
        /** Speech recognition (ASR) */
        SPEECH_RECOGNITION,
        
        /** Text-to-speech synthesis */
        SPEECH_SYNTHESIS,
        
        /** Content moderation */
        CONTENT_MODERATION
    }
    
    /**
     * Available runner implementations for each task type.
     */
    enum class RunnerType {
        /** Use the default runner for the task */
        DEFAULT,
        
        /** Use a mock runner that returns predefined responses */
        MOCK,
        
        /** Use ExecuTorch-based runner */
        EXECUTORCH,
        
        /** Use ONNX Runtime-based runner */
        ONNX,
        
        /** Use MediaTek APU-optimized runner */
        MTK_APU,
        
        /** Use llama.cpp-based runner */
        LLAMA_CPP,
        
        /** Use system APIs where available */
        SYSTEM
    }
    
    /**
     * Common keys for additional settings.
     */
    object SettingKeys {
        const val ENABLE_LOGGING = "enable_logging"
        const val CACHE_MODELS = "cache_models"
        const val MAX_CACHE_SIZE_MB = "max_cache_size_mb"
        const val ENABLE_STREAMING = "enable_streaming"
        const val ENABLE_GUARDRAILS = "enable_guardrails"
        const val MODEL_PATH_PREFIX = "model_path_"  // Use with task type, e.g. model_path_text_generation
        const val RUNNER_CONFIG_PREFIX = "runner_config_" // Use with task type for runner-specific settings
    }

    companion object {
        @JvmStatic
        fun createFromParcel(parcel: Parcel): Configuration {
            val apiVersion = parcel.readInt()
            val logLevel = parcel.readInt()
            
            // Read runtime enum by name for better stability
            val runtimeName = parcel.readString() ?: RuntimeBackend.AUTO.name
            val preferredRuntime = try {
                RuntimeBackend.valueOf(runtimeName)
            } catch (e: IllegalArgumentException) {
                RuntimeBackend.AUTO
            }
            
            // Read runner configurations map
            val runnerConfigCount = parcel.readInt()
            val runnerConfigs = mutableMapOf<AITaskType, RunnerType>()
            for (i in 0 until runnerConfigCount) {
                val taskName = parcel.readString() ?: AITaskType.TEXT_GENERATION.name
                val runnerName = parcel.readString() ?: RunnerType.DEFAULT.name
                
                val taskType = try {
                    AITaskType.valueOf(taskName)
                } catch (e: IllegalArgumentException) {
                    AITaskType.TEXT_GENERATION
                }
                
                val runnerType = try {
                    RunnerType.valueOf(runnerName)
                } catch (e: IllegalArgumentException) {
                    RunnerType.DEFAULT
                }
                
                runnerConfigs[taskType] = runnerType
            }
            
            val defaultModelName = parcel.readString() ?: ""
            val languagePreference = parcel.readString() ?: "en"
            val timeoutMs = parcel.readLong()
            val maxTokens = parcel.readInt()
            val temperature = parcel.readFloat()
            
            // Read additional settings map
            val settingsCount = parcel.readInt()
            val settings = mutableMapOf<String, String>()
            for (i in 0 until settingsCount) {
                val key = parcel.readString() ?: ""
                val value = parcel.readString() ?: ""
                settings[key] = value
            }
            
            return Configuration(
                apiVersion = apiVersion,
                logLevel = logLevel,
                preferredRuntime = preferredRuntime,
                runnerConfigurations = runnerConfigs,
                defaultModelName = defaultModelName,
                languagePreference = languagePreference,
                timeoutMs = timeoutMs,
                maxTokens = maxTokens,
                temperature = temperature,
                additionalSettings = settings
            )
        }
    }
}

/**
 * Custom parceler for RuntimeBackend enum that writes the enum as a string name
 * instead of an ordinal value for better stability across versions.
 */
object RuntimeBackendParceler : Parceler<Configuration.RuntimeBackend> {
    override fun create(parcel: Parcel): Configuration.RuntimeBackend {
        val name = parcel.readString() ?: return Configuration.RuntimeBackend.AUTO
        return try {
            Configuration.RuntimeBackend.valueOf(name)
        } catch (e: IllegalArgumentException) {
            Configuration.RuntimeBackend.AUTO
        }
    }

    override fun Configuration.RuntimeBackend.write(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
    }
}

/**
 * Custom parceler for AITaskType enum that writes the enum as a string name
 * instead of an ordinal value for better stability across versions.
 */
object AITaskTypeParceler : Parceler<Configuration.AITaskType> {
    override fun create(parcel: Parcel): Configuration.AITaskType {
        val name = parcel.readString() ?: return Configuration.AITaskType.TEXT_GENERATION
        return try {
            Configuration.AITaskType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            Configuration.AITaskType.TEXT_GENERATION
        }
    }

    override fun Configuration.AITaskType.write(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
    }
}

/**
 * Custom parceler for RunnerType enum that writes the enum as a string name
 * instead of an ordinal value for better stability across versions.
 */
object RunnerTypeParceler : Parceler<Configuration.RunnerType> {
    override fun create(parcel: Parcel): Configuration.RunnerType {
        val name = parcel.readString() ?: return Configuration.RunnerType.DEFAULT
        return try {
            Configuration.RunnerType.valueOf(name)
        } catch (e: IllegalArgumentException) {
            Configuration.RunnerType.DEFAULT
        }
    }

    override fun Configuration.RunnerType.write(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
    }
} 