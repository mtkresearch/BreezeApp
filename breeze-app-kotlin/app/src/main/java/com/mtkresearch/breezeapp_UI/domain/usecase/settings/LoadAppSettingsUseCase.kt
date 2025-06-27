package com.mtkresearch.breezeapp_kotlin.domain.usecase.settings

import com.mtkresearch.breezeapp_kotlin.domain.model.settings.AppSettings
import com.mtkresearch.breezeapp_kotlin.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class LoadAppSettingsUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) {
    operator fun invoke(): Flow<AppSettings> {
        return appSettingsRepository.getAppSettings()
    }
} 