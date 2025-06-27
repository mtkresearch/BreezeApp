package com.mtkresearch.breezeapp_UI.domain.model

import java.time.ZonedDateTime

/**
 * Represents a single message in a chat session, used within the domain and UI layers.
 *
 * @param id Unique identifier for the message.
 * @param sessionId The ID of the session this message belongs to.
 * @param author The author of the message (USER, AI, SYSTEM).
 * @param content The text content of the message.
 * @param timestamp The time the message was created.
 * @param isLoading Indicates if this is a temporary message awaiting a response (e.g., for AI thinking animation).
 */
data class ChatMessage(
    val id: String,
    val sessionId: String,
    val author: MessageAuthor,
    val content: String,
    val timestamp: ZonedDateTime,
    val isLoading: Boolean = false
) 