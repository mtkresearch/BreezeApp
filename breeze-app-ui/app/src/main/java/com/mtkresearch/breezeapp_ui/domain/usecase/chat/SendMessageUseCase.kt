package com.mtkresearch.breezeapp_ui.domain.usecase.chat

import com.mtkresearch.breezeapp_ui.domain.model.ChatMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * 發送訊息 Use Case (v2.2 - Mocked for UI Development)
 *
 * This is a temporary, fake implementation that simulates an AI response.
 */
class SendMessageUseCase @Inject constructor() { // Repository removed

    /**
     * Simulates sending a message and receiving a delayed response.
     */
    operator fun invoke(
        message: ChatMessage,
        history: List<ChatMessage>
    ): Flow<Any> { // Return type changed to avoid router dependency
        return flow {
            // Simulate AI "thinking" time
            delay(1500)

            // Create a fake success response object
            val fakeResponse = object {
                val isSuccess = true
                val text = "這是一個來自假AI的回應，用來測試UI。 您剛才說的是：'${message.content}'"
                val id = "fake-response-id"
            }
            emit(fakeResponse)
        }
    }
} 