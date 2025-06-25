package com.mtkresearch.breezeapp_kotlin.presentation.settings.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.*
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.*
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
        
        // Setup default mock behaviors using runTest for suspend functions
        runTest {
            whenever(mockLoadUseCase()).thenReturn(Result.success(RuntimeSettings()))
            whenever(mockSaveUseCase(any<RuntimeSettings>())).thenReturn(Result.success(Unit))
        }
        whenever(mockUpdateUseCase(any<RuntimeSettings>(), any<ParameterUpdate>())).thenReturn(Result.success(RuntimeSettings()))
        whenever(mockValidateUseCase(any<RuntimeSettings>())).thenReturn(ValidationResult.Valid)

        viewModel = RuntimeSettingsViewModel(
            mockLoadUseCase,
            mockSaveUseCase,
            mockUpdateUseCase,
            mockValidateUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== 初始化測試 ==========

    @Test
    fun `viewModel initialization - should load settings and set initial state`() = runTest(testDispatcher) {
        // Observer setup
        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        viewModel.previewSettings.observeForever(mockPreviewSettingsObserver)

        // Trigger initialization
        viewModel.loadSettings()

        // Verify settings were loaded
        verify(mockLoadUseCase).invoke()
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(any())
        verify(mockPreviewSettingsObserver, atLeastOnce()).onChanged(any())
    }

    @Test
    fun `viewModel initialization - load failure should handle gracefully`() = runTest(testDispatcher) {
        val testException = RuntimeException("Load failed")
        whenever(mockLoadUseCase()).thenReturn(Result.failure(testException))

        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        viewModel.loadSettings()

        // Should still set default settings on failure
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(any())
    }

    // ========== 設定載入測試 ==========

    @Test
    fun `loadSettings - successful load should update state correctly`() = runTest(testDispatcher) {
        val testSettings = createTestSettings()
        whenever(mockLoadUseCase()).thenReturn(Result.success(testSettings))

        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        viewModel.previewSettings.observeForever(mockPreviewSettingsObserver)

        viewModel.loadSettings()

        verify(mockCurrentSettingsObserver).onChanged(testSettings)
        verify(mockPreviewSettingsObserver).onChanged(testSettings)
    }

    @Test
    fun `loadSettings - failure should handle gracefully and use defaults`() = runTest(testDispatcher) {
        val exception = RuntimeException("Database error")
        whenever(mockLoadUseCase()).thenReturn(Result.failure(exception))

        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        viewModel.loadSettings()

        // Should receive default settings on failure
        verify(mockCurrentSettingsObserver).onChanged(any())
    }

    @Test
    fun `loadSettings - multiple calls should not cause issues`() = runTest(testDispatcher) {
        val testSettings = createTestSettings()
        whenever(mockLoadUseCase()).thenReturn(Result.success(testSettings))

        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)

        // Load settings multiple times
        viewModel.loadSettings()
        viewModel.loadSettings()
        viewModel.loadSettings()

        // Should handle multiple calls gracefully
        verify(mockLoadUseCase, times(3)).invoke()
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(testSettings)
    }

    // ========== 設定保存測試 ==========

    @Test
    fun `saveSettings - successful save should update current settings`() = runTest(testDispatcher) {
        val originalSettings = RuntimeSettings()
        val modifiedSettings = RuntimeSettings(
            llmParams = LLMParameters(temperature = 1.1f)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockSaveUseCase(modifiedSettings)).thenReturn(Result.success(Unit))
        
        viewModel.loadSettings()
        
        // Manually set preview settings to simulate user changes
        viewModel.previewSettings.observeForever(mockPreviewSettingsObserver)
        viewModel.currentSettings.observeForever(mockCurrentSettingsObserver)
        
        // Simulate parameter update
        whenever(mockUpdateUseCase(any<RuntimeSettings>(), any<ParameterUpdate>())).thenReturn(Result.success(modifiedSettings))
        viewModel.updateLLMTemperature(1.1f)
        
        // Apply changes
        viewModel.saveSettings()
        
        verify(mockSaveUseCase).invoke(modifiedSettings)
        verify(mockCurrentSettingsObserver, atLeastOnce()).onChanged(modifiedSettings)
    }

    @Test
    fun `saveSettings - save failure should handle gracefully`() = runTest(testDispatcher) {
        val testSettings = createTestSettings()
        val exception = RuntimeException("Save failed")
        whenever(mockLoadUseCase()).thenReturn(Result.success(testSettings))
        whenever(mockSaveUseCase(testSettings)).thenReturn(Result.failure(exception))

        viewModel.loadSettings()
        viewModel.saveSettings()

        verify(mockSaveUseCase).invoke(testSettings)
        // Should handle failure without crashing
    }

    // ========== 參數更新測試 ==========

    @Test
    fun `updateLLMTemperature - valid value should update preview settings`() = runTest(testDispatcher) {
        val originalSettings = RuntimeSettings()
        val updatedSettings = RuntimeSettings(
            llmParams = LLMParameters(temperature = 1.2f)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any<RuntimeSettings>(), any<ParameterUpdate>())).thenReturn(Result.success(updatedSettings))
        
        viewModel.loadSettings()
        viewModel.updateLLMTemperature(1.2f)
        
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateLLMTemperature - invalid value should handle gracefully`() = runTest(testDispatcher) {
        val originalSettings = RuntimeSettings()
        val exception = IllegalArgumentException("Invalid temperature")
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any<RuntimeSettings>(), any<ParameterUpdate>())).thenReturn(Result.failure(exception))
        
        viewModel.loadSettings()
        viewModel.updateLLMTemperature(-0.5f) // Invalid value
        
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
        // Should handle error gracefully without crashing
    }

    @Test
    fun `updateLLMTopK - valid value should update correctly`() = runTest(testDispatcher) {
        val originalSettings = RuntimeSettings()
        val updatedSettings = RuntimeSettings(
            llmParams = LLMParameters(topK = 25)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any<RuntimeSettings>(), any<ParameterUpdate>())).thenReturn(Result.success(updatedSettings))
        
        viewModel.loadSettings()
        viewModel.updateLLMTopK(25)
        
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateLLMMaxTokens - valid value should update correctly`() = runTest(testDispatcher) {
        val originalSettings = RuntimeSettings()
        val updatedSettings = RuntimeSettings(
            llmParams = LLMParameters(maxTokens = 1024)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any<RuntimeSettings>(), any<ParameterUpdate>())).thenReturn(Result.success(updatedSettings))
        
        viewModel.loadSettings()
        viewModel.updateLLMMaxTokens(1024)
        
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateVLMVisionTemperature - should update temperature correctly`() = runTest(testDispatcher) {
        val originalSettings = RuntimeSettings()
        val updatedSettings = RuntimeSettings(
            vlmParams = VLMParameters(visionTemperature = 0.8f)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any<RuntimeSettings>(), any<ParameterUpdate>())).thenReturn(Result.success(updatedSettings))
        
        viewModel.loadSettings()
        viewModel.updateVLMVisionTemperature(0.8f)
        
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    @Test
    fun `updateVLMImageResolution - should update resolution correctly`() = runTest(testDispatcher) {
        val originalSettings = RuntimeSettings()
        val updatedSettings = RuntimeSettings(
            vlmParams = VLMParameters(imageResolution = ImageResolution.HIGH)
        )
        
        whenever(mockLoadUseCase()).thenReturn(Result.success(originalSettings))
        whenever(mockUpdateUseCase(any<RuntimeSettings>(), any<ParameterUpdate>())).thenReturn(Result.success(updatedSettings))
        
        viewModel.loadSettings()
        viewModel.updateVLMImageResolution(2) // HIGH resolution index
        
        verify(mockUpdateUseCase).invoke(eq(originalSettings), any())
    }

    // ========== Helper Methods ==========

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