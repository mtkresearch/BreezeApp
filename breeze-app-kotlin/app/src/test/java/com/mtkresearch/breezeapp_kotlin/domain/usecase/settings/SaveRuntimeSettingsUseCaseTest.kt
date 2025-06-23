package com.mtkresearch.breezeapp_kotlin.domain.usecase.settings

import com.mtkresearch.breezeapp_kotlin.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * SaveRuntimeSettingsUseCase 單元測試（JUnit 5 版）
 * 
 * 測試範圍：
 * - 正常設定保存流程
 * - 驗證與保存的整合
 * - Repository 異常處理
 * - 無效設定的拒絕機制
 */
class SaveRuntimeSettingsUseCaseTest {

    @Mock
    private lateinit var mockRepository: RuntimeSettingsRepository
    
    @Mock
    private lateinit var mockValidateUseCase: ValidateRuntimeSettingsUseCase

    private lateinit var saveUseCase: SaveRuntimeSettingsUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        saveUseCase = SaveRuntimeSettingsUseCase(mockRepository, mockValidateUseCase)
    }

    @Test
    fun `invokeValidSettingsShouldSaveSuccessfully`() = runTest {
        val validSettings = createValidSettings()
        `when`(mockValidateUseCase(validSettings)).thenReturn(ValidationResult.Valid)
        `when`(mockRepository.saveSettings(validSettings)).thenReturn(Result.success(Unit))

        val result = saveUseCase(validSettings)

        assertTrue(result.isSuccess, "Save should succeed")
        verify(mockValidateUseCase).invoke(validSettings)
        verify(mockRepository).saveSettings(validSettings)
    }

    @Test
    fun `invokeInvalidSettingsShouldFailValidation`() = runTest {
        val invalidSettings = createInvalidSettings()
        val validationErrors = listOf("Temperature out of range", "TopK too high")
        `when`(mockValidateUseCase(invalidSettings)).thenReturn(ValidationResult.Invalid(validationErrors))

        val result = saveUseCase(invalidSettings)

        assertTrue(result.isFailure, "Save should fail")
        verify(mockValidateUseCase).invoke(invalidSettings)
        verify(mockRepository, never()).saveSettings(any())
        
        val exception = result.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException, "Should be IllegalArgumentException")
        assertTrue(exception!!.message!!.contains("Temperature out of range"), "Should contain validation errors")
    }

    @Test
    fun `invokeRepositorySaveFailureShouldReturnFailure`() = runTest {
        val validSettings = createValidSettings()
        val saveError = RuntimeException("Disk full")
        `when`(mockValidateUseCase(validSettings)).thenReturn(ValidationResult.Valid)
        `when`(mockRepository.saveSettings(validSettings)).thenReturn(Result.failure(saveError))

        val result = saveUseCase(validSettings)

        assertTrue(result.isFailure, "Save should fail")
        verify(mockValidateUseCase).invoke(validSettings)
        verify(mockRepository).saveSettings(validSettings)
        assertEquals(saveError, result.exceptionOrNull(), "Should return original exception")
    }

    @Test
    fun `invokeSettingsWithWarningsShouldStillSave`() = runTest {
        val warningSettings = createWarningSettings()
        val warnings = listOf("High temperature setting detected")
        `when`(mockValidateUseCase(warningSettings)).thenReturn(ValidationResult.Warning(warnings))
        `when`(mockRepository.saveSettings(warningSettings)).thenReturn(Result.success(Unit))

        val result = saveUseCase(warningSettings)

        assertTrue(result.isSuccess, "Save should succeed with warnings")
        verify(mockValidateUseCase).invoke(warningSettings)
        verify(mockRepository).saveSettings(warningSettings)
    }

    private fun createValidSettings(): RuntimeSettings {
        return RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 0.7f,
                topK = 50,
                topP = 0.9f,
                maxTokens = 2048,
                enableStreaming = true
            ),
            vlmParams = VLMParameters(
                visionTemperature = 0.7f,
                imageResolution = ImageResolution.MEDIUM,
                enableImageAnalysis = true
            ),
            asrParams = ASRParameters(
                languageModel = "zh-TW",
                beamSize = 4,
                enableNoiseSuppression = true
            ),
            ttsParams = TTSParameters(
                speakerId = 0,
                speedRate = 1.0f,
                volume = 0.8f
            ),
            generalParams = GeneralParameters(
                enableGPUAcceleration = true,
                enableNPUAcceleration = false,
                maxConcurrentTasks = 2,
                enableDebugLogging = false
            )
        )
    }

    private fun createInvalidSettings(): RuntimeSettings {
        return RuntimeSettings(
            llmParams = LLMParameters(
                temperature = -0.5f,  // Invalid
                topK = 150,          // Invalid
                topP = 0.9f,
                maxTokens = 2048,
                enableStreaming = true
            )
        )
    }

    private fun createWarningSettings(): RuntimeSettings {
        return RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 1.8f,  // High but valid
                topK = 5,
                topP = 0.9f,
                maxTokens = 2048,
                enableStreaming = true
            ),
            generalParams = GeneralParameters(
                maxConcurrentTasks = 8  // High but valid
            )
        )
    }
} 