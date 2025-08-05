package com.mtkresearch.breezeapp.domain.repository

import com.mtkresearch.breezeapp.domain.model.settings.AppSettings
import com.mtkresearch.breezeapp.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp.domain.model.settings.FontSize
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {

    /**
     * Retrieves the application settings as a Flow.
     * This allows observers to automatically receive updates when settings change.
     */
    fun getAppSettings(): Flow<AppSettings>

    /**
     * Saves the entire AppSettings object.
     * @param settings The AppSettings object to save.
     */
    suspend fun saveAppSettings(settings: AppSettings)

    /**
     * Updates the theme mode.
     * @param themeMode The new theme mode to save.
     */
    suspend fun updateThemeMode(themeMode: ThemeMode)

    /**
     * Updates the font size.
     * @param fontSize The new font size to save.
     */
    suspend fun updateFontSize(fontSize: FontSize)

    /**
     * Updates the primary color.
     * @param colorHex The new color hex string to save.
     */
    suspend fun updatePrimaryColor(colorHex: String)

    /**
     * Updates the notification preference.
     * @param enabled The new value for notification preference.
     */
    suspend fun updateNotificationsPreference(enabled: Boolean)

    // Synchronous methods for immediate access
    fun getThemeMode(): ThemeMode
    fun getFontSize(): FontSize

} 