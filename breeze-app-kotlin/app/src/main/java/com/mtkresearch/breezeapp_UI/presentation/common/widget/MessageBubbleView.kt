package com.mtkresearch.breezeapp_UI.presentation.common.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.mtkresearch.breezeapp_UI.R
import com.mtkresearch.breezeapp_UI.core.utils.ColorUtils
import com.mtkresearch.breezeapp_UI.domain.model.MessageAuthor
import com.mtkresearch.breezeapp_UI.databinding.WidgetMessageBubbleBinding

/**
 * 訊息氣泡UI組件
 * 
 * 功能特色:
 * - 支援USER/AI/SYSTEM三種訊息類型，自動調整樣式和對齊方式
 * - 四種訊息狀態管理 (NORMAL/LOADING/ERROR/TYPING)
 * - 智能按鈕配置 (語音播放、點讚、重試)
 * - 主題感知的顏色系統，確保最佳對比度
 * - 自適應氣泡大小和背景顏色
 * - 圖片訊息支援 (框架已備，可擴展)
 * 
 * 使用方式:
 * ```kotlin
 * messageBubble.setMessage(
 *     text = "Hello World",
 *     author = MessageAuthor.AI,
 *     state = MessageState.LOADING,
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
     * 訊息狀態枚舉
     */
    enum class MessageState {
        NORMAL,   // 正常狀態
        LOADING,  // 載入中
        ERROR,    // 錯誤狀態
        TYPING    // 正在輸入 (AI專用)
    }

    private val binding: WidgetMessageBubbleBinding
    private var currentMessageAuthor: MessageAuthor = MessageAuthor.USER
    private var currentState = MessageState.NORMAL

    // 回調函數
    private var onSpeakerClickListener: (() -> Unit)? = null
    private var onLikeClickListener: ((isPositive: Boolean) -> Unit)? = null
    private var onRetryClickListener: (() -> Unit)? = null

    init {
        binding = WidgetMessageBubbleBinding.inflate(LayoutInflater.from(context), this, true)
        parseAttributes(attrs)
        setupClickListeners()
        orientation = VERTICAL
    }

    /**
     * 解析XML屬性
     */
    private fun parseAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(
                it, R.styleable.MessageBubbleView, 0, 0
            )
            try {
                val messageStateIndex = typedArray.getInt(
                    R.styleable.MessageBubbleView_messageState,
                    MessageState.NORMAL.ordinal
                )
                currentState = MessageState.values()[messageStateIndex]
                
                val showButtons = typedArray.getBoolean(
                    R.styleable.MessageBubbleView_showButtons,
                    false
                )
                binding.buttonsContainer.isVisible = showButtons
            } finally {
                typedArray.recycle()
            }
        }
    }

    /**
     * 設置點擊監聽器
     */
    private fun setupClickListeners() {
        binding.voiceButton.setOnClickListener { onSpeakerClickListener?.invoke() }
        binding.likeButton.setOnClickListener { onLikeClickListener?.invoke(true) }
        binding.dislikeButton.setOnClickListener { onLikeClickListener?.invoke(false) }
        binding.retryButton.setOnClickListener { onRetryClickListener?.invoke() }
    }

    /**
     * 設置訊息內容
     */
    fun setMessage(
        text: String,
        author: MessageAuthor,
        state: MessageState,
        showButtons: Boolean
    ) {
        currentMessageAuthor = author
        currentState = state
        
        binding.messageText.text = text
        binding.messageText.isVisible = text.isNotEmpty()
        
        binding.buttonsContainer.isVisible = showButtons
        
        applyMessageStyle()
        applyMessageState()
        configureButtons()
    }

    /**
     * 更新訊息狀態
     */
    fun updateState(state: MessageState) {
        currentState = state
        applyMessageState()
    }

    /**
     * 應用訊息樣式 (根據類型)
     */
    private fun applyMessageStyle() {
        val colors = ColorUtils.getMessageColors(context, currentMessageAuthor)
        
        binding.messageText.setTextColor(colors.textColor)
        
        when (currentMessageAuthor) {
            MessageAuthor.USER -> {
                gravity = Gravity.END
                binding.messageContainer.background = ContextCompat.getDrawable(context, R.drawable.bg_ai_message)
                (binding.messageContainer.background as? android.graphics.drawable.GradientDrawable)?.setColor(colors.backgroundColor)
            }
            MessageAuthor.AI -> {
                gravity = Gravity.START
                binding.messageContainer.background = null
            }
            MessageAuthor.SYSTEM_INFO, MessageAuthor.SYSTEM_ERROR -> {
                gravity = Gravity.CENTER_HORIZONTAL
                binding.messageContainer.background = ContextCompat.getDrawable(context, R.drawable.bg_system_message)
                 (binding.messageContainer.background as? android.graphics.drawable.GradientDrawable)?.setColor(colors.backgroundColor)
            }
        }
    }

    /**
     * 應用訊息狀態
     */
    private fun applyMessageState() {
        // 統一管理alpha值，避免衝突
        val (messageAlpha, containerAlpha) = when (currentState) {
            MessageState.NORMAL -> Pair(1.0f, 1.0f)
            MessageState.LOADING -> Pair(0.7f, 1.0f)  // 載入狀態：訊息文字較暗
            MessageState.ERROR -> Pair(0.8f, 1.0f)    // 錯誤狀態：訊息文字稍暗
            MessageState.TYPING -> Pair(0.8f, 1.0f)   // TYPING狀態（向後兼容）
        }
        
        // 同時設置所有相關的alpha值，確保一致性
        binding.messageText.alpha = messageAlpha
        binding.messageContainer.alpha = containerAlpha
        
        // 錯誤狀態的特殊處理
        if (currentState == MessageState.ERROR) {
            val errorBackground = ContextCompat.getDrawable(context, R.drawable.bg_message_bubble)
            binding.messageContainer.background = errorBackground
        }
    }

    /**
     * 配置按鈕顯示
     */
    private fun configureButtons() {
        val isAiMessage = currentMessageAuthor == MessageAuthor.AI
        val isErrorState = currentState == MessageState.ERROR

        binding.voiceButton.isVisible = isAiMessage && !isErrorState
        binding.likeButton.isVisible = isAiMessage && !isErrorState
        binding.dislikeButton.isVisible = isAiMessage && !isErrorState
        binding.retryButton.isVisible = isErrorState
        
        // 應用按鈕主題顏色
        applyButtonTheme()
    }

    /**
     * 應用按鈕主題顏色
     */
    private fun applyButtonTheme() {
        val primaryColor = ContextCompat.getColor(context, R.color.primary)
        val errorColor = ContextCompat.getColor(context, R.color.error)
        
        // 設置按鈕圖示顏色為主色調
        val primaryColorStateList = ColorStateList.valueOf(primaryColor)
        binding.voiceButton.imageTintList = primaryColorStateList
        binding.likeButton.imageTintList = primaryColorStateList
        binding.dislikeButton.imageTintList = primaryColorStateList
        binding.retryButton.imageTintList = ColorStateList.valueOf(errorColor)
        
        // 🔧 關鍵修復：為每個按鈕創建獨立的drawable實例
        // 使用 mutate() 確保每個按鈕都有自己的狀態，不會互相影響
        binding.voiceButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline)?.mutate()
        binding.likeButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline)?.mutate()
        binding.dislikeButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline)?.mutate()
        
        // 重試按鈕使用錯誤顏色邊框，也需要獨立實例
        binding.retryButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline_error)?.mutate()
    }

    /**
     * 設置語音播放點擊監聽器
     */
    fun setOnSpeakerClickListener(listener: () -> Unit) {
        onSpeakerClickListener = listener
    }

    /**
     * 設置點讚點擊監聽器
     */
    fun setOnLikeClickListener(listener: (isPositive: Boolean) -> Unit) {
        onLikeClickListener = listener
    }

    /**
     * 設置重試點擊監聽器
     */
    fun setOnRetryClickListener(listener: () -> Unit) {
        onRetryClickListener = listener
    }

    /**
     * 驗證顏色對比度 (用於開發階段檢測)
     */
    private fun validateColorContrast() {
        // 顏色對比度驗證已移至ColorUtils，這裡保留空方法以備未來使用
    }

    companion object {
        private const val TAG = "MessageBubbleView"
    }
} 