package com.mtkresearch.breezeapp_kotlin.domain.usecase.settings

import com.mtkresearch.breezeapp_kotlin.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

/**
 * LoadRuntimeSettingsUseCase 單元測試（JUnit 5 版）
 *
 * 測試範圍：
 * - 正常設定載入流程
 * - Repository 異常處理
 * - 預設值回傳機制
 * - 數據完整性驗證
 */
class LoadRuntimeSettingsUseCaseTest {

    @Mock
    private lateinit var mockRepository: RuntimeSettingsRepository

    private lateinit var loadUseCase: LoadRuntimeSettingsUseCase

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        loadUseCase = LoadRuntimeSettingsUseCase(mockRepository)
    }

    @Test
    fun `invokeSuccessfulLoadShouldReturnSettingsFromRepository`() = runTest {
        val expectedSettings = createTestSettings()
        `when`(mockRepository.loadSettings()).thenReturn(expectedSettings)

        val result = loadUseCase()

        assertEquals(expectedSettings, result, "Should return settings from repository")
        verify(mockRepository).loadSettings()
    }

    @Test
    fun `invokeRepositoryExceptionShouldReturnDefaultSettings`() = runTest {
        `when`(mockRepository.loadSettings()).thenThrow(RuntimeException("Database error"))

        val result = loadUseCase()

        assertEquals(RuntimeSettings(), result, "Should return default settings on exception")
        verify(mockRepository).loadSettings()
    }

    @Test
    fun `invokeShouldAlwaysReturnNonNullSettings`() = runTest {
        `when`(mockRepository.loadSettings()).thenReturn(RuntimeSettings())

        val result = loadUseCase()

        assertNotNull(result, "Result should never be null")
    }

    private fun createTestSettings(): RuntimeSettings {
        return RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 1.2f,
                topK = 25,
                topP = 0.85f,
                maxTokens = 1024,
                enableStreaming = false
            ),
            vlmParams = VLMParameters(
                visionTemperature = 0.8f,
                imageResolution = ImageResolution.HIGH,
                enableImageAnalysis = false
            ),
            asrParams = ASRParameters(
                languageModel = "en-US",
                beamSize = 6,
                enableNoiseSuppression = false
            ),
            ttsParams = TTSParameters(
                speakerId = 3,
                speedRate = 1.3f,
                volume = 0.9f
            ),
            generalParams = GeneralParameters(
                enableGPUAcceleration = false,
                enableNPUAcceleration = true,
                maxConcurrentTasks = 4,
                enableDebugLogging = true
            )
        )
    }
}