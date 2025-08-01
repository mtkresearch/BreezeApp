package com.mtkresearch.breezeapp.presentation.chat.model

import java.util.UUID

/**
 * 臨時聊天訊息模型
 * 注意：這是Phase 1.3的臨時實作，正式的Domain Model將在Phase 2實作
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val state: MessageState = MessageState.NORMAL,
    val imageUrl: String? = null
) {
    enum class MessageState {
        NORMAL,     // 正常狀態
        SENDING,    // 發送中
        LOADING,    // AI回應載入中
        ERROR,      // 發送/接收錯誤
        TYPING      // AI正在輸入
    }
}

/**
 * 聊天會話模型
 */
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新對話",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) 