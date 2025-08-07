package com.mtkresearch.breezeapp.core.debug

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug utility for troubleshooting foreground service and overlay permission issues
 * Use this in BreezeApp-engine to diagnose FGS_MICROPHONE failures
 */
object ForegroundServiceDebugger {
    
    private const val TAG = "FGSDebugger"
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * Comprehensive diagnostic check before starting FGS_MICROPHONE
     * Call this in your engine app before calling startForeground()
     */
    fun diagnosticCheck(context: Context, serviceName: String = "BreezeAppEngineService"): DiagnosticResult {
        val timestamp = dateFormatter.format(Date())
        Log.d(TAG, "=== FGS_MICROPHONE Diagnostic Check [$timestamp] ===")
        
        val result = DiagnosticResult()
        
        // 1. Check overlay permission
        result.overlayPermissionGranted = checkOverlayPermission(context)
        
        // 2. Check app foreground state
        result.appInForeground = checkAppForegroundState(context)
        
        // 3. Check microphone permission
        result.microphonePermissionGranted = checkMicrophonePermission(context)
        
        // 4. Check service state
        result.serviceRunning = checkServiceState(context, serviceName)
        
        // 5. System information
        result.androidVersion = Build.VERSION.SDK_INT
        result.deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
        
        // 6. Check battery optimization
        result.batteryOptimized = checkBatteryOptimization(context)
        
        // Log comprehensive results
        logDiagnosticResults(result)
        
        return result
    }
    
    /**
     * Check overlay permission status
     */
    private fun checkOverlayPermission(context: Context): Boolean {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
        
        Log.d(TAG, "📱 Overlay Permission: ${if (granted) "✅ GRANTED" else "❌ DENIED"}")
        if (!granted) {
            Log.w(TAG, "   ⚠️  This will likely cause FGS_MICROPHONE to fail on Android 10+")
        }
        return granted
    }
    
    /**
     * Check if app is in foreground state
     */
    private fun checkAppForegroundState(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        
        val inForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ method
            val runningAppProcesses = activityManager.runningAppProcesses
            val myProcess = runningAppProcesses?.find { it.pid == android.os.Process.myPid() }
            val importance = myProcess?.importance ?: ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
            
            val foregroundStates = listOf(
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE,
                ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
            )
            
            val inForeground = importance in foregroundStates
            Log.d(TAG, "🔄 App Foreground State: ${if (inForeground) "✅ FOREGROUND" else "❌ BACKGROUND"}")
            Log.d(TAG, "   └── Process importance: $importance")
            
            inForeground
        } else {
            // Fallback for older Android versions
            true
        }
        
        if (!inForeground) {
            Log.w(TAG, "   ⚠️  App not in foreground - FGS_MICROPHONE requires foreground state")
        }
        
        return inForeground
    }
    
    /**
     * Check microphone permission
     */
    private fun checkMicrophonePermission(context: Context): Boolean {
        val granted = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == 
                     android.content.pm.PackageManager.PERMISSION_GRANTED
        
        Log.d(TAG, "🎤 Microphone Permission: ${if (granted) "✅ GRANTED" else "❌ DENIED"}")
        return granted
    }
    
    /**
     * Check if service is running
     */
    private fun checkServiceState(context: Context, serviceName: String): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = activityManager.getRunningServices(Integer.MAX_VALUE)
        val running = services.any { it.service.className.contains(serviceName) }
        
        Log.d(TAG, "⚙️  Service State: ${if (running) "✅ RUNNING" else "❌ STOPPED"}")
        return running
    }
    
    /**
     * Check battery optimization status
     */
    private fun checkBatteryOptimization(context: Context): Boolean {
        val batteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !(context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(context.packageName)
        } else {
            false
        }
        
        Log.d(TAG, "🔋 Battery Optimized: ${if (batteryOptimized) "❌ YES (may affect FGS)" else "✅ NO"}")
        return batteryOptimized
    }
    
    /**
     * Log the attempt to start foreground service
     */
    fun logForegroundServiceStart(
        serviceName: String,
        notificationId: Int,
        foregroundServiceType: Int
    ) {
        val timestamp = dateFormatter.format(Date())
        Log.d(TAG, "🚀 Starting Foreground Service [$timestamp]")
        Log.d(TAG, "   └── Service: $serviceName")
        Log.d(TAG, "   └── Notification ID: $notificationId")
        Log.d(TAG, "   └── FGS Type: ${getForegroundServiceTypeName(foregroundServiceType)}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "   ⚠️  Android 10+ detected - strict FGS rules apply")
        }
    }
    
    /**
     * Log foreground service success/failure
     */
    fun logForegroundServiceResult(success: Boolean, error: Throwable? = null) {
        val timestamp = dateFormatter.format(Date())
        if (success) {
            Log.d(TAG, "✅ Foreground Service Started Successfully [$timestamp]")
        } else {
            Log.e(TAG, "❌ Foreground Service Failed to Start [$timestamp]")
            error?.let { 
                Log.e(TAG, "   └── Error: ${it.message}", it)
                
                // Specific SecurityException analysis
                if (it is SecurityException && it.message?.contains("microphone") == true) {
                    Log.e(TAG, "   🔍 SecurityException Analysis:")
                    Log.e(TAG, "      • This is likely due to FGS_MICROPHONE restrictions")
                    Log.e(TAG, "      • App must be in foreground when starting FGS")
                    Log.e(TAG, "      • Overlay permission may be required")
                    Log.e(TAG, "      • Check if user interaction triggered this request")
                }
            }
        }
    }
    
    /**
     * Get human-readable foreground service type name
     */
    private fun getForegroundServiceTypeName(type: Int): String {
        return when (type) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE -> "MICROPHONE"
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA -> "CAMERA"
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION -> "LOCATION"
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL -> "PHONE_CALL"
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK -> "MEDIA_PLAYBACK"
            else -> "UNKNOWN ($type)"
        }
    }
    
    /**
     * Log comprehensive diagnostic results
     */
    private fun logDiagnosticResults(result: DiagnosticResult) {
        Log.d(TAG, "📊 === Diagnostic Summary ===")
        Log.d(TAG, "   Overlay Permission: ${formatStatus(result.overlayPermissionGranted)}")
        Log.d(TAG, "   App Foreground: ${formatStatus(result.appInForeground)}")
        Log.d(TAG, "   Microphone Permission: ${formatStatus(result.microphonePermissionGranted)}")
        Log.d(TAG, "   Service Running: ${formatStatus(result.serviceRunning)}")
        Log.d(TAG, "   Battery Optimized: ${formatStatus(!result.batteryOptimized)}")
        Log.d(TAG, "   Android Version: ${result.androidVersion}")
        Log.d(TAG, "   Device: ${result.deviceInfo}")
        
        val readyForFGS = result.overlayPermissionGranted && 
                         result.appInForeground && 
                         result.microphonePermissionGranted
        
        Log.d(TAG, "🎯 FGS_MICROPHONE Ready: ${formatStatus(readyForFGS)}")
        
        if (!readyForFGS) {
            Log.w(TAG, "⚠️  Recommendations:")
            if (!result.overlayPermissionGranted) {
                Log.w(TAG, "   • Request overlay permission in main app")
            }
            if (!result.appInForeground) {
                Log.w(TAG, "   • Ensure engine app is brought to foreground before FGS")
            }
            if (!result.microphonePermissionGranted) {
                Log.w(TAG, "   • Request microphone permission")
            }
        }
        
        Log.d(TAG, "=== End Diagnostic Check ===")
    }
    
    private fun formatStatus(status: Boolean): String {
        return if (status) "✅ OK" else "❌ ISSUE"
    }
    
    /**
     * Data class to hold diagnostic results
     */
    data class DiagnosticResult(
        var overlayPermissionGranted: Boolean = false,
        var appInForeground: Boolean = false,
        var microphonePermissionGranted: Boolean = false,
        var serviceRunning: Boolean = false,
        var batteryOptimized: Boolean = false,
        var androidVersion: Int = 0,
        var deviceInfo: String = ""
    ) {
        val isReadyForFGS: Boolean
            get() = overlayPermissionGranted && appInForeground && microphonePermissionGranted
    }
}