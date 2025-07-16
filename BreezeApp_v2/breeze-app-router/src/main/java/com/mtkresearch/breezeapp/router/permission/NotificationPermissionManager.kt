package com.mtkresearch.breezeapp.router.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Manages notification permission requests following Android 13+ requirements.
 * 
 * This class handles the POST_NOTIFICATIONS permission that became required
 * in Android 13 (API 33) for showing notifications.
 */
class NotificationPermissionManager(private val context: Context) {
    
    companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val PERMISSION_POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS
    }
    
    /**
     * Checks if notification permission is required for the current Android version.
     * @return true if running on Android 13+ where permission is required
     */
    fun isPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }
    
    /**
     * Checks if notification permission is currently granted.
     * @return true if permission is granted or not required for this Android version
     */
    fun isPermissionGranted(): Boolean {
        return if (isPermissionRequired()) {
            ContextCompat.checkSelfPermission(
                context, 
                PERMISSION_POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Permission not required on Android < 13
            true
        }
    }
    
    /**
     * Checks if we should show rationale for notification permission.
     * @param activity The activity context for checking rationale
     * @return true if rationale should be shown
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return if (isPermissionRequired()) {
            ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                PERMISSION_POST_NOTIFICATIONS
            )
        } else {
            false
        }
    }
    
    /**
     * Requests notification permission from the user.
     * @param activity The activity to request permission from
     */
    fun requestPermission(activity: Activity) {
        if (isPermissionRequired()) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(PERMISSION_POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    
    /**
     * Handles the result of permission request.
     * @param requestCode The request code from onRequestPermissionsResult
     * @param permissions The requested permissions
     * @param grantResults The grant results for the corresponding permissions
     * @return true if notification permission was granted
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ): Boolean {
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            val permissionIndex = permissions.indexOf(PERMISSION_POST_NOTIFICATIONS)
            if (permissionIndex >= 0 && permissionIndex < grantResults.size) {
                return grantResults[permissionIndex] == PackageManager.PERMISSION_GRANTED
            }
        }
        return false
    }
}