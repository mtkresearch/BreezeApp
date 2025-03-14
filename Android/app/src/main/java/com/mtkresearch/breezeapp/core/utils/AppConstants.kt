package com.mtkresearch.breezeapp.core.utils

import android.content.Context
import android.app.ActivityManager
import java.io.File

/**
 * Central place for application constants
 */
object AppConstants {
    // Tags
    const val TAG_MAIN = "MainActivity"
    const val TAG_CHAT = "ChatActivity"
    
    // Shared Preferences
    const val PREFS_NAME = "BreezeAppSettings"
    
    // Preference Keys
    const val KEY_HISTORY_LOOKBACK = "history_lookback"
    const val KEY_SEQUENCE_LENGTH = "sequence_length"
    const val KEY_DEFAULT_MODEL = "default_model"
    const val KEY_FIRST_LAUNCH = "first_launch"
    const val KEY_TEMPERATURE = "temperature"
    const val KEY_PREFERRED_BACKEND = "preferred_backend"
    const val KEY_MODEL_SIZE_PREFERENCE = "model_size_preference"
    
    // Default Values
    const val DEFAULT_BACKEND = "cpu"
    const val DEFAULT_HISTORY_LOOKBACK = 4
    const val DEFAULT_SEQUENCE_LENGTH = 1024
    const val DEFAULT_TEMPERATURE = 0.7f
    
    // Feature Flags
    const val LLM_ENABLED = true  // LLM is essential
    const val VLM_ENABLED = true  // Vision capabilities
    const val ASR_ENABLED = true  // Speech recognition
    const val TTS_ENABLED = true  // Text-to-speech
    
    // Backend Constants
    const val BACKEND_NONE = "none"
    const val BACKEND_CPU = "cpu"
    const val BACKEND_MTK = "mtk"  // Custom backend if applicable
    
    // Model Selection
    const val MODEL_SIZE_LARGE = "large"
    const val MODEL_SIZE_SMALL = "small"
    const val MODEL_SIZE_AUTO = "auto"
    
    // Resource Thresholds
    const val MIN_RAM_REQUIRED_GB = 4  // Minimum RAM for the app to run
    const val LARGE_MODEL_MIN_RAM_GB = 8  // Minimum RAM for large model
    
    // System Prompts
    const val DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistant. Answer questions clearly and concisely."
    
    // Request Codes
    const val PERMISSION_REQUEST_CODE = 123
    const val PICK_IMAGE_REQUEST = 1
    const val CAPTURE_IMAGE_REQUEST = 2
    const val PICK_FILE_REQUEST = 3
    
    // UI Constants
    const val ENABLED_ALPHA = 1.0f
    const val DISABLED_ALPHA = 0.3f
    const val CONVERSATION_HISTORY_LOOKBACK = 4
    
    // Paths and Directories
    const val APP_MODEL_DIR = "models"
    
    // Error Messages
    const val LLM_ERROR_RESPONSE = "I apologize, but I encountered an error. Please try again."
    const val LLM_EMPTY_RESPONSE_ERROR = "I apologize, but I couldn't generate a proper response. Please try rephrasing your question."
    const val LLM_INPUT_TOO_LONG_ERROR = "I apologize, but your input is too long. Please try breaking it into smaller parts."
    
    /**
     * Get the size of available RAM in GB
     */
    fun getAvailableRamGB(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024L * 1024L * 1024L) // Convert to GB
    }
    
    /**
     * Check if device has enough RAM for large model
     */
    fun canUseLargeModel(context: Context): Boolean {
        return getAvailableRamGB(context) >= LARGE_MODEL_MIN_RAM_GB
    }
    
    /**
     * Get the app's model directory
     */
    fun getAppModelDir(context: Context): String {
        return context.filesDir.absolutePath + File.separator + APP_MODEL_DIR
    }
    
    /**
     * Format file size with appropriate unit
     */
    fun formatFileSize(size: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var fileSize = size.toDouble()
        var unitIndex = 0
        
        while (fileSize > 1024 && unitIndex < units.size - 1) {
            fileSize /= 1024
            unitIndex++
        }
        
        return String.format("%.2f %s", fileSize, units[unitIndex])
    }
} 