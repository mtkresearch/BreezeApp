package com.mtkresearch.breezeapp.core.debug

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * Example showing how to integrate debugging into BreezeApp-engine service
 * Copy this pattern to your actual engine service implementation
 */
class EngineServiceDebugExample : Service() {
    
    companion object {
        private const val TAG = "EngineService"
        private const val NOTIFICATION_ID = 1001
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null // Replace with your actual AIDL binder
    }
    
    /**
     * Example method for starting microphone recording with comprehensive debugging
     * Replace this with your actual microphone recording implementation
     */
    fun startMicrophoneRecording() {
        Log.d(TAG, "🎤 Microphone recording requested")
        
        // Step 1: Run diagnostic check BEFORE attempting to start FGS
        val diagnostic = ForegroundServiceDebugger.diagnosticCheck(this, "EngineService")
        
        if (!diagnostic.isReadyForFGS) {
            Log.e(TAG, "❌ Not ready for FGS_MICROPHONE - aborting")
            // Handle the error - maybe return error to client app
            return
        }
        
        // Step 2: Create notification (your existing notification code)
        val notification = createMicrophoneNotification() // Your implementation
        
        // Step 3: Log the attempt
        ForegroundServiceDebugger.logForegroundServiceStart(
            serviceName = "EngineService",
            notificationId = NOTIFICATION_ID,
            foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        
        // Step 4: Attempt to start foreground service with try-catch
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            
            // Step 5: Log success
            ForegroundServiceDebugger.logForegroundServiceResult(success = true)
            
            // Step 6: Start actual microphone recording
            startActualMicrophoneRecording()
            
        } catch (e: SecurityException) {
            // Step 7: Log failure with detailed analysis
            ForegroundServiceDebugger.logForegroundServiceResult(success = false, error = e)
            
            // Additional specific logging for SecurityException
            Log.e(TAG, "🚨 SecurityException during FGS start:")
            Log.e(TAG, "   Message: ${e.message}")
            Log.e(TAG, "   Likely causes:")
            Log.e(TAG, "   1. App not in foreground when FGS was attempted")
            Log.e(TAG, "   2. Missing overlay permission")
            Log.e(TAG, "   3. User didn't trigger this action directly")
            Log.e(TAG, "   4. Background app limitations")
            
            // Re-run diagnostic to see current state
            val postErrorDiagnostic = ForegroundServiceDebugger.diagnosticCheck(this, "EngineService")
            Log.e(TAG, "   Post-error overlay permission: ${postErrorDiagnostic.overlayPermissionGranted}")
            Log.e(TAG, "   Post-error foreground state: ${postErrorDiagnostic.appInForeground}")
            
            throw e // Re-throw to let caller handle
            
        } catch (e: Exception) {
            ForegroundServiceDebugger.logForegroundServiceResult(success = false, error = e)
            Log.e(TAG, "❌ Unexpected error starting FGS", e)
            throw e
        }
    }
    
    /**
     * Mock implementation - replace with your actual notification creation
     */
    private fun createMicrophoneNotification(): android.app.Notification {
        // Your notification creation code here
        return android.app.Notification.Builder(this, "microphone_channel")
            .setContentTitle("Recording Audio")
            .setContentText("BreezeApp is recording audio")
            .setSmallIcon(android.R.drawable.ic_media_play) // Replace with your icon
            .build()
    }
    
    /**
     * Mock implementation - replace with your actual microphone recording logic
     */
    private fun startActualMicrophoneRecording() {
        Log.d(TAG, "✅ Starting actual microphone recording implementation")
        // Your microphone recording code here
    }
    
    /**
     * Example method for stopping recording with debugging
     */
    fun stopMicrophoneRecording() {
        Log.d(TAG, "🛑 Stopping microphone recording")
        
        try {
            // Stop actual recording first
            stopActualMicrophoneRecording()
            
            // Stop foreground service
            stopForeground(true)
            
            Log.d(TAG, "✅ Microphone recording stopped successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping microphone recording", e)
        }
    }
    
    private fun stopActualMicrophoneRecording() {
        // Your implementation here
    }
}