package com.mtkresearch.breezeapp_kotlin.domain.usecase.settings

import com.mtkresearch.breezeapp_kotlin.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.RuntimeSettings

/**
 * Load Runtime Settings Use Case
 * 
 * 負責載入Runtime設定的業務邏輯
 * 符合Clean Architecture的Domain Layer
 */
class LoadRuntimeSettingsUseCase(
    private val repository: RuntimeSettingsRepository
) {
    
    suspend operator fun invoke(): Result<RuntimeSettings> {
        return try {
            val settings = repository.loadSettings()
            Result.success(settings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 