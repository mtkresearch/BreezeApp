package com.mtkresearch.breezeapp.domain.repository

import com.mtkresearch.breezeapp.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp.presentation.chat.model.ChatSession
import kotlinx.coroutines.flow.Flow

/**
 * Chat Repository Interface
 * 
 * Handles conversation persistence to maintain chat history across app sessions
 */
interface ChatRepository {
    
    /**
     * Get the current conversation session
     */
    suspend fun getCurrentSession(): ChatSession?
    
    /**
     * Save the current conversation session
     */
    suspend fun saveCurrentSession(session: ChatSession)
    
    /**
     * Clear the current conversation
     */
    suspend fun clearCurrentSession()
    
    /**
     * Observe current session changes
     */
    fun observeCurrentSession(): Flow<ChatSession?>
}