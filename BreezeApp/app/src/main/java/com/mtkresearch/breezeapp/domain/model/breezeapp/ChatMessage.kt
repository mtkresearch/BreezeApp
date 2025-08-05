package com.mtkresearch.breezeapp.domain.model.breezeapp

/**
 * Chat message model for BreezeApp Engine
 * 
 * This model represents a single message in a chat conversation.
 * It follows Clean Architecture principles by being independent of external frameworks.
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val role: MessageRole,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false
) {
    companion object {
        fun createUserMessage(content: String): ChatMessage {
            return ChatMessage(
                id = generateId(),
                content = content,
                role = MessageRole.USER
            )
        }
        
        fun createAssistantMessage(content: String, isStreaming: Boolean = false): ChatMessage {
            return ChatMessage(
                id = generateId(),
                content = content,
                role = MessageRole.ASSISTANT,
                isStreaming = isStreaming
            )
        }
        
        private fun generateId(): String = "msg_${System.currentTimeMillis()}_${(0..9999).random()}"
    }
}

/**
 * Message role enumeration
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}