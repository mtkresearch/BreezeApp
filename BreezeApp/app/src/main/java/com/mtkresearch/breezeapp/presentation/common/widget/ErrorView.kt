package com.mtkresearch.breezeapp.presentation.common.widget

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.core.utils.ColorUtils
import com.mtkresearch.breezeapp.core.utils.ErrorSeverity
import com.mtkresearch.breezeapp.core.utils.ErrorType

/**
 * 錯誤狀態UI組件
 * 
 * 功能特色:
 * - 八種錯誤類型 (網路、伺服器、驗證、權限、模型載入、AI處理、檔案存取、未知)
 * - 四種嚴重程度 (資訊、警告、錯誤、關鍵)
 * - 智能圖示和顏色管理，根據錯誤類型自動選擇
 * - 主題感知的顏色系統，確保最佳對比度
 * - 可配置重試按鈕、詳細資訊、聯絡支援
 * - 支援自訂錯誤訊息和建議操作
 * 
 * 使用方式:
 * ```kotlin
 * errorView.showError(
 *     type = ErrorType.NETWORK,
 *     message = "網路連接失敗",
 *     showRetry = true,
 *     suggestion = "請檢查網路設定並重試"
 * )
 * ```
 */
class ErrorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // UI組件
    private lateinit var errorContainer: View
    private lateinit var errorIcon: ImageView
    private lateinit var errorTitle: TextView
    private lateinit var errorMessage: TextView
    private lateinit var errorSuggestion: TextView
    private lateinit var retryButton: Button
    private lateinit var detailsButton: Button
    private lateinit var supportButton: Button

    // 當前狀態
    private var currentErrorType = ErrorType.UNKNOWN
    private var currentSeverity = ErrorSeverity.ERROR

    // 回調函數
    private var onRetryListener: (() -> Unit)? = null
    private var onDetailsListener: (() -> Unit)? = null
    private var onSupportListener: (() -> Unit)? = null

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
        LayoutInflater.from(context).inflate(R.layout.widget_error, this, true)
        
        // 綁定UI組件
        errorContainer = findViewById(R.id.errorContainer)
        errorIcon = findViewById(R.id.errorIcon)
        errorTitle = findViewById(R.id.errorTitle)
        errorMessage = findViewById(R.id.errorMessage)
        errorSuggestion = findViewById(R.id.errorSuggestion)
        retryButton = findViewById(R.id.retryButton)
        detailsButton = findViewById(R.id.detailsButton)
        supportButton = findViewById(R.id.supportButton)
        
        // 設置預設配置
        orientation = VERTICAL
        isVisible = false
    }

    /**
     * 解析XML屬性
     */
    private fun parseAttributes(attrs: AttributeSet?) {
        attrs?.let {
            val typedArray: TypedArray = context.obtainStyledAttributes(
                it, R.styleable.ErrorView, 0, 0
            )
            
            try {
                // 解析錯誤類型
                val errorTypeIndex = typedArray.getInt(
                    R.styleable.ErrorView_errorType, 
                    ErrorType.UNKNOWN.ordinal
                )
                currentErrorType = ErrorType.values()[errorTypeIndex]
                
                // 解析嚴重程度
                val severityIndex = typedArray.getInt(
                    R.styleable.ErrorView_errorSeverity,
                    ErrorSeverity.ERROR.ordinal
                )
                currentSeverity = ErrorSeverity.values()[severityIndex]
                
                // 解析是否顯示重試按鈕
                val showRetry = typedArray.getBoolean(
                    R.styleable.ErrorView_showRetry,
                    false
                )
                retryButton.isVisible = showRetry
                
                // 解析錯誤訊息
                val errorMessage = typedArray.getString(R.styleable.ErrorView_errorMessage)
                errorMessage?.let { this.errorMessage.text = it }
                
            } finally {
                typedArray.recycle()
            }
        }
    }

    /**
     * 設置點擊監聽器
     */
    private fun setupClickListeners() {
        retryButton.setOnClickListener { 
            onRetryListener?.invoke()
        }
        
        detailsButton.setOnClickListener {
            onDetailsListener?.invoke()
        }
        
        supportButton.setOnClickListener {
            onSupportListener?.invoke()
        }
    }

    /**
     * 應用預設樣式
     */
    private fun applyDefaultStyle() {
        applyErrorStyle()
        applyThemeColors()
    }

    /**
     * 顯示錯誤
     */
    fun showError(
        type: ErrorType = ErrorType.UNKNOWN,
        message: String,
        suggestion: String = "",
        showRetry: Boolean = false,
        showDetails: Boolean = false,
        showSupport: Boolean = false,
        severity: ErrorSeverity = ErrorSeverity.ERROR
    ) {
        currentErrorType = type
        currentSeverity = severity
        
        // 設置錯誤訊息
        errorMessage.text = message
        errorMessage.isVisible = message.isNotEmpty()
        
        // 設置建議文字
        if (suggestion.isNotEmpty()) {
            errorSuggestion.text = suggestion
            errorSuggestion.isVisible = true
        } else {
            errorSuggestion.isVisible = false
        }
        
        // 設置按鈕顯示
        retryButton.isVisible = showRetry
        detailsButton.isVisible = showDetails
        supportButton.isVisible = showSupport
        
        // 應用樣式
        applyErrorStyle()
        applyThemeColors()
        
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
     * 更新錯誤訊息
     */
    fun updateMessage(message: String, suggestion: String = "") {
        errorMessage.text = message
        errorMessage.isVisible = message.isNotEmpty()
        
        if (suggestion.isNotEmpty()) {
            errorSuggestion.text = suggestion
            errorSuggestion.isVisible = true
        } else if (suggestion.isEmpty()) {
            errorSuggestion.isVisible = false
        }
    }

    /**
     * 應用錯誤樣式
     */
    private fun applyErrorStyle() {
        // 設置標題和圖示
        val (titleRes, iconRes) = getErrorStyleResources(currentErrorType)
        errorTitle.setText(titleRes)
        errorIcon.setImageResource(iconRes)
        
        // 根據嚴重程度設置顏色
        val severityColors = ColorUtils.getSeverityColors(context, currentSeverity)
        
        // 設置容器背景色 (淡化版本)
        val backgroundColor = ColorUtils.adjustAlpha(severityColors.backgroundColor, 0.1f)
        errorContainer.setBackgroundColor(backgroundColor)
        
        // 設置圖示顏色
        errorIcon.imageTintList = android.content.res.ColorStateList.valueOf(severityColors.backgroundColor)
    }

    /**
     * 應用主題顏色
     */
    private fun applyThemeColors() {
        // 設置文字顏色
        val primaryTextColor = ContextCompat.getColor(context, R.color.text_primary)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        
        errorTitle.setTextColor(primaryTextColor)
        errorMessage.setTextColor(primaryTextColor)
        errorSuggestion.setTextColor(secondaryTextColor)
        
        // 設置按鈕顏色
        applyButtonTheme()
    }

    /**
     * 應用按鈕主題
     */
    private fun applyButtonTheme() {
        // 重試按鈕 - 使用主色調
        val retryColors = ColorUtils.getButtonColors(context, enabled = true)
        retryButton.setTextColor(retryColors.textColor)
        retryButton.setBackgroundColor(retryColors.backgroundColor)
        
        // 詳細資訊按鈕 - 使用次要顏色
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        detailsButton.setTextColor(secondaryTextColor)
        detailsButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline)
        
        // 聯絡支援按鈕 - 使用警告顏色
        val warningColor = ContextCompat.getColor(context, R.color.warning)
        supportButton.setTextColor(warningColor)
        supportButton.background = ContextCompat.getDrawable(context, R.drawable.bg_button_outline)
    }

    /**
     * 獲取錯誤樣式資源
     */
    private fun getErrorStyleResources(errorType: ErrorType): Pair<Int, Int> {
        return when (errorType) {
            ErrorType.NETWORK -> Pair(
                R.string.error_network_title,
                R.drawable.ic_network_error
            )
            ErrorType.SERVER -> Pair(
                R.string.error_server_title,
                R.drawable.ic_server_error
            )
            ErrorType.VALIDATION -> Pair(
                R.string.error_validation_title,
                R.drawable.ic_validation_error
            )
            ErrorType.PERMISSION -> Pair(
                R.string.error_permission_title,
                R.drawable.ic_permission_error
            )
            ErrorType.MODEL_LOADING -> Pair(
                R.string.error_model_loading_title,
                R.drawable.ic_model_error
            )
            ErrorType.AI_PROCESSING -> Pair(
                R.string.error_ai_processing_title,
                R.drawable.ic_ai_error
            )
            ErrorType.FILE_ACCESS -> Pair(
                R.string.error_file_access_title,
                R.drawable.ic_file_error
            )
            ErrorType.UNKNOWN -> Pair(
                R.string.error_unknown_title,
                R.drawable.ic_unknown_error
            )
        }
    }

    /**
     * 設置重試監聽器
     */
    fun setOnRetryListener(listener: (() -> Unit)?) {
        onRetryListener = listener
    }
    
    /**
     * 設置重試點擊監聽器 (別名)
     */
    fun setOnRetryClickListener(listener: (() -> Unit)?) {
        onRetryListener = listener
    }

    /**
     * 設置詳細資訊監聽器
     */
    fun setOnDetailsListener(listener: (() -> Unit)?) {
        onDetailsListener = listener
    }

    /**
     * 設置支援監聽器
     */
    fun setOnSupportListener(listener: (() -> Unit)?) {
        onSupportListener = listener
    }
    
    /**
     * 設置關閉點擊監聽器
     */
    fun setOnCloseClickListener(listener: (() -> Unit)?) {
        // 關閉即隱藏錯誤視圖
        setOnClickListener { 
            hide()
            listener?.invoke()
        }
    }

    /**
     * 檢查是否正在顯示
     */
    fun isShowing(): Boolean = isVisible

    /**
     * 快速顯示網路錯誤
     */
    fun showNetworkError(
        customMessage: String? = null,
        showRetry: Boolean = true
    ) {
        val message = customMessage ?: context.getString(R.string.error_network_default_message)
        val suggestion = context.getString(R.string.error_network_suggestion)
        
        showError(
            type = ErrorType.NETWORK,
            message = message,
            suggestion = suggestion,
            showRetry = showRetry,
            severity = ErrorSeverity.ERROR
        )
    }

    /**
     * 快速顯示伺服器錯誤
     */
    fun showServerError(
        customMessage: String? = null,
        showRetry: Boolean = true,
        showSupport: Boolean = true
    ) {
        val message = customMessage ?: context.getString(R.string.error_server_default_message)
        val suggestion = context.getString(R.string.error_server_suggestion)
        
        showError(
            type = ErrorType.SERVER,
            message = message,
            suggestion = suggestion,
            showRetry = showRetry,
            showSupport = showSupport,
            severity = ErrorSeverity.ERROR
        )
    }

    /**
     * 快速顯示權限錯誤
     */
    fun showPermissionError(
        customMessage: String? = null,
        showDetails: Boolean = true
    ) {
        val message = customMessage ?: context.getString(R.string.error_permission_default_message)
        val suggestion = context.getString(R.string.error_permission_suggestion)
        
        showError(
            type = ErrorType.PERMISSION,
            message = message,
            suggestion = suggestion,
            showDetails = showDetails,
            severity = ErrorSeverity.WARNING
        )
    }

    /**
     * 快速顯示AI處理錯誤
     */
    fun showAIError(
        customMessage: String? = null,
        showRetry: Boolean = true
    ) {
        val message = customMessage ?: context.getString(R.string.error_ai_default_message)
        val suggestion = context.getString(R.string.error_ai_suggestion)
        
        showError(
            type = ErrorType.AI_PROCESSING,
            message = message,
            suggestion = suggestion,
            showRetry = showRetry,
            severity = ErrorSeverity.ERROR
        )
    }

    /**
     * 快速顯示資訊訊息
     */
    fun showInfo(
        message: String,
        suggestion: String = ""
    ) {
        showError(
            type = ErrorType.UNKNOWN,
            message = message,
            suggestion = suggestion,
            showRetry = false,
            severity = ErrorSeverity.INFO
        )
    }

    /**
     * 快速顯示警告訊息
     */
    fun showWarning(
        message: String,
        suggestion: String = "",
        showDetails: Boolean = false
    ) {
        showError(
            type = ErrorType.UNKNOWN,
            message = message,
            suggestion = suggestion,
            showDetails = showDetails,
            severity = ErrorSeverity.WARNING
        )
    }

    /**
     * 驗證顏色對比度 (用於開發階段檢測)
     */
    private fun validateColorContrast() {
        // 顏色對比度驗證已移至ColorUtils，這裡保留空方法以備未來使用
    }

    companion object {
        private const val TAG = "ErrorView"
    }
} 