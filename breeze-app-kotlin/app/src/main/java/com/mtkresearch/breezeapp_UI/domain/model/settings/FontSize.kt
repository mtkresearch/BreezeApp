package com.mtkresearch.breezeapp_UI.domain.model.settings

enum class FontSize(val scale: Float, val displayName: String) {
    SMALL(0.85f, "小"),
    MEDIUM(1.0f, "中"),
    LARGE(1.15f, "大"),
    EXTRA_LARGE(1.3f, "特大");

    companion object {
        fun fromScale(scale: Float): FontSize {
            return entries.minByOrNull { kotlin.math.abs(it.scale - scale) } ?: MEDIUM
        }
    }
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