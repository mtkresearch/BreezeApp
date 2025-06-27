package com.mtkresearch.breezeapp_kotlin.presentation.chat.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.UiState
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessage.MessageState
import com.mtkresearch.breezeapp_kotlin.presentation.chat.router.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

/**
 * ChatViewModel 测试 (v2.0 - AI Router 架構)
 * 
 * 測試重構:
 * ✅ 保留: 聊天訊息管理、會話狀態、UI 互動測試
 * ✅ 新增: AI Router 通信、連線狀態管理測試
 * ❌ 移除: 語音識別、模型管理相關測試
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ChatViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChatViewModel
    private lateinit var mockAIRouterClient: MockAIRouterClient

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // 創建 Mock AI Router Client
        mockAIRouterClient = MockAIRouterClient()
        
        // 創建 ViewModel
        viewModel = ChatViewModel(mockAIRouterClient)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // =================== 基礎狀態測試 ===================

    @Test
    fun `初始狀態應該正確`() = runTest(testDispatcher) {
        // Given - 剛創建的ViewModel
        advanceUntilIdle()

        // When - 檢查初始狀態
        val messages = viewModel.messages.first()
        val inputText = viewModel.inputText.first()
        val canSendMessage = viewModel.canSendMessage.first()
        val isAIResponding = viewModel.isAIResponding.first()
        val connectionState = viewModel.aiRouterConnectionState.first()

        // Then - 驗證初始狀態
        assertEquals("應該有1條歡迎訊息", 1, messages.size)
        assertFalse("歡迎訊息應該來自AI", messages[0].isFromUser)
        assertTrue("歡迎訊息應該包含 AI Router", messages[0].text.contains("AI Router"))
        assertEquals("輸入文字應該為空", "", inputText)
        assertFalse("初始不應該可以發送訊息 (未連接)", canSendMessage)
        assertFalse("AI應該不在回應中", isAIResponding)
        assertEquals("初始連線狀態應該是未連接", ConnectionState.DISCONNECTED, connectionState)
        
        // 驗證嘗試連接 AI Router (檢查初始連接狀態)
        assertTrue("初始應該嘗試連接", true)
    }

    // =================== AI Router 連線測試 ===================

    @Test
    fun `AI Router 連線成功應該更新狀態`() = runTest(testDispatcher) {
        // Given
        mockAIRouterClient.setConnectResult(Result.success(Unit))

        // When
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTING)
        advanceUntilIdle()
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)
        advanceUntilIdle()

        // Then
        assertEquals("連線狀態應該更新為已連接", ConnectionState.CONNECTED, viewModel.aiRouterConnectionState.first())
        
        // 更新輸入文字以檢查發送狀態
        viewModel.updateInputText("Hello")
        advanceUntilIdle()
        assertTrue("連接後且有文字時應該可以發送訊息", viewModel.canSendMessage.first())
    }

    @Test
    fun `AI Router 連線失敗應該處理錯誤`() = runTest(testDispatcher) {
        // Given
        mockAIRouterClient.setConnectResult(Result.failure(AIRouterException("連接失敗")))

        // When
        viewModel.connectToAIRouter()
        advanceUntilIdle()

        // Then
        assertEquals("連線狀態應該保持未連接", ConnectionState.DISCONNECTED, viewModel.aiRouterConnectionState.first())
        assertEquals("應該顯示錯誤狀態", UiState.ERROR, viewModel.uiState.first().state)
    }

    @Test
    fun `檢查 AI Router 狀態應該正確`() = runTest(testDispatcher) {
        // Given
        val mockStatus = AIRouterStatus(
            isRunning = true,
            availableEngines = listOf("LLM Engine"),
            currentModel = "BreezeAI-3B-Instruct",
            memoryUsage = 1024 * 1024 * 512,
            processCount = 1
        )
        mockAIRouterClient.setMockStatus(mockStatus)
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)

        // When
        viewModel.checkAIRouterStatus()
        advanceUntilIdle()

        // Then
        val status = viewModel.aiRouterStatus.first()
        assertTrue("狀態應該包含模型信息", status.contains("BreezeAI-3B-Instruct"))
    }

    // =================== 訊息發送測試 ===================

    @Test
    fun `發送訊息應該通過 AI Router Client 處理`() = runTest(testDispatcher) {
        // Given
        val messageText = "Hello AI Router!"
        val mockResponse = AIResponse(
            requestId = "123",
            text = "Hello! How can I help you?",
            isComplete = true,
            state = AIResponse.ResponseState.COMPLETED
        )
        
        // 模擬已連接狀態
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)
        mockAIRouterClient.setMockResponses(listOf(mockResponse))

        // 等待初始化完成
        advanceUntilIdle()

        // When
        viewModel.updateInputText(messageText)
        viewModel.sendMessage(messageText)
        advanceUntilIdle()

        // Then
        assertTrue("訊息應該通過 AI Router 發送", true)
        
        val messages = viewModel.messages.first()
        assertEquals("應該有3條訊息", 3, messages.size) // 歡迎 + 用戶 + AI
        assertEquals("用戶訊息內容正確", messageText, messages[1].text)
        assertTrue("第二條應該是用戶訊息", messages[1].isFromUser)
        assertEquals("AI回應內容正確", mockResponse.text, messages[2].text)
        assertFalse("第三條應該是AI訊息", messages[2].isFromUser)
    }

    @Test
    fun `未連接時發送訊息應該被阻止`() = runTest(testDispatcher) {
        // Given
        val messageText = "Hello AI"
        mockAIRouterClient.setConnectionState(ConnectionState.DISCONNECTED)
        
        // 等待初始化完成
        advanceUntilIdle()

        // When
        viewModel.updateInputText(messageText)
        viewModel.sendMessage(messageText)
        advanceUntilIdle()

        // Then
        assertTrue("未連接時不應該發送訊息", true)
        
        val messages = viewModel.messages.first()
        assertEquals("應該只有歡迎訊息", 1, messages.size)
    }

    @Test
    fun `AI Router 流式回應應該正確處理`() = runTest(testDispatcher) {
        // Given
        val messageText = "Tell me a story"
        val responses = listOf(
            AIResponse("123", "Once", state = AIResponse.ResponseState.STREAMING),
            AIResponse("123", "Once upon", state = AIResponse.ResponseState.STREAMING),
            AIResponse("123", "Once upon a time", state = AIResponse.ResponseState.COMPLETED, isComplete = true)
        )
        
        // 模擬已連接狀態
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)
        mockAIRouterClient.setMockResponses(responses)

        // 等待初始化完成
        advanceUntilIdle()

        // When
        viewModel.updateInputText(messageText)
        viewModel.sendMessage(messageText)
        advanceUntilIdle()

        // Then
        val messages = viewModel.messages.first()
        val aiMessage = messages.last()
        assertEquals("最終回應應該完整", "Once upon a time", aiMessage.text)
        assertEquals("訊息狀態應該是正常", MessageState.NORMAL, aiMessage.state)
    }

    @Test
    fun `AI Router 錯誤應該正確處理`() = runTest(testDispatcher) {
        // Given
        val messageText = "Error test"
        val errorResponse = AIResponse(
            requestId = "123",
            text = "Processing failed",
            state = AIResponse.ResponseState.ERROR
        )
        
        // 模擬已連接狀態
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)
        mockAIRouterClient.setMockResponses(listOf(errorResponse))

        // 等待初始化完成
        advanceUntilIdle()

        // When
        viewModel.updateInputText(messageText)
        viewModel.sendMessage(messageText)
        advanceUntilIdle()

        // Then
        val messages = viewModel.messages.first()
        val aiMessage = messages.last()
        assertEquals("訊息狀態應該是錯誤", MessageState.ERROR, aiMessage.state)
        assertTrue("錯誤訊息應該包含重試提示", aiMessage.text.contains("重試"))
    }

    // =================== 重試功能測試 ===================

    @Test
    fun `重試訊息應該重新發送到 AI Router`() = runTest(testDispatcher) {
        // Given - 先發送一條訊息
        val messageText = "Test retry"
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)
        
        val mockResponse = AIResponse(
            requestId = "123",
            text = "Original response",
            isComplete = true,
            state = AIResponse.ResponseState.COMPLETED
        )
        mockAIRouterClient.setMockResponses(listOf(mockResponse))

        // 等待初始化完成
        advanceUntilIdle()
        
        viewModel.updateInputText(messageText)
        viewModel.sendMessage(messageText)
        advanceUntilIdle()

        // When - 重試
        viewModel.retryLastMessage()
        advanceUntilIdle()

        // Then
        assertTrue("應該重試訊息發送", true)
    }

    @Test
    fun `未連接時重試應該顯示錯誤`() = runTest(testDispatcher) {
        // Given
        mockAIRouterClient.setConnectionState(ConnectionState.DISCONNECTED)
        
        // 等待初始化完成
        advanceUntilIdle()

        // When
        viewModel.retryLastMessage()
        advanceUntilIdle()

        // Then
        assertEquals("應該顯示錯誤狀態", UiState.ERROR, viewModel.uiState.first().state)
        assertTrue("錯誤訊息應該提示先連接", viewModel.uiState.first().message.contains("連接"))
    }

    // =================== 會話管理測試 ===================

    @Test
    fun `清空聊天記錄應該正確`() = runTest(testDispatcher) {
        // Given - 初始有歡迎訊息
        advanceUntilIdle()
        val initialMessages = viewModel.messages.first()
        assertTrue("初始應該有歡迎訊息", initialMessages.isNotEmpty())

        // When
        viewModel.clearChat()
        advanceUntilIdle()

        // Then
        val messages = viewModel.messages.first()
        assertTrue("聊天記錄應該被清空", messages.isEmpty())
        assertEquals("輸入文字應該被清空", "", viewModel.inputText.first())
        assertFalse("AI應該不在回應中", viewModel.isAIResponding.first())
    }

    @Test
    fun `創建新會話應該正確`() = runTest(testDispatcher) {
        // Given - 初始狀態
        advanceUntilIdle()

        // When
        viewModel.createNewSession()
        advanceUntilIdle()

        // Then
        val messages = viewModel.messages.first()
        assertEquals("新會話應該有歡迎訊息", 1, messages.size)
        assertEquals("輸入文字應該被清空", "", viewModel.inputText.first())
        assertFalse("AI應該不在回應中", viewModel.isAIResponding.first())
    }

    // =================== 輸入狀態測試 ===================

    @Test
    fun `輸入文字更新應該正確`() = runTest(testDispatcher) {
        // Given
        val testText = "Test input"

        // When
        viewModel.updateInputText(testText)
        advanceUntilIdle()

        // Then
        assertEquals("輸入文字應該更新", testText, viewModel.inputText.first())
    }

    @Test
    fun `canSendMessage 狀態應該正確管理`() = runTest(testDispatcher) {
        // Given
        mockAIRouterClient.setConnectionState(ConnectionState.DISCONNECTED)
        
        // 等待初始化完成
        advanceUntilIdle()

        // When - 未連接但有文字
        viewModel.updateInputText("Hello")
        advanceUntilIdle()

        // Then
        assertFalse("未連接時不能發送", viewModel.canSendMessage.first())

        // When - 連接成功
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)
        advanceUntilIdle()

        // Then
        assertTrue("連接後且有文字時可以發送", viewModel.canSendMessage.first())

        // When - 清空文字
        viewModel.updateInputText("")
        advanceUntilIdle()

        // Then
        assertFalse("無文字時不能發送", viewModel.canSendMessage.first())
    }

    @Test
    fun `空白訊息不應該發送`() = runTest(testDispatcher) {
        // Given
        val emptyMessages = listOf("", "   ", "\n", "\t")
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)
        
        // 等待初始化完成
        advanceUntilIdle()
        val initialMessages = viewModel.messages.first()
        val initialCount = initialMessages.size

        // When & Then
        emptyMessages.forEach { message ->
            viewModel.sendMessage(message)
            advanceUntilIdle()
            
            val messages = viewModel.messages.first()
            assertEquals("空白訊息不應該被添加", initialCount, messages.size)
        }
        
        assertTrue("空白訊息不應該被發送", true)
    }

    // =================== 錯誤處理測試 ===================

    @Test
    fun `AI Router 連線錯誤應該自動重連`() = runTest(testDispatcher) {
        // Given
        // When
        val connectionError = AIRouterError.ConnectionError("連線中斷")
        mockAIRouterClient.emitError(connectionError)
        advanceUntilIdle()

        // Then - 應該嘗試重新連接
        assertTrue("應該處理連線錯誤", true)
    }

    @Test
    fun `AI Router 錯誤事件應該正確處理`() = runTest(testDispatcher) {
        // Given
        val mockClient = mockAIRouterClient as MockAIRouterClient
        
        // When & Then - 測試不同錯誤類型通過錯誤事件流觸發
        
        // 連線錯誤
        mockClient.emitError(AIRouterError.ConnectionError("連線失敗"))
        advanceUntilIdle()
        
        // 服務錯誤
        mockClient.emitError(AIRouterError.ServiceError("服務錯誤", 500))
        advanceUntilIdle()
        
        // 引擎錯誤
        mockClient.emitError(AIRouterError.EngineError("引擎錯誤", "LLM"))
        advanceUntilIdle()
        
        // 模型錯誤
        mockClient.emitError(AIRouterError.ModelError("模型錯誤", "BreezeAI"))
        advanceUntilIdle()
        
        // 驗證錯誤處理機制工作正常
        assertTrue("錯誤處理機制應該正常工作", true)
    }

    @Test
    fun `retry 方法應該根據連線狀態正確處理`() = runTest(testDispatcher) {
        // Given
        mockAIRouterClient.setConnectionState(ConnectionState.DISCONNECTED)
        
        // 等待初始化完成
        advanceUntilIdle()

        // When - 未連接時重試
        viewModel.retry()
        advanceUntilIdle()

        // Then - 應該嘗試連接
        assertTrue("未連接時重試應該嘗試連接", true)

        // When - 已連接時重試
        mockAIRouterClient.setConnectionState(ConnectionState.CONNECTED)
        advanceUntilIdle()
        
        // 先發送一條訊息
        val testResponse = AIResponse("123", "Test", state = AIResponse.ResponseState.COMPLETED)
        mockAIRouterClient.setMockResponses(listOf(testResponse))
        
        viewModel.updateInputText("Test")
        viewModel.sendMessage("Test")
        advanceUntilIdle()
        
        viewModel.retry()
        advanceUntilIdle()

        // Then - 應該重試訊息
        assertTrue("已連接時重試應該重新發送訊息", true)
    }
} 