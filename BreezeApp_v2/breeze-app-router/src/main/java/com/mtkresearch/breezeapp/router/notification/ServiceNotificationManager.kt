package com.mtkresearch.breezeapp.router.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.mtkresearch.breezeapp.router.R
import com.mtkresearch.breezeapp.router.domain.model.NotificationPriority
import com.mtkresearch.breezeapp.router.domain.model.ServiceState
import com.mtkresearch.breezeapp.router.ui.DummyLauncherActivity

/**
 * Manages foreground service notifications following Clean Architecture principles.
 * 
 * Responsibilities:
 * - Create and manage notification channels
 * - Build notifications based on service state
 * - Handle notification updates and styling
 * - Maintain separation between domain models and Android framework
 */
class ServiceNotificationManager(private val context: Context) {
    
    companion object {
        const val CHANNEL_ID = "ai_router_service_channel"
        const val CHANNEL_NAME = "AI Router Service"
        const val CHANNEL_DESCRIPTION = "Shows AI Router service status and progress"
        
        private const val REQUEST_CODE_MAIN = 1001
    }
    
    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    /**
     * Creates the notification channel required for foreground service.
     * Must be called before creating notifications on Android O+.
     * 
     * Note: Android doesn't allow apps to programmatically enable notifications
     * if the user has disabled them. This creates the channel with optimal settings
     * for foreground services, but users may still need to manually enable
     * notifications in system settings.
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                // Enable by default (but user can still disable in settings)
                setBypassDnd(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Checks if notifications are enabled for this app.
     * Returns true if notifications are allowed, false otherwise.
     */
    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            notificationManager.areNotificationsEnabled() && 
            (channel?.importance != NotificationManager.IMPORTANCE_NONE)
        } else {
            notificationManager.areNotificationsEnabled()
        }
    }
    
    /**
     * Opens the app's notification settings so user can enable notifications.
     * Should be called when notifications are disabled and user needs to enable them.
     */
    fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
        context.startActivity(intent)
    }
    
    /**
     * Creates a notification based on the current service state.
     * 
     * @param state Current service state from domain layer
     * @return Notification ready for foreground service
     */
    fun createNotification(state: ServiceState): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("BreezeApp AI Router")
            .setContentText(state.getDisplayText())
            .setSmallIcon(state.getIcon())
            .setOngoing(state.isOngoing())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(createContentIntent())
            .setPriority(mapPriorityToCompat(state.getNotificationPriority()))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        
        // Configure progress display
        if (state.showProgress()) {
            builder.setProgress(
                state.getProgressMax(),
                state.getProgressValue(),
                state.isIndeterminate()
            )
        }
        
        // Add state-specific styling
        when (state) {
            is ServiceState.Error -> {
                builder.setColor(context.getColor(R.color.error))
                    .setColorized(true)
            }
            is ServiceState.Downloading -> {
                builder.setColor(context.getColor(R.color.primary))
                    .setSubText("${state.progress}% complete")
            }
            is ServiceState.Processing -> {
                builder.setColor(context.getColor(R.color.primary))
                    .setSubText("Active processing")
            }
            is ServiceState.Ready -> {
                builder.setColor(context.getColor(R.color.surface_variant))
            }
        }
        
        return builder.build()
    }
    
    /**
     * Updates an existing notification with new state.
     * 
     * @param notificationId The ID of the notification to update
     * @param state New service state
     */
    fun updateNotification(notificationId: Int, state: ServiceState) {
        val notification = createNotification(state)
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * Creates a pending intent for notification tap action.
     * Currently opens the dummy launcher activity.
     */
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, DummyLauncherActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_MAIN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Maps domain notification priority to Android NotificationCompat priority.
     */
    private fun mapPriorityToCompat(priority: NotificationPriority): Int = when (priority) {
        NotificationPriority.LOW -> NotificationCompat.PRIORITY_LOW
        NotificationPriority.DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
        NotificationPriority.HIGH -> NotificationCompat.PRIORITY_HIGH
    }
}