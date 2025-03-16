package com.mtkresearch.breezeapp.message

import com.mtkresearch.breezeapp.message.types.AssistantMessage
import com.mtkresearch.breezeapp.message.types.MediaMessage
import com.mtkresearch.breezeapp.message.types.UserMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.junit.MockitoJUnitRunner
import java.util.Date

@RunWith(MockitoJUnitRunner::class)
class ChatMessageTest {

    @Test
    fun `user message is created with correct properties`() {
        // Arrange & Act
        val message = UserMessage(
            id = "test-id",
            content = "Hello world",
            timestamp = Date(1625097600000) // July 1, 2021
        )
        
        // Assert
        assertEquals("test-id", message.id)
        assertEquals("Hello world", message.content)
        assertEquals(1625097600000, message.timestamp.time)
        assertEquals(ChatMessageType.USER, message.type)
        assertNull(message.mediaFilePath)
        assertFalse(message.hasMedia())
    }
    
    @Test
    fun `assistant message is created with correct properties`() {
        // Arrange & Act
        val message = AssistantMessage(
            id = "test-id",
            content = "I am an assistant",
            timestamp = Date(1625097600000) // July 1, 2021
        )
        
        // Assert
        assertEquals("test-id", message.id)
        assertEquals("I am an assistant", message.content)
        assertEquals(1625097600000, message.timestamp.time)
        assertEquals(ChatMessageType.ASSISTANT, message.type)
        assertNull(message.mediaFilePath)
        assertFalse(message.hasMedia())
    }
    
    @Test
    fun `media message is created with correct properties`() {
        // Arrange & Act
        val message = MediaMessage(
            id = "test-id",
            mediaFilePath = "/path/to/image.jpg",
            timestamp = Date(1625097600000) // July 1, 2021
        )
        
        // Assert
        assertEquals("test-id", message.id)
        assertEquals("", message.content) // Should be empty for media-only message
        assertEquals(1625097600000, message.timestamp.time)
        assertEquals(ChatMessageType.MEDIA, message.type)
        assertEquals("/path/to/image.jpg", message.mediaFilePath)
        assertTrue(message.hasMedia())
    }
    
    @Test
    fun `message factory creates appropriate message types`() {
        // Arrange & Act
        val userMessage = MessageFactory.createUserMessage("Hello world")
        val assistantMessage = MessageFactory.createAssistantMessage("I am an assistant")
        val mediaMessage = MessageFactory.createMediaMessage("/path/to/image.jpg")
        val userMessageWithMedia = MessageFactory.createUserMessageWithMedia("Hello with image", "/path/to/image.jpg")
        
        // Assert
        assertNotNull(userMessage.id) // ID should be generated
        assertEquals(ChatMessageType.USER, userMessage.type)
        assertEquals("Hello world", userMessage.content)
        
        assertNotNull(assistantMessage.id)
        assertEquals(ChatMessageType.ASSISTANT, assistantMessage.type)
        assertEquals("I am an assistant", assistantMessage.content)
        
        assertNotNull(mediaMessage.id)
        assertEquals(ChatMessageType.MEDIA, mediaMessage.type)
        assertEquals("", mediaMessage.content)
        assertEquals("/path/to/image.jpg", mediaMessage.mediaFilePath)
        
        assertNotNull(userMessageWithMedia.id)
        assertEquals(ChatMessageType.USER, userMessageWithMedia.type)
        assertEquals("Hello with image", userMessageWithMedia.content)
        assertEquals("/path/to/image.jpg", userMessageWithMedia.mediaFilePath)
    }
    
    @Test
    fun `copy button state is preserved in message`() {
        // Arrange
        val message = UserMessage(
            id = "test-id",
            content = "Hello world",
            timestamp = Date()
        )
        
        // Act & Assert
        assertFalse(message.isCopyEnabled()) // Default should be false
        
        message.setCopyEnabled(true)
        assertTrue(message.isCopyEnabled())
        
        message.setCopyEnabled(false)
        assertFalse(message.isCopyEnabled())
    }
    
    @Test
    fun `text to speech state is preserved in message`() {
        // Arrange
        val message = UserMessage(
            id = "test-id",
            content = "Hello world",
            timestamp = Date()
        )
        
        // Act & Assert
        assertFalse(message.isTtsEnabled()) // Default should be false
        
        message.setTtsEnabled(true)
        assertTrue(message.isTtsEnabled())
        
        message.setTtsEnabled(false)
        assertFalse(message.isTtsEnabled())
    }
    
    @Test
    fun `message can be set as media only`() {
        // Arrange
        val message = UserMessage(
            id = "test-id",
            content = "Hello world",
            timestamp = Date()
        )
        
        // Act
        message.setMediaFilePath("/path/to/image.jpg")
        
        // Assert
        assertTrue(message.hasMedia())
        assertEquals("/path/to/image.jpg", message.mediaFilePath)
    }
} 