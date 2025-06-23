package com.mtkresearch.breezeapp_kotlin.presentation.settings.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseViewModel
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.*
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.LoadRuntimeSettingsUseCase
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.SaveRuntimeSettingsUseCase
import kotlinx.coroutines.launch

/**
 * AI推論層設定ViewModel
 * 
 * 負責管理AI推論相關參數的狀態和業務邏輯
 * 功能：
 * - LLM/VLM/ASR/TTS參數管理
 * - 即時參數驗證
 * - 參數預覽和應用
 * - 引擎參數同步
 * - 預設值管理
 * 
 * 符合MVVM架構，透過Use Cases與Repository交互
 */
class RuntimeSettingsViewModel(
    private val loadRuntimeSettingsUseCase: LoadRuntimeSettingsUseCase,
    private val saveRuntimeSettingsUseCase: SaveRuntimeSettingsUseCase
) : BaseViewModel() {

    // 當前運行時設定狀態
    private val _currentSettings = MutableLiveData<RuntimeSettings>()
    val currentSettings: LiveData<RuntimeSettings> = _currentSettings

    // 設定預覽狀態 (用於即時調整和預覽)
    private val _previewSettings = MutableLiveData<RuntimeSettings>()
    val previewSettings: LiveData<RuntimeSettings> = _previewSettings

    // 設定變更狀態
    private val _hasChanges = MutableLiveData<Boolean>()
    val hasChanges: LiveData<Boolean> = _hasChanges

    // 參數驗證結果
    private val _validationResult = MutableLiveData<ValidationResult>()
    val validationResult: LiveData<ValidationResult> = _validationResult

    // 應用狀態 (應用參數到AI引擎)
    private val _applyStatus = MutableLiveData<RuntimeApplyStatus>()
    val applyStatus: LiveData<RuntimeApplyStatus> = _applyStatus

    // 當前選中的參數類別
    private val _selectedCategory = MutableLiveData<ParameterCategory>()
    val selectedCategory: LiveData<ParameterCategory> = _selectedCategory

    init {
        loadSettings()
        _selectedCategory.value = ParameterCategory.LLM // 預設選中LLM
    }

    /**
     * 載入運行時設定
     */
    fun loadSettings() {
        viewModelScope.launch {
            try {
                setLoading(true)
                
                loadRuntimeSettingsUseCase().fold(
                    onSuccess = { settings ->
                        _currentSettings.value = settings
                        _previewSettings.value = settings
                        _hasChanges.value = false
                        validateCurrentSettings()
                    },
                    onFailure = { exception ->
                        setError("載入AI參數設定失敗", exception)
                        // 載入失敗時使用預設設定
                        val defaultSettings = RuntimeSettings()
                        _currentSettings.value = defaultSettings
                        _previewSettings.value = defaultSettings
                        _hasChanges.value = false
                        validateCurrentSettings()
                    }
                )
                
                setLoading(false)
            } catch (e: Exception) {
                setError("載入AI參數設定失敗", e)
            }
        }
    }

    /**
     * 使用指定設定初始化ViewModel
     */
    fun initializeWithSettings(settings: RuntimeSettings) {
        _currentSettings.value = settings
        _previewSettings.value = settings
        _hasChanges.value = false
        validateCurrentSettings()
    }

    /**
     * 選擇參數類別
     */
    fun selectCategory(category: ParameterCategory) {
        _selectedCategory.value = category
    }

    // ========== LLM 參數管理 ==========

    /**
     * 更新LLM溫度參數
     */
    fun updateLLMTemperature(temperature: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newLLMParams = currentPreview.llmParams.copy(temperature = temperature)
        val newSettings = currentPreview.copy(llmParams = newLLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新LLM Top-K參數
     */
    fun updateLLMTopK(topK: Int) {
        val currentPreview = _previewSettings.value ?: return
        val newLLMParams = currentPreview.llmParams.copy(topK = topK)
        val newSettings = currentPreview.copy(llmParams = newLLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新LLM Top-P參數
     */
    fun updateLLMTopP(topP: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newLLMParams = currentPreview.llmParams.copy(topP = topP)
        val newSettings = currentPreview.copy(llmParams = newLLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新LLM最大Token數
     */
    fun updateLLMMaxTokens(maxTokens: Int) {
        val currentPreview = _previewSettings.value ?: return
        val newLLMParams = currentPreview.llmParams.copy(maxTokens = maxTokens)
        val newSettings = currentPreview.copy(llmParams = newLLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新LLM重複懲罰
     */
    fun updateLLMRepetitionPenalty(penalty: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newLLMParams = currentPreview.llmParams.copy(repetitionPenalty = penalty)
        val newSettings = currentPreview.copy(llmParams = newLLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新LLM頻率懲罰
     */
    fun updateLLMFrequencyPenalty(penalty: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newLLMParams = currentPreview.llmParams.copy(frequencyPenalty = penalty)
        val newSettings = currentPreview.copy(llmParams = newLLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新LLM系統提示詞
     */
    fun updateLLMSystemPrompt(prompt: String) {
        val currentPreview = _previewSettings.value ?: return
        val newLLMParams = currentPreview.llmParams.copy(systemPrompt = prompt)
        val newSettings = currentPreview.copy(llmParams = newLLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新LLM串流輸出設定
     */
    fun updateLLMStreaming(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newLLMParams = currentPreview.llmParams.copy(enableStreaming = enabled)
        val newSettings = currentPreview.copy(llmParams = newLLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    // ========== VLM 參數管理 ==========

    /**
     * 更新VLM圖像解析度
     */
    fun updateVLMImageResolution(resolutionIndex: Int) {
        val currentPreview = _previewSettings.value ?: return
        val resolution = when (resolutionIndex) {
            0 -> ImageResolution.LOW
            1 -> ImageResolution.MEDIUM
            2 -> ImageResolution.HIGH
            else -> ImageResolution.MEDIUM
        }
        val newVLMParams = currentPreview.vlmParams.copy(imageResolution = resolution)
        val newSettings = currentPreview.copy(vlmParams = newVLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新VLM視覺溫度
     */
    fun updateVLMVisionTemperature(temperature: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newVLMParams = currentPreview.vlmParams.copy(visionTemperature = temperature)
        val newSettings = currentPreview.copy(vlmParams = newVLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新VLM最大圖像Token數
     */
    fun updateVLMMaxImageTokens(maxTokens: Int) {
        val currentPreview = _previewSettings.value ?: return
        val newVLMParams = currentPreview.vlmParams.copy(maxImageTokens = maxTokens)
        val newSettings = currentPreview.copy(vlmParams = newVLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新VLM圖像分析設定
     */
    fun updateVLMImageAnalysis(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newVLMParams = currentPreview.vlmParams.copy(enableImageAnalysis = enabled)
        val newSettings = currentPreview.copy(vlmParams = newVLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新VLM圖像裁切設定
     */
    fun updateVLMCropImages(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newVLMParams = currentPreview.vlmParams.copy(cropImages = enabled)
        val newSettings = currentPreview.copy(vlmParams = newVLMParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    // ========== ASR 參數管理 ==========

    /**
     * 更新ASR語言模型
     */
    fun updateASRLanguageModel(languageIndex: Int) {
        val currentPreview = _previewSettings.value ?: return
        val languageModel = when (languageIndex) {
            0 -> "zh-TW"
            1 -> "zh-CN"
            2 -> "en-US"
            3 -> "ja-JP"
            else -> "zh-TW"
        }
        val newASRParams = currentPreview.asrParams.copy(languageModel = languageModel)
        val newSettings = currentPreview.copy(asrParams = newASRParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新ASR語言模型
     */
    fun updateASRLanguageModel(languageModel: String) {
        val currentPreview = _previewSettings.value ?: return
        val newASRParams = currentPreview.asrParams.copy(languageModel = languageModel)
        val newSettings = currentPreview.copy(asrParams = newASRParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新ASR Beam大小
     */
    fun updateASRBeamSize(beamSize: Int) {
        val currentPreview = _previewSettings.value ?: return
        val newASRParams = currentPreview.asrParams.copy(beamSize = beamSize)
        val newSettings = currentPreview.copy(asrParams = newASRParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新ASR VAD閾值
     */
    fun updateASRVADThreshold(threshold: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newASRParams = currentPreview.asrParams.copy(vadThreshold = threshold)
        val newSettings = currentPreview.copy(asrParams = newASRParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新ASR噪音抑制設定
     */
    fun updateASRNoiseSuppression(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newASRParams = currentPreview.asrParams.copy(enableNoiseSuppression = enabled)
        val newSettings = currentPreview.copy(asrParams = newASRParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新ASR回音消除設定
     */
    fun updateASREchoCancellation(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newASRParams = currentPreview.asrParams.copy(enableEchoCancellation = enabled)
        val newSettings = currentPreview.copy(asrParams = newASRParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    // ========== TTS 參數管理 ==========

    /**
     * 更新TTS說話者ID
     */
    fun updateTTSSpeakerId(speakerId: Int) {
        val currentPreview = _previewSettings.value ?: return
        val newTTSParams = currentPreview.ttsParams.copy(speakerId = speakerId)
        val newSettings = currentPreview.copy(ttsParams = newTTSParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新TTS語速
     */
    fun updateTTSSpeedRate(speedRate: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newTTSParams = currentPreview.ttsParams.copy(speedRate = speedRate)
        val newSettings = currentPreview.copy(ttsParams = newTTSParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新TTS音調
     */
    fun updateTTSPitchScale(pitchScale: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newTTSParams = currentPreview.ttsParams.copy(pitchScale = pitchScale)
        val newSettings = currentPreview.copy(ttsParams = newTTSParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新TTS音量
     */
    fun updateTTSVolume(volume: Float) {
        val currentPreview = _previewSettings.value ?: return
        val newTTSParams = currentPreview.ttsParams.copy(volume = volume)
        val newSettings = currentPreview.copy(ttsParams = newTTSParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新TTS語音增強設定
     */
    fun updateTTSSpeechEnhancement(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newTTSParams = currentPreview.ttsParams.copy(enableSpeechEnhancement = enabled)
        val newSettings = currentPreview.copy(ttsParams = newTTSParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    // ========== 通用參數管理 ==========

    /**
     * 更新GPU加速設定
     */
    fun updateGPUAcceleration(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newGeneralParams = currentPreview.generalParams.copy(enableGPUAcceleration = enabled)
        val newSettings = currentPreview.copy(generalParams = newGeneralParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新NPU加速設定
     */
    fun updateNPUAcceleration(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newGeneralParams = currentPreview.generalParams.copy(enableNPUAcceleration = enabled)
        val newSettings = currentPreview.copy(generalParams = newGeneralParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新最大並發任務數
     */
    fun updateMaxConcurrentTasks(maxTasks: Int) {
        val currentPreview = _previewSettings.value ?: return
        val newGeneralParams = currentPreview.generalParams.copy(maxConcurrentTasks = maxTasks)
        val newSettings = currentPreview.copy(generalParams = newGeneralParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新推論超時時間
     */
    fun updateTimeoutSeconds(timeoutSeconds: Int) {
        val currentPreview = _previewSettings.value ?: return
        val newGeneralParams = currentPreview.generalParams.copy(timeoutSeconds = timeoutSeconds)
        val newSettings = currentPreview.copy(generalParams = newGeneralParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 更新除錯日誌設定
     */
    fun updateDebugLogging(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newGeneralParams = currentPreview.generalParams.copy(enableDebugLogging = enabled)
        val newSettings = currentPreview.copy(generalParams = newGeneralParams)
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    // ========== 設定管理功能 ==========

    /**
     * 應用設定到AI引擎
     */
    fun applySettings() {
        val settingsToApply = _previewSettings.value ?: return
        
        viewModelScope.launch {
            try {
                setLoading(true)
                _applyStatus.value = RuntimeApplyStatus.APPLYING
                
                // 使用Use Case保存設定
                saveRuntimeSettingsUseCase(settingsToApply).fold(
                    onSuccess = {
                        // 保存成功，更新當前設定
                        _currentSettings.value = settingsToApply
                        _hasChanges.value = false
                        _applyStatus.value = RuntimeApplyStatus.SUCCESS
                        setSuccess("AI參數已成功應用並保存")
                    },
                    onFailure = { exception ->
                        _applyStatus.value = RuntimeApplyStatus.ERROR
                        setError("應用AI參數失敗: ${exception.message}", exception)
                    }
                )
                
                setLoading(false)
            } catch (e: Exception) {
                _applyStatus.value = RuntimeApplyStatus.ERROR
                setError("應用AI參數失敗", e)
            }
        }
    }

    /**
     * 重置為預設設定
     */
    fun resetToDefault() {
        val defaultSettings = RuntimeSettings()
        _previewSettings.value = defaultSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 重置指定類別參數
     */
    fun resetCategoryToDefault(category: ParameterCategory) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = when (category) {
            ParameterCategory.LLM -> currentPreview.copy(llmParams = LLMParameters())
            ParameterCategory.VLM -> currentPreview.copy(vlmParams = VLMParameters())
            ParameterCategory.ASR -> currentPreview.copy(asrParams = ASRParameters())
            ParameterCategory.TTS -> currentPreview.copy(ttsParams = TTSParameters())
            ParameterCategory.GENERAL -> currentPreview.copy(generalParams = GeneralParameters())
        }
        
        _previewSettings.value = newSettings
        checkForChanges()
        validateCurrentSettings()
    }

    /**
     * 取消變更
     */
    fun discardChanges() {
        val currentSettings = _currentSettings.value ?: return
        _previewSettings.value = currentSettings
        _hasChanges.value = false
        validateCurrentSettings()
    }

    /**
     * 檢查是否有變更
     */
    private fun checkForChanges() {
        val current = _currentSettings.value
        val preview = _previewSettings.value
        _hasChanges.value = current != preview
    }

    /**
     * 驗證當前設定
     */
    private fun validateCurrentSettings() {
        val settings = _previewSettings.value ?: return
        val validation = settings.validateAll()
        _validationResult.value = validation
    }

    /**
     * 匯出運行時設定
     */
    fun exportRuntimeSettings(): String? {
        val settings = _currentSettings.value ?: return null
        
        // TODO: 實作設定序列化
        return "運行時設定匯出功能待實作"
    }

    /**
     * 匯入運行時設定
     */
    fun importRuntimeSettings(settingsData: String) {
        viewModelScope.launch {
            try {
                setLoading(true)
                
                // TODO: 實作設定反序列化
                setSuccess("運行時設定匯入功能待實作")
                
                setLoading(false)
            } catch (e: Exception) {
                setError("匯入運行時設定失敗", e)
            }
        }
    }
}

/**
 * 參數類別
 */
enum class ParameterCategory {
    LLM,        // 大語言模型
    VLM,        // 視覺語言模型
    ASR,        // 語音識別
    TTS,        // 文字轉語音
    GENERAL     // 通用設定
}

/**
 * 運行時設定應用狀態
 */
enum class RuntimeApplyStatus {
    IDLE,               // 空閒
    APPLYING,           // 應用中
    SUCCESS,            // 應用成功
    ERROR,              // 應用錯誤
    VALIDATION_ERROR    // 驗證錯誤
} 