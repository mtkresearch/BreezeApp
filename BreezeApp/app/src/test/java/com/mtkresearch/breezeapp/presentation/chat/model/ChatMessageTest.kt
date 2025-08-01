package com.mtkresearch.breezeapp.presentation.chat.model

import com.mtkresearch.breezeapp.presentation.chat.model.ChatMessage.MessageState
import org.junit.Assert.*
import org.junit.Test

class ChatMessageTest {

    @Test
    fun `創建默認ChatMessage應該有正確的屬性`() {
        // Given
        val id = "test-id"
        val text = "Test message"
        val isFromUser = true

        // When
        val message = ChatMessage(
            id = id,
            text = text,
            isFromUser = isFromUser
        )

        // Then
        assertEquals("ID應該正確", id, message.id)
        assertEquals("文字應該正確", text, message.text)
        assertEquals("用戶標記應該正確", isFromUser, message.isFromUser)
        assertEquals("默認狀態應該是NORMAL", MessageState.NORMAL, message.state)
        assertTrue("時間戳記應該在合理範圍內", message.timestamp > 0)
        assertTrue("時間戳記不應該太久以前", message.timestamp <= System.currentTimeMillis())
    }

    @Test
    fun `創建帶有狀態的ChatMessage應該正確`() {
        // Given
        val message = ChatMessage(
            id = "test-id",
            text = "Test message",
            isFromUser = false,
            state = MessageState.LOADING
        )

        // Then
        assertEquals("狀態應該正確設置", MessageState.LOADING, message.state)
    }

    @Test
    fun `創建帶有時間戳記的ChatMessage應該正確`() {
        // Given
        val customTimestamp = 1234567890L
        val message = ChatMessage(
            id = "test-id",
            text = "Test message",
            isFromUser = true,
            timestamp = customTimestamp
        )

        // Then
        assertEquals("自定義時間戳記應該正確", customTimestamp, message.timestamp)
    }

    @Test
    fun `兩個相同內容的ChatMessage應該相等`() {
        // Given
        val timestamp = System.currentTimeMillis()
        val message1 = ChatMessage(
            id = "same-id",
            text = "Same text",
            isFromUser = true,
            state = MessageState.NORMAL,
            timestamp = timestamp
        )
        val message2 = ChatMessage(
            id = "same-id",
            text = "Same text",
            isFromUser = true,
            state = MessageState.NORMAL,
            timestamp = timestamp
        )

        // When & Then
        assertEquals("相同內容的訊息應該相等", message1, message2)
        assertEquals("相同內容的訊息應該有相同hashCode", message1.hashCode(), message2.hashCode())
    }

    @Test
    fun `不同ID的ChatMessage應該不相等`() {
        // Given
        val message1 = ChatMessage(id = "id1", text = "Same text", isFromUser = true)
        val message2 = ChatMessage(id = "id2", text = "Same text", isFromUser = true)

        // When & Then
        assertNotEquals("不同ID的訊息應該不相等", message1, message2)
    }

    @Test
    fun `不同文字的ChatMessage應該不相等`() {
        // Given
        val message1 = ChatMessage(id = "same-id", text = "Text 1", isFromUser = true)
        val message2 = ChatMessage(id = "same-id", text = "Text 2", isFromUser = true)

        // When & Then
        assertNotEquals("不同文字的訊息應該不相等", message1, message2)
    }

    @Test
    fun `不同用戶標記的ChatMessage應該不相等`() {
        // Given
        val message1 = ChatMessage(id = "same-id", text = "Same text", isFromUser = true)
        val message2 = ChatMessage(id = "same-id", text = "Same text", isFromUser = false)

        // When & Then
        assertNotEquals("不同用戶標記的訊息應該不相等", message1, message2)
    }

    @Test
    fun `不同狀態的ChatMessage應該不相等`() {
        // Given
        val message1 = ChatMessage(
            id = "same-id",
            text = "Same text",
            isFromUser = true,
            state = MessageState.NORMAL
        )
        val message2 = ChatMessage(
            id = "same-id",
            text = "Same text",
            isFromUser = true,
            state = MessageState.LOADING
        )

        // When & Then
        assertNotEquals("不同狀態的訊息應該不相等", message1, message2)
    }

    @Test
    fun `不同時間戳記的ChatMessage應該不相等`() {
        // Given
        val message1 = ChatMessage(
            id = "same-id",
            text = "Same text",
            isFromUser = true,
            timestamp = 1000L
        )
        val message2 = ChatMessage(
            id = "same-id",
            text = "Same text",
            isFromUser = true,
            timestamp = 2000L
        )

        // When & Then
        assertNotEquals("不同時間戳記的訊息應該不相等", message1, message2)
    }

    @Test
    fun `copy方法應該正確創建副本`() {
        // Given
        val original = ChatMessage(
            id = "original-id",
            text = "Original text",
            isFromUser = true,
            state = MessageState.NORMAL,
            timestamp = 1234567890L
        )

        // When
        val copy = original.copy()

        // Then
        assertEquals("副本應該與原本相等", original, copy)
        assertNotSame("副本應該是不同的對象", original, copy)
    }

    @Test
    fun `copy方法修改屬性應該正確`() {
        // Given
        val original = ChatMessage(
            id = "original-id",
            text = "Original text",
            isFromUser = true,
            state = MessageState.NORMAL
        )

        // When
        val modified = original.copy(
            text = "Modified text",
            state = MessageState.LOADING
        )

        // Then
        assertEquals("ID應該保持不變", original.id, modified.id)
        assertEquals("isFromUser應該保持不變", original.isFromUser, modified.isFromUser)
        assertEquals("timestamp應該保持不變", original.timestamp, modified.timestamp)
        assertEquals("文字應該已修改", "Modified text", modified.text)
        assertEquals("狀態應該已修改", MessageState.LOADING, modified.state)
    }

    @Test
    fun `toString方法應該包含所有重要屬性`() {
        // Given
        val message = ChatMessage(
            id = "test-id",
            text = "Test message",
            isFromUser = true,
            state = MessageState.NORMAL,
            timestamp = 1234567890L
        )

        // When
        val stringRepresentation = message.toString()

        // Then
        assertTrue("toString應該包含ID", stringRepresentation.contains("test-id"))
        assertTrue("toString應該包含文字", stringRepresentation.contains("Test message"))
        assertTrue("toString應該包含用戶標記", stringRepresentation.contains("true"))
        assertTrue("toString應該包含狀態", stringRepresentation.contains("NORMAL"))
        assertTrue("toString應該包含時間戳記", stringRepresentation.contains("1234567890"))
    }

    @Test
    fun `空字串訊息應該正確處理`() {
        // Given
        val message = ChatMessage(
            id = "empty-text-id",
            text = "",
            isFromUser = true
        )

        // When & Then
        assertEquals("空字串應該正確保存", "", message.text)
        assertNotNull("訊息對象應該不為null", message)
    }

    @Test
    fun `很長的訊息文字應該正確處理`() {
        // Given
        val longText = "A".repeat(10000) // 10K字符的長文字
        val message = ChatMessage(
            id = "long-text-id",
            text = longText,
            isFromUser = false
        )

        // When & Then
        assertEquals("長文字應該正確保存", longText, message.text)
        assertEquals("長度應該正確", 10000, message.text.length)
    }

    @Test
    fun `包含特殊字符的訊息應該正確處理`() {
        // Given
        val specialText = "特殊字符: 🚀 emoji, \n換行, \t製表符, \"引號\", '單引號', \\反斜線"
        val message = ChatMessage(
            id = "special-char-id",
            text = specialText,
            isFromUser = true
        )

        // When & Then
        assertEquals("特殊字符應該正確保存", specialText, message.text)
    }

    @Test
    fun `用戶訊息和AI訊息應該能正確區分`() {
        // Given
        val userMessage = ChatMessage(
            id = "user-msg",
            text = "User message",
            isFromUser = true
        )
        val aiMessage = ChatMessage(
            id = "ai-msg",
            text = "AI message",
            isFromUser = false
        )

        // When & Then
        assertTrue("用戶訊息應該標記為用戶", userMessage.isFromUser)
        assertFalse("AI訊息應該標記為非用戶", aiMessage.isFromUser)
        assertNotEquals("用戶訊息和AI訊息應該不相等", userMessage, aiMessage)
    }

    @Test
    fun `所有MessageState狀態都應該能正確設置`() {
        val states = listOf(
            MessageState.NORMAL,
            MessageState.LOADING,
            MessageState.ERROR,
            MessageState.SENDING
        )

        states.forEach { state: MessageState ->
            // When
            val message = ChatMessage(
                id = "state-test-${state.name}",
                text = "Test for $state",
                isFromUser = true,
                state = state
            )

            // Then
            assertEquals("狀態 $state 應該正確設置", state, message.state)
        }
    }

    @Test
    fun `時間戳記應該是合理的值`() {
        // Given
        val beforeCreation = System.currentTimeMillis()
        
        // When
        val message = ChatMessage(
            id = "timestamp-test",
            text = "Timestamp test",
            isFromUser = true
        )
        
        val afterCreation = System.currentTimeMillis()

        // Then
        assertTrue("時間戳記應該在創建前後的範圍內", 
            message.timestamp >= beforeCreation && message.timestamp <= afterCreation)
    }

    @Test
    fun `data class的componentN方法應該正確`() {
        // Given
        val message = ChatMessage(
            id = "component-test",
            text = "Component test",
            isFromUser = true,
            timestamp = 9999999L,
            state = MessageState.LOADING
        )

        // When
        val (id, text, isFromUser, timestamp, state) = message

        // Then
        assertEquals("component1 (id) 應該正確", "component-test", id)
        assertEquals("component2 (text) 應該正確", "Component test", text)
        assertEquals("component3 (isFromUser) 應該正確", true, isFromUser)
        assertEquals("component4 (timestamp) 應該正確", 9999999L, timestamp)
        assertEquals("component5 (state) 應該正確", MessageState.LOADING, state)
    }
} 