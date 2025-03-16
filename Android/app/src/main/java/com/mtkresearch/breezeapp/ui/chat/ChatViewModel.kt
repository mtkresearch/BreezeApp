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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * ViewModel for the chat interface
 */
open class ChatViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ChatViewModel"
    }
    
    // Application context
    protected val context: Context = application.applicationContext
    
    // Repositories and managers
    protected var conversationRepo = ConversationRepository()
    protected var modelManager = ModelManager(context)
    
    // Services
    protected var llmService: LLMService? = null
    
    // UI State
    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    // Conversation state
    val messages = conversationRepo.messages
    
    // Saved conversations from repository
    val savedConversations = conversationRepo.savedConversations
    
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
        conversationRepo.addMessage(userMessage)
        
        // Add a processing message
        val processingMessage = MessageFactory.createProcessingMessage()
        conversationRepo.addMessage(processingMessage)
        
        // Generate response
        llmService?.generateText(
            prompt = conversationRepo.getFormattedConversationHistory(),
            callback = object : LLMService.StreamingResponseCallback {
                private val responseBuilder = StringBuilder()
                
                override fun onToken(token: String) {
                    responseBuilder.append(token)
                    conversationRepo.replaceProcessingMessage(responseBuilder.toString())
                }
                
                override fun onComplete(fullResponse: String) {
                    // Ensure the final response is set
                    conversationRepo.replaceProcessingMessage(fullResponse)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "LLM error: $error")
                    conversationRepo.replaceProcessingMessage(
                        AppConstants.LLM_ERROR_RESPONSE
                    )
                }
            }
        )
    }
    
    /**
     * Send a message with media
     */
    fun sendMessageWithMedia(message: String, mediaUri: Uri, mediaType: MediaType) {
        // Add user message with media
        val userMessage = MessageFactory.createUserMessage(
            content = message,
            mediaUri = mediaUri,
            mediaType = mediaType
        )
        conversationRepo.addMessage(userMessage)
        
        // Add processing message
        val processingMessage = MessageFactory.createProcessingMessage()
        conversationRepo.addMessage(processingMessage)
        
        // Generate response with context about the media
        val mediaDescription = when (mediaType) {
            MediaType.IMAGE -> "[User has attached an image]"
            MediaType.AUDIO -> "[User has attached an audio file]"
            MediaType.VIDEO -> "[User has attached a video]"
            MediaType.DOCUMENT -> "[User has attached a document]"
            MediaType.FILE -> "[User has attached a file]"
            MediaType.NONE -> ""
        }
        
        val prompt = "$mediaDescription\n\n${conversationRepo.getFormattedConversationHistory()}"
        generateResponse(prompt)
    }
    
    /**
     * Send a media-only message
     */
    fun sendMediaMessage(mediaUri: Uri, mediaType: MediaType) {
        // Add media-only message
        val mediaMessage = MessageFactory.createMediaMessage(mediaUri, mediaType)
        conversationRepo.addMessage(mediaMessage)
        
        // Add processing message
        val processingMessage = MessageFactory.createProcessingMessage()
        conversationRepo.addMessage(processingMessage)
        
        // Generate response with context about the media
        val mediaDescription = when (mediaType) {
            MediaType.IMAGE -> "[User has attached an image]"
            MediaType.AUDIO -> "[User has attached an audio file]"
            MediaType.VIDEO -> "[User has attached a video]"
            MediaType.DOCUMENT -> "[User has attached a document]"
            MediaType.FILE -> "[User has attached a file]"
            MediaType.NONE -> ""
        }
        
        val prompt = "$mediaDescription\n\n${conversationRepo.getFormattedConversationHistory()}"
        generateResponse(prompt)
    }
    
    /**
     * Clear the current conversation
     */
    fun clearConversation() {
        conversationRepo.clearConversation()
    }
    
    /**
     * Update the system prompt
     */
    fun updateSystemPrompt(prompt: String) {
        conversationRepo.setSystemPrompt(prompt)
    }
    
    /**
     * Get the current system prompt
     */
    fun getSystemPrompt(): String {
        return conversationRepo.getSystemPrompt()
    }
    
    /**
     * Save the current conversation
     */
    fun saveConversation(name: String): String {
        return conversationRepo.saveConversation(name)
    }
    
    /**
     * Load a saved conversation
     */
    fun loadConversation(conversationId: String): Boolean {
        return conversationRepo.loadSavedConversation(conversationId)
    }
    
    /**
     * Delete a specific message from the conversation
     */
    fun deleteMessage(message: ChatMessage) {
        // Implementation would depend on how messages are stored
        // This is a placeholder
    }
    
    /**
     * Access to repository for external components
     */
    fun getConversationRepository(): ConversationRepository {
        return conversationRepo
    }
    
    /**
     * Generate a response from the LLM service
     */
    private fun generateResponse(prompt: String) {
        llmService?.generateText(
            prompt = prompt,
            temperature = AppConstants.DEFAULT_TEMPERATURE,
            callback = object : LLMService.StreamingResponseCallback {
                private val responseBuilder = StringBuilder()
                
                override fun onToken(token: String) {
                    responseBuilder.append(token)
                    conversationRepo.replaceProcessingMessage(responseBuilder.toString())
                }
                
                override fun onComplete(fullResponse: String) {
                    conversationRepo.replaceProcessingMessage(fullResponse)
                }
                
                override fun onError(error: String) {
                    Log.e(TAG, "LLM error: $error")
                    conversationRepo.replaceProcessingMessage(
                        AppConstants.LLM_ERROR_RESPONSE
                    )
                }
            }
        )
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