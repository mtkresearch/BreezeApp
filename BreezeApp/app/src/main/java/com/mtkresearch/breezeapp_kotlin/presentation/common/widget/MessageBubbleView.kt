package com.mtkresearch.breezeapp_kotlin.presentation.common.widget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.core.utils.ColorUtils
import com.mtkresearch.breezeapp_kotlin.core.utils.MessageType

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
 *     type = MessageType.AI,
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

    // UI組件
    private lateinit var messageContainer: LinearLayout
    private lateinit var messageText: TextView
    private lateinit var buttonsContainer: LinearLayout
    private lateinit var voiceButton: ImageButton  // 改名為voiceButton以匹配佈局
    private lateinit var likeButton: ImageButton
    private lateinit var dislikeButton: ImageButton
    private lateinit var retryButton: ImageButton


    // 當前狀態
    private var currentMessageType = MessageType.USER
    private var currentState = MessageState.NORMAL

    // 回調函數
    private var onSpeakerClickListener: (() -> Unit)? = null
    private var onLikeClickListener: ((isPositive: Boolean) -> Unit)? = null
    private var onRetryClickListener: (() -> Unit)? = null

    init {
        initializeView()
        parseAttributes(attrs)
        setupClickListeners()
    }

    /**
     * 初始化視圖
     */
    private fun initializeView() {
        LayoutInflater.from(context).inflate(R.layout.widget_message_bubble, this, true)
        
        // 綁定UI組件
        messageContainer = findViewById(R.id.messageContainer)
        messageText = findViewById(R.id.messageText)
        buttonsContainer = findViewById(R.id.buttonsContainer)
        voiceButton = findViewById(R.id.voiceButton)
        likeButton = findViewById(R.id.likeButton)
        dislikeButton = findViewById(R.id.dislikeButton)
        retryButton = findViewById(R.id.retryButton)

        
        // 設置預設配置
        orientation = VERTICAL
        setupDefaultStyles()
    }

    /**
     * 解析XML屬性
     */
    private fun parseAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray: TypedArray = context.obtainStyledAttributes(
                it, R.styleable.MessageBubbleView, 0, 0
            )
            
            try {
                // 解析訊息類型
                val messageTypeIndex = typedArray.getInt(
                    R.styleable.MessageBubbleView_messageType, 
                    MessageType.USER.ordinal
                )
                currentMessageType = MessageType.values()[messageTypeIndex]
                
                // 解析訊息狀態
                val messageStateIndex = typedArray.getInt(
                    R.styleable.MessageBubbleView_messageState,
                    MessageState.NORMAL.ordinal
                )
                currentState = MessageState.values()[messageStateIndex]
                
                // 解析是否顯示按鈕
                val showButtons = typedArray.getBoolean(
                    R.styleable.MessageBubbleView_showButtons,
                    false
                )
                buttonsContainer.isVisible = showButtons
                
            } finally {
                typedArray.recycle()
            }
        }
    }

    /**
     * 設置預設樣式
     */
    private fun setupDefaultStyles() {
        applyMessageStyle()
        applyMessageState()
    }

    /**
     * 設置點擊監聽器
     */
    private fun setupClickListeners() {
        voiceButton.setOnClickListener { onSpeakerClickListener?.invoke() }
        likeButton.setOnClickListener { onLikeClickListener?.invoke(true) }
        dislikeButton.setOnClickListener { onLikeClickListener?.invoke(false) }
        retryButton.setOnClickListener { onRetryClickListener?.invoke() }
    }

    /**
     * 設置訊息內容
     */
    fun setMessage(
        text: String,
        type: MessageType = MessageType.USER,
        state: MessageState = MessageState.NORMAL,
        showButtons: Boolean = false,
        imageUrl: String? = null
    ) {
        currentMessageType = type
        currentState = state
        
        // 設置文字內容
        messageText.text = text
        messageText.isVisible = text.isNotEmpty()
        
        // 圖片功能暫時移除，如需要可在未來添加
        // TODO: 未來版本可添加圖片支援
        
        // 設置按鈕顯示
        buttonsContainer.isVisible = showButtons
        
        // 應用樣式和狀態
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
        val colors = ColorUtils.getMessageColors(context, currentMessageType)
        
        // 設置文字顏色
        messageText.setTextColor(colors.textColor)
        
        // 設置對齊方式和邊距
        when (currentMessageType) {
            MessageType.USER -> {
                // 用戶訊息：右對齊，有背景
                gravity = Gravity.END
                val params = messageContainer.layoutParams as MarginLayoutParams
                params.setMargins(
                    resources.getDimensionPixelSize(R.dimen.message_margin_large),
                    resources.getDimensionPixelSize(R.dimen.message_margin_small),
                    resources.getDimensionPixelSize(R.dimen.message_margin_small),
                    resources.getDimensionPixelSize(R.dimen.message_margin_small)
                )
                messageContainer.layoutParams = params
                
                // 用戶訊息使用背景色和drawable
                messageContainer.setBackgroundColor(colors.backgroundColor)
                val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.bg_message_bubble)
                messageContainer.background = backgroundDrawable
            }
            MessageType.AI -> {
                // AI訊息：左對齊，無背景
                gravity = Gravity.START
                val params = messageContainer.layoutParams as MarginLayoutParams
                params.setMargins(
                    resources.getDimensionPixelSize(R.dimen.message_margin_small),
                    resources.getDimensionPixelSize(R.dimen.message_margin_small),
                    resources.getDimensionPixelSize(R.dimen.message_margin_large),
                    resources.getDimensionPixelSize(R.dimen.message_margin_small)
                )
                messageContainer.layoutParams = params
                
                // AI訊息不使用背景
                messageContainer.background = null
            }
            MessageType.SYSTEM -> {
                // 系統訊息：居中，有背景
                gravity = Gravity.CENTER_HORIZONTAL
                val params = messageContainer.layoutParams as MarginLayoutParams
                params.setMargins(
                    resources.getDimensionPixelSize(R.dimen.message_margin_medium),
                    resources.getDimensionPixelSize(R.dimen.message_margin_small),
                    resources.getDimensionPixelSize(R.dimen.message_margin_medium),
                    resources.getDimensionPixelSize(R.dimen.message_margin_small)
                )
                messageContainer.layoutParams = params
                
                // 系統訊息使用背景色和drawable
                messageContainer.setBackgroundColor(colors.backgroundColor)
                val backgroundDrawable = ContextCompat.getDrawable(context, R.drawable.bg_message_bubble)
                messageContainer.background = backgroundDrawable
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
        messageText.alpha = messageAlpha
        messageContainer.alpha = containerAlpha
        
        // 錯誤狀態的特殊處理
        if (currentState == MessageState.ERROR) {
            val errorBackground = ContextCompat.getDrawable(context, R.drawable.bg_message_bubble)
            messageContainer.background = errorBackground
        }
    }

    /**
     * 配置按鈕顯示
     */
    private fun configureButtons() {
        if (!buttonsContainer.isVisible) return
        
        when (currentMessageType) {
            MessageType.USER -> {
                // 用戶訊息：只顯示重試按鈕（錯誤狀態）
                voiceButton.isVisible = false
                likeButton.isVisible = false
                dislikeButton.isVisible = false
                retryButton.isVisible = currentState == MessageState.ERROR
            }
            MessageType.AI -> {
                // AI訊息：顯示語音播放和點讚按鈕
                voiceButton.isVisible = currentState == MessageState.NORMAL
                likeButton.isVisible = currentState == MessageState.NORMAL
                dislikeButton.isVisible = currentState == MessageState.NORMAL
                retryButton.isVisible = currentState == MessageState.ERROR
            }
            MessageType.SYSTEM -> {
                // 系統訊息：不顯示任何按鈕
                voiceButton.isVisible = false
                likeButton.isVisible = false
                dislikeButton.isVisible = false
                retryButton.isVisible = false
            }
        }
        
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
        voiceButton.imageTintList = primaryColorStateList
        likeButton.imageTintList = primaryColorStateList
        dislikeButton.imageTintList = primaryColorStateList
        retryButton.imageTintList = ColorStateList.valueOf(errorColor)
        
        // 🔧 關鍵修復：為每個按鈕創建獨立的drawable實例
        // 使用 mutate() 確保每個按鈕都有自己的狀態，不會互相影響
        voiceButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline)?.mutate()
        likeButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline)?.mutate()
        dislikeButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline)?.mutate()
        
        // 重試按鈕使用錯誤顏色邊框，也需要獨立實例
        retryButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline_error)?.mutate()
    }

    /**
     * 設置語音播放點擊監聽器
     */
    fun setOnSpeakerClickListener(listener: (() -> Unit)?) {
        onSpeakerClickListener = listener
    }

    /**
     * 設置點讚點擊監聽器
     */
    fun setOnLikeClickListener(listener: ((isPositive: Boolean) -> Unit)?) {
        onLikeClickListener = listener
    }

    /**
     * 設置重試點擊監聽器
     */
    fun setOnRetryClickListener(listener: (() -> Unit)?) {
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