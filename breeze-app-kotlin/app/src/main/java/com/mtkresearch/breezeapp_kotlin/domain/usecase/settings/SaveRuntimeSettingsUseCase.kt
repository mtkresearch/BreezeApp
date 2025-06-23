package com.mtkresearch.breezeapp_kotlin.domain.usecase.settings

import com.mtkresearch.breezeapp_kotlin.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.RuntimeSettings

/**
 * Save Runtime Settings Use Case
 * 
 * 負責儲存Runtime設定的業務邏輯
 * 符合Clean Architecture的Domain Layer
 */
class SaveRuntimeSettingsUseCase(
    private val repository: RuntimeSettingsRepository
) {
    
    suspend operator fun invoke(settings: RuntimeSettings): Result<Unit> {
        return try {
            // 可以在這裡添加業務邏輯驗證
            val validationResult = settings.validateAll()
            if (!validationResult.isValid) {
                return Result.failure(
                    IllegalArgumentException("設定驗證失敗: ${validationResult.errors.joinToString(", ")}")
                )
            }
            
            repository.saveSettings(settings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 