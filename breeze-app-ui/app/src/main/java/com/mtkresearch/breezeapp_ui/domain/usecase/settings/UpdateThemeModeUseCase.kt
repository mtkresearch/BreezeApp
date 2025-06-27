package com.mtkresearch.breezeapp_ui.domain.usecase.settings

import com.mtkresearch.breezeapp_ui.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp_ui.domain.repository.AppSettingsRepository
import javax.inject.Inject

class UpdateThemeModeUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) {
    suspend operator fun invoke(themeMode: ThemeMode) {
        appSettingsRepository.updateThemeMode(themeMode)
    }
} 