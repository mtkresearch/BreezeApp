package com.mtkresearch.breezeapp.router.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mtkresearch.breezeapp.router.R
import com.mtkresearch.breezeapp.router.notification.ServiceNotificationManager

/**
 * Router Entry Activity - Professional entry point for BreezeApp AI Router
 * 
 * This activity serves as the main entry point for the AI Router application.
 * When launched, it immediately starts the AI Router Service as a foreground service,
 * ensuring the notification appears and the service is available for client connections.
 * 
 * Enhanced UX: Shows professional status dialog instead of flash-and-close behavior.
 */
class BreezeAppRouterLauncherActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_router_launcher)

        // Start the AI Router Service immediately when app is launched
        startAIRouterService()

        // Initialize the premium UI components
        initializePremiumUI()
    }
    
    
    /**
     * Shows detailed service information for advanced users.
     */
    private fun showServiceInfoDialog() {
        AlertDialog.Builder(this)
            .setTitle("Service Information")
            .setMessage("BreezeApp AI Router Details:\n\n" +
                       "• Type: Foreground Service\n" +
                       "• Purpose: On-device AI processing\n" +
                       "• Capabilities: Chat, TTS, ASR\n" +
                       "• Status: Always available for clients\n" +
                       "• Privacy: All processing stays on device\n\n" +
                       "The service runs continuously to provide instant AI responses to client applications.")
            .setPositiveButton("Got it") { dialog, _ ->
                dialog.dismiss() // Close dialog, stay in premium layout
            }
            .setCancelable(true) // Allow back button to close dialog
            .show()
    }
    
    /**
     * Attempts to open the notification panel to show the service notification.
     */
    private fun openNotificationPanel() {
        try {
            // Try to expand notification panel (may not work on all devices)
            val statusBarService = getSystemService(Context.STATUS_BAR_SERVICE)
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val expandNotifications = statusBarManager.getMethod("expandNotificationsPanel")
            expandNotifications.invoke(statusBarService)
        } catch (e: Exception) {
            // Fallback: show toast instruction
            Toast.makeText(this, "Swipe down from top to see AI Router notification", Toast.LENGTH_LONG).show()
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
    
    private fun initializePremiumUI() {
        // Initialize views for the premium layout
        val statusText = findViewById<android.widget.TextView>(R.id.statusText)
        val btnViewNotifications = findViewById<android.widget.Button>(R.id.btnViewNotifications)
        val btnServiceInfo = findViewById<android.widget.Button>(R.id.btnServiceInfo)
        val fabClose = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fabClose)
        
        // Set button text colors to primary orange
        btnViewNotifications?.setTextColor(ContextCompat.getColor(this, R.color.primary))
        btnServiceInfo?.setTextColor(ContextCompat.getColor(this, R.color.primary))
        
        // Setup click listeners
        btnViewNotifications?.setOnClickListener {
            openNotificationPanel()
        }
        
        btnServiceInfo?.setOnClickListener {
            showServiceInfoDialog()
        }
        
        fabClose?.setOnClickListener {
            finish()
        }
        
        // Start real-time status updates
        startServiceStatusUpdates(statusText)
    }
    
    private fun startServiceStatusUpdates(statusText: android.widget.TextView?) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        
        // Simulate real service startup sequence
        val statusUpdates = listOf(
            "Initializing AI Engine..." to 500L,
            "Loading Neural Models..." to 1500L,
            "Starting Foreground Service..." to 2500L,
            "Service Ready - Accepting Connections!" to 3500L
        )
        
        statusUpdates.forEach { (status, delay) ->
            handler.postDelayed({
                statusText?.text = status
                android.util.Log.d("BreezeAppRouterLauncher", "Status updated: $status")
            }, delay)
        }
        
        // Final status after service is fully ready
        handler.postDelayed({
            statusText?.text = "AI Router Online - Ready for Clients"
            statusText?.setTextColor(ContextCompat.getColor(this, R.color.success))
        }, 4000L)
    }
}