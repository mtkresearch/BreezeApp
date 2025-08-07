package com.mtkresearch.breezeapp.presentation.chat.viewmodel

import android.app.Application
import android.content.Context
import android.text.Html
import android.util.Log
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.mtkresearch.breezeapp.presentation.common.base.BaseViewModel
import com.mtkresearch.breezeapp.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp.presentation.chat.model.ChatSession
import com.mtkresearch.breezeapp.domain.usecase.breezeapp.*
import com.mtkresearch.breezeapp.domain.model.breezeapp.ConnectionState as BreezeAppConnectionState
import com.mtkresearch.breezeapp.domain.model.breezeapp.BreezeAppError
import com.mtkresearch.breezeapp.core.permission.OverlayPermissionManager
import com.mtkresearch.breezeapp.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 聊天ViewModel
 * 
 * 功能特色:
 * - 管理聊天訊息列表和會話狀態
 * - 處理用戶輸入和AI回應
 * - 支援訊息重試和錯誤處理
 * - 提供打字指示器和載入狀態
 * - 整合 BreezeApp Engine 提供真實AI功能
 * 
 * 遵循 Clean Architecture 原則:
 * - 使用 UseCase 處理業務邏輯
 * - 保持 ViewModel 的 UI 導向
 * - 統一的錯誤處理
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val application: Application,
    private val connectionUseCase: ConnectionUseCase,
    private val chatUseCase: ChatUseCase,
    private val streamingChatUseCase: StreamingChatUseCase,
    private val ttsUseCase: TtsUseCase,
    private val asrMicrophoneUseCase: AsrMicrophoneUseCase,
    private val requestCancellationUseCase: RequestCancellationUseCase,
    private val overlayPermissionManager: OverlayPermissionManager
) : BaseViewModel() {

    private val tag: String = "ChatViewModel"
    private var microphoneStreamingJob: Job? = null
    private var isUserStoppingMicrophone: Boolean = false

    /**
     * 取得應用程式字串資源
     */
    private fun getApplicationString(resId: Int): String {
        return Html.fromHtml(application.getString(resId), Html.FROM_HTML_MODE_COMPACT).toString()
    }

    // 當前聊天會話
    private val _currentSession = MutableStateFlow(ChatSession())
    val currentSession: StateFlow<ChatSession> = _currentSession.asStateFlow()

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

    // 語音識別狀態
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // 歷史會話列表 (簡化實作)
    private val _chatSessions = MutableStateFlow<List<ChatSession>>(emptyList())
    val chatSessions: StateFlow<List<ChatSession>> = _chatSessions.asStateFlow()
    
    // BreezeApp Engine 連接狀態
    private val _connectionState = MutableStateFlow<BreezeAppConnectionState>(BreezeAppConnectionState.Disconnected)
    val connectionState: StateFlow<BreezeAppConnectionState> = _connectionState.asStateFlow()
    
    // 當前串流請求ID
    private var currentStreamingRequestId: String? = null

    init {
        // 初始化時加載歡迎訊息
        loadWelcomeMessage()
        // 確保初始狀態正確
        updateCanSendMessageState()
        // 初始化 BreezeApp Engine 連接
        initializeBreezeAppEngine()
    }

    /**
     * 更新輸入文字
     */
    fun updateInputText(text: String) {
        _inputText.value = text
        updateCanSendMessageState()
    }

    /**
     * 更新canSendMessage狀態
     */
    private fun updateCanSendMessageState() {
        _canSendMessage.value = _inputText.value.trim().isNotEmpty() && !_isAIResponding.value && !_isListening.value
    }

    /**
     * 發送訊息
     */
    fun sendMessage(text: String = _inputText.value) {
        val messageText = text.trim()
        // 檢查是否在語音識別中或AI回應中，如果是則直接返回不執行
        if (messageText.isEmpty() || _isAIResponding.value || _isListening.value) return

        if (!validateInput(messageText.isNotBlank(), "訊息不能為空")) return

        launchSafely(showLoading = false) {
            // 創建用戶訊息 - 直接設為正常狀態
            val userMessage = ChatMessage(
                text = messageText,
                isFromUser = true,
                state = ChatMessage.MessageState.NORMAL
            )

            // 添加用戶訊息到列表
            addMessage(userMessage)
            
            // 清空輸入框
            _inputText.value = ""
            _canSendMessage.value = false
            _isAIResponding.value = true

            // 開始AI回應流程
            generateAIResponse(messageText)
        }
    }

    /**
     * 生成AI回應 (整合 BreezeApp Engine)
     */
    private suspend fun generateAIResponse(userInput: String) {
        try {
            // 檢查 BreezeApp Engine 連接狀態
            if (connectionUseCase.isConnected()) {
                // 使用真實的 BreezeApp Engine
                generateAIResponseWithBreezeApp(userInput)
            } else {
                // 回退到模擬回應
                generateMockAIResponse(userInput)
            }
        } catch (e: Exception) {
            handleAIResponseError(e)
        } finally {
            _isAIResponding.value = false
            // AI回應完成後，更新canSendMessage狀態
            updateCanSendMessageState()
        }
    }
    
    /**
     * 生成模擬AI回應 (回退方案)
     */
    private suspend fun generateMockAIResponse(userInput: String) {
        try {
            // 創建AI回應訊息 (初始為載入狀態)
            val aiMessage = ChatMessage(
                text = "正在思考中...",
                isFromUser = false,
                state = ChatMessage.MessageState.TYPING
            )
            addMessage(aiMessage)

            // 模擬AI思考時間
            delay((1500 + (0..1000).random()).toLong())

            // 生成模擬回應
            val response = generateMockResponse(userInput)
            
            // 更新AI訊息
            updateMessageText(aiMessage.id, response)
            updateMessageState(aiMessage.id, ChatMessage.MessageState.NORMAL)

            // 更新會話
            updateCurrentSession()
            
            setSuccess("AI回應完成 (模擬模式)")

        } catch (e: Exception) {
            handleAIResponseError(e)
        }
    }

    /**
     * 處理AI回應錯誤
     */
    private suspend fun handleAIResponseError(error: Throwable) {
        // 找到最後一條AI訊息並標記為錯誤
        val lastAIMessage = _messages.value.lastOrNull { !it.isFromUser }
        lastAIMessage?.let { message ->
            updateMessageText(message.id, "抱歉，我遇到了一些問題。請點擊重試。")
            updateMessageState(message.id, ChatMessage.MessageState.ERROR)
        }
        
        setError("AI回應失敗: ${error.message}")
    }

    /**
     * 重試最後一次AI回應
     */
    fun retryLastAIResponse() {
        val lastUserMessage = _messages.value.lastOrNull { it.isFromUser }
        if (lastUserMessage != null && !_isAIResponding.value) {
            launchSafely(showLoading = false) {
                _isAIResponding.value = true
                _canSendMessage.value = false
                
                // 找到並更新最後一條AI訊息，而不是移除它
                val lastAIMessage = _messages.value.lastOrNull { !it.isFromUser }
                if (lastAIMessage != null) {
                    // 重置AI訊息為載入狀態
                    updateMessageText(lastAIMessage.id, "正在思考中...")
                    updateMessageState(lastAIMessage.id, ChatMessage.MessageState.TYPING)
                    
                    // 模擬AI思考時間
                    delay((1500 + (0..1000).random()).toLong())
                    
                    // 生成新的回應
                    val response = generateMockResponse(lastUserMessage.text)
                    updateMessageText(lastAIMessage.id, response)
                    updateMessageState(lastAIMessage.id, ChatMessage.MessageState.NORMAL)
                    
                    setSuccess("AI回應重試完成")
                } else {
                    // 如果沒有AI訊息，創建新的
                    generateAIResponse(lastUserMessage.text)
                }
                
                _isAIResponding.value = false
                updateCanSendMessageState()
            }
        }
    }

    // Overlay permission status
    private val _overlayPermissionGranted = MutableStateFlow(false)
    val overlayPermissionGranted: StateFlow<Boolean> = _overlayPermissionGranted.asStateFlow()

    /**
     * Check overlay permission status
     */
    fun checkOverlayPermission(context: Context) {
        _overlayPermissionGranted.value = overlayPermissionManager.isOverlayPermissionGranted(context)
        Log.d(tag, "Overlay permission status: ${_overlayPermissionGranted.value}")
    }

    /**
     * Request overlay permission for microphone functionality
     */
    fun requestOverlayPermissionForMicrophone(context: Context) {
        if (overlayPermissionManager.isOverlayPermissionGranted(context)) {
            _overlayPermissionGranted.value = true
            setSuccess("覆蓋權限已授予")
            return
        }
        
        Log.d(tag, "Requesting overlay permission for microphone functionality")
        overlayPermissionManager.requestOverlayPermission(context)
    }

    /**
     * 開始語音識別 - 增強版本包含權限檢查
     */
    fun startVoiceRecognition(context: Context? = null) {
        if (_isListening.value || _isAIResponding.value) return

        // Check overlay permission before starting microphone
        context?.let { ctx ->
            if (!overlayPermissionManager.isOverlayPermissionGranted(ctx)) {
                Log.w(tag, "⚠️ Overlay permission not granted - this may cause FGS_MICROPHONE to fail")
                setError("需要覆蓋權限才能使用語音功能，請在設定中授予權限")
                return
            }
        }

        // Cancel any existing microphone streaming job
        microphoneStreamingJob?.cancel()

        microphoneStreamingJob = viewModelScope.launch {
            try {
                Log.d(tag, "🎤 Starting microphone streaming ASR...")
                Log.d(tag, "🔄 Engine will handle microphone recording directly")
                Log.d(tag, "✅ Overlay permission verified before starting")
                
                // Set recording state to true
                _isListening.value = true
                
                Log.d(tag, "🔄 Sending microphone mode ASR request to engine...")
                Log.d(tag, "   └── Engine will open microphone and process audio directly")
                
                asrMicrophoneUseCase.execute().collect { response ->
                    // Update input text with ASR result for real-time display
                    updateInputText(response.text)
                    
                    if (response.isChunk) {
                        Log.d(tag, "📡 [Microphone] ${response.text}")
                    } else {
                        Log.d(tag, "✅ [Microphone Final] ${response.text}")
                        // Final result received, but keep recording state until user stops
                        // The recording will continue until the user explicitly stops it
                    }
                    
                    // Show additional details if available
                    response.language?.let { lang ->
                        Log.d(tag, "   └── Detected language: $lang")
                    }
                    
                    response.segments?.forEach { segment ->
                        Log.d(tag, "   └── Segment: ${segment.text} (${segment.start}s - ${segment.end}s)")
                    }
                    
                    // Note: Flow will continue until cancelled by stopMicrophoneStreaming()
                    // We don't stop recording automatically on final result
                    // User must explicitly stop the recording
                }
                
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Check if this was user-initiated (most robust approach)
                if (isUserStoppingMicrophone) {
                    Log.d(tag, "✅ Microphone streaming stopped by user")
                } else {
                    Log.d(tag, "⚠️ Microphone streaming cancelled unexpectedly")
                }
                _isListening.value = false
            } catch (e: Exception) {
                Log.d(tag, "❌ Failed to start microphone streaming: ${e.message}")
                _isListening.value = false
            } finally {
                // Ensure recording state is reset when job completes
                _isListening.value = false
                microphoneStreamingJob = null
                isUserStoppingMicrophone = false // Reset flag
            }
        }
    }

    /**
     * 停止語音識別
     */
    fun stopVoiceRecognition() {
        if (_isListening.value) {
            isUserStoppingMicrophone = true
            microphoneStreamingJob?.cancel()
            microphoneStreamingJob = null
            requestCancellationUseCase.cancelLastRequest()
            _isListening.value = false
            updateCanSendMessageState() // 停止語音識別時更新按鈕狀態
            setSuccess("語音識別已停止")
        }
    }

    /**
     * 清空聊天記錄
     */
    fun clearChat() {
        _messages.value = emptyList()
        _currentSession.value = ChatSession()
        _inputText.value = ""
        _canSendMessage.value = false
        _isAIResponding.value = false
        _isListening.value = false
        updateCanSendMessageState() // 確保狀態一致
        setSuccess("聊天記錄已清空")
        // 不自動重新載入歡迎訊息，讓測試可以驗證空狀態
    }

    /**
     * 創建新會話
     */
    fun createNewSession() {
        // 保存當前會話
        if (_messages.value.isNotEmpty()) {
            val updatedSession = _currentSession.value.copy(
                messages = _messages.value,
                updatedAt = System.currentTimeMillis()
            )
            saveSession(updatedSession)
        }

        // 創建新會話
        _currentSession.value = ChatSession()
        _messages.value = emptyList()
        _inputText.value = ""
        _canSendMessage.value = false
        _isAIResponding.value = false
        updateCanSendMessageState() // 確保狀態一致
        
        // 加載歡迎訊息
        loadWelcomeMessage()
        
        setSuccess("已創建新對話")
    }

    /**
     * 加載會話
     */
    fun loadSession(session: ChatSession) {
        _currentSession.value = session
        _messages.value = session.messages
        _inputText.value = ""
        _canSendMessage.value = false
        _isAIResponding.value = false
        updateCanSendMessageState() // 確保狀態一致
        setSuccess("已載入對話: ${session.title}")
    }

    /**
     * 更新會話標題
     */
    fun updateSessionTitle(title: String) {
        _currentSession.value = _currentSession.value.copy(
            title = title,
            updatedAt = System.currentTimeMillis()
        )
    }

    /**
     * 處理訊息互動
     */
    fun handleMessageInteraction(action: MessageAction, message: ChatMessage, extra: Any? = null) {
        when (action) {
            MessageAction.SPEAKER_CLICK -> {
                playTtsForMessage(message)
            }
            MessageAction.LIKE_CLICK -> {
                val isPositive = extra as? Boolean ?: true
                if (isPositive) {
                    setSuccess("感謝您的正面回饋")
                } else {
                    setSuccess("我們會改進AI回應品質")
                }
            }
            MessageAction.RETRY_CLICK -> {
                retryLastAIResponse()
            }
            MessageAction.LONG_CLICK -> {
                // TODO: 顯示訊息選項菜單 (複製、刪除等)
                setSuccess("長按功能正在開發中")
            }
            MessageAction.IMAGE_CLICK -> {
                // TODO: 顯示圖片全螢幕預覽
                setSuccess("圖片預覽功能正在開發中")
            }
        }
    }

    /**
     * 添加訊息到列表
     */
    private fun addMessage(message: ChatMessage) {
        val currentMessages = _messages.value.toMutableList()
        currentMessages.add(message)
        _messages.value = currentMessages
    }

    /**
     * 更新訊息狀態
     */
    private fun updateMessageState(messageId: String, newState: ChatMessage.MessageState) {
        val currentMessages = _messages.value.toMutableList()
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            currentMessages[index] = currentMessages[index].copy(state = newState)
            _messages.value = currentMessages
        }
    }

    /**
     * 更新訊息文字
     */
    private fun updateMessageText(messageId: String, newText: String) {
        val currentMessages = _messages.value.toMutableList()
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            currentMessages[index] = currentMessages[index].copy(text = newText)
            _messages.value = currentMessages
        }
    }

    /**
     * 更新當前會話
     */
    private fun updateCurrentSession() {
        _currentSession.value = _currentSession.value.copy(
            messages = _messages.value,
            updatedAt = System.currentTimeMillis(),
            title = generateSessionTitle()
        )
    }

    /**
     * 保存會話
     */
    private fun saveSession(session: ChatSession) {
        val currentSessions = _chatSessions.value.toMutableList()
        val existingIndex = currentSessions.indexOfFirst { it.id == session.id }
        
        if (existingIndex != -1) {
            currentSessions[existingIndex] = session
        } else {
            currentSessions.add(0, session) // 新會話添加到最前面
        }
        
        _chatSessions.value = currentSessions
    }

    /**
     * 生成會話標題
     */
    private fun generateSessionTitle(): String {
        val firstUserMessage = _messages.value.firstOrNull { it.isFromUser }
        return if (firstUserMessage != null) {
            firstUserMessage.text.take(20) + if (firstUserMessage.text.length > 20) "..." else ""
        } else {
            "新對話"
        }
    }

    /**
     * 加載歡迎訊息
     */
    private fun loadWelcomeMessage() {
        // 同步加載歡迎訊息，避免測試中的異步問題
        val welcomeMessage = ChatMessage(
            text = getApplicationString(R.string.ai_welcome_message),
            isFromUser = false,
            state = ChatMessage.MessageState.NORMAL
        )
        addMessage(welcomeMessage)
        updateCurrentSession()
    }

    /**
     * 生成模擬AI回應
     */
    private fun generateMockResponse(userInput: String): String {
        val responses = listOf(
            "這是一個很有趣的問題！讓我來為您分析一下...",
            "根據您的描述，我建議您可以考慮以下幾個方面：",
            "我理解您的需求，這裡有一些可能對您有幫助的建議：",
            "這個話題很值得探討，從我的角度來看...",
            "感謝您的提問！我認為您可以從這個角度來思考：",
            "基於您提供的資訊，我的建議是...",
            "這確實是一個重要的問題，讓我為您詳細說明一下：",
            "我很樂意為您解答這個問題！首先..."
        )
        
        return responses.random() + "\n\n" + 
               "（這是Phase 1.3的模擬回應，真實的AI整合將在Phase 4實作）"
    }

    /**
     * 模擬語音識別
     */
    private fun mockVoiceRecognition(): String {
        val phrases = listOf(
            "你好，請幫我解答一個問題",
            "今天天氣如何",
            "請介紹一下這個應用程式的功能",
            "我想了解AI技術的發展",
            "可以給我一些學習建議嗎"
        )
        return phrases.random()
    }

    /**
     * 初始化 BreezeApp Engine 連接
     */
    private fun initializeBreezeAppEngine() {
        launchSafely(showLoading = false) {
            connectionUseCase.initialize().collect { state ->
                _connectionState.value = state
                when (state) {
                    is BreezeAppConnectionState.Connected -> {
                        setSuccess("BreezeApp Engine 連接成功")
                    }
                    is BreezeAppConnectionState.Failed -> {
                        setError("BreezeApp Engine 連接失敗: ${state.message}")
                    }
                    else -> {
                        // 處理其他狀態
                    }
                }
            }
        }
    }
    
    /**
     * 使用 BreezeApp Engine 生成AI回應
     */
    private suspend fun generateAIResponseWithBreezeApp(userInput: String) {
        var aiMessage: ChatMessage? = null
        var accumulatedContent = StringBuilder()
        try {
            // 檢查連接狀態
            if (!connectionUseCase.isConnected()) {
                throw BreezeAppError.ConnectionError.ServiceDisconnected("BreezeApp Engine 未連接")
            }
            
            // 創建AI回應訊息 (初始為載入狀態)
            aiMessage = ChatMessage(
                text = "正在思考中...",
                isFromUser = false,
                state = ChatMessage.MessageState.TYPING
            )
            addMessage(aiMessage)
            
            // 使用串流聊天 UseCase
            streamingChatUseCase.execute(
                prompt = userInput,
                systemPrompt = "你是一個友善、專業的AI助手。請用繁體中文回答，並保持簡潔明瞭。",
                temperature = 0.7f
            ).collect { response ->
                // 更新AI訊息內容
                val content = response.choices.firstOrNull()?.delta?.content ?: ""
                if (content.isNotEmpty()) {
                    // 直接使用streaming response的內容，因為它可能已經是累積的完整內容
                    // 不再使用本地StringBuilder累積，避免重複累積
                    updateMessageText(aiMessage.id, content)
                }
            }
            
            // 更新訊息狀態為正常
            updateMessageState(aiMessage.id, ChatMessage.MessageState.NORMAL)
            
            // 更新會話
            updateCurrentSession()
            
            setSuccess("AI回應完成")
            
        } catch (e: BreezeAppError) {
            aiMessage?.let { message ->
                handleBreezeAppError(e, message)
            }
        } catch (e: Exception) {
            handleAIResponseError(e)
        }
    }
    
    /**
     * 處理 BreezeApp Engine 錯誤
     */
    private suspend fun handleBreezeAppError(error: BreezeAppError, aiMessage: ChatMessage) {
        val errorMessage = when (error) {
            is BreezeAppError.ConnectionError.ServiceDisconnected -> "BreezeApp Engine 連接中斷，請檢查連接狀態"
            is BreezeAppError.ChatError.InvalidInput -> "輸入格式不正確，請重新輸入"
            is BreezeAppError.ChatError.ModelNotFound -> "AI模型未找到，請檢查設定"
            is BreezeAppError.ChatError.GenerationFailed -> "AI回應生成失敗，請重試"
            is BreezeAppError.ChatError.StreamingError -> "串流回應中斷，請重試"
            else -> "發生未知錯誤，請重試"
        }
        
        updateMessageText(aiMessage.id, errorMessage)
        updateMessageState(aiMessage.id, ChatMessage.MessageState.ERROR)
        setError(errorMessage)
    }

    private fun playTtsForMessage(message: ChatMessage) {
        launchSafely(showLoading = false) {
            try {
                if (!connectionUseCase.isConnected()) {
                    throw BreezeAppError.ConnectionError.ServiceDisconnected("BreezeApp Engine 未連接")
                }

                // 從當前狀態獲取最新的訊息內容，避免使用過期的訊息引用
                val currentMessage = _messages.value.find { it.id == message.id }
                val textToSpeak = currentMessage?.text ?: message.text
                
                if (textToSpeak.isEmpty() || textToSpeak == "正在思考中...") {
                    setError("無法播放空白或載入中的訊息")
                    return@launchSafely
                }

                ttsUseCase.execute(textToSpeak).collect { response ->
                    if (response.isLastChunk == true) {
                        setSuccess("語音播放完畢")
                    }
                }
            } catch (e: BreezeAppError) {
                // 使用當前訊息進行錯誤處理
                val currentMessage = _messages.value.find { it.id == message.id } ?: message
                handleBreezeAppError(e, currentMessage)
            } catch (e: Exception) {
                handleAIResponseError(e)
            }
        }
    }
    
    /**
     * 重連 BreezeApp Engine
     */
    fun reconnectBreezeAppEngine() {
        launchSafely(showLoading = false) {
            connectionUseCase.connect().collect { state ->
                _connectionState.value = state
                when (state) {
                    is BreezeAppConnectionState.Connected -> {
                        setSuccess("BreezeApp Engine 重連成功")
                    }
                    is BreezeAppConnectionState.Failed -> {
                        setError("BreezeApp Engine 重連失敗: ${state.message}")
                    }
                    else -> {
                        // 處理其他狀態
                    }
                }
            }
        }
    }
    
    /**
     * 取消當前串流請求
     */
    fun cancelCurrentStreamingRequest() {
        currentStreamingRequestId?.let { requestId ->
            requestCancellationUseCase.cancelRequest(requestId)
            currentStreamingRequestId = null
        }
    }

    /**
     * 訊息互動動作枚舉
     */
    enum class MessageAction {
        SPEAKER_CLICK,  // 語音播放
        LIKE_CLICK,     // 點讚/點踩
        RETRY_CLICK,    // 重試
        LONG_CLICK,     // 長按
        IMAGE_CLICK     // 圖片點擊
    }
} 