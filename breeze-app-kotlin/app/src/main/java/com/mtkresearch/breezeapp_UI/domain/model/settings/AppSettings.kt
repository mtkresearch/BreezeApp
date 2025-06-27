package com.mtkresearch.breezeapp_UI.domain.model.settings

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: String = "#F99A1B", // Default orange
    val fontSize: FontSize = FontSize.MEDIUM,
    val language: String = "zh-TW",
    val enableNotifications: Boolean = true,
    val enableAnimations: Boolean = true,
    val storageLocation: StorageLocation = StorageLocation.INTERNAL,
    val autoBackup: Boolean = false,
    val enableHapticFeedback: Boolean = true,
    val enableSoundEffects: Boolean = true
) {
    /**
     * 檢查是否使用深色模式
     */
    fun isDarkMode(): Boolean = themeMode == ThemeMode.DARK
    
    /**
     * 檢查是否使用系統主題
     */
    fun isSystemTheme(): Boolean = themeMode == ThemeMode.SYSTEM
    
    /**
     * 檢查是否為預設設定
     */
    fun isDefault(): Boolean = this == AppSettings()
    
    /**
     * 重置為預設設定
     */
    fun resetToDefault(): AppSettings = AppSettings()
}

// The following enums are defined in their own files and should be removed from here. 