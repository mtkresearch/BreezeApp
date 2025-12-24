package com.mtkresearch.breezeapp.presentation.common.base

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.mtkresearch.breezeapp.domain.model.settings.FontSize
import com.mtkresearch.breezeapp.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp.domain.repository.AppSettingsRepository
import com.mtkresearch.breezeapp.edgeai.DownloadConstants
import com.mtkresearch.breezeapp.presentation.common.download.AppDownloadProgressBottomSheet
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

abstract class BaseActivity : AppCompatActivity() {

    private val downloadReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                DownloadConstants.ACTION_SHOW_DOWNLOAD_UI -> {
                    // Show UI only when explicitly requested (batch start)
                    showDownloadProgressUI()
                }
                DownloadConstants.ACTION_DOWNLOAD_STARTED -> {
                    // File-level progress update - don't show UI again, just update progress
                    val modelId = intent.getStringExtra(DownloadConstants.EXTRA_MODEL_ID) ?: ""
                    val fileName = intent.getStringExtra(DownloadConstants.EXTRA_FILE_NAME) ?: ""
                    val index = intent.getIntExtra("current_file_index", -1)
                    val total = intent.getIntExtra("total_files", -1)
                    onDownloadProgressUpdate(modelId, fileName, index, total)
                }
                "com.mtkresearch.breezeapp.GLOBAL_DOWNLOAD_STATE" -> {
                    val isDownloading = intent.getBooleanExtra("is_downloading", false)
                    val modelId = intent.getStringExtra(DownloadConstants.EXTRA_MODEL_ID)
                    onDownloadStateChanged(isDownloading, modelId)
                }
            }
        }
    }
    
    private var downloadSheet: AppDownloadProgressBottomSheet? = null
    protected var isGlobalDownloadActive = false

    private fun showDownloadProgressUI() {
        try {
            val fm = supportFragmentManager
            // Avoid showing multiple instances
            if (fm.findFragmentByTag(AppDownloadProgressBottomSheet.TAG) == null) {
                downloadSheet = AppDownloadProgressBottomSheet.show(fm)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // Override in subclasses to handle download state
    open fun onDownloadStateChanged(isDownloading: Boolean, modelId: String?) {
        isGlobalDownloadActive = isDownloading
    }

    // Override in subclasses to handle detailed progress
    open fun onDownloadProgressUpdate(modelId: String, fileName: String, currentFileIndex: Int, totalFiles: Int) {
        // Default no-op
    }
    
    // Helper to check before AI actions
    protected fun canPerformAIAction(): Boolean {
        if (isGlobalDownloadActive) {
            android.widget.Toast.makeText(
                this,
                "Please wait for model download to complete",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return false
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(DownloadConstants.ACTION_SHOW_DOWNLOAD_UI)
                addAction(DownloadConstants.ACTION_DOWNLOAD_STARTED)
                addAction(DownloadConstants.ACTION_DOWNLOAD_PROGRESS)
                addAction(DownloadConstants.ACTION_DOWNLOAD_COMPLETED)
                addAction(DownloadConstants.ACTION_DOWNLOAD_FAILED)
                addAction("com.mtkresearch.breezeapp.GLOBAL_DOWNLOAD_STATE")
            }
            // Register global receiver (exported for cross-process if needed, or NOT_EXPORTED if internal)
            // Since Engine might be in a separate process but same app signature, assume EXPORTED or handle version.
            // For simplicity and inter-process communication:
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(downloadReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(downloadReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SettingsEntryPoint {
        fun appSettingsRepository(): AppSettingsRepository
    }

    // 記錄上次應用的主題，避免重複設定
    companion object {
        private var lastAppliedTheme: ThemeMode? = null
    }

    override fun attachBaseContext(newBase: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(newBase, SettingsEntryPoint::class.java)
        val repo = entryPoint.appSettingsRepository()

        val savedThemeMode = repo.getThemeMode()
        
        // 只在主題真正變更時才應用，避免重複設定
        if (lastAppliedTheme != savedThemeMode) {
            applyTheme(savedThemeMode)
            lastAppliedTheme = savedThemeMode
        }

        val savedFontSize = repo.getFontSize()
        val configuration = createConfiguration(newBase, savedFontSize)

        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    private fun applyTheme(themeMode: ThemeMode) {
        val currentMode = AppCompatDelegate.getDefaultNightMode()
        val targetMode = when (themeMode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        
        // 只有在模式真正不同時才設定，避免不必要的變更
        if (currentMode != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    private fun createConfiguration(baseContext: Context, fontSize: FontSize): Configuration {
        val config = Configuration(baseContext.resources.configuration)
        val newFontScale = when (fontSize) {
            FontSize.SMALL -> 0.85f
            FontSize.MEDIUM -> 1.0f
            FontSize.LARGE -> 1.15f
        }
        
        // 只在字體大小真正變更時才修改配置
        if (config.fontScale != newFontScale) {
            config.fontScale = newFontScale
        }
        
        return config
    }
} 