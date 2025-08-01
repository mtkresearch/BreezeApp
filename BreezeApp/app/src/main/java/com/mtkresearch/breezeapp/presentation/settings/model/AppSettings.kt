package com.mtkresearch.breezeapp.presentation.settings.model

/**
 * 應用層設定模型
 * 
 * 負責管理整個應用程式的UI和行為設定
 * 生命週期: 應用程式級別，持久化儲存
 * 影響範圍: 整個應用程式的UI和行為
 * 儲存方式: SharedPreferences + DataStore
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: String = "#F99A1B",
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

/**
 * 主題模式
 */
enum class ThemeMode {
    LIGHT,   // 淺色模式
    DARK,    // 深色模式
    SYSTEM   // 跟隨系統
}

/**
 * 字體大小
 */
enum class FontSize(val scale: Float, val displayName: String) {
    SMALL(0.85f, "小"),
    MEDIUM(1.0f, "中"),
    LARGE(1.15f, "大"),
    EXTRA_LARGE(1.3f, "特大")
}

/**
 * 儲存位置
 */
enum class StorageLocation(val displayName: String) {
    INTERNAL("內部儲存"),
    EXTERNAL("外部儲存"),
    CUSTOM("自訂位置")
} 