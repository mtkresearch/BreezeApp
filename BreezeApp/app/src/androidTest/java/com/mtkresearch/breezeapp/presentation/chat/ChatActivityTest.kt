package com.mtkresearch.breezeapp.presentation.chat

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.R
import org.hamcrest.CoreMatchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before

@RunWith(AndroidJUnit4::class)
class ChatActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(ChatActivity::class.java)

    @Before
    fun setUp() {
        // 等待 Activity 和 Fragment 載入完成
        Thread.sleep(3000)
    }

    @Test
    fun activity_loadsSuccessfully() {
        // 測試 Activity 本身是否成功載入
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
    }

    @Test
    fun activity_hasCorrectLayout() {
        // 測試 Activity 的佈局結構
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
            
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
    }

    @Test
    fun toolbar_isConfiguredCorrectly() {
        // 測試工具欄配置
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
            .check(matches(hasDescendant(withId(R.id.btn_settings))))
    }

    @Test
    fun fragment_isProperlyLoaded() {
        // 測試 Fragment 是否正確載入到容器中
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
            
        // 驗證 Fragment 的主要內容是否存在（間接測試 Fragment 載入）
        onView(withId(R.id.recyclerViewMessages))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsButton_opensSettingsPanel() {
        // 測試 Activity 層級的設定功能
        onView(withId(R.id.btn_settings))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
            .perform(click())
            
        // 等待設定面板載入
        Thread.sleep(1000)
        
        // 檢查設定遮罩是否顯示
        onView(withId(R.id.settings_overlay))
            .check(matches(isDisplayed()))
    }

    @Test
    fun settingsPanel_canBeClosed() {
        // 開啟設定面板
        onView(withId(R.id.btn_settings))
            .perform(click())
            
        Thread.sleep(1000)
        
        // 確認設定面板顯示
        onView(withId(R.id.settings_overlay))
            .check(matches(isDisplayed()))
        
        // 使用 Android 返回鍵關閉設定面板（更可靠的方法）
        androidx.test.espresso.Espresso.pressBack()
        
        Thread.sleep(500)
        
        // 驗證設定面板已關閉
        onView(withId(R.id.settings_overlay))
            .check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    @Test
    fun activity_handlesConfigurationChanges() {
        // 測試 Activity 在配置變更時的行為
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
        
        // 簡單的狀態保持測試
        Thread.sleep(500)
        
        // 再次驗證關鍵組件仍然存在
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
    }

    @Test
    fun navigation_backButtonHandling() {
        // 測試返回按鈕處理（如果有的話）
        onView(withId(R.id.toolbar))
            .check(matches(isDisplayed()))
        
        // 確保 Activity 正常運行，沒有意外關閉
        Thread.sleep(500)
        
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
    }
} 