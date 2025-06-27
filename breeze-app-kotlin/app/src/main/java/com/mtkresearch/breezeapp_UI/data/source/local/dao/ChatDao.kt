package com.mtkresearch.breezeapp_UI.data.source.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mtkresearch.breezeapp_UI.data.source.local.entity.ChatMessageEntity
import com.mtkresearch.breezeapp_UI.data.source.local.entity.ChatSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Chat related operations.
 */
@Dao
interface ChatDao {

    // === Session Operations ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity)

    @Update
    suspend fun updateSession(session: ChatSessionEntity)

    @Query("SELECT * FROM chat_sessions ORDER BY lastMessageTime DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ChatSessionEntity?
    
    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: String)

    // === Message Operations ===

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE session_id = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Transaction
    @Query("SELECT COUNT(id) FROM chat_messages WHERE session_id = :sessionId")
    suspend fun getMessageCountForSession(sessionId: String): Int
} 