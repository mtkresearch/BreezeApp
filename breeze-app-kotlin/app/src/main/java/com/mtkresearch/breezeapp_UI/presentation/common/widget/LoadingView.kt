package com.mtkresearch.breezeapp_kotlin.presentation.common.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.core.utils.ColorUtils

/**
 * 載入狀態UI組件
 * 
 * 功能特色:
 * - 四種載入樣式 (圓形進度條、線性進度條、點動畫、自訂動畫)
 * - 三種尺寸選擇 (小、中、大)
 * - 可配置載入訊息和取消操作
 * - 主題感知的顏色系統
 * - 支援背景遮罩和透明度設定
 * - 自動適應深色/淺色主題
 * 
 * 使用方式:
 * ```kotlin
 * loadingView.show(
 *     message = "正在載入...",
 *     style = LoadingStyle.CIRCULAR,
 *     size = LoadingSize.MEDIUM,
 *     showCancel = true
 * )
 * ```
 */
class LoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * 載入樣式枚舉
     */
    enum class LoadingStyle {
        CIRCULAR,    // 圓形進度條
        LINEAR,      // 線性進度條
        DOTS,        // 點動畫
        CUSTOM       // 自訂動畫
    }

    /**
     * 載入尺寸枚舉
     */
    enum class LoadingSize {
        SMALL,       // 小尺寸
        MEDIUM,      // 中等尺寸
        LARGE        // 大尺寸
    }

    // UI組件
    private lateinit var backgroundOverlay: View
    private lateinit var loadingContainer: View
    private lateinit var circularProgress: ProgressBar
    private lateinit var linearProgress: ProgressBar
    private lateinit var dotsContainer: View
    private lateinit var customContainer: View
    private lateinit var loadingText: TextView

    // 當前配置
    private var currentStyle = LoadingStyle.CIRCULAR
    private var currentSize = LoadingSize.MEDIUM
    private var showBackground = true
    private var isDismissible = false

    // 回調函數
    private var onCancelListener: (() -> Unit)? = null

    init {
        initializeView()
        parseAttributes(attrs)
        setupClickListeners()
        applyDefaultStyle()
    }

    /**
     * 初始化視圖
     */
    private fun initializeView() {
        LayoutInflater.from(context).inflate(R.layout.widget_loading, this, true)
        
        // 綁定UI組件
        backgroundOverlay = findViewById(R.id.backgroundOverlay)
        loadingContainer = findViewById(R.id.loadingContainer)
        circularProgress = findViewById(R.id.circularProgress)
        linearProgress = findViewById(R.id.linearProgress)
        dotsContainer = findViewById(R.id.dotsContainer)
        customContainer = findViewById(R.id.customContainer)
        loadingText = findViewById(R.id.loadingText)
        
        // 設置預設可見性
        isVisible = false
    }

    /**
     * 解析XML屬性
     */
    private fun parseAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray: TypedArray = context.obtainStyledAttributes(
                it, R.styleable.LoadingView, 0, 0
            )
            
            try {
                // 解析載入樣式
                val styleIndex = typedArray.getInt(
                    R.styleable.LoadingView_loadingStyle, 
                    LoadingStyle.CIRCULAR.ordinal
                )
                currentStyle = LoadingStyle.values()[styleIndex]
                
                // 解析載入尺寸
                val sizeIndex = typedArray.getInt(
                    R.styleable.LoadingView_loadingSize,
                    LoadingSize.MEDIUM.ordinal
                )
                currentSize = LoadingSize.values()[sizeIndex]
                
                // 解析背景顯示
                showBackground = typedArray.getBoolean(
                    R.styleable.LoadingView_showBackground,
                    true
                )
                
                // 解析是否可取消
                isDismissible = typedArray.getBoolean(
                    R.styleable.LoadingView_dismissible,
                    false
                )
                
                // 解析載入文字
                val loadingMessage = typedArray.getString(R.styleable.LoadingView_loadingText)
                loadingMessage?.let { loadingText.text = it }
                
            } finally {
                typedArray.recycle()
            }
        }
    }

    /**
     * 設置點擊監聽器
     */
    private fun setupClickListeners() {
        // 如果不可取消，背景點擊無效
        backgroundOverlay.setOnClickListener {
            if (isDismissible) {
                onCancelListener?.invoke()
                hide()
            }
        }
        
        // 防止載入容器的點擊事件冒泡
        loadingContainer.setOnClickListener { /* 消費點擊事件 */ }
    }

    /**
     * 應用預設樣式
     */
    private fun applyDefaultStyle() {
        applyLoadingStyle()
        applyLoadingSize()
        applyThemeColors()
    }

    /**
     * 顯示載入視圖
     */
    fun show(
        message: String = "",
        style: LoadingStyle = currentStyle,
        size: LoadingSize = currentSize,
        showCancel: Boolean = false,
        dismissible: Boolean = false
    ) {
        currentStyle = style
        currentSize = size
        isDismissible = dismissible
        
        // 設置載入訊息
        if (message.isNotEmpty()) {
            loadingText.text = message
            loadingText.isVisible = true
        } else {
            loadingText.isVisible = false
        }
        
        // 取消功能通過背景點擊實現（當dismissible=true時）
        
        // 應用樣式
        applyLoadingStyle()
        applyLoadingSize()
        applyThemeColors()
        
        // 顯示視圖
        isVisible = true
        
        // 啟動動畫
        startAnimation()
    }

    /**
     * 隱藏載入視圖
     */
    fun hide() {
        stopAnimation()
        isVisible = false
    }

    /**
     * 更新載入訊息
     */
    fun updateMessage(message: String) {
        loadingText.text = message
        loadingText.isVisible = message.isNotEmpty()
    }

    /**
     * 應用載入樣式
     */
    private fun applyLoadingStyle() {
        // 隱藏所有載入組件
        circularProgress.isVisible = false
        linearProgress.isVisible = false
        dotsContainer.isVisible = false
        customContainer.isVisible = false
        
        // 根據樣式顯示對應組件
        when (currentStyle) {
            LoadingStyle.CIRCULAR -> {
                circularProgress.isVisible = true
            }
            LoadingStyle.LINEAR -> {
                linearProgress.isVisible = true
            }
            LoadingStyle.DOTS -> {
                dotsContainer.isVisible = true
            }
            LoadingStyle.CUSTOM -> {
                customContainer.isVisible = true
            }
        }
    }

    /**
     * 應用載入尺寸
     */
    private fun applyLoadingSize() {
        val (progressSize, textSize, containerPadding) = when (currentSize) {
            LoadingSize.SMALL -> Triple(
                resources.getDimensionPixelSize(R.dimen.loading_progress_size_small),
                resources.getDimension(R.dimen.loading_text_size_small),
                resources.getDimensionPixelSize(R.dimen.loading_container_padding_small)
            )
            LoadingSize.MEDIUM -> Triple(
                resources.getDimensionPixelSize(R.dimen.loading_progress_size_medium),
                resources.getDimension(R.dimen.loading_text_size_medium),
                resources.getDimensionPixelSize(R.dimen.loading_container_padding_medium)
            )
            LoadingSize.LARGE -> Triple(
                resources.getDimensionPixelSize(R.dimen.loading_progress_size_large),
                resources.getDimension(R.dimen.loading_text_size_large),
                resources.getDimensionPixelSize(R.dimen.loading_container_padding_large)
            )
        }
        
        // 設置進度條尺寸
        circularProgress.layoutParams.apply {
            width = progressSize
            height = progressSize
        }
        
        // 設置文字大小
        loadingText.textSize = textSize / resources.displayMetrics.scaledDensity
        
        // 設置容器內邊距
        loadingContainer.setPadding(
            containerPadding, containerPadding,
            containerPadding, containerPadding
        )
    }

    /**
     * 應用主題顏色
     */
    private fun applyThemeColors() {
        // 設置背景遮罩
        if (showBackground) {
            backgroundOverlay.isVisible = true
            val overlayColor = if (ColorUtils.isDarkTheme(context)) {
                ContextCompat.getColor(context, R.color.overlay_dark)
            } else {
                ContextCompat.getColor(context, R.color.overlay_light)
            }
            backgroundOverlay.setBackgroundColor(overlayColor)
        } else {
            backgroundOverlay.isVisible = false
        }
        
        // 設置載入容器背景
        val containerBackground = if (ColorUtils.isDarkTheme(context)) {
            ContextCompat.getDrawable(context, R.drawable.bg_loading_container_dark)
        } else {
            ContextCompat.getDrawable(context, R.drawable.bg_loading_container_light)
        }
        loadingContainer.background = containerBackground
        
        // 設置進度條顏色
        val progressColor = ContextCompat.getColor(context, R.color.primary)
        circularProgress.indeterminateTintList = android.content.res.ColorStateList.valueOf(progressColor)
        linearProgress.indeterminateTintList = android.content.res.ColorStateList.valueOf(progressColor)
        
        // 設置文字顏色
        val textColor = ContextCompat.getColor(context, R.color.text_primary)
        loadingText.setTextColor(textColor)
        
        // 取消功能通過背景點擊實現，無需額外按鈕樣式
    }

    /**
     * 啟動動畫
     */
    private fun startAnimation() {
        when (currentStyle) {
            LoadingStyle.CIRCULAR -> {
                // 圓形進度條動畫已內建
            }
            LoadingStyle.LINEAR -> {
                // 線性進度條動畫已內建
            }
            LoadingStyle.DOTS -> {
                startDotsAnimation()
            }
            LoadingStyle.CUSTOM -> {
                startCustomAnimation()
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
            LoadingStyle.CUSTOM -> {
                stopCustomAnimation()
            }
            else -> {
                // 其他類型的動畫會自動停止
            }
        }
    }

    /**
     * 啟動點動畫
     */
    private fun startDotsAnimation() {
        // TODO: 實作點動畫邏輯
        // 可以使用ValueAnimator或屬性動畫實現三個點的依序閃爍效果
    }

    /**
     * 停止點動畫
     */
    private fun stopDotsAnimation() {
        // TODO: 停止點動畫
    }

    /**
     * 啟動自訂動畫
     */
    private fun startCustomAnimation() {
        // TODO: 實作自訂動畫邏輯
        // 可以是Lottie動畫或其他自訂動畫效果
    }

    /**
     * 停止自訂動畫
     */
    private fun stopCustomAnimation() {
        // TODO: 停止自訂動畫
    }

    /**
     * 設置取消監聽器
     */
    fun setOnCancelListener(listener: (() -> Unit)?) {
        onCancelListener = listener
    }
    
    /**
     * 設置取消點擊監聽器 (別名)
     */
    fun setOnCancelClickListener(listener: (() -> Unit)?) {
        onCancelListener = listener
    }

    /**
     * 檢查是否正在顯示
     */
    fun isShowing(): Boolean = isVisible

    /**
     * 快速顯示方法
     */
    fun showSimple(message: String = context.getString(R.string.loading_default_message)) {
        show(
            message = message,
            style = LoadingStyle.CIRCULAR,
            size = LoadingSize.MEDIUM,
            showCancel = false,
            dismissible = false
        )
    }

    /**
     * 快速顯示可取消的載入
     */
    fun showCancellable(
        message: String = context.getString(R.string.loading_default_message),
        onCancel: (() -> Unit)? = null
    ) {
        setOnCancelListener(onCancel)
        show(
            message = message,
            style = LoadingStyle.CIRCULAR,
            size = LoadingSize.MEDIUM,
            showCancel = true,
            dismissible = true
        )
    }

    /**
     * 驗證顏色對比度 (用於開發階段檢測)
     */
    private fun validateColorContrast() {
        // 顏色對比度驗證已移至ColorUtils，這裡保留空方法以備未來使用
    }

    companion object {
        private const val TAG = "LoadingView"
    }
} 