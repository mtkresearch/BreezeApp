package com.mtkresearch.breezeapp.domain.usecase.breezeapp

import android.util.Log
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequest
import com.mtkresearch.breezeapp.edgeai.ChatResponse
import com.mtkresearch.breezeapp.edgeai.EdgeAIException
import com.mtkresearch.breezeapp.edgeai.InvalidInputException
import com.mtkresearch.breezeapp.edgeai.ModelNotFoundException
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import com.mtkresearch.breezeapp.domain.model.breezeapp.BreezeAppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * UseCase for handling non-streaming chat requests
 * 
 * Responsibilities:
 * - Process chat completion requests
 * - Handle chat-specific error scenarios
 * - Provide unified error handling
 * - Return complete chat responses
 * 
 * This UseCase follows Clean Architecture principles by:
 * - Being independent of external frameworks
 * - Having a single responsibility
 * - Being easily testable
 */
class ChatUseCase @Inject constructor() {
    
    companion object {
        private const val TAG = "ChatUseCase"
    }
    
    /**
     * Execute a non-streaming chat request
     * 
     * @param prompt The user's input prompt
     * @param systemPrompt Optional system prompt to guide the AI
     * @param temperature Controls randomness (0.0 to 2.0)
     * @param maxTokens Maximum tokens to generate
     * @return ChatResponse from BreezeApp Engine
     */
    suspend fun execute(
        prompt: String,
        systemPrompt: String = "You are a helpful AI assistant.",
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): ChatResponse {
        
        Log.d(TAG, "Executing chat request: '$prompt'")
        
        val request = chatRequest(
            prompt = prompt,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = false
        )
        
        return EdgeAI.chat(request)
            .catch { e ->
                Log.e(TAG, "Chat request failed: ${e.message}")
                when (e) {
                    is InvalidInputException -> throw BreezeAppError.ChatError.InvalidInput(e.message ?: "Invalid input")
                    is ModelNotFoundException -> throw BreezeAppError.ChatError.ModelNotFound(e.message ?: "Model not found")
                    is ServiceConnectionException -> throw BreezeAppError.ConnectionError.ServiceDisconnected(e.message ?: "Connection error")
                    is EdgeAIException -> throw BreezeAppError.ChatError.GenerationFailed(e.message ?: "Unknown error")
                    else -> throw BreezeAppError.UnknownError(e.message ?: "Unexpected error")
                }
            }
            .first() // Take the first (and only) response for non-streaming
    }
}