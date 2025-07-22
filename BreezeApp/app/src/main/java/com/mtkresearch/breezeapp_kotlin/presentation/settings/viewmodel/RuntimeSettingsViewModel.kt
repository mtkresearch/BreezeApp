package com.mtkresearch.breezeapp_kotlin.presentation.settings.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseViewModel
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.*
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.LoadRuntimeSettingsUseCase
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.SaveRuntimeSettingsUseCase
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.UpdateRuntimeParameterUseCase
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.ValidateRuntimeSettingsUseCase
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.ParameterUpdate
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.ValidationResult
import kotlinx.coroutines.launch

/**
 * AI推論層設定ViewModel
 * 
 * 負責管理AI推論相關參數的狀態和業務邏輯
 * 遵循MVVM架構，透過Use Cases與Domain層交互
 * 
 * 功能：
 * - LLM/VLM/ASR/TTS參數管理
 * - 即時參數驗證
 * - 參數預覽和應用
 * - 設定持久化
 */
class RuntimeSettingsViewModel(
    private val loadRuntimeSettingsUseCase: LoadRuntimeSettingsUseCase,
    private val saveRuntimeSettingsUseCase: SaveRuntimeSettingsUseCase,
    private val updateRuntimeParameterUseCase: UpdateRuntimeParameterUseCase,
    private val validateRuntimeSettingsUseCase: ValidateRuntimeSettingsUseCase
) : BaseViewModel() {

    // ========== 狀態管理 ==========
    
    // 當前已保存的設定
    private val _currentSettings = MutableLiveData<RuntimeSettings>()
    val currentSettings: LiveData<RuntimeSettings> = _currentSettings

    // 預覽設定 (用於即時調整)
    private val _previewSettings = MutableLiveData<RuntimeSettings>()
    val previewSettings: LiveData<RuntimeSettings> = _previewSettings

    // 是否有未保存的變更
    private val _hasChanges = MutableLiveData<Boolean>()
    val hasChanges: LiveData<Boolean> = _hasChanges

    // 參數驗證結果
    private val _validationResult = MutableLiveData<ValidationResult>()
    val validationResult: LiveData<ValidationResult> = _validationResult

    // 當前選中的參數類別
    private val _selectedCategory = MutableLiveData<ParameterCategory>()
    val selectedCategory: LiveData<ParameterCategory> = _selectedCategory

    init {
        loadSettings()
        _selectedCategory.value = ParameterCategory.LLM
    }

    // ========== 設定管理 ==========

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
                        validateSettings(settings)
                    },
                    onFailure = { exception ->
                        setError("載入AI參數設定失敗", exception)
                        // 載入失敗時使用預設設定
                        val defaultSettings = RuntimeSettings()
                        _currentSettings.value = defaultSettings
                        _previewSettings.value = defaultSettings
                        _hasChanges.value = false
                        validateSettings(defaultSettings)
                    }
                )
                
            } catch (e: Exception) {
                setError("載入AI參數設定失敗", e)
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * 保存當前預覽設定
     */
    fun saveSettings() {
        val settingsToSave = _previewSettings.value ?: return
        
        viewModelScope.launch {
            try {
                setLoading(true)
                
                saveRuntimeSettingsUseCase(settingsToSave).fold(
                    onSuccess = {
                        _currentSettings.value = settingsToSave
                        _hasChanges.value = false
                        setSuccess("AI參數設定已保存")
                    },
                    onFailure = { exception ->
                        setError("保存AI參數設定失敗", exception)
                    }
                )
                
            } catch (e: Exception) {
                setError("保存AI參數設定失敗", e)
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * 放棄變更，恢復到已保存的設定
     */
    fun discardChanges() {
        val currentSaved = _currentSettings.value ?: return
        _previewSettings.value = currentSaved
        _hasChanges.value = false
        validateSettings(currentSaved)
    }

    /**
     * 選擇參數類別
     * 切換tab時自動放棄未應用的變更，恢復到已保存的設定
     */
    fun selectCategory(category: ParameterCategory) {
        // 如果有未應用的變更，先恢復到已保存的設定
        if (_hasChanges.value == true) {
            discardChanges()
        }
        _selectedCategory.value = category
    }

    // ========== 參數更新 ==========

    /**
     * 通用參數更新方法
     */
    private fun updateParameter(parameterUpdate: ParameterUpdate) {
        val currentPreview = _previewSettings.value ?: return
        
        updateRuntimeParameterUseCase(currentPreview, parameterUpdate).fold(
            onSuccess = { updatedSettings ->
                _previewSettings.value = updatedSettings
                checkForChanges()
                validateSettings(updatedSettings)
            },
            onFailure = { exception ->
                setError("參數更新失敗", exception)
            }
        )
    }

    // ========== LLM 參數更新 ==========
    
    fun updateLLMTemperature(temperature: Float) {
        updateParameter(ParameterUpdate.LLM.Temperature(temperature))
    }

    fun updateLLMTopK(topK: Int) {
        updateParameter(ParameterUpdate.LLM.TopK(topK))
    }

    fun updateLLMTopP(topP: Float) {
        updateParameter(ParameterUpdate.LLM.TopP(topP))
    }

    fun updateLLMMaxTokens(maxTokens: Int) {
        updateParameter(ParameterUpdate.LLM.MaxTokens(maxTokens))
    }

    fun updateLLMStreaming(enabled: Boolean) {
        updateParameter(ParameterUpdate.LLM.EnableStreaming(enabled))
    }

    // ========== VLM 參數更新 ==========
    
    fun updateVLMImageResolution(resolutionIndex: Int) {
        val resolution = when (resolutionIndex) {
            0 -> ImageResolution.LOW
            1 -> ImageResolution.MEDIUM
            2 -> ImageResolution.HIGH
            else -> ImageResolution.MEDIUM
        }
        updateParameter(ParameterUpdate.VLM.ImageResolution(resolution))
    }

    fun updateVLMVisionTemperature(temperature: Float) {
        updateParameter(ParameterUpdate.VLM.VisionTemperature(temperature))
    }

    fun updateVLMImageAnalysis(enabled: Boolean) {
        updateParameter(ParameterUpdate.VLM.EnableImageAnalysis(enabled))
    }

    // ========== ASR 參數更新 ==========
    
    fun updateASRLanguageModel(languageIndex: Int) {
        val languageModel = when (languageIndex) {
            0 -> "zh-TW"
            1 -> "zh-CN"
            2 -> "en-US"
            3 -> "ja-JP"
            else -> "zh-TW"
        }
        updateParameter(ParameterUpdate.ASR.LanguageModel(languageModel))
    }

    fun updateASRBeamSize(beamSize: Int) {
        updateParameter(ParameterUpdate.ASR.BeamSize(beamSize))
    }

    fun updateASRNoiseSuppression(enabled: Boolean) {
        updateParameter(ParameterUpdate.ASR.EnableNoiseSuppression(enabled))
    }

    // ========== TTS 參數更新 ==========
    
    fun updateTTSSpeakerId(speakerId: Int) {
        updateParameter(ParameterUpdate.TTS.SpeakerId(speakerId))
    }

    fun updateTTSSpeedRate(speedRate: Float) {
        updateParameter(ParameterUpdate.TTS.SpeedRate(speedRate))
    }

    fun updateTTSVolume(volume: Float) {
        updateParameter(ParameterUpdate.TTS.Volume(volume))
    }

    // ========== 通用參數更新 ==========
    
    fun updateGPUAcceleration(enabled: Boolean) {
        updateParameter(ParameterUpdate.General.EnableGPUAcceleration(enabled))
    }

    fun updateNPUAcceleration(enabled: Boolean) {
        updateParameter(ParameterUpdate.General.EnableNPUAcceleration(enabled))
    }

    fun updateMaxConcurrentTasks(maxTasks: Int) {
        updateParameter(ParameterUpdate.General.MaxConcurrentTasks(maxTasks))
    }

    fun updateDebugLogging(enabled: Boolean) {
        updateParameter(ParameterUpdate.General.EnableDebugLogging(enabled))
    }

    // ========== 私有輔助方法 ==========

    /**
     * 檢查是否有變更
     */
    private fun checkForChanges() {
        val current = _currentSettings.value
        val preview = _previewSettings.value
        _hasChanges.value = current != preview
    }

    /**
     * 驗證設定
     */
    private fun validateSettings(settings: RuntimeSettings) {
        val result = validateRuntimeSettingsUseCase(settings)
        _validationResult.value = result
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

 