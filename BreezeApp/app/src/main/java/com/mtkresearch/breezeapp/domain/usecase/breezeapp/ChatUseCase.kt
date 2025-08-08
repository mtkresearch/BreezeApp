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
     * @param temperature Controls randomness (0.0 to 1.0 recommended, 0.0 to 2.0 technical max)
     * @param maxTokens Maximum tokens to generate
     * @param topK Optional top-k sampling parameter
     * @param topP Optional nucleus sampling parameter (0.0 to 1.0)
     * @param repetitionPenalty Optional repetition penalty parameter
     * @param model Model identifier to use (defaults to breeze2)
     * @return ChatResponse from BreezeApp Engine
     */
    suspend fun execute(
        prompt: String,
        systemPrompt: String = "You are a helpful AI assistant.",
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        topK: Int? = null,
        topP: Float? = null,
        repetitionPenalty: Float? = null,
        model: String = "breeze2"
    ): ChatResponse {
        
        Log.d(TAG, "Executing chat request: '$prompt'")
        
        // Build messages list
        val messages = mutableListOf<com.mtkresearch.breezeapp.edgeai.ChatMessage>()
        if (!systemPrompt.isNullOrBlank()) {
            messages.add(com.mtkresearch.breezeapp.edgeai.ChatMessage(role = "system", content = systemPrompt))
        }
        messages.add(com.mtkresearch.breezeapp.edgeai.ChatMessage(role = "user", content = prompt))

        // Build metadata for non-standard params (top_k, repetition_penalty)
        val metadata = mutableMapOf<String, String>()
        topK?.let { metadata["top_k"] = it.toString() }
        repetitionPenalty?.let { metadata["repetition_penalty"] = it.toString() }

        val request = com.mtkresearch.breezeapp.edgeai.ChatRequest(
            model = model,
            messages = messages,
            temperature = temperature,
            topP = topP,
            maxCompletionTokens = maxTokens,
            stream = false,
            metadata = if (metadata.isEmpty()) null else metadata
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