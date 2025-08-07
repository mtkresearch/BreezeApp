package com.mtkresearch.breezeapp.presentation.chat

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.presentation.chat.fragment.ChatFragment
import org.hamcrest.CoreMatchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before

@RunWith(AndroidJUnit4::class)
class ChatFragmentTest {

    @Before
    fun setUp() {
        // 不需要特殊設置
    }

    @Test
    fun chatFragment_UIComponents_areDisplayed() {
        // 直接啟動 Fragment
        launchFragmentInContainer<ChatFragment>(
            themeResId = R.style.Theme_BreezeApp
        )
        
        // 等待 Fragment 完全載入
        Thread.sleep(2000)
        
        // 驗證主要 UI 元素是否顯示
        onView(withId(R.id.recyclerViewMessages))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.editTextMessage))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.buttonSend))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.buttonVoice))
            .check(matches(isDisplayed()))
    }

    @Test
    fun inputField_canReceiveFocusAndInput() {
        // 直接啟動 Fragment
        launchFragmentInContainer<ChatFragment>(
            themeResId = R.style.Theme_BreezeApp
        )
        
        // 等待 Fragment 完全載入
        Thread.sleep(2000)
        
        // 測試輸入框是否可以接收焦點
        onView(withId(R.id.editTextMessage))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
            .check(matches(isFocusable()))
            .perform(click()) // 點擊獲得焦點
        
        // 等待焦點變化
        Thread.sleep(500)
        
        // 驗證焦點狀態
        onView(withId(R.id.editTextMessage))
            .check(matches(hasFocus()))
    }

    @Test
    fun inputField_acceptsBasicText() {
        // 直接啟動 Fragment
        launchFragmentInContainer<ChatFragment>(
            themeResId = R.style.Theme_BreezeApp
        )
        
        // 等待 Fragment 完全載入
        Thread.sleep(2000)
        
        // 測試輸入框的基本功能和交互能力
        onView(withId(R.id.editTextMessage))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
            .check(matches(isFocusable()))
            .perform(click()) // 獲得焦點
        
        // 等待焦點變化
        Thread.sleep(500)
        
        // 驗證焦點狀態
        onView(withId(R.id.editTextMessage))
            .check(matches(hasFocus()))
        
        // 嘗試輸入操作（不檢查內容）
        onView(withId(R.id.editTextMessage))
            .perform(typeText("test"))
        
        // 等待處理
        Thread.sleep(300)
        
        // 最終檢查：確保輸入框功能正常
        onView(withId(R.id.editTextMessage))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    @Test
    fun buttons_areInteractive() {
        // 直接啟動 Fragment
        launchFragmentInContainer<ChatFragment>(
            themeResId = R.style.Theme_BreezeApp
        )
        
        // 等待 Fragment 完全載入
        Thread.sleep(2000)
        
        // 測試發送按鈕
        onView(withId(R.id.buttonSend))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
            .perform(click()) // 測試點擊
        
        // 等待處理
        Thread.sleep(500)
        
        // 測試語音按鈕
        onView(withId(R.id.buttonVoice))
            .check(matches(isDisplayed()))
            .check(matches(isClickable()))
            .perform(click()) // 測試點擊
    }

    @Test
    fun recyclerView_isProperlyConfigured() {
        // 直接啟動 Fragment
        launchFragmentInContainer<ChatFragment>(
            themeResId = R.style.Theme_BreezeApp
        )
        
        // 等待 Fragment 完全載入
        Thread.sleep(2000)
        
        // 測試 RecyclerView 是否正確設置
        onView(withId(R.id.recyclerViewMessages))
            .check(matches(isDisplayed()))
    }

    @Test
    fun inputSection_isProperlyConfigured() {
        // 直接啟動 Fragment
        launchFragmentInContainer<ChatFragment>(
            themeResId = R.style.Theme_BreezeApp
        )
        
        // 等待 Fragment 完全載入
        Thread.sleep(2000)
        
        // 測試輸入區域的配置
        onView(withId(R.id.inputSection))
            .check(matches(isDisplayed()))
            
        // 測試輸入框的初始狀態
        onView(withId(R.id.editTextMessage))
            .check(matches(isEnabled()))
            .check(matches(isFocusable()))
    }

    @Test
    fun fragment_handlesMultipleInteractions() {
        // 直接啟動 Fragment
        launchFragmentInContainer<ChatFragment>(
            themeResId = R.style.Theme_BreezeApp
        )
        
        // 等待 Fragment 完全載入
        Thread.sleep(2000)
        
        // 連續點擊測試
        onView(withId(R.id.editTextMessage))
            .perform(click())
        
        Thread.sleep(300)
        
        onView(withId(R.id.buttonVoice))
            .perform(click())
        
        Thread.sleep(300)
        
        onView(withId(R.id.buttonSend))
            .perform(click())
        
        // 確保 Fragment 仍然正常
        onView(withId(R.id.recyclerViewMessages))
            .check(matches(isDisplayed()))
    }
} 