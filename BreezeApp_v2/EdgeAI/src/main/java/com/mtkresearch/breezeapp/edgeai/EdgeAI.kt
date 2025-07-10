package com.mtkresearch.breezeapp.edgeai

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * EdgeAI SDK - OpenAI-compatible AI capabilities for Android applications
 * 
 * This is the main entry point for accessing AI services provided by the BreezeApp AI Router.
 * It provides OpenAI-compatible APIs for chat completions, text-to-speech, and speech recognition.
 * 
 * Usage:
 * 1. Call EdgeAI.initialize(context) in your Application.onCreate() or Activity.onCreate()
 * 2. Use EdgeAI.chat(), EdgeAI.tts(), or EdgeAI.asr() to access AI capabilities
 * 3. Call EdgeAI.shutdown(context) when done (e.g., in onDestroy)
 */
object EdgeAI {
    
    private const val TAG = "EdgeAI"
    private const val AI_ROUTER_SERVICE_ACTION = "com.mtkresearch.breezeapp.router.SERVICE"
    private const val AI_ROUTER_SERVICE_PACKAGE = "com.mtkresearch.breezeapp.router"
    
    private var isInitialized = false
    private var service: IAIRouterService? = null
    private var isBound = false
    private var context: Context? = null
    
    // Track pending requests and their response channels
    private val pendingRequests = ConcurrentHashMap<String, Channel<com.mtkresearch.breezeapp.edgeai.model.AIResponse>>()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected: $name")
            service = IAIRouterService.Stub.asInterface(binder)
            isBound = true
            
            // Register our listener
            service?.registerListener(aiRouterListener)
            
            Log.i(TAG, "EdgeAI SDK connected to AI Router Service")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected: $name")
            service?.unregisterListener(aiRouterListener)
            service = null
            isBound = false
            
            // Cancel all pending requests
            pendingRequests.values.forEach { channel ->
                channel.close(ServiceConnectionException("Service disconnected"))
            }
            pendingRequests.clear()
            
            Log.w(TAG, "EdgeAI SDK disconnected from AI Router Service")
        }
    }
    
    private val aiRouterListener = object : IAIRouterListener.Stub() {
        override fun onResponse(response: com.mtkresearch.breezeapp.edgeai.model.AIResponse?) {
            response?.let { aiResponse ->
                Log.d(TAG, "Received response for request: ${aiResponse.requestId}")
                
                pendingRequests[aiResponse.requestId]?.let { channel ->
                    channel.trySend(aiResponse).onFailure { throwable ->
                        Log.e(TAG, "Failed to send response to channel", throwable)
                    }
                    
                    // If this is the final response, close the channel
                    if (aiResponse.isComplete || aiResponse.state == com.mtkresearch.breezeapp.edgeai.model.AIResponse.ResponseState.ERROR) {
                        pendingRequests.remove(aiResponse.requestId)
                        channel.close()
                    }
                } ?: run {
                    Log.w(TAG, "Received response for unknown request: ${aiResponse.requestId}")
                }
            }
        }
    }
    
    /**
     * Initialize the EdgeAI SDK and establish connection to the AI Router service.
     * 
     * @param context Application or Activity context
     * @throws ServiceConnectionException if initialization fails
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.w(TAG, "EdgeAI SDK is already initialized")
            return
        }
        
        this.context = context.applicationContext
        
        try {
            val intent = Intent(AI_ROUTER_SERVICE_ACTION).apply {
                setPackage(AI_ROUTER_SERVICE_PACKAGE)
            }
            
            val success = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!success) {
                throw ServiceConnectionException("Failed to bind to AI Router Service. Make sure the service is installed and available.")
            }
            
            isInitialized = true
            Log.i(TAG, "EdgeAI SDK initialization started")
            
        } catch (e: Exception) {
            throw ServiceConnectionException("Failed to initialize EdgeAI SDK: ${e.message}", e)
        }
    }
    
    /**
     * Shutdown the EdgeAI SDK and release all resources.
     * 
     * @param context Application or Activity context
     */
    fun shutdown(context: Context) {
        if (!isInitialized) {
            return
        }
        
        try {
            // Cancel all pending requests
            pendingRequests.values.forEach { channel ->
                channel.close(ServiceConnectionException("SDK is shutting down"))
            }
            pendingRequests.clear()
            
            // Unregister listener and unbind service
            if (isBound && service != null) {
                service?.unregisterListener(aiRouterListener)
                context.unbindService(serviceConnection)
            }
            
            service = null
            isBound = false
            isInitialized = false
            this.context = null
            
            Log.i(TAG, "EdgeAI SDK shutdown completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during EdgeAI SDK shutdown", e)
        }
    }
    
    /**
     * Generate chat completions using OpenAI-compatible API.
     * 
     * @param request ChatCompletionRequest containing all parameters
     * @return Flow of ChatCompletionResponse objects (for streaming) or single response (for non-streaming)
     * @throws InvalidInputException if request parameters are invalid
     * @throws ModelNotFoundException if specified model is not available
     * @throws ServiceConnectionException if SDK is not initialized
     */
    fun chat(request: ChatCompletionRequest): Flow<ChatCompletionResponse> {
        return channelFlow {
            validateConnection()
            
            // Convert ChatCompletionRequest to AIRequest
            val aiRequest = convertChatRequestToAIRequest(request)
            
            // Create channel for this request
            val responseChannel = Channel<com.mtkresearch.breezeapp.edgeai.model.AIResponse>()
            pendingRequests[aiRequest.id] = responseChannel
            
            try {
                // Send request to service
                service?.sendMessage(aiRequest)
                
                // Process responses
                for (aiResponse in responseChannel) {
                    val chatResponse = convertAIResponseToChatResponse(aiResponse, request.stream ?: false)
                    send(chatResponse)
                }
                
            } catch (e: Exception) {
                pendingRequests.remove(aiRequest.id)
                responseChannel.close()
                throw when (e) {
                    is EdgeAIException -> e
                    else -> InternalErrorException("Chat completion failed: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Convert text to speech using OpenAI-compatible API.
     * 
     * @param request TTSRequest containing all parameters
     * @return InputStream containing the generated audio data
     * @throws InvalidInputException if request parameters are invalid
     * @throws ModelNotFoundException if specified model is not available
     * @throws ServiceConnectionException if SDK is not initialized
     */
    fun tts(request: TTSRequest): InputStream {
        validateConnection()
        
        // Convert TTSRequest to AIRequest
        val aiRequest = convertTTSRequestToAIRequest(request)
        
        // Create channel for this request
        val responseChannel = Channel<com.mtkresearch.breezeapp.edgeai.model.AIResponse>()
        pendingRequests[aiRequest.id] = responseChannel
        
        try {
            // Send request to service
            service?.sendMessage(aiRequest)
            
            // Wait for response (TTS is typically not streaming)
            val aiResponse = runBlocking {
                responseChannel.receive()
            }
            
            // Clean up
            pendingRequests.remove(aiRequest.id)
            responseChannel.close()
            
            // Extract audio data from response
            return convertAIResponseToAudioStream(aiResponse)
            
        } catch (e: Exception) {
            pendingRequests.remove(aiRequest.id)
            responseChannel.close()
            throw when (e) {
                is EdgeAIException -> e
                else -> InternalErrorException("TTS failed: ${e.message}", e)
            }
        }
    }
    
    /**
     * Convert speech to text using OpenAI-compatible API.
     * 
     * @param request ASRRequest containing all parameters
     * @return Flow of ASRResponse objects for streaming, or single response for non-streaming
     * @throws InvalidInputException if request parameters are invalid
     * @throws ModelNotFoundException if specified model is not available
     * @throws AudioProcessingException if audio processing fails
     * @throws ServiceConnectionException if SDK is not initialized
     */
    fun asr(request: ASRRequest): Flow<ASRResponse> {
        return channelFlow {
            validateConnection()
            
            // Convert ASRRequest to AIRequest
            val aiRequest = convertASRRequestToAIRequest(request)
            
            // Create channel for this request
            val responseChannel = Channel<com.mtkresearch.breezeapp.edgeai.model.AIResponse>()
            pendingRequests[aiRequest.id] = responseChannel
            
            try {
                // Send request to service
                service?.sendMessage(aiRequest)
                
                // Process responses
                for (aiResponse in responseChannel) {
                    val asrResponse = convertAIResponseToASRResponse(aiResponse, request.responseFormat ?: "json")
                    send(asrResponse)
                }
                
            } catch (e: Exception) {
                pendingRequests.remove(aiRequest.id)
                responseChannel.close()
                throw when (e) {
                    is EdgeAIException -> e
                    else -> InternalErrorException("ASR failed: ${e.message}", e)
                }
            }
        }
    }
    
    /**
     * Check if the EdgeAI SDK is properly initialized and connected to the service.
     * 
     * @return true if ready to use, false otherwise
     */
    fun isReady(): Boolean {
        return isInitialized && isBound && service != null
    }
    
    // Private helper methods
    
    private fun validateConnection() {
        if (!isInitialized) {
            throw ServiceConnectionException("EdgeAI SDK is not initialized. Call EdgeAI.initialize(context) first.")
        }
        
        if (!isBound || service == null) {
            throw ServiceConnectionException("EdgeAI SDK is not connected to the AI Router Service.")
        }
    }
    
    private fun convertChatRequestToAIRequest(request: ChatCompletionRequest): com.mtkresearch.breezeapp.edgeai.model.AIRequest {
        // Convert messages to a simple prompt for now
        val prompt = request.messages.joinToString("\n") { message ->
            "${message.role}: ${message.content}"
        }
        
        val payload = com.mtkresearch.breezeapp.edgeai.model.RequestPayload.TextChat(
            prompt = prompt,
            modelName = request.model,
            temperature = request.temperature,
            maxTokens = request.maxCompletionTokens,
            streaming = request.stream ?: false
        )
        
        return com.mtkresearch.breezeapp.edgeai.model.AIRequest(payload = payload)
    }
    
    private fun convertAIResponseToChatResponse(
        aiResponse: com.mtkresearch.breezeapp.edgeai.model.AIResponse,
        isStreaming: Boolean
    ): ChatCompletionResponse {
        val choice = if (isStreaming) {
            Choice(
                index = 0,
                delta = ChatMessage(role = "assistant", content = aiResponse.text),
                finishReason = if (aiResponse.isComplete) "stop" else null
            )
        } else {
            Choice(
                index = 0,
                message = ChatMessage(role = "assistant", content = aiResponse.text),
                finishReason = "stop"
            )
        }
        
        return ChatCompletionResponse(
            id = aiResponse.requestId,
            `object` = if (isStreaming) "chat.completion.chunk" else "chat.completion",
            created = System.currentTimeMillis() / 1000,
            model = "breeze2", // TODO: extract from metadata
            choices = listOf(choice),
            usage = if (!isStreaming && aiResponse.isComplete) {
                Usage(promptTokens = 0, completionTokens = 0, totalTokens = 0) // TODO: extract from metadata
            } else null
        )
    }
    
    private fun convertTTSRequestToAIRequest(request: TTSRequest): com.mtkresearch.breezeapp.edgeai.model.AIRequest {
        val payload = com.mtkresearch.breezeapp.edgeai.model.RequestPayload.SpeechSynthesis(
            text = request.input,
            voiceId = request.voice,
            speed = request.speed,
            modelName = request.model,
            streaming = false // TTS is typically not streaming
        )
        
        return com.mtkresearch.breezeapp.edgeai.model.AIRequest(payload = payload)
    }
    
    private fun convertAIResponseToAudioStream(aiResponse: com.mtkresearch.breezeapp.edgeai.model.AIResponse): InputStream {
        // In a real implementation, the audio data would be in the response metadata or as binary data
        // For now, return empty stream as placeholder
        // TODO: Extract actual audio data from AIResponse
        return ByteArrayInputStream(ByteArray(0))
    }
    
    private fun convertASRRequestToAIRequest(request: ASRRequest): com.mtkresearch.breezeapp.edgeai.model.AIRequest {
        val payload = com.mtkresearch.breezeapp.edgeai.model.RequestPayload.AudioTranscription(
            audio = request.file,
            language = request.language,
            modelName = request.model,
            streaming = request.stream ?: false
        )
        
        return com.mtkresearch.breezeapp.edgeai.model.AIRequest(payload = payload)
    }
    
    private fun convertAIResponseToASRResponse(
        aiResponse: com.mtkresearch.breezeapp.edgeai.model.AIResponse,
        responseFormat: String
    ): ASRResponse {
        return ASRResponse(
            text = aiResponse.text,
            rawResponse = when (responseFormat) {
                "text" -> aiResponse.text
                "json" -> """{"text": "${aiResponse.text}"}"""
                else -> aiResponse.text
            },
            isChunk = !aiResponse.isComplete
        )
    }
} 