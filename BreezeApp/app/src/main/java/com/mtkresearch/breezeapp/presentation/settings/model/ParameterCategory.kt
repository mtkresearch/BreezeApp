package com.mtkresearch.breezeapp.presentation.settings.model

/**
 * AI推論參數類別枚舉
 */
enum class ParameterCategory(val displayName: String) {
    LLM("大語言模型"),
    VLM("視覺語言模型"),
    ASR("語音識別"),
    TTS("語音合成"),
    GENERAL("通用設定");

    companion object {
        fun fromDisplayName(displayName: String): ParameterCategory? {
            return values().find { it.displayName == displayName }
        }
    }
} 