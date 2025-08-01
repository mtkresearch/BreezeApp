package com.mtkresearch.breezeapp_kotlin

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp_kotlin.domain.repository.AppSettingsRepository
import com.mtkresearch.breezeapp_kotlin.domain.usecase.breezeapp.ConnectionUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BreezeApplication : Application() {

    @Inject
    lateinit var appSettingsRepository: AppSettingsRepository
    
    @Inject
    lateinit var connectionUseCase: ConnectionUseCase

    override fun onCreate() {
        super.onCreate()
        applyTheme()
        initializeBreezeAppEngine()
    }

    private fun applyTheme() {
        CoroutineScope(Dispatchers.Main).launch {
            val settings = appSettingsRepository.getAppSettings().first()
            val mode = when(settings.themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
    
    /**
     * Initialize BreezeApp Engine connection
     * This is called during app startup to establish connection to the AI engine
     */
    private fun initializeBreezeAppEngine() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                connectionUseCase.initialize().collect { state ->
                    when (state) {
                        is com.mtkresearch.breezeapp_kotlin.domain.model.breezeapp.ConnectionState.Connected -> {
                            android.util.Log.i("BreezeApplication", "BreezeApp Engine connected successfully")
                        }
                        is com.mtkresearch.breezeapp_kotlin.domain.model.breezeapp.ConnectionState.Failed -> {
                            android.util.Log.w("BreezeApplication", "BreezeApp Engine connection failed: ${state.message}")
                        }
                        else -> {
                            // Handle other states if needed
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BreezeApplication", "Error initializing BreezeApp Engine", e)
            }
        }
    }
} 