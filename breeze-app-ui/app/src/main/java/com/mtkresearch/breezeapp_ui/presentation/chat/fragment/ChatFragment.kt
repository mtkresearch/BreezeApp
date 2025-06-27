package com.mtkresearch.breezeapp_ui.presentation.chat.fragment

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mtkresearch.breezeapp_ui.databinding.FragmentChatBinding
import com.mtkresearch.breezeapp_ui.presentation.common.base.BaseFragment
import com.mtkresearch.breezeapp_ui.presentation.chat.adapter.MessageAdapter
import com.mtkresearch.breezeapp_ui.domain.model.ChatMessage
import com.mtkresearch.breezeapp_ui.presentation.chat.viewmodel.ChatViewModel
import com.mtkresearch.breezeapp_ui.domain.model.router.ConnectionState
import com.mtkresearch.breezeapp_ui.core.utils.ErrorType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 聊天Fragment (v2.1 - Hilt + 本地歷史紀錄)
 *
 * 功能重構:
 * ✅ Hilt 注入: ViewModel 由 Hilt 自動注入，移除了手動工廠。
 * ✅ 狀態驅動: 現在由 ViewModel 中的 `sessionId` 驅動，可載入歷史紀錄。
 * ✅ 架構對齊: 方法呼叫與重構後的 ViewModel 保持一致。
 */
@AndroidEntryPoint
class ChatFragment : BaseFragment(), MessageAdapter.MessageInteractionListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    // ViewModel 由 Hilt 注入
    private val viewModel: ChatViewModel by viewModels()
    
    private lateinit var messageAdapter: MessageAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 接收傳入的 sessionId，如果沒有則為 null (代表新聊天)
        val sessionId = arguments?.getString("session_id")
        viewModel.loadSession(sessionId)
    }

    override fun setupUI() {
        setupRecyclerView()
        setupInputSection()
        setupButtons()
        setupErrorAndLoadingViews()
        setupKeyboardListener()
        setupAIRouterConnectionIndicator()
    }

    override fun observeUIState() {
        // 觀察基礎UI狀態
        viewModel.uiState.collectSafely { state ->
            when (state.state) {
                com.mtkresearch.breezeapp_ui.presentation.common.base.UiState.LOADING -> {
                    binding.loadingView.show(state.message)
                }
                com.mtkresearch.breezeapp_ui.presentation.common.base.UiState.ERROR -> {
                    binding.loadingView.hide()
                    binding.errorView.showError(
                        type = ErrorType.AI_PROCESSING,
                        message = state.message,
                        showRetry = true
                    )
                }
                com.mtkresearch.breezeapp_ui.presentation.common.base.UiState.SUCCESS -> {
                    binding.loadingView.hide()
                    binding.errorView.hide()
                    if (state.message.isNotEmpty()) {
                        showSuccess(state.message)
                    }
                }
                com.mtkresearch.breezeapp_ui.presentation.common.base.UiState.IDLE -> {
                    binding.loadingView.hide()
                    binding.errorView.hide()
                }
            }
        }

        // 觀察聊天訊息
        viewModel.messages.collectSafely { messages ->
            messageAdapter.submitList(messages) {
                // 訊息更新後自動滾動到最新
                if (messages.isNotEmpty()) {
                    binding.recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
                }
            }
        }

        // 觀察輸入文字
        viewModel.inputText.collectSafely { text ->
            if (binding.editTextMessage.text.toString() != text) {
                binding.editTextMessage.setText(text)
                binding.editTextMessage.setSelection(text.length)
            }
        }

        // 觀察發送按鈕狀態
        viewModel.canSendMessage.collectSafely { canSend ->
            binding.buttonSend.isEnabled = canSend
            binding.buttonSend.alpha = if (canSend) 1.0f else 0.5f
        }

        // 觀察AI回應狀態
        viewModel.isAIResponding.collectSafely { isResponding ->
            binding.textViewAIStatus.visibility = if (isResponding) View.VISIBLE else View.GONE
            binding.textViewAIStatus.text = if (isResponding) "AI正在思考和回應中..." else ""
        }

        // 觀察 AI Router 連線狀態
        viewModel.aiRouterConnectionState.collectSafely { state ->
            updateConnectionIndicator(state)
        }

        // 觀察 AI Router 狀態訊息 (使用 AI 狀態指示器顯示)
        viewModel.aiRouterStatus.collectSafely { status ->
            binding.textViewAIStatus.text = status
            binding.textViewAIStatus.visibility = if (status.isNotBlank()) View.VISIBLE else View.GONE
        }


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 設置RecyclerView
     */
    private fun setupRecyclerView() {
        messageAdapter = MessageAdapter().apply {
            setMessageInteractionListener(this@ChatFragment)
        }

        binding.recyclerViewMessages.apply {
            adapter = messageAdapter
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true // 從底部開始堆疊，新訊息出現在底部
            }
            
            // 設置項目動畫，讓新訊息添加有流暢動畫
            itemAnimator?.addDuration = 300
            itemAnimator?.removeDuration = 300
        }
    }

    /**
     * 設置輸入區域
     */
    private fun setupInputSection() {
        // 輸入框文字變化監聽
        binding.editTextMessage.addTextChangedListener { editable ->
            val text = editable?.toString() ?: ""
            viewModel.updateInputText(text)
        }

        // 輸入框焦點變化
        binding.editTextMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 當輸入框獲得焦點時，延遲滾動到最新訊息，等待鍵盤動畫完成
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(300) // 給鍵盤動畫一些時間
                    scrollToLatestMessage()
                }
            }
        }

        // 設置輸入框提示文字
        binding.editTextMessage.hint = "輸入訊息..."
    }

    /**
     * 設置按鈕
     */
    private fun setupButtons() {
        // 發送按鈕
        binding.buttonSend.setOnClickListener {
            sendMessage()
        }

        // 語音按鈕 (簡化版 - 通過 AI Router 實現)
        binding.buttonVoice?.setOnClickListener {
            showSuccess("語音功能將通過 AI Router 實現")
        }

        // 清空聊天按鈕 (如果有的話)
        binding.buttonClearChat?.setOnClickListener {
            showClearChatConfirmation()
        }

        // 新會話按鈕 (如果有的話)
        binding.buttonNewChat?.setOnClickListener {
            viewModel.startNewSession()
        }
    }

    /**
     * 設置錯誤和載入視圖
     */
    private fun setupErrorAndLoadingViews() {
        // 錯誤視圖重試按鈕
        binding.errorView.setOnRetryClickListener {
            binding.errorView.hide()
            // 根據錯誤類型決定重試行為
            val lastMessage = messageAdapter.getLastMessage()
            if (lastMessage != null && lastMessage.author == com.mtkresearch.breezeapp_ui.domain.model.MessageAuthor.SYSTEM_ERROR) {
                // TODO: 應該要能夠重試上一條發送失敗的訊息
                // viewModel.retrySendMessage(lastMessage)
            } else {
                // 通用重試，重新連接 AI Router
                viewModel.connectToAIRouter()
            }
        }

        // 錯誤視圖關閉按鈕
        binding.errorView.setOnCloseClickListener {
            binding.errorView.hide()
            viewModel.clearError()
        }

        // 載入視圖取消按鈕
        binding.loadingView.setOnCancelClickListener {
            binding.loadingView.hide()
            // 停止當前操作 (暫無需要停止的操作)
        }
    }

    /**
     * 發送訊息
     */
    private fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()
        if (messageText.isNotEmpty()) {
            // 先隱藏鍵盤，提供即時反饋
            hideKeyboard()
            
            // 發送訊息到ViewModel
            viewModel.sendMessage(messageText)
            // 清空輸入框在ViewModel中處理
            
            // 發送訊息後滾動到最新位置
            viewLifecycleOwner.lifecycleScope.launch {
                delay(100)
                scrollToLatestMessage()
            }
        }
    }

    /**
     * 設置 AI Router 連線指示器 (暫時使用 AI 狀態文字顯示)
     */
    private fun setupAIRouterConnectionIndicator() {
        // AI 狀態文字點擊事件 - 檢查連線狀態
        binding.textViewAIStatus.setOnClickListener {
            viewModel.checkAIRouterStatus()
        }
        
        // 長按 AI 狀態文字 - 重新連線
        binding.textViewAIStatus.setOnLongClickListener {
            viewModel.connectToAIRouter()
            showSuccess("正在重新連接 AI Router...")
            true
        }
    }

    /**
     * 更新 AI Router 連線狀態指示器 (使用 AI 狀態文字顯示)
     */
    private fun updateConnectionIndicator(state: ConnectionState) {
        _binding?.textViewAIStatus?.apply {
            when (state) {
                ConnectionState.CONNECTED -> {
                    text = "AI Router 已連接"
                    setTextColor(ContextCompat.getColor(context, com.mtkresearch.breezeapp_ui.R.color.on_success))
                    setBackgroundColor(ContextCompat.getColor(context, com.mtkresearch.breezeapp_ui.R.color.success))
                    visibility = View.VISIBLE
                }
                ConnectionState.CONNECTING -> {
                    text = "正在連接 AI Router..."
                    setTextColor(ContextCompat.getColor(context, com.mtkresearch.breezeapp_ui.R.color.on_warning))
                    setBackgroundColor(ContextCompat.getColor(context, com.mtkresearch.breezeapp_ui.R.color.warning))
                    visibility = View.VISIBLE
                }
                ConnectionState.DISCONNECTED -> {
                    text = "AI Router 未連接 - 點擊重連"
                    setTextColor(ContextCompat.getColor(context, com.mtkresearch.breezeapp_ui.R.color.on_error))
                    setBackgroundColor(ContextCompat.getColor(context, com.mtkresearch.breezeapp_ui.R.color.error))
                    visibility = View.VISIBLE
                }
                ConnectionState.ERROR -> {
                    text = "AI Router 連接錯誤 - 長按重試"
                    setTextColor(ContextCompat.getColor(context, com.mtkresearch.breezeapp_ui.R.color.on_critical))
                    setBackgroundColor(ContextCompat.getColor(context, com.mtkresearch.breezeapp_ui.R.color.critical))
                    visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * 滾動到最新訊息
     */
    private fun scrollToLatestMessage() {
        _binding?.let { binding ->
            if (messageAdapter.itemCount > 0) {
                val lastPosition = messageAdapter.itemCount - 1
                val layoutManager = binding.recyclerViewMessages.layoutManager as? LinearLayoutManager
                
                if (layoutManager != null) {
                    val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    if (lastPosition - lastVisiblePosition <= 5) { // 增加緩衝區
                        binding.recyclerViewMessages.smoothScrollToPosition(lastPosition)
                    } else {
                        binding.recyclerViewMessages.scrollToPosition(lastPosition)
                    }
                } else {
                    binding.recyclerViewMessages.smoothScrollToPosition(lastPosition)
                }
            }
        }
    }

    /**
     * 顯示清空聊天確認對話框
     */
    private fun showClearChatConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("清空聊天記錄")
            .setMessage("確定要清空所有聊天記錄嗎？此操作無法復原。")
            .setPositiveButton("確定") { _, _ ->
                viewModel.clearChat()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 權限處理回調 (語音功能移至 AI Router)
     */
    override fun onPermissionsGranted(permissions: List<String>) {
        super.onPermissionsGranted(permissions)
        // 語音功能將通過 AI Router 處理
    }

    override fun onPermissionsDenied(permissions: List<String>) {
        super.onPermissionsDenied(permissions)
        // 語音功能將通過 AI Router 處理
    }

    // ============ MessageAdapter.MessageInteractionListener 實作 ============

    override fun onSpeakerClick(message: ChatMessage) {
        viewModel.handleMessageInteraction(
            ChatViewModel.MessageAction.SPEAKER_CLICK,
            message
        )
    }

    override fun onLikeClick(message: ChatMessage, isPositive: Boolean) {
        viewModel.handleMessageInteraction(
            ChatViewModel.MessageAction.LIKE_CLICK,
            message,
            isPositive
        )
        
        // 在頂部狀態欄顯示反饋訊息
        val feedbackText = if (isPositive) "已對此回應表示讚同" else "已對此回應表示不讚同"
        showFeedbackMessage(feedbackText)
    }

    override fun onRetryClick(message: ChatMessage) {
        viewModel.handleMessageInteraction(
            ChatViewModel.MessageAction.RETRY_CLICK,
            message
        )
    }

    override fun onMessageLongClick(message: ChatMessage): Boolean {
        viewModel.handleMessageInteraction(
            ChatViewModel.MessageAction.LONG_CLICK,
            message
        )
        
        // 顯示訊息操作菜單 (簡化實作)
        showMessageContextMenu(message)
        return true
    }

    override fun onImageClick(message: ChatMessage, imageUrl: String) {
        viewModel.handleMessageInteraction(
            ChatViewModel.MessageAction.IMAGE_CLICK,
            message,
            imageUrl
        )
    }

    /**
     * 顯示訊息上下文菜單
     */
    private fun showMessageContextMenu(message: ChatMessage) {
        val options = mutableListOf<String>()
        
        // 添加複製選項
        options.add("複製文字")
        
        // 如果是AI訊息，添加重新生成選項
        if (!message.isFromUser) {
            options.add("重新生成回應")
        }
        
        // 添加分享選項
        options.add("分享訊息")

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("訊息操作")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> copyMessageToClipboard(message)
                    1 -> {
                        if (!message.isFromUser) {
                            viewModel.retryLastMessage()
                        }
                    }
                    2 -> shareMessage(message)
                }
            }
            .show()
    }

    /**
     * 複製訊息到剪貼簿
     */
    private fun copyMessageToClipboard(message: ChatMessage) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("訊息", message.text)
        clipboard.setPrimaryClip(clip)
        showSuccess("訊息已複製到剪貼簿")
    }

    /**
     * 分享訊息
     */
    private fun shareMessage(message: ChatMessage) {
        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, message.text)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "來自BreezeApp的對話")
        }
        startActivity(android.content.Intent.createChooser(shareIntent, "分享訊息"))
    }

    /**
     * 顯示反饋訊息在頂部狀態欄
     */
    private fun showFeedbackMessage(message: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            _binding?.textViewAIStatus?.apply {
                text = message
                visibility = View.VISIBLE
            }
            delay(3000)
            _binding?.textViewAIStatus?.let {
                // 再次檢查文字是為了防止快速連續的訊息覆蓋舊的隱藏任務
                if (it.text == message) {
                    it.visibility = View.GONE
                }
            }
        }
    }

    /**
     * 重寫showSuccess方法，讓成功訊息也顯示在頂部
     */
    override fun showSuccess(message: String) {
        showFeedbackMessage(message)
    }

    /**
     * 設置鍵盤監聽器
     */
    private fun setupKeyboardListener() {
        _binding?.let { binding ->
            ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
                val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom

                binding.root.setPadding(0, 0, 0, if (imeVisible) imeHeight else 0)

                if (imeVisible) {
                    // 延遲滾動確保佈局調整完成
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(100)
                        scrollToLatestMessage()
                    }
                }
                insets
            }
        }
    }

    /**
     * 處理返回按鈕事件
     * 
     * @return true 如果Fragment處理了返回事件，false 如果應該由Activity處理
     */
    fun onBackPressed(): Boolean {
        // 如果輸入框有內容，清空內容
        if (binding.editTextMessage.text.isNotEmpty()) {
            binding.editTextMessage.text.clear()
            return true
        }
        
        // 如果有錯誤顯示，隱藏錯誤
        if (binding.errorView.visibility == View.VISIBLE) {
            binding.errorView.hide()
            return true
        }
        
        // 其他情況讓Activity處理（返回主頁面）
        return false
    }

    /**
     * 處理點擊鍵盤外區域收起鍵盤
     * 
     * @param event 觸摸事件
     */
    fun handleTouchOutsideKeyboard(event: MotionEvent) {
        // 檢查輸入框是否有焦點（即鍵盤是否顯示）
        if (!binding.editTextMessage.hasFocus()) {
            return
        }
        
        // 獲取目前焦點的view
        val view = activity?.currentFocus
        if (view != null) {
            val rect = Rect()
            view.getGlobalVisibleRect(rect)
            if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                hideKeyboard()
            }
        }
    }

    /**
     * 隱藏軟鍵盤
     */
    private fun hideKeyboard() {
        _binding?.let {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.root.windowToken, 0)
            it.editTextMessage.clearFocus()
        }
    }

    /**
     * 處理訊息互動
     * @param message 進行互動的訊息
     */
    override fun onMessageInteraction(message: ChatMessage) {
        // 根據訊息類型執行不同操作
        // 例如：重試、複製、朗讀等
        if (message.author == com.mtkresearch.breezeapp_ui.domain.model.MessageAuthor.SYSTEM_ERROR) {
            // viewModel.retrySendMessage(message)
        }
    }

    companion object {
        /**
         * 創建新的ChatFragment實例
         */
        fun newInstance(): ChatFragment {
            return ChatFragment()
        }
        
        /**
         * Fragment標籤
         */
        const val TAG = "ChatFragment"
    }
} 