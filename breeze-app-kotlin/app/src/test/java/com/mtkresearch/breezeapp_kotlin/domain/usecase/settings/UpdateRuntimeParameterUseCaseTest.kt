package com.mtkresearch.breezeapp_kotlin.domain.usecase.settings

import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * UpdateRuntimeParameterUseCase 單元測試（JUnit 5 版）
 * 
 * 測試範圍：
 * - 各類型參數更新邏輯
 * - 參數驗證與邊界檢查
 * - 錯誤處理和例外狀況
 * - 設定不變性確保
 * - 類型安全檢查
 */
class UpdateRuntimeParameterUseCaseTest {

    @Mock
    private lateinit var mockValidateUseCase: ValidateRuntimeSettingsUseCase
    
    private lateinit var updateUseCase: UpdateRuntimeParameterUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // Setup default mock behavior - return Valid for most cases
        `when`(mockValidateUseCase(any())).thenReturn(ValidationResult.Valid)
        updateUseCase = UpdateRuntimeParameterUseCase(mockValidateUseCase)
    }

    // ========== LLM 參數更新測試 ==========

    @Test
    fun `update LLM temperature - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.LLM.Temperature(1.2f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(1.2f, updatedSettings.llmParams.temperature, "Temperature should be updated")
    }

    @Test
    fun `update LLM temperature - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.LLM.Temperature(-0.5f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        val exception = result.exceptionOrNull()
        assertNotNull(exception, "Should have exception")
        assertTrue(exception is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update LLM topK - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.LLM.TopK(25)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(25, updatedSettings.llmParams.topK, "TopK should be updated")
    }

    @Test
    fun `update LLM topK - boundary values should be handled correctly`() {
        val originalSettings = RuntimeSettings()

        // Test minimum boundary
        val updateMin = ParameterUpdate.LLM.TopK(1)
        val resultMin = updateUseCase(originalSettings, updateMin)
        assertTrue(resultMin.isSuccess, "Minimum TopK should succeed")
        assertEquals(1, resultMin.getOrNull()!!.llmParams.topK, "TopK should be 1")

        // Test maximum boundary
        val updateMax = ParameterUpdate.LLM.TopK(100)
        val resultMax = updateUseCase(originalSettings, updateMax)
        assertTrue(resultMax.isSuccess, "Maximum TopK should succeed")
        assertEquals(100, resultMax.getOrNull()!!.llmParams.topK, "TopK should be 100")

        // Test invalid value
        val updateInvalid = ParameterUpdate.LLM.TopK(150)
        val resultInvalid = updateUseCase(originalSettings, updateInvalid)
        assertTrue(resultInvalid.isFailure, "Invalid TopK should fail")
    }

    @Test
    fun `update LLM topP - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.LLM.TopP(0.85f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(0.85f, updatedSettings.llmParams.topP, "TopP should be updated")
    }

    @Test
    fun `update LLM topP - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.LLM.TopP(1.5f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update LLM maxTokens - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.LLM.MaxTokens(1024)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(1024, updatedSettings.llmParams.maxTokens, "MaxTokens should be updated")
    }

    @Test
    fun `update LLM maxTokens - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.LLM.MaxTokens(10000)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update LLM enableStreaming - should toggle correctly`() {
        val originalSettings = RuntimeSettings(
            llmParams = LLMParameters(enableStreaming = true)
        )
        val update = ParameterUpdate.LLM.EnableStreaming(false)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertFalse(updatedSettings.llmParams.enableStreaming, "Streaming should be disabled")
    }

    // ========== VLM 參數更新測試 ==========

    @Test
    fun `update VLM visionTemperature - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.VLM.VisionTemperature(0.8f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(0.8f, updatedSettings.vlmParams.visionTemperature, "Vision temperature should be updated")
    }

    @Test
    fun `update VLM visionTemperature - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.VLM.VisionTemperature(-0.5f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update VLM enableImageAnalysis - should toggle correctly`() {
        val originalSettings = RuntimeSettings(
            vlmParams = VLMParameters(enableImageAnalysis = true)
        )
        val update = ParameterUpdate.VLM.EnableImageAnalysis(false)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertFalse(updatedSettings.vlmParams.enableImageAnalysis, "Image analysis should be disabled")
    }

    @Test
    fun `update VLM maxImageTokens - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.VLM.MaxImageTokens(768)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(768, updatedSettings.vlmParams.maxImageTokens, "Max image tokens should be updated")
    }

    @Test
    fun `update VLM maxImageTokens - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.VLM.MaxImageTokens(3000)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update VLM imageResolution - should update correctly`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.VLM.ImageResolution(ImageResolution.HIGH)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(ImageResolution.HIGH, updatedSettings.vlmParams.imageResolution, "Image resolution should be HIGH")
    }

    // ========== ASR 參數更新測試 ==========

    @Test
    fun `update ASR languageModel - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.ASR.LanguageModel("en-US")

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals("en-US", updatedSettings.asrParams.languageModel, "Language model should be updated")
    }

    @Test
    fun `update ASR languageModel - empty value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.ASR.LanguageModel("")

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update ASR beamSize - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.ASR.BeamSize(8)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(8, updatedSettings.asrParams.beamSize, "Beam size should be updated")
    }

    @Test
    fun `update ASR beamSize - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.ASR.BeamSize(25)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update ASR enableNoiseSuppression - should toggle correctly`() {
        val originalSettings = RuntimeSettings(
            asrParams = ASRParameters(enableNoiseSuppression = true)
        )
        val update = ParameterUpdate.ASR.EnableNoiseSuppression(false)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertFalse(updatedSettings.asrParams.enableNoiseSuppression, "Noise suppression should be disabled")
    }

    // ========== TTS 參數更新測試 ==========

    @Test
    fun `update TTS speakerId - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.TTS.SpeakerId(3)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(3, updatedSettings.ttsParams.speakerId, "Speaker ID should be updated")
    }

    @Test
    fun `update TTS speakerId - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.TTS.SpeakerId(15)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update TTS speedRate - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.TTS.SpeedRate(1.5f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(1.5f, updatedSettings.ttsParams.speedRate, "Speed rate should be updated")
    }

    @Test
    fun `update TTS speedRate - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.TTS.SpeedRate(5.0f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update TTS volume - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.TTS.Volume(0.7f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(0.7f, updatedSettings.ttsParams.volume, "Volume should be updated")
    }

    @Test
    fun `update TTS volume - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.TTS.Volume(1.5f)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    // ========== General 參數更新測試 ==========

    @Test
    fun `update General enableGPUAcceleration - should toggle correctly`() {
        val originalSettings = RuntimeSettings(
            generalParams = GeneralParameters(enableGPUAcceleration = false)
        )
        val update = ParameterUpdate.General.EnableGPUAcceleration(true)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertTrue(updatedSettings.generalParams.enableGPUAcceleration, "GPU acceleration should be enabled")
    }

    @Test
    fun `update General enableNPUAcceleration - should toggle correctly`() {
        val originalSettings = RuntimeSettings(
            generalParams = GeneralParameters(enableNPUAcceleration = false)
        )
        val update = ParameterUpdate.General.EnableNPUAcceleration(true)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertTrue(updatedSettings.generalParams.enableNPUAcceleration, "NPU acceleration should be enabled")
    }

    @Test
    fun `update General maxConcurrentTasks - valid value should succeed`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.General.MaxConcurrentTasks(4)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertEquals(4, updatedSettings.generalParams.maxConcurrentTasks, "Max concurrent tasks should be updated")
    }

    @Test
    fun `update General maxConcurrentTasks - invalid value should fail`() {
        val originalSettings = RuntimeSettings()
        val update = ParameterUpdate.General.MaxConcurrentTasks(20)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        assertTrue(result.exceptionOrNull() is IllegalArgumentException, "Should be IllegalArgumentException")
    }

    @Test
    fun `update General enableDebugLogging - should toggle correctly`() {
        val originalSettings = RuntimeSettings(
            generalParams = GeneralParameters(enableDebugLogging = false)
        )
        val update = ParameterUpdate.General.EnableDebugLogging(true)

        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        assertTrue(updatedSettings.generalParams.enableDebugLogging, "Debug logging should be enabled")
    }

    // ========== 邊界和不變性測試 ==========

    @Test
    fun `update parameter - should preserve other parameters in same category`() {
        val originalSettings = RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 0.7f,
                topK = 50,
                topP = 0.9f,
                maxTokens = 2048,
                enableStreaming = true
            )
        )
        
        val update = ParameterUpdate.LLM.Temperature(1.2f)
        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        
        // Verify only temperature changed, other LLM params preserved
        assertEquals(1.2f, updatedSettings.llmParams.temperature, "Temperature should be updated")
        assertEquals(50, updatedSettings.llmParams.topK, "TopK should remain unchanged")
        assertEquals(0.9f, updatedSettings.llmParams.topP, "TopP should remain unchanged")
        assertEquals(2048, updatedSettings.llmParams.maxTokens, "MaxTokens should remain unchanged")
        assertTrue(updatedSettings.llmParams.enableStreaming, "Streaming should remain enabled")
    }

    @Test
    fun `update parameter - validation failure should preserve original settings`() {
        val originalSettings = RuntimeSettings(
            llmParams = LLMParameters(temperature = 0.7f)
        )
        
        val update = ParameterUpdate.LLM.Temperature(-0.5f) // Invalid value
        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isFailure, "Update should fail")
        // Original settings should remain unchanged since update failed
        assertEquals(0.7f, originalSettings.llmParams.temperature, "Original temperature should be preserved")
    }

    @Test
    fun `update parameter - should preserve other parameter categories`() {
        val originalSettings = RuntimeSettings(
            llmParams = LLMParameters(temperature = 0.7f),
            vlmParams = VLMParameters(visionTemperature = 0.8f),
            asrParams = ASRParameters(beamSize = 4),
            ttsParams = TTSParameters(volume = 0.9f),
            generalParams = GeneralParameters(maxConcurrentTasks = 2)
        )
        
        val update = ParameterUpdate.LLM.Temperature(1.1f)
        val result = updateUseCase(originalSettings, update)

        assertTrue(result.isSuccess, "Update should succeed")
        val updatedSettings = result.getOrNull()!!
        
        // Verify only LLM temperature changed, other categories preserved
        assertEquals(1.1f, updatedSettings.llmParams.temperature, "LLM temperature should be updated")
        assertEquals(0.8f, updatedSettings.vlmParams.visionTemperature, "VLM temperature should be preserved")
        assertEquals(4, updatedSettings.asrParams.beamSize, "ASR beam size should be preserved")
        assertEquals(0.9f, updatedSettings.ttsParams.volume, "TTS volume should be preserved")
        assertEquals(2, updatedSettings.generalParams.maxConcurrentTasks, "General tasks should be preserved")
    }
} 