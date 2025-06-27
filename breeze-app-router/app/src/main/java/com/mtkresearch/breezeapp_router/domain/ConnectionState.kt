package com.mtkresearch.breezeapp_router.domain

/**
 * AI Router 連接狀態
 */
enum class ConnectionState {
    DISCONNECTED,    // 未連接
    CONNECTING,      // 連接中
    CONNECTED,       // 已連接
    ERROR           // 連接錯誤
} 