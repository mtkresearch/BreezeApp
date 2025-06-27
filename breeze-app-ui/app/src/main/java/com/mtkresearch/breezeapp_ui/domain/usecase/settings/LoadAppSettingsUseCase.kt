package com.mtkresearch.breezeapp_ui.domain.usecase.settings

import com.mtkresearch.breezeapp_ui.domain.model.settings.AppSettings
import com.mtkresearch.breezeapp_ui.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoadAppSettingsUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) {
    operator fun invoke(): Flow<AppSettings> {
        return appSettingsRepository.getAppSettings()
    }
} 