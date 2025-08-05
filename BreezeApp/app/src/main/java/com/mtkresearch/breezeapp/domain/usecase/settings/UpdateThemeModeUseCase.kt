package com.mtkresearch.breezeapp.domain.usecase.settings

import com.mtkresearch.breezeapp.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp.domain.repository.AppSettingsRepository
import javax.inject.Inject

class UpdateThemeModeUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) {
    suspend operator fun invoke(themeMode: ThemeMode) {
        appSettingsRepository.updateThemeMode(themeMode)
    }
} 