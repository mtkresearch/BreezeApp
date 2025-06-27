package com.mtkresearch.breezeapp_UI.domain.usecase.chat

import com.mtkresearch.breezeapp_router.domain.repository.AIRouterRepository
import com.mtkresearch.breezeapp_router.domain.model.AIResponse
import com.mtkresearch.breezeapp_router.domain.model.AIRequest
import com.mtkresearch.breezeapp_UI.domain.model.ChatMessage
import com.mtkresearch.breezeapp_UI.domain.model.MessageAuthor
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import java.util.UUID

/**
 * 發送訊息 Use Case (v2.1)
 *
 * 負責處理訊息發送的業務邏輯，包括：
 * - 格式化歷史紀錄
 * - 建立 AI Router 請求
 * - 調用 Repository 與 AI Router 通信
 */
class SendMessageUseCase @Inject constructor(
    private val aiRouterRepository: AIRouterRepository
) {

    /**
     * 發送訊息到 AI Router
     *
     * @param message 當前要發送的訊息
     * @param history 包含在此訊息之前的對話歷史
     * @return AI回應的Flow
     */
    operator fun invoke(
        message: ChatMessage,
        history: List<ChatMessage>
    ): Flow<AIResponse> {
        // 驗證輸入
        require(message.content.isNotBlank()) { "訊息內容不能為空" }

        // 將 ChatMessage 歷史轉換為 AIRequest 需要的格式
        val requestHistory = history.map {
            mapOf(
                "role" to if (it.author == MessageAuthor.USER) "user" else "assistant",
                "content" to it.content
            )
        }

        // 創建AI Router請求
        val request = AIRequest(
            prompt = message.content,
            history = requestHistory
        )

        // 通過 AI Router Repository 發送訊息
        return aiRouterRepository.generateChatCompletion(request)
    }
} 