package com.mtkresearch.breezeapp_kotlin.presentation.settings.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mtkresearch.breezeapp_kotlin.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.LoadRuntimeSettingsUseCase
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.SaveRuntimeSettingsUseCase

/**
 * RuntimeSettingsViewModel Factory
 * 
 * 負責創建包含依賴項的RuntimeSettingsViewModel實例
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
            val loadUseCase = LoadRuntimeSettingsUseCase(repository)
            val saveUseCase = SaveRuntimeSettingsUseCase(repository)
            
            return RuntimeSettingsViewModel(loadUseCase, saveUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 