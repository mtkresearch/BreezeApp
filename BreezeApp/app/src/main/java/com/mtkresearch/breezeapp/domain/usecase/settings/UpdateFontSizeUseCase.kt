package com.mtkresearch.breezeapp.domain.usecase.settings

import com.mtkresearch.breezeapp.domain.model.settings.FontSize
import com.mtkresearch.breezeapp.domain.repository.AppSettingsRepository
import javax.inject.Inject

class UpdateFontSizeUseCase @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) {
    suspend operator fun invoke(fontSize: FontSize) {
        appSettingsRepository.updateFontSize(fontSize)
    }
} 