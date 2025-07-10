package com.mtkresearch.breezeapp.edgeai

/**
 * Convenience builders for EdgeAI requests to simplify common use cases
 * while maintaining full standard API compatibility
 */

/**
 * Simple chat completion builder for basic text conversations
 */
fun chatRequest(
    model: String = "breeze2",
    prompt: String,
    systemPrompt: String? = null,
    temperature: Float? = null,
    maxTokens: Int? = null,
    stream: Boolean = false
): ChatRequest {
    val messages = mutableListOf<ChatMessage>()
    
    // Add system prompt if provided
    if (systemPrompt != null) {
        messages.add(ChatMessage(role = "system", content = systemPrompt))
    }
    
    // Add user prompt
    messages.add(ChatMessage(role = "user", content = prompt))
    
    return ChatRequest(
        model = model,
        messages = messages,
        temperature = temperature,
        maxCompletionTokens = maxTokens,
        stream = stream
    )
}

/**
 * Chat completion builder with conversation history
 */
fun chatRequestWithHistory(
    model: String = "breeze2",
    messages: List<ChatMessage>,
    temperature: Float? = null,
    maxTokens: Int? = null,
    stream: Boolean = false
): ChatRequest {
    return ChatRequest(
        model = model,
        messages = messages,
        temperature = temperature,
        maxCompletionTokens = maxTokens,
        stream = stream
    )
}

/**
 * Simple TTS request builder
 */
fun ttsRequest(
    input: String,
    model: String = "tts-1",
    voice: String = "alloy",
    speed: Float? = null,
    format: String = "mp3"
): TTSRequest {
    return TTSRequest(
        input = input,
        model = model,
        voice = voice,
        speed = speed,
        responseFormat = format
    )
}

/**
 * Simple ASR request builder
 */
fun asrRequest(
    audioBytes: ByteArray,
    model: String = "whisper-1",
    language: String? = null,
    format: String = "json",
    temperature: Float? = null
): ASRRequest {
    return ASRRequest(
        file = audioBytes,
        model = model,
        language = language,
        responseFormat = format,
        temperature = temperature
    )
}

/**
 * ASR request builder with detailed options
 */
fun asrRequestDetailed(
    audioBytes: ByteArray,
    model: String = "whisper-1",
    language: String? = null,
    prompt: String? = null,
    format: String = "verbose_json",
    includeWordTimestamps: Boolean = false,
    temperature: Float? = null,
    stream: Boolean = false
): ASRRequest {
    val timestampGranularities = if (includeWordTimestamps) {
        listOf("word", "segment")
    } else {
        listOf("segment")
    }
    
    return ASRRequest(
        file = audioBytes,
        model = model,
        language = language,
        prompt = prompt,
        responseFormat = format,
        temperature = temperature,
        timestampGranularities = timestampGranularities,
        stream = stream
    )
}

// Extension functions for building conversation history

/**
 * Extension function to easily build conversation history
 */
fun List<ChatMessage>.addUser(content: String): List<ChatMessage> {
    return this + ChatMessage(role = "user", content = content)
}

fun List<ChatMessage>.addAssistant(content: String): List<ChatMessage> {
    return this + ChatMessage(role = "assistant", content = content)
}

fun List<ChatMessage>.addSystem(content: String): List<ChatMessage> {
    return this + ChatMessage(role = "system", content = content)
}

/**
 * Create conversation history from scratch
 */
fun conversationHistory(vararg messages: Pair<String, String>): List<ChatMessage> {
    return messages.map { (role, content) ->
        ChatMessage(role = role, content = content)
    }
}

/**
 * DSL-style conversation builder
 */
class ConversationBuilder {
    private val messages = mutableListOf<ChatMessage>()
    
    fun system(content: String) {
        messages.add(ChatMessage(role = "system", content = content))
    }
    
    fun user(content: String) {
        messages.add(ChatMessage(role = "user", content = content))
    }
    
    fun assistant(content: String) {
        messages.add(ChatMessage(role = "assistant", content = content))
    }
    
    fun build(): List<ChatMessage> = messages.toList()
}

fun conversation(builder: ConversationBuilder.() -> Unit): List<ChatMessage> {
    return ConversationBuilder().apply(builder).build()
} 