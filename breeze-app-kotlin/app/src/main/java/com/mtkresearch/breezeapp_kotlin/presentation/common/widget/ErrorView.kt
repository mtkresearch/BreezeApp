package com.mtkresearch.breezeapp_kotlin.presentation.common.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.WidgetErrorBinding

/**
 * 可重複使用的錯誤狀態UI組件
 * 
 * 功能特色:
 * - 支援多種錯誤類型 (網路、服務、驗證等)
 * - 可配置錯誤標題、訊息和操作按鈕
 * - 自動顯示對應的錯誤圖示
 * - 支援重試和自定義操作
 * - 可配置是否可關閉
 * 
 * 使用方式:
 * ```kotlin
 * errorView.showError(
 *     type = ErrorType.NETWORK,
 *     title = "網路連線失敗",
 *     message = "請檢查網路設定後重試",
 *     showRetry = true,
 *     showClose = true
 * )
 * ```
 */
class ErrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    /**
     * 錯誤類型，決定顯示的圖示和預設訊息
     */
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

    /**
     * 錯誤嚴重程度
     */
    enum class ErrorSeverity {
        INFO,     // 資訊 (藍色)
        WARNING,  // 警告 (橘色)
        ERROR,    // 錯誤 (紅色)
        CRITICAL  // 嚴重 (深紅色)
    }

    private val binding: WidgetErrorBinding = 
        WidgetErrorBinding.inflate(LayoutInflater.from(context), this, true)

    private var currentType: ErrorType = ErrorType.UNKNOWN
    private var currentSeverity: ErrorSeverity = ErrorSeverity.ERROR
    
    // 回調函數
    private var onRetryClickListener: (() -> Unit)? = null
    private var onCloseClickListener: (() -> Unit)? = null
    private var onCustomActionClickListener: (() -> Unit)? = null

    init {
        setupViews()
        setupClickListeners()
        parseAttributes(attrs)
    }

    private fun setupViews() {
        orientation = VERTICAL
        setPadding(
            resources.getDimensionPixelSize(R.dimen.spacing_medium),
            resources.getDimensionPixelSize(R.dimen.spacing_medium),
            resources.getDimensionPixelSize(R.dimen.spacing_medium),
            resources.getDimensionPixelSize(R.dimen.spacing_medium)
        )
        
        // 預設隱藏
        isVisible = false
    }

    private fun setupClickListeners() {
        binding.retryButton.setOnClickListener {
            onRetryClickListener?.invoke()
        }
        
        binding.closeButton.setOnClickListener {
            hide()
            onCloseClickListener?.invoke()
        }
        
        binding.customActionButton.setOnClickListener {
            onCustomActionClickListener?.invoke()
        }
        
        // 點擊背景不隱藏
        setOnClickListener { /* 吸收點擊事件 */ }
    }

    private fun parseAttributes(attrs: AttributeSet?) {
        if (attrs == null) return
        
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ErrorView)
        try {
            // 讀取自定義屬性
            val typeIndex = typedArray.getInt(R.styleable.ErrorView_errorType, 7)
            currentType = ErrorType.values()[typeIndex]
            
            val severityIndex = typedArray.getInt(R.styleable.ErrorView_errorSeverity, 2)
            currentSeverity = ErrorSeverity.values()[severityIndex]
            
            val title = typedArray.getString(R.styleable.ErrorView_errorTitle)
            val message = typedArray.getString(R.styleable.ErrorView_errorMessage)
            val showRetry = typedArray.getBoolean(R.styleable.ErrorView_showRetry, false)
            val showClose = typedArray.getBoolean(R.styleable.ErrorView_showClose, true)
            
            // 應用屬性
            if (!title.isNullOrEmpty() || !message.isNullOrEmpty()) {
                showError(currentType, currentSeverity, title ?: "", message ?: "", showRetry, showClose)
            }
            
        } finally {
            typedArray.recycle()
        }
    }

    /**
     * 顯示錯誤訊息
     */
    fun showError(
        type: ErrorType = ErrorType.UNKNOWN,
        severity: ErrorSeverity = ErrorSeverity.ERROR,
        title: String = "",
        message: String = "",
        showRetry: Boolean = false,
        showClose: Boolean = true,
        customActionText: String = ""
    ) {
        currentType = type
        currentSeverity = severity
        
        // 設置錯誤圖示
        setErrorIcon(type, severity)
        
        // 設置標題
        val errorTitle = title.ifEmpty { getDefaultTitle(type) }
        binding.errorTitle.text = errorTitle
        binding.errorTitle.isVisible = errorTitle.isNotEmpty()
        
        // 設置訊息
        val errorMessage = message.ifEmpty { getDefaultMessage(type) }
        binding.errorMessage.text = errorMessage
        binding.errorMessage.isVisible = errorMessage.isNotEmpty()
        
        // 設置按鈕
        binding.retryButton.isVisible = showRetry
        binding.closeButton.isVisible = showClose
        
        // 設置自定義操作按鈕
        if (customActionText.isNotEmpty()) {
            binding.customActionButton.text = customActionText
            binding.customActionButton.isVisible = true
        } else {
            binding.customActionButton.isVisible = false
        }
        
        // 應用嚴重程度樣式
        applySeverityStyle(severity)
        
        // 顯示視圖
        isVisible = true
    }

    /**
     * 隱藏錯誤視圖
     */
    fun hide() {
        isVisible = false
    }

    /**
     * 切換顯示狀態
     */
    fun toggle() {
        if (isVisible) hide() else showError()
    }

    /**
     * 設置錯誤圖示
     */
    private fun setErrorIcon(type: ErrorType, severity: ErrorSeverity) {
        val iconRes = when (type) {
            ErrorType.NETWORK -> R.drawable.ic_wifi_off
            ErrorType.SERVER -> R.drawable.ic_cloud_off
            ErrorType.VALIDATION -> R.drawable.ic_warning
            ErrorType.PERMISSION -> R.drawable.ic_lock
            ErrorType.MODEL_LOADING -> R.drawable.ic_download_off
            ErrorType.AI_PROCESSING -> R.drawable.ic_smart_toy_off
            ErrorType.FILE_ACCESS -> R.drawable.ic_folder_off
            ErrorType.UNKNOWN -> R.drawable.ic_error
        }
        
        binding.errorIcon.setImageResource(iconRes)
        
        // 根據嚴重程度設置圖示顏色
        val tintColor = when (severity) {
            ErrorSeverity.INFO -> ContextCompat.getColor(context, R.color.primary)
            ErrorSeverity.WARNING -> ContextCompat.getColor(context, R.color.primary_dark)
            ErrorSeverity.ERROR -> ContextCompat.getColor(context, R.color.error)
            ErrorSeverity.CRITICAL -> ContextCompat.getColor(context, R.color.error)
        }
        
        binding.errorIcon.setColorFilter(tintColor)
    }

    /**
     * 獲取預設標題
     */
    private fun getDefaultTitle(type: ErrorType): String {
        return when (type) {
            ErrorType.NETWORK -> context.getString(R.string.error_network_title)
            ErrorType.SERVER -> context.getString(R.string.error_server_title)
            ErrorType.VALIDATION -> context.getString(R.string.error_validation_title)
            ErrorType.PERMISSION -> context.getString(R.string.error_permission_title)
            ErrorType.MODEL_LOADING -> context.getString(R.string.error_model_loading_title)
            ErrorType.AI_PROCESSING -> context.getString(R.string.error_ai_processing_title)
            ErrorType.FILE_ACCESS -> context.getString(R.string.error_file_access_title)
            ErrorType.UNKNOWN -> context.getString(R.string.error_unknown_title)
        }
    }

    /**
     * 獲取預設訊息
     */
    private fun getDefaultMessage(type: ErrorType): String {
        return when (type) {
            ErrorType.NETWORK -> context.getString(R.string.error_network_message)
            ErrorType.SERVER -> context.getString(R.string.error_server_message)
            ErrorType.VALIDATION -> context.getString(R.string.error_validation_message)
            ErrorType.PERMISSION -> context.getString(R.string.error_permission_message)
            ErrorType.MODEL_LOADING -> context.getString(R.string.error_model_loading_message)
            ErrorType.AI_PROCESSING -> context.getString(R.string.error_ai_processing_message)
            ErrorType.FILE_ACCESS -> context.getString(R.string.error_file_access_message)
            ErrorType.UNKNOWN -> context.getString(R.string.error_unknown_message)
        }
    }

    /**
     * 應用嚴重程度樣式
     */
    private fun applySeverityStyle(severity: ErrorSeverity) {
        val backgroundColor = when (severity) {
            ErrorSeverity.INFO -> ContextCompat.getColor(context, R.color.surface)
            ErrorSeverity.WARNING -> ContextCompat.getColor(context, R.color.surface)
            ErrorSeverity.ERROR -> ContextCompat.getColor(context, R.color.surface)
            ErrorSeverity.CRITICAL -> ContextCompat.getColor(context, R.color.surface)
        }
        
        binding.errorContainer.setBackgroundColor(backgroundColor)
        
        // 根據嚴重程度調整文字顏色
        val textColor = when (severity) {
            ErrorSeverity.INFO -> ContextCompat.getColor(context, R.color.text_primary)
            ErrorSeverity.WARNING -> ContextCompat.getColor(context, R.color.text_primary)
            ErrorSeverity.ERROR -> ContextCompat.getColor(context, R.color.text_primary)
            ErrorSeverity.CRITICAL -> ContextCompat.getColor(context, R.color.text_primary)
        }
        
        binding.errorTitle.setTextColor(textColor)
        binding.errorMessage.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
    }

    /**
     * 設置重試按鈕點擊監聽器
     */
    fun setOnRetryClickListener(listener: () -> Unit) {
        onRetryClickListener = listener
    }

    /**
     * 設置關閉按鈕點擊監聽器
     */
    fun setOnCloseClickListener(listener: () -> Unit) {
        onCloseClickListener = listener
    }

    /**
     * 設置自定義操作按鈕點擊監聽器
     */
    fun setOnCustomActionClickListener(listener: () -> Unit) {
        onCustomActionClickListener = listener
    }

    /**
     * 更新錯誤訊息
     */
    fun updateError(title: String, message: String) {
        binding.errorTitle.text = title
        binding.errorTitle.isVisible = title.isNotEmpty()
        
        binding.errorMessage.text = message
        binding.errorMessage.isVisible = message.isNotEmpty()
    }

    /**
     * 檢查是否正在顯示
     */
    fun isShowing(): Boolean = isVisible

    /**
     * 獲取當前錯誤類型
     */
    fun getCurrentType(): ErrorType = currentType

    /**
     * 獲取當前嚴重程度
     */
    fun getCurrentSeverity(): ErrorSeverity = currentSeverity

    /**
     * 快速顯示網路錯誤
     */
    fun showNetworkError(showRetry: Boolean = true) {
        showError(
            type = ErrorType.NETWORK,
            severity = ErrorSeverity.ERROR,
            showRetry = showRetry
        )
    }

    /**
     * 快速顯示服務器錯誤
     */
    fun showServerError(showRetry: Boolean = true) {
        showError(
            type = ErrorType.SERVER,
            severity = ErrorSeverity.ERROR,
            showRetry = showRetry
        )
    }

    /**
     * 快速顯示AI處理錯誤
     */
    fun showAIError(showRetry: Boolean = true) {
        showError(
            type = ErrorType.AI_PROCESSING,
            severity = ErrorSeverity.WARNING,
            showRetry = showRetry
        )
    }
} 