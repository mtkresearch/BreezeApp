package com.mtkresearch.breezeapp_UI.domain.usecase.chat

import com.mtkresearch.breezeapp_UI.domain.model.ChatSession
import com.mtkresearch.breezeapp_UI.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * A use case that retrieves a list of all chat sessions.
 */
class GetChatSessionsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<ChatSession>> {
        return chatRepository.getChatSessions()
    }
} 