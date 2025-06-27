package com.mtkresearch.breezeapp_UI.data.mapper

import com.mtkresearch.breezeapp_UI.data.source.local.entity.ChatMessageEntity
import com.mtkresearch.breezeapp_UI.data.source.local.entity.ChatSessionEntity
import com.mtkresearch.breezeapp_UI.domain.model.ChatMessage
import com.mtkresearch.breezeapp_UI.domain.model.ChatSession

/**
 * Converts a [ChatMessageEntity] from the data layer to a [ChatMessage] in the domain layer.
 */
fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = this.id,
        sessionId = this.sessionId,
        author = this.author,
        content = this.content,
        timestamp = this.timestamp
    )
}

/**
 * Converts a [ChatMessage] from the domain layer to a [ChatMessageEntity] for the data layer.
 */
fun ChatMessage.toEntity(): ChatMessageEntity {
    return ChatMessageEntity(
        id = this.id,
        sessionId = this.sessionId,
        author = this.author,
        content = this.content,
        timestamp = this.timestamp
    )
}

/**
 * Converts a [ChatSessionEntity] from the data layer to a [ChatSession] in the domain layer.
 */
fun ChatSessionEntity.toDomain(messageCount: Int): ChatSession {
    return ChatSession(
        id = this.id,
        title = this.title,
        startTime = this.startTime,
        lastMessageTime = this.lastMessageTime,
        messageCount = messageCount
    )
}

/**
 * Converts a [ChatSession] from the domain layer to a [ChatSessionEntity] for the data layer.
 */
fun ChatSession.toEntity(): ChatSessionEntity {
    return ChatSessionEntity(
        id = this.id,
        title = this.title,
        startTime = this.startTime,
        lastMessageTime = this.lastMessageTime
    )
} 