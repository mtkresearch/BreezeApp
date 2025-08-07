package com.mtkresearch.breezeapp.data.repository

import com.mtkresearch.breezeapp.domain.repository.ChatRepository
import com.mtkresearch.breezeapp.presentation.chat.model.ChatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat Repository Implementation
 * 
 * Uses in-memory storage to persist conversation across Activity instances
 * The conversation persists until app process is killed
 */
@Singleton
class ChatRepositoryImpl @Inject constructor() : ChatRepository {
    
    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    
    override suspend fun getCurrentSession(): ChatSession? {
        return _currentSession.value
    }
    
    override suspend fun saveCurrentSession(session: ChatSession) {
        _currentSession.value = session
    }
    
    override suspend fun clearCurrentSession() {
        _currentSession.value = null
    }
    
    override fun observeCurrentSession(): Flow<ChatSession?> {
        return _currentSession.asStateFlow()
    }
}