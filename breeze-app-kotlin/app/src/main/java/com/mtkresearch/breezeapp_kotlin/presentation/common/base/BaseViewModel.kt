package com.mtkresearch.breezeapp_kotlin.presentation.common.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI狀態枚舉
 */
enum class UiState {
    IDLE,       // 閒置狀態
    LOADING,    // 載入中
    SUCCESS,    // 成功
    ERROR       // 錯誤
}

/**
 * 基礎UI狀態數據類別
 */
data class BaseUiState(
    val state: UiState = UiState.IDLE,
    val message: String = "",
    val isLoading: Boolean = false,
    val error: Throwable? = null
)

/**
 * BaseViewModel - 所有ViewModel的基礎類別
 * 
 * 提供統一的功能：
 * - 狀態管理 (Loading, Error, Success)
 * - 協程異常處理
 * - 通用UI狀態
 * - 錯誤處理機制
 */
abstract class BaseViewModel : ViewModel() {

    // 基礎UI狀態
    private val _uiState = MutableStateFlow(BaseUiState())
    val uiState: StateFlow<BaseUiState> = _uiState.asStateFlow()

    // Loading狀態
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 錯誤狀態
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // 成功訊息
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // 協程異常處理器
    protected val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        handleError(exception)
    }

    /**
     * 設置Loading狀態
     */
    protected fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
        _uiState.value = _uiState.value.copy(
            isLoading = isLoading,
            state = if (isLoading) UiState.LOADING else UiState.IDLE
        )
    }

    /**
     * 設置錯誤狀態
     */
    protected fun setError(message: String, throwable: Throwable? = null) {
        _error.value = message
        _isLoading.value = false
        _uiState.value = _uiState.value.copy(
            state = UiState.ERROR,
            message = message,
            isLoading = false,
            error = throwable
        )
    }

    /**
     * 設置成功狀態
     */
    protected fun setSuccess(message: String = "") {
        _successMessage.value = message.takeIf { it.isNotEmpty() }
        _isLoading.value = false
        _error.value = null
        _uiState.value = _uiState.value.copy(
            state = UiState.SUCCESS,
            message = message,
            isLoading = false,
            error = null
        )
    }

    /**
     * 重置狀態為閒置
     */
    protected fun setIdle() {
        _isLoading.value = false
        _error.value = null
        _successMessage.value = null
        _uiState.value = BaseUiState()
    }

    /**
     * 清除錯誤狀態
     */
    fun clearError() {
        _error.value = null
        if (_uiState.value.state == UiState.ERROR) {
            _uiState.value = _uiState.value.copy(
                state = UiState.IDLE,
                message = "",
                error = null
            )
        }
    }

    /**
     * 清除成功訊息
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
        if (_uiState.value.state == UiState.SUCCESS) {
            _uiState.value = _uiState.value.copy(
                state = UiState.IDLE,
                message = ""
            )
        }
    }

    /**
     * 安全執行協程操作
     * 自動處理Loading狀態和錯誤
     */
    protected fun launchSafely(
        showLoading: Boolean = true,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch(exceptionHandler) {
            try {
                if (showLoading) setLoading(true)
                block()
                if (showLoading) setLoading(false)
            } catch (e: Exception) {
                if (showLoading) setLoading(false)
                onError?.invoke(e) ?: handleError(e)
            }
        }
    }

    /**
     * 執行帶結果的協程操作
     */
    protected fun <T> launchWithResult(
        showLoading: Boolean = true,
        onSuccess: (T) -> Unit,
        onError: ((Throwable) -> Unit)? = null,
        block: suspend () -> T
    ) {
        viewModelScope.launch(exceptionHandler) {
            try {
                if (showLoading) setLoading(true)
                val result = block()
                if (showLoading) setLoading(false)
                onSuccess(result)
            } catch (e: Exception) {
                if (showLoading) setLoading(false)
                onError?.invoke(e) ?: handleError(e)
            }
        }
    }

    /**
     * 統一錯誤處理
     */
    protected open fun handleError(throwable: Throwable) {
        val errorMessage = when (throwable) {
            is IllegalArgumentException -> "參數錯誤: ${throwable.message}"
            is IllegalStateException -> "狀態錯誤: ${throwable.message}"
            is SecurityException -> "權限不足: ${throwable.message}"
            is java.net.UnknownHostException -> "網路連接失敗，請檢查網路設定"
            is java.net.SocketTimeoutException -> "網路請求超時，請重試"
            is java.io.IOException -> "網路錯誤: ${throwable.message}"
            else -> throwable.message ?: "發生未知錯誤"
        }
        setError(errorMessage, throwable)
    }

    /**
     * 重試機制
     * 子類別可以覆寫此方法實現重試邏輯
     */
    open fun retry() {
        clearError()
        // 子類別可以覆寫實現具體重試邏輯
    }

    /**
     * 驗證輸入數據
     */
    protected fun validateInput(condition: Boolean, errorMessage: String): Boolean {
        if (!condition) {
            setError(errorMessage)
            return false
        }
        return true
    }

    /**
     * 檢查是否為空字串
     */
    protected fun String?.isNotNullOrEmpty(): Boolean {
        return !this.isNullOrEmpty()
    }

    /**
     * 安全轉換數據
     */
    protected inline fun <T> safeCall(
        noinline onError: ((Exception) -> Unit)? = null,
        block: () -> T
    ): T? {
        return try {
            block()
        } catch (e: Exception) {
            onError?.invoke(e) ?: handleError(e)
            null
        }
    }

    /**
     * ViewModel清理時的處理
     */
    override fun onCleared() {
        super.onCleared()
        // 子類別可以在此進行額外清理
        onViewModelCleared()
    }

    /**
     * 清理資源
     * 子類別可以覆寫此方法進行自訂清理
     */
    protected open fun onViewModelCleared() {
        // 預設空實作
    }

    /**
     * 工具方法：格式化錯誤訊息
     */
    protected fun formatErrorMessage(prefix: String, throwable: Throwable): String {
        return "$prefix: ${throwable.message ?: "未知錯誤"}"
    }

    /**
     * 工具方法：記錄日誌
     */
    protected fun logError(tag: String, message: String, throwable: Throwable? = null) {
        // 這裡可以整合日誌框架，如Timber
        println("[$tag] ERROR: $message ${throwable?.message ?: ""}")
    }

    /**
     * 工具方法：記錄信息
     */
    protected fun logInfo(tag: String, message: String) {
        println("[$tag] INFO: $message")
    }
} 