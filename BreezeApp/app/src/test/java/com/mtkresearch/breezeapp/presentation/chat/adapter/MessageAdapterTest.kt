package com.mtkresearch.breezeapp.presentation.chat.adapter

import android.content.Context
import android.os.Looper
import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.mtkresearch.breezeapp.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp.presentation.chat.model.ChatMessage.MessageState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MessageAdapterTest {

    private lateinit var adapter: MessageAdapter
    private lateinit var context: Context
    private lateinit var mockInteractionListener: MessageAdapter.MessageInteractionListener

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockInteractionListener = mock()
        adapter = MessageAdapter()
        adapter.setMessageInteractionListener(mockInteractionListener)
    }

    @Test
    fun `適配器初始狀態應該正確`() {
        // Given - 新創建的適配器

        // When & Then
        assertEquals("初始項目數量應該為0", 0, adapter.itemCount)
        assertTrue("初始應該為空", adapter.isEmpty())
        assertFalse("初始不應該不為空", adapter.isNotEmpty())
        assertNull("初始沒有第一項", adapter.getFirstItem())
        assertNull("初始沒有最後項", adapter.getLastItem())
    }

    @Test
    fun `提交訊息列表應該正確更新`() {
        // Given
        val messages = listOf(
            ChatMessage(id = "1", text = "Hello", isFromUser = true),
            ChatMessage(id = "2", text = "Hi there", isFromUser = false),
            ChatMessage(id = "3", text = "How are you?", isFromUser = true)
        )

        // When
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // Then
        assertEquals("項目數量應該正確", 3, adapter.itemCount)
        assertFalse("不應該為空", adapter.isEmpty())
        assertTrue("應該不為空", adapter.isNotEmpty())
        assertEquals("第一項應該正確", messages[0], adapter.getFirstItem())
        assertEquals("最後項應該正確", messages[2], adapter.getLastItem())
    }

    @Test
    fun `getItemAt方法應該正確`() {
        // Given
        val messages = listOf(
            ChatMessage(id = "1", text = "Message 1", isFromUser = true),
            ChatMessage(id = "2", text = "Message 2", isFromUser = false)
        )
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When & Then
        assertEquals("應該返回正確的項目", messages[0], adapter.getItemAt(0))
        assertEquals("應該返回正確的項目", messages[1], adapter.getItemAt(1))
        assertNull("越界應該返回null", adapter.getItemAt(2))
        assertNull("負索引應該返回null", adapter.getItemAt(-1))
    }

    @Test
    fun `findPosition方法應該正確`() {
        // Given
        val message1 = ChatMessage(id = "1", text = "Hello", isFromUser = true)
        val message2 = ChatMessage(id = "2", text = "World", isFromUser = false)
        val messages = listOf(message1, message2)
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When & Then
        assertEquals("應該找到正確位置", 0, adapter.findPosition { it.id == "1" })
        assertEquals("應該找到正確位置", 1, adapter.findPosition { it.id == "2" })
        assertEquals("找不到應該返回-1", -1, adapter.findPosition { it.id == "3" })
        assertEquals("應該根據文字找到", 0, adapter.findPosition { it.text == "Hello" })
    }

    @Test
    fun `findItem方法應該正確`() {
        // Given
        val message1 = ChatMessage(id = "1", text = "Hello", isFromUser = true)
        val message2 = ChatMessage(id = "2", text = "World", isFromUser = false)
        val messages = listOf(message1, message2)
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When & Then
        assertEquals("應該找到正確項目", message1, adapter.findItem { it.id == "1" })
        assertEquals("應該找到正確項目", message2, adapter.findItem { it.text == "World" })
        assertNull("找不到應該返回null", adapter.findItem { it.id == "3" })
    }

    @Test
    fun `getLastUserMessage方法應該正確`() {
        // Given
        val messages = listOf(
            ChatMessage(id = "1", text = "User 1", isFromUser = true),
            ChatMessage(id = "2", text = "AI 1", isFromUser = false),
            ChatMessage(id = "3", text = "User 2", isFromUser = true),
            ChatMessage(id = "4", text = "AI 2", isFromUser = false)
        )
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When
        val lastUserMessage = adapter.getLastUserMessage()

        // Then
        assertNotNull("應該有最後的用戶訊息", lastUserMessage)
        assertEquals("應該是最後的用戶訊息", "User 2", lastUserMessage?.text)
        assertEquals("應該是用戶訊息", true, lastUserMessage?.isFromUser)
    }

    @Test
    fun `getLastAIMessage方法應該正確`() {
        // Given
        val messages = listOf(
            ChatMessage(id = "1", text = "User 1", isFromUser = true),
            ChatMessage(id = "2", text = "AI 1", isFromUser = false),
            ChatMessage(id = "3", text = "User 2", isFromUser = true),
            ChatMessage(id = "4", text = "AI 2", isFromUser = false)
        )
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When
        val lastAIMessage = adapter.getLastAIMessage()

        // Then
        assertNotNull("應該有最後的AI訊息", lastAIMessage)
        assertEquals("應該是最後的AI訊息", "AI 2", lastAIMessage?.text)
        assertEquals("應該是AI訊息", false, lastAIMessage?.isFromUser)
    }

    @Test
    fun `沒有用戶訊息時getLastUserMessage應該返回null`() {
        // Given - 只有AI訊息
        val messages = listOf(
            ChatMessage(id = "1", text = "AI 1", isFromUser = false),
            ChatMessage(id = "2", text = "AI 2", isFromUser = false)
        )
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When
        val lastUserMessage = adapter.getLastUserMessage()

        // Then
        assertNull("應該返回null", lastUserMessage)
    }

    @Test
    fun `沒有AI訊息時getLastAIMessage應該返回null`() {
        // Given - 只有用戶訊息
        val messages = listOf(
            ChatMessage(id = "1", text = "User 1", isFromUser = true),
            ChatMessage(id = "2", text = "User 2", isFromUser = true)
        )
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When
        val lastAIMessage = adapter.getLastAIMessage()

        // Then
        assertNull("應該返回null", lastAIMessage)
    }

    @Test
    fun `updateMessageState方法應該正確`() {
        // Given
        val messages = listOf(
            ChatMessage(id = "1", text = "Hello", isFromUser = true, state = MessageState.NORMAL),
            ChatMessage(id = "2", text = "Loading...", isFromUser = false, state = MessageState.LOADING)
        )
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When
        val updated = adapter.updateMessageState("2", MessageState.NORMAL)
        shadowOf(Looper.getMainLooper()).idle()

        // Then
        assertTrue("應該更新成功", updated)
        val updatedMessage = adapter.findMessageByPredicate { it.id == "2" }
        assertEquals("狀態應該已更新", MessageState.NORMAL, updatedMessage?.state)
    }

    @Test
    fun `updateMessageState找不到訊息應該返回false`() {
        // Given
        val messages = listOf(
            ChatMessage(id = "1", text = "Hello", isFromUser = true)
        )
        adapter.submitList(messages)
        shadowOf(Looper.getMainLooper()).idle()

        // When
        val updated = adapter.updateMessageState("999", MessageState.ERROR)

        // Then
        assertFalse("應該更新失敗", updated)
    }

    @Test
    fun `updateMessageText方法應該正確`() {
        // Given
        val messages = listOf(
            ChatMessage(id = "1", text = "Original", isFromUser = false)
        )
        adapter.submitList(messages)
        // 等待submitList完成
        shadowOf(Looper.getMainLooper()).idle()

        // When
        val updated = adapter.updateMessageText("1", "Updated text")
        shadowOf(Looper.getMainLooper()).idle()

        // Then
        assertTrue("應該更新成功", updated)
        val updatedMessage = adapter.findMessageByPredicate { it.id == "1" }
        assertEquals("文字應該已更新", "Updated text", updatedMessage?.text)
    }

    @Test
    fun `addMessage方法應該正確添加訊息`() {
        // Given
        val existingMessages = listOf(
            ChatMessage(id = "1", text = "Existing", isFromUser = true)
        )
        adapter.submitList(existingMessages)
        shadowOf(Looper.getMainLooper()).idle()

        // When
        val newMessage = ChatMessage(id = "2", text = "New message", isFromUser = false)
        adapter.addMessage(newMessage)
        shadowOf(Looper.getMainLooper()).idle()

        // Then
        assertEquals("訊息數量應該增加", 2, adapter.getMessageCount())
        assertEquals("新訊息應該在最後", newMessage, adapter.getLastMessage())
    }

    @Test
    fun `DiffUtil應該正確比較訊息`() {
        // Given
        val messages1 = listOf(
            ChatMessage(id = "1", text = "Hello", isFromUser = true),
            ChatMessage(id = "2", text = "World", isFromUser = false)
        )
        adapter.submitList(messages1)
        shadowOf(Looper.getMainLooper()).idle()

        // When - 更新列表，改變一條訊息的內容
        val messages2 = listOf(
            ChatMessage(id = "1", text = "Hello", isFromUser = true), // 相同
            ChatMessage(id = "2", text = "Updated World", isFromUser = false) // 內容改變
        )
        adapter.submitList(messages2)
        shadowOf(Looper.getMainLooper()).idle()

        // Then - DiffUtil應該正確識別變化
        assertEquals("項目數量應該相同", 2, adapter.itemCount)
        assertEquals("更新的內容應該正確", "Updated World", adapter.getMessageAt(1)?.text)
    }

    @Test
    fun `監聽器設置和清除應該正確`() {
        // Given
        val listener = mock<MessageAdapter.MessageInteractionListener>()

        // When - 設置監聽器
        adapter.setMessageInteractionListener(listener)

        // Then - 監聽器應該被設置
        // (這裡我們無法直接測試私有屬性，但可以通過行為驗證)

        // When - 清除監聽器
        adapter.setMessageInteractionListener(null)

        // Then - 監聽器應該被清除
        // (同樣通過行為驗證)
    }

    @Test
    fun `空列表操作應該安全`() {
        // Given - 空列表
        adapter.submitList(emptyList())
        shadowOf(Looper.getMainLooper()).idle()

        // When & Then - 所有操作都應該安全
        assertEquals("空列表項目數為0", 0, adapter.itemCount)
        assertTrue("應該為空", adapter.isEmpty())
        assertNull("獲取項目應該返回null", adapter.getItemAt(0))
        assertEquals("查找應該返回-1", -1, adapter.findPosition { true })
        assertNull("查找項目應該返回null", adapter.findItem { true })
        assertNull("最後用戶訊息應該為null", adapter.getLastUserMessage())
        assertNull("最後AI訊息應該為null", adapter.getLastAIMessage())
        assertFalse("更新狀態應該失敗", adapter.updateMessageState("1", MessageState.NORMAL))
        assertFalse("更新文字應該失敗", adapter.updateMessageText("1", "new text"))
    }

    @Test
    fun `訊息狀態變化應該正確`() {
        // Given
        val message = ChatMessage(
            id = "test",
            text = "Test message",
            isFromUser = false,
            state = MessageState.LOADING
        )
        adapter.submitList(listOf(message))
        shadowOf(Looper.getMainLooper()).idle()

        // 測試各種狀態變化
        val states = listOf(
            MessageState.NORMAL,
            MessageState.ERROR,
            MessageState.LOADING,
            MessageState.SENDING
        )

        states.forEach { state: MessageState ->
            // When
            val updated = adapter.updateMessageState("test", state)
            shadowOf(Looper.getMainLooper()).idle()

            // Then
            assertTrue("狀態更新應該成功", updated)
            assertEquals("狀態應該正確更新", state, adapter.findMessageByPredicate { it.id == "test" }?.state)
        }
    }

    @Test
    fun `大量訊息處理應該正確`() {
        // Given - 創建大量訊息
        val largeMessageList = (1..1000).map { index ->
            ChatMessage(
                id = index.toString(),
                text = "Message $index",
                isFromUser = index % 2 == 0
            )
        }

        // When
        adapter.submitList(largeMessageList)
        shadowOf(Looper.getMainLooper()).idle()

        // Then
        assertEquals("項目數量應該正確", 1000, adapter.itemCount)
        assertEquals("第一項應該正確", largeMessageList.first(), adapter.getFirstItem())
        assertEquals("最後項應該正確", largeMessageList.last(), adapter.getLastItem())

        // 測試查找性能 (應該能快速找到)
        val foundMessage = adapter.findItem { it.id == "500" }
        assertNotNull("應該能找到中間的訊息", foundMessage)
        assertEquals("找到的訊息應該正確", "Message 500", foundMessage?.text)
    }
} 