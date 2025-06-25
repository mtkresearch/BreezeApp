package com.mtkresearch.breezeapp_kotlin.presentation.settings.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.AppSettings
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.FontSize
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.LoadAppSettingsUseCase
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.UpdateFontSizeUseCase
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.UpdateThemeModeUseCase
import com.mtkresearch.breezeapp_kotlin.presentation.common.SingleLiveEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

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
@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val loadAppSettingsUseCase: LoadAppSettingsUseCase,
    private val updateThemeModeUseCase: UpdateThemeModeUseCase,
    private val updateFontSizeUseCase: UpdateFontSizeUseCase
) : ViewModel() {

    private val _settings = MutableLiveData<AppSettings>()
    val settings: LiveData<AppSettings> = _settings

    private val _themeChangedEvent = SingleLiveEvent<Unit>()
    val themeChangedEvent: LiveData<Unit> = _themeChangedEvent

    // 防重複更新的追蹤
    private var currentThemeMode: ThemeMode? = null
    private var currentFontSize: FontSize? = null
    private var isUpdating = false

    init {
        loadSettings()
    }

    private fun loadSettings() {
        loadAppSettingsUseCase()
            .distinctUntilChanged() // 只有真正改變的設定才會觸發更新
            .onEach { newSettings ->
                if (!isUpdating) {
                    _settings.value = newSettings
                    currentThemeMode = newSettings.themeMode
                    currentFontSize = newSettings.fontSize
                }
            }.launchIn(viewModelScope)
    }

    fun onThemeModeChanged(newThemeMode: ThemeMode) {
        // 防止重複更新相同的主題
        if (currentThemeMode == newThemeMode || isUpdating) {
            return
        }

        viewModelScope.launch {
            isUpdating = true
            try {
                updateThemeModeUseCase(newThemeMode)
                currentThemeMode = newThemeMode
                
                // 只在主題真正改變時才觸發重建事件
                _themeChangedEvent.value = Unit
            } finally {
                // 延遲重置更新標記，避免立即重複觸發
                kotlinx.coroutines.delay(500)
                isUpdating = false
            }
        }
    }

    fun onFontSizeChanged(scale: Float) {
        val fontSize = FontSize.fromScale(scale)
        
        // 防止重複更新相同的字體大小
        if (currentFontSize == fontSize || isUpdating) {
            return
        }

        viewModelScope.launch {
            isUpdating = true
            try {
                updateFontSizeUseCase(fontSize)
                currentFontSize = fontSize
            } finally {
                // 延遲重置更新標記
                kotlinx.coroutines.delay(200)
                isUpdating = false
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