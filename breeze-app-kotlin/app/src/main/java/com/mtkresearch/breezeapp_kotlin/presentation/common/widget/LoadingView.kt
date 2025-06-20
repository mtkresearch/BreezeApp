package com.mtkresearch.breezeapp_kotlin.presentation.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.WidgetLoadingBinding

/**
 * 可重複使用的載入狀態UI組件
 * 
 * 功能特色:
 * - 支援多種載入樣式 (圓形進度、橫條進度、點動畫)
 * - 可配置載入文字和副標題
 * - 支援取消操作
 * - 自動管理可見性
 * - 支援主題切換
 * 
 * 使用方式:
 * ```kotlin
 * loadingView.show(
 *     message = "載入中...",
 *     subtitle = "請稍候",
 *     showCancel = true,
 *     style = LoadingStyle.CIRCULAR
 * )
 * ```
 */
class LoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /**
     * 載入樣式
     */
    enum class LoadingStyle {
        CIRCULAR,     // 圓形進度指示器
        HORIZONTAL,   // 橫條進度指示器
        DOTS,         // 點動畫
        SPINNER       // 旋轉器
    }

    /**
     * 載入大小
     */
    enum class LoadingSize {
        SMALL,    // 小尺寸 (24dp)
        MEDIUM,   // 中等尺寸 (48dp)
        LARGE     // 大尺寸 (72dp)
    }

    private val binding: WidgetLoadingBinding = 
        WidgetLoadingBinding.inflate(LayoutInflater.from(context), this, true)

    private var currentStyle: LoadingStyle = LoadingStyle.CIRCULAR
    private var currentSize: LoadingSize = LoadingSize.MEDIUM
    
    // 回調函數
    private var onCancelClickListener: (() -> Unit)? = null

    init {
        setupViews()
        setupClickListeners()
        parseAttributes(attrs)
    }

    private fun setupViews() {
        orientation = VERTICAL
        setPadding(
            resources.getDimensionPixelSize(R.dimen.loading_container_padding),
            resources.getDimensionPixelSize(R.dimen.loading_container_padding),
            resources.getDimensionPixelSize(R.dimen.loading_container_padding),
            resources.getDimensionPixelSize(R.dimen.loading_container_padding)
        )
        
        // 預設隱藏
        isVisible = false
    }

    private fun setupClickListeners() {
        binding.cancelButton.setOnClickListener {
            hide()
            onCancelClickListener?.invoke()
        }
        
        // 點擊背景不隱藏，避免誤觸
        setOnClickListener { /* 吸收點擊事件 */ }
    }

    private fun parseAttributes(attrs: AttributeSet?) {
        if (attrs == null) return
        
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.LoadingView)
        try {
            // 讀取自定義屬性
            val styleIndex = typedArray.getInt(R.styleable.LoadingView_loadingStyle, 0)
            currentStyle = LoadingStyle.values()[styleIndex]
            
            val sizeIndex = typedArray.getInt(R.styleable.LoadingView_loadingSize, 1)
            currentSize = LoadingSize.values()[sizeIndex]
            
            val message = typedArray.getString(R.styleable.LoadingView_loadingMessage)
            val subtitle = typedArray.getString(R.styleable.LoadingView_loadingSubtitle)
            val showCancel = typedArray.getBoolean(R.styleable.LoadingView_showCancel, false)
            
            // 應用屬性
            if (!message.isNullOrEmpty() || !subtitle.isNullOrEmpty()) {
                show(message ?: "", subtitle ?: "", showCancel, currentStyle, currentSize)
            }
            
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * 顯示載入視圖
     */
    fun show(
        message: String = context.getString(R.string.loading),
        subtitle: String = "",
        showCancel: Boolean = false,
        style: LoadingStyle = LoadingStyle.CIRCULAR,
        size: LoadingSize = LoadingSize.MEDIUM
    ) {
        currentStyle = style
        currentSize = size
        
        // 設置文字
        binding.loadingMessage.text = message
        binding.loadingMessage.isVisible = message.isNotEmpty()
        
        binding.loadingSubtitle.text = subtitle
        binding.loadingSubtitle.isVisible = subtitle.isNotEmpty()
        
        // 設置取消按鈕
        binding.cancelButton.isVisible = showCancel
        
        // 應用樣式
        applyLoadingStyle(style, size)
        
        // 顯示視圖
        isVisible = true
        
        // 開始動畫
        startAnimation()
    }

    /**
     * 隱藏載入視圖
     */
    fun hide() {
        isVisible = false
        stopAnimation()
    }

    /**
     * 切換顯示狀態
     */
    fun toggle() {
        if (isVisible) hide() else show()
    }

    /**
     * 更新載入訊息
     */
    fun updateMessage(message: String, subtitle: String = "") {
        binding.loadingMessage.text = message
        binding.loadingMessage.isVisible = message.isNotEmpty()
        
        binding.loadingSubtitle.text = subtitle
        binding.loadingSubtitle.isVisible = subtitle.isNotEmpty()
    }

    /**
     * 應用載入樣式
     */
    private fun applyLoadingStyle(style: LoadingStyle, size: LoadingSize) {
        // 隱藏所有指示器
        binding.circularProgress.isVisible = false
        binding.horizontalProgress.isVisible = false
        binding.dotsAnimation.isVisible = false
        
        // 根據大小設置尺寸
        val progressSize = when (size) {
            LoadingSize.SMALL -> resources.getDimensionPixelSize(R.dimen.loading_circle_size) / 2
            LoadingSize.MEDIUM -> resources.getDimensionPixelSize(R.dimen.loading_circle_size)
            LoadingSize.LARGE -> resources.getDimensionPixelSize(R.dimen.loading_circle_size) * 3 / 2
        }
        
        // 根據樣式顯示對應指示器
        when (style) {
            LoadingStyle.CIRCULAR, LoadingStyle.SPINNER -> {
                binding.circularProgress.isVisible = true
                val layoutParams = binding.circularProgress.layoutParams
                layoutParams.width = progressSize
                layoutParams.height = progressSize
                binding.circularProgress.layoutParams = layoutParams
            }
            
            LoadingStyle.HORIZONTAL -> {
                binding.horizontalProgress.isVisible = true
                binding.horizontalProgress.isIndeterminate = true
            }
            
            LoadingStyle.DOTS -> {
                binding.dotsAnimation.isVisible = true
                // 點動畫需要自定義View或使用Lottie動畫
                // 暫時使用圓形進度替代
                binding.circularProgress.isVisible = true
                val layoutParams = binding.circularProgress.layoutParams
                layoutParams.width = progressSize
                layoutParams.height = progressSize
                binding.circularProgress.layoutParams = layoutParams
            }
        }
    }

    /**
     * 開始動畫
     */
    private fun startAnimation() {
        when (currentStyle) {
            LoadingStyle.CIRCULAR, LoadingStyle.SPINNER -> {
                // 圓形進度已經有內建動畫
            }
            
            LoadingStyle.HORIZONTAL -> {
                // 橫條進度已經有內建動畫
            }
            
            LoadingStyle.DOTS -> {
                // 啟動點動畫 (需要自定義實現)
                startDotsAnimation()
            }
        }
    }

    /**
     * 停止動畫
     */
    private fun stopAnimation() {
        when (currentStyle) {
            LoadingStyle.DOTS -> {
                stopDotsAnimation()
            }
            else -> {
                // 其他樣式會自動停止
            }
        }
    }

    /**
     * 開始點動畫 (簡單實現)
     */
    private fun startDotsAnimation() {
        // 這裡可以實現自定義的點動畫
        // 或者使用Lottie動畫庫
        binding.dotsAnimation.animate()
            .alpha(0.3f)
            .setDuration(500)
            .withEndAction {
                binding.dotsAnimation.animate()
                    .alpha(1.0f)
                    .setDuration(500)
                    .withEndAction {
                        if (isVisible) startDotsAnimation()
                    }
            }
    }

    /**
     * 停止點動畫
     */
    private fun stopDotsAnimation() {
        binding.dotsAnimation.clearAnimation()
        binding.dotsAnimation.alpha = 1.0f
    }

    /**
     * 設置取消按鈕點擊監聽器
     */
    fun setOnCancelClickListener(listener: () -> Unit) {
        onCancelClickListener = listener
    }

    /**
     * 檢查是否正在顯示
     */
    fun isShowing(): Boolean = isVisible

    /**
     * 獲取當前載入樣式
     */
    fun getCurrentStyle(): LoadingStyle = currentStyle

    /**
     * 獲取當前載入大小
     */
    fun getCurrentSize(): LoadingSize = currentSize
} 