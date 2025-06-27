package com.mtkresearch.breezeapp_ui.data.repository

import com.mtkresearch.breezeapp_ui.data.mapper.toDomain
import com.mtkresearch.breezeapp_ui.data.mapper.toEntity
import com.mtkresearch.breezeapp_ui.data.source.local.dao.ChatDao
import com.mtkresearch.breezeapp_ui.domain.model.ChatMessage
import com.mtkresearch.breezeapp_ui.domain.model.ChatSession
import com.mtkresearch.breezeapp_ui.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao
) : ChatRepository {

    override fun getChatSessions(): Flow<List<ChatSession>> {
        return chatDao.getAllSessions().map { entities ->
            entities.map { entity ->
                val messageCount = chatDao.getMessageCountForSession(entity.id)
                entity.toDomain(messageCount)
            }
        }
    }

    override fun getChatMessages(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun createNewSession(firstMessage: ChatMessage): String {
        val sessionEntity = ChatSession(
            id = firstMessage.sessionId,
            title = firstMessage.content.take(80),
            startTime = firstMessage.timestamp,
            lastMessageTime = firstMessage.timestamp
        ).toEntity()

        chatDao.insertSession(sessionEntity)
        chatDao.insertMessage(firstMessage.toEntity())
        return sessionEntity.id
    }

    override suspend fun saveMessage(message: ChatMessage) {
        chatDao.insertMessage(message.toEntity())
        // Also update the session's last message time
        val session = chatDao.getSessionById(message.sessionId)
        session?.let {
            chatDao.updateSession(it.copy(lastMessageTime = message.timestamp))
        }
    }

    override suspend fun getSession(sessionId: String): ChatSession? {
        val sessionEntity = chatDao.getSessionById(sessionId)
        return sessionEntity?.let {
            val messageCount = chatDao.getMessageCountForSession(it.id)
            it.toDomain(messageCount)
        }
    }

    override suspend fun deleteSession(sessionId: String) {
        chatDao.deleteSessionById(sessionId)
    }
} 