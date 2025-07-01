package com.mtkresearch.breezeapp.router.domain.model

/**
 * AI 能力類型定義
 * 定義 AI Router 支援的各種能力類型
 */
enum class CapabilityType {
    LLM,        // 大語言模型
    VLM,        // 視覺語言模型
    ASR,        // 語音識別
    TTS,        // 語音合成
    GUARDIAN    // 內容安全檢測
} 