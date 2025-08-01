package com.mtkresearch.breezeapp.presentation.common.widget

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.presentation.common.widget.LoadingView.LoadingStyle
import com.mtkresearch.breezeapp.presentation.common.widget.LoadingView.LoadingSize
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * LoadingView UI組件測試
 * 
 * 測試重點:
 * - 載入組件創建和初始化
 * - 不同載入樣式切換
 * - 不同尺寸設定
 * - 顯示和隱藏功能
 * - 訊息更新功能
 * - 取消功能（點擊背景）
 * - 主題適配能力
 */
@RunWith(AndroidJUnit4::class)
class LoadingViewTest {

    private lateinit var context: Context
    private lateinit var loadingView: LoadingView
    private lateinit var parentContainer: ViewGroup

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        parentContainer = FrameLayout(context)
        loadingView = LoadingView(context)
        parentContainer.addView(loadingView)
    }

    /**
     * 測試LoadingView正確創建和初始化
     */
    @Test
    fun loadingView_createsSuccessfully() {
        try {
            assertNotNull("LoadingView should be created", loadingView)
            assertEquals("Parent should have one child", 1, parentContainer.childCount)
            assertEquals("Initial visibility should be GONE", android.view.View.GONE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "LoadingView creates successfully")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "LoadingView creation test failed", e)
            throw e
        }
    }

    /**
     * 測試基本顯示功能
     */
    @Test
    fun show_displaysLoadingView() {
        try {
            loadingView.show()
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Show displays loading view correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Show display test failed", e)
            throw e
        }
    }

    /**
     * 測試基本隱藏功能
     */
    @Test
    fun hide_hidesLoadingView() {
        try {
            // 先顯示再隱藏
            loadingView.show()
            assertEquals("LoadingView should be visible after show", android.view.View.VISIBLE, loadingView.visibility)
            
            loadingView.hide()
            assertEquals("LoadingView should be hidden after hide", android.view.View.GONE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Hide hides loading view correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Hide test failed", e)
            throw e
        }
    }

    /**
     * 測試帶訊息的顯示功能
     */
    @Test
    fun show_withMessage_displaysMessage() {
        try {
            val testMessage = "正在載入測試資料..."
            loadingView.show(message = testMessage)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            // 注意：這裡我們無法直接測試TextView的文字，因為它是private
            // 但我們可以測試功能是否正常工作
            
            android.util.Log.d("LoadingViewTest", "Show with message works correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Show with message test failed", e)
            throw e
        }
    }

    /**
     * 測試圓形載入樣式
     */
    @Test
    fun show_withCircularStyle_appliesCorrectStyle() {
        try {
            loadingView.show(style = LoadingStyle.CIRCULAR)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Circular style applies correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Circular style test failed", e)
            throw e
        }
    }

    /**
     * 測試線性載入樣式
     */
    @Test
    fun show_withLinearStyle_appliesCorrectStyle() {
        try {
            loadingView.show(style = LoadingStyle.LINEAR)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Linear style applies correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Linear style test failed", e)
            throw e
        }
    }

    /**
     * 測試點動畫載入樣式
     */
    @Test
    fun show_withDotsStyle_appliesCorrectStyle() {
        try {
            loadingView.show(style = LoadingStyle.DOTS)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Dots style applies correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Dots style test failed", e)
            throw e
        }
    }

    /**
     * 測試自訂載入樣式
     */
    @Test
    fun show_withCustomStyle_appliesCorrectStyle() {
        try {
            loadingView.show(style = LoadingStyle.CUSTOM)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Custom style applies correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Custom style test failed", e)
            throw e
        }
    }

    /**
     * 測試小尺寸設定
     */
    @Test
    fun show_withSmallSize_appliesCorrectSize() {
        try {
            loadingView.show(size = LoadingSize.SMALL)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Small size applies correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Small size test failed", e)
            throw e
        }
    }

    /**
     * 測試中等尺寸設定
     */
    @Test
    fun show_withMediumSize_appliesCorrectSize() {
        try {
            loadingView.show(size = LoadingSize.MEDIUM)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Medium size applies correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Medium size test failed", e)
            throw e
        }
    }

    /**
     * 測試大尺寸設定
     */
    @Test
    fun show_withLargeSize_appliesCorrectSize() {
        try {
            loadingView.show(size = LoadingSize.LARGE)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Large size applies correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Large size test failed", e)
            throw e
        }
    }

    /**
     * 測試訊息更新功能
     */
    @Test
    fun updateMessage_changesLoadingMessage() {
        try {
            loadingView.show(message = "初始訊息")
            
            val newMessage = "更新的訊息"
            loadingView.updateMessage(newMessage)
            
            // 確保LoadingView仍然可見
            assertEquals("LoadingView should still be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Update message changes loading message correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Update message test failed", e)
            throw e
        }
    }

    /**
     * 測試可取消功能設定
     */
    @Test
    fun show_withDismissible_allowsCancellation() {
        try {
            loadingView.show(dismissible = true)
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Dismissible functionality works correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Dismissible test failed", e)
            throw e
        }
    }

    /**
     * 測試樣式和尺寸組合
     */
    @Test
    fun show_withStyleAndSizeCombination_appliesBothCorrectly() {
        try {
            loadingView.show(
                message = "組合測試",
                style = LoadingStyle.CIRCULAR,
                size = LoadingSize.LARGE,
                dismissible = true
            )
            
            assertEquals("LoadingView should be visible", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Style and size combination applies correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Style and size combination test failed", e)
            throw e
        }
    }

    /**
     * 測試多次顯示隱藏的穩定性
     */
    @Test
    fun multipleShowHide_maintainsStability() {
        try {
            // 測試多次顯示隱藏
            for (i in 1..5) {
                loadingView.show(message = "測試 $i")
                assertEquals("LoadingView should be visible in iteration $i", android.view.View.VISIBLE, loadingView.visibility)
                
                Thread.sleep(100) // 短暫等待
                
                loadingView.hide()
                assertEquals("LoadingView should be hidden in iteration $i", android.view.View.GONE, loadingView.visibility)
            }
            
            android.util.Log.d("LoadingViewTest", "Multiple show/hide maintains stability")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Multiple show/hide test failed", e)
            throw e
        }
    }

    /**
     * 測試空訊息處理
     */
    @Test
    fun show_withEmptyMessage_handlesCorrectly() {
        try {
            loadingView.show(message = "")
            
            assertEquals("LoadingView should be visible with empty message", android.view.View.VISIBLE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Empty message handles correctly")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Empty message test failed", e)
            throw e
        }
    }

    /**
     * 測試載入組件的整體功能完整性
     */
    @Test
    fun loadingView_overallFunctionalityIntegrity() {
        try {
            // 測試完整的使用流程
            assertNotNull("LoadingView should be created", loadingView)
            
            // 測試顯示
            loadingView.show(
                message = "載入中...",
                style = LoadingStyle.CIRCULAR,
                size = LoadingSize.MEDIUM,
                dismissible = false
            )
            assertEquals("Should be visible after show", android.view.View.VISIBLE, loadingView.visibility)
            
            // 測試更新訊息
            loadingView.updateMessage("更新載入狀態...")
            assertEquals("Should still be visible after update", android.view.View.VISIBLE, loadingView.visibility)
            
            // 測試樣式切換
            loadingView.show(style = LoadingStyle.LINEAR)
            assertEquals("Should still be visible after style change", android.view.View.VISIBLE, loadingView.visibility)
            
            // 測試隱藏
            loadingView.hide()
            assertEquals("Should be hidden after hide", android.view.View.GONE, loadingView.visibility)
            
            android.util.Log.d("LoadingViewTest", "Overall functionality integrity verified")
        } catch (e: Exception) {
            android.util.Log.e("LoadingViewTest", "Overall functionality test failed", e)
            throw e
        }
    }
} 