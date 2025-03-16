package com.mtkresearch.breezeapp.data.models

import android.net.Uri
import java.util.Date

/**
 * Represents a message in a chat conversation.
 * Can be a user message, assistant message, or system message.
 */
data class ChatMessage(
    val id: String = System.currentTimeMillis().toString(),
    val content: String,
    val sender: MessageSender,
    val timestamp: Date = Date(),
    val isProcessing: Boolean = false,
    val mediaUri: Uri? = null,
    val mediaType: MediaType? = null,
    val error: Boolean = false
)

/**
 * Represents who sent a message
 */
enum class MessageSender {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * Extensions to create different types of messages
 */
object MessageFactory {
    
    /**
     * Create a user message
     */
    fun createUserMessage(content: String, mediaUri: Uri? = null, mediaType: MediaType? = null): ChatMessage {
        return ChatMessage(
            content = content,
            sender = MessageSender.USER,
            mediaUri = mediaUri,
            mediaType = mediaType
        )
    }
    
    /**
     * Create a media-only message
     */
    fun createMediaMessage(mediaUri: Uri, mediaType: MediaType): ChatMessage {
        return ChatMessage(
            content = "",  // Empty content for media-only messages
            sender = MessageSender.USER,
            mediaUri = mediaUri,
            mediaType = mediaType
        )
    }
    
    /**
     * Create an assistant message
     */
    fun createAssistantMessage(content: String, isProcessing: Boolean = false): ChatMessage {
        return ChatMessage(
            content = content,
            sender = MessageSender.ASSISTANT,
            isProcessing = isProcessing
        )
    }
    
    /**
     * Create a system message (for instructions, notifications)
     */
    fun createSystemMessage(content: String): ChatMessage {
        return ChatMessage(
            content = content,
            sender = MessageSender.SYSTEM
        )
    }
    
    /**
     * Create an error message from the assistant
     */
    fun createErrorMessage(errorMessage: String): ChatMessage {
        return ChatMessage(
            content = errorMessage,
            sender = MessageSender.ASSISTANT,
            error = true
        )
    }
    
    /**
     * Create a processing message (placeholder while generating response)
     */
    fun createProcessingMessage(): ChatMessage {
        return ChatMessage(
            content = "",
            sender = MessageSender.ASSISTANT,
            isProcessing = true
        )
    }
} 