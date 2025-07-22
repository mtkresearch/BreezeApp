package com.mtkresearch.breezeapp_kotlin.data.repository

import com.mtkresearch.breezeapp_kotlin.data.source.local.AppSettingsLocalDataSource
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.AppSettings
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.FontSize
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp_kotlin.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsRepositoryImpl @Inject constructor(
    private val localDataSource: AppSettingsLocalDataSource
) : AppSettingsRepository {

    override fun getAppSettings(): Flow<AppSettings> {
        return localDataSource.getAppSettings()
    }

    override suspend fun saveAppSettings(settings: AppSettings) {
        localDataSource.saveAppSettings(settings)
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        localDataSource.updateThemeMode(themeMode)
    }

    override suspend fun updateFontSize(fontSize: FontSize) {
        localDataSource.updateFontSize(fontSize)
    }

    override suspend fun updatePrimaryColor(colorHex: String) {
        localDataSource.updatePrimaryColor(colorHex)
    }

    override suspend fun updateNotificationsPreference(enabled: Boolean) {
        localDataSource.updateNotificationsPreference(enabled)
    }
    
    override fun getThemeMode(): ThemeMode {
        return localDataSource.getThemeMode()
    }

    override fun getFontSize(): FontSize {
        return localDataSource.getFontSize()
    }
} 