package com.mtkresearch.breezeapp.domain.usecase.chat

import com.mtkresearch.breezeapp.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * Use Case for clearing current chat session
 */
class ClearCurrentSessionUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke() {
        chatRepository.clearCurrentSession()
    }
}