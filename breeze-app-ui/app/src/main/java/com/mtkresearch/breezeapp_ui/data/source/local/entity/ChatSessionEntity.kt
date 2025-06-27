package com.mtkresearch.breezeapp_ui.data.source.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.ZonedDateTime

/**
 * Represents a chat session in the local database.
 */
@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val startTime: ZonedDateTime,
    val lastMessageTime: ZonedDateTime
) 