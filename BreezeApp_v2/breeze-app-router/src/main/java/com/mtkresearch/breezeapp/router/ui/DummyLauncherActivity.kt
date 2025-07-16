package com.mtkresearch.breezeapp.router.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.mtkresearch.breezeapp.router.notification.ServiceNotificationManager

/**
 * A dummy, non-functional Activity whose only purpose is to be the LAUNCHER
 * entry point so that the "Run" button in Android Studio works for this
 * service-only application.
 */
class BreezeAppRouterLauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the AI Router Service immediately when app is launched
        startAIRouterService()

        // Check notification status and provide helpful guidance
        val notificationManager = ServiceNotificationManager(this)
        
        if (!notificationManager.areNotificationsEnabled()) {
            // Show dialog to help user enable notifications
            showNotificationDialog(notificationManager)
        } else {
            // Show success message
            Toast.makeText(
                this,
                "BreezeApp Router is running. Check notification panel for status.",
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }
    
    private fun showNotificationDialog(notificationManager: ServiceNotificationManager) {
        AlertDialog.Builder(this)
            .setTitle("Enable Notifications")
            .setMessage("BreezeApp Router runs as a background service. Enable notifications to see service status and progress updates.")
            .setPositiveButton("Open Settings") { _, _ ->
                notificationManager.openNotificationSettings()
                finish()
            }
            .setNegativeButton("Skip") { _, _ ->
                Toast.makeText(this, "Service is running without visible status.", Toast.LENGTH_LONG).show()
                finish()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Starts the AI Router Service immediately as a foreground service.
     * This ensures the notification appears right when the app is launched.
     */
    private fun startAIRouterService() {
        try {
            val serviceIntent = Intent(this, com.mtkresearch.breezeapp.router.AIRouterService::class.java).apply {
                putExtra("start_reason", "user_launch")
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val componentName = startForegroundService(serviceIntent)
                android.util.Log.i("RouterEntryActivity", "AI Router Service started as foreground service: $componentName")
            } else {
                val componentName = startService(serviceIntent)
                android.util.Log.i("RouterEntryActivity", "AI Router Service started: $componentName")
            }
            
            // Give the service a moment to start and show notification
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                android.util.Log.i("RouterEntryActivity", "Service should now be running with notification visible")
            }, 1000)
            
        } catch (e: Exception) {
            android.util.Log.e("RouterEntryActivity", "Failed to start AI Router Service", e)
            Toast.makeText(this, "Failed to start AI Router Service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
} 