package com.mtkresearch.breezeapp_kotlin.presentation.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.WidgetMessageBubbleBinding

/**
 * 可重複使用的訊息氣泡UI組件
 * 
 * 功能特色:
 * - 支援用戶和AI訊息的不同樣式
 * - 自動調整氣泡方向和顏色
 * - 支援文字訊息和圖片內容
 * - 提供操作按鈕 (語音播放、點讚等)
 * - 支援載入狀態和錯誤狀態
 * 
 * 使用方式:
 * ```kotlin
 * messageBubbleView.setMessage(
 *     text = "Hello World",
 *     type = MessageType.USER,
 *     showButtons = true
 * )
 * ```
 */
class MessageBubbleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /**
     * 訊息類型，決定氣泡的樣式和位置
     */
    enum class MessageType {
        USER,    // 用戶訊息：右對齊，橘色背景
        AI,      // AI訊息：左對齊，白色背景
        SYSTEM   // 系統訊息：居中，灰色背景
    }

    /**
     * 訊息狀態，用於顯示載入和錯誤狀態
     */
    enum class MessageState {
        NORMAL,   // 正常狀態
        LOADING,  // 載入中
        ERROR,    // 錯誤狀態
        TYPING    // 正在輸入 (AI專用)
    }

    private val binding: WidgetMessageBubbleBinding = 
        WidgetMessageBubbleBinding.inflate(LayoutInflater.from(context), this, true)

    private var currentType: MessageType = MessageType.USER
    private var currentState: MessageState = MessageState.NORMAL
    
    // 回調函數
    private var onSpeakerClickListener: (() -> Unit)? = null
    private var onLikeClickListener: ((isPositive: Boolean) -> Unit)? = null
    private var onRetryClickListener: (() -> Unit)? = null

    init {
        setupViews()
        setupClickListeners()
    }

    private fun setupViews() {
        orientation = VERTICAL
        setPadding(
            resources.getDimensionPixelSize(R.dimen.spacing_small),
            resources.getDimensionPixelSize(R.dimen.spacing_micro),
            resources.getDimensionPixelSize(R.dimen.spacing_small),
            resources.getDimensionPixelSize(R.dimen.spacing_micro)
        )
    }

    private fun setupClickListeners() {
        binding.speakerButton.setOnClickListener {
            onSpeakerClickListener?.invoke()
        }
        
        binding.upLikeButton.setOnClickListener {
            onLikeClickListener?.invoke(true)
        }
        
        binding.downLikeButton.setOnClickListener {
            onLikeClickListener?.invoke(false)
        }
        
        binding.retryButton.setOnClickListener {
            onRetryClickListener?.invoke()
        }
    }

    /**
     * 設置訊息內容和樣式
     */
    fun setMessage(
        text: String,
        type: MessageType = MessageType.USER,
        state: MessageState = MessageState.NORMAL,
        showButtons: Boolean = false,
        imageUrl: String? = null
    ) {
        currentType = type
        currentState = state
        
        // 設置文字內容
        binding.messageText.text = text
        binding.messageText.isVisible = text.isNotEmpty()
        
        // 設置圖片 (暫時隱藏，後續可加入圖片載入邏輯)
        binding.messageImage.isVisible = false
        
        // 根據類型調整樣式
        applyMessageStyle(type)
        
        // 根據狀態調整UI
        applyMessageState(state)
        
        // 設置按鈕顯示
        setupButtons(type, showButtons)
    }

    /**
     * 根據訊息類型調整樣式
     */
    private fun applyMessageStyle(type: MessageType) {
        val layoutParams = binding.messageBubble.layoutParams as LayoutParams
        
        when (type) {
            MessageType.USER -> {
                // 用戶訊息：右對齊，橘色背景
                layoutParams.gravity = Gravity.END
                binding.messageBubble.background = ContextCompat.getDrawable(
                    context, R.drawable.bg_user_message
                )
                binding.messageText.setTextColor(
                    ContextCompat.getColor(context, R.color.user_message_text)
                )
                // 設置最大寬度
                val maxWidth = resources.getDimensionPixelSize(R.dimen.message_bubble_max_width)
                binding.messageBubble.layoutParams.width = maxWidth
            }
            
            MessageType.AI -> {
                // AI訊息：左對齊，白色背景
                layoutParams.gravity = Gravity.START
                binding.messageBubble.background = ContextCompat.getDrawable(
                    context, R.drawable.bg_ai_message
                )
                binding.messageText.setTextColor(
                    ContextCompat.getColor(context, R.color.ai_message_text)
                )
                // AI訊息使用包裹內容的寬度
                binding.messageBubble.layoutParams.width = LayoutParams.WRAP_CONTENT
            }
            
            MessageType.SYSTEM -> {
                // 系統訊息：居中，灰色背景
                layoutParams.gravity = Gravity.CENTER_HORIZONTAL
                binding.messageBubble.background = ContextCompat.getDrawable(
                    context, R.drawable.bg_message_bubble
                )
                binding.messageText.setTextColor(
                    ContextCompat.getColor(context, R.color.text_secondary)
                )
                // 設置最大寬度
                val maxWidth = resources.getDimensionPixelSize(R.dimen.message_bubble_max_width)
                binding.messageBubble.layoutParams.width = maxWidth
            }
        }
        
        binding.messageBubble.layoutParams = layoutParams
    }

    /**
     * 根據訊息狀態調整UI
     */
    private fun applyMessageState(state: MessageState) {
        when (state) {
            MessageState.NORMAL -> {
                binding.loadingIndicator.isVisible = false
                binding.errorIndicator.isVisible = false
                binding.messageText.alpha = 1.0f
            }
            
            MessageState.LOADING -> {
                binding.loadingIndicator.isVisible = true
                binding.errorIndicator.isVisible = false
                binding.messageText.alpha = 0.6f
                binding.messageText.text = context.getString(R.string.generating_response)
            }
            
            MessageState.ERROR -> {
                binding.loadingIndicator.isVisible = false
                binding.errorIndicator.isVisible = true
                binding.messageText.alpha = 0.6f
                binding.messageText.text = context.getString(R.string.error_generating_response)
            }
            
            MessageState.TYPING -> {
                binding.loadingIndicator.isVisible = true
                binding.errorIndicator.isVisible = false
                binding.messageText.alpha = 0.8f
                binding.messageText.text = context.getString(R.string.ai_is_typing)
            }
        }
    }

    /**
     * 設置操作按鈕的顯示
     */
    private fun setupButtons(type: MessageType, showButtons: Boolean) {
        val shouldShowButtons = showButtons && currentState == MessageState.NORMAL
        binding.buttonRow.isVisible = shouldShowButtons
        
        if (shouldShowButtons) {
            when (type) {
                MessageType.USER -> {
                    // 用戶訊息只顯示語音播放按鈕
                    binding.speakerButton.isVisible = true
                    binding.upLikeButton.isVisible = false
                    binding.downLikeButton.isVisible = false
                }
                
                MessageType.AI -> {
                    // AI訊息顯示完整按鈕組
                    binding.speakerButton.isVisible = true
                    binding.upLikeButton.isVisible = true
                    binding.downLikeButton.isVisible = true
                }
                
                MessageType.SYSTEM -> {
                    // 系統訊息不顯示操作按鈕
                    binding.buttonRow.isVisible = false
                }
            }
        }
        
        // 錯誤狀態特殊處理
        binding.retryButton.isVisible = currentState == MessageState.ERROR
    }

    /**
     * 設置語音播放按鈕點擊監聽器
     */
    fun setOnSpeakerClickListener(listener: (() -> Unit)?) {
        onSpeakerClickListener = listener
    }

    /**
     * 設置點讚按鈕點擊監聽器
     */
    fun setOnLikeClickListener(listener: ((isPositive: Boolean) -> Unit)?) {
        onLikeClickListener = listener
    }

    /**
     * 設置重試按鈕點擊監聽器
     */
    fun setOnRetryClickListener(listener: (() -> Unit)?) {
        onRetryClickListener = listener
    }

    /**
     * 更新載入狀態
     */
    fun setLoading(isLoading: Boolean) {
        currentState = if (isLoading) MessageState.LOADING else MessageState.NORMAL
        applyMessageState(currentState)
        setupButtons(currentType, binding.buttonRow.isVisible)
    }

    /**
     * 設置錯誤狀態
     */
    fun setError(errorMessage: String? = null) {
        currentState = MessageState.ERROR
        errorMessage?.let { binding.messageText.text = it }
        applyMessageState(currentState)
        setupButtons(currentType, true) // 錯誤時顯示重試按鈕
    }

    /**
     * 設置AI正在輸入狀態
     */
    fun setTyping(isTyping: Boolean) {
        currentState = if (isTyping) MessageState.TYPING else MessageState.NORMAL
        applyMessageState(currentState)
        setupButtons(currentType, !isTyping)
    }

    /**
     * 獲取當前訊息文字
     */
    fun getMessageText(): String = binding.messageText.text.toString()

    /**
     * 獲取當前訊息類型
     */
    fun getMessageType(): MessageType = currentType

    /**
     * 獲取當前訊息狀態
     */
    fun getMessageState(): MessageState = currentState
} 