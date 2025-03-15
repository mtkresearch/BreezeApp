package com.mtkresearch.breezeapp.data.repository

import com.mtkresearch.breezeapp.core.utils.AppConstants
import com.mtkresearch.breezeapp.data.models.ChatMessage
import com.mtkresearch.breezeapp.data.models.MessageFactory
import com.mtkresearch.breezeapp.data.models.MessageSender
import com.mtkresearch.breezeapp.data.models.SavedConversation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
import java.util.UUID

/**
 * Repository for managing conversations and chat history
 */
class ConversationRepository {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    private var systemPrompt = AppConstants.DEFAULT_SYSTEM_PROMPT
    
    // Saved conversations list
    private val _savedConversations = MutableStateFlow<List<SavedConversation>>(emptyList())
    val savedConversations: StateFlow<List<SavedConversation>> = _savedConversations.asStateFlow()
    
    init {
        // Initialize with a system message
        addMessage(MessageFactory.createSystemMessage(systemPrompt))
    }
    
    /**
     * Add a new message to the conversation
     */
    fun addMessage(message: ChatMessage) {
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages
    }
    
    /**
     * Replace a processing message with a completed message
     */
    fun replaceProcessingMessage(content: String) {
        val currentMessages = _messages.value.toMutableList()
        val processingMessageIndex = currentMessages.indexOfLast { 
            it.sender == MessageSender.ASSISTANT && it.isProcessing 
        }
        
        if (processingMessageIndex != -1) {
            val completedMessage = MessageFactory.createAssistantMessage(content)
            currentMessages[processingMessageIndex] = completedMessage
            _messages.value = currentMessages
        } else {
            // If no processing message found, just add a new one
            addMessage(MessageFactory.createAssistantMessage(content))
        }
    }
    
    /**
     * Update the system prompt
     */
    fun setSystemPrompt(prompt: String) {
        systemPrompt = prompt
        
        // Update the system message
        val currentMessages = _messages.value.toMutableList()
        val systemMessageIndex = currentMessages.indexOfFirst { it.sender == MessageSender.SYSTEM }
        
        if (systemMessageIndex != -1) {
            currentMessages[systemMessageIndex] = MessageFactory.createSystemMessage(prompt)
        } else {
            // If no system message found, add one at the beginning
            currentMessages.add(0, MessageFactory.createSystemMessage(prompt))
        }
        
        _messages.value = currentMessages
    }
    
    /**
     * Get the current system prompt
     */
    fun getSystemPrompt(): String {
        return systemPrompt
    }
    
    /**
     * Clear all messages in the conversation
     */
    fun clearConversation() {
        _messages.value = listOf(MessageFactory.createSystemMessage(systemPrompt))
    }
    
    /**
     * Get the conversation history formatted for the LLM model
     */
    fun getFormattedConversationHistory(lookbackLimit: Int = AppConstants.DEFAULT_HISTORY_LOOKBACK): String {
        val historyBuilder = StringBuilder()
        
        // Always include the system prompt
        val systemMessage = _messages.value.firstOrNull { it.sender == MessageSender.SYSTEM }
        systemMessage?.let {
            historyBuilder.append("System: ${it.content}\n\n")
        }
        
        // Get user-assistant message pairs, limited by lookback
        val conversationMessages = _messages.value
            .filter { it.sender != MessageSender.SYSTEM }
            .takeLast(lookbackLimit * 2) // Take pairs of messages
        
        for (message in conversationMessages) {
            val role = when (message.sender) {
                MessageSender.USER -> "User"
                MessageSender.ASSISTANT -> "Assistant"
                else -> continue
            }
            historyBuilder.append("$role: ${message.content}\n\n")
        }
        
        return historyBuilder.toString()
    }
    
    /**
     * Save the current conversation as a named chat history
     */
    fun saveConversation(name: String? = null): String {
        val conversationName = name ?: "Chat ${Date()}"
        val id = UUID.randomUUID().toString()
        val savedConversation = SavedConversation.fromMessages(
            id = id,
            messages = _messages.value,
            title = conversationName
        )
        
        // Add to saved conversations
        _savedConversations.value = _savedConversations.value + savedConversation
        
        return id
    }
    
    /**
     * Load a previously saved conversation
     * @return true if loaded successfully
     */
    fun loadSavedConversation(conversationId: String): Boolean {
        // In a real app, this would load from storage/database
        // For this example, we'll just mock it
        
        // Simulate loading - in a real app, we would load the actual messages
        return true
    }
    
    /**
     * Delete a saved conversation
     */
    fun deleteSavedConversation(conversationId: String) {
        _savedConversations.value = _savedConversations.value.filter { it.id != conversationId }
    }
    
    /**
     * Get all saved conversations
     */
    fun getSavedConversations(): List<SavedConversation> {
        return _savedConversations.value
    }
    
    // Extension function to capitalize first letter
    private fun String.capitalize(): String {
        return this.lowercase().replaceFirstChar { it.uppercase() }
    }
} 