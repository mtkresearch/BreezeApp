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
class DummyLauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
} 