package com.mtkresearch.breezeapp_UI.domain.repository

import com.mtkresearch.breezeapp_UI.domain.model.ChatMessage
import com.mtkresearch.breezeapp_UI.domain.model.ChatSession
import kotlinx.coroutines.flow.Flow

/**
 * Interface for the chat repository, defining the contract for chat data operations.
 * This abstracts the data source from the domain layer.
 */
interface ChatRepository {

    /**
     * Retrieves a flow of all chat sessions, ordered by the most recent.
     */
    fun getChatSessions(): Flow<List<ChatSession>>

    /**
     * Retrieves a flow of all messages for a given session ID, ordered by timestamp.
     */
    fun getChatMessages(sessionId: String): Flow<List<ChatMessage>>

    /**
     * Creates a new chat session and saves its first message.
     * @param firstMessage The initial message that starts the session.
     * @return The ID of the newly created session.
     */
    suspend fun createNewSession(firstMessage: ChatMessage): String

    /**
     * Saves a single chat message to the database.
     */
    suspend fun saveMessage(message: ChatMessage)

    /**
     * Retrieves a single chat session by its ID.
     */
    suspend fun getSession(sessionId: String): ChatSession?

    /**
     * Deletes a chat session and all its associated messages.
     */
    suspend fun deleteSession(sessionId: String)
} 