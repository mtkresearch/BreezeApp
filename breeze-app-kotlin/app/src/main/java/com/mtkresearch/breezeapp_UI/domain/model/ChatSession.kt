package com.mtkresearch.breezeapp_UI.domain.model

import java.time.ZonedDateTime

/**
 * Represents a chat session, which is a collection of messages.
 *
 * @param id Unique identifier for the session.
 * @param title The title of the chat session (e.g., the first user message).
 * @param startTime The time the session was created.
 * @param lastMessageTime The timestamp of the most recent message.
 * @param messageCount The total number of messages in the session.
 */
data class ChatSession(
    val id: String,
    val title: String,
    val startTime: ZonedDateTime,
    val lastMessageTime: ZonedDateTime,
    val messageCount: Int = 0
) 