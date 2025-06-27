package com.mtkresearch.breezeapp_kotlin.data.source.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.AppSettings
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.FontSize
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsLocalDataSource @Inject constructor(@ApplicationContext context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAppSettings(): Flow<AppSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(loadAppSettings())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(loadAppSettings()) // Emit initial value
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun loadAppSettings(): AppSettings {
        return AppSettings(
            themeMode = ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!),
            fontSize = FontSize.fromScale(prefs.getFloat(KEY_FONT_SIZE, FontSize.MEDIUM.scale)),
            primaryColor = prefs.getString(KEY_PRIMARY_COLOR, "#F99A1B")!!,
            enableNotifications = prefs.getBoolean(KEY_NOTIFICATIONS, true)
            // ... load other settings
        )
    }

    suspend fun saveAppSettings(settings: AppSettings) {
        prefs.edit {
            putString(KEY_THEME_MODE, settings.themeMode.name)
            putFloat(KEY_FONT_SIZE, settings.fontSize.scale)
            putString(KEY_PRIMARY_COLOR, settings.primaryColor)
            putBoolean(KEY_NOTIFICATIONS, settings.enableNotifications)
            // ... save other settings
        }
    }

    suspend fun updateThemeMode(themeMode: ThemeMode) {
        prefs.edit {
            putString(KEY_THEME_MODE, themeMode.name)
        }
    }

    suspend fun updateFontSize(fontSize: FontSize) {
        prefs.edit {
            putFloat(KEY_FONT_SIZE, fontSize.scale)
        }
    }

    suspend fun updatePrimaryColor(colorHex: String) {
        prefs.edit {
            putString(KEY_PRIMARY_COLOR, colorHex)
        }
    }

    suspend fun updateNotificationsPreference(enabled: Boolean) {
        prefs.edit {
            putBoolean(KEY_NOTIFICATIONS, enabled)
        }
    }

    fun getThemeMode(): ThemeMode {
        return ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)!!)
    }

    fun getFontSize(): FontSize {
        return FontSize.fromScale(prefs.getFloat(KEY_FONT_SIZE, FontSize.MEDIUM.scale))
    }

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_PRIMARY_COLOR = "primary_color"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
} 