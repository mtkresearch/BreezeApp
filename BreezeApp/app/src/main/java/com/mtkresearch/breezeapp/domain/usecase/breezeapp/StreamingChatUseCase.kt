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
     * @param topK Optional top-k sampling parameter
     * @param topP Optional nucleus sampling parameter (0.0 to 1.0)
     * @param repetitionPenalty Optional repetition penalty parameter
     * @param model Model identifier to use (defaults to empty string, letting engine decide)
     * @return Flow of ChatResponse from BreezeApp Engine
     */
    fun execute(
        prompt: String,
        systemPrompt: String = "You are a helpful AI assistant.",
        temperature: Float = 0.7f,
        maxTokens: Int? = null,
        topK: Int? = null,
        topP: Float? = null,
        repetitionPenalty: Float? = null,
        model: String = "" // Empty string means let engine decide
    ): Flow<ChatResponse> {
        
        Log.d(TAG, "Executing streaming chat request: '$prompt'")
        Log.d(TAG, "🔥 StreamingChatUseCase DEBUG - temperature: $temperature, maxTokens: $maxTokens, topK: $topK, topP: $topP, repetitionPenalty: $repetitionPenalty")
        
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
            stream = true,
            metadata = if (metadata.isEmpty()) null else metadata
        )
        
        return EdgeAI.chat(request)
            .catch { e ->
                Log.e(TAG, "Streaming chat request failed: ${e.message}")
                when (e) {
                    is InvalidInputException -> throw BreezeAppError.ChatError.InvalidInput(e.message ?: "Invalid input")
                    is ModelNotFoundException -> throw BreezeAppError.ChatError.ModelNotFound(e.message ?: "Model not found")
                    is ServiceConnectionException -> throw BreezeAppError.ConnectionError.ServiceDisconnected(e.message ?: "Connection error")
                    is EdgeAIException -> {
                        // Pass through the EdgeAI exception message directly
                        // Guardian messages are already contained in e.message
                        val errorMessage = e.message ?: "Unknown error"
                        Log.w(TAG, "EdgeAI streaming error: $errorMessage")
                        throw BreezeAppError.ChatError.StreamingError(errorMessage)
                    }
                    else -> throw BreezeAppError.UnknownError(e.message ?: "Unexpected error")
                }
            }
    }
}