package com.mtkresearch.breezeapp.domain.usecase.chat

import com.mtkresearch.breezeapp.domain.repository.ChatRepository
import com.mtkresearch.breezeapp.presentation.chat.model.ChatSession
import javax.inject.Inject

/**
 * Use Case for saving current chat session
 */
class SaveCurrentSessionUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(session: ChatSession) {
        chatRepository.saveCurrentSession(session)
    }
}