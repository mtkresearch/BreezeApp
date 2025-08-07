package com.mtkresearch.breezeapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.mtkresearch.breezeapp.presentation.settings.model.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * RuntimeSettingsRepository 單元測試（JUnit 5 版）
 * 
 * 測試範圍：
 * - 設定載入功能（正常和異常情況）
 * - 設定保存功能（正常和異常情況）
 * - SharedPreferences 整合
 * - 預設值處理
 * - 數據轉換和映射
 * - 錯誤處理和恢復
 */
class RuntimeSettingsRepositoryTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor

    private lateinit var repository: RuntimeSettingsRepository

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        
        // Setup SharedPreferences mock behavior
        `when`(mockContext.getSharedPreferences("runtime_settings", Context.MODE_PRIVATE))
            .thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putFloat(anyString(), anyFloat())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.apply()).then { }

        repository = RuntimeSettingsRepository(mockContext)
    }

    @Test
    fun `test sample - placeholder for now`() {
        assertTrue(true, "Placeholder test")
    }

    // ========== 載入設定測試 ==========

    @Test
    fun `loadSettingsWithDefaultValuesShouldReturnCorrectSettings`() = runTest {
        // Setup default values in SharedPreferences
        setupDefaultPreferences()

        val settings = repository.loadSettings()

        assertNotNull(settings, "Settings should not be null")
        assertEquals(0.7f, settings.llmParams.temperature, "LLM temperature should be default")
        assertEquals(5, settings.llmParams.topK, "LLM topK should be default")
        assertEquals(0.9f, settings.llmParams.topP, "LLM topP should be default")
        assertEquals(2048, settings.llmParams.maxTokens, "LLM maxTokens should be default")
        assertTrue(settings.llmParams.enableStreaming, "LLM streaming should be enabled by default")
    }

    @Test
    fun `loadSettingsWithCustomValuesShouldReturnCorrectSettings`() = runTest {
        // Setup custom values in SharedPreferences
        setupCustomPreferences()

        val settings = repository.loadSettings()

        assertNotNull(settings, "Settings should not be null")
        assertEquals(1.2f, settings.llmParams.temperature, "LLM temperature should be custom")
        assertEquals(25, settings.llmParams.topK, "LLM topK should be custom")
        assertEquals(0.8f, settings.vlmParams.visionTemperature, "VLM vision temperature should be custom")
        assertEquals(ImageResolution.HIGH, settings.vlmParams.imageResolution, "VLM image resolution should be HIGH")
        assertEquals("en-US", settings.asrParams.languageModel, "ASR language model should be custom")
        assertEquals(8, settings.asrParams.beamSize, "ASR beam size should be custom")
        assertFalse(settings.asrParams.enableNoiseSuppression, "ASR noise suppression should be disabled")
    }

    @Test
    fun `loadSettingsWithAllParameterCategoriesShouldLoadCorrectly`() = runTest {
        // Setup comprehensive preferences
        setupComprehensivePreferences()

        val settings = repository.loadSettings()

        // Verify LLM parameters
        assertEquals(0.5f, settings.llmParams.temperature, "LLM temperature")
        assertEquals(10, settings.llmParams.topK, "LLM topK")
        assertEquals(0.95f, settings.llmParams.topP, "LLM topP")
        assertEquals(1024, settings.llmParams.maxTokens, "LLM maxTokens")
        assertFalse(settings.llmParams.enableStreaming, "LLM streaming")

        // Verify VLM parameters
        assertEquals(0.6f, settings.vlmParams.visionTemperature, "VLM vision temperature")
        assertEquals(ImageResolution.LOW, settings.vlmParams.imageResolution, "VLM image resolution")
        assertFalse(settings.vlmParams.enableImageAnalysis, "VLM image analysis")

        // Verify ASR parameters
        assertEquals("ja-JP", settings.asrParams.languageModel, "ASR language model")
        assertEquals(6, settings.asrParams.beamSize, "ASR beam size")
        assertFalse(settings.asrParams.enableNoiseSuppression, "ASR noise suppression")

        // Verify TTS parameters
        assertEquals(3, settings.ttsParams.speakerId, "TTS speaker ID")
        assertEquals(1.5f, settings.ttsParams.speedRate, "TTS speed rate")
        assertEquals(0.9f, settings.ttsParams.volume, "TTS volume")

        // Verify General parameters
        assertFalse(settings.generalParams.enableGPUAcceleration, "General GPU acceleration")
        assertTrue(settings.generalParams.enableNPUAcceleration, "General NPU acceleration")
        assertEquals(4, settings.generalParams.maxConcurrentTasks, "General max concurrent tasks")
        assertTrue(settings.generalParams.enableDebugLogging, "General debug logging")
    }

    @Test
    fun `loadSettingsWithExceptionShouldReturnDefaultSettings`() = runTest {
        // Setup exception scenario
        `when`(mockSharedPreferences.getFloat(anyString(), anyFloat()))
            .thenThrow(RuntimeException("Test exception"))

        val settings = repository.loadSettings()

        // Should return default settings when exception occurs
        assertNotNull(settings, "Settings should not be null")
        assertEquals(RuntimeSettings(), settings, "Should return default settings")
    }

    @Test
    fun `loadSettingsWithInvalidEnumOrdinalShouldHandleGracefully`() = runTest {
        // Setup invalid enum ordinal
        `when`(mockSharedPreferences.getFloat(anyString(), anyFloat())).thenReturn(0.7f)
        `when`(mockSharedPreferences.getInt(anyString(), anyInt())).thenReturn(5)
        `when`(mockSharedPreferences.getInt("vlm_image_resolution", 1)).thenReturn(99) // Invalid ordinal
        `when`(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(true)
        `when`(mockSharedPreferences.getString(anyString(), anyString())).thenReturn("zh-TW")

        val settings = repository.loadSettings()

        // Should handle invalid enum ordinal gracefully and return default settings
        assertNotNull(settings, "Settings should not be null")
        assertEquals(RuntimeSettings(), settings, "Should return default settings")
    }

    // ========== 保存設定測試 ==========

    @Test
    fun `saveSettingsWithValidSettingsShouldSucceed`() = runTest {
        val testSettings = createTestSettings()

        val result = repository.saveSettings(testSettings)

        assertTrue(result.isSuccess, "Save should succeed")
        
        // Verify all parameters were saved
        verify(mockEditor).putFloat("llm_temperature", 1.1f)
        verify(mockEditor).putInt("llm_top_k", 30)
        verify(mockEditor).putFloat("llm_top_p", 0.85f)
        verify(mockEditor).putInt("llm_max_tokens", 1536)
        verify(mockEditor).putBoolean("llm_streaming", false)
        
        verify(mockEditor).putFloat("vlm_vision_temperature", 0.9f)
        verify(mockEditor).putInt("vlm_image_resolution", ImageResolution.HIGH.ordinal)
        verify(mockEditor).putBoolean("vlm_image_analysis", false)
        
        verify(mockEditor).putString("asr_language_model", "en-US")
        verify(mockEditor).putInt("asr_beam_size", 7)
        verify(mockEditor).putBoolean("asr_noise_suppression", false)
        
        verify(mockEditor).putInt("tts_speaker_id", 2)
        verify(mockEditor).putFloat("tts_speed_rate", 1.3f)
        verify(mockEditor).putFloat("tts_volume", 0.9f)
        
        verify(mockEditor).putBoolean("general_gpu_acceleration", false)
        verify(mockEditor).putBoolean("general_npu_acceleration", true)
        verify(mockEditor).putInt("general_max_concurrent_tasks", 3)
        verify(mockEditor).putBoolean("general_debug_logging", true)
        
        verify(mockEditor).apply()
    }

    @Test
    fun `saveSettingsWithDefaultSettingsShouldSaveCorrectly`() = runTest {
        val defaultSettings = RuntimeSettings()

        val result = repository.saveSettings(defaultSettings)

        assertTrue(result.isSuccess, "Save should succeed")
        
        // Verify default values were saved
        verify(mockEditor).putFloat("llm_temperature", 0.7f)
        verify(mockEditor).putInt("llm_top_k", 5)
        verify(mockEditor).putFloat("llm_top_p", 0.9f)
        verify(mockEditor).putInt("llm_max_tokens", 2048)
        verify(mockEditor).putBoolean("llm_streaming", true)
        verify(mockEditor).apply()
    }

    @Test
    fun `saveSettingsWithEditorExceptionShouldReturnFailure`() = runTest {
        val testSettings = createTestSettings()
        doThrow(RuntimeException("Editor exception")).`when`(mockEditor).apply()

        val result = repository.saveSettings(testSettings)

        assertTrue(result.isFailure, "Save should fail")
        assertNotNull(result.exceptionOrNull(), "Should have exception")
    }

    @Test
    fun `saveSettingsWithSharedPreferencesExceptionShouldReturnFailure`() = runTest {
        val testSettings = createTestSettings()
        `when`(mockSharedPreferences.edit()).thenThrow(RuntimeException("SharedPreferences exception"))

        val result = repository.saveSettings(testSettings)

        assertTrue(result.isFailure, "Save should fail")
        assertNotNull(result.exceptionOrNull(), "Should have exception")
    }

    @Test
    fun `resetToDefaultShouldSaveDefaultSettings`() = runTest {
        val result = repository.resetToDefault()

        assertTrue(result.isSuccess, "Reset should succeed")
        
        // Verify default settings were saved
        val defaultSettings = RuntimeSettings()
        verify(mockEditor).putFloat("llm_temperature", defaultSettings.llmParams.temperature)
        verify(mockEditor).putInt("llm_top_k", defaultSettings.llmParams.topK)
        verify(mockEditor).apply()
    }

    // ========== 整合測試 ==========

    @Test
    fun `saveSettingsThenLoadSettingsShouldMaintainDataIntegrity`() = runTest {
        val originalSettings = createTestSettings()
        
        // Mock save operation
        `when`(mockEditor.apply()).then { }
        
        // Mock load operation to return saved settings
        setupMockPreferencesFromSettings(originalSettings)
        
        // Save then load
        val saveResult = repository.saveSettings(originalSettings)
        val loadedSettings = repository.loadSettings()
        
        assertTrue(saveResult.isSuccess, "Save should succeed")
        assertEquals(originalSettings.llmParams.temperature, loadedSettings.llmParams.temperature, "LLM temperature should match")
        assertEquals(originalSettings.llmParams.topK, loadedSettings.llmParams.topK, "LLM topK should match")
        assertEquals(originalSettings.llmParams.topP, loadedSettings.llmParams.topP, "LLM topP should match")
        assertEquals(originalSettings.llmParams.maxTokens, loadedSettings.llmParams.maxTokens, "LLM maxTokens should match")
        assertEquals(originalSettings.llmParams.enableStreaming, loadedSettings.llmParams.enableStreaming, "LLM streaming should match")
        
        assertEquals(originalSettings.vlmParams.visionTemperature, loadedSettings.vlmParams.visionTemperature, "VLM vision temperature should match")
        assertEquals(originalSettings.vlmParams.imageResolution, loadedSettings.vlmParams.imageResolution, "VLM image resolution should match")
        assertEquals(originalSettings.vlmParams.enableImageAnalysis, loadedSettings.vlmParams.enableImageAnalysis, "VLM image analysis should match")
        
        assertEquals(originalSettings.asrParams.languageModel, loadedSettings.asrParams.languageModel, "ASR language model should match")
        assertEquals(originalSettings.asrParams.beamSize, loadedSettings.asrParams.beamSize, "ASR beam size should match")
        assertEquals(originalSettings.asrParams.enableNoiseSuppression, loadedSettings.asrParams.enableNoiseSuppression, "ASR noise suppression should match")
    }

    @Test
    fun `loadSettingsWithMissingValuesShouldUseDefaults`() = runTest {
        // Setup only some preferences, others should use defaults
        `when`(mockSharedPreferences.getFloat("llm_temperature", 0.7f)).thenReturn(1.2f)
        `when`(mockSharedPreferences.getInt("llm_top_k", 5)).thenReturn(25)
        // Other values will use defaults
        `when`(mockSharedPreferences.getFloat("llm_top_p", 0.9f)).thenReturn(0.9f)
        `when`(mockSharedPreferences.getInt("llm_max_tokens", 2048)).thenReturn(2048)
        `when`(mockSharedPreferences.getBoolean("llm_streaming", true)).thenReturn(true)
        `when`(mockSharedPreferences.getFloat("vlm_vision_temperature", 0.7f)).thenReturn(0.7f)
        `when`(mockSharedPreferences.getInt("vlm_image_resolution", 1)).thenReturn(1)
        `when`(mockSharedPreferences.getBoolean("vlm_image_analysis", true)).thenReturn(true)
        `when`(mockSharedPreferences.getString("asr_language_model", "zh-TW")).thenReturn("zh-TW")
        `when`(mockSharedPreferences.getInt("asr_beam_size", 4)).thenReturn(4)
        `when`(mockSharedPreferences.getBoolean("asr_noise_suppression", true)).thenReturn(true)
        `when`(mockSharedPreferences.getInt("tts_speaker_id", 0)).thenReturn(0)
        `when`(mockSharedPreferences.getFloat("tts_speed_rate", 1.0f)).thenReturn(1.0f)
        `when`(mockSharedPreferences.getFloat("tts_volume", 0.8f)).thenReturn(0.8f)
        `when`(mockSharedPreferences.getBoolean("general_gpu_acceleration", true)).thenReturn(true)
        `when`(mockSharedPreferences.getBoolean("general_npu_acceleration", false)).thenReturn(false)
        `when`(mockSharedPreferences.getInt("general_max_concurrent_tasks", 2)).thenReturn(2)
        `when`(mockSharedPreferences.getBoolean("general_debug_logging", false)).thenReturn(false)

        val settings = repository.loadSettings()

        // Verify overridden values
        assertEquals(1.2f, settings.llmParams.temperature, "Custom temperature should be loaded")
        assertEquals(25, settings.llmParams.topK, "Custom topK should be loaded")
        
        // Verify default values are used for others
        assertEquals(0.9f, settings.llmParams.topP, "Default topP should be used")
        assertEquals(2048, settings.llmParams.maxTokens, "Default maxTokens should be used")
    }

    // ========== Helper Methods ==========

    private fun setupDefaultPreferences() {
        val defaultSettings = RuntimeSettings()
        setupMockPreferencesFromSettings(defaultSettings)
    }

    private fun setupCustomPreferences() {
        `when`(mockSharedPreferences.getFloat("llm_temperature", 0.7f)).thenReturn(1.2f)
        `when`(mockSharedPreferences.getInt("llm_top_k", 5)).thenReturn(25)
        `when`(mockSharedPreferences.getFloat("llm_top_p", 0.9f)).thenReturn(0.8f)
        `when`(mockSharedPreferences.getInt("llm_max_tokens", 2048)).thenReturn(1024)
        `when`(mockSharedPreferences.getBoolean("llm_streaming", true)).thenReturn(false)
        `when`(mockSharedPreferences.getFloat("vlm_vision_temperature", 0.7f)).thenReturn(0.8f)
        `when`(mockSharedPreferences.getInt("vlm_image_resolution", 1)).thenReturn(ImageResolution.HIGH.ordinal)
        `when`(mockSharedPreferences.getBoolean("vlm_image_analysis", true)).thenReturn(false)
        `when`(mockSharedPreferences.getString("asr_language_model", "zh-TW")).thenReturn("en-US")
        `when`(mockSharedPreferences.getInt("asr_beam_size", 4)).thenReturn(8)
        `when`(mockSharedPreferences.getBoolean("asr_noise_suppression", true)).thenReturn(false)
        `when`(mockSharedPreferences.getInt("tts_speaker_id", 0)).thenReturn(2)
        `when`(mockSharedPreferences.getFloat("tts_speed_rate", 1.0f)).thenReturn(1.5f)
        `when`(mockSharedPreferences.getFloat("tts_volume", 0.8f)).thenReturn(0.9f)
        `when`(mockSharedPreferences.getBoolean("general_gpu_acceleration", true)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("general_npu_acceleration", false)).thenReturn(true)
        `when`(mockSharedPreferences.getInt("general_max_concurrent_tasks", 2)).thenReturn(4)
        `when`(mockSharedPreferences.getBoolean("general_debug_logging", false)).thenReturn(true)
    }

    private fun setupComprehensivePreferences() {
        `when`(mockSharedPreferences.getFloat("llm_temperature", 0.7f)).thenReturn(0.5f)
        `when`(mockSharedPreferences.getInt("llm_top_k", 5)).thenReturn(10)
        `when`(mockSharedPreferences.getFloat("llm_top_p", 0.9f)).thenReturn(0.95f)
        `when`(mockSharedPreferences.getInt("llm_max_tokens", 2048)).thenReturn(1024)
        `when`(mockSharedPreferences.getBoolean("llm_streaming", true)).thenReturn(false)
        `when`(mockSharedPreferences.getFloat("vlm_vision_temperature", 0.7f)).thenReturn(0.6f)
        `when`(mockSharedPreferences.getInt("vlm_image_resolution", 1)).thenReturn(ImageResolution.LOW.ordinal)
        `when`(mockSharedPreferences.getBoolean("vlm_image_analysis", true)).thenReturn(false)
        `when`(mockSharedPreferences.getString("asr_language_model", "zh-TW")).thenReturn("ja-JP")
        `when`(mockSharedPreferences.getInt("asr_beam_size", 4)).thenReturn(6)
        `when`(mockSharedPreferences.getBoolean("asr_noise_suppression", true)).thenReturn(false)
        `when`(mockSharedPreferences.getInt("tts_speaker_id", 0)).thenReturn(3)
        `when`(mockSharedPreferences.getFloat("tts_speed_rate", 1.0f)).thenReturn(1.5f)
        `when`(mockSharedPreferences.getFloat("tts_volume", 0.8f)).thenReturn(0.9f)
        `when`(mockSharedPreferences.getBoolean("general_gpu_acceleration", true)).thenReturn(false)
        `when`(mockSharedPreferences.getBoolean("general_npu_acceleration", false)).thenReturn(true)
        `when`(mockSharedPreferences.getInt("general_max_concurrent_tasks", 2)).thenReturn(4)
        `when`(mockSharedPreferences.getBoolean("general_debug_logging", false)).thenReturn(true)
    }

    private fun setupMockPreferencesFromSettings(settings: RuntimeSettings) {
        `when`(mockSharedPreferences.getFloat("llm_temperature", 0.7f)).thenReturn(settings.llmParams.temperature)
        `when`(mockSharedPreferences.getInt("llm_top_k", 5)).thenReturn(settings.llmParams.topK)
        `when`(mockSharedPreferences.getFloat("llm_top_p", 0.9f)).thenReturn(settings.llmParams.topP)
        `when`(mockSharedPreferences.getInt("llm_max_tokens", 2048)).thenReturn(settings.llmParams.maxTokens)
        `when`(mockSharedPreferences.getBoolean("llm_streaming", true)).thenReturn(settings.llmParams.enableStreaming)
        `when`(mockSharedPreferences.getFloat("vlm_vision_temperature", 0.7f)).thenReturn(settings.vlmParams.visionTemperature)
        `when`(mockSharedPreferences.getInt("vlm_image_resolution", 1)).thenReturn(settings.vlmParams.imageResolution.ordinal)
        `when`(mockSharedPreferences.getBoolean("vlm_image_analysis", true)).thenReturn(settings.vlmParams.enableImageAnalysis)
        `when`(mockSharedPreferences.getString("asr_language_model", "zh-TW")).thenReturn(settings.asrParams.languageModel)
        `when`(mockSharedPreferences.getInt("asr_beam_size", 4)).thenReturn(settings.asrParams.beamSize)
        `when`(mockSharedPreferences.getBoolean("asr_noise_suppression", true)).thenReturn(settings.asrParams.enableNoiseSuppression)
        `when`(mockSharedPreferences.getInt("tts_speaker_id", 0)).thenReturn(settings.ttsParams.speakerId)
        `when`(mockSharedPreferences.getFloat("tts_speed_rate", 1.0f)).thenReturn(settings.ttsParams.speedRate)
        `when`(mockSharedPreferences.getFloat("tts_volume", 0.8f)).thenReturn(settings.ttsParams.volume)
        `when`(mockSharedPreferences.getBoolean("general_gpu_acceleration", true)).thenReturn(settings.generalParams.enableGPUAcceleration)
        `when`(mockSharedPreferences.getBoolean("general_npu_acceleration", false)).thenReturn(settings.generalParams.enableNPUAcceleration)
        `when`(mockSharedPreferences.getInt("general_max_concurrent_tasks", 2)).thenReturn(settings.generalParams.maxConcurrentTasks)
        `when`(mockSharedPreferences.getBoolean("general_debug_logging", false)).thenReturn(settings.generalParams.enableDebugLogging)
    }

    private fun createTestSettings(): RuntimeSettings {
        return RuntimeSettings(
            llmParams = LLMParameters(
                temperature = 1.1f,
                topK = 30,
                topP = 0.85f,
                maxTokens = 1536,
                enableStreaming = false
            ),
            vlmParams = VLMParameters(
                visionTemperature = 0.9f,
                imageResolution = ImageResolution.HIGH,
                enableImageAnalysis = false
            ),
            asrParams = ASRParameters(
                languageModel = "en-US",
                beamSize = 7,
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