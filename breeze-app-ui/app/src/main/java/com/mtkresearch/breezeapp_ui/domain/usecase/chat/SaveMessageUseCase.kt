package com.mtkresearch.breezeapp_ui.domain.usecase.chat

import com.mtkresearch.breezeapp_ui.domain.model.ChatMessage
import com.mtkresearch.breezeapp_ui.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * A use case for saving a single chat message.
 */
class SaveMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(message: ChatMessage) {
        // Business logic can be added here, e.g. validation
        if (message.content.isBlank()) {
            // In a real app, you might throw a custom domain exception here
            return
        }
        chatRepository.saveMessage(message)
    }
} 