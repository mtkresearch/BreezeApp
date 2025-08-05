package com.mtkresearch.breezeapp.presentation.settings.viewmodel

import androidx.lifecycle.Observer
import com.mtkresearch.breezeapp.domain.usecase.settings.*
import com.mtkresearch.breezeapp.presentation.settings.model.*
import com.mtkresearch.breezeapp.util.InstantExecutorExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import org.robolectric.annotation.Config

/**
 * RuntimeSettingsViewModel 單元測試（JUnit 5 版）
 * 
 * 測試範圍：
 * - ViewModel 生命週期管理
 * - 設定載入和保存業務流程
 * - 參數更新和驗證邏輯
 * - 錯誤處理和用戶反饋
 * - 分類切換和變更追蹤
 * - Use Case 整合
 */
@ExperimentalCoroutinesApi
@Config(manifest = Config.NONE)
@ExtendWith(InstantExecutorExtension::class)
class RuntimeSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var mockLoadUseCase: LoadRuntimeSettingsUseCase
    
    @Mock
    private lateinit var mockSaveUseCase: SaveRuntimeSettingsUseCase
    
    @Mock
    private lateinit var mockUpdateUseCase: UpdateRuntimeParameterUseCase
    
    @Mock
    private lateinit var mockValidateUseCase: ValidateRuntimeSettingsUseCase
    
    @Mock
    private lateinit var mockCurrentSettingsObserver: Observer<RuntimeSettings>
    
    @Mock
    private lateinit var mockPreviewSettingsObserver: Observer<RuntimeSettings>

    private lateinit var viewModel: RuntimeSettingsViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        
        // Common mock behaviors
        whenever(mockValidateUseCase(any<RuntimeSettings>())).thenReturn(ValidationResult.Valid)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== 初始化測試 ==========

    @Test
    fun `viewModel initialization - should load settings and set initial state`() = runTest {
        // Arrange
        whenever(mockLoadUseCase()).thenReturn(Result.success(RuntimeSettings()))

        // Act
        viewModel = createViewModel()
        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        viewModel.previewSettings.observeForever(mockPreviewSettingsObserver)
        advanceUntilIdle()

        // Assert
        verify(mockLoadUseCase).invoke()
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(any())
        verify(mockPreviewSettingsObserver, atLeastOnce()).onChanged(any())
    }

    @Test
    fun `viewModel initialization - load failure should handle gracefully`() = runTest {
        // Arrange
        val testException = RuntimeException("Load failed")
        whenever(mockLoadUseCase()).thenReturn(Result.failure(testException))

        // Act
        viewModel = createViewModel()
        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        advanceUntilIdle()
        
        // Assert
        verify(mockLoadUseCase).invoke()
        // The init call should trigger a load, which fails, but the LiveData should still be initialized with default settings.
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(any())
    }

    // ========== 設定載入測試 ==========

    @Test
    fun `loadSettings - successful load should update state correctly`() = runTest {
        // Arrange
        val testSettings = createTestSettings()
        whenever(mockLoadUseCase()).thenReturn(Result.success(testSettings))

        // Act
        viewModel = createViewModel()
        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        viewModel.previewSettings.observeForever(mockPreviewSettingsObserver)
        advanceUntilIdle()

        // Assert
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(testSettings)
        verify(mockPreviewSettingsObserver, atLeastOnce()).onChanged(testSettings)
    }

    @Test
    fun `loadSettings - failure should handle gracefully and use defaults`() = runTest {
        // Arrange
        val exception = RuntimeException("Database error")
        whenever(mockLoadUseCase()).thenReturn(Result.failure(exception))

        // Act
        viewModel = createViewModel()
        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        advanceUntilIdle()

        // Assert
        verify(mockLoadUseCase).invoke()
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(any())
    }

    @Test
    fun `loadSettings - multiple calls should not cause issues`() = runTest {
        // Arrange
        val testSettings = createTestSettings()
        whenever(mockLoadUseCase()).thenReturn(Result.success(testSettings))

        // Act
        viewModel = createViewModel()
        viewModel.loadSettings()
        viewModel.loadSettings()
        advanceUntilIdle()
        
        // Assert
        // The ViewModel is expected to prevent concurrent/redundant loads.
        // The first load is in init(), subsequent explicit calls should be handled gracefully.
        // We expect invoke() to be called at least once from init.
        verify(mockLoadUseCase, atLeastOnce()).invoke()
    }

    // ========== 設定保存測試 ==========

    @Test
    fun `saveSettings - successful save should update current settings`() = runTest {
        // Arrange
        val originalSettings = RuntimeSettings()
        val modifiedSettings = originalSettings.copy(
            llmParams = originalSettings.llmParams.copy(temperature = 1.1f)
        )

        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any(), any())).thenReturn(Result.success(modifiedSettings))
        whenever(mockSaveUseCase(any())).thenReturn(Result.success(Unit))

        viewModel = createViewModel()
        advanceUntilIdle() // Ensure init load is complete

        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        viewModel.previewSettings.observeForever(mockPreviewSettingsObserver)
        
        // Act
        viewModel.updateLLMTemperature(1.1f)
        advanceUntilIdle() // Ensure update is complete

        viewModel.saveSettings()
        advanceUntilIdle() // Ensure save is complete
        
        // Assert
        verify(mockSaveUseCase).invoke(modifiedSettings)
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(modifiedSettings)
    }

    @Test
    fun `saveSettings - save failure should handle gracefully`() = runTest {
        // Arrange
        val originalSettings = createTestSettings()
        val modifiedSettings = originalSettings.copy(
            llmParams = originalSettings.llmParams.copy(temperature = 99.0f) // A change to make it savable
        )
        val exception = RuntimeException("Save failed")
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any(), any())).thenReturn(Result.success(modifiedSettings))
        whenever(mockSaveUseCase(any())).thenReturn(Result.failure(exception))

        viewModel = createViewModel()
        advanceUntilIdle() // Ensure init load is complete

        // Act
        viewModel.updateLLMTemperature(99.0f)
        advanceUntilIdle()
        
        viewModel.saveSettings()
        advanceUntilIdle()

        // Assert
        verify(mockSaveUseCase).invoke(modifiedSettings)
    }

    // ========== 參數更新測試 ==========

    @Test
    fun `updateLLMTemperature - valid value should update preview settings`() = runTest {
        // Arrange
        val originalSettings = RuntimeSettings()
        val updatedSettings = originalSettings.copy(
            llmParams = originalSettings.llmParams.copy(temperature = 1.2f)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any(), any())).thenReturn(Result.success(updatedSettings))
        viewModel = createViewModel()
        advanceUntilIdle()
        
        // Act
        viewModel.updateLLMTemperature(1.2f)
        
        // Assert
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateLLMTemperature - invalid value should handle gracefully`() = runTest {
        // Arrange
        val originalSettings = RuntimeSettings()
        val exception = IllegalArgumentException("Invalid temperature")
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any(), any())).thenReturn(Result.failure(exception))
        viewModel = createViewModel()
        advanceUntilIdle()
        
        // Act
        viewModel.updateLLMTemperature(-0.5f) // Invalid value
        
        // Assert
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateLLMTopK - valid value should update correctly`() = runTest {
        // Arrange
        val originalSettings = RuntimeSettings()
        val updatedSettings = originalSettings.copy(
            llmParams = originalSettings.llmParams.copy(topK = 25)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any(), any())).thenReturn(Result.success(updatedSettings))
        viewModel = createViewModel()
        advanceUntilIdle()
        
        // Act
        viewModel.updateLLMTopK(25)
        
        // Assert
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateLLMMaxTokens - valid value should update correctly`() = runTest {
        // Arrange
        val originalSettings = RuntimeSettings()
        val updatedSettings = originalSettings.copy(
            llmParams = originalSettings.llmParams.copy(maxTokens = 1024)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any(), any())).thenReturn(Result.success(updatedSettings))
        viewModel = createViewModel()
        advanceUntilIdle()

        // Act
        viewModel.updateLLMMaxTokens(1024)
        
        // Assert
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateVLMVisionTemperature - should update temperature correctly`() = runTest {
        // Arrange
        val originalSettings = RuntimeSettings()
        val updatedSettings = originalSettings.copy(
            vlmParams = originalSettings.vlmParams.copy(visionTemperature = 0.8f)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any(), any())).thenReturn(Result.success(updatedSettings))
        viewModel = createViewModel()
        advanceUntilIdle()

        // Act
        viewModel.updateVLMVisionTemperature(0.8f)
        
        // Assert
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateVLMImageResolution - should update resolution correctly`() = runTest {
        // Arrange
        val originalSettings = RuntimeSettings()
        val updatedSettings = originalSettings.copy(
            vlmParams = originalSettings.vlmParams.copy(imageResolution = ImageResolution.HIGH)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any(), any())).thenReturn(Result.success(updatedSettings))
        viewModel = createViewModel()
        advanceUntilIdle()
        
        // Act
        viewModel.updateVLMImageResolution(2) // HIGH resolution index
        
        // Assert
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    // ========== Helper Methods ==========

    private fun createViewModel(): RuntimeSettingsViewModel {
        return RuntimeSettingsViewModel(
            mockLoadUseCase,
            mockSaveUseCase,
            mockUpdateUseCase,
            mockValidateUseCase
        )
    }

    private fun createTestSettings(): RuntimeSettings {
        return RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 1.0f,
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
                speakerId = 2,
                speedRate = 1.3f,
                volume = 0.9f
            ),
            generalParams = GeneralParameters(
                enableGPUAcceleration = false,
                enableNPUAcceleration = true,
                maxConcurrentTasks = 3,
                enableDebugLogging = true
            )
        )
    }
} 