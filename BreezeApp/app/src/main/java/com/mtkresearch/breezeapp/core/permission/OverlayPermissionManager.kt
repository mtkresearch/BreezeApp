package com.mtkresearch.breezeapp.core.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for handling system overlay permissions
 * Required for BreezeApp-engine to display foreground UI for microphone recording
 */
@Singleton
class OverlayPermissionManager @Inject constructor() {
    
    companion object {
        private const val TAG = "OverlayPermissionManager"
        private const val PREF_KEY_OVERLAY_REQUESTED = "overlay_permission_requested"
        private const val PREF_KEY_OVERLAY_DENIED_COUNT = "overlay_permission_denied_count"
        private const val MAX_DENIAL_COUNT = 2
    }
    
    private var permissionLauncher: ActivityResultLauncher<Intent>? = null
    private var onPermissionResult: ((Boolean) -> Unit)? = null
    
    /**
     * Check if overlay permission is granted
     */
    fun isOverlayPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context).also { granted ->
                Log.d(TAG, "Overlay permission granted: $granted")
            }
        } else {
            true // Not required for API < 23
        }
    }
    
    /**
     * Setup permission launcher in Activity/Fragment
     */
    fun setupPermissionLauncher(
        activity: FragmentActivity,
        onResult: (Boolean) -> Unit
    ) {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val granted = isOverlayPermissionGranted(activity)
            Log.d(TAG, "Overlay permission result: granted=$granted")
            
            if (!granted) {
                incrementDenialCount(activity)
            }
            
            onPermissionResult?.invoke(granted)
            onResult(granted)
        }
        onPermissionResult = onResult
    }
    
    /**
     * Request overlay permission with user-friendly dialog
     */
    fun requestOverlayPermission(
        context: Context,
        showRationale: Boolean = true
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            onPermissionResult?.invoke(true)
            return
        }
        
        if (isOverlayPermissionGranted(context)) {
            onPermissionResult?.invoke(true)
            return
        }
        
        if (showRationale && shouldShowRationale(context)) {
            showPermissionRationale(context)
        } else {
            launchPermissionSettings(context)
        }
    }
    
    /**
     * Show rationale dialog explaining why overlay permission is needed
     */
    private fun showPermissionRationale(context: Context) {
        if (context !is FragmentActivity) {
            launchPermissionSettings(context)
            return
        }
        
        AlertDialog.Builder(context)
            .setTitle("語音錄音權限")
            .setMessage("""
                為了提供語音識別功能，需要允許 BreezeApp 在其他應用程式上層顯示。
                
                這個權限用於：
                • 啟動語音錄音引擎
                • 顯示錄音狀態指示器
                • 確保語音功能正常運作
                
                請在下一個畫面中允許「顯示在其他應用程式上層」權限。
            """.trimIndent())
            .setPositiveButton("前往設定") { _, _ ->
                launchPermissionSettings(context)
            }
            .setNegativeButton("取消") { _, _ ->
                incrementDenialCount(context)
                onPermissionResult?.invoke(false)
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Launch system settings for overlay permission
     */
    private fun launchPermissionSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            permissionLauncher?.launch(intent) ?: run {
                // Fallback for non-Activity contexts
                context.startActivity(intent)
            }
            
            markPermissionRequested(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch overlay permission settings", e)
            onPermissionResult?.invoke(false)
        }
    }
    
    /**
     * Check if we should show permission rationale
     */
    private fun shouldShowRationale(context: Context): Boolean {
        val prefs = context.getSharedPreferences("overlay_permission", Context.MODE_PRIVATE)
        val denialCount = prefs.getInt(PREF_KEY_OVERLAY_DENIED_COUNT, 0)
        val wasRequested = prefs.getBoolean(PREF_KEY_OVERLAY_REQUESTED, false)
        
        return !wasRequested || denialCount < MAX_DENIAL_COUNT
    }
    
    /**
     * Mark that overlay permission was requested
     */
    private fun markPermissionRequested(context: Context) {
        context.getSharedPreferences("overlay_permission", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_KEY_OVERLAY_REQUESTED, true)
            .apply()
    }
    
    /**
     * Increment denial count
     */
    private fun incrementDenialCount(context: Context) {
        val prefs = context.getSharedPreferences("overlay_permission", Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(PREF_KEY_OVERLAY_DENIED_COUNT, 0)
        prefs.edit()
            .putInt(PREF_KEY_OVERLAY_DENIED_COUNT, currentCount + 1)
            .apply()
    }
    
    /**
     * Reset denial count (call when permission is granted)
     */
    fun resetDenialCount(context: Context) {
        context.getSharedPreferences("overlay_permission", Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_KEY_OVERLAY_DENIED_COUNT, 0)
            .apply()
    }
    
    /**
     * Check if overlay permission is critically needed for microphone functionality
     */
    fun isOverlayPermissionCritical(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q // Android 10+ requires it for FGS_MICROPHONE
    }
    
    /**
     * Get user-friendly explanation for overlay permission
     */
    fun getOverlayPermissionExplanation(): String {
        return """
            BreezeApp 需要「顯示在其他應用程式上層」權限來：
            
            1. 啟動語音識別引擎
            2. 在錄音時顯示狀態指示器
            3. 確保語音功能在背景正常運作
            
            這個權限不會用於廣告或其他干擾性內容。
        """.trimIndent()
    }
}