package com.mtkresearch.breezeapp_kotlin.presentation.chat.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.mtkresearch.breezeapp_kotlin.databinding.FragmentChatBinding
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseFragment
import com.mtkresearch.breezeapp_kotlin.presentation.chat.adapter.MessageAdapter
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessage
import com.mtkresearch.breezeapp_kotlin.presentation.chat.viewmodel.ChatViewModel

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
                        type = com.mtkresearch.breezeapp_kotlin.presentation.common.widget.ErrorView.ErrorType.AI_PROCESSING,
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
            binding.textViewAIStatus.text = if (isResponding) "AI正在回應中..." else ""
        }

        // 觀察語音識別狀態
        viewModel.isListening.collectSafely { isListening ->
            updateVoiceButton(isListening)
            binding.textViewVoiceStatus.visibility = if (isListening) View.VISIBLE else View.GONE
            binding.textViewVoiceStatus.text = if (isListening) "正在聽取語音..." else ""
        }

        // 觀察打字狀態
        viewModel.isTyping.collectSafely { isTyping ->
            binding.textViewTypingIndicator.visibility = if (isTyping) View.VISIBLE else View.GONE
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
                // 當輸入框獲得焦點時，滾動到最新訊息
                scrollToLatestMessage()
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
        binding.buttonVoice.text = if (isListening) "停止" else "語音"
        
        // 如果正在聽取語音，禁用發送按鈕
        if (isListening) {
            binding.buttonSend.isEnabled = false
            binding.buttonSend.alpha = 0.5f
        }
    }

    /**
     * 滾動到最新訊息
     */
    private fun scrollToLatestMessage() {
        if (messageAdapter.itemCount > 0) {
            binding.recyclerViewMessages.smoothScrollToPosition(messageAdapter.itemCount - 1)
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