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
            errors.add("Temperature必須在0.0-2.0範圍內")
        }
        
        // Top-K驗證
        if (params.topK < 1 || params.topK > 200) {
            errors.add("Top-K必須在1-200範圍內")
        }
        
        // Top-P驗證
        if (params.topP < 0f || params.topP > 1f) {
            errors.add("Top-P必須在0.0-1.0範圍內")
        }
        
        // Max Tokens驗證
        if (params.maxTokens < 1 || params.maxTokens > 8192) {
            errors.add("Max Tokens必須在1-8192範圍內")
        }
        
        // 參數組合驗證
        if (params.temperature > 1.5f && params.topP < 0.1f) {
            warnings.add("高Temperature配合低Top-P可能產生不穩定的輸出")
        }
    }

    private fun validateVLMParameters(params: VLMParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Vision Temperature驗證
        if (params.visionTemperature < 0f || params.visionTemperature > 2f) {
            errors.add("視覺溫度必須在0.0-2.0範圍內")
        }
        
        // Max Image Tokens驗證
        if (params.maxImageTokens < 1 || params.maxImageTokens > 4096) {
            errors.add("最大圖像Token數必須在1-4096範圍內")
        }
        
        // 圖像解析度與性能警告
        if (params.imageResolution == ImageResolution.HIGH && !params.enableImageAnalysis) {
            warnings.add("高解析度模式下建議啟用圖像分析以獲得最佳效果")
        }
    }

    private fun validateASRParameters(params: ASRParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Beam Size驗證
        if (params.beamSize < 1 || params.beamSize > 20) {
            errors.add("Beam大小必須在1-20範圍內")
        }
        
        // VAD Threshold驗證
        if (params.vadThreshold < 0f || params.vadThreshold > 1f) {
            errors.add("VAD閾值必須在0.0-1.0範圍內")
        }
        
        // 語言模型驗證
        val supportedLanguages = listOf("zh-TW", "zh-CN", "en-US", "ja-JP")
        if (params.languageModel !in supportedLanguages) {
            errors.add("不支援的語言模型: ${params.languageModel}")
        }
    }

    private fun validateTTSParameters(params: TTSParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Speed Rate驗證
        if (params.speedRate < 0.1f || params.speedRate > 3f) {
            errors.add("語音速度必須在0.1-3.0範圍內")
        }
        
        // Volume驗證
        if (params.volume < 0f || params.volume > 1f) {
            errors.add("音量必須在0.0-1.0範圍內")
        }
        
        // Speaker ID驗證
        if (params.speakerId < 0 || params.speakerId > 10) {
            errors.add("說話者ID必須在0-10範圍內")
        }
    }

    private fun validateGeneralParameters(params: GeneralParameters, errors: MutableList<String>, warnings: MutableList<String>) {
        // Max Concurrent Tasks驗證
        if (params.maxConcurrentTasks < 1 || params.maxConcurrentTasks > 16) {
            errors.add("最大並發任務數必須在1-16範圍內")
        }
        
        // 硬體加速組合驗證
        if (params.enableGPUAcceleration && params.enableNPUAcceleration) {
            warnings.add("同時啟用GPU和NPU加速可能導致資源競爭")
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