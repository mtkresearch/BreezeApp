package com.mtkresearch.breezeapp.domain.usecase.chat

import com.mtkresearch.breezeapp.domain.repository.ChatRepository
import com.mtkresearch.breezeapp.presentation.chat.model.ChatSession
import javax.inject.Inject

/**
 * Use Case for loading current chat session
 */
class LoadCurrentSessionUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(): ChatSession? {
        return chatRepository.getCurrentSession()
    }
}