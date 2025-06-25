package com.mtkresearch.breezeapp_kotlin.domain.usecase.settings

import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.*

/**
 * 運行時設定驗證用例
 * 負責驗證AI推論參數的有效性和一致性
 */
class ValidateRuntimeSettingsUseCase {

    /**
     * 驗證運行時設定
     * @param settings 要驗證的設定
     * @return ValidationResult 驗證結果
     */
    operator fun invoke(settings: RuntimeSettings): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 驗證LLM參數
        validateLLMParameters(settings.llmParams, errors, warnings)
        
        // 驗證VLM參數
        validateVLMParameters(settings.vlmParams, errors, warnings)
        
        // 驗證ASR參數
        validateASRParameters(settings.asrParams, errors, warnings)
        
        // 驗證TTS參數
        validateTTSParameters(settings.ttsParams, errors, warnings)
        
        // 驗證通用參數
        validateGeneralParameters(settings.generalParams, errors, warnings)

        return when {
            errors.isNotEmpty() -> ValidationResult.Invalid(errors)
            warnings.isNotEmpty() -> ValidationResult.Warning(warnings)
            else -> ValidationResult.Valid
        }
    }

    private fun validateLLMParameters(params: LLMParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Temperature驗證
        if (params.temperature < 0f || params.temperature > 2f) {
            errors.add("Temperature must be between 0.0 and 2.0")
        } else if (params.temperature > 1.5f) {
            warnings.add("High temperature setting detected")
        }
        
        // Top-K驗證
        if (params.topK < 1 || params.topK > 100) {
            errors.add("Top-K must be between 1 and 100")
        }
        
        // Top-P驗證
        if (params.topP < 0f || params.topP > 1f) {
            errors.add("Top-P must be between 0.0 and 1.0")
        }
        
        // Max Tokens驗證
        if (params.maxTokens < 10 || params.maxTokens > 8192) {
            errors.add("Max tokens must be between 10 and 8192")
        }
        
        // Repetition Penalty驗證
        if (params.repetitionPenalty < 0.1f || params.repetitionPenalty > 2f) {
            errors.add("Repetition penalty must be between 0.1 and 2.0")
        }
        
        // Frequency Penalty驗證
        if (params.frequencyPenalty < 0f || params.frequencyPenalty > 2f) {
            errors.add("Frequency penalty must be between 0.0 and 2.0")
        }
    }

    private fun validateVLMParameters(params: VLMParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Vision Temperature驗證
        if (params.visionTemperature < 0f || params.visionTemperature > 2f) {
            errors.add("Vision temperature must be between 0.0 and 2.0")
        }
        
        // Max Image Tokens驗證
        if (params.maxImageTokens < 32 || params.maxImageTokens > 2048) {
            errors.add("Max image tokens must be between 32 and 2048")
        }
        
        // 圖像解析度與性能警告
        if (params.imageResolution == ImageResolution.HIGH && !params.enableImageAnalysis) {
            warnings.add("High resolution mode recommends enabling image analysis for best results")
        }
    }

    private fun validateASRParameters(params: ASRParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Beam Size驗證
        if (params.beamSize < 1 || params.beamSize > 20) {
            errors.add("Beam size must be between 1 and 20")
        }
        
        // VAD Threshold驗證
        if (params.vadThreshold < 0f || params.vadThreshold > 1f) {
            errors.add("VAD threshold must be between 0.0 and 1.0")
        }
        
        // 語言模型驗證
        if (params.languageModel.isBlank()) {
            errors.add("Language model cannot be empty")
        }
        
        val supportedLanguages = listOf("zh-TW", "zh-CN", "en-US", "ja-JP")
        if (params.languageModel.isNotBlank() && params.languageModel !in supportedLanguages) {
            errors.add("Unsupported language model: ${params.languageModel}")
        }
    }

    private fun validateTTSParameters(params: TTSParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Speed Rate驗證
        if (params.speedRate < 0.5f || params.speedRate > 2f) {
            errors.add("Speed rate must be between 0.5 and 2.0")
        }
        
        // Volume驗證
        if (params.volume < 0f || params.volume > 1f) {
            errors.add("Volume must be between 0.0 and 1.0")
        }
        
        // Pitch驗證
        if (params.pitch < 0.5f || params.pitch > 2f) {
            errors.add("Pitch must be between 0.5 and 2.0")
        }
        
        // Speaker ID驗證
        if (params.speakerId < 0 || params.speakerId > 9) {
            errors.add("Speaker ID must be between 0 and 9")
        }
    }

    private fun validateGeneralParameters(params: GeneralParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Max Concurrent Tasks驗證
        if (params.maxConcurrentTasks < 1 || params.maxConcurrentTasks > 16) {
            errors.add("Max concurrent tasks must be between 1 and 16")
        } else if (params.maxConcurrentTasks > 12) {
            warnings.add("High concurrent task count detected")
        }
        
        // Timeout驗證
        if (params.timeoutSeconds < 5 || params.timeoutSeconds > 300) {
            errors.add("Timeout must be between 5 and 300 seconds")
        }
        
        // 硬體加速組合驗證
        if (params.enableGPUAcceleration && params.enableNPUAcceleration) {
            warnings.add("Enabling both GPU and NPU acceleration may cause resource contention")
        }
    }
}

/**
 * 驗證結果
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Warning(val warnings: List<String>) : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
    
    val isValid: Boolean
        get() = this is Valid || this is Warning
} 