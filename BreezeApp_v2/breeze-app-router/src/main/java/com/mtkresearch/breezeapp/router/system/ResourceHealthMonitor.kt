package com.mtkresearch.breezeapp.router.system

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Resource Health Monitor for proactive resource management.
 * 
 * Monitors the health of native libraries, memory usage, and service state
 * to provide early warning and prevent resource-related failures.
 * 
 * Integrates with GlobalLibraryTracker to provide comprehensive resource oversight.
 */
class ResourceHealthMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "ResourceHealthMonitor"
        
        // Health thresholds
        private const val CRITICAL_MEMORY_THRESHOLD_MB = 512L
        private const val WARNING_MEMORY_THRESHOLD_MB = 256L
        private const val MAX_SAFE_FORCE_STOP_COUNT = 3
        private const val MAX_SAFE_LIBRARY_COUNT = 5
        private const val CRITICAL_INFERENCE_STUCK_TIME_MS = 30000L // 30 seconds
        
        // Monitoring intervals
        private const val HEALTH_CHECK_INTERVAL_MS = 30000L // 30 seconds
        private const val MEMORY_CHECK_INTERVAL_MS = 60000L // 1 minute
    }
    
    private val lastHealthCheck = AtomicLong(0)
    private val lastMemoryCheck = AtomicLong(0)
    private var lastHealthReport: HealthReport? = null
    
    /**
     * Health status levels
     */
    enum class HealthStatus {
        HEALTHY,    // All systems normal
        WARNING,    // Minor issues detected
        CRITICAL,   // Serious issues requiring attention
        ERROR       // System errors detected
    }
    
    /**
     * Comprehensive health report
     */
    data class HealthReport(
        val timestamp: Long = System.currentTimeMillis(),
        val overallHealth: HealthStatus,
        val nativeLibraryHealth: HealthStatus,
        val memoryHealth: HealthStatus,
        val serviceHealth: HealthStatus,
        val diagnosticInfo: Map<String, Any>,
        val recommendations: List<String>,
        val criticalIssues: List<String>
    ) {
        fun isCritical(): Boolean = overallHealth == HealthStatus.CRITICAL || overallHealth == HealthStatus.ERROR
        fun hasWarnings(): Boolean = overallHealth == HealthStatus.WARNING || isCritical()
        
        fun getSummary(): String {
            return "Health: $overallHealth, Memory: $memoryHealth, Native: $nativeLibraryHealth, Service: $serviceHealth"
        }
    }
    
    /**
     * Check native library health status
     */
    fun checkNativeLibraryHealth(): HealthStatus {
        return try {
            val diagnosticInfo = GlobalLibraryTracker.getDiagnosticInfo()
            val forceStopCount = diagnosticInfo["forceStopCount"] as? Int ?: 0
            val wasInferenceActive = diagnosticInfo["wasInferenceActive"] as? Boolean ?: false
            val isLibraryLoaded = diagnosticInfo["isLibraryActuallyLoaded"] as? Boolean ?: false
            val lastLoadTime = diagnosticInfo["lastLoadTime"] as? Long ?: 0
            
            Log.d(TAG, "Native library diagnostics: forceStops=$forceStopCount, inferenceActive=$wasInferenceActive, loaded=$isLibraryLoaded")
            
            when {
                forceStopCount >= MAX_SAFE_FORCE_STOP_COUNT -> {
                    Log.w(TAG, "CRITICAL: Excessive force stops detected ($forceStopCount)")
                    HealthStatus.CRITICAL
                }
                wasInferenceActive && (System.currentTimeMillis() - lastLoadTime) > CRITICAL_INFERENCE_STUCK_TIME_MS -> {
                    Log.w(TAG, "CRITICAL: Inference appears stuck for extended period")
                    HealthStatus.CRITICAL
                }
                forceStopCount > 1 -> {
                    Log.w(TAG, "WARNING: Multiple force stops detected ($forceStopCount)")
                    HealthStatus.WARNING
                }
                wasInferenceActive -> {
                    Log.d(TAG, "WARNING: Inference currently active")
                    HealthStatus.WARNING
                }
                !isLibraryLoaded -> {
                    Log.d(TAG, "INFO: Native library not loaded (normal)")
                    HealthStatus.HEALTHY
                }
                else -> {
                    Log.d(TAG, "Native library health: HEALTHY")
                    HealthStatus.HEALTHY
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking native library health", e)
            HealthStatus.ERROR
        }
    }
    
    /**
     * Check memory health status
     */
    fun checkMemoryHealth(): HealthStatus {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            val availableMemoryMB = memoryInfo.availMem / (1024 * 1024)
            val totalMemoryMB = memoryInfo.totalMem / (1024 * 1024)
            val usedMemoryMB = totalMemoryMB - availableMemoryMB
            val memoryUsagePercent = (usedMemoryMB * 100) / totalMemoryMB
            
            Log.d(TAG, "Memory status: ${availableMemoryMB}MB available, ${usedMemoryMB}MB used (${memoryUsagePercent}%)")
            
            when {
                availableMemoryMB < CRITICAL_MEMORY_THRESHOLD_MB -> {
                    Log.w(TAG, "CRITICAL: Low memory - ${availableMemoryMB}MB available")
                    HealthStatus.CRITICAL
                }
                availableMemoryMB < WARNING_MEMORY_THRESHOLD_MB -> {
                    Log.w(TAG, "WARNING: Memory getting low - ${availableMemoryMB}MB available")
                    HealthStatus.WARNING
                }
                memoryUsagePercent > 90 -> {
                    Log.w(TAG, "WARNING: High memory usage - ${memoryUsagePercent}%")
                    HealthStatus.WARNING
                }
                else -> {
                    Log.d(TAG, "Memory health: HEALTHY")
                    HealthStatus.HEALTHY
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking memory health", e)
            HealthStatus.ERROR
        }
    }
    
    /**
     * Check service health status
     */
    fun checkServiceHealth(): HealthStatus {
        return try {
            // Check if service components are properly initialized
            val nativeLibraryGuardian = NativeLibraryGuardian.getInstance()
            val loadedLibraryCount = nativeLibraryGuardian.getLoadedLibraryCount()
            
            Log.d(TAG, "Service status: ${loadedLibraryCount} libraries loaded")
            
            when {
                loadedLibraryCount > MAX_SAFE_LIBRARY_COUNT -> {
                    Log.w(TAG, "WARNING: Many libraries loaded ($loadedLibraryCount)")
                    HealthStatus.WARNING
                }
                loadedLibraryCount < 0 -> {
                    Log.e(TAG, "ERROR: Invalid library count")
                    HealthStatus.ERROR
                }
                else -> {
                    Log.d(TAG, "Service health: HEALTHY")
                    HealthStatus.HEALTHY
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service health", e)
            HealthStatus.ERROR
        }
    }
    
    /**
     * Perform comprehensive health check
     */
    fun performHealthCheck(forceCheck: Boolean = false): HealthReport {
        val currentTime = System.currentTimeMillis()
        
        // Return cached result if recent and not forced
        if (!forceCheck && 
            lastHealthReport != null && 
            (currentTime - lastHealthCheck.get()) < HEALTH_CHECK_INTERVAL_MS) {
            return lastHealthReport!!
        }
        
        Log.d(TAG, "Performing comprehensive health check...")
        
        val nativeHealth = checkNativeLibraryHealth()
        val memoryHealth = checkMemoryHealth()
        val serviceHealth = checkServiceHealth()
        
        // Determine overall health (worst of all components)
        val overallHealth = listOf(nativeHealth, memoryHealth, serviceHealth)
            .maxByOrNull { it.ordinal } ?: HealthStatus.HEALTHY
        
        // Gather diagnostic information
        val diagnosticInfo = mutableMapOf<String, Any>()
        try {
            diagnosticInfo.putAll(GlobalLibraryTracker.getDiagnosticInfo())
            diagnosticInfo["memoryHealthStatus"] = memoryHealth.name
            diagnosticInfo["serviceHealthStatus"] = serviceHealth.name
            diagnosticInfo["loadedLibraryCount"] = NativeLibraryGuardian.getInstance().getLoadedLibraryCount()
        } catch (e: Exception) {
            Log.w(TAG, "Error gathering diagnostic info", e)
            diagnosticInfo["diagnosticError"] = e.message ?: "Unknown error"
        }
        
        // Generate recommendations and critical issues
        val recommendations = generateRecommendations(nativeHealth, memoryHealth, serviceHealth, diagnosticInfo)
        val criticalIssues = generateCriticalIssues(nativeHealth, memoryHealth, serviceHealth, diagnosticInfo)
        
        val healthReport = HealthReport(
            timestamp = currentTime,
            overallHealth = overallHealth,
            nativeLibraryHealth = nativeHealth,
            memoryHealth = memoryHealth,
            serviceHealth = serviceHealth,
            diagnosticInfo = diagnosticInfo,
            recommendations = recommendations,
            criticalIssues = criticalIssues
        )
        
        lastHealthReport = healthReport
        lastHealthCheck.set(currentTime)
        
        Log.i(TAG, "Health check completed: ${healthReport.getSummary()}")
        if (healthReport.isCritical()) {
            Log.w(TAG, "CRITICAL ISSUES DETECTED: ${criticalIssues.joinToString(", ")}")
        }
        
        return healthReport
    }
    
    /**
     * Generate recommendations based on health status
     */
    private fun generateRecommendations(
        nativeHealth: HealthStatus,
        memoryHealth: HealthStatus,
        serviceHealth: HealthStatus,
        diagnosticInfo: Map<String, Any>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        // Native library recommendations
        when (nativeHealth) {
            HealthStatus.CRITICAL -> {
                recommendations.add("Restart service to clear stuck native resources")
                recommendations.add("Consider reducing inference frequency")
            }
            HealthStatus.WARNING -> {
                recommendations.add("Monitor native library usage closely")
                recommendations.add("Consider preventive cleanup")
            }
            else -> {}
        }
        
        // Memory recommendations
        when (memoryHealth) {
            HealthStatus.CRITICAL -> {
                recommendations.add("Free memory immediately - close other apps")
                recommendations.add("Restart device if memory issues persist")
            }
            HealthStatus.WARNING -> {
                recommendations.add("Monitor memory usage")
                recommendations.add("Consider reducing concurrent operations")
            }
            else -> {}
        }
        
        // Service recommendations
        when (serviceHealth) {
            HealthStatus.WARNING -> {
                recommendations.add("Monitor service resource usage")
            }
            HealthStatus.ERROR -> {
                recommendations.add("Restart service to recover from errors")
            }
            else -> {}
        }
        
        // Force stop specific recommendations
        val forceStopCount = diagnosticInfo["forceStopCount"] as? Int ?: 0
        if (forceStopCount > 2) {
            recommendations.add("Frequent force stops detected - consider graceful shutdown methods")
        }
        
        return recommendations
    }
    
    /**
     * Generate critical issues list
     */
    private fun generateCriticalIssues(
        nativeHealth: HealthStatus,
        memoryHealth: HealthStatus,
        serviceHealth: HealthStatus,
        diagnosticInfo: Map<String, Any>
    ): List<String> {
        val criticalIssues = mutableListOf<String>()
        
        if (nativeHealth == HealthStatus.CRITICAL) {
            criticalIssues.add("Native library in critical state")
        }
        
        if (memoryHealth == HealthStatus.CRITICAL) {
            criticalIssues.add("System memory critically low")
        }
        
        if (serviceHealth == HealthStatus.ERROR) {
            criticalIssues.add("Service errors detected")
        }
        
        val forceStopCount = diagnosticInfo["forceStopCount"] as? Int ?: 0
        if (forceStopCount >= MAX_SAFE_FORCE_STOP_COUNT) {
            criticalIssues.add("Excessive force stops ($forceStopCount)")
        }
        
        val wasInferenceActive = diagnosticInfo["wasInferenceActive"] as? Boolean ?: false
        if (wasInferenceActive) {
            criticalIssues.add("Inference process may be stuck")
        }
        
        return criticalIssues
    }
    
    /**
     * Check if preventive maintenance is recommended
     */
    fun shouldPerformPreventiveMaintenance(): Boolean {
        val healthReport = performHealthCheck()
        return healthReport.hasWarnings() || 
               healthReport.recommendations.isNotEmpty() ||
               (healthReport.diagnosticInfo["forceStopCount"] as? Int ?: 0) > 1
    }
    
    /**
     * Get quick health status without full check
     */
    fun getQuickHealthStatus(): HealthStatus {
        return lastHealthReport?.overallHealth ?: HealthStatus.HEALTHY
    }
}