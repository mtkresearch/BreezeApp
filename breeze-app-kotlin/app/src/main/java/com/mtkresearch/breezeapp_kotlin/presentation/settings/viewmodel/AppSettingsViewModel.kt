package com.mtkresearch.breezeapp_kotlin.presentation.settings.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseViewModel
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.AppSettings
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.FontSize
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.StorageLocation
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.ThemeMode
import kotlinx.coroutines.launch

/**
 * 應用層設定ViewModel
 * 
 * 負責管理應用程式層面的設定狀態和業務邏輯
 * 功能：
 * - 設定載入和儲存
 * - 設定驗證和預覽
 * - 即時設定更新
 * - 設定重置和備份
 */
class AppSettingsViewModel : BaseViewModel() {

    // 當前設定狀態
    private val _currentSettings = MutableLiveData<AppSettings>()
    val currentSettings: LiveData<AppSettings> = _currentSettings

    // 設定預覽狀態 (用於即時預覽)
    private val _previewSettings = MutableLiveData<AppSettings>()
    val previewSettings: LiveData<AppSettings> = _previewSettings

    // 設定變更狀態
    private val _hasChanges = MutableLiveData<Boolean>()
    val hasChanges: LiveData<Boolean> = _hasChanges

    // 儲存狀態
    private val _saveStatus = MutableLiveData<SettingSaveStatus>()
    val saveStatus: LiveData<SettingSaveStatus> = _saveStatus

    init {
        loadSettings()
    }

    /**
     * 載入設定
     */
    fun loadSettings() {
        viewModelScope.launch {
            try {
                setLoading(true)
                
                // TODO: 實際從Repository載入設定
                // 目前使用預設設定
                val settings = AppSettings()
                
                _currentSettings.value = settings
                _previewSettings.value = settings
                _hasChanges.value = false
                
                setLoading(false)
            } catch (e: Exception) {
                setError("載入設定失敗", e)
            }
        }
    }

    /**
     * 更新主題模式
     */
    fun updateThemeMode(themeMode: ThemeMode) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(themeMode = themeMode)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新主要顏色
     */
    fun updatePrimaryColor(color: String) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(primaryColor = color)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新字體大小
     */
    fun updateFontSize(fontSize: FontSize) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(fontSize = fontSize)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新語言
     */
    fun updateLanguage(language: String) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(language = language)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新通知設定
     */
    fun updateNotifications(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(enableNotifications = enabled)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新動畫設定
     */
    fun updateAnimations(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(enableAnimations = enabled)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新儲存位置
     */
    fun updateStorageLocation(location: StorageLocation) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(storageLocation = location)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新自動備份設定
     */
    fun updateAutoBackup(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(autoBackup = enabled)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新觸覺回饋設定
     */
    fun updateHapticFeedback(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(enableHapticFeedback = enabled)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 更新音效設定
     */
    fun updateSoundEffects(enabled: Boolean) {
        val currentPreview = _previewSettings.value ?: return
        val newSettings = currentPreview.copy(enableSoundEffects = enabled)
        
        _previewSettings.value = newSettings
        checkForChanges()
    }

    /**
     * 儲存設定
     */
    fun saveSettings() {
        val settingsToSave = _previewSettings.value ?: return
        
        viewModelScope.launch {
            try {
                setLoading(true)
                _saveStatus.value = SettingSaveStatus.SAVING
                
                // TODO: 實際儲存到Repository
                // 模擬儲存延遲
                kotlinx.coroutines.delay(500)
                
                _currentSettings.value = settingsToSave
                _hasChanges.value = false
                _saveStatus.value = SettingSaveStatus.SUCCESS
                
                setLoading(false)
            } catch (e: Exception) {
                _saveStatus.value = SettingSaveStatus.ERROR
                setError("儲存設定失敗", e)
            }
        }
    }

    /**
     * 重置設定
     */
    fun resetToDefault() {
        val defaultSettings = AppSettings()
        _previewSettings.value = defaultSettings
        checkForChanges()
    }

    /**
     * 取消變更
     */
    fun discardChanges() {
        val currentSettings = _currentSettings.value ?: return
        _previewSettings.value = currentSettings
        _hasChanges.value = false
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
     * 匯出設定
     */
    fun exportSettings(): String? {
        val settings = _currentSettings.value ?: return null
        
        // TODO: 實作設定序列化
        return "設定匯出功能待實作"
    }

    /**
     * 匯入設定
     */
    fun importSettings(settingsData: String) {
        viewModelScope.launch {
            try {
                setLoading(true)
                
                // TODO: 實作設定反序列化
                // 目前顯示成功訊息
                setSuccess("設定匯入功能待實作")
                
                setLoading(false)
            } catch (e: Exception) {
                setError("匯入設定失敗", e)
            }
        }
    }
}

/**
 * 設定儲存狀態
 */
enum class SettingSaveStatus {
    IDLE,       // 空閒
    SAVING,     // 儲存中
    SUCCESS,    // 儲存成功
    ERROR       // 儲存錯誤
} 