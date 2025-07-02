# 🚀 **BreezeApp Kotlin API 設計文檔 (v2.0 - AI Router 架構)**

*版本: v2.0 | 最後更新: 2024-12-19 | 基於AI Router獨立架構設計*

---

## 📋 **目錄**

1. [**架構概覽**](#architecture-overview)
2. [**基礎架構 API**](#base-architecture-api)
3. [**UI組件 API**](#ui-components-api)
4. [**聊天模組 API**](#chat-module-api)
5. [**AI Router 通信 API**](#ai-router-communication-api)
6. [**測試架構**](#testing-architecture)
7. [**使用指南**](#usage-guide)
8. [**最佳實踐**](#best-practices)

---

## 🏗️ **架構概覽** {#architecture-overview}

### **AI Router 獨立架構模式**

```
┌─────────────────────────────┐    ┌─────────────────────────────┐
│        UI Module            │    │      AI Router Module       │
│      (Main Process)         │◄──►│   (Background Service)      │
├─────────────────────────────┤    ├─────────────────────────────┤
│ • Chat UI & Navigation      │    │ • AI Engine Management     │
│ • App Settings & Profile    │    │ • Model Download & Cache    │
│ • User Interface Only       │    │ • Runtime Configuration     │
│ • AI Router Client          │    │ • Floating Management UI    │
└─────────────────────────────┘    └─────────────────────────────┘
         │                                       │
    ┌─────────┐                           ┌─────────┐
    │Fragment │                           │ Service │
    │Activity │                           │ Runner  │
    │ViewModel│                           │ Manager │
    └─────────┘                           └─────────┘
```

### **責任分離原則**

#### **UI Module 職責**
- ✅ **聊天互動**: 訊息收發、歷史管理、UI 狀態
- ✅ **應用設定**: 主題、字體、語言、使用者偏好
- ✅ **導航管理**: Fragment 切換、Activity 管理
- ✅ **通信客戶端**: 與 AI Router 的介面層

#### **AI Router Module 職責**  
- ✅ **AI 引擎管理**: LLM/VLM/ASR/TTS 引擎運行
- ✅ **模型管理**: 下載、驗證、快取、版本控制
- ✅ **推論配置**: AI 參數設定、效能調整
- ✅ **系統管理**: 浮動介面、狀態監控

### **模組依賴關係**

```
app/src/main/java/com/mtkresearch/breezeapp_kotlin/
├── presentation/          # UI層 (簡化版 - 92%完成)
│   ├── common/           # 基礎組件和工具
│   ├── chat/            # 聊天功能模組 (重構中)
│   ├── home/            # 主頁功能模組
│   ├── settings/        # 應用層設定 (僅UI偏好)
│   └── router/          # AI Router 通信客戶端 (新增)
├── domain/               # UI業務邏輯層 (88%完成)
├── data/                 # UI資料層 (Room Database)
└── core/                 # 核心工具和擴展

AI Router Service (獨立進程):
├── service/              # 背景服務核心 (待實作)
├── engine/              # AI 引擎管理 (待實作)
├── model/               # 模型管理 (待實作)
├── config/              # 推論配置 (待實作)
└── management/          # 系統管理界面 (待實作)
```

---

## 🏛️ **基礎架構 API** {#base-architecture-api}

### **BaseFragment.kt** (202行)

統一的Fragment基礎類別，提供生命週期管理、權限處理和錯誤顯示。

#### **核心API**

```kotlin
abstract class BaseFragment : Fragment() {
    
    // 抽象方法 - 子類別必須實作
    protected abstract fun setupUI()
    
    // 可選覆寫
    protected open fun observeUIState()
    protected open fun onCleanup()
    
    // 狀態顯示
    protected open fun showLoading()
    protected open fun hideLoading()
    protected open fun showError(message: String, action: (() -> Unit)? = null)
    protected open fun showSuccess(message: String)
    
    // 安全的Flow收集
    protected fun <T> Flow<T>.collectSafely(
        state: Lifecycle.State = Lifecycle.State.STARTED,
        action: (T) -> Unit
    )
    
    // 權限處理
    protected fun hasPermission(permission: String): Boolean
    protected fun hasPermissions(permissions: Array<String>): Boolean
    protected fun requestPermission(permission: String)
    protected fun requestPermissions(permissions: Array<String>)
    
    // 權限回調
    protected open fun onPermissionsResult(permissions: Map<String, Boolean>)
    protected open fun onPermissionsDenied(permissions: List<String>)
    protected open fun onPermissionsGranted(permissions: List<String>)
}
```

#### **常用權限常數**

```kotlin
companion object {
    const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    const val READ_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
    
    val MEDIA_PERMISSIONS = arrayOf(READ_EXTERNAL_STORAGE_PERMISSION, CAMERA_PERMISSION)
    val AUDIO_PERMISSIONS = arrayOf(RECORD_AUDIO_PERMISSION)
}
```

#### **使用範例**

```kotlin
class ChatFragment : BaseFragment() {
    override fun setupUI() {
        // 初始化UI組件
        setupRecyclerView()
        setupInputField()
    }
    
    override fun observeUIState() {
        viewModel.uiState.collectSafely { state ->
            when (state.state) {
                UiState.LOADING -> showLoading()
                UiState.ERROR -> showError(state.message)
                UiState.SUCCESS -> hideLoading()
            }
        }
    }
}
```

---

### **BaseViewModel.kt** (279行)

統一的ViewModel基礎類別，提供狀態管理、協程處理和錯誤處理。

#### **UI狀態枚舉**

```kotlin
enum class UiState {
    IDLE,       // 閒置狀態
    LOADING,    // 載入中
    SUCCESS,    // 成功
    ERROR       // 錯誤
}

data class BaseUiState(
    val state: UiState = UiState.IDLE,
    val message: String = "",
    val isLoading: Boolean = false,
    val error: Throwable? = null
)
```

#### **核心API**

```kotlin
abstract class BaseViewModel : ViewModel() {
    
    // 狀態Flow
    val uiState: StateFlow<BaseUiState>
    val isLoading: StateFlow<Boolean>
    val error: StateFlow<String?>
    val successMessage: StateFlow<String?>
    
    // 狀態設置
    protected fun setLoading(isLoading: Boolean)
    protected fun setError(message: String, throwable: Throwable? = null)
    protected fun setSuccess(message: String = "")
    protected fun resetState()
    
    // 狀態清除
    fun clearError()
    fun clearSuccessMessage()
    
    // 安全協程執行
    protected fun launchSafely(
        showLoading: Boolean = true,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Unit
    )
    
    protected fun <T> launchWithResult(
        showLoading: Boolean = true,
        onSuccess: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> T
    )
    
    // 工具方法
    protected fun validateInput(condition: Boolean, errorMessage: String): Boolean
    protected fun String?.isNotNullOrEmpty(): Boolean
    protected inline fun <T> safeCall(block: () -> T): T?
    
    // 錯誤處理
    protected open fun handleError(throwable: Throwable)
    open fun retry()
}
```

#### **使用範例**

```kotlin
class ChatViewModel : BaseViewModel() {
    
    fun sendMessage(text: String) = launchSafely {
        if (!validateInput(text.isNotBlank(), "訊息不能為空")) return@launchSafely
        
        // 發送訊息邏輯
        val response = aiService.sendMessage(text)
        setSuccess("訊息發送成功")
    }
    
    override fun handleError(throwable: Throwable) {
        when (throwable) {
            is NetworkException -> setError("網路連線失敗")
            else -> super.handleError(throwable)
        }
    }
}
```

---

### **BaseAdapter.kt** (288行)

統一的RecyclerView適配器基礎類別，提供DiffUtil支援和點擊處理。

#### **核心API**

```kotlin
abstract class BaseAdapter<T, VH : BaseViewHolder<T>>(
    diffCallback: DiffUtil.ItemCallback<T>
) : ListAdapter<T, VH>(diffCallback) {
    
    // 抽象方法
    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
    
    // 點擊監聽器
    fun setOnItemClickListener(listener: OnItemClickListener<T>)
    fun setOnItemClickListener(onClick: (T, Int, View) -> Unit)
    
    // 數據操作
    fun addItem(item: T, position: Int = itemCount)
    fun addItems(items: List<T>, position: Int = itemCount)
    fun removeItem(position: Int)
    fun removeItem(item: T)
    fun updateItem(position: Int, newItem: T)
    fun updateItem(oldItem: T, newItem: T)
    fun clear()
    fun refresh()
    
    // 查詢方法
    fun findItem(predicate: (T) -> Boolean): T?
    fun findPosition(predicate: (T) -> Boolean): Int
    fun getItemAt(position: Int): T?
    fun getFirstItem(): T?
    fun getLastItem(): T?
    
    // 狀態檢查
    fun isEmpty(): Boolean
    fun isNotEmpty(): Boolean
}
```

#### **ViewHolder基礎類別**

```kotlin
abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: T, position: Int)
    
    open fun bind(item: T, position: Int, payloads: List<Any>) {
        bind(item, position)
    }
    
    open fun onViewRecycled() {
        // 清理資源
    }
}
```

#### **使用範例**

```kotlin
class MessageAdapter : BaseAdapter<ChatMessage, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        return MessageViewHolder(...)
    }
    
    init {
        setOnItemClickListener { message, position, view ->
            // 處理點擊事件
        }
    }
}

private class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
    override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
        return oldItem == newItem
    }
}
```

---

## 🎨 **UI組件 API** {#ui-components-api}

### **MessageBubbleView.kt** (377行)

訊息氣泡UI組件，支援多種訊息類型和狀態。

#### **訊息類型和狀態**

```kotlin
enum class MessageType {
    USER,    // 用戶訊息
    AI,      // AI回應
    SYSTEM   // 系統訊息
}

enum class MessageState {
    NORMAL,   // 正常狀態
    LOADING,  // 載入中
    ERROR,    // 錯誤狀態
    TYPING    // 打字中
}
```

#### **核心API**

```kotlin
class MessageBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    // 主要設置方法
    fun setMessage(
        text: String,
        type: MessageType = MessageType.USER,
        state: MessageState = MessageState.NORMAL,
        showButtons: Boolean = false,
        imageUrl: String? = null
    )
    
    // 互動回調
    fun setOnSpeakerClickListener(listener: () -> Unit)
    fun setOnLikeClickListener(listener: (isPositive: Boolean) -> Unit)
    fun setOnRetryClickListener(listener: () -> Unit)
    fun setOnLongClickListener(listener: () -> Unit)
    fun setOnImageClickListener(listener: () -> Unit)
    
    // 狀態更新
    fun updateState(newState: MessageState)
    fun updateText(newText: String)
    fun showTypingAnimation()
    fun hideTypingAnimation()
}
```

#### **使用範例**

```kotlin
// XML佈局
<com.mtkresearch.breezeapp_kotlin.presentation.common.widget.MessageBubbleView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:messageType="ai"
    app:messageState="normal"
    app:showButtons="true" />

// Kotlin代碼
messageBubble.setMessage(
    text = "您好！我是AI助手。",
    type = MessageType.AI,
    state = MessageState.NORMAL,
    showButtons = true
)

messageBubble.setOnSpeakerClickListener {
    // 播放語音
}

messageBubble.setOnLikeClickListener { isPositive ->
    // 處理點讚/點踩
}
```

---

### **LoadingView.kt** (458行)

載入狀態組件，支援多種載入樣式和尺寸。

#### **載入樣式和尺寸**

```kotlin
enum class LoadingStyle {
    CIRCULAR,    // 圓形進度條
    HORIZONTAL,  // 水平進度條
    DOTS,        // 點狀動畫
    SPINNER      // 旋轉動畫
}

enum class LoadingSize {
    SMALL,   // 小尺寸
    MEDIUM,  // 中等尺寸
    LARGE    // 大尺寸
}
```

#### **核心API**

```kotlin
class LoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    // 顯示載入
    fun show(
        message: String = "",
        subtitle: String = "",
        showCancel: Boolean = false,
        style: LoadingStyle = LoadingStyle.CIRCULAR,
        size: LoadingSize = LoadingSize.MEDIUM
    )
    
    // 隱藏載入
    fun hide()
    
    // 設置回調
    fun setOnCancelClickListener(listener: () -> Unit)
    
    // 更新訊息
    fun updateMessage(message: String, subtitle: String = "")
    
    // 狀態檢查
    fun isShowing(): Boolean
}
```

#### **使用範例**

```kotlin
// 顯示載入
loadingView.show(
    message = "載入AI模型中...",
    subtitle = "首次載入需要較長時間",
    showCancel = true,
    style = LoadingStyle.CIRCULAR,
    size = LoadingSize.LARGE
)

loadingView.setOnCancelClickListener {
    // 取消載入
}

// 隱藏載入
loadingView.hide()
```

---

### **ErrorView.kt** (483行)

錯誤狀態組件，支援多種錯誤類型和嚴重程度。

#### **錯誤類型和嚴重程度**

```kotlin
enum class ErrorType {
    NETWORK,        // 網路錯誤
    SERVER,         // 服務器錯誤
    MODEL_LOADING,  // 模型載入錯誤
    AI_PROCESSING,  // AI處理錯誤
    FILE_ACCESS,    // 檔案存取錯誤
    VALIDATION,     // 驗證錯誤
    PERMISSION,     // 權限錯誤
    UNKNOWN         // 未知錯誤
}

enum class ErrorSeverity {
    INFO,     // 信息
    WARNING,  // 警告
    ERROR,    // 錯誤
    CRITICAL  // 嚴重錯誤
}
```

#### **核心API**

```kotlin
class ErrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {
    
    // 通用錯誤顯示
    fun showError(
        type: ErrorType,
        severity: ErrorSeverity = ErrorSeverity.ERROR,
        title: String? = null,
        message: String? = null,
        showRetry: Boolean = true,
        showClose: Boolean = true,
        customAction: String? = null
    )
    
    // 快速錯誤方法
    fun showNetworkError(showRetry: Boolean = true)
    fun showServerError(showRetry: Boolean = true)
    fun showAIError(showRetry: Boolean = true)
    fun showPermissionError(showSettings: Boolean = true)
    
    // 隱藏錯誤
    fun hide()
    
    // 設置回調
    fun setOnRetryClickListener(listener: () -> Unit)
    fun setOnCloseClickListener(listener: () -> Unit)
    fun setOnCustomActionClickListener(listener: () -> Unit)
    
    // 狀態檢查
    fun isShowing(): Boolean
}
```

#### **使用範例**

```kotlin
// 快速顯示網路錯誤
errorView.showNetworkError(showRetry = true)

// 自定義錯誤
errorView.showError(
    type = ErrorType.AI_PROCESSING,
    severity = ErrorSeverity.ERROR,
    title = "AI處理失敗",
    message = "請檢查網路連線後重試",
    showRetry = true,
    customAction = "檢查設定"
)

errorView.setOnRetryClickListener {
    // 重試邏輯
}
```

---

## 💬 **聊天模組 API (重構版)** {#chat-module-api}

### **ChatMessage.kt** (35行)

訊息數據模型，移除AI相關複雜屬性。

```kotlin
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val state: MessageState = MessageState.NORMAL,
    val imageUrl: String? = null
) {
    enum class MessageState {
        NORMAL,   // 正常狀態
        SENDING,  // 發送中
        LOADING,  // 載入中 (等待AI Router回應)
        ERROR,    // 錯誤狀態
        TYPING    // 打字中
    }
}

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新對話",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

---

### **ChatViewModel.kt** (重構版)

聊天視圖模型，專注於UI狀態管理和AI Router通信。

#### **重構要點**
- ❌ **移除**: 模型下載、模型切換、引擎直接調用
- ✅ **保留**: 訊息收發、聊天歷史、UI 狀態管理  
- ✅ **新增**: AI Router 通信、連線狀態管理

#### **狀態Flow (重構版)**

```kotlin
class ChatViewModel(
    private val aiRouterClient: AIRouterClient // 新增: AI Router通信客戶端
) : BaseViewModel() {
    
    // 聊天狀態 (保留)
    val messages: StateFlow<List<ChatMessage>>
    val inputText: StateFlow<String>
    val canSendMessage: StateFlow<Boolean>
    val isTyping: StateFlow<Boolean>
    
    // AI Router連線狀態 (新增)
    val aiRouterConnectionState: StateFlow<ConnectionState>
    val aiRouterStatus: StateFlow<String>
    
    // 會話管理 (保留)
    val currentSession: StateFlow<ChatSession>
    val chatSessions: StateFlow<List<ChatSession>>
    
    enum class ConnectionState {
        DISCONNECTED,    // 未連接
        CONNECTING,      // 連接中
        CONNECTED,       // 已連接
        ERROR           // 連接錯誤
    }
}
```

#### **核心API (重構版)**

```kotlin
// 訊息處理 (保留，但改為通過AI Router)
fun sendMessage(text: String)
fun updateInputText(text: String)
fun retryLastMessage()

// AI Router 連線管理 (新增)
fun connectToAIRouter()
fun disconnectFromAIRouter()
fun checkAIRouterStatus()

// 會話管理 (保留)
fun clearChat()
fun createNewSession()
fun loadSession(session: ChatSession)
fun updateSessionTitle(title: String)

// 訊息互動 (保留)
fun handleMessageInteraction(action: MessageAction, message: ChatMessage, extra: Any? = null)

// ❌ 移除的方法 (移至AI Router):
// fun downloadModel()
// fun switchModel()
// fun updateModelSettings()
// fun startVoiceRecognition()
// fun stopVoiceRecognition()
```

---

### **MessageAdapter.kt** (400行)

訊息列表適配器，繼承BaseAdapter提供訊息顯示功能。

#### **核心API**

```kotlin
class MessageAdapter : BaseAdapter<ChatMessage, MessageAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    // 訊息互動監聽器
    interface MessageInteractionListener {
        fun onSpeakerClick(message: ChatMessage, position: Int)
        fun onLikeClick(message: ChatMessage, position: Int, isPositive: Boolean)
        fun onRetryClick(message: ChatMessage, position: Int)
        fun onLongClick(message: ChatMessage, position: Int)
        fun onImageClick(message: ChatMessage, position: Int)
    }
    
    // 設置監聽器
    fun setMessageInteractionListener(listener: MessageInteractionListener)
    
    // 滾動控制
    fun scrollToLatest(recyclerView: RecyclerView)
    
    // 狀態更新
    fun updateMessageState(messageId: String, newState: ChatMessage.MessageState): Boolean
    fun updateMessageText(messageId: String, newText: String): Boolean
    
    // 訊息操作
    fun addMessage(message: ChatMessage, recyclerView: RecyclerView? = null)
    fun addMessages(messages: List<ChatMessage>, scrollToLatest: Boolean = true, recyclerView: RecyclerView? = null)
    fun clearMessages()
    
    // 查詢方法
    fun getLastMessage(): ChatMessage?
    fun getLastUserMessage(): ChatMessage?
    fun getLastAIMessage(): ChatMessage?
    fun findMessageById(messageId: String): ChatMessage?
    fun findMessageByPredicate(predicate: (ChatMessage) -> Boolean): ChatMessage?
    fun findMessagePosition(predicate: (ChatMessage) -> Boolean): Int
    fun getMessageAt(position: Int): ChatMessage?
    fun getMessageCount(): Int
}
```

#### **使用範例**

```kotlin
// 設置適配器
val adapter = MessageAdapter()
recyclerView.adapter = adapter

// 設置互動監聽器
adapter.setMessageInteractionListener(object : MessageAdapter.MessageInteractionListener {
    override fun onSpeakerClick(message: ChatMessage, position: Int) {
        // 播放語音
    }
    
    override fun onLikeClick(message: ChatMessage, position: Int, isPositive: Boolean) {
        viewModel.handleMessageInteraction(
            ChatViewModel.MessageAction.LIKE_CLICK, 
            message, 
            isPositive
        )
    }
    
    override fun onRetryClick(message: ChatMessage, position: Int) {
        viewModel.retryLastMessage()
    }
    
    override fun onLongClick(message: ChatMessage, position: Int) {
        showMessageContextMenu(message)
    }
    
    override fun onImageClick(message: ChatMessage, position: Int) {
        // 顯示圖片預覽
    }
})

// 更新訊息列表
viewModel.messages.collectSafely { messages ->
    adapter.submitList(messages) {
        adapter.scrollToLatest(recyclerView)
    }
}
```

---

### **ChatFragment.kt** (593行)

聊天介面Fragment，整合所有聊天功能。

#### **核心功能**

```kotlin
class ChatFragment : BaseFragment() {
    
    // ViewBinding
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    
    // ViewModel
    private lateinit var viewModel: ChatViewModel
    
    // 適配器
    private lateinit var adapter: MessageAdapter
    
    // 主要方法
    override fun setupUI()
    override fun observeUIState()
    
    // 互動處理
    private fun setupRecyclerView()
    private fun setupInputField()
    private fun setupAIRouterConnection()
    
    // 權限處理
    override fun onPermissionsResult(permissions: Map<String, Boolean>)
    
    // 鍵盤處理
    fun onBackPressed(): Boolean
    fun handleTouchOutsideKeyboard(event: MotionEvent)
    
    // 工廠方法
    companion object {
        fun newInstance(): ChatFragment
        const val TAG = "ChatFragment"
    }
}
```

#### **使用範例**

```kotlin
// Activity中使用
class ChatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val fragment = ChatFragment.newInstance()
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment, ChatFragment.TAG)
            .commit()
    }
    
    override fun onBackPressed() {
        val fragment = supportFragmentManager.findFragmentByTag(ChatFragment.TAG) as? ChatFragment
        if (fragment?.onBackPressed() != true) {
            super.onBackPressed()
        }
    }
}

// MainActivity中使用
private fun navigateToChat() {
    val intent = Intent(this, ChatActivity::class.java)
    startActivity(intent)
}
```

---

## 🔗 **AI Router 通信 API** {#ai-router-communication-api}

### **AIRouterClient.kt** (新增)

UI Layer 與 AI Router Service 的統一通信介面。

#### **核心API**

```kotlin
interface AIRouterClient {
    
    // 連線管理
    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    fun isConnected(): Boolean
    fun getConnectionState(): Flow<ConnectionState>
    
    // 訊息處理
    suspend fun sendMessage(
        text: String,
        sessionId: String = "",
        messageType: MessageType = MessageType.TEXT
    ): Flow<AIResponse>
    
    // AI Router狀態查詢
    suspend fun getAIRouterStatus(): Result<AIRouterStatus>
    suspend fun getAvailableCapabilities(): Result<List<AICapability>>
    
    // 錯誤處理
    fun getErrorEvents(): Flow<AIRouterError>
}

// 實現類別
class AIRouterClientImpl(
    private val context: Context,
    private val messenger: Messenger? = null
) : AIRouterClient {
    
    private val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val serviceConnection = object : ServiceConnection { ... }
    
    override suspend fun connect(): Result<Unit> {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.mtkresearch.breezeapp_kotlin.airouter", 
                    "com.mtkresearch.breezeapp_kotlin.airouter.AIRouterService"
                )
            }
            val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (bound) {
                connectionState.value = ConnectionState.CONNECTING
                Result.success(Unit)
            } else {
                Result.failure(AIRouterException("Failed to bind AI Router Service"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun sendMessage(
        text: String,
        sessionId: String,
        messageType: MessageType
    ): Flow<AIResponse> = callbackFlow {
        // IPC 通信實現
        val request = AIRequest(
            id = UUID.randomUUID().toString(),
            text = text,
            sessionId = sessionId,
            type = messageType,
            timestamp = System.currentTimeMillis()
        )
        
        // 發送到AI Router Service
        sendRequestToService(request) { response ->
            trySend(response)
        }
        
        awaitClose { /* 清理資源 */ }
    }
}
```

#### **資料模型**

```kotlin
data class AIRequest(
    val id: String,
    val text: String,
    val sessionId: String,
    val type: MessageType,
    val timestamp: Long,
    val metadata: Map<String, Any> = emptyMap()
)

data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean = false,
    val state: ResponseState = ResponseState.PROCESSING,
    val metadata: Map<String, Any> = emptyMap()
) {
    enum class ResponseState {
        PROCESSING,  // 處理中
        STREAMING,   // 串流回應中
        COMPLETED,   // 完成
        ERROR       // 錯誤
    }
}

data class AIRouterStatus(
    val isRunning: Boolean,
    val availableEngines: List<String>,
    val currentModel: String?,
    val memoryUsage: Long,
    val processCount: Int
)

enum class AICapability {
    TEXT_GENERATION,  // 文字生成 (LLM)
    IMAGE_ANALYSIS,   // 圖像分析 (VLM)
    SPEECH_TO_TEXT,   // 語音識別 (ASR)
    TEXT_TO_SPEECH    // 語音合成 (TTS)
}
```

#### **錯誤處理**

```kotlin
sealed class AIRouterError : Exception() {
    data class ConnectionError(override val message: String) : AIRouterError()
    data class ServiceError(override val message: String, val code: Int) : AIRouterError()
    data class EngineError(override val message: String, val engine: String) : AIRouterError()
    data class ModelError(override val message: String, val model: String) : AIRouterError()
}

class AIRouterException(message: String, cause: Throwable? = null) : Exception(message, cause)
```

---

### **AIRouterFacade.kt** (AI Router Service 側)

AI Router Service 提供給 UI Layer 的統一介面。

```kotlin
interface AIRouterFacade {
    
    // 核心能力
    suspend fun processTextGeneration(request: TextGenerationRequest): Flow<TextGenerationResponse>
    suspend fun processImageAnalysis(request: ImageAnalysisRequest): Flow<ImageAnalysisResponse>
    suspend fun processSpeechToText(request: SpeechToTextRequest): Flow<SpeechToTextResponse>
    suspend fun processTextToSpeech(request: TextToSpeechRequest): Flow<TextToSpeechResponse>
    
    // 系統管理
    suspend fun getSystemStatus(): AIRouterSystemStatus
    suspend fun getAvailableModels(): List<ModelInfo>
    suspend fun getEngineCapabilities(): Map<String, List<AICapability>>
    
    // 配置管理 (從UI移來的Runtime Settings功能)
    suspend fun updateRuntimeConfig(config: RuntimeConfig): Result<Unit>
    suspend fun getCurrentConfig(): RuntimeConfig
    suspend fun validateConfig(config: RuntimeConfig): ValidationResult
}
```

---

## 🧪 **測試架構 (v2.0)** {#testing-architecture}

### **UI Layer 測試 (已完成)**

#### **ChatViewModelTest.kt** (需重構)

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChatViewModel
    private lateinit var mockAIRouterClient: AIRouterClient
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockAIRouterClient = mockk<AIRouterClient>()
        viewModel = ChatViewModel(mockAIRouterClient)
    }
    
    @Test
    fun `發送訊息應該通過AI Router Client處理`() = runTest(testDispatcher) {
        // Given
        val messageText = "Hello AI"
        val mockResponse = AIResponse(
            requestId = "123",
            text = "Hello! How can I help you?",
            isComplete = true,
            state = AIResponse.ResponseState.COMPLETED
        )
        
        coEvery { 
            mockAIRouterClient.sendMessage(messageText, any(), any()) 
        } returns flowOf(mockResponse)
        
        // When
        viewModel.sendMessage(messageText)
        advanceUntilIdle()
        
        // Then
        coVerify { mockAIRouterClient.sendMessage(messageText, any(), any()) }
        
        val messages = viewModel.messages.value
        assertEquals(2, messages.size) // User message + AI response
        assertEquals(messageText, messages[0].text)
        assertEquals(mockResponse.text, messages[1].text)
    }
    
    @Test
    fun `AI Router連線狀態應該正確管理`() = runTest(testDispatcher) {
        // Given
        val connectionFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        every { mockAIRouterClient.getConnectionState() } returns connectionFlow.asStateFlow()
        
        // When
        connectionFlow.value = ConnectionState.CONNECTED
        advanceUntilIdle()
        
        // Then
        assertEquals(ConnectionState.CONNECTED, viewModel.aiRouterConnectionState.value)
    }
    
    // ❌ 移除的測試 (移至AI Router):
    // @Test fun `模型下載測試`()
    // @Test fun `語音識別測試`()
    // @Test fun `AI引擎切換測試`()
}
```

### **AIRouterClientTest.kt** (新增)

```kotlin
@RunWith(RobolectricTestRunner::class)
class AIRouterClientTest {
    
    private lateinit var context: Context
    private lateinit var aiRouterClient: AIRouterClient
    private lateinit var mockMessenger: Messenger
    
    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        mockMessenger = mockk<Messenger>()
        aiRouterClient = AIRouterClientImpl(context)
    }
    
    @Test
    fun `連接AI Router Service應該成功`() = runTest {
        // Given - Mock ServiceConnection
        
        // When
        val result = aiRouterClient.connect()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(ConnectionState.CONNECTING, aiRouterClient.getConnectionState().value)
    }
    
    @Test
    fun `發送訊息應該返回正確的Flow`() = runTest {
        // Given
        val messageText = "Test message"
        
        // When
        val responseFlow = aiRouterClient.sendMessage(messageText)
        
        // Then
        responseFlow.test {
            val response = awaitItem()
            assertEquals(messageText, response.requestId)
            awaitComplete()
        }
    }
    
    @Test
    fun `連線錯誤應該正確處理`() = runTest {
        // Given - Mock connection failure
        
        // When
        val result = aiRouterClient.connect()
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is AIRouterException)
    }
}
```

### **測試覆蓋統計 (v2.0)**

```kotlin
UI Layer 測試 (已完成):
├── ChatViewModelTest.kt (重構中) - AI Router通信測試
├── MessageAdapterTest.kt (保留) - 適配器測試 
├── ChatMessageTest.kt (保留) - 訊息模型測試
├── AppSettingsTest.kt (保留) - 應用設定測試
├── AIRouterClientTest.kt (新增) - 通信客戶端測試
└── UI組件測試 (保留) - MessageBubble/Loading/Error

AI Router Service 測試 (計畫中):
├── AIRouterServiceTest.kt - 背景服務測試
├── AIEngineManagerTest.kt - 引擎管理測試  
├── ModelManagerTest.kt - 模型管理測試
├── RuntimeConfigTest.kt - 推論配置測試
└── AIRouterFacadeTest.kt - 介面層測試

整合測試 (計畫中):
├── UIToAIRouterIntegrationTest.kt - 端到端通信測試
├── AIRouterServiceLifecycleTest.kt - 服務生命週期測試
└── PerformanceTest.kt - IPC通信效能測試
```

---

## 🚀 **使用指南 (v2.0)**

### **快速開始**

#### **1. 創建聊天Fragment (重構版)**

```kotlin
class ChatFragment : BaseFragment() {
    private lateinit var binding: FragmentChatBinding
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: MessageAdapter
    private lateinit var aiRouterClient: AIRouterClient
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化AI Router Client
        aiRouterClient = AIRouterClientImpl(requireContext())
        viewModel = ChatViewModel(aiRouterClient)
    }
    
    override fun setupUI() {
        setupRecyclerView()
        setupInputField()
        setupAIRouterConnection()
    }
    
    override fun observeUIState() {
        // 觀察聊天狀態
        viewModel.messages.collectSafely { messages ->
            adapter.submitList(messages)
        }
        
        // 觀察AI Router連線狀態
        viewModel.aiRouterConnectionState.collectSafely { state ->
            updateConnectionIndicator(state)
        }
        
        // 觀察AI Router狀態訊息
        viewModel.aiRouterStatus.collectSafely { status ->
            binding.statusText.text = status
        }
    }
    
    private fun setupAIRouterConnection() {
        // 初始化連線
        viewLifecycleOwner.lifecycleScope.launch {
            val result = aiRouterClient.connect()
            if (result.isFailure) {
                showError("無法連接到AI引擎服務")
            }
        }
    }
    
    private fun updateConnectionIndicator(state: ChatViewModel.ConnectionState) {
        binding.connectionIndicator.apply {
            when (state) {
                ChatViewModel.ConnectionState.CONNECTED -> {
                    setImageResource(R.drawable.ic_connected)
                    setColorFilter(ContextCompat.getColor(context, R.color.success))
                }
                ChatViewModel.ConnectionState.CONNECTING -> {
                    setImageResource(R.drawable.ic_connecting)
                    setColorFilter(ContextCompat.getColor(context, R.color.warning))
                }
                ChatViewModel.ConnectionState.DISCONNECTED, 
                ChatViewModel.ConnectionState.ERROR -> {
                    setImageResource(R.drawable.ic_disconnected)
                    setColorFilter(ContextCompat.getColor(context, R.color.error))
                }
            }
        }
    }
}
```

#### **2. 使用AI Router Client**

```kotlin
class ChatViewModel(
    private val aiRouterClient: AIRouterClient
) : BaseViewModel() {
    
    fun sendMessage(text: String) = launchSafely {
        if (!validateInput(text.isNotBlank(), "訊息不能為空")) return@launchSafely
        
        // 添加用戶訊息
        addUserMessage(text)
        
        try {
            // 通過AI Router發送訊息
            aiRouterClient.sendMessage(text, currentSessionId).collect { response ->
                when (response.state) {
                    AIResponse.ResponseState.PROCESSING -> {
                        showTypingIndicator()
                    }
                    AIResponse.ResponseState.STREAMING -> {
                        updateAIResponse(response.text, isComplete = false)
                    }
                    AIResponse.ResponseState.COMPLETED -> {
                        updateAIResponse(response.text, isComplete = true)
                        hideTypingIndicator()
                    }
                    AIResponse.ResponseState.ERROR -> {
                        setError("AI處理失敗: ${response.text}")
                        hideTypingIndicator()
                    }
                }
            }
        } catch (e: Exception) {
            handleAIRouterError(e)
        }
    }
    
    private fun handleAIRouterError(error: Throwable) {
        when (error) {
            is AIRouterError.ConnectionError -> {
                setError("與AI引擎服務連線中斷")
                // 嘗試重新連線
                retryConnection()
            }
            is AIRouterError.ServiceError -> {
                setError("AI服務暫時無法使用: ${error.message}")
            }
            is AIRouterError.ModelError -> {
                setError("AI模型錯誤: ${error.message}")
            }
            else -> {
                setError("未知錯誤: ${error.message}")
            }
        }
    }
}
```

### **架構最佳實踐 (v2.0)**

#### **1. AI Router 通信模式**

```kotlin
// ✅ 正確: 使用統一的Client介面
class ChatViewModel(private val aiRouterClient: AIRouterClient) {
    fun sendMessage(text: String) = launchSafely {
        aiRouterClient.sendMessage(text).collect { response ->
            handleAIResponse(response)
        }
    }
}

// ❌ 錯誤: 直接調用AI引擎 (舊架構)
class ChatViewModel(private val aiEngine: AIEngine) {
    fun sendMessage(text: String) = launchSafely {
        val response = aiEngine.generateText(text) // 不再使用
        handleResponse(response)
    }
}
```

#### **2. 錯誤處理模式**

```kotlin
// AI Router 專用錯誤處理
override fun handleError(throwable: Throwable) {
    when (throwable) {
        is AIRouterError.ConnectionError -> {
            setError("AI引擎服務連線失敗，請檢查服務狀態")
            // 嘗試重新連線
            retryConnection()
        }
        is AIRouterError.ServiceError -> {
            setError("AI服務暫時無法使用: ${throwable.message}")
        }
        is AIRouterError.EngineError -> {
            setError("AI引擎錯誤，正在切換到備用引擎...")
            // AI Router會自動Fallback
        }
        else -> super.handleError(throwable)
    }
}
```

#### **3. 生命週期管理**

```kotlin
// Fragment中的AI Router連線管理
override fun onStart() {
    super.onStart()
    // 連接AI Router
    viewLifecycleOwner.lifecycleScope.launch {
        aiRouterClient.connect()
    }
}

override fun onStop() {
    super.onStop()
    // 斷開連線但保持服務運行
    viewLifecycleOwner.lifecycleScope.launch {
        aiRouterClient.disconnect()
    }
}
```

---

## 📝 **API 架構變更總結**

### **已移除的API (移至AI Router)**
- ❌ **ModelManager.kt** → AI Router Service
- ❌ **RuntimeSettingsViewModel.kt** → AI Router Management
- ❌ **所有AI引擎直接調用** → 通過AIRouterClient
- ❌ **模型下載和管理功能** → AI Router Service
- ❌ **語音識別直接調用** → AI Router Service

### **新增的API**
- ✅ **AIRouterClient.kt** - UI ↔ AI Router 通信介面
- ✅ **ConnectionState** - AI Router 連線狀態管理
- ✅ **AIRequest/AIResponse** - 標準化請求回應格式
- ✅ **AIRouterError** - 專用錯誤處理體系

### **保留並強化的API**
- ✅ **BaseFragment/BaseViewModel** - 基礎架構不變
- ✅ **MessageBubbleView** - UI組件保持不變
- ✅ **ChatMessage** - 簡化但保留核心功能
- ✅ **AppSettings** - 專注於UI層偏好設定

---

*最後更新: 2024-12-19*  
*架構版本: v2.0 (AI Router 獨立架構)*  
*實作狀態: UI Layer API 92%完成, AI Router API 0%待設計*  
*重構重點: ChatViewModel移除AI引擎管理 → 實作AIRouterClient通信*  
*下一步: 完成AI Router通信協議設計 → 實作Service端AIRouterFacade*

## Introduction

This document outlines the API specification for client applications integrating with the BreezeApp AI Router Service. It provides comprehensive guidance on how to properly implement the client-side components for secure and robust communication with the AI Router.

> **NEW**: We have implemented a reference client application (`breeze-app-router-client`) that demonstrates all the concepts described in this document. Developers are encouraged to review this implementation for practical examples of best practices.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Integration Options](#integration-options)
- [Reference Implementation](#reference-implementation)
- [API Components](#api-components)
  - [Repository Interface](#repository-interface)
  - [Repository Implementation](#repository-implementation)
  - [Use Cases](#use-cases)
  - [ViewModel Integration](#viewmodel-integration)
- [Error Handling](#error-handling)
- [Threading](#threading)
- [Security](#security)
- [Examples](#examples)
- [Troubleshooting](#troubleshooting)

## Architecture Overview

The client-side architecture follows MVVM with Clean Architecture principles:

```
UI (Activity/Fragment) → ViewModel → UseCase → Repository → AIRouterService (AIDL)
```

All communication with the AI Router Service happens through the Repository layer, which encapsulates the AIDL binding and communication details.

## Integration Options

There are two primary ways to integrate with the BreezeApp AI Router Service:

1. **Module Dependency** (recommended for developers within the BreezeApp ecosystem):
   - Include the `shared-contracts` module as a dependency
   - Implement the repository pattern as described in this document

2. **Contract Copying** (for standalone development):
   - Copy the necessary AIDL files and Parcelable data models
   - Ensure the package structure matches exactly

> **Note**: In the future, we plan to publish the `shared-contracts` module as a standalone dependency via Maven/JitPack to simplify integration.

## Reference Implementation

The `breeze-app-router-client` module serves as a comprehensive reference implementation that demonstrates best practices for integrating with the AI Router Service. Key features include:

- Complete MVVM architecture with separation of concerns
- Robust service connection with error handling
- Reactive UI updates using StateFlow
- Comprehensive testing utilities
- Diagnostic tools for troubleshooting

Developers should review this implementation as a starting point for their own integration. The code is thoroughly documented with KDoc comments explaining the rationale behind design decisions.

### Key Components in the Reference Implementation

- **MainViewModel**: Demonstrates proper service binding, state management, and error handling
- **UiState**: Shows how to structure UI state for reactive updates
- **AIRouterTester**: Provides utilities for testing and validating the connection
- **test_connection.sh**: A diagnostic script for troubleshooting connection issues

## API Components

### Repository Interface

```kotlin
interface AIRouterRepository {
    val connectionState: Flow<ConnectionState>
    val responses: Flow<AIResponse>
    
    suspend fun connect()
    suspend fun disconnect()
    suspend fun initialize(config: Configuration): Boolean
    suspend fun sendMessage(request: AIRequest): Boolean
    suspend fun cancelRequest(requestId: String): Boolean
}
```

### Repository Implementation

```kotlin
class AIRouterRepositoryImpl(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : AIRouterRepository {
    // Implementation details as shown in breeze-app-router-client/MainViewModel.kt
}
```

### Use Cases

```kotlin
class ConnectToRouterUseCase(private val repository: AIRouterRepository) {
    suspend operator fun invoke() = repository.connect()
}

class SendMessageUseCase(private val repository: AIRouterRepository) {
    suspend operator fun invoke(request: AIRequest) = repository.sendMessage(request)
}
```

### ViewModel Integration

```kotlin
class ChatViewModel(
    private val connectUseCase: ConnectToRouterUseCase,
    private val sendMessageUseCase: SendMessageUseCase
) : ViewModel() {
    // Implementation details as shown in breeze-app-router-client/MainViewModel.kt
}
```

## Error Handling

Robust error handling is critical for a good user experience. The reference implementation in `breeze-app-router-client` demonstrates comprehensive error handling strategies:

- Service connection failures (service not installed, wrong permissions)
- Remote exceptions during IPC calls
- Timeout handling
- Graceful degradation when features are unavailable

## Threading

All IPC calls should be performed off the main thread to prevent ANRs (Application Not Responding errors). The reference implementation shows how to properly use Kotlin Coroutines with `Dispatchers.IO` for this purpose.

## Security

The AI Router Service is protected by a signature-level permission. Your application must:

1. Declare the permission in the manifest
2. Be signed with the same key as the service (for production use)

## Examples

### Basic Connection Example

```kotlin
// See breeze-app-router-client/MainViewModel.kt for a complete implementation
private fun bindToService() {
    val intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService")
    intent.`package` = "com.mtkresearch.breezeapp.router"
    
    // Try to bind to the service
    val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    
    if (!bound) {
        // Try fallback to debug version
        intent.`package` = "com.mtkresearch.breezeapp.router.debug"
        val debugBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        
        if (!debugBound) {
            // Handle connection failure
        }
    }
}
```

### Sending a Request

```kotlin
// See breeze-app-router-client/MainViewModel.kt for a complete implementation
suspend fun sendTextMessage(text: String) = withContext(Dispatchers.IO) {
    try {
        val request = AIRequest(
            id = UUID.randomUUID().toString(),
            type = AIRequest.TYPE_TEXT,
            text = text
        )
        
        service?.sendMessage(request)
        // Handle response through listener
    } catch (e: RemoteException) {
        // Handle error
    }
}
```

## Troubleshooting

For common integration issues, please refer to the troubleshooting guide in the `breeze-app-router-client` README. The module also includes a diagnostic script (`test_connection.sh`) that can help identify connection problems.

For more detailed examples and implementation guidance, please review the reference implementation in the `breeze-app-router-client` module.
