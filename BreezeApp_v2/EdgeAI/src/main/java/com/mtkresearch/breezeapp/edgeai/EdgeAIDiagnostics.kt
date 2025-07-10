package com.mtkresearch.breezeapp.edgeai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Internal Diagnostics Tool for EdgeAI SDK
 * 
 * This utility class provides internal diagnostic capabilities for the EdgeAI SDK
 * to troubleshoot service connection issues. It is used internally by the SDK
 * and not exposed to client applications.
 */
internal object EdgeAIDiagnostics {
    
    private const val TAG = "EdgeAIDiagnostics"
    private const val ROUTER_PACKAGE = "com.mtkresearch.breezeapp.router"
    private const val ROUTER_SERVICE_CLASS = "com.mtkresearch.breezeapp.router.AIRouterService"
    private const val REQUIRED_PERMISSION = "com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE"
    
    /**
     * Performs comprehensive diagnostic checks internally for SDK troubleshooting
     */
    internal fun runInternalDiagnostics(context: Context): InternalDiagnosticReport {
        Log.d(TAG, "🔍 Running internal SDK diagnostics...")
        
        val report = InternalDiagnosticReport()
        
        // Check critical requirements for service connection
        report.isServicePackageInstalled = checkServicePackageInstalled(context)
        report.isServiceComponentDeclared = checkServiceComponentDeclared(context)
        report.hasRequiredPermission = checkPermissions(context)
        report.signaturesMatch = checkSignatures(context)
        report.canResolveServiceIntent = checkServiceIntentResolution(context)
        
        Log.d(TAG, "📋 Internal diagnostic completed: ${report.isReadyForConnection}")
        return report
    }
    
    /**
     * Quick health check for SDK initialization
     */
    internal fun isServiceAvailable(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo(ROUTER_PACKAGE, 0)
            
            val intent = Intent().apply {
                component = ComponentName(ROUTER_PACKAGE, ROUTER_SERVICE_CLASS)
            }
            val resolveInfo = packageManager.resolveService(intent, 0)
            resolveInfo != null
        } catch (e: Exception) {
            Log.w(TAG, "Service availability check failed: ${e.message}")
            false
        }
    }
    
    /**
     * Generate user-friendly error message based on diagnostic results
     */
    internal fun generateUserFriendlyError(context: Context): String {
        val report = runInternalDiagnostics(context)
        
        return when {
            !report.isServicePackageInstalled -> 
                "AI service is not installed. Please install the required AI service package."
            
            !report.hasRequiredPermission -> 
                "Missing required permissions. Please check your app configuration."
                
            !report.signaturesMatch -> 
                "Service compatibility issue. Please ensure you're using compatible app versions."
                
            !report.canResolveServiceIntent -> 
                "AI service is not available. Please check if the service is properly installed."
                
            else -> 
                "Unable to connect to AI service. Please try again or restart the app."
        }
    }
    
    private fun checkServicePackageInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(ROUTER_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    private fun checkServiceComponentDeclared(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(ROUTER_PACKAGE, ROUTER_SERVICE_CLASS)
            }
            val resolveInfo = context.packageManager.resolveService(intent, PackageManager.MATCH_ALL)
            resolveInfo != null
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkPermissions(context: Context): Boolean {
        return context.checkCallingOrSelfPermission(REQUIRED_PERMISSION) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun checkSignatures(context: Context): Boolean {
        return try {
            val packageManager = context.packageManager
            val clientPackage = context.packageName
            
            val clientSignatures = packageManager.getPackageInfo(
                clientPackage, PackageManager.GET_SIGNATURES
            ).signatures
            
            val serviceSignatures = packageManager.getPackageInfo(
                ROUTER_PACKAGE, PackageManager.GET_SIGNATURES
            ).signatures
            
            clientSignatures.contentEquals(serviceSignatures)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun checkServiceIntentResolution(context: Context): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(ROUTER_PACKAGE, ROUTER_SERVICE_CLASS)
            }
            
            val resolveInfo = context.packageManager.resolveService(intent, 0)
            resolveInfo != null
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Public Diagnostics API for Developers (Optional)
 * 
 * Provides optional diagnostic capabilities for developers who need to troubleshoot
 * service connection issues. This is intended for development and debugging only.
 */
object EdgeAIDebug {
    
    /**
     * Check if the AI service is available and properly configured.
     * This is a lightweight check for development purposes.
     * 
     * @param context Application context
     * @return true if service appears to be available, false otherwise
     */
    fun isServiceAvailable(context: Context): Boolean {
        return EdgeAIDiagnostics.isServiceAvailable(context)
    }
    
    /**
     * Get a user-friendly error message if service is not available.
     * Useful for showing helpful error messages during development.
     * 
     * @param context Application context
     * @return User-friendly error message explaining potential issues
     */
    fun getDiagnosticMessage(context: Context): String {
        return EdgeAIDiagnostics.generateUserFriendlyError(context)
    }
}

/**
 * Internal diagnostic report for SDK use only
 */
internal data class InternalDiagnosticReport(
    var isServicePackageInstalled: Boolean = false,
    var isServiceComponentDeclared: Boolean = false,
    var hasRequiredPermission: Boolean = false,
    var signaturesMatch: Boolean = false,
    var canResolveServiceIntent: Boolean = false
) {
    val isReadyForConnection: Boolean
        get() = isServicePackageInstalled && 
                isServiceComponentDeclared && 
                hasRequiredPermission && 
                canResolveServiceIntent
} 