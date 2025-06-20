package com.mtkresearch.breezeapp_kotlin.presentation.chat.fragment

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
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.mtkresearch.breezeapp_kotlin.databinding.FragmentChatBinding
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseFragment
import com.mtkresearch.breezeapp_kotlin.presentation.chat.adapter.MessageAdapter
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp_kotlin.presentation.chat.viewmodel.ChatViewModel
import com.mtkresearch.breezeapp_kotlin.core.utils.ErrorType

/**
 * 聊天Fragment
 * 
 * 功能特色:
 * - 完整的聊天介面 (訊息列表、輸入框、語音按鈕)
 * - 整合MessageAdapter顯示訊息氣泡
 * - 支援語音識別和文字輸入
 * - 訊息互動功能 (語音播放、點讚、重試)
 * - 自動滾動到最新訊息
 * - 錯誤和載入狀態處理
 * 
 * 架構整合:
 * - 繼承BaseFragment，獲得統一的生命週期和錯誤處理
 * - 使用ChatViewModel進行狀態管理
 * - 通過MessageAdapter.MessageInteractionListener處理訊息互動
 */
class ChatFragment : BaseFragment(), MessageAdapter.MessageInteractionListener {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

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

    override fun setupUI() {
        setupRecyclerView()
        setupInputSection()
        setupButtons()
        setupErrorAndLoadingViews()
        setupKeyboardListener()
    }

    override fun observeUIState() {
        // 觀察基礎UI狀態
        viewModel.uiState.collectSafely { state ->
            when (state.state) {
                com.mtkresearch.breezeapp_kotlin.presentation.common.base.UiState.LOADING -> {
                    binding.loadingView.show(state.message)
                }
                com.mtkresearch.breezeapp_kotlin.presentation.common.base.UiState.ERROR -> {
                    binding.loadingView.hide()
                    binding.errorView.showError(
                        type = ErrorType.AI_PROCESSING,
                        message = state.message,
                        showRetry = true
                    )
                }
                com.mtkresearch.breezeapp_kotlin.presentation.common.base.UiState.SUCCESS -> {
                    binding.loadingView.hide()
                    binding.errorView.hide()
                    if (state.message.isNotEmpty()) {
                        showSuccess(state.message)
                    }
                }
                com.mtkresearch.breezeapp_kotlin.presentation.common.base.UiState.IDLE -> {
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

        // 觀察語音識別狀態
        viewModel.isListening.collectSafely { isListening ->
            updateVoiceButton(isListening)
            binding.textViewVoiceStatus.visibility = if (isListening) View.VISIBLE else View.GONE
            binding.textViewVoiceStatus.text = if (isListening) "正在聽取語音..." else ""
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
                binding.root.postDelayed({
                    scrollToLatestMessage()
                }, 300) // 給鍵盤動畫一些時間
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

        // 語音按鈕
        binding.buttonVoice.setOnClickListener {
            toggleVoiceRecognition()
        }

        // 清空聊天按鈕 (如果有的話)
        binding.buttonClearChat?.setOnClickListener {
            showClearChatConfirmation()
        }

        // 新會話按鈕 (如果有的話)
        binding.buttonNewChat?.setOnClickListener {
            viewModel.createNewSession()
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
            if (lastMessage != null && lastMessage.state == ChatMessage.MessageState.ERROR) {
                viewModel.retryLastAIResponse()
            } else {
                // 通用重試，重新載入歡迎訊息
                viewModel.clearChat()
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
            // 停止當前操作
            viewModel.stopVoiceRecognition()
        }
    }

    /**
     * 發送訊息
     */
    private fun sendMessage() {
        val messageText = binding.editTextMessage.text.toString().trim()
        if (messageText.isNotEmpty()) {
            viewModel.sendMessage(messageText)
            // 清空輸入框在ViewModel中處理
            
            // 發送訊息後滾動到最新位置
            binding.root.postDelayed({
                scrollToLatestMessage()
            }, 100)
        }
    }

    /**
     * 切換語音識別
     */
    private fun toggleVoiceRecognition() {
        if (viewModel.isListening.value) {
            viewModel.stopVoiceRecognition()
        } else {
            // 檢查權限
            if (hasPermission(RECORD_AUDIO_PERMISSION)) {
                viewModel.startVoiceRecognition()
            } else {
                requestPermission(RECORD_AUDIO_PERMISSION)
            }
        }
    }

    /**
     * 更新語音按鈕狀態
     */
    private fun updateVoiceButton(isListening: Boolean) {
        binding.buttonVoice.isSelected = isListening
        // 改變按鈕圖示來表示狀態
        binding.buttonVoice.setImageResource(
            if (isListening) com.mtkresearch.breezeapp_kotlin.R.drawable.ic_mic_off 
            else com.mtkresearch.breezeapp_kotlin.R.drawable.ic_mic
        )
        binding.buttonVoice.contentDescription = if (isListening) "停止語音輸入" else "開始語音輸入"
        
        // 如果正在聽取語音，禁用發送按鈕
        if (isListening) {
            binding.buttonSend.isEnabled = false
            binding.buttonSend.alpha = 0.5f
        } else {
            binding.buttonSend.isEnabled = true
            binding.buttonSend.alpha = 1.0f
        }
    }

    /**
     * 滾動到最新訊息
     */
    private fun scrollToLatestMessage() {
        if (messageAdapter.itemCount > 0) {
            val lastPosition = messageAdapter.itemCount - 1
            val layoutManager = binding.recyclerViewMessages.layoutManager as? LinearLayoutManager
            
            // 如果最後一個item已經完全可見，使用smoothScrollToPosition
            // 否則直接跳到最後位置以避免長時間滾動
            if (layoutManager != null) {
                val lastVisiblePosition = layoutManager.findLastCompletelyVisibleItemPosition()
                if (lastPosition - lastVisiblePosition <= 3) {
                    // 相差不多，使用平滑滾動
                    binding.recyclerViewMessages.smoothScrollToPosition(lastPosition)
                } else {
                    // 相差很多，直接跳到最後
                    binding.recyclerViewMessages.scrollToPosition(lastPosition)
                }
            } else {
                binding.recyclerViewMessages.smoothScrollToPosition(lastPosition)
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
     * 權限處理回調
     */
    override fun onPermissionsGranted(permissions: List<String>) {
        super.onPermissionsGranted(permissions)
        if (permissions.contains(RECORD_AUDIO_PERMISSION)) {
            viewModel.startVoiceRecognition()
        }
    }

    override fun onPermissionsDenied(permissions: List<String>) {
        super.onPermissionsDenied(permissions)
        if (permissions.contains(RECORD_AUDIO_PERMISSION)) {
            showError("需要錄音權限才能使用語音識別功能")
        }
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
                            viewModel.retryLastAIResponse()
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
        binding.textViewAIStatus.apply {
            text = message
            visibility = View.VISIBLE
        }
        
        // 3秒後自動隱藏
        binding.textViewAIStatus.postDelayed({
            if (binding.textViewAIStatus.text == message) {
                binding.textViewAIStatus.visibility = View.GONE
            }
        }, 3000)
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // 鍵盤高度
            val keyboardHeight = imeInsets.bottom
            val isKeyboardVisible = keyboardHeight > 0
            
            // 調整輸入區域的位置，讓它始終保持在鍵盤上方
            val inputLayoutParams = binding.inputSection.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            inputLayoutParams.bottomMargin = keyboardHeight
            binding.inputSection.layoutParams = inputLayoutParams
            
            // 不需要額外調整chatContentLayout的bottomMargin
            // 因為它已經通過constraint約束到inputSection的頂部
            // 只需要保持原有的spacing即可
            
            if (isKeyboardVisible) {
                // 延遲滾動確保佈局調整完成
                binding.root.postDelayed({
                    scrollToLatestMessage()
                }, 200)
            }
            
            // 保持RecyclerView的原始padding，不需要動態調整
            // 因為chatContentLayout到inputSection的約束已經提供了足夠的間距
            
            // 返回CONSUMED表示我們已經處理了這些insets
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * 處理返回按鈕事件
     * 
     * @return true 如果Fragment處理了返回事件，false 如果應該由Activity處理
     */
    fun onBackPressed(): Boolean {
        // 如果正在語音識別，停止識別
        if (viewModel.isListening.value) {
            viewModel.stopVoiceRecognition()
            return true
        }
        
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
        
        // 獲取輸入區域的位置
        val inputRect = Rect()
        binding.inputSection.getGlobalVisibleRect(inputRect)
        
        // 檢查觸摸點是否在輸入區域外
        val touchX = event.x.toInt()
        val touchY = event.y.toInt()
        
        if (!inputRect.contains(touchX, touchY)) {
            // 點擊在輸入區域外，收起鍵盤
            hideKeyboard()
        }
    }

    /**
     * 隱藏軟鍵盤
     */
    private fun hideKeyboard() {
        val inputMethodManager = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        
        // 清除輸入框焦點
        binding.editTextMessage.clearFocus()
        
        // 隱藏鍵盤
        inputMethodManager.hideSoftInputFromWindow(
            binding.editTextMessage.windowToken,
            0
        )
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