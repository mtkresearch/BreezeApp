package com.mtkresearch.breezeapp.data.repository

import app.cash.turbine.test
import com.mtkresearch.breezeapp.core.utils.AppConstants
import com.mtkresearch.breezeapp.data.models.MessageFactory
import com.mtkresearch.breezeapp.data.models.MessageSender
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationRepositoryTest {
    
    private lateinit var repository: ConversationRepository
    
    @Before
    fun setup() {
        repository = ConversationRepository()
    }
    
    @Test
    fun `init adds system message with default prompt`() = runTest {
        // Verify the repository starts with a system message
        repository.messages.test {
            val messages = awaitItem()
            
            assertEquals(1, messages.size)
            assertEquals(MessageSender.SYSTEM, messages[0].sender)
            assertEquals(AppConstants.DEFAULT_SYSTEM_PROMPT, messages[0].content)
        }
    }
    
    @Test
    fun `addMessage adds message to conversation`() = runTest {
        val userMessage = MessageFactory.createUserMessage("Hello")
        
        // Collect messages before adding new message
        repository.messages.test {
            val initialMessages = awaitItem()
            assertEquals(1, initialMessages.size) // System message
            
            // Add a message
            repository.addMessage(userMessage)
            
            // Verify the message was added
            val updatedMessages = awaitItem()
            assertEquals(2, updatedMessages.size)
            assertEquals(userMessage, updatedMessages[1])
        }
    }
    
    @Test
    fun `replaceProcessingMessage replaces processing message content`() = runTest {
        // Add a processing message
        val processingMessage = MessageFactory.createProcessingMessage()
        repository.addMessage(processingMessage)
        
        // Replace it with content
        val responseContent = "This is a response"
        
        repository.messages.test {
            // Skip initial state
            awaitItem()
            
            // Replace the processing message
            repository.replaceProcessingMessage(responseContent)
            
            // Verify it was replaced
            val messages = awaitItem()
            assertEquals(2, messages.size)
            assertEquals(MessageSender.ASSISTANT, messages[1].sender)
            assertEquals(responseContent, messages[1].content)
            assertEquals(false, messages[1].isProcessing)
        }
    }
    
    @Test
    fun `replaceProcessingMessage adds new message if no processing message exists`() = runTest {
        val responseContent = "This is a response"
        
        repository.messages.test {
            // Skip initial state (just system message)
            awaitItem()
            
            // Try to replace a non-existent processing message
            repository.replaceProcessingMessage(responseContent)
            
            // It should add a new message instead
            val messages = awaitItem()
            assertEquals(2, messages.size)
            assertEquals(MessageSender.ASSISTANT, messages[1].sender)
            assertEquals(responseContent, messages[1].content)
        }
    }
    
    @Test
    fun `setSystemPrompt updates system message`() = runTest {
        val newPrompt = "New system prompt"
        
        repository.messages.test {
            // Skip initial state
            awaitItem()
            
            // Update system prompt
            repository.setSystemPrompt(newPrompt)
            
            // Verify the system message was updated
            val messages = awaitItem()
            assertEquals(1, messages.size)
            assertEquals(MessageSender.SYSTEM, messages[0].sender)
            assertEquals(newPrompt, messages[0].content)
            
            // Also verify the get method returns the updated prompt
            assertEquals(newPrompt, repository.getSystemPrompt())
        }
    }
    
    @Test
    fun `clearConversation resets to just system message`() = runTest {
        // Add some messages
        repository.addMessage(MessageFactory.createUserMessage("Hello"))
        repository.addMessage(MessageFactory.createAssistantMessage("Hi there"))
        
        repository.messages.test {
            // Skip current state with 3 messages
            val initialMessages = awaitItem()
            assertEquals(3, initialMessages.size)
            
            // Clear conversation
            repository.clearConversation()
            
            // Verify it was reset to just the system message
            val clearedMessages = awaitItem()
            assertEquals(1, clearedMessages.size)
            assertEquals(MessageSender.SYSTEM, clearedMessages[0].sender)
        }
    }
    
    @Test
    fun `getFormattedConversationHistory formats conversation correctly`() {
        // Add some conversation messages
        repository.addMessage(MessageFactory.createUserMessage("Hello"))
        repository.addMessage(MessageFactory.createAssistantMessage("Hi there"))
        repository.addMessage(MessageFactory.createUserMessage("How are you?"))
        
        // Get formatted conversation
        val formatted = repository.getFormattedConversationHistory()
        
        // Verify it contains all messages in correct format
        assertTrue(formatted.contains("System: ${AppConstants.DEFAULT_SYSTEM_PROMPT}"))
        assertTrue(formatted.contains("User: Hello"))
        assertTrue(formatted.contains("Assistant: Hi there"))
        assertTrue(formatted.contains("User: How are you?"))
    }
    
    @Test
    fun `getFormattedConversationHistory with lookback limits messages`() {
        // Add more messages than the lookback limit
        repository.addMessage(MessageFactory.createUserMessage("Message 1"))
        repository.addMessage(MessageFactory.createAssistantMessage("Response 1"))
        repository.addMessage(MessageFactory.createUserMessage("Message 2"))
        repository.addMessage(MessageFactory.createAssistantMessage("Response 2"))
        repository.addMessage(MessageFactory.createUserMessage("Message 3"))
        repository.addMessage(MessageFactory.createAssistantMessage("Response 3"))
        
        // Get formatted conversation with lookback of 2 (should include last 2 user-assistant pairs)
        val formatted = repository.getFormattedConversationHistory(2)
        
        // Should include system message and last 4 messages (2 pairs)
        assertTrue(formatted.contains("System: ${AppConstants.DEFAULT_SYSTEM_PROMPT}"))
        
        // Should NOT include the first pair
        assertTrue(!formatted.contains("User: Message 1"))
        assertTrue(!formatted.contains("Assistant: Response 1"))
        
        // Should include the second and third pairs
        assertTrue(formatted.contains("User: Message 2"))
        assertTrue(formatted.contains("Assistant: Response 2"))
        assertTrue(formatted.contains("User: Message 3"))
        assertTrue(formatted.contains("Assistant: Response 3"))
    }
    
    @Test
    fun `saveConversation returns conversation name`() {
        val name = "Test conversation"
        assertEquals(name, repository.saveConversation(name))
    }
} 