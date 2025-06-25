package com.mtkresearch.breezeapp_kotlin.presentation.common.base

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.FontSize
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp_kotlin.domain.repository.AppSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

abstract class BaseActivity : AppCompatActivity() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsEntryPoint {
        fun appSettingsRepository(): AppSettingsRepository
    }

    // 記錄上次應用的主題，避免重複設定
    companion object {
        private var lastAppliedTheme: ThemeMode? = null
    }

    override fun attachBaseContext(newBase: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(newBase, SettingsEntryPoint::class.java)
        val repo = entryPoint.appSettingsRepository()

        val savedThemeMode = repo.getThemeMode()
        
        // 只在主題真正變更時才應用，避免重複設定
        if (lastAppliedTheme != savedThemeMode) {
            applyTheme(savedThemeMode)
            lastAppliedTheme = savedThemeMode
        }

        val savedFontSize = repo.getFontSize()
        val configuration = createConfiguration(newBase, savedFontSize)

        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    private fun applyTheme(themeMode: ThemeMode) {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val targetMode = when (themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        
        // 只有在模式真正不同時才設定，避免不必要的變更
        if (currentMode != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    private fun createConfiguration(baseContext: Context, fontSize: FontSize): Configuration {
        val config = Configuration(baseContext.resources.configuration)
        val newFontScale = when (fontSize) {
            FontSize.SMALL -> 0.85f
            FontSize.MEDIUM -> 1.0f
            FontSize.LARGE -> 1.15f
        }
        
        // 只在字體大小真正變更時才修改配置
        if (config.fontScale != newFontScale) {
            config.fontScale = newFontScale
        }
        
        return config
    }
} 