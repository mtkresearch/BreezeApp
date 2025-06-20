# BreezeApp Kotlin API Documentation

## 📋 **文件概述**

本文件提供BreezeApp Kotlin重構版本的完整API說明，涵蓋已實作的所有組件、功能和使用方式。此文件將隨著重構進度持續更新，幫助開發者了解目前架構的功能支援狀況。

**當前實作狀態**: Phase 1.3 完成 + Home Module (78% Phase 1進度, 23% 整體進度)  
**最後更新**: 2024-12-19  
**覆蓋範圍**: Presentation Layer - Base Classes, UI Components, Chat Module & Home Module

---

## 🏗️ **架構概覽**

### **已實作層級**
- ✅ **Presentation Layer**: Base Classes, Common UI Components & Chat Module
- ⏳ **Domain Layer**: 待實作 (UseCase, Repository Interface, Domain Models)
- ⏳ **Data Layer**: 待實作 (Repository Implementation, Data Sources)
- ⏳ **AI Engine Layer**: 待實作 (Engine Management, Backend Strategy)
- ⏳ **Runtime Layer**: 待實作 (Native Integration, Model Loading)

### **設計模式**
- **MVVM**: Model-View-ViewModel with StateFlow
- **Repository Pattern**: 抽象數據存取層 (待實作)
- **Strategy Pattern**: Backend選擇策略 (待實作)
- **Factory Pattern**: AI引擎建立 (待實作)

---

## 📦 **已實作組件 API Reference**

## 1. Base Classes (基礎類別)

### 1.1 BaseFragment

**檔案位置**: `presentation/common/base/BaseFragment.kt`  
**繼承**: `Fragment`  
**功能**: 統一Fragment生命週期管理、權限處理、錯誤處理

#### **抽象方法**
```kotlin
abstract fun setupUI()
```
- **用途**: 子類別必須實作此方法來初始化UI組件
- **呼叫時機**: `onViewCreated` 之後

#### **權限管理 API**
```kotlin
// 檢查權限
fun hasPermission(permission: String): Boolean
fun hasPermissions(permissions: Array<String>): Boolean

// 請求權限
fun requestPermission(permission: String)
fun requestPermissions(permissions: Array<String>)

// 權限回調 (可覆寫)
open fun onPermissionsResult(permissions: Map<String, Boolean>)
open fun onPermissionsDenied(permissions: List<String>)
open fun onPermissionsGranted(permissions: List<String>)
```

#### **UI狀態管理 API**
```kotlin
// 載入狀態
open fun showLoading()
open fun hideLoading()

// 錯誤處理
open fun showError(message: String, action: (() -> Unit)? = null)
open fun showSuccess(message: String)
```

#### **Flow收集 API**
```kotlin
fun <T> Flow<T>.collectSafely(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    action: (T) -> Unit
)
```
- **特色**: 自動處理Fragment生命週期，防止記憶體洩漏
- **用途**: 安全收集ViewModel的StateFlow數據

#### **常用權限常數**
```kotlin
companion object {
    const val CAMERA_PERMISSION = Manifest.permission.CAMERA
    const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO
    const val READ_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.READ_EXTERNAL_STORAGE
    const val WRITE_EXTERNAL_STORAGE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE
    
    val MEDIA_PERMISSIONS = arrayOf(...)
    val AUDIO_PERMISSIONS = arrayOf(...)
}
```

#### **使用範例**
```kotlin
class ChatFragment : BaseFragment() {
    override fun setupUI() {
        // 初始化UI組件
        binding.sendButton.setOnClickListener { sendMessage() }
    }
    
    override fun observeUIState() {
        // 觀察ViewModel狀態
        viewModel.uiState.collectSafely { state ->
            when (state.state) {
                UiState.LOADING -> showLoading()
                UiState.ERROR -> showError(state.message)
                UiState.SUCCESS -> hideLoading()
            }
        }
    }
    
    private fun requestAudioPermission() {
        if (!hasPermission(RECORD_AUDIO_PERMISSION)) {
            requestPermission(RECORD_AUDIO_PERMISSION)
        }
    }
}
```

---

### 1.2 BaseViewModel

**檔案位置**: `presentation/common/base/BaseViewModel.kt`  
**繼承**: `ViewModel`  
**功能**: 統一ViewModel狀態管理、協程處理、錯誤處理

#### **狀態管理枚舉**
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

#### **狀態管理 API**
```kotlin
// 狀態觀察
val uiState: StateFlow<BaseUiState>
val isLoading: StateFlow<Boolean>
val error: StateFlow<String?>
val successMessage: StateFlow<String?>

// 狀態設置 (protected)
protected fun setLoading(isLoading: Boolean)
protected fun setError(message: String, throwable: Throwable? = null)
protected fun setSuccess(message: String = "")
protected fun setIdle()

// 狀態清除 (public)
fun clearError()
fun clearSuccessMessage()
```

#### **協程執行 API**
```kotlin
// 安全協程執行
protected fun launchSafely(
    showLoading: Boolean = true,
    onError: ((Throwable) -> Unit)? = null,
    block: suspend () -> Unit
)

// 帶結果的協程執行
protected fun <T> launchWithResult(
    showLoading: Boolean = true,
    onSuccess: (T) -> Unit,
    onError: ((Throwable) -> Unit)? = null,
    block: suspend () -> T
)
```

#### **錯誤處理 API**
```kotlin
// 統一錯誤處理 (可覆寫)
protected open fun handleError(throwable: Throwable)

// 重試機制
protected fun retry(maxAttempts: Int = 3, block: suspend () -> Unit)

// 輸入驗證
protected fun validateInput(condition: Boolean, errorMessage: String): Boolean
```

#### **內建異常處理**
- `IllegalArgumentException` → "參數錯誤"
- `IllegalStateException` → "狀態錯誤"  
- `SecurityException` → "權限不足"
- `UnknownHostException` → "網路連接失敗"
- `SocketTimeoutException` → "網路請求超時"
- `IOException` → "網路錯誤"

#### **使用範例**
```kotlin
class ChatViewModel : BaseViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()
    
    fun sendMessage(text: String) {
        if (!validateInput(text.isNotBlank(), "訊息不能為空")) return
        
        launchSafely {
            val response = aiRepository.generateResponse(text)
            _messages.value = _messages.value + response
            setSuccess("訊息發送成功")
        }
    }
    
    fun loadHistory() {
        launchWithResult(
            onSuccess = { history ->
                _messages.value = history
            }
        ) {
            chatRepository.getHistory()
        }
    }
}
```

---

### 1.3 BaseAdapter

**檔案位置**: `presentation/common/base/BaseAdapter.kt`  
**繼承**: `ListAdapter<T, VH>`  
**功能**: 統一RecyclerView適配器、DiffUtil、點擊處理

#### **ViewHolder基礎類別**
```kotlin
abstract class BaseViewHolder<T>(itemView: View) : RecyclerView.ViewHolder(itemView) {
    abstract fun bind(item: T, position: Int)
    open fun bind(item: T, position: Int, payloads: List<Any>)
    open fun onViewRecycled()
}
```

#### **點擊監聽器介面**
```kotlin
interface OnItemClickListener<T> {
    fun onItemClick(item: T, position: Int, view: View)
    fun onItemLongClick(item: T, position: Int, view: View): Boolean = false
}
```

#### **適配器API**
```kotlin
// 點擊監聽器設置
fun setOnItemClickListener(listener: OnItemClickListener<T>?)
fun setOnItemClickListener(onClick: (item: T, position: Int, view: View) -> Unit)
fun setOnItemClickListener(
    onClick: (item: T, position: Int, view: View) -> Unit,
    onLongClick: ((item: T, position: Int, view: View) -> Boolean)? = null
)

// 數據操作
fun getItemAt(position: Int): T?
fun isEmpty(): Boolean
fun isNotEmpty(): Boolean
fun getFirstItem(): T?
fun getLastItem(): T?
fun findPosition(predicate: (T) -> Boolean): Int
fun findItem(predicate: (T) -> Boolean): T?

// 設置
var isClickAnimationEnabled: Boolean
```

#### **簡化DiffCallback**
```kotlin
class SimpleDiffCallback<T>(
    private val areItemsSame: (oldItem: T, newItem: T) -> Boolean,
    private val areContentsSame: (oldItem: T, newItem: T) -> Boolean = { old, new -> old == new }
) : DiffUtil.ItemCallback<T>()
```

#### **使用範例**
```kotlin
data class ChatMessage(val id: String, val text: String, val isUser: Boolean)

class MessageViewHolder(private val binding: ItemMessageBinding) : BaseViewHolder<ChatMessage>(binding.root) {
    override fun bind(item: ChatMessage, position: Int) {
        binding.messageText.text = item.text
        binding.messageBubble.setMessage(item.text, 
            if (item.isUser) MessageType.USER else MessageType.AI)
    }
}

class MessageAdapter : BaseAdapter<ChatMessage, MessageViewHolder>(
    SimpleDiffCallback(
        areItemsSame = { old, new -> old.id == new.id }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }
}

// Fragment中使用
messageAdapter.setOnItemClickListener { message, position, view ->
    showMessageDetails(message)
}
```

---

## 2. UI Components (UI組件)

### 2.1 MessageBubbleView

**檔案位置**: `presentation/common/widget/MessageBubbleView.kt`  
**繼承**: `LinearLayout`  
**功能**: 可重複使用的訊息氣泡UI組件

#### **訊息類型**
```kotlin
enum class MessageType {
    USER,    // 用戶訊息：右對齊，橘色背景
    AI,      // AI訊息：左對齊，白色背景
    SYSTEM   // 系統訊息：居中，灰色背景
}
```

#### **訊息狀態**
```kotlin
enum class MessageState {
    NORMAL,   // 正常狀態
    LOADING,  // 載入中
    ERROR,    // 錯誤狀態
    TYPING    // 正在輸入 (AI專用)
}
```

#### **主要API**
```kotlin
// 設置訊息
fun setMessage(
    text: String,
    type: MessageType = MessageType.USER,
    state: MessageState = MessageState.NORMAL,
    showButtons: Boolean = false,
    imageUrl: String? = null
)

// 更新狀態
fun updateState(state: MessageState)
fun showTypingIndicator()
fun hideTypingIndicator()

// 回調設置
fun setOnSpeakerClickListener(listener: (() -> Unit)?)
fun setOnLikeClickListener(listener: ((isPositive: Boolean) -> Unit)?)
fun setOnRetryClickListener(listener: (() -> Unit)?)
```

#### **XML屬性支援**
```xml
<com.mtkresearch.breezeapp.presentation.common.widget.MessageBubbleView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:messageType="ai"
    app:messageState="normal"
    app:showButtons="true" />
```

#### **使用範例**
```kotlin
// 顯示用戶訊息
messageBubbleView.setMessage(
    text = "Hello, AI assistant!",
    type = MessageType.USER,
    showButtons = false
)

// 顯示AI回應載入中
messageBubbleView.setMessage(
    text = "思考中...",
    type = MessageType.AI,
    state = MessageState.LOADING,
    showButtons = true
)

// 設置互動回調
messageBubbleView.setOnSpeakerClickListener {
    textToSpeech.speak(messageText)
}

messageBubbleView.setOnLikeClickListener { isPositive ->
    if (isPositive) {
        analytics.trackPositiveFeedback()
    } else {
        analytics.trackNegativeFeedback()
    }
}
```

---

### 2.2 LoadingView

**檔案位置**: `presentation/common/widget/LoadingView.kt`  
**繼承**: `LinearLayout`  
**功能**: 可重複使用的載入狀態UI組件

#### **載入樣式**
```kotlin
enum class LoadingStyle {
    CIRCULAR,     // 圓形進度指示器
    HORIZONTAL,   // 橫條進度指示器
    DOTS,         // 點動畫
    SPINNER       // 旋轉器
}
```

#### **載入大小**
```kotlin
enum class LoadingSize {
    SMALL,    // 小尺寸 (24dp)
    MEDIUM,   // 中等尺寸 (48dp)
    LARGE     // 大尺寸 (72dp)
}
```

#### **主要API**
```kotlin
// 顯示載入
fun show(
    message: String = context.getString(R.string.loading),
    subtitle: String = "",
    showCancel: Boolean = false,
    style: LoadingStyle = LoadingStyle.CIRCULAR,
    size: LoadingSize = LoadingSize.MEDIUM
)

// 控制顯示
fun hide()
fun toggle()

// 更新內容
fun updateMessage(message: String, subtitle: String = "")

// 狀態查詢
fun isShowing(): Boolean

// 回調設置
fun setOnCancelClickListener(listener: (() -> Unit)?)
```

#### **XML屬性支援**
```xml
<com.mtkresearch.breezeapp.presentation.common.widget.LoadingView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:loadingStyle="circular"
    app:loadingSize="medium"
    app:loadingMessage="載入中..."
    app:showCancel="true" />
```

#### **使用範例**
```kotlin
// 顯示模型載入
loadingView.show(
    message = "載入AI模型中...",
    subtitle = "首次載入需要較長時間",
    showCancel = true,
    style = LoadingStyle.CIRCULAR,
    size = LoadingSize.LARGE
)

// 與ViewModel整合
viewModel.isLoading.collectSafely { isLoading ->
    if (isLoading) {
        loadingView.show("處理中...")
    } else {
        loadingView.hide()
    }
}

// 設置取消回調
loadingView.setOnCancelClickListener {
    viewModel.cancelOperation()
}
```

---

### 2.3 ErrorView

**檔案位置**: `presentation/common/widget/ErrorView.kt`  
**繼承**: `LinearLayout`  
**功能**: 可重複使用的錯誤狀態UI組件

#### **錯誤類型**
```kotlin
enum class ErrorType {
    NETWORK,        // 網路錯誤
    SERVER,         // 服務器錯誤  
    VALIDATION,     // 驗證錯誤
    PERMISSION,     // 權限錯誤
    MODEL_LOADING,  // 模型載入錯誤
    AI_PROCESSING,  // AI處理錯誤
    FILE_ACCESS,    // 檔案存取錯誤
    UNKNOWN         // 未知錯誤
}
```

#### **錯誤嚴重程度**
```kotlin
enum class ErrorSeverity {
    INFO,     // 資訊 (藍色)
    WARNING,  // 警告 (橘色)
    ERROR,    // 錯誤 (紅色)
    CRITICAL  // 嚴重 (深紅色)
}
```

#### **主要API**
```kotlin
// 顯示錯誤
fun showError(
    type: ErrorType = ErrorType.UNKNOWN,
    severity: ErrorSeverity = ErrorSeverity.ERROR,
    title: String = "",
    message: String = "",
    showRetry: Boolean = false,
    showClose: Boolean = true,
    customActionText: String = ""
)

// 控制顯示
fun hide()
fun toggle()

// 快速方法
fun showNetworkError(showRetry: Boolean = true)
fun showServerError(showRetry: Boolean = true)
fun showValidationError(message: String)
fun showPermissionError()
fun showAIError(showRetry: Boolean = true)

// 回調設置
fun setOnRetryClickListener(listener: (() -> Unit)?)
fun setOnCloseClickListener(listener: (() -> Unit)?)
fun setOnCustomActionClickListener(listener: (() -> Unit)?)
```

#### **預設錯誤訊息**
每種錯誤類型都有預設的標題和訊息：
- **NETWORK**: "網路連線失敗" / "請檢查網路設定後重試"
- **SERVER**: "服務暫時無法使用" / "伺服器正在維護中，請稍後再試"
- **AI_PROCESSING**: "AI處理失敗" / "模型處理出現問題，請重試"
- **MODEL_LOADING**: "模型載入失敗" / "無法載入AI模型，請檢查儲存空間"

#### **XML屬性支援**
```xml
<com.mtkresearch.breezeapp.presentation.common.widget.ErrorView
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    app:errorType="network"
    app:errorSeverity="error"
    app:showRetry="true"
    app:showClose="true" />
```

#### **使用範例**
```kotlin
// 顯示網路錯誤
errorView.showNetworkError(showRetry = true)

// 顯示自定義錯誤
errorView.showError(
    type = ErrorType.AI_PROCESSING,
    severity = ErrorSeverity.ERROR,
    title = "AI處理失敗",
    message = "模型推理超時，請重試",
    showRetry = true,
    customActionText = "切換模型"
)

// 與ViewModel整合
viewModel.error.collectSafely { errorMessage ->
    if (errorMessage != null) {
        errorView.showError(
            type = ErrorType.AI_PROCESSING,
            message = errorMessage,
            showRetry = true
        )
    } else {
        errorView.hide()
    }
}

// 設置重試回調
errorView.setOnRetryClickListener {
    viewModel.retryLastOperation()
}
```

---

## 🎨 **資源系統**

### **顏色系統**
```xml
<!-- 基礎色彩 -->
<color name="primary">#FF6200EE</color>
<color name="primary_dark">#FF3700B3</color>
<color name="secondary">#FF03DAC6</color>

<!-- 訊息色彩 -->
<color name="ai_message_bg">#FFF5F5F5</color>
<color name="user_message_bg">#FF6200EE</color>
<color name="ai_message_text">#DE000000</color>
<color name="user_message_text">#FFFFFFFF</color>

<!-- 狀態色彩 -->
<color name="error">#FFF44336</color>
<color name="warning">#FFFF9800</color>
<color name="success">#FF4CAF50</color>
<color name="info">#FF2196F3</color>
```

### **尺寸系統**
```xml
<!-- 間距 -->
<dimen name="spacing_micro">4dp</dimen>
<dimen name="spacing_small">8dp</dimen>
<dimen name="spacing_medium">16dp</dimen>
<dimen name="spacing_large">24dp</dimen>
<dimen name="spacing_xlarge">32dp</dimen>

<!-- 組件尺寸 -->
<dimen name="message_bubble_padding">12dp</dimen>
<dimen name="message_bubble_radius">16dp</dimen>
<dimen name="message_bubble_max_width">280dp</dimen>
<dimen name="loading_circle_size">48dp</dimen>
<dimen name="error_button_height">40dp</dimen>
```

### **圖示系統**
**已提供圖示**:
- `ic_speaker` - 語音播放
- `ic_thumb_up` / `ic_thumb_down` - 點讚/點踩
- `ic_error` / `ic_warning` - 錯誤/警告
- `ic_close` - 關閉
- `ic_wifi_off` - 網路錯誤
- `ic_cloud_off` - 服務器錯誤
- `ic_smart_toy_off` - AI錯誤
- `ic_folder_off` - 檔案錯誤
- `ic_download_off` - 下載錯誤
- `ic_lock` - 權限錯誤

---

## 🧪 **測試指南**

### **已提供測試**
- `BaseViewModelTest.kt` - BaseViewModel完整單元測試 (13個測試案例)

### **測試覆蓋率**
| 組件 | 單元測試 | 整合測試 | UI測試 |
|------|----------|----------|--------|
| BaseViewModel | ✅ 95% | ⏳ 待補強 | N/A |
| BaseFragment | ⏳ 待實作 | ⏳ 待實作 | ⏳ 待實作 |
| BaseAdapter | ⏳ 待實作 | ⏳ 待實作 | ⏳ 待實作 |
| MessageBubbleView | ⏳ 待實作 | ⏳ 待實作 | ⏳ 待實作 |
| LoadingView | ⏳ 待實作 | ⏳ 待實作 | ⏳ 待實作 |
| ErrorView | ⏳ 待實作 | ⏳ 待實作 | ⏳ 待實作 |
| ChatViewModel | ⏳ 待實作 | ⏳ 待實作 | N/A |
| MessageAdapter | ⏳ 待實作 | ⏳ 待實作 | ⏳ 待實作 |
| ChatFragment | ⏳ 待實作 | ⏳ 待實作 | ⏳ 待實作 |

### **測試框架**
```kotlin
// 測試依賴
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
testImplementation 'androidx.arch.core:core-testing:2.2.0'

// UI測試依賴 (計畫中)
androidTestImplementation 'androidx.test.espresso:espresso-core:3.5.1'
androidTestImplementation 'androidx.fragment:fragment-testing:1.6.2'
```

---

## 🚀 **使用指南**

### **快速開始**

#### **1. 創建Fragment**
```kotlin
class MyFragment : BaseFragment() {
    private lateinit var binding: FragmentMyBinding
    private lateinit var viewModel: MyViewModel
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentMyBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun setupUI() {
        // 初始化UI組件
        setupRecyclerView()
        setupButtons()
    }
    
    override fun observeUIState() {
        // 觀察ViewModel狀態
        viewModel.uiState.collectSafely { state ->
            handleUIState(state)
        }
    }
}
```

#### **2. 創建ViewModel**
```kotlin
class MyViewModel : BaseViewModel() {
    private val _data = MutableStateFlow<List<Item>>(emptyList())
    val data: StateFlow<List<Item>> = _data.asStateFlow()
    
    fun loadData() {
        launchSafely {
            val result = repository.getData()
            _data.value = result
            setSuccess("載入完成")
        }
    }
}
```

#### **3. 創建RecyclerView Adapter**
```kotlin
class MyAdapter : BaseAdapter<Item, MyViewHolder>(
    SimpleDiffCallback(
        areItemsSame = { old, new -> old.id == new.id }
    )
) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        // 創建ViewHolder
    }
}
```

#### **4. 使用UI組件**
```kotlin
// 訊息氣泡
binding.messageBubble.setMessage("Hello", MessageType.AI, showButtons = true)

// 載入視圖
binding.loadingView.show("載入中...", showCancel = true)

// 錯誤視圖
binding.errorView.showNetworkError(showRetry = true)
```

---

## 3. Chat Module (聊天模組)

### 3.1 ChatMessage & ChatSession (臨時領域模型)

**檔案位置**: `presentation/chat/model/ChatMessage.kt` (34行)  
**性質**: 臨時實作，Phase 2將替換為正式Domain Model  
**功能**: 聊天訊息和會話的基本數據結構

#### **ChatMessage數據類別**
```kotlin
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val state: MessageState = MessageState.NORMAL,
    val imageUrl: String? = null
)
```

#### **ChatSession數據類別**
```kotlin
data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新對話",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### **訊息狀態**
```kotlin
enum class MessageState {
    NORMAL,   // 正常狀態
    SENDING,  // 發送中 
    LOADING,  // AI回應載入中
    ERROR,    // 發送/接收錯誤
    TYPING    // AI正在輸入
}
```

#### **使用範例**
```kotlin
// 創建用戶訊息
val userMessage = ChatMessage(
    text = "Hello AI!",
    isFromUser = true
)

// 創建AI回應 (載入中)
val aiMessage = ChatMessage(
    text = "思考中...",
    isFromUser = false,
    state = MessageState.LOADING
)

// 創建聊天會話
val session = ChatSession(
    title = "AI助手對話",
    messages = listOf(userMessage, aiMessage)
)
```

---

### 3.2 MessageAdapter

**檔案位置**: `presentation/chat/adapter/MessageAdapter.kt` (329行)  
**繼承**: `BaseAdapter<ChatMessage, MessageViewHolder>`  
**功能**: 聊天訊息列表的RecyclerView適配器

#### **互動監聽器介面**
```kotlin
interface MessageInteractionListener {
    fun onSpeakerClick(message: ChatMessage)
    fun onLikeClick(message: ChatMessage, isPositive: Boolean)
    fun onRetryClick(message: ChatMessage)
    fun onMessageLongClick(message: ChatMessage): Boolean
    fun onImageClick(message: ChatMessage, imageUrl: String)
}
```

#### **主要API**
```kotlin
// 設置互動監聽器  
fun setMessageInteractionListener(listener: MessageInteractionListener?)

// ViewHolder配置
MessageViewHolder.bind(item: ChatMessage, position: Int)
MessageViewHolder.bind(item: ChatMessage, position: Int, payloads: List<Any>)
MessageViewHolder.onViewRecycled()

// 數據操作 (繼承自BaseAdapter)
fun submitList(list: List<ChatMessage>?)
fun getItemAt(position: Int): ChatMessage?
fun isEmpty(): Boolean
fun isNotEmpty(): Boolean
fun getFirstItem(): ChatMessage?
fun getLastItem(): ChatMessage?
```

#### **特色功能**
- **自動樣式調整**: USER訊息右對齊，AI訊息左對齊
- **狀態指示器**: 載入、錯誤、打字狀態的視覺反饋
- **部分更新支援**: 使用payload進行高效更新
- **記憶體管理**: ViewHolder回收時自動清理監聽器

#### **使用範例**
```kotlin
class ChatFragment : BaseFragment(), MessageAdapter.MessageInteractionListener {
    private lateinit var messageAdapter: MessageAdapter
    
    override fun setupUI() {
        messageAdapter = MessageAdapter()
        messageAdapter.setInteractionListener(this)
        binding.recyclerView.adapter = messageAdapter
    }
    
    override fun onSpeakerClick(message: ChatMessage) {
        // 播放語音
        textToSpeech.speak(message.text)
    }
    
    override fun onLikeClick(message: ChatMessage, isPositive: Boolean) {
        // 處理點讚/點踩
        viewModel.rateFeedback(message.id, isPositive)
    }
    
    override fun onRetryClick(message: ChatMessage) {
        // 重試AI回應
        viewModel.retryAIResponse(message.id)
    }
}
```

---

### 3.3 ChatViewModel

**檔案位置**: `presentation/chat/viewmodel/ChatViewModel.kt` (426行)  
**繼承**: `BaseViewModel`  
**功能**: 聊天狀態管理、AI回應處理、會話管理

#### **主要狀態**
```kotlin
// 訊息列表
val messages: StateFlow<List<ChatMessage>>

// 輸入控制
val inputText: StateFlow<String>
val canSendMessage: StateFlow<Boolean>

// AI狀態
val isAIResponding: StateFlow<Boolean>

// 語音識別
val isListening: StateFlow<Boolean>

// 會話管理 (簡化版本)
private val _sessions = MutableStateFlow<List<ChatSession>>(emptyList())
val sessions: StateFlow<List<ChatSession>> = _sessions.asStateFlow()
```

#### **主要API**
```kotlin
// 訊息處理
fun sendMessage(text: String)
fun retryLastAIResponse()
fun updateInputText(text: String)

// 語音識別 (模擬實作)
fun startVoiceRecognition()
fun stopVoiceRecognition()

// 會話管理
fun createNewSession()
fun clearCurrentChat()

// 訊息互動 
fun handleMessageInteraction(action: MessageAction, message: ChatMessage, data: Any? = null)
```

#### **AI回應流程**
```kotlin
private suspend fun generateAIResponse(userMessage: String): String {
    // 模擬AI思考時間
    delay((1500 + random()).toLong())
    
    // 顯示打字狀態
    setTyping(true)
    delay(800)
    setTyping(false)
    
    // 返回模擬回應
    return "這是AI的模擬回應：$userMessage"
}
```

#### **使用範例**
```kotlin
class ChatFragment : BaseFragment() {
    private lateinit var viewModel: ChatViewModel
    
    override fun observeUIState() {
        // 觀察訊息列表
        viewModel.messages.collectSafely { messages ->
            messageAdapter.submitList(messages)
        }
        
        // 觀察輸入狀態
        viewModel.canSendMessage.collectSafely { canSend ->
            binding.sendButton.isEnabled = canSend
        }
        
        // 觀察AI狀態
        viewModel.isAIResponding.collectSafely { isResponding ->
            binding.aiStatusIndicator.isVisible = isResponding
        }
    }
    
    private fun sendMessage() {
        val text = binding.inputEditText.text.toString()
        if (text.isNotBlank()) {
            viewModel.sendMessage(text)
            binding.inputEditText.text.clear()
        }
    }
}
```

---

### 3.4 ChatFragment

**檔案位置**: `presentation/chat/fragment/ChatFragment.kt` (593行)  
**繼承**: `BaseFragment`  
**功能**: 主聊天介面，整合所有聊天相關功能

#### **核心功能**
- **訊息顯示**: 使用RecyclerView + MessageAdapter顯示對話記錄
- **輸入處理**: 文字輸入框 + 語音識別按鈕
- **狀態管理**: 整合ErrorView和LoadingView
- **權限處理**: 自動請求錄音權限
- **訊息互動**: 實作MessageInteractionListener

#### **UI架構**
```kotlin
// 主要UI組件
binding.recyclerViewMessages     // 訊息列表
binding.editTextMessage         // 文字輸入框
binding.buttonVoice            // 語音識別按鈕
binding.buttonSend            // 發送按鈕
binding.textViewAIStatus      // AI狀態指示器
binding.textViewVoiceStatus   // 語音狀態指示器
binding.errorView            // 錯誤狀態顯示
binding.loadingView          // 載入狀態顯示
binding.inputSection         // 輸入區域容器
```

#### **權限處理**
```kotlin
private fun checkVoicePermission(): Boolean {
    return hasPermission(RECORD_AUDIO_PERMISSION)
}

private fun requestVoicePermission() {
    requestPermission(RECORD_AUDIO_PERMISSION)
}

override fun onPermissionsGranted(permissions: List<String>) {
    if (RECORD_AUDIO_PERMISSION in permissions) {
        startVoiceRecognition()
    }
}
```

#### **訊息互動處理**
```kotlin
override fun onSpeakerClick(message: ChatMessage) {
    // TODO: 整合TTS引擎
    showSuccess("語音播放功能將在Phase 4實作")
}

override fun onLikeClick(message: ChatMessage, isPositive: Boolean) {
    viewModel.handleMessageInteraction(
        MessageInteractionType.FEEDBACK,
        message,
        isPositive
    )
    val action = if (isPositive) "點讚" else "點踩"
    showSuccess("已${action}此回應")
}

override fun onMessageLongClick(message: ChatMessage): Boolean {
    showMessageContextMenu(message)
    return true
}
```

#### **上下文菜單**
```kotlin
private fun showMessageContextMenu(message: ChatMessage) {
    val items = arrayOf("複製", "重新生成", "分享")
    AlertDialog.Builder(requireContext())
        .setItems(items) { _, which ->
            when (which) {
                0 -> copyToClipboard(message.text)
                1 -> viewModel.retryAIResponse(message.id)
                2 -> shareMessage(message.text)
            }
        }
        .show()
}
```

#### **佈局檔案**
**fragment_chat.xml**:
```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">
    
    <!-- 訊息列表 -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
    
    <!-- AI狀態指示器 -->
    <TextView
        android:id="@+id/aiStatusIndicator"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="AI正在回應中..."
        android:visibility="gone" />
    
    <!-- 輸入區域 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:padding="16dp">
        
        <EditText
            android:id="@+id/inputEditText"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="輸入訊息..." />
        
        <ImageButton
            android:id="@+id/voiceButton"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@drawable/ic_mic" />
        
        <ImageButton
            android:id="@+id/sendButton"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@drawable/ic_send" />
    </LinearLayout>
    
    <!-- 錯誤/載入視圖 -->
    <include layout="@layout/widget_error" />
    <include layout="@layout/widget_loading" />
    
</LinearLayout>
```

---

## 4. Home Module (主頁模組)

### 4.1 HomeFragment

**檔案位置**: `presentation/home/fragment/HomeFragment.kt` (105行)  
**繼承**: `Fragment`  
**功能**: 應用程式主頁面，提供功能導航入口

#### **核心功能**
- **歡迎區域**: 顯示應用歡迎訊息和介紹
- **功能導航**: 提供AI聊天、設定、下載管理的快速入口
- **響應式設計**: 支援不同螢幕尺寸的適配
- **Material Design**: 現代化的卡片式設計

#### **主要API**
```kotlin
// Fragment生命週期
fun onCreateView(): View
fun onViewCreated(view: View, savedInstanceState: Bundle?)
fun onDestroyView()

// UI設置
private fun setupWelcomeContent()
private fun setupNavigationButtons()

// 導航功能
private fun startChatActivity()
private fun showComingSoon(featureName: String)

// 靜態方法
companion object {
    fun newInstance(): HomeFragment
}
```

#### **UI架構**
```kotlin
// 主要UI組件
binding.welcomeTitle        // 主標題
binding.welcomeMessage      // 歡迎訊息
binding.welcomeSubtitle     // 副標題
binding.buttonChat         // AI聊天按鈕
binding.buttonSettings     // 設定按鈕
binding.buttonDownload     // 下載管理按鈕
```

#### **使用範例**
```kotlin
// 在MainActivity中使用
class MainActivity : AppCompatActivity() {
    private val homeFragment = HomeFragment()
    
    private fun showHomeFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, homeFragment)
            .commit()
    }
}
```

### 4.2 ChatActivity

**檔案位置**: `presentation/chat/ChatActivity.kt` (134行)  
**繼承**: `AppCompatActivity`  
**功能**: 獨立的聊天Activity，專注聊天體驗

#### **核心功能**
- **獨立Activity**: 與主Activity分離，提供專注的聊天體驗
- **自定義工具欄**: 支援返回主頁面導航
- **沉浸式界面**: Edge-to-Edge顯示，現代化視覺體驗
- **鍵盤適配**: 智能處理軟鍵盤顯示和隱藏
- **觸摸處理**: 點擊鍵盤外區域自動收起鍵盤

#### **主要API**
```kotlin
// Activity生命週期
fun onCreate(savedInstanceState: Bundle?)

// UI設置
private fun setupToolbar()
private fun setupEdgeToEdge()
private fun loadChatFragment()

// 事件處理
fun onOptionsItemSelected(item: MenuItem): Boolean
fun onBackPressed()
fun dispatchTouchEvent(ev: MotionEvent?): Boolean
```

#### **特色功能**
```kotlin
// 工具欄配置
supportActionBar?.apply {
    setDisplayHomeAsUpEnabled(true)
    setDisplayShowHomeEnabled(true)
    title = getString(R.string.chat_title)
    setHomeAsUpIndicator(R.drawable.ic_arrow_back)
}

// 鍵盤外點擊處理
override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
    if (ev?.action == MotionEvent.ACTION_DOWN) {
        val chatFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? ChatFragment
        chatFragment?.handleTouchOutsideKeyboard(ev)
    }
    return super.dispatchTouchEvent(ev)
}
```

### 4.3 MainActivity (更新版本)

**檔案位置**: `presentation/MainActivity.kt` (107行)  
**繼承**: `AppCompatActivity`  
**功能**: 主Activity，管理Fragment導航

#### **架構改進**
- **簡化設計**: 移除底部導航，採用卡片式導航
- **Fragment管理**: 統一的Fragment切換機制
- **主頁導向**: 預設顯示HomeFragment
- **生命週期優化**: 正確的Fragment隱藏/顯示管理

#### **主要API**
```kotlin
// Activity生命週期
fun onCreate(savedInstanceState: Bundle?)

// UI設置
private fun setupEdgeToEdge()

// Fragment管理
private fun showHomeFragment()
private fun switchFragment(fragment: Fragment, tag: String)

// 事件處理
@Deprecated("Deprecated in Java")
override fun onBackPressed()
```

---

## 🔄 **待實作功能**

### **Phase 1.4 - Settings Module (下一階段)**
- `SettingsFragment.kt` - 設定介面
- `SettingsViewModel.kt` - 設定狀態管理

### **Phase 1.4 - Settings Module (下一階段)**
- `SettingsFragment.kt` - 設定介面
- `SettingsViewModel.kt` - 設定狀態管理

**Phase 1.5 - Download Module**
- `DownloadFragment.kt` - 下載管理介面
- `DownloadViewModel.kt` - 下載狀態管理

### **Phase 2 - Domain Layer**
- Domain Models (ChatMessage, AIRequest, ModelConfig)
- Repository Interfaces
- Use Cases (SendMessage, LoadHistory, DownloadModel)

### **Phase 3 - Data Layer**
- Data Entities
- Local/Remote Data Sources
- Repository Implementations

### **Phase 4 - AI Engine Layer**
- AI Engine Manager
- Backend Strategies (CPU/NPU/GPU)
- Native Integration (JNI Bridge)

---

## 📋 **最佳實踐**

### **Fragment實作**
1. 繼承`BaseFragment`
2. 實作`setupUI()`方法
3. 覆寫`observeUIState()`觀察ViewModel
4. 使用`collectSafely()`安全收集Flow

### **ViewModel實作**
1. 繼承`BaseViewModel`
2. 使用`launchSafely()`執行協程
3. 使用狀態管理API更新UI狀態
4. 覆寫`handleError()`自定義錯誤處理

### **Adapter實作**
1. 繼承`BaseAdapter`
2. 使用`SimpleDiffCallback`簡化實作
3. 實作ViewHolder時繼承`BaseViewHolder`
4. 使用內建點擊處理API

### **UI組件使用**
1. 在XML中聲明或程式碼中動態創建
2. 使用高層API快速配置
3. 設置適當的回調函數
4. 與ViewModel狀態綁定

---

## 📚 **依賴資訊**

### **必要依賴**
```kotlin
// Kotlin Coroutines
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

// Lifecycle & ViewModel
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'

// Fragment & Activity
implementation 'androidx.fragment:fragment-ktx:1.6.2'
implementation 'androidx.activity:activity-ktx:1.8.2'

// RecyclerView & UI
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'com.google.android.material:material:1.11.0'
```

### **測試依賴**
```kotlin
testImplementation 'junit:junit:4.13.2'
testImplementation 'org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3'
testImplementation 'androidx.arch.core:core-testing:2.2.0'
```

---

## 🔄 **版本歷史**

### **v0.4.0 (2024-12-19) - Phase 1.3 完成 + Home Module**
- ✅ 新增 ChatMessage & ChatSession (34行)
- ✅ 新增 MessageAdapter (329行)
- ✅ 新增 ChatViewModel (426行)
- ✅ 新增 ChatFragment (593行)
- ✅ 新增 HomeFragment (105行)
- ✅ 新增 ChatActivity (134行)
- ✅ 更新 MainActivity (107行)
- ✅ 完整聊天功能實現
- ✅ 主頁面導航架構
- ✅ 鍵盤適配和觸摸處理

### **v0.3.0 (2024-12-19) - Phase 1.2 完成**
- ✅ 新增 MessageBubbleView (326行)
- ✅ 新增 LoadingView (309行)  
- ✅ 新增 ErrorView (381行)
- ✅ 完整資源系統 (顏色、尺寸、圖示、字串)
- ✅ XML屬性支援

### **v0.2.0 (2024-12-19) - Phase 1.1 完成**
- ✅ BaseFragment 基礎類別 (167行)
- ✅ BaseViewModel 基礎類別 (271行)
- ✅ BaseAdapter 基礎類別 (250行) 
- ✅ BaseViewModelTest 單元測試 (235行)
- ✅ 基礎架構建立

---

**📞 支援**: 如有問題或建議，請參考專案文件或提出Issue  
**📖 更多文件**: 參考 `docs/architecture/overview.md` 了解整體架構設計
