package com.mtkresearch.breezeapp_kotlin.domain.usecase.breezeapp

import android.util.Log
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.chatRequest
import com.mtkresearch.breezeapp.edgeai.ChatResponse
import com.mtkresearch.breezeapp.edgeai.EdgeAIException
import com.mtkresearch.breezeapp.edgeai.InvalidInputException
import com.mtkresearch.breezeapp.edgeai.ModelNotFoundException
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import com.mtkresearch.breezeapp_kotlin.domain.model.breezeapp.BreezeAppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import javax.inject.Inject

/**
 * UseCase for handling streaming chat requests
 * 
 * Responsibilities:
 * - Process streaming chat completion requests
 * - Handle streaming-specific error scenarios
 * - Provide unified error handling
 * - Return streaming chat responses
 * 
 * This UseCase follows Clean Architecture principles by:
 * - Being independent of external frameworks
 * - Having a single responsibility
 * - Being easily testable
 */
class StreamingChatUseCase @Inject constructor() {
    
    companion object {
        private const val TAG = "StreamingChatUseCase"
    }
    
    /**
     * Execute a streaming chat request
     * 
     * @param prompt The user's input prompt
     * @param systemPrompt Optional system prompt to guide the AI
     * @param temperature Controls randomness (0.0 to 2.0)
     * @param maxTokens Maximum tokens to generate
     * @return Flow of ChatResponse from BreezeApp Engine
     */
    suspend fun execute(
        prompt: String,
        systemPrompt: String = "You are a helpful AI assistant.",
        temperature: Float = 0.7f,
        maxTokens: Int? = null
    ): Flow<ChatResponse> {
        
        Log.d(TAG, "Executing streaming chat request: '$prompt'")
        
        val request = chatRequest(
            prompt = prompt,
            systemPrompt = systemPrompt,
            temperature = temperature,
            maxTokens = maxTokens,
            stream = true
        )
        
        return EdgeAI.chat(request)
            .catch { e ->
                Log.e(TAG, "Streaming chat request failed: ${e.message}")
                when (e) {
                    is InvalidInputException -> throw BreezeAppError.ChatError.InvalidInput(e.message ?: "Invalid input")
                    is ModelNotFoundException -> throw BreezeAppError.ChatError.ModelNotFound(e.message ?: "Model not found")
                    is ServiceConnectionException -> throw BreezeAppError.ConnectionError.ServiceDisconnected(e.message ?: "Connection error")
                    is EdgeAIException -> throw BreezeAppError.ChatError.StreamingError(e.message ?: "Streaming error")
                    else -> throw BreezeAppError.UnknownError(e.message ?: "Unexpected error")
                }
            }
    }
}