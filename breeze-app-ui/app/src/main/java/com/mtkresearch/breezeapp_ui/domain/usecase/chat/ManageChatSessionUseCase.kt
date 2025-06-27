package com.mtkresearch.breezeapp_ui.domain.usecase.chat

import com.mtkresearch.breezeapp_ui.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp_ui.presentation.chat.model.ChatSession
import java.util.UUID
import javax.inject.Inject

/**
 * 管理聊天會話 Use Case
 * 
 * 負責處理聊天會話相關的業務邏輯，包括：
 * - 會話創建和管理
 * - 訊息添加和更新
 * - 會話狀態管理
 */
class ManageChatSessionUseCase @Inject constructor() {
    
    /**
     * 創建新的聊天會話
     */
    fun createNewSession(title: String = "新對話"): ChatSession {
        return ChatSession(
            id = UUID.randomUUID().toString(),
            title = title,
            messages = emptyList(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 添加訊息到會話
     */
    fun addMessageToSession(
        session: ChatSession,
        message: ChatMessage
    ): ChatSession {
        return session.copy(
            messages = session.messages + message,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新會話中的特定訊息
     */
    fun updateMessageInSession(
        session: ChatSession,
        messageId: String,
        updatedMessage: ChatMessage
    ): ChatSession {
        val updatedMessages = session.messages.map { message ->
            if (message.id == messageId) updatedMessage else message
        }
        return session.copy(
            messages = updatedMessages,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新會話標題
     */
    fun updateSessionTitle(
        session: ChatSession,
        newTitle: String
    ): ChatSession {
        return session.copy(
            title = newTitle,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 清空會話訊息
     */
    fun clearSessionMessages(session: ChatSession): ChatSession {
        return session.copy(
            messages = emptyList(),
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 創建用戶訊息
     */
    fun createUserMessage(text: String): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isFromUser = true,
            timestamp = System.currentTimeMillis(),
            state = ChatMessage.MessageState.NORMAL
        )
    }
    
    /**
     * 創建AI訊息
     */
    fun createAIMessage(text: String, state: ChatMessage.MessageState = ChatMessage.MessageState.NORMAL): ChatMessage {
        return ChatMessage(
            id = UUID.randomUUID().toString(),
            text = text,
            isFromUser = false,
            timestamp = System.currentTimeMillis(),
            state = state
        )
    }
} 