package com.mtkresearch.breezeapp.domain.usecase.settings

import com.mtkresearch.breezeapp.presentation.settings.model.*

/**
 * 運行時參數更新用例
 * 負責處理運行時設定的參數更新邏輯
 */
class UpdateRuntimeParameterUseCase(
    private val validateRuntimeSettingsUseCase: ValidateRuntimeSettingsUseCase
) {

    /**
     * 更新運行時參數
     * @param currentSettings 當前設定
     * @param parameterUpdate 參數更新操作
     * @return Result<RuntimeSettings> 更新後的設定或錯誤
     */
    operator fun invoke(
        currentSettings: RuntimeSettings,
        parameterUpdate: ParameterUpdate
    ): Result<RuntimeSettings> {
        return try {
            val updatedSettings = when (parameterUpdate) {
                is ParameterUpdate.LLM -> updateLLMParameter(currentSettings, parameterUpdate)
                is ParameterUpdate.VLM -> updateVLMParameter(currentSettings, parameterUpdate)
                is ParameterUpdate.ASR -> updateASRParameter(currentSettings, parameterUpdate)
                is ParameterUpdate.TTS -> updateTTSParameter(currentSettings, parameterUpdate)
                is ParameterUpdate.General -> updateGeneralParameter(currentSettings, parameterUpdate)
            }
            
            // 驗證更新後的設定
            val validationResult = validateRuntimeSettingsUseCase(updatedSettings)
            when (validationResult) {
                is ValidationResult.Invalid -> {
                    Result.failure(IllegalArgumentException(validationResult.errors.joinToString(", ")))
                }
                else -> Result.success(updatedSettings)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun updateLLMParameter(settings: RuntimeSettings, update: ParameterUpdate.LLM): RuntimeSettings {
        val newParams = when (update) {
            is ParameterUpdate.LLM.Temperature -> settings.llmParams.copy(temperature = update.value)
            is ParameterUpdate.LLM.TopK -> settings.llmParams.copy(topK = update.value)
            is ParameterUpdate.LLM.TopP -> settings.llmParams.copy(topP = update.value)
            is ParameterUpdate.LLM.MaxTokens -> settings.llmParams.copy(maxTokens = update.value)
            is ParameterUpdate.LLM.RepetitionPenalty -> settings.llmParams.copy(repetitionPenalty = update.value)
            is ParameterUpdate.LLM.FrequencyPenalty -> settings.llmParams.copy(frequencyPenalty = update.value)
            is ParameterUpdate.LLM.SystemPrompt -> settings.llmParams.copy(systemPrompt = update.value)
            is ParameterUpdate.LLM.EnableStreaming -> settings.llmParams.copy(enableStreaming = update.value)
        }
        return settings.copy(llmParams = newParams)
    }

    private fun updateVLMParameter(settings: RuntimeSettings, update: ParameterUpdate.VLM): RuntimeSettings {
        val newParams = when (update) {
            is ParameterUpdate.VLM.ImageResolution -> settings.vlmParams.copy(imageResolution = update.value)
            is ParameterUpdate.VLM.VisionTemperature -> settings.vlmParams.copy(visionTemperature = update.value)
            is ParameterUpdate.VLM.MaxImageTokens -> settings.vlmParams.copy(maxImageTokens = update.value)
            is ParameterUpdate.VLM.EnableImageAnalysis -> settings.vlmParams.copy(enableImageAnalysis = update.value)
            is ParameterUpdate.VLM.CropImages -> settings.vlmParams.copy(cropImages = update.value)
        }
        return settings.copy(vlmParams = newParams)
    }

    private fun updateASRParameter(settings: RuntimeSettings, update: ParameterUpdate.ASR): RuntimeSettings {
        val newParams = when (update) {
            is ParameterUpdate.ASR.LanguageModel -> settings.asrParams.copy(languageModel = update.value)
            is ParameterUpdate.ASR.BeamSize -> settings.asrParams.copy(beamSize = update.value)
            is ParameterUpdate.ASR.VADThreshold -> settings.asrParams.copy(vadThreshold = update.value)
            is ParameterUpdate.ASR.EnableNoiseSuppression -> settings.asrParams.copy(enableNoiseSuppression = update.value)
            is ParameterUpdate.ASR.EnableEchoCancellation -> settings.asrParams.copy(enableEchoCancellation = update.value)
        }
        return settings.copy(asrParams = newParams)
    }

    private fun updateTTSParameter(settings: RuntimeSettings, update: ParameterUpdate.TTS): RuntimeSettings {
        val newParams = when (update) {
            is ParameterUpdate.TTS.SpeakerId -> settings.ttsParams.copy(speakerId = update.value)
            is ParameterUpdate.TTS.SpeedRate -> settings.ttsParams.copy(speedRate = update.value)
            is ParameterUpdate.TTS.Volume -> settings.ttsParams.copy(volume = update.value)
            is ParameterUpdate.TTS.Pitch -> settings.ttsParams.copy(pitch = update.value)
        }
        return settings.copy(ttsParams = newParams)
    }

    private fun updateGeneralParameter(settings: RuntimeSettings, update: ParameterUpdate.General): RuntimeSettings {
        val newParams = when (update) {
            is ParameterUpdate.General.EnableGPUAcceleration -> settings.generalParams.copy(enableGPUAcceleration = update.value)
            is ParameterUpdate.General.EnableNPUAcceleration -> settings.generalParams.copy(enableNPUAcceleration = update.value)
            is ParameterUpdate.General.MaxConcurrentTasks -> settings.generalParams.copy(maxConcurrentTasks = update.value)
            is ParameterUpdate.General.EnableDebugLogging -> settings.generalParams.copy(enableDebugLogging = update.value)
        }
        return settings.copy(generalParams = newParams)
    }
}

/**
 * 參數更新操作定義
 */
sealed class ParameterUpdate {
    sealed class LLM : ParameterUpdate() {
        data class Temperature(val value: Float) : LLM()
        data class TopK(val value: Int) : LLM()
        data class TopP(val value: Float) : LLM()
        data class MaxTokens(val value: Int) : LLM()
        data class RepetitionPenalty(val value: Float) : LLM()
        data class FrequencyPenalty(val value: Float) : LLM()
        data class SystemPrompt(val value: String) : LLM()
        data class EnableStreaming(val value: Boolean) : LLM()
    }

    sealed class VLM : ParameterUpdate() {
        data class ImageResolution(val value: com.mtkresearch.breezeapp.presentation.settings.model.ImageResolution) : VLM()
        data class VisionTemperature(val value: Float) : VLM()
        data class MaxImageTokens(val value: Int) : VLM()
        data class EnableImageAnalysis(val value: Boolean) : VLM()
        data class CropImages(val value: Boolean) : VLM()
    }

    sealed class ASR : ParameterUpdate() {
        data class LanguageModel(val value: String) : ASR()
        data class BeamSize(val value: Int) : ASR()
        data class VADThreshold(val value: Float) : ASR()
        data class EnableNoiseSuppression(val value: Boolean) : ASR()
        data class EnableEchoCancellation(val value: Boolean) : ASR()
    }

    sealed class TTS : ParameterUpdate() {
        data class SpeakerId(val value: Int) : TTS()
        data class SpeedRate(val value: Float) : TTS()
        data class Volume(val value: Float) : TTS()
        data class Pitch(val value: Float) : TTS()
    }

    sealed class General : ParameterUpdate() {
        data class EnableGPUAcceleration(val value: Boolean) : General()
        data class EnableNPUAcceleration(val value: Boolean) : General()
        data class MaxConcurrentTasks(val value: Int) : General()
        data class EnableDebugLogging(val value: Boolean) : General()
    }
} 