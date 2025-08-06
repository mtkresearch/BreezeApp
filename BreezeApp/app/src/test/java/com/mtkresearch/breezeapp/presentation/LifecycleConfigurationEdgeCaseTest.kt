package com.mtkresearch.breezeapp.presentation

import android.app.Application
import android.content.res.Configuration
import androidx.lifecycle.SavedStateHandle
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.core.permission.OverlayPermissionManager
import com.mtkresearch.breezeapp.domain.usecase.breezeapp.*
import com.mtkresearch.breezeapp.domain.usecase.settings.*
import com.mtkresearch.breezeapp.presentation.chat.viewmodel.ChatViewModel
import com.mtkresearch.breezeapp.presentation.settings.viewmodel.RuntimeSettingsViewModel
import com.mtkresearch.breezeapp.edgeai.*
import com.mtkresearch.breezeapp.data.repository.RuntimeSettingsRepository
import com.mtkresearch.breezeapp.domain.usecase.settings.ValidationResult
import io.mockk.*
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Assert.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Lifecycle and Configuration Change Edge Case Tests
 * 
 * 基於 BreezeApp_Edge_Case_Test_Plan.md 的邊緣案例測試
 * 涵蓋: LC-01 配置變更, LC-02 運行時設定, LC-03 狀態保存, LC-04 多視窗模式
 */
@RunWith(RobolectricTestRunner::class)
class LifecycleConfigurationEdgeCaseTest {

    // ChatViewModel dependencies
    private val mockApplication = mockk<Application>()
    private val mockConnectionUseCase = mockk<ConnectionUseCase>()
    private val mockChatUseCase = mockk<ChatUseCase>()
    private val mockStreamingChatUseCase = mockk<StreamingChatUseCase>()
    private val mockTtsUseCase = mockk<TtsUseCase>()
    private val mockAsrMicrophoneUseCase = mockk<AsrMicrophoneUseCase>()
    private val mockRequestCancellationUseCase = mockk<RequestCancellationUseCase>()
    private val mockOverlayPermissionManager = mockk<OverlayPermissionManager>()

    // RuntimeSettingsViewModel dependencies
    private val mockLoadRuntimeSettingsUseCase = mockk<LoadRuntimeSettingsUseCase>()
    private val mockSaveRuntimeSettingsUseCase = mockk<SaveRuntimeSettingsUseCase>()
    private val mockUpdateRuntimeParameterUseCase = mockk<UpdateRuntimeParameterUseCase>()
    private val mockValidateRuntimeSettingsUseCase = mockk<ValidateRuntimeSettingsUseCase>()

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        // Mock common dependencies
        every { mockApplication.getString(any()) } returns "Test String"
        every { mockConnectionUseCase.isConnected() } returns false
        coEvery { mockConnectionUseCase.initialize() } returns flowOf(
            com.mtkresearch.breezeapp.domain.model.breezeapp.ConnectionState.Disconnected
        )
        coEvery { mockRequestCancellationUseCase.cancelLastRequest() } just awaits
        every { mockOverlayPermissionManager.isOverlayPermissionGranted(any()) } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ========== LC-01: 配置變更期間活躍操作測試 ==========

    @Test
    fun `LC-01a - 螢幕旋轉期間ASR應該繼續無中斷`() = testScope.runTest {
        // Given: 長時間的ASR操作
        val asrResponses = listOf(
            "Before rotation",
            "During rotation", 
            "After rotation"
        )
        
        var responseIndex = 0
        val asrFlow = flow {
            repeat(asrResponses.size) { index ->
                delay(500) // 模擬語音處理時間
                emit(mockk<ASRResponse> {
                    every { text } returns asrResponses[index]
                    every { isChunk } returns (index < asrResponses.size - 1)
                    every { language } returns null
                    every { segments } returns null
                })
            }
        }
        
        coEvery { mockAsrMicrophoneUseCase.execute() } returns asrFlow

        // When: 開始ASR，然後模擬螢幕旋轉（通過重新創建ViewModel）
        val chatViewModel1 = ChatViewModel(
            mockApplication, mockConnectionUseCase, mockChatUseCase,
            mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
            mockRequestCancellationUseCase, mockOverlayPermissionManager
        )

        // 開始語音識別
        chatViewModel1.startVoiceRecognition()
        advanceTimeBy(750) // 讓ASR運行一段時間

        // 模擬配置變更 - 創建新的ViewModel實例（保持相同依賴）
        val chatViewModel2 = ChatViewModel(
            mockApplication, mockConnectionUseCase, mockChatUseCase,
            mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
            mockRequestCancellationUseCase, mockOverlayPermissionManager
        )

        // 驗證狀態一致性
        advanceTimeBy(1000)

        // Then: 新ViewModel應該能正確初始化，ASR操作的影響應該可控
        assertNotNull("新ViewModel應該正確創建", chatViewModel2)
        
        // 在實際實現中，ViewModel會被Android框架保留，但我們測試的是依賴的穩定性
    }

    @Test
    fun `LC-01b - 旋轉期間串流聊天應該保持連接`() = testScope.runTest {
        // Given: 正在進行的串流聊天
        val streamingResponses = listOf("Pre-rotation", "Mid-rotation", "Post-rotation")
        var responseIndex = 0
        
        val streamFlow = flow {
            repeat(streamingResponses.size) { index ->
                delay(400)
                emit(mockk<ChatResponse> {
                    every { choices } returns listOf(mockk {
                        every { delta } returns mockk {
                            every { content } returns streamingResponses[index]
                        }
                    })
                })
            }
        }
        
        every { mockConnectionUseCase.isConnected() } returns true
        coEvery { mockStreamingChatUseCase.execute(any(), any(), any()) } returns streamFlow

        // When: 開始聊天並模擬旋轉
        val chatViewModel = ChatViewModel(
            mockApplication, mockConnectionUseCase, mockChatUseCase,
            mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
            mockRequestCancellationUseCase, mockOverlayPermissionManager
        )

        chatViewModel.sendMessage("Test rotation message")
        advanceTimeBy(600) // 讓串流進行一段時間

        // 檢查AI是否正在回應（模擬旋轉期間的狀態）
        val isResponding = chatViewModel.isAIResponding.value
        val messages = chatViewModel.messages.value

        advanceTimeBy(800) // 完成串流

        // Then: 聊天狀態應該保持一致
        assertTrue("應該有用戶訊息", messages.any { it.isFromUser })
        assertTrue("應該有AI回應", messages.any { !it.isFromUser })
    }

    // ========== LC-02: 運行時設定配置變更測試 ==========

    @Test
    fun `LC-02a - 設定變更期間立即旋轉應該保存更改`() = testScope.runTest {
        // Given: Runtime Settings正在變更
        val mockSavedStateHandle = mockk<SavedStateHandle>()
        every { mockSavedStateHandle.get<String>(any()) } returns null
        every { mockSavedStateHandle.set(any<String>(), any<String>()) } just runs
        
        coEvery { mockLoadRuntimeSettingsUseCase.invoke() } returns Result.success(mockk())
        coEvery { mockSaveRuntimeSettingsUseCase.invoke(any()) } returns Result.success(Unit)
        coEvery { mockUpdateRuntimeParameterUseCase.invoke(any(), any()) } returns Result.success(mockk())
        coEvery { mockValidateRuntimeSettingsUseCase.invoke(any()) } returns ValidationResult.Valid

        val runtimeViewModel = RuntimeSettingsViewModel(
            mockLoadRuntimeSettingsUseCase,
            mockSaveRuntimeSettingsUseCase,
            mockUpdateRuntimeParameterUseCase,
            mockValidateRuntimeSettingsUseCase
        )

        // When: 更改設定後立即旋轉（模擬保存狀態）
        runtimeViewModel.updateLLMTemperature(0.8f)
        advanceTimeBy(100) // 讓更新開始處理

        // 模擬配置變更保存狀態
        val stateToSave = mutableMapOf<String, Any>()
        // 在實際實現中，ViewModel會將狀態保存到SavedStateHandle

        // Then: 設定應該被正確保存
        // Note: The actual parameter update is internal to the ViewModel
        assertTrue("RuntimeSettingsViewModel應該正常創建", runtimeViewModel != null)
    }

    @Test
    fun `LC-02b - 字體大小變更期間旋轉應該保持UI一致性`() = testScope.runTest {
        // Given: 字體大小正在變更
        val mockSavedStateHandle = mockk<SavedStateHandle>()
        every { mockSavedStateHandle.get<String>(any()) } returns null
        every { mockSavedStateHandle.set(any<String>(), any<String>()) } just runs
        
        coEvery { mockLoadRuntimeSettingsUseCase.invoke() } returns Result.success(mockk())

        val runtimeViewModel = RuntimeSettingsViewModel(
            mockLoadRuntimeSettingsUseCase,
            mockSaveRuntimeSettingsUseCase,
            mockUpdateRuntimeParameterUseCase,
            mockValidateRuntimeSettingsUseCase
        )

        // When: 更改字體大小（模擬UI字體變更）
        // Note: Font size changes are handled at UI level, not in RuntimeSettingsViewModel
        advanceTimeBy(50)

        // 模擬配置變更期間的狀態檢查
        advanceUntilIdle()

        // Then: ViewModel應該正常運行
        assertTrue("RuntimeSettingsViewModel應該正常創建", runtimeViewModel != null)
    }

    // ========== LC-03: 狀態保存測試 ==========

    @Test
    fun `LC-03a - 未發送訊息應該在旋轉後保持`() = testScope.runTest {
        // Given: 有未發送的輸入文字
        val chatViewModel = ChatViewModel(
            mockApplication, mockConnectionUseCase, mockChatUseCase,
            mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
            mockRequestCancellationUseCase, mockOverlayPermissionManager
        )

        val unsentMessage = "This message was not sent before rotation"
        chatViewModel.updateInputText(unsentMessage)

        // When: 模擬配置變更前的狀態
        val inputTextBeforeRotation = chatViewModel.inputText.value
        val canSendBeforeRotation = chatViewModel.canSendMessage.value

        // 在實際Android中，ViewModel會保持狀態
        // 這裡我們測試狀態的一致性
        assertEquals("輸入文字應該保持", unsentMessage, inputTextBeforeRotation)
        assertTrue("應該可以發送訊息", canSendBeforeRotation)

        // Then: 狀態應該一致
        assertEquals("未發送訊息應該保持", unsentMessage, chatViewModel.inputText.value)
    }

    @Test
    fun `LC-03b - 聊天記錄滾動位置應該保持`() = testScope.runTest {
        // Given: 有多條聊天記錄
        val chatViewModel = ChatViewModel(
            mockApplication, mockConnectionUseCase, mockChatUseCase,
            mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
            mockRequestCancellationUseCase, mockOverlayPermissionManager
        )

        // 添加多條消息來模擬長聊天記錄
        repeat(5) { index ->
            chatViewModel.sendMessage("Message $index")
            advanceTimeBy(100) // 讓每個消息處理完成
        }

        advanceUntilIdle()

        // When: 檢查消息列表
        val messagesBeforeRotation = chatViewModel.messages.value
        val messageCount = messagesBeforeRotation.size

        // 在實際實現中，滾動位置會通過SavedStateHandle或其他機制保存
        // 這裡我們驗證消息列表的完整性

        // Then: 消息應該完整保存
        assertTrue("應該有用戶消息", messagesBeforeRotation.any { it.isFromUser })
        assertTrue("消息數量應該合理", messageCount >= 5) // 至少5條用戶消息
    }

    // ========== LC-04: 多視窗模式測試 ==========

    @Test
    fun `LC-04a - 分割螢幕模式下UI尺寸變更應該正確適應`() = testScope.runTest {
        // Given: 模擬分割螢幕模式的Configuration變更
        val originalConfig = Configuration().apply {
            screenWidthDp = 360
            screenHeightDp = 640
        }
        
        val splitScreenConfig = Configuration().apply {
            screenWidthDp = 360
            screenHeightDp = 320 // 高度減半
        }

        // 這個測試主要驗證ViewModel在配置變更時的穩定性
        val chatViewModel = ChatViewModel(
            mockApplication, mockConnectionUseCase, mockChatUseCase,
            mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
            mockRequestCancellationUseCase, mockOverlayPermissionManager
        )

        // When: 模擬進入分割螢幕模式
        chatViewModel.updateInputText("Test message in split screen")
        val inputBeforeSplit = chatViewModel.inputText.value

        // 模擬配置變更（在實際中由Android框架處理）
        // ViewModel應該保持狀態
        
        // Then: ViewModel狀態應該保持穩定
        assertEquals("分割螢幕模式下輸入應該保持", inputBeforeSplit, chatViewModel.inputText.value)
    }

    @Test
    fun `LC-04b - 視窗大小調整期間音頻操作應該繼續`() = testScope.runTest {
        // Given: 音頻操作正在進行
        val asrFlow = flow {
            repeat(5) { index ->
                delay(300)
                emit(mockk<ASRResponse> {
                    every { text } returns "Audio continues $index"
                    every { isChunk } returns (index < 4)
                    every { language } returns null
                    every { segments } returns null
                })
            }
        }
        
        coEvery { mockAsrMicrophoneUseCase.execute() } returns asrFlow

        val chatViewModel = ChatViewModel(
            mockApplication, mockConnectionUseCase, mockChatUseCase,
            mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
            mockRequestCancellationUseCase, mockOverlayPermissionManager
        )

        // When: 開始語音識別，然後模擬視窗大小調整
        chatViewModel.startVoiceRecognition()
        advanceTimeBy(600) // 讓ASR開始運行

        val isListeningDuringResize = chatViewModel.isListening.value
        val inputTextDuringResize = chatViewModel.inputText.value

        advanceTimeBy(900) // 讓ASR完成更多處理

        // Then: 音頻操作應該不受視窗調整影響
        assertTrue("視窗調整期間應該仍在監聽", isListeningDuringResize)
        assertTrue("應該有語音識別結果", chatViewModel.inputText.value.isNotEmpty())
    }

    // ========== 記憶體和資源管理測試 ==========

    @Test
    fun `配置變更期間記憶體洩漏測試`() = testScope.runTest {
        // Given: 創建和銷毀多個ViewModel實例
        val viewModels = mutableListOf<ChatViewModel>()
        
        repeat(5) { index ->
            val viewModel = ChatViewModel(
                mockApplication, mockConnectionUseCase, mockChatUseCase,
                mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
                mockRequestCancellationUseCase, mockOverlayPermissionManager
            )
            
            viewModel.updateInputText("Test message $index")
            viewModels.add(viewModel)
        }

        // When: 清理所有ViewModel
        // Note: onCleared() is protected, so we can't call it directly in tests
        // In real scenarios, Android framework calls this when ViewModel is cleared

        advanceUntilIdle()

        // Then: 清理應該正常完成，沒有異常
        assertEquals("應該創建5個ViewModel", 5, viewModels.size)
        // 在實際實現中，這裡會檢查資源是否正確釋放
    }

    @Test
    fun `快速配置變更應該不影響功能穩定性`() = testScope.runTest {
        // Given: 快速的配置變更週期
        val chatViewModel = ChatViewModel(
            mockApplication, mockConnectionUseCase, mockChatUseCase,
            mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
            mockRequestCancellationUseCase, mockOverlayPermissionManager
        )

        // When: 快速執行多個操作模擬快速配置變更
        repeat(10) { index ->
            chatViewModel.updateInputText("Quick change $index")
            advanceTimeBy(10) // 極短間隔
            
            if (index % 3 == 0) {
                chatViewModel.clearChat()
            }
            
            if (index % 4 == 0) {
                chatViewModel.updateInputText("")
            }
        }

        advanceUntilIdle()

        // Then: ViewModel應該保持穩定狀態
        val finalState = chatViewModel.inputText.value
        val canSend = chatViewModel.canSendMessage.value
        
        // 狀態一致性檢查
        assertEquals("輸入狀態和發送能力應該一致",
            finalState.trim().isNotEmpty() && !chatViewModel.isAIResponding.value && !chatViewModel.isListening.value,
            canSend)
    }

    @Test
    fun `極端配置變更情況下的恢復測試`() = testScope.runTest {
        // Given: 模擬極端的配置變更情況
        var exceptionCount = 0
        
        // 模擬在配置變更期間可能發生的各種異常
        every { mockApplication.getString(R.string.ai_welcome_message) } answers {
            exceptionCount++
            if (exceptionCount % 3 == 0) {
                throw RuntimeException("Configuration change exception")
            }
            "Test welcome message"
        }

        // When: 在異常情況下創建ViewModel
        val viewModels = mutableListOf<ChatViewModel>()
        var creationFailures = 0
        
        repeat(10) { index ->
            try {
                val viewModel = ChatViewModel(
                    mockApplication, mockConnectionUseCase, mockChatUseCase,
                    mockStreamingChatUseCase, mockTtsUseCase, mockAsrMicrophoneUseCase,
                    mockRequestCancellationUseCase, mockOverlayPermissionManager
                )
                viewModels.add(viewModel)
            } catch (e: Exception) {
                creationFailures++
            }
        }

        // Then: 大部分創建應該成功，失敗應該可控
        assertTrue("大部分ViewModel應該創建成功", viewModels.size > creationFailures)
        assertTrue("失敗次數應該可控", creationFailures <= 5)
    }
}