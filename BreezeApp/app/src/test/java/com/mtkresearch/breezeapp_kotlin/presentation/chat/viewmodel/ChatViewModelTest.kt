package com.mtkresearch.breezeapp_kotlin.presentation.chat.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.UiState
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessage.MessageState
import com.mtkresearch.breezeapp_kotlin.domain.usecase.breezeapp.*
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChatViewModel
    
    // Mock dependencies
    private val mockConnectionUseCase: ConnectionUseCase = mockk(relaxed = true)
    private val mockChatUseCase: ChatUseCase = mockk(relaxed = true)
    private val mockStreamingChatUseCase: StreamingChatUseCase = mockk(relaxed = true)
    private val mockTtsUseCase: TtsUseCase = mockk(relaxed = true)
    private val mockAsrMicrophoneUseCase: AsrMicrophoneUseCase = mockk(relaxed = true)
    private val mockRequestCancellationUseCase: RequestCancellationUseCase = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ChatViewModel(
            connectionUseCase = mockConnectionUseCase,
            chatUseCase = mockChatUseCase,
            streamingChatUseCase = mockStreamingChatUseCase,
            ttsUseCase = mockTtsUseCase,
            asrMicrophoneUseCase = mockAsrMicrophoneUseCase,
            requestCancellationUseCase = mockRequestCancellationUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `初始狀態應該正確`() = runTest(testDispatcher) {
        // Given - 剛創建的ViewModel

        // When - 檢查初始狀態
        val messages = viewModel.messages.first()
        val inputText = viewModel.inputText.first()
        val canSendMessage = viewModel.canSendMessage.first()
        val isAIResponding = viewModel.isAIResponding.first()
        val isListening = viewModel.isListening.first()

        // Then - 驗證初始狀態
        assertEquals("應該有1條歡迎訊息", 1, messages.size) // ChatViewModel初始化時載入歡迎訊息
        assertFalse("歡迎訊息應該來自AI", messages[0].isFromUser)
        assertTrue("歡迎訊息應該包含助手介紹", messages[0].text.contains("BreezeApp AI助手"))
        assertEquals("輸入文字應該為空", "", inputText)
        assertTrue("應該可以發送訊息", canSendMessage)
        assertFalse("AI應該不在回應中", isAIResponding)
        assertFalse("應該不在語音識別中", isListening)
    }

    @Test
    fun `發送訊息應該添加用戶訊息並觸發AI回應`() = runTest(testDispatcher) {
        // Given
        val testMessage = "Hello, AI!"

        // When
        viewModel.sendMessage(testMessage)
        advanceTimeBy(100) // 讓用戶訊息先添加

        // Then
        val messages = viewModel.messages.first()
        assertEquals("應該有3條訊息", 3, messages.size) // 歡迎訊息 + 用戶訊息 + AI思考

        // 找到用戶訊息（應該是第二條）
        val userMessage = messages[1]
        assertEquals("訊息內容應該正確", testMessage, userMessage.text)
        assertTrue("應該是用戶訊息", userMessage.isFromUser)
        assertEquals("訊息狀態應該是正常", MessageState.NORMAL, userMessage.state)
        assertTrue("AI應該在回應中", viewModel.isAIResponding.first())
    }

    @Test
    fun `完整的AI回應流程應該正確`() = runTest(testDispatcher) {
        // Given
        val userMessage = "Test message"

        // When
        viewModel.sendMessage(userMessage)
        
        // 等待AI回應完成 (模擬時間: 1500ms + 隨機時間 + 800ms打字時間)
        advanceTimeBy(3000)

        // Then
        val messages = viewModel.messages.first()
        // 修正期望：初始有歡迎訊息 + 用戶訊息 + AI回應 = 3條訊息
        assertEquals("應該有3條訊息", 3, messages.size)
        
        // 檢查歡迎訊息
        val welcomeMsg = messages[0]
        assertTrue("歡迎訊息應該包含助手介紹", welcomeMsg.text.contains("BreezeApp AI助手"))
        assertFalse("歡迎訊息應該來自AI", welcomeMsg.isFromUser)
        
        // 檢查用戶訊息
        val userMsg = messages[1]
        assertEquals("用戶訊息內容正確", userMessage, userMsg.text)
        assertTrue("應該是用戶訊息", userMsg.isFromUser)
        assertEquals("訊息狀態應該是正常", MessageState.NORMAL, userMsg.state)
        
        // 檢查AI回應
        val aiMsg = messages[2]
        assertFalse("應該是AI訊息", aiMsg.isFromUser)
        assertTrue("AI回應應該包含標註", aiMsg.text.contains("模擬回應"))
        assertEquals("AI訊息狀態應該是正常", MessageState.NORMAL, aiMsg.state)
        
        // 檢查狀態重置
        assertFalse("AI應該不在回應中", viewModel.isAIResponding.first())
        // AI回應完成後，如果有文字輸入就可以發送
        viewModel.updateInputText("New message")
        assertTrue("有文字輸入時應該可以發送訊息", viewModel.canSendMessage.first())
    }

    @Test
    fun `空白訊息不應該發送`() = runTest(testDispatcher) {
        // Given
        val emptyMessages = listOf("", "   ", "\n", "\t")
        
        // 獲取初始訊息數量（應該只有歡迎訊息）
        val initialMessages = viewModel.messages.first()
        val initialCount = initialMessages.size

        // When & Then
        emptyMessages.forEach { message ->
            viewModel.sendMessage(message)
            advanceTimeBy(100)
            
            val messages = viewModel.messages.first()
            assertEquals("空白訊息不應該被添加", initialCount, messages.size)
        }
    }

    @Test
    fun `輸入文字更新應該正確`() = runTest(testDispatcher) {
        // Given
        val testText = "Test input"

        // When
        viewModel.updateInputText(testText)

        // Then
        assertEquals("輸入文字應該更新", testText, viewModel.inputText.first())
    }

    @Test
    fun `語音識別狀態管理應該正確`() = runTest(testDispatcher) {
        // Given - 初始狀態
        assertFalse("初始不應該在語音識別", viewModel.isListening.first())

        // When - 開始語音識別
        viewModel.startVoiceRecognition()
        advanceTimeBy(100)

        // Then - 檢查狀態更新
        assertTrue("應該在語音識別中", viewModel.isListening.first())
        
        // When - 停止語音識別
        viewModel.stopVoiceRecognition()
        advanceTimeBy(100)

        // Then - 檢查狀態重置
        assertFalse("應該停止語音識別", viewModel.isListening.first())
    }

    @Test
    fun `模擬語音識別結果應該正確`() = runTest(testDispatcher) {
        // When
        viewModel.startVoiceRecognition()
        // 等待語音識別完成 (最大4000ms + 額外時間)
        advanceTimeBy(5000)

        // Then
        val inputText = viewModel.inputText.first()
        assertTrue("應該有語音識別結果", inputText.isNotBlank())
        assertFalse("語音識別應該結束", viewModel.isListening.first())
    }

    @Test
    fun `清空聊天記錄應該正確`() = runTest(testDispatcher) {
        // Given - 先添加一些訊息
        viewModel.sendMessage("Test message 1")
        advanceTimeBy(100)
        viewModel.sendMessage("Test message 2")
        advanceTimeBy(100)

        // 確認有訊息
        assertTrue("應該有訊息", viewModel.messages.first().isNotEmpty())

        // When
        viewModel.clearChat()

        // Then
        assertTrue("訊息列表應該被清空", viewModel.messages.first().isEmpty())
    }

    @Test
    fun `重試AI回應應該正確`() = runTest(testDispatcher) {
        // Given - 先發送一條訊息等AI回應
        viewModel.sendMessage("Original message")
        advanceTimeBy(3000) // 等待AI回應完成

        val originalMessages = viewModel.messages.first()
        assertEquals("應該有3條訊息", 3, originalMessages.size) // 歡迎 + 用戶 + AI

        // When - 重試最後的AI回應
        viewModel.retryLastAIResponse()
        advanceTimeBy(3000) // 等待新的AI回應

        // Then
        val newMessages = viewModel.messages.first()
        assertEquals("仍然應該有3條訊息", 3, newMessages.size) // 數量不變
        assertTrue("用戶訊息應該保持不變", newMessages[1].isFromUser)
        assertFalse("AI訊息應該是新的", newMessages[2].isFromUser)
        // AI回應內容可能不同（因為隨機性），但結構應該正確
        assertTrue("新的AI回應應該包含標註", newMessages[2].text.contains("模擬回應"))
    }

    @Test
    fun `沒有用戶訊息時重試應該不執行`() = runTest(testDispatcher) {
        // Given - 清空聊天記錄，只留歡迎訊息
        viewModel.clearChat()
        
        val messages = viewModel.messages.first()
        assertTrue("聊天記錄應該被清空", messages.isEmpty())

        // When
        viewModel.retryLastAIResponse()
        advanceTimeBy(100)

        // Then
        val messagesAfter = viewModel.messages.first()
        assertTrue("仍然應該沒有訊息", messagesAfter.isEmpty())
        assertFalse("AI不應該在回應中", viewModel.isAIResponding.first())
    }

    @Test
    fun `AI回應中時不應該能發送新訊息`() = runTest(testDispatcher) {
        // Given
        viewModel.sendMessage("First message")
        advanceTimeBy(100) // AI開始回應

        // 確認AI正在回應
        assertTrue("AI應該在回應中", viewModel.isAIResponding.first())
        assertFalse("不應該能發送訊息", viewModel.canSendMessage.first())

        // When - 嘗試發送新訊息
        viewModel.sendMessage("Second message")
        advanceTimeBy(100)

        // Then - 新訊息不應該被發送，應該有歡迎訊息 + 第一條用戶訊息 + AI正在思考
        val messages = viewModel.messages.first()
        val userMessages = messages.filter { it.isFromUser }
        assertEquals("應該只有1條用戶訊息", 1, userMessages.size)
        assertTrue("唯一的用戶訊息應該是第一條", userMessages[0].text == "First message")
    }

    @Test
    fun `語音識別中時不應該能發送訊息`() = runTest(testDispatcher) {
        // Given
        val initialMessageCount = viewModel.messages.first().size // 包含歡迎訊息
        
        viewModel.startVoiceRecognition()
        advanceTimeBy(500) // 等待語音識別開始

        // 確認在語音識別中
        assertTrue("應該在語音識別中", viewModel.isListening.first())

        // When - 嘗試發送訊息
        viewModel.sendMessage("Test message")
        advanceTimeBy(500)

        // Then - 訊息不應該被發送（訊息數量不變）
        val messages = viewModel.messages.first()
        assertEquals("訊息數量不應該增加", initialMessageCount, messages.size)
        assertFalse("歡迎訊息應該來自AI", messages[0].isFromUser)
    }

    @Test
    fun `UI狀態繼承測試 - 錯誤處理`() = runTest(testDispatcher) {
        // Given - 檢查初始UI狀態
        val initialState = viewModel.uiState.first()
        assertEquals("初始狀態應該是IDLE", UiState.IDLE, initialState.state)

        // 由於ChatViewModel主要使用內部狀態管理，這裡主要驗證繼承的BaseViewModel功能正常
        assertTrue("ViewModel應該正確繼承BaseViewModel", viewModel.isLoading.first() == false)
        assertTrue("錯誤狀態應該正確初始化", viewModel.error.first() == null)
    }

    @Test
    fun `會話管理功能測試`() = runTest(testDispatcher) {
        // Given - 創建一個有訊息的聊天
        viewModel.sendMessage("Test message")
        advanceTimeBy(3000)
        
        val messagesBeforeNew = viewModel.messages.first()
        assertTrue("應該有訊息", messagesBeforeNew.isNotEmpty())

        // When - 創建新會話
        viewModel.createNewSession()

        // Then - 新會話應該只有歡迎訊息
        val messagesAfterNew = viewModel.messages.first()
        assertEquals("新會話應該只有歡迎訊息", 1, messagesAfterNew.size)
        assertFalse("歡迎訊息應該來自AI", messagesAfterNew[0].isFromUser)
        
        // 檢查會話列表
        val sessions = viewModel.chatSessions.first()
        assertEquals("應該有1個歷史會話", 1, sessions.size)
        assertEquals("會話應該包含之前的訊息", messagesBeforeNew.size, sessions[0].messages.size)
    }

    @Test
    fun `訊息ID生成應該唯一`() = runTest(testDispatcher) {
        // When - 發送多條訊息
        repeat(5) { index ->
            viewModel.sendMessage("Message $index")
            advanceTimeBy(100)
        }

        // Then
        val messages = viewModel.messages.first()
        val ids = messages.map { it.id }
        assertEquals("所有ID應該唯一", ids.size, ids.toSet().size)
        assertTrue("所有ID都應該有值", ids.all { it.isNotBlank() })
    }

    @Test
    fun `timestampGenerationShouldBeCorrect`() = runTest(testDispatcher) {
        // Given
        val startTime = System.currentTimeMillis()

        // When
        viewModel.sendMessage("Test message")
        advanceTimeBy(100)

        // Then
        val messages = viewModel.messages.first()
        // 取最新的訊息（用戶訊息，歡迎訊息是第一條）
        val latestMessage = messages.last { it.isFromUser }
        assertTrue("時間戳記應該在合理範圍內", latestMessage.timestamp >= startTime - 5000) // 允許5秒誤差
        assertTrue("時間戳記不應該太久以前", latestMessage.timestamp <= System.currentTimeMillis() + 5000) // 允許5秒誤差
    }
} 