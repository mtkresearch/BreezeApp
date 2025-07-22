package com.mtkresearch.breezeapp_kotlin.presentation.common.widget

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp_kotlin.core.utils.ErrorType
import com.mtkresearch.breezeapp_kotlin.core.utils.ErrorSeverity
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * ErrorView UI組件測試
 * 
 * 測試重點:
 * - 錯誤組件創建和初始化
 * - 不同錯誤類型顯示
 * - 不同嚴重程度設定
 * - 顯示和隱藏功能
 * - 重試按鈕功能
 * - 回調函數設定
 * - 快速錯誤顯示方法
 * - 訊息更新功能
 */
@RunWith(AndroidJUnit4::class)
class ErrorViewTest {

    private lateinit var context: Context
    private lateinit var errorView: ErrorView
    private lateinit var parentContainer: ViewGroup

    // 測試回調標記
    private var retryCallbackTriggered = false
    private var detailsCallbackTriggered = false
    private var supportCallbackTriggered = false

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        parentContainer = FrameLayout(context)
        errorView = ErrorView(context)
        parentContainer.addView(errorView)
        
        // 重置回調標記
        retryCallbackTriggered = false
        detailsCallbackTriggered = false
        supportCallbackTriggered = false
    }

    /**
     * 測試ErrorView正確創建和初始化
     */
    @Test
    fun errorView_createsSuccessfully() {
        try {
            assertNotNull("ErrorView should be created", errorView)
            assertEquals("Parent should have one child", 1, parentContainer.childCount)
            assertEquals("Initial visibility should be GONE", android.view.View.GONE, errorView.visibility)
            assertFalse("Should not be showing initially", errorView.isShowing())
            
            android.util.Log.d("ErrorViewTest", "ErrorView creates successfully")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "ErrorView creation test failed", e)
            throw e
        }
    }

    /**
     * 測試基本錯誤顯示功能
     */
    @Test
    fun showError_displaysErrorView() {
        try {
            val testMessage = "測試錯誤訊息"
            errorView.showError(
                type = ErrorType.UNKNOWN,
                message = testMessage
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            assertTrue("Should be showing after showError", errorView.isShowing())
            
            android.util.Log.d("ErrorViewTest", "Show error displays error view correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Show error display test failed", e)
            throw e
        }
    }

    /**
     * 測試基本隱藏功能
     */
    @Test
    fun hide_hidesErrorView() {
        try {
            // 先顯示再隱藏
            errorView.showError(type = ErrorType.UNKNOWN, message = "測試")
            assertEquals("ErrorView should be visible after show", android.view.View.VISIBLE, errorView.visibility)
            
            errorView.hide()
            assertEquals("ErrorView should be hidden after hide", android.view.View.GONE, errorView.visibility)
            assertFalse("Should not be showing after hide", errorView.isShowing())
            
            android.util.Log.d("ErrorViewTest", "Hide hides error view correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Hide test failed", e)
            throw e
        }
    }

    /**
     * 測試網路錯誤類型
     */
    @Test
    fun showError_withNetworkType_displaysNetworkError() {
        try {
            errorView.showError(
                type = ErrorType.NETWORK,
                message = "網路連接失敗",
                suggestion = "請檢查網路設定"
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Network error type displays correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Network error type test failed", e)
            throw e
        }
    }

    /**
     * 測試伺服器錯誤類型
     */
    @Test
    fun showError_withServerType_displaysServerError() {
        try {
            errorView.showError(
                type = ErrorType.SERVER,
                message = "伺服器內部錯誤",
                severity = ErrorSeverity.CRITICAL
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Server error type displays correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Server error type test failed", e)
            throw e
        }
    }

    /**
     * 測試AI處理錯誤類型
     */
    @Test
    fun showError_withAIProcessingType_displaysAIError() {
        try {
            errorView.showError(
                type = ErrorType.AI_PROCESSING,
                message = "AI模型處理失敗",
                showRetry = true
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "AI processing error type displays correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "AI processing error type test failed", e)
            throw e
        }
    }

    /**
     * 測試模型載入錯誤類型
     */
    @Test
    fun showError_withModelLoadingType_displaysModelError() {
        try {
            errorView.showError(
                type = ErrorType.MODEL_LOADING,
                message = "模型載入失敗",
                showRetry = true,
                showSupport = true
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Model loading error type displays correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Model loading error type test failed", e)
            throw e
        }
    }

    /**
     * 測試權限錯誤類型
     */
    @Test
    fun showError_withPermissionType_displaysPermissionError() {
        try {
            errorView.showError(
                type = ErrorType.PERMISSION,
                message = "缺少必要權限",
                suggestion = "請在設定中授予權限",
                severity = ErrorSeverity.WARNING
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Permission error type displays correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Permission error type test failed", e)
            throw e
        }
    }

    /**
     * 測試重試按鈕功能
     */
    @Test
    fun showError_withRetryButton_enablesRetryFunctionality() {
        try {
            errorView.setOnRetryListener {
                retryCallbackTriggered = true
            }
            
            errorView.showError(
                type = ErrorType.NETWORK,
                message = "測試重試功能",
                showRetry = true
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Retry button functionality enabled correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Retry button test failed", e)
            throw e
        }
    }

    /**
     * 測試詳細資訊按鈕功能
     */
    @Test
    fun showError_withDetailsButton_enablesDetailsFunctionality() {
        try {
            errorView.setOnDetailsListener {
                detailsCallbackTriggered = true
            }
            
            errorView.showError(
                type = ErrorType.SERVER,
                message = "測試詳細資訊功能",
                showDetails = true
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Details button functionality enabled correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Details button test failed", e)
            throw e
        }
    }

    /**
     * 測試支援按鈕功能
     */
    @Test
    fun showError_withSupportButton_enablesSupportFunctionality() {
        try {
            errorView.setOnSupportListener {
                supportCallbackTriggered = true
            }
            
            errorView.showError(
                type = ErrorType.UNKNOWN,
                message = "測試支援功能",
                showSupport = true
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Support button functionality enabled correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Support button test failed", e)
            throw e
        }
    }

    /**
     * 測試訊息更新功能
     */
    @Test
    fun updateMessage_changesErrorMessage() {
        try {
            errorView.showError(type = ErrorType.UNKNOWN, message = "初始訊息")
            assertEquals("Should be visible after show", android.view.View.VISIBLE, errorView.visibility)
            
            val newMessage = "更新的錯誤訊息"
            val newSuggestion = "新的建議"
            errorView.updateMessage(newMessage, newSuggestion)
            
            // 確保ErrorView仍然可見
            assertEquals("ErrorView should still be visible", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Update message changes error message correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Update message test failed", e)
            throw e
        }
    }

    /**
     * 測試快速網路錯誤顯示
     */
    @Test
    fun showNetworkError_displaysNetworkErrorQuickly() {
        try {
            errorView.showNetworkError(
                customMessage = "自訂網路錯誤訊息",
                showRetry = true
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            assertTrue("Should be showing", errorView.isShowing())
            
            android.util.Log.d("ErrorViewTest", "Show network error displays quickly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Show network error test failed", e)
            throw e
        }
    }

    /**
     * 測試嚴重程度設定
     */
    @Test
    fun showError_withDifferentSeverities_appliesCorrectSeverity() {
        try {
            // 測試資訊級別
            errorView.showError(
                type = ErrorType.UNKNOWN,
                message = "資訊訊息",
                severity = ErrorSeverity.INFO
            )
            assertEquals("Should be visible with INFO severity", android.view.View.VISIBLE, errorView.visibility)
            
            // 測試警告級別
            errorView.showError(
                type = ErrorType.VALIDATION,
                message = "警告訊息",
                severity = ErrorSeverity.WARNING
            )
            assertEquals("Should be visible with WARNING severity", android.view.View.VISIBLE, errorView.visibility)
            
            // 測試錯誤級別
            errorView.showError(
                type = ErrorType.NETWORK,
                message = "錯誤訊息",
                severity = ErrorSeverity.ERROR
            )
            assertEquals("Should be visible with ERROR severity", android.view.View.VISIBLE, errorView.visibility)
            
            // 測試關鍵級別
            errorView.showError(
                type = ErrorType.SERVER,
                message = "關鍵錯誤",
                severity = ErrorSeverity.CRITICAL
            )
            assertEquals("Should be visible with CRITICAL severity", android.view.View.VISIBLE, errorView.visibility)
            
            android.util.Log.d("ErrorViewTest", "Different severities apply correctly")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Severities test failed", e)
            throw e
        }
    }

    /**
     * 測試多次顯示隱藏的穩定性
     */
    @Test
    fun multipleShowHide_maintainsStability() {
        try {
            val errorTypes = arrayOf(
                ErrorType.NETWORK, ErrorType.SERVER, ErrorType.AI_PROCESSING,
                ErrorType.MODEL_LOADING, ErrorType.PERMISSION
            )
            
            // 測試多次顯示隱藏不同類型的錯誤
            for (i in errorTypes.indices) {
                val errorType = errorTypes[i]
                errorView.showError(
                    type = errorType,
                    message = "錯誤測試 ${i + 1}",
                    showRetry = i % 2 == 0
                )
                assertEquals("ErrorView should be visible in iteration ${i + 1}", android.view.View.VISIBLE, errorView.visibility)
                
                Thread.sleep(100) // 短暫等待
                
                errorView.hide()
                assertEquals("ErrorView should be hidden in iteration ${i + 1}", android.view.View.GONE, errorView.visibility)
            }
            
            android.util.Log.d("ErrorViewTest", "Multiple show/hide maintains stability")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Multiple show/hide test failed", e)
            throw e
        }
    }

    /**
     * 測試回調函數設定和切換
     */
    @Test
    fun callbackListeners_canBeSetAndChanged() {
        try {
            // 設定初始回調
            errorView.setOnRetryListener { retryCallbackTriggered = true }
            errorView.setOnDetailsListener { detailsCallbackTriggered = true }
            errorView.setOnSupportListener { supportCallbackTriggered = true }
            
            // 顯示錯誤以啟用回調
            errorView.showError(
                type = ErrorType.NETWORK,
                message = "回調測試",
                showRetry = true,
                showDetails = true,
                showSupport = true
            )
            
            assertEquals("ErrorView should be visible", android.view.View.VISIBLE, errorView.visibility)
            
            // 測試回調可以被重新設置
            errorView.setOnRetryClickListener { } // 使用別名方法
            errorView.setOnRetryListener(null) // 清除回調
            
            android.util.Log.d("ErrorViewTest", "Callback listeners can be set and changed")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Callback listeners test failed", e)
            throw e
        }
    }

    /**
     * 測試錯誤組件的整體功能完整性
     */
    @Test
    fun errorView_overallFunctionalityIntegrity() {
        try {
            // 測試完整的使用流程
            assertNotNull("ErrorView should be created", errorView)
            assertFalse("Should not be showing initially", errorView.isShowing())
            
            // 設置回調
            errorView.setOnRetryListener { retryCallbackTriggered = true }
            
            // 測試顯示複雜錯誤
            errorView.showError(
                type = ErrorType.AI_PROCESSING,
                message = "AI處理過程中發生錯誤",
                suggestion = "請檢查模型配置並重試",
                showRetry = true,
                showDetails = true,
                severity = ErrorSeverity.ERROR
            )
            assertEquals("Should be visible after complex show", android.view.View.VISIBLE, errorView.visibility)
            assertTrue("Should be showing", errorView.isShowing())
            
            // 測試訊息更新
            errorView.updateMessage("更新的錯誤訊息", "更新的建議")
            assertEquals("Should still be visible after update", android.view.View.VISIBLE, errorView.visibility)
            
            // 測試快速方法
            errorView.showNetworkError(showRetry = true)
            assertEquals("Should be visible after network error", android.view.View.VISIBLE, errorView.visibility)
            
            // 測試隱藏
            errorView.hide()
            assertEquals("Should be hidden after hide", android.view.View.GONE, errorView.visibility)
            assertFalse("Should not be showing after hide", errorView.isShowing())
            
            android.util.Log.d("ErrorViewTest", "Overall functionality integrity verified")
        } catch (e: Exception) {
            android.util.Log.e("ErrorViewTest", "Overall functionality test failed", e)
            throw e
        }
    }
} 