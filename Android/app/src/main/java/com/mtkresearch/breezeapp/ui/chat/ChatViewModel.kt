package com.mtkresearch.breezeapp.ui.chat

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp.core.utils.AppConstants
import com.mtkresearch.breezeapp.core.utils.BaseEngineService
import com.mtkresearch.breezeapp.core.utils.ModelManager
import com.mtkresearch.breezeapp.core.utils.ModelType
import com.mtkresearch.breezeapp.core.utils.ServiceState
import com.mtkresearch.breezeapp.data.models.ChatMessage
import com.mtkresearch.breezeapp.data.models.MediaType
import com.mtkresearch.breezeapp.data.models.MessageFactory
import com.mtkresearch.breezeapp.data.repository.ConversationRepository
import com.mtkresearch.breezeapp.features.llm.LLMService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * ViewModel for the chat interface
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    // Application context
    private val context: Context = application.applicationContext
    
    // Repositories and managers
    private val conversationRepository = ConversationRepository()
    private val modelManager = ModelManager(context)
    
    // Services
    private var llmService: LLMService? = null
    
    // UI State
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Conversation state
    val messages = conversationRepository.messages
    
    // Service connections
    private val llmConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            llmService = (service as LLMService.LocalBinder).getService()
            Log.d(TAG, "LLM service connected")
            
            // Observe service state
            viewModelScope.launch {
                llmService?.serviceState?.collectLatest { state ->
                    when (state) {
                        is ServiceState.Ready -> {
                            _uiState.value = ChatUiState.Ready
                        }
                        is ServiceState.Error -> {
                            _uiState.value = ChatUiState.Error(state.message)
                        }
                        is ServiceState.Initializing -> {
                            _uiState.value = ChatUiState.Loading
                        }
                        else -> {}
                    }
                }
            }
            
            // Initialize the service
            initializeService()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            llmService = null
            Log.d(TAG, "LLM service disconnected")
            _uiState.value = ChatUiState.Error("LLM service disconnected")
        }
    }
    
    init {
        // Bind to services
        bindServices()
    }
    
    /**
     * Bind to required services
     */
    private fun bindServices() {
        val llmIntent = Intent(context, LLMService::class.java)
        
        // Find a suitable model file
        val llmModels = modelManager.getAvailableModels(ModelType.LLM)
        if (llmModels.isNotEmpty()) {
            val modelToUse = llmModels.first()
            llmIntent.putExtra(BaseEngineService.EXTRA_MODEL_PATH, modelToUse.path)
        }
        
        context.bindService(llmIntent, llmConnection, Context.BIND_AUTO_CREATE)
    }
    
    /**
     * Initialize services after binding
     */
    private fun initializeService() {
        viewModelScope.launch {
            llmService?.initialize()
        }
    }
    
    /**
     * Send a text message
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        // Add user message to conversation
        val userMessage = MessageFactory.createUserMessage(message)
        conversationRepository.addMessage(userMessage)
        
        // Add a processing message
        val processingMessage = MessageFactory.createProcessingMessage()
        conversationRepository.addMessage(processingMessage)
        
        // Generate response
        llmService?.generateText(
            prompt = conversationRepository.getFormattedConversationHistory(),
            callback = object : LLMService.StreamingResponseCallback {
                private val responseBuilder = StringBuilder()
                
                override fun onToken(token: String) {
                    responseBuilder.append(token)
                    conversationRepository.replaceProcessingMessage(responseBuilder.toString())
                }
                
                override fun onComplete(fullResponse: String) {
                    // Ensure the final response is set
                    conversationRepository.replaceProcessingMessage(fullResponse)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "LLM error: $error")
                    conversationRepository.replaceProcessingMessage(
                        AppConstants.LLM_ERROR_RESPONSE
                    )
                }
            }
        )
    }
    
    /**
     * Send a message with media attachment
     */
    fun sendMessageWithMedia(message: String, mediaUri: Uri, mediaType: MediaType) {
        // Add user message with media to conversation
        val userMessage = MessageFactory.createUserMessage(message, mediaUri, mediaType)
        conversationRepository.addMessage(userMessage)
        
        // Add a processing message
        val processingMessage = MessageFactory.createProcessingMessage()
        conversationRepository.addMessage(processingMessage)
        
        // In a real implementation, you would handle different types of media here
        // For now, we'll just use the LLM service with a modified prompt
        val prompt = "User sent a message with ${mediaType.name.lowercase()} attachment: $message"
        
        llmService?.generateText(
            prompt = prompt,
            callback = object : LLMService.StreamingResponseCallback {
                private val responseBuilder = StringBuilder()
                
                override fun onToken(token: String) {
                    responseBuilder.append(token)
                    conversationRepository.replaceProcessingMessage(responseBuilder.toString())
                }
                
                override fun onComplete(fullResponse: String) {
                    conversationRepository.replaceProcessingMessage(fullResponse)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "LLM error: $error")
                    conversationRepository.replaceProcessingMessage(
                        AppConstants.LLM_ERROR_RESPONSE
                    )
                }
            }
        )
    }
    
    /**
     * Clear the current conversation
     */
    fun clearConversation() {
        conversationRepository.clearConversation()
    }
    
    /**
     * Update the system prompt
     */
    fun updateSystemPrompt(prompt: String) {
        conversationRepository.setSystemPrompt(prompt)
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Unbind services
        context.unbindService(llmConnection)
        
        // Release resources
        viewModelScope.launch {
            llmService?.releaseResources()
        }
    }
}

/**
 * Sealed class representing UI states for the chat interface
 */
sealed class ChatUiState {
    data object Loading : ChatUiState()
    data object Ready : ChatUiState()
    data class Error(val message: String) : ChatUiState()
} 