package com.mtkresearch.breezeapp.data.models

import java.util.Date

/**
 * Data class representing a saved conversation
 */
data class SavedConversation(
    val id: String,
    val title: String,
    val date: Date,
    val previewText: String,
    val messageCount: Int
) {
    companion object {
        // Factory method to create a saved conversation from a list of messages
        fun fromMessages(
            id: String,
            messages: List<ChatMessage>,
            title: String? = null
        ): SavedConversation {
            val firstUserMessage = messages.firstOrNull { it.sender == MessageSender.USER }
            val previewText = firstUserMessage?.content ?: "Empty conversation"
            
            return SavedConversation(
                id = id,
                title = title ?: previewText.take(30) + if (previewText.length > 30) "..." else "",
                date = Date(),
                previewText = previewText,
                messageCount = messages.size
            )
        }
    }
} 