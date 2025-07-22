package com.mtkresearch.breezeapp_kotlin.domain.model.settings

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val primaryColor: String = "#F99A1B", // Default orange
    val fontSize: FontSize = FontSize.MEDIUM,
    val language: String = "en-US",
    val enableNotifications: Boolean = true,
    val enableAnimations: Boolean = true,
    val storageLocation: StorageLocation = StorageLocation.INTERNAL,
    val autoBackup: Boolean = false
)

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class FontSize(val scale: Float) {
    SMALL(0.8f),
    MEDIUM(1.0f),
    LARGE(1.2f);

    companion object {
        fun fromScale(scale: Float): FontSize {
            return entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: MEDIUM
        }
    }
}

enum class StorageLocation {
    INTERNAL,
    EXTERNAL
} 