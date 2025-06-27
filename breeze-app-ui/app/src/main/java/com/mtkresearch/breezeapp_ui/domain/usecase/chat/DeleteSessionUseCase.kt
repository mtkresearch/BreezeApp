package com.mtkresearch.breezeapp_ui.domain.usecase.chat

import com.mtkresearch.breezeapp_ui.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * A use case for deleting a chat session and all its messages.
 */
class DeleteSessionUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(sessionId: String) {
        if (sessionId.isBlank()) {
            return
        }
        chatRepository.deleteSession(sessionId)
    }
} 