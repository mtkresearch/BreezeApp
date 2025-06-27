package com.mtkresearch.breezeapp_UI.presentation.chat.viewmodel

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import com.mtkresearch.breezeapp_UI.presentation.common.base.BaseViewModel
import com.mtkresearch.breezeapp_UI.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp_UI.presentation.chat.model.ChatSession
import com.mtkresearch.breezeapp_router.domain.model.AIResponse
import com.mtkresearch.breezeapp_router.domain.AIRouterError
import com.mtkresearch.breezeapp_router.domain.ConnectionState
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.SendMessageUseCase
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.ConnectAIRouterUseCase
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.LoadChatHistoryUseCase
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.ManageChatSessionUseCase
import com.mtkresearch.breezeapp_UI.domain.usecase.chat.SaveMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * 聊天ViewModel (v2.1 - Clean Architecture + 本地歷史紀錄)
 *
 * 功能重構:
 * ✅ 狀態管理: 改為基於 `sessionId` 驅動，非同步載入歷史訊息。
 * ✅ 本地儲存: 整合 `LoadChatHistoryUseCase` 和 `SaveMessageUseCase`。
 * ✅ 架構: 移除職責不清的 `ManageChatSessionUseCase`，邏輯更清晰。
 *
 * 核心改變:
 * - ViewModel 現在是無狀態的，它響應 `sessionId` 的變化來載入對應的聊天狀態。
 * - 新的聊天會話在發送第一則訊息時才被建立。
 * - 所有資料庫操作都通過專門的 Use Case 進行。
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val connectAIRouterUseCase: ConnectAIRouterUseCase,
    private val loadChatHistoryUseCase: LoadChatHistoryUseCase,
    private val saveMessageUseCase: SaveMessageUseCase,
    private val manageChatSessionUseCase: ManageChatSessionUseCase
) : BaseViewModel() {

    // ========================= 聊天狀態 (重構版) =========================

    // 當前聊天會話ID
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    // 聊天訊息列表
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // 輸入框文字
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    // 是否可以發送訊息
    private val _canSendMessage = MutableStateFlow(false)
    val canSendMessage: StateFlow<Boolean> = _canSendMessage.asStateFlow()

    // AI是否正在回應
    private val _isAIResponding = MutableStateFlow(false)
    val isAIResponding: StateFlow<Boolean> = _isAIResponding.asStateFlow()

    // 歷史會話列表
    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()

    // =================== AI Router 連線狀態 (新增) ===================
    
    // AI Router 連線狀態
    private val _aiRouterConnectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val aiRouterConnectionState: StateFlow<ConnectionState> = _aiRouterConnectionState.asStateFlow()

    // AI Router 狀態訊息
    private val _aiRouterStatus = MutableStateFlow("未連接到 AI 引擎服務")
    val aiRouterStatus: StateFlow<String> = _aiRouterStatus.asStateFlow()

    // 是否正在連接 AI Router
    private val _isConnectingToAIRouter = MutableStateFlow(false)
    val isConnectingToAIRouter: StateFlow<Boolean> = _isConnectingToAIRouter.asStateFlow()

    init {
        // 初始化連線狀態監控
        observeAIRouterConnection()
        // 初始化時嘗試連接 AI Router
        connectToAIRouter()
        // 監聽 sessionId 的變化，並載入對應的聊天歷史
        observeSessionIdChanges()
        // 載入歡迎訊息 (如果沒有當前會話)
        loadWelcomeMessage()
        // 確保初始狀態正確
        updateCanSendMessageState()
    }

    /**
     * 從UI層調用，用來設定當前要互動的聊天會話
     * @param sessionId 傳入會話ID以載入現有對話，或傳入null以開始新對話。
     */
    fun loadSession(sessionId: String?) {
        val finalSessionId = sessionId ?: manageChatSessionUseCase.startNewSession()
        _currentSessionId.value = finalSessionId
    }

    private fun observeSessionIdChanges() {
        _currentSessionId
            .flatMapLatest { sessionId ->
                loadChatHistoryUseCase(sessionId)
            }
            .onEach { historyMessages ->
                _messages.value = historyMessages
            }
            .catch { e ->
                setError("載入聊天歷史失敗: ${e.message}")
            }
            .launchIn(viewModelScope)
    }

    // =================== AI Router 連線管理 (新增) ===================

    /**
     * 連接到 AI Router Service
     */
    fun connectToAIRouter() {
        if (_isConnectingToAIRouter.value || _aiRouterConnectionState.value == ConnectionState.CONNECTED) {
            return
        }

        launchSafely(showLoading = false) {
            _isConnectingToAIRouter.value = true
            _aiRouterStatus.value = "正在連接 AI 引擎服務..."
            
            try {
                val result = connectAIRouterUseCase.connect()
                if (result.isSuccess) {
                    _aiRouterStatus.value = "AI 引擎服務已連接"
                    setSuccess("AI 引擎服務連接成功")
                } else {
                    _aiRouterStatus.value = "AI 引擎服務連接失敗"
                    setError("無法連接到 AI 引擎服務: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                _aiRouterStatus.value = "AI 引擎服務連接錯誤"
                setError("AI 引擎服務連接錯誤: ${e.message}")
            } finally {
                _isConnectingToAIRouter.value = false
                updateCanSendMessageState()
            }
        }
    }

    /**
     * 斷開 AI Router 連接
     */
    fun disconnectFromAIRouter() {
        launchSafely(showLoading = false) {
            try {
                val result = connectAIRouterUseCase.disconnect()
                if (result.isSuccess) {
                    _aiRouterStatus.value = "已斷開 AI 引擎服務連接"
                } else {
                    setError("斷開連接失敗: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                setError("斷開連接錯誤: ${e.message}")
            }
        }
    }

    /**
     * 檢查 AI Router 狀態
     */
    fun checkAIRouterStatus() {
        launchSafely(showLoading = false) {
            try {
                val status = connectAIRouterUseCase.getStatus()
                if (status.isSuccess) {
                    val routerStatus = status.getOrNull()
                    _aiRouterStatus.value = if (routerStatus?.isRunning == true) {
                        "AI 引擎服務運行正常 (模型: ${routerStatus.currentModel ?: "未載入"})"
                    } else {
                        "AI 引擎服務未運行"
                    }
                } else {
                    _aiRouterStatus.value = "無法獲取 AI 引擎狀態"
                }
            } catch (e: Exception) {
                _aiRouterStatus.value = "AI 引擎狀態檢查錯誤"
                setError("狀態檢查錯誤: ${e.message}")
            }
        }
    }

    /**
     * 監控 AI Router 連線狀態
     */
    private fun observeAIRouterConnection() {
        launchSafely(showLoading = false) {
            connectAIRouterUseCase.getConnectionState().collect { state ->
                _aiRouterConnectionState.value = state
                when (state) {
                    ConnectionState.DISCONNECTED -> {
                        _aiRouterStatus.value = "未連接到 AI 引擎服務"
                    }
                    ConnectionState.CONNECTING -> {
                        _aiRouterStatus.value = "正在連接 AI 引擎服務..."
                    }
                    ConnectionState.CONNECTED -> {
                        _aiRouterStatus.value = "AI 引擎服務已連接"
                    }
                    ConnectionState.ERROR -> {
                        _aiRouterStatus.value = "AI 引擎服務連接錯誤"
                    }
                }
                updateCanSendMessageState()
            }
        }
    }

    // =================== 訊息處理 (重構版) ===================

    /**
     * 更新輸入文字
     */
    fun updateInputText(text: String) {
        _inputText.value = text
        updateCanSendMessageState()
    }

    /**
     * 更新 canSendMessage 狀態
     */
    private fun updateCanSendMessageState() {
        _canSendMessage.value = _inputText.value.trim().isNotEmpty() && 
                                !_isAIResponding.value && 
                                _aiRouterConnectionState.value == ConnectionState.CONNECTED &&
                                !_isConnectingToAIRouter.value
    }

    /**
     * 發送訊息 (通過 Use Case 和 AI Router)
     */
    fun sendMessage(text: String = _inputText.value) {
        val messageText = text.trim()
        
        // 檢查前置條件
        if (messageText.isEmpty() ||
            _isAIResponding.value ||
            _aiRouterConnectionState.value != ConnectionState.CONNECTED) {
            return
        }

        if (!validateInput(messageText.isNotBlank(), "訊息不能為空")) return

        launchSafely(showLoading = false) {
            val sessionId = _currentSessionId.value ?: UUID.randomUUID().toString()
            val isNewSession = _currentSessionId.value == null
            if (isNewSession) {
                _currentSessionId.value = sessionId
            }

            // 創建用戶訊息
            val userMessage = ChatMessage(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                author = MessageAuthor.USER,
                content = messageText,
                timestamp = ZonedDateTime.now()
            )

            // 儲存用戶訊息到資料庫 (透過Use Case)
            // 如果是新會話，Repository會負責建立會話實體
            saveMessageUseCase(userMessage)

            // 清空輸入框
            _inputText.value = ""
            updateCanSendMessageState()
            _isAIResponding.value = true

            // 通過 Use Case 發送訊息到 AI Router
            sendMessageToAIRouter(userMessage)
        }
    }

    /**
     * 通過 Use Case 和 AI Router 發送訊息
     */
    private suspend fun sendMessageToAIRouter(message: ChatMessage) {
        launchSafely(showLoading = false) {
            val history = _messages.value.filter { !it.isLoading }
            
            sendMessageUseCase(message, history)
                .onStart {
                    addOrUpdateLoadingMessage(true)
                }
                .onCompletion {
                    addOrUpdateLoadingMessage(false)
                    _isAIResponding.value = false
                    updateCanSendMessageState()
                }
                .catch { error ->
                    handleSendMessageError(error)
                }
                .collect { response ->
                    handleSendMessageResponse(response)
                }
        }
    }

    /**
     * 處理訊息發送成功的回應
     */
    private suspend fun handleSendMessageResponse(response: AIResponse) {
        if (response.isSuccess) {
            val aiMessage = ChatMessage(
                id = response.id ?: UUID.randomUUID().toString(),
                sessionId = _currentSessionId.value!!, // 此時不可能為null
                author = MessageAuthor.AI,
                content = response.text,
                timestamp = ZonedDateTime.now()
            )
            // 儲存AI訊息
            saveMessageUseCase(aiMessage)
        } else {
            val error = response.error ?: AIRouterError.UnknownError("未知錯誤")
            handleSendMessageError(error)
        }
    }

    /**
     * 處理訊息發送失敗
     */
    private fun handleSendMessageError(error: Throwable) {
        val errorMessage = when (error) {
            is AIRouterError -> error.message
            else -> error.message ?: "未知錯誤"
        }
        setError("訊息發送失敗: $errorMessage")
        // 可以選擇在聊天中顯示一條錯誤訊息
        val systemErrorMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            sessionId = _currentSessionId.value ?: "temp_session",
            author = MessageAuthor.SYSTEM_ERROR,
            content = "錯誤: $errorMessage",
            timestamp = ZonedDateTime.now()
        )
        // 這條錯誤訊息可以選擇性地儲存
        // viewModelScope.launch { saveMessageUseCase(systemErrorMessage) }
        _messages.value = _messages.value + systemErrorMessage
    }

    /**
     * 新增或移除 AI 等待中的訊息
     */
    private fun addOrUpdateLoadingMessage(isLoading: Boolean) {
        val currentMessages = _messages.value.toMutableList()
        val loadingMessage = currentMessages.find { it.isLoading }

        if (isLoading) {
            if (loadingMessage == null) {
                val newLoadingMessage = ChatMessage(
                    id = "loading_message",
                    sessionId = _currentSessionId.value!!,
                    author = MessageAuthor.AI,
                    content = "...",
                    timestamp = ZonedDateTime.now(),
                    isLoading = true
                )
                _messages.value = currentMessages + newLoadingMessage
            }
        } else {
            if (loadingMessage != null) {
                _messages.value = currentMessages.filterNot { it.isLoading }
            }
        }
    }

    // =================== 會話管理 (重構版) ===================

    /**
     * 載入歡迎訊息
     */
    private fun loadWelcomeMessage() {
        if (_messages.value.isEmpty()) {
            _messages.value = listOf(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = _currentSessionId.value ?: "welcome_session",
                    author = MessageAuthor.SYSTEM_INFO,
                    content = "歡迎使用 BreezeApp！請開始您的對話。",
                    timestamp = ZonedDateTime.now()
                )
            )
        }
    }

    /**
     * 刪除當前會話
     */
    fun deleteCurrentSession() {
        // TODO: 實作刪除會話的邏輯
    }

    /**
     * 開始新會話
     */
    fun startNewSession() {
        loadSession(null)
    }

    // =================== 其他 UI 邏輯 (保留) ===================

    /**
     * 處理語音輸入結果
     */
    fun handleVoiceInput(text: String) {
        if (text.isNotBlank()) {
            updateInputText(text)
            sendMessage(text)
        }
    }

    /**
     * 處理重試操作
     */
    fun retrySendMessage(message: ChatMessage) {
        // TODO: 重新發送特定訊息的邏輯
    }
} 