package com.mtkresearch.breezeapp.domain.model.breezeapp

/**
 * ASR (Automatic Speech Recognition) mode configuration
 * 
 * Defines the different modes of speech recognition available in the app:
 * - Online: Real-time streaming ASR using microphone directly through BreezeApp-engine
 * - Offline: Record audio first, then process through BreezeApp-engine ASR
 */
enum class AsrMode {
    /**
     * Online streaming ASR mode
     * - Microphone streaming directly to BreezeApp-engine
     * - Real-time transcription results
     * - Lower latency but requires stable connection
     */
    ONLINE_STREAMING,
    
    /**
     * Offline file-based ASR mode  
     * - Record audio to file first
     * - Send recorded audio to BreezeApp-engine for processing
     * - Higher latency but more reliable for poor connections
     */
    OFFLINE_FILE
}

/**
 * ASR availability configuration
 * Controls which ASR modes are enabled in the application
 */
data class AsrAvailabilityConfig(
    val onlineStreamingEnabled: Boolean = false, // Disable online streaming for robustness
    val offlineFileEnabled: Boolean = true,      // Keep offline mode enabled
    val allowModeToggle: Boolean = false         // Disable mode toggle for single-mode operation
) {
    
    companion object {
        /**
         * Predefined configuration presets
         */
        
        // Most robust: Offline-only mode (default)
        fun offlineOnly() = AsrAvailabilityConfig(
            onlineStreamingEnabled = false,
            offlineFileEnabled = true,
            allowModeToggle = false
        )
        
        // Both modes available with toggle
        fun bothModesWithToggle() = AsrAvailabilityConfig(
            onlineStreamingEnabled = true,
            offlineFileEnabled = true,
            allowModeToggle = true
        )
        
        // Online-only mode (for testing)
        fun onlineOnly() = AsrAvailabilityConfig(
            onlineStreamingEnabled = true,
            offlineFileEnabled = false,
            allowModeToggle = false
        )
        
        // Both modes available but no toggle (fixed to default)
        fun bothModesFixed() = AsrAvailabilityConfig(
            onlineStreamingEnabled = true,
            offlineFileEnabled = true,
            allowModeToggle = false
        )
    }
    /**
     * Get list of available ASR modes based on configuration
     */
    fun getAvailableModes(): List<AsrMode> {
        val modes = mutableListOf<AsrMode>()
        if (onlineStreamingEnabled) modes.add(AsrMode.ONLINE_STREAMING)
        if (offlineFileEnabled) modes.add(AsrMode.OFFLINE_FILE)
        return modes
    }
    
    /**
     * Get the default ASR mode based on availability
     */
    fun getDefaultMode(): AsrMode {
        return when {
            offlineFileEnabled -> AsrMode.OFFLINE_FILE
            onlineStreamingEnabled -> AsrMode.ONLINE_STREAMING
            else -> AsrMode.OFFLINE_FILE // Fallback
        }
    }
    
    /**
     * Check if a specific mode is available
     */
    fun isModeAvailable(mode: AsrMode): Boolean {
        return when (mode) {
            AsrMode.ONLINE_STREAMING -> onlineStreamingEnabled
            AsrMode.OFFLINE_FILE -> offlineFileEnabled
        }
    }
}

/**
 * ASR configuration data class
 */
data class AsrConfig(
    val mode: AsrMode = AsrMode.OFFLINE_FILE,
    val language: String = "en",
    val format: String = "json",
    val maxRecordingDurationMs: Long = 15000, // 15 seconds max for offline mode 
    val autoStopOnSilence: Boolean = true,
    val availabilityConfig: AsrAvailabilityConfig = AsrAvailabilityConfig() // Default to offline-only
) {
    /**
     * Check if current mode is available according to configuration
     */
    fun isCurrentModeAvailable(): Boolean {
        return availabilityConfig.isModeAvailable(mode)
    }
    
    /**
     * Get next available mode for toggling
     */
    fun getNextAvailableMode(): AsrMode? {
        val availableModes = availabilityConfig.getAvailableModes()
        if (availableModes.size <= 1) return null // No toggle possible
        
        val currentIndex = availableModes.indexOf(mode)
        return if (currentIndex >= 0) {
            availableModes[(currentIndex + 1) % availableModes.size]
        } else {
            availabilityConfig.getDefaultMode()
        }
    }
}