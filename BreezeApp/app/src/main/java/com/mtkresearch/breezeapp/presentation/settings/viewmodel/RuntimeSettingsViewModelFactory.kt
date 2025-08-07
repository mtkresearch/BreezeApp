package com.mtkresearch.breezeapp.presentation.settings.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mtkresearch.breezeapp.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp.domain.usecase.settings.LoadRuntimeSettingsUseCase
import com.mtkresearch.breezeapp.domain.usecase.settings.SaveRuntimeSettingsUseCase
import com.mtkresearch.breezeapp.domain.usecase.settings.UpdateRuntimeParameterUseCase
import com.mtkresearch.breezeapp.domain.usecase.settings.ValidateRuntimeSettingsUseCase

/**
 * RuntimeSettingsViewModel Factory
 * 
 * 負責創建包含所有依賴項的RuntimeSettingsViewModel實例
 * 這是過渡性解決方案，未來可以用Hilt取代
 */
class RuntimeSettingsViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RuntimeSettingsViewModel::class.java)) {
            // 手動創建依賴項
            val repository = RuntimeSettingsRepository(context)
            val validateUseCase = ValidateRuntimeSettingsUseCase()
            val loadUseCase = LoadRuntimeSettingsUseCase(repository)
            val saveUseCase = SaveRuntimeSettingsUseCase(repository, validateUseCase)
            val updateParameterUseCase = UpdateRuntimeParameterUseCase(validateUseCase)
            
            return RuntimeSettingsViewModel(
                loadUseCase,
                saveUseCase,
                updateParameterUseCase,
                validateUseCase
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 