package com.mtkresearch.breezeapp_ui.domain.usecase.chat

import com.mtkresearch.breezeapp_ui.domain.model.ChatMessage
import com.mtkresearch.breezeapp_ui.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/**
 * A use case that loads the message history for a given chat session ID.
 */
class LoadChatHistoryUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(sessionId: String?): Flow<List<ChatMessage>> {
        if (sessionId == null) {
            return emptyFlow()
        }
        return chatRepository.getChatMessages(sessionId)
    }
} 