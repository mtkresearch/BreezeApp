package com.mtkresearch.breezeapp.edgeai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

/**
 * Example usage of the EdgeAI SDK demonstrating all three main APIs:
 * - Chat Completion
 * - Text-to-Speech
 * - Automatic Speech Recognition
 * 
 * This file serves as both documentation and a reference implementation.
 */
class EdgeAIUsageExample(private val context: Context) {
    
    companion object {
        private const val TAG = "EdgeAIExample"
    }
    
    /**
     * Initialize the EdgeAI SDK (Basic - may not be connected immediately)
     * Call this once in your Application.onCreate() or Activity.onCreate()
     */
    fun initializeSDK() {
        try {
            EdgeAI.initialize(context)
            Log.i(TAG, "EdgeAI SDK initialization started")
        } catch (e: ServiceConnectionException) {
            Log.e(TAG, "Failed to initialize EdgeAI SDK", e)
        }
    }
    
    /**
     * Initialize the EdgeAI SDK and wait for connection (Recommended)
     * This suspending function ensures the service is fully connected before returning.
     */
    suspend fun initializeSDKAndWait() {
        try {
            EdgeAI.initializeAndWait(context, timeoutMs = 10000)
            Log.i(TAG, "EdgeAI SDK initialized and connected successfully")
        } catch (e: ServiceConnectionException) {
            Log.e(TAG, "Failed to initialize or connect EdgeAI SDK", e)
        }
    }
    
    /**
     * Example: Simple chat completion
     */
    fun simpleChatExample() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Using convenience function for simple chat
                val request = chatRequest(
                    prompt = "Tell me a joke about programming",
                    systemPrompt = "You are a helpful and funny assistant",
                    temperature = 0.7f
                )
                
                EdgeAI.chat(request)
                    .catch { e ->
                        Log.e(TAG, "Chat error: ${e.message}")
                    }
                    .collect { response ->
                        response.choices.forEach { choice ->
                            choice.message?.content?.let { content ->
                                Log.d(TAG, "Chat response: $content")
                            }
                        }
                    }
                    
            } catch (e: EdgeAIException) {
                Log.e(TAG, "EdgeAI error: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Streaming chat completion
     */
    fun streamingChatExample() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = chatRequest(
                    prompt = "Write a short story about a robot learning to paint",
                    temperature = 0.8f,
                    stream = true
                )
                
                EdgeAI.chat(request)
                    .catch { e ->
                        Log.e(TAG, "Streaming chat error: ${e.message}")
                    }
                    .collect { response ->
                        response.choices.forEach { choice ->
                            choice.delta?.content?.let { deltaContent ->
                                Log.d(TAG, "Streaming delta: $deltaContent")
                                // In real app: append to UI
                            }
                        }
                    }
                    
            } catch (e: EdgeAIException) {
                Log.e(TAG, "EdgeAI streaming error: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Chat with conversation history
     */
    fun chatWithHistoryExample() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Build conversation using DSL
                val messages = conversation {
                    system("You are a helpful programming tutor")
                    user("What is the difference between val and var in Kotlin?")
                    assistant("In Kotlin, `val` creates read-only properties while `var` creates mutable properties.")
                    user("Can you give me a practical example?")
                }
                
                val request = chatRequestWithHistory(
                    messages = messages,
                    temperature = 0.5f
                )
                
                EdgeAI.chat(request)
                    .catch { e ->
                        Log.e(TAG, "Chat with history error: ${e.message}")
                    }
                    .collect { response ->
                        response.choices.forEach { choice ->
                            choice.message?.content?.let { content ->
                                Log.d(TAG, "Tutor response: $content")
                            }
                        }
                    }
                    
            } catch (e: EdgeAIException) {
                Log.e(TAG, "EdgeAI conversation error: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Text-to-Speech
     */
    fun textToSpeechExample() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = ttsRequest(
                    input = "Hello! This is a test of the text-to-speech system.",
                    voice = "alloy",
                    speed = 1.0f,
                    format = "mp3"
                )
                
                EdgeAI.tts(request)
                    .catch { e ->
                        when (e) {
                            is InvalidInputException -> Log.e(TAG, "Invalid TTS input: ${e.message}")
                            is ModelNotFoundException -> Log.e(TAG, "TTS model not found: ${e.message}")
                            else -> Log.e(TAG, "TTS error: ${e.message}")
                        }
                    }
                    .collect { response ->
                        // In real app: play the audio stream
                        Log.d(TAG, "TTS audio generated: ${response.audioData.size} bytes")
                        Log.d(TAG, "Audio format: ${response.format}")
                        
                        // Convert to InputStream for playback if needed
                        // val audioStream = response.toInputStream()
                        // audioPlayer.play(audioStream)
                    }
                
            } catch (e: EdgeAIException) {
                Log.e(TAG, "TTS error: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Speech Recognition (ASR)
     */
    fun speechRecognitionExample(audioBytes: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = asrRequest(
                    audioBytes = audioBytes,
                    language = "en",
                    format = "json"
                )
                
                EdgeAI.asr(request)
                    .catch { e ->
                        Log.e(TAG, "ASR error: ${e.message}")
                    }
                    .collect { response ->
                        Log.d(TAG, "Transcription: ${response.text}")
                    }
                    
            } catch (e: AudioProcessingException) {
                Log.e(TAG, "Audio processing failed: ${e.message}")
            } catch (e: EdgeAIException) {
                Log.e(TAG, "ASR error: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Advanced ASR with word-level timestamps
     */
    fun advancedASRExample(audioBytes: ByteArray) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = asrRequestDetailed(
                    audioBytes = audioBytes,
                    language = "en",
                    format = "verbose_json",
                    includeWordTimestamps = true
                )
                
                EdgeAI.asr(request)
                    .catch { e ->
                        Log.e(TAG, "Advanced ASR error: ${e.message}")
                    }
                    .collect { response ->
                        Log.d(TAG, "Full transcription: ${response.text}")
                        
                        response.segments?.forEach { segment ->
                            Log.d(TAG, "Segment: ${segment.text} (${segment.start}s - ${segment.end}s)")
                            
                            segment.words?.forEach { word ->
                                Log.d(TAG, "  Word: ${word.word} (${word.start}s - ${word.end}s)")
                            }
                        }
                    }
                    
            } catch (e: EdgeAIException) {
                Log.e(TAG, "Advanced ASR error: ${e.message}")
            }
        }
    }
    
    /**
     * Example: Full standard API-compatible chat request
     */
    fun fullChatAPIExample() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = ChatRequest(
                    model = "breeze2",
                    messages = listOf(
                        ChatMessage(role = "system", content = "You are a helpful assistant"),
                        ChatMessage(role = "user", content = "Explain quantum computing in simple terms")
                    ),
                    temperature = 0.7f,
                    maxCompletionTokens = 500,
                    presencePenalty = 0.1f,
                    frequencyPenalty = 0.1f,
                    stream = false,
                    user = "user123"
                )
                
                EdgeAI.chat(request)
                    .catch { e ->
                        when (e) {
                            is InvalidInputException -> Log.e(TAG, "Invalid input: ${e.message}")
                            is ModelNotFoundException -> Log.e(TAG, "Model not found: ${e.message}")
                            is ServiceConnectionException -> Log.e(TAG, "Service issue: ${e.message}")
                            else -> Log.e(TAG, "Unexpected error: ${e.message}")
                        }
                    }
                    .collect { response ->
                        Log.d(TAG, "Full API response ID: ${response.id}")
                        Log.d(TAG, "Model: ${response.model}")
                        Log.d(TAG, "Created: ${response.created}")
                        
                        response.usage?.let { usage ->
                            Log.d(TAG, "Token usage - Prompt: ${usage.promptTokens}, Completion: ${usage.completionTokens}, Total: ${usage.totalTokens}")
                        }
                        
                        response.choices.forEach { choice ->
                            Log.d(TAG, "Choice ${choice.index}: ${choice.message?.content}")
                            Log.d(TAG, "Finish reason: ${choice.finishReason}")
                        }
                    }
                    
            } catch (e: EdgeAIException) {
                Log.e(TAG, "Full API error: ${e.message}")
            }
        }
    }
    
    /**
     * Cleanup - call this in onDestroy
     */
    fun cleanup() {
        EdgeAI.shutdown()
        Log.i(TAG, "EdgeAI SDK shutdown")
    }
    
    /**
     * Check if SDK is ready for use
     */
    fun checkSDKStatus(): Boolean {
        val isReady = EdgeAI.isReady()
        Log.d(TAG, "EdgeAI SDK ready: $isReady")
        return isReady
    }
} 