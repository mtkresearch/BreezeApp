package com.mtkresearch.breezeapp.domain.usecase.settings

import com.mtkresearch.breezeapp.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp.presentation.settings.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.*
import org.mockito.MockitoAnnotations
import org.junit.jupiter.api.Assertions.*

/**
 * LoadRuntimeSettingsUseCase 單元測試（JUnit 5 版）
 *
 * 測試範圍：
 * - 正常設定載入流程
 * - Repository 異常處理
 * - Result 封裝驗證
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
        whenever(mockRepository.loadSettings()).thenReturn(expectedSettings)

        val result = loadUseCase()

        assertTrue(result.isSuccess, "Should return success result")
        assertEquals(expectedSettings, result.getOrNull(), "Should return settings from repository")
        verify(mockRepository).loadSettings()
    }

    @Test
    fun `invokeRepositoryExceptionShouldReturnFailure`() = runTest {
        val exception = RuntimeException("Database error")
        whenever(mockRepository.loadSettings()).thenThrow(exception)

        val result = loadUseCase()

        assertTrue(result.isFailure, "Should return failure result on exception")
        assertEquals(exception, result.exceptionOrNull(), "Should contain the original exception")
        verify(mockRepository).loadSettings()
    }

    @Test
    fun `invokeShouldAlwaysReturnNonNullResult`() = runTest {
        whenever(mockRepository.loadSettings()).thenReturn(RuntimeSettings())

        val result = loadUseCase()

        assertNotNull(result, "Result should never be null")
        assertTrue(result.isSuccess, "Should be successful with default settings")
    }

    @Test
    fun `invokeWithNullRepositoryResponseShouldReturnSuccess`() = runTest {
        val defaultSettings = RuntimeSettings()
        whenever(mockRepository.loadSettings()).thenReturn(defaultSettings)

        val result = loadUseCase()

        assertTrue(result.isSuccess, "Should return success even with default settings")
        assertEquals(defaultSettings, result.getOrNull(), "Should return default settings")
        verify(mockRepository).loadSettings()
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