package com.mtkresearch.breezeapp.domain.usecase.settings

import com.mtkresearch.breezeapp.presentation.settings.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * ValidateRuntimeSettingsUseCase 單元測試（JUnit 5 版）
 * 
 * 測試範圍：
 * - LLM參數驗證（邊界值、有效範圍）
 * - VLM參數驗證（解析度、溫度）
 * - ASR參數驗證（語言模型、Beam大小）
 * - TTS參數驗證（語速、音量、音調）
 * - 通用參數驗證（並發任務數）
 * - 組合驗證場景
 */
class ValidateRuntimeSettingsUseCaseTest {

    private lateinit var validateUseCase: ValidateRuntimeSettingsUseCase

    @BeforeEach
    fun setUp() {
        validateUseCase = ValidateRuntimeSettingsUseCase()
    }

    // ========== LLM參數驗證測試 ==========

    @Test
    fun `validate LLM parameters - valid range should return Valid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 0.7f,
                topK = 50,
                topP = 0.9f,
                maxTokens = 2048
            )
        )

        val result = validateUseCase(settings)

        assertTrue(result.isValid, "Valid LLM parameters should be accepted")
        assertTrue(result is ValidationResult.Valid, "Result should be Valid")
    }

    @Test
    fun `validate LLM temperature - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(temperature = -0.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid temperature should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Temperature") && it.contains("0.0") }, 
            "Should contain temperature error")
    }

    @Test
    fun `validate LLM temperature - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(temperature = 2.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid temperature should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Temperature") && it.contains("2.0") }, 
            "Should contain temperature error")
    }

    @Test
    fun `validate LLM topK - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(topK = 0)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid topK should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Top-K") && it.contains("1") }, 
            "Should contain TopK error")
    }

    @Test
    fun `validate LLM topK - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(topK = 101)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid topK should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Top-K") && it.contains("100") }, 
            "Should contain TopK error")
    }

    @Test
    fun `validate LLM topP - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(topP = -0.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid topP should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Top-P") && it.contains("0.0") }, 
            "Should contain TopP error")
    }

    @Test
    fun `validate LLM topP - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(topP = 1.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid topP should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Top-P") && it.contains("1.0") }, 
            "Should contain TopP error")
    }

    @Test
    fun `validate LLM maxTokens - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(maxTokens = 9)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid maxTokens should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Max tokens") && it.contains("10") }, 
            "Should contain maxTokens error")
    }

    @Test
    fun `validate LLM maxTokens - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(maxTokens = 8193)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid maxTokens should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Max tokens") && it.contains("8192") }, 
            "Should contain maxTokens error")
    }

    // ========== VLM參數驗證測試 ==========

    @Test
    fun `validate VLM visionTemperature - valid range should return Valid`() {
        val settings = RuntimeSettings(
            vlmParams = VLMParameters(visionTemperature = 0.5f)
        )

        val result = validateUseCase(settings)

        assertTrue(result.isValid, "Valid VLM parameters should be accepted")
    }

    @Test
    fun `validate VLM visionTemperature - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            vlmParams = VLMParameters(visionTemperature = -0.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid vision temperature should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Vision temperature") && it.contains("0.0") }, 
            "Should contain vision temperature error")
    }

    @Test
    fun `validate VLM visionTemperature - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            vlmParams = VLMParameters(visionTemperature = 2.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid vision temperature should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Vision temperature") && it.contains("2.0") }, 
            "Should contain vision temperature error")
    }

    @Test
    fun `validate VLM maxImageTokens - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            vlmParams = VLMParameters(maxImageTokens = 31)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid max image tokens should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Max image tokens") && it.contains("32") }, 
            "Should contain max image tokens error")
    }

    @Test
    fun `validate VLM maxImageTokens - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            vlmParams = VLMParameters(maxImageTokens = 2049)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid max image tokens should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Max image tokens") && it.contains("2048") }, 
            "Should contain max image tokens error")
    }

    // ========== ASR參數驗證測試 ==========

    @Test
    fun `validate ASR beamSize - valid range should return Valid`() {
        val settings = RuntimeSettings(
            asrParams = ASRParameters(beamSize = 5)
        )

        val result = validateUseCase(settings)

        assertTrue(result.isValid, "Valid ASR parameters should be accepted")
    }

    @Test
    fun `validate ASR beamSize - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            asrParams = ASRParameters(beamSize = 0)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid beam size should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Beam size") && it.contains("1") }, 
            "Should contain beam size error")
    }

    @Test
    fun `validate ASR beamSize - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            asrParams = ASRParameters(beamSize = 21)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid beam size should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Beam size") && it.contains("20") }, 
            "Should contain beam size error")
    }

    @Test
    fun `validate ASR languageModel - empty string should return Invalid`() {
        val settings = RuntimeSettings(
            asrParams = ASRParameters(languageModel = "")
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Empty language model should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Language model") }, 
            "Should contain language model error")
    }

    // ========== TTS參數驗證測試 ==========

    @Test
    fun `validate TTS speedRate - valid range should return Valid`() {
        val settings = RuntimeSettings(
            ttsParams = TTSParameters(speedRate = 1.0f)
        )

        val result = validateUseCase(settings)

        assertTrue(result.isValid, "Valid TTS parameters should be accepted")
    }

    @Test
    fun `validate TTS speedRate - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            ttsParams = TTSParameters(speedRate = 0.4f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid speed rate should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Speed rate") && it.contains("0.5") }, 
            "Should contain speed rate error")
    }

    @Test
    fun `validate TTS speedRate - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            ttsParams = TTSParameters(speedRate = 2.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid speed rate should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Speed rate") && it.contains("2.0") }, 
            "Should contain speed rate error")
    }

    @Test
    fun `validate TTS volume - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            ttsParams = TTSParameters(volume = -0.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid volume should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Volume") && it.contains("0.0") }, 
            "Should contain volume error")
    }

    @Test
    fun `validate TTS volume - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            ttsParams = TTSParameters(volume = 1.1f)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid volume should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Volume") && it.contains("1.0") }, 
            "Should contain volume error")
    }

    @Test
    fun `validate TTS speakerId - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            ttsParams = TTSParameters(speakerId = -1)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid speaker ID should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Speaker ID") && it.contains("0") }, 
            "Should contain speaker ID error")
    }

    @Test
    fun `validate TTS speakerId - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            ttsParams = TTSParameters(speakerId = 10)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid speaker ID should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Speaker ID") && it.contains("9") }, 
            "Should contain speaker ID error")
    }

    // ========== 通用參數驗證測試 ==========

    @Test
    fun `validate General maxConcurrentTasks - valid range should return Valid`() {
        val settings = RuntimeSettings(
            generalParams = GeneralParameters(maxConcurrentTasks = 4)
        )

        val result = validateUseCase(settings)

        assertTrue(result.isValid, "Valid General parameters should be accepted")
    }

    @Test
    fun `validate General maxConcurrentTasks - below minimum should return Invalid`() {
        val settings = RuntimeSettings(
            generalParams = GeneralParameters(maxConcurrentTasks = 0)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid max concurrent tasks should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Max concurrent tasks") && it.contains("1") }, 
            "Should contain max concurrent tasks error")
    }

    @Test
    fun `validate General maxConcurrentTasks - above maximum should return Invalid`() {
        val settings = RuntimeSettings(
            generalParams = GeneralParameters(maxConcurrentTasks = 17)
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Invalid max concurrent tasks should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Max concurrent tasks") && it.contains("16") }, 
            "Should contain max concurrent tasks error")
    }

    // ========== 組合驗證場景測試 ==========

    @Test
    fun `validate multiple invalid parameters - should return all errors`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(
                temperature = -0.5f,  // Invalid
                topK = 150           // Invalid
            ),
            ttsParams = TTSParameters(
                volume = 1.5f        // Invalid
            )
        )

        val result = validateUseCase(settings)

        assertFalse(result.isValid, "Multiple invalid parameters should be rejected")
        assertTrue(result is ValidationResult.Invalid, "Result should be Invalid")
        val errors = (result as ValidationResult.Invalid).errors
        assertTrue(errors.size >= 3, "Should have multiple errors")
        assertTrue(errors.any { it.contains("Temperature") }, "Should contain temperature error")
        assertTrue(errors.any { it.contains("Top-K") }, "Should contain TopK error")
        assertTrue(errors.any { it.contains("Volume") }, "Should contain volume error")
    }

    @Test
    fun `validate warning conditions - should return Warning with messages`() {
        val settings = RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 1.8f  // High but valid - should trigger warning
            ),
            generalParams = GeneralParameters(
                maxConcurrentTasks = 8  // High but valid - should trigger warning
            )
        )

        val result = validateUseCase(settings)

        assertTrue(result.isValid, "Parameters with warnings should still be valid")
        assertTrue(result is ValidationResult.Warning, "Result should be Warning")
        val warnings = (result as ValidationResult.Warning).warnings
        assertTrue(warnings.isNotEmpty(), "Should have warning messages")
    }

    @Test
    fun `validate default settings - should always be valid`() {
        val defaultSettings = RuntimeSettings()

        val result = validateUseCase(defaultSettings)

        assertTrue(result.isValid, "Default settings should always be valid")
        assertTrue(result is ValidationResult.Valid, "Result should be Valid")
    }

    @Test
    fun `validate performance-oriented settings - should be valid`() {
        val performanceSettings = RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 0.1f,  // Low for consistency
                topK = 1,           // Very focused
                maxTokens = 512     // Short responses
            ),
            generalParams = GeneralParameters(
                enableGPUAcceleration = true,
                enableNPUAcceleration = true,
                maxConcurrentTasks = 1  // Single task for max performance
            )
        )

        val result = validateUseCase(performanceSettings)

        assertTrue(result.isValid, "Performance-oriented settings should be valid")
    }

    @Test
    fun `validate creative-oriented settings - should be valid`() {
        val creativeSettings = RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 1.8f,  // High for creativity
                topK = 100,         // Wide sampling
                topP = 0.95f        // High diversity
            ),
            vlmParams = VLMParameters(
                visionTemperature = 1.5f,  // High for creative image analysis
                enableImageAnalysis = true
            )
        )

        val result = validateUseCase(creativeSettings)

        assertTrue(result.isValid, "Creative-oriented settings should be valid")
        // May have warnings but should still be valid
    }
} 