package com.mtkresearch.breezeapp_kotlin.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.*

/**
 * Runtime Settings Repository
 * 
 * 負責Runtime設定的數據持久化和管理
 * 符合Repository Pattern，封裝數據訪問邏輯
 */
class RuntimeSettingsRepository(
    private val context: Context
) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    companion object {
        private const val PREFS_NAME = "runtime_settings"
        
        // LLM Parameters Keys
        private const val KEY_LLM_TEMPERATURE = "llm_temperature"
        private const val KEY_LLM_TOP_K = "llm_top_k"
        private const val KEY_LLM_TOP_P = "llm_top_p"
        private const val KEY_LLM_MAX_TOKENS = "llm_max_tokens"
        private const val KEY_LLM_STREAMING = "llm_streaming"
        
        // VLM Parameters Keys
        private const val KEY_VLM_VISION_TEMPERATURE = "vlm_vision_temperature"
        private const val KEY_VLM_IMAGE_RESOLUTION = "vlm_image_resolution"
        private const val KEY_VLM_IMAGE_ANALYSIS = "vlm_image_analysis"
        
        // ASR Parameters Keys
        private const val KEY_ASR_LANGUAGE_MODEL = "asr_language_model"
        private const val KEY_ASR_BEAM_SIZE = "asr_beam_size"
        private const val KEY_ASR_NOISE_SUPPRESSION = "asr_noise_suppression"
        
        // TTS Parameters Keys
        private const val KEY_TTS_SPEAKER_ID = "tts_speaker_id"
        private const val KEY_TTS_SPEED_RATE = "tts_speed_rate"
        private const val KEY_TTS_VOLUME = "tts_volume"
        
        // General Parameters Keys
        private const val KEY_GENERAL_GPU_ACCELERATION = "general_gpu_acceleration"
        private const val KEY_GENERAL_NPU_ACCELERATION = "general_npu_acceleration"
        private const val KEY_GENERAL_MAX_CONCURRENT_TASKS = "general_max_concurrent_tasks"
        private const val KEY_GENERAL_DEBUG_LOGGING = "general_debug_logging"
    }
    
    /**
     * 載入Runtime設定
     */
    suspend fun loadSettings(): RuntimeSettings {
        return try {
            val llmParams = LLMParameters(
                temperature = prefs.getFloat(KEY_LLM_TEMPERATURE, 0.7f),
                topK = prefs.getInt(KEY_LLM_TOP_K, 5),
                topP = prefs.getFloat(KEY_LLM_TOP_P, 0.9f),
                maxTokens = prefs.getInt(KEY_LLM_MAX_TOKENS, 2048),
                enableStreaming = prefs.getBoolean(KEY_LLM_STREAMING, true)
            )
            
            val vlmParams = VLMParameters(
                visionTemperature = prefs.getFloat(KEY_VLM_VISION_TEMPERATURE, 0.7f),
                imageResolution = ImageResolution.values()[prefs.getInt(KEY_VLM_IMAGE_RESOLUTION, 1)],
                enableImageAnalysis = prefs.getBoolean(KEY_VLM_IMAGE_ANALYSIS, true)
            )
            
            val asrParams = ASRParameters(
                languageModel = prefs.getString(KEY_ASR_LANGUAGE_MODEL, "zh-TW") ?: "zh-TW",
                beamSize = prefs.getInt(KEY_ASR_BEAM_SIZE, 4),
                enableNoiseSuppression = prefs.getBoolean(KEY_ASR_NOISE_SUPPRESSION, true)
            )
            
            val ttsParams = TTSParameters(
                speakerId = prefs.getInt(KEY_TTS_SPEAKER_ID, 0),
                speedRate = prefs.getFloat(KEY_TTS_SPEED_RATE, 1.0f),
                volume = prefs.getFloat(KEY_TTS_VOLUME, 0.8f)
            )
            
            val generalParams = GeneralParameters(
                enableGPUAcceleration = prefs.getBoolean(KEY_GENERAL_GPU_ACCELERATION, true),
                enableNPUAcceleration = prefs.getBoolean(KEY_GENERAL_NPU_ACCELERATION, false),
                maxConcurrentTasks = prefs.getInt(KEY_GENERAL_MAX_CONCURRENT_TASKS, 2),
                enableDebugLogging = prefs.getBoolean(KEY_GENERAL_DEBUG_LOGGING, false)
            )
            
            RuntimeSettings(
                llmParams = llmParams,
                vlmParams = vlmParams,
                asrParams = asrParams,
                ttsParams = ttsParams,
                generalParams = generalParams
            )
        } catch (e: Exception) {
            // 如果載入失敗，返回預設設定
            RuntimeSettings()
        }
    }
    
    /**
     * 儲存Runtime設定
     */
    suspend fun saveSettings(settings: RuntimeSettings): Result<Unit> {
        return try {
            prefs.edit().apply {
                // LLM Parameters
                putFloat(KEY_LLM_TEMPERATURE, settings.llmParams.temperature)
                putInt(KEY_LLM_TOP_K, settings.llmParams.topK)
                putFloat(KEY_LLM_TOP_P, settings.llmParams.topP)
                putInt(KEY_LLM_MAX_TOKENS, settings.llmParams.maxTokens)
                putBoolean(KEY_LLM_STREAMING, settings.llmParams.enableStreaming)
                
                // VLM Parameters
                putFloat(KEY_VLM_VISION_TEMPERATURE, settings.vlmParams.visionTemperature)
                putInt(KEY_VLM_IMAGE_RESOLUTION, settings.vlmParams.imageResolution.ordinal)
                putBoolean(KEY_VLM_IMAGE_ANALYSIS, settings.vlmParams.enableImageAnalysis)
                
                // ASR Parameters
                putString(KEY_ASR_LANGUAGE_MODEL, settings.asrParams.languageModel)
                putInt(KEY_ASR_BEAM_SIZE, settings.asrParams.beamSize)
                putBoolean(KEY_ASR_NOISE_SUPPRESSION, settings.asrParams.enableNoiseSuppression)
                
                // TTS Parameters
                putInt(KEY_TTS_SPEAKER_ID, settings.ttsParams.speakerId)
                putFloat(KEY_TTS_SPEED_RATE, settings.ttsParams.speedRate)
                putFloat(KEY_TTS_VOLUME, settings.ttsParams.volume)
                
                // General Parameters
                putBoolean(KEY_GENERAL_GPU_ACCELERATION, settings.generalParams.enableGPUAcceleration)
                putBoolean(KEY_GENERAL_NPU_ACCELERATION, settings.generalParams.enableNPUAcceleration)
                putInt(KEY_GENERAL_MAX_CONCURRENT_TASKS, settings.generalParams.maxConcurrentTasks)
                putBoolean(KEY_GENERAL_DEBUG_LOGGING, settings.generalParams.enableDebugLogging)
                
                apply()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 重置為預設設定
     */
    suspend fun resetToDefault(): Result<Unit> {
        return saveSettings(RuntimeSettings())
    }
} 