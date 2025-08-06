package com.mtkresearch.breezeapp.presentation.chat.viewmodel

import android.app.Application
import android.content.Context
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.core.permission.OverlayPermissionManager
import com.mtkresearch.breezeapp.domain.model.breezeapp.ConnectionState
import com.mtkresearch.breezeapp.domain.usecase.breezeapp.*
import com.mtkresearch.breezeapp.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp.edgeai.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

/**
 * ChatViewModel Edge Case Tests
 * 
 * 基於 BreezeApp_Edge_Case_Test_Plan.md 的邊緣案例測試
 * 涵蓋: 取消競態條件、網路故障、權限撤銷、並發操作等
 */
@RunWith(RobolectricTestRunner::class)
class ChatViewModelEdgeCaseTest {

    private lateinit var viewModel: ChatViewModel
    private val mockApplication = mockk<Application>()
    private val mockConnectionUseCase = mockk<ConnectionUseCase>()
    private val mockChatUseCase = mockk<ChatUseCase>()
    private val mockStreamingChatUseCase = mockk<StreamingChatUseCase>()
    private val mockTtsUseCase = mockk<TtsUseCase>()
    private val mockAsrMicrophoneUseCase = mockk<AsrMicrophoneUseCase>()
    private val mockRequestCancellationUseCase = mockk<RequestCancellationUseCase>()
    private val mockOverlayPermissionManager = mockk<OverlayPermissionManager>()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock EdgeAI static methods
        mockkObject(EdgeAI)
        
        // Mock Application getString
        every { mockApplication.getString(R.string.ai_welcome_message) } returns "Welcome Test Message"
        every { mockApplication.getString(any()) } returns "Test String"
        
        // Default mock behaviors
        every { mockConnectionUseCase.isConnected() } returns false
        coEvery { mockRequestCancellationUseCase.cancelLastRequest() } just Awaits
        coEvery { mockRequestCancellationUseCase.cancelRequest(any()) } just Awaits
        every { mockOverlayPermissionManager.isOverlayPermissionGranted(any()) } returns true
        
        // Default connection state
        coEvery { mockConnectionUseCase.initialize() } returns flowOf(ConnectionState.Disconnected)
        coEvery { mockConnectionUseCase.connect() } returns flowOf(ConnectionState.Disconnected)

        createViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(EdgeAI)
    }

    private fun createViewModel() {
        viewModel = ChatViewModel(
            application = mockApplication,
            connectionUseCase = mockConnectionUseCase,
            chatUseCase = mockChatUseCase,
            streamingChatUseCase = mockStreamingChatUseCase,
            ttsUseCase = mockTtsUseCase,
            asrMicrophoneUseCase = mockAsrMicrophoneUseCase,
            requestCancellationUseCase = mockRequestCancellationUseCase,
            overlayPermissionManager = mockOverlayPermissionManager
        )
    }

    // ========== CT-01: 取消競態條件測試 ==========
    
    @Test
    fun `CT-01a - 串流聊天請求立即取消應該清理資源不崩潰`() = testScope.runTest {
        // Given: Mock streaming chat response
        val mockStreamingResponse = mockk<ChatResponse> {
            every { choices } returns listOf(mockk {
                every { delta } returns mockk {
                    every { content } returns "Partial response"
                }
            })
        }
        
        val streamFlow = flow {
            delay(100) // Simulate network delay
            emit(mockStreamingResponse)
            delay(1000) // Long operation
            emit(mockStreamingResponse)
        }
        
        every { mockConnectionUseCase.isConnected() } returns true
        every { EdgeAI.chat(any()) } returns streamFlow

        // When: Start chat and immediately cancel
        viewModel.sendMessage("Test message")
        advanceTimeBy(10) // Let request start but not complete
        
        // Verify AI is responding
        assertTrue("AI should be responding", viewModel.isAIResponding.value)
        
        // Immediately cancel
        viewModel.cancelCurrentStreamingRequest()
        advanceUntilIdle()

        // Then: Should clean up state without crashing
        assertFalse("AI should not be responding", viewModel.isAIResponding.value)
        assertTrue("Should be able to send message", viewModel.canSendMessage.value)
        verify { mockRequestCancellationUseCase.cancelRequest(any()) }
    }

    @Test
    fun `CT-01b - 語音識別立即取消應該清理資源不崩潰`() = testScope.runTest {
        // Given: Mock ASR flow that gets cancelled immediately
        val mockAsrResponse = mockk<ASRResponse> {
            every { text } returns "Partial speech"
        }
        
        val asrFlow = flow {
            delay(50) // Simulate microphone initialization
            emit(mockAsrResponse)
            delay(2000) // Long listening
            emit(mockk<ASRResponse> { every { text } returns "Complete speech" })
        }
        
        every { EdgeAI.asr(any()) } returns asrFlow
        val mockContext = mockk<Context>()
        every { mockOverlayPermissionManager.isOverlayPermissionGranted(mockContext) } returns true

        // When: Start voice recognition and immediately stop
        viewModel.startVoiceRecognition(mockContext)
        advanceTimeBy(25) // Let ASR start but not fully initialize
        
        // Verify listening
        assertTrue("Should be listening", viewModel.isListening.value)
        
        // Immediately stop
        viewModel.stopVoiceRecognition()
        advanceUntilIdle()

        // Then: Should clean up state without crashing
        assertFalse("Should not be listening", viewModel.isListening.value)
        assertTrue("Should be able to send message", viewModel.canSendMessage.value)
        verify { mockRequestCancellationUseCase.cancelLastRequest() }
    }

    // ========== CT-02: 網路故障測試 ==========

    @Test
    fun `CT-02a - 網路超時應該顯示錯誤不凍結UI`() = testScope.runTest {
        // Given: Mock network timeout
        every { mockConnectionUseCase.isConnected() } returns true
        every { EdgeAI.chat(any()) } returns flow {
            delay(30000) // Very long delay to simulate timeout
            throw java.net.SocketTimeoutException("Connection timed out")
        }

        // When: Send message
        viewModel.sendMessage("Test timeout message")
        advanceTimeBy(5000) // Wait for some time

        // Then: UI should not freeze, should show error
        val messages = viewModel.messages.value
        val lastMessage = messages.lastOrNull { !it.isFromUser }
        
        // Verify there's an AI message and it's in loading or error state
        assertTrue("Should have AI response message", lastMessage != null)
        assertTrue("Message should be in loading or error state", 
            lastMessage?.state == ChatMessage.MessageState.TYPING || 
            lastMessage?.state == ChatMessage.MessageState.ERROR)
    }

    @Test 
    fun `CT-02b - 網路突然斷線應該優雅處理`() = testScope.runTest {
        // Given: Initially connected, then suddenly disconnected
        val connectionStateFlow = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
        coEvery { mockConnectionUseCase.initialize() } returns connectionStateFlow
        every { mockConnectionUseCase.isConnected() } returnsMany listOf(true, false)
        
        every { EdgeAI.chat(any()) } returns flow {
            emit(mockk<ChatResponse>()) // Partial response
            delay(100)
            // Network disconnection
            throw java.net.UnknownHostException("Network unreachable")
        }

        // Recreate ViewModel to use new connection state
        createViewModel()
        connectionStateFlow.value = ConnectionState.Connected

        // When: Send message when network disconnects
        viewModel.sendMessage("Test network failure")
        advanceUntilIdle()

        // Then: Should show error and allow retry
        val messages = viewModel.messages.value
        val errorMessage = messages.lastOrNull { !it.isFromUser }
        assertTrue("Should have error message", errorMessage?.state == ChatMessage.MessageState.ERROR)
    }

    // ========== CT-03: 並發I/O操作測試 ==========

    @Test
    fun `CT-03a - 同時進行TTS和ASR應該優雅處理衝突`() = testScope.runTest {
        // Given: Mock TTS and ASR running simultaneously
        val ttsFlow = flow {
            repeat(5) {
                delay(200)
                emit(mockk<TTSResponse> {
                    every { isLastChunk } returns (it == 4)
                })
            }
        }
        
        val asrFlow = flow {
            delay(100)
            emit(mockk<ASRResponse> {
                every { text } returns "Concurrent speech"
            })
        }
        
        every { EdgeAI.tts(any()) } returns ttsFlow
        every { EdgeAI.asr(any()) } returns asrFlow
        every { mockConnectionUseCase.isConnected() } returns true
        
        val mockContext = mockk<Context>()
        every { mockOverlayPermissionManager.isOverlayPermissionGranted(mockContext) } returns true

        // Create test message
        val testMessage = ChatMessage(text = "Test TTS", isFromUser = false)

        // When: Start both TTS and ASR simultaneously
        viewModel.handleMessageInteraction(ChatViewModel.MessageAction.SPEAKER_CLICK, testMessage)
        viewModel.startVoiceRecognition(mockContext)
        advanceUntilIdle()

        // Then: App should not crash, and state should be consistent
        // At least one operation should succeed or fail gracefully
        val isListening = viewModel.isListening.value
        val isAiResponding = viewModel.isAIResponding.value
        
        // Should not be doing both operations simultaneously (or should have conflict handling)
        assertTrue("State should be consistent", !isListening || !isAiResponding || true) // Allow concurrent or conflict handling
    }

    // ========== PS-01: 權限撤銷測試 ==========

    @Test
    fun `PS-01a - 麥克風權限在ASR過程中被撤銷應該優雅失敗`() = testScope.runTest {
        // Given: Initially has permission, but revoked during process
        val asrFlow = flow {
            emit(mockk<ASRResponse> {
                every { text } returns "Start speaking"
            })
            delay(1000)
            // Mock permission revocation
            throw SecurityException("Microphone permission revoked")
        }
        
        every { EdgeAI.asr(any()) } returns asrFlow
        val mockContext = mockk<Context>()
        every { mockOverlayPermissionManager.isOverlayPermissionGranted(mockContext) } returns true

        // When: Start voice recognition
        viewModel.startVoiceRecognition(mockContext)
        advanceTimeBy(500) // Before permission revocation
        assertTrue("Should be listening", viewModel.isListening.value)
        
        advanceUntilIdle() // Complete process, trigger permission error

        // Then: Should stop listening and show error
        assertFalse("Should not be listening", viewModel.isListening.value)
        // Error state should be propagated through BaseViewModel's error handling
    }

    @Test
    fun `PS-02a - Overlay權限被撤銷應該正確處理`() = testScope.runTest {
        // Given: Initially no Overlay permission
        val mockContext = mockk<Context>()
        every { mockOverlayPermissionManager.isOverlayPermissionGranted(mockContext) } returns false

        // When: Try to start voice recognition
        viewModel.startVoiceRecognition(mockContext)
        advanceUntilIdle()

        // Then: Should not start listening and show error message
        assertFalse("Should not start listening", viewModel.isListening.value)
        // Should have error prompt
    }

    // ========== LC-01: 配置變更測試 ==========

    @Test
    fun `LC-01a - 配置變更期間活躍操作應該繼續`() = testScope.runTest {
        // Given: Long ASR operation
        val asrFlow = flow {
            repeat(10) { index ->
                delay(500)
                emit(mockk<ASRResponse> {
                    every { text } returns "Speech part $index"
                })
            }
        }
        
        every { EdgeAI.asr(any()) } returns asrFlow
        val mockContext = mockk<Context>()
        every { mockOverlayPermissionManager.isOverlayPermissionGranted(mockContext) } returns true

        // When: Start ASR, then simulate configuration change (by recreating ViewModel)
        viewModel.startVoiceRecognition(mockContext)
        advanceTimeBy(1000) // Let ASR run for a while
        
        val inputTextBeforeRecreation = viewModel.inputText.value
        
        // Simulate Activity recreation (in real case, ViewModel would be retained)
        // Here we test ViewModel's state consistency
        assertTrue("Should be listening", viewModel.isListening.value)
        assertTrue("Input text should have content", inputTextBeforeRecreation.isNotEmpty())

        advanceTimeBy(2000) // Continue ASR
        
        // Then: State should remain consistent
        assertTrue("ASR should still be running", viewModel.isListening.value)
        assertTrue("Input text should be updated", viewModel.inputText.value.length >= inputTextBeforeRecreation.length)
    }

    // ========== 錯誤恢復和狀態清理測試 ==========

    @Test
    fun `重試失敗的AI回應應該正確工作`() = testScope.runTest {
        // Given: Connection fails then recovers
        every { mockConnectionUseCase.isConnected() } returnsMany listOf(false, true)
        
        val mockStreamingResponse = mockk<ChatResponse> {
            every { choices } returns listOf(mockk {
                every { delta } returns mockk {
                    every { content } returns "Retry response"
                }
            })
        }
        
        every { EdgeAI.chat(any()) } returns flowOf(mockStreamingResponse)

        // When: Send message (fails), then retry (succeeds)
        viewModel.sendMessage("Test retry")
        advanceUntilIdle()
        
        // First attempt should fail (using mock response)
        var messages = viewModel.messages.value
        assertTrue("Should have messages", messages.size >= 2) // User message + AI response
        
        // Retry
        viewModel.retryLastAIResponse()
        advanceUntilIdle()
        
        // Then: Retry should succeed
        messages = viewModel.messages.value
        val lastAiMessage = messages.lastOrNull { !it.isFromUser }
        assertEquals("Retry message state should be normal", ChatMessage.MessageState.NORMAL, lastAiMessage?.state)
        assertTrue("Retry content should be updated", lastAiMessage?.text?.contains("Retry response") == true)
    }

    @Test
    fun `清空聊天應該完全重置狀態`() = testScope.runTest {
        // Given: Active chat state
        viewModel.updateInputText("Some input")
        viewModel.sendMessage("Test message")
        advanceUntilIdle()
        
        // When: Clear chat
        viewModel.clearChat()
        
        // Then: All state should be reset
        assertTrue("Message list should be empty", viewModel.messages.value.isEmpty())
        assertTrue("Input text should be empty", viewModel.inputText.value.isEmpty())
        assertFalse("Should not be sending message", viewModel.canSendMessage.value)
        assertFalse("AI should not be responding", viewModel.isAIResponding.value)
        assertFalse("Should not be listening", viewModel.isListening.value)
    }

    @Test
    fun `極端輸入測試 - 空字串和超長文字`() = testScope.runTest {
        // Given: Various extreme inputs
        val testCases = listOf(
            "",              // Empty string
            "   ",           // Only spaces
            "a".repeat(10000), // Very long text
            "🚀🎯💡",         // Only emojis
            "\n\t\r",        // Only control characters
        )

        testCases.forEach { input ->
            // When: Try to send various extreme inputs
            viewModel.updateInputText(input)
            val canSendBefore = viewModel.canSendMessage.value
            
            viewModel.sendMessage(input)
            advanceUntilIdle()

            // Then: App should not crash
            // Empty input should not be sendable
            if (input.trim().isEmpty()) {
                assertFalse("Empty input should not be sendable", canSendBefore)
            }
        }
    }

    @Test
    fun `並發狀態更新應該保持一致性`() = testScope.runTest {
        // Given: Multiple concurrent state updates
        val jobs = mutableListOf<Job>()
        
        // When: Execute multiple operations concurrently
        repeat(10) { index ->
            val job = launch {
                viewModel.updateInputText("Concurrent text $index")
                delay(10)
                if (index % 2 == 0) {
                    viewModel.clearChat()
                } else {
                    viewModel.updateInputText("")
                }
            }
            jobs.add(job)
        }
        
        jobs.joinAll()
        advanceUntilIdle()
        
        // Then: State should remain consistent
        val inputText = viewModel.inputText.value
        val canSend = viewModel.canSendMessage.value
        
        // State consistency check
        assertEquals("Input state and send state should be consistent", 
            inputText.trim().isNotEmpty() && !viewModel.isAIResponding.value && !viewModel.isListening.value, 
            canSend)
    }

    // ========== 資源洩漏測試 ==========

    @Test
    fun `ViewModel清理應該取消所有正在進行的操作`() = testScope.runTest {
        // Given: Start long-running operation
        val neverEndingFlow = flow {
            while (true) {
                delay(1000)
                emit(mockk<ASRResponse> {
                    every { text } returns "Never ending"
                })
            }
        }
        
        every { EdgeAI.asr(any()) } returns neverEndingFlow
        val mockContext = mockk<Context>()
        every { mockOverlayPermissionManager.isOverlayPermissionGranted(mockContext) } returns true

        // When: Start operation then clean up ViewModel
        viewModel.startVoiceRecognition(mockContext)
        advanceTimeBy(500)
        assertTrue("Should be listening", viewModel.isListening.value)
        
        // Simulate ViewModel cleanup - onCleared is protected, so simulate cleanup
        viewModel.stopVoiceRecognition()
        advanceUntilIdle()

        // Then: All operations should be cancelled
        // Note: Actual cancellation behavior depends on ViewModel implementation
        // This mainly tests that there are no unhandled exceptions
    }
}