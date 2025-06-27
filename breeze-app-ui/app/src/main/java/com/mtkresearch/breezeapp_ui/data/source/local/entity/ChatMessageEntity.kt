package com.mtkresearch.breezeapp_ui.data.source.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mtkresearch.breezeapp_ui.domain.model.MessageAuthor
import java.time.ZonedDateTime

/**
 * Represents a single chat message in the local database.
 * It is linked to a [ChatSessionEntity] by the `session_id`.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE // If a session is deleted, its messages are also deleted.
        )
    ],
    indices = [Index("session_id")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    val author: MessageAuthor,
    val content: String,
    val timestamp: ZonedDateTime
) 