package com.mtkresearch.breezeapp.core.debug

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Analyzer to compare behavior between BreezeApp and BreezeApp-client
 * Helps identify differences that might cause microphone permission issues
 */
object AppComparisonAnalyzer {
    
    private const val TAG = "AppComparison"
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    /**
     * Compare both apps' configurations and permissions
     */
    fun compareApps(
        context: Context,
        workingAppPackage: String = "com.mtkresearch.breezeapp.engine.client", // BreezeApp-client
        failingAppPackage: String = "com.mtkresearch.breezeapp" // BreezeApp
    ): ComparisonResult {
        val timestamp = dateFormatter.format(Date())
        Log.d(TAG, "=== App Comparison Analysis [$timestamp] ===")
        
        val result = ComparisonResult()
        
        try {
            // Compare overlay permissions
            result.workingAppOverlay = checkAppOverlayPermission(context, workingAppPackage)
            result.failingAppOverlay = checkAppOverlayPermission(context, failingAppPackage)
            
            // Compare microphone permissions
            result.workingAppMicrophone = checkAppMicrophonePermission(context, workingAppPackage)
            result.failingAppMicrophone = checkAppMicrophonePermission(context, failingAppPackage)
            
            // Compare manifest permissions
            result.workingAppManifest = analyzeManifestPermissions(context, workingAppPackage)
            result.failingAppManifest = analyzeManifestPermissions(context, failingAppPackage)
            
            // Compare app info
            result.workingAppInfo = getAppInfo(context, workingAppPackage)
            result.failingAppInfo = getAppInfo(context, failingAppPackage)
            
            // Compare launch patterns
            result.workingAppLaunchPattern = analyzeLaunchPattern(context, workingAppPackage)
            result.failingAppLaunchPattern = analyzeLaunchPattern(context, failingAppPackage)
            
            logComparisonResults(result, workingAppPackage, failingAppPackage)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during app comparison", e)
        }
        
        return result
    }
    
    /**
     * Check overlay permission for specific app
     */
    private fun checkAppOverlayPermission(context: Context, packageName: String): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Note: This checks current app's overlay permission
                // For other apps, you'd need system-level access
                if (packageName == context.packageName) {
                    Settings.canDrawOverlays(context)
                } else {
                    // Can't directly check other app's overlay permission without system access
                    // This is a limitation - we can only check our own app
                    false // Assume not granted for comparison
                }
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking overlay permission for $packageName", e)
            false
        }
    }
    
    /**
     * Check microphone permission for specific app
     */
    private fun checkAppMicrophonePermission(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            pm.checkPermission(
                android.Manifest.permission.RECORD_AUDIO,
                packageName
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking microphone permission for $packageName", e)
            false
        }
    }
    
    /**
     * Analyze manifest permissions
     */
    private fun analyzeManifestPermissions(context: Context, packageName: String): ManifestAnalysis {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            
            val requestedPermissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
            
            ManifestAnalysis(
                hasRecordAudio = requestedPermissions.contains(android.Manifest.permission.RECORD_AUDIO),
                hasSystemAlertWindow = requestedPermissions.contains(android.Manifest.permission.SYSTEM_ALERT_WINDOW),
                hasCamera = requestedPermissions.contains(android.Manifest.permission.CAMERA),
                hasForegroundService = requestedPermissions.contains(android.Manifest.permission.FOREGROUND_SERVICE),
                allPermissions = requestedPermissions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing manifest for $packageName", e)
            ManifestAnalysis()
        }
    }
    
    /**
     * Get basic app info
     */
    private fun getAppInfo(context: Context, packageName: String): AppInfo {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val packageInfo = pm.getPackageInfo(packageName, 0)
            
            AppInfo(
                targetSdk = appInfo.targetSdkVersion,
                minSdk = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    appInfo.minSdkVersion
                } else {
                    0
                },
                versionCode = packageInfo.versionCode.toLong(),
                versionName = packageInfo.versionName ?: "unknown",
                isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                isDebuggable = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting app info for $packageName", e)
            AppInfo()
        }
    }
    
    /**
     * Analyze launch pattern differences
     */
    private fun analyzeLaunchPattern(context: Context, packageName: String): LaunchPatternAnalysis {
        return try {
            val pm = context.packageManager
            val intent = pm.getLaunchIntentForPackage(packageName)
            
            LaunchPatternAnalysis(
                hasLaunchIntent = intent != null,
                launchCategory = intent?.categories?.firstOrNull() ?: "none",
                launchAction = intent?.action ?: "none"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing launch pattern for $packageName", e)
            LaunchPatternAnalysis()
        }
    }
    
    /**
     * Log detailed comparison results
     */
    private fun logComparisonResults(
        result: ComparisonResult,
        workingApp: String,
        failingApp: String
    ) {
        Log.d(TAG, "📊 === Comparison Results ===")
        Log.d(TAG, "Working App: $workingApp")
        Log.d(TAG, "Failing App: $failingApp")
        Log.d(TAG, "")
        
        // Permission comparison
        Log.d(TAG, "🔐 Permission Comparison:")
        Log.d(TAG, "   Overlay Permission:")
        Log.d(TAG, "     Working: ${formatStatus(result.workingAppOverlay)}")
        Log.d(TAG, "     Failing: ${formatStatus(result.failingAppOverlay)}")
        Log.d(TAG, "   Microphone Permission:")
        Log.d(TAG, "     Working: ${formatStatus(result.workingAppMicrophone)}")
        Log.d(TAG, "     Failing: ${formatStatus(result.failingAppMicrophone)}")
        
        // Manifest comparison
        Log.d(TAG, "📋 Manifest Comparison:")
        logManifestComparison("Working", result.workingAppManifest)
        logManifestComparison("Failing", result.failingAppManifest)
        
        // App info comparison
        Log.d(TAG, "ℹ️ App Info Comparison:")
        logAppInfoComparison("Working", result.workingAppInfo)
        logAppInfoComparison("Failing", result.failingAppInfo)
        
        // Key differences
        Log.d(TAG, "🔍 Key Differences:")
        if (result.workingAppOverlay != result.failingAppOverlay) {
            Log.w(TAG, "   ⚠️  OVERLAY PERMISSION MISMATCH - This is likely the root cause!")
        }
        if (result.workingAppMicrophone != result.failingAppMicrophone) {
            Log.w(TAG, "   ⚠️  MICROPHONE PERMISSION MISMATCH")
        }
        if (result.workingAppInfo.targetSdk != result.failingAppInfo.targetSdk) {
            Log.w(TAG, "   ⚠️  TARGET SDK MISMATCH (${result.workingAppInfo.targetSdk} vs ${result.failingAppInfo.targetSdk})")
        }
        
        // Recommendations
        Log.d(TAG, "💡 Recommendations:")
        if (!result.failingAppOverlay && result.workingAppOverlay) {
            Log.d(TAG, "   1. Grant overlay permission to failing app")
        }
        if (!result.failingAppMicrophone && result.workingAppMicrophone) {
            Log.d(TAG, "   2. Grant microphone permission to failing app")
        }
        if (result.workingAppInfo.targetSdk != result.failingAppInfo.targetSdk) {
            Log.d(TAG, "   3. Consider target SDK differences in FGS behavior")
        }
        
        Log.d(TAG, "=== End Comparison ===")
    }
    
    private fun logManifestComparison(appType: String, manifest: ManifestAnalysis) {
        Log.d(TAG, "   $appType App Manifest:")
        Log.d(TAG, "     RECORD_AUDIO: ${formatStatus(manifest.hasRecordAudio)}")
        Log.d(TAG, "     SYSTEM_ALERT_WINDOW: ${formatStatus(manifest.hasSystemAlertWindow)}")
        Log.d(TAG, "     CAMERA: ${formatStatus(manifest.hasCamera)}")
        Log.d(TAG, "     FOREGROUND_SERVICE: ${formatStatus(manifest.hasForegroundService)}")
        Log.d(TAG, "     Total permissions: ${manifest.allPermissions.size}")
    }
    
    private fun logAppInfoComparison(appType: String, info: AppInfo) {
        Log.d(TAG, "   $appType App Info:")
        Log.d(TAG, "     Target SDK: ${info.targetSdk}")
        Log.d(TAG, "     Min SDK: ${info.minSdk}")
        Log.d(TAG, "     Version: ${info.versionName} (${info.versionCode})")
        Log.d(TAG, "     System App: ${info.isSystemApp}")
        Log.d(TAG, "     Debuggable: ${info.isDebuggable}")
    }
    
    private fun formatStatus(status: Boolean): String {
        return if (status) "✅ YES" else "❌ NO"
    }
    
    // Data classes for holding comparison results
    data class ComparisonResult(
        var workingAppOverlay: Boolean = false,
        var failingAppOverlay: Boolean = false,
        var workingAppMicrophone: Boolean = false,
        var failingAppMicrophone: Boolean = false,
        var workingAppManifest: ManifestAnalysis = ManifestAnalysis(),
        var failingAppManifest: ManifestAnalysis = ManifestAnalysis(),
        var workingAppInfo: AppInfo = AppInfo(),
        var failingAppInfo: AppInfo = AppInfo(),
        var workingAppLaunchPattern: LaunchPatternAnalysis = LaunchPatternAnalysis(),
        var failingAppLaunchPattern: LaunchPatternAnalysis = LaunchPatternAnalysis()
    )
    
    data class ManifestAnalysis(
        val hasRecordAudio: Boolean = false,
        val hasSystemAlertWindow: Boolean = false,
        val hasCamera: Boolean = false,
        val hasForegroundService: Boolean = false,
        val allPermissions: List<String> = emptyList()
    )
    
    data class AppInfo(
        val targetSdk: Int = 0,
        val minSdk: Int = 0,
        val versionCode: Long = 0,
        val versionName: String = "",
        val isSystemApp: Boolean = false,
        val isDebuggable: Boolean = false
    )
    
    data class LaunchPatternAnalysis(
        val hasLaunchIntent: Boolean = false,
        val launchCategory: String = "",
        val launchAction: String = ""
    )
}