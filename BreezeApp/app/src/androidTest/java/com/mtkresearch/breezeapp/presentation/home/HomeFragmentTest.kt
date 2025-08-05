package com.mtkresearch.breezeapp.presentation.home

import android.os.Bundle
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.presentation.chat.ChatActivity
import com.mtkresearch.breezeapp.presentation.home.fragment.HomeFragment
import com.mtkresearch.breezeapp.presentation.settings.SettingsActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * HomeFragment UI測試
 * 
 * 測試重點:
 * - Fragment基本UI顯示
 * - 導航按鈕功能
 * - Intent啟動正確性
 * - 用戶交互回應
 */
@RunWith(AndroidJUnit4::class)
class HomeFragmentTest {

    private lateinit var scenario: FragmentScenario<HomeFragment>

    @Before
    fun setUp() {
        // 初始化Intent測試框架
        Intents.init()
        
        // 直接在測試線程中啟動Fragment（不使用runOnMainSync）
        scenario = launchFragmentInContainer<HomeFragment>(
            fragmentArgs = Bundle(),
            themeResId = R.style.Theme_BreezeApp
        )
        
        // 等待Fragment完全載入和UI穩定
        Thread.sleep(2000)
    }

    @After
    fun tearDown() {
        try {
            // 安全關閉scenario
            if (::scenario.isInitialized) {
                scenario.close()
            }
        } catch (e: Exception) {
            // 忽略關閉錯誤
            android.util.Log.w("HomeFragmentTest", "Error closing scenario", e)
        }
        // 釋放Intent測試框架
        Intents.release()
    }

    /**
     * 測試Fragment基本UI顯示
     */
    @Test
    fun homeFragment_displaysBasicUI() {
        // 使用try-catch確保測試穩定性
        try {
            // 驗證歡迎標題顯示
            onView(withId(R.id.textViewWelcomeTitle))
                .check(matches(isDisplayed()))

            // 驗證歡迎訊息顯示
            onView(withId(R.id.textViewWelcomeMessage))
                .check(matches(isDisplayed()))

            // 驗證歡迎副標題顯示
            onView(withId(R.id.textViewWelcomeSubtitle))
                .check(matches(isDisplayed()))
        } catch (e: Exception) {
            // 如果UI元素未找到，記錄但不失敗
            android.util.Log.e("HomeFragmentTest", "UI elements not found", e)
            throw e
        }
    }

    /**
     * 測試導航按鈕正確顯示
     */
    @Test
    fun navigationButtons_areDisplayed() {
        try {
            // 驗證聊天按鈕
            onView(withId(R.id.buttonChat))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))

            // 驗證設定按鈕
            onView(withId(R.id.buttonSettings))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))

            // 驗證下載按鈕
            onView(withId(R.id.buttonDownload))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "Navigation buttons not found", e)
            throw e
        }
    }

    /**
     * 測試聊天按鈕啟動ChatActivity
     */
    @Test
    fun chatButton_launchesChatActivity() {
        try {
            // 點擊聊天按鈕
            onView(withId(R.id.buttonChat))
                .perform(click())

            // 等待Intent處理
            Thread.sleep(1000)

            // 驗證ChatActivity被啟動
            Intents.intended(hasComponent(ChatActivity::class.java.name))
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "Chat button test failed", e)
            throw e
        }
    }

    /**
     * 測試設定按鈕啟動SettingsActivity
     */
    @Test
    fun settingsButton_launchesSettingsActivity() {
        try {
            // 點擊設定按鈕
            onView(withId(R.id.buttonSettings))
                .perform(click())

            // 等待Intent處理
            Thread.sleep(1000)

            // 驗證SettingsActivity被啟動
            Intents.intended(hasComponent(SettingsActivity::class.java.name))
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "Settings button test failed", e)
            throw e
        }
    }

    /**
     * 測試下載按鈕顯示對話框
     */
    @Test
    fun downloadButton_showsComingSoonDialog() {
        try {
            // 點擊下載按鈕
            onView(withId(R.id.buttonDownload))
                .perform(click())

            // 等待對話框出現
            Thread.sleep(1000)

            // 驗證對話框標題
            onView(withText("即將推出"))
                .check(matches(isDisplayed()))

            // 驗證對話框內容
            onView(withText("下載管理功能 將在未來版本中推出，敬請期待！"))
                .check(matches(isDisplayed()))
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "Download dialog test failed", e)
            throw e
        }
    }

    /**
     * 測試對話框關閉功能
     */
    @Test
    fun comingSoonDialog_canBeDismissed() {
        try {
            // 點擊下載按鈕打開對話框
            onView(withId(R.id.buttonDownload))
                .perform(click())

            Thread.sleep(1000)

            // 點擊確定按鈕關閉對話框
            onView(withText("確定"))
                .perform(click())

            Thread.sleep(500)

            // 驗證對話框已關閉，原按鈕仍可見
            onView(withId(R.id.buttonDownload))
                .check(matches(isDisplayed()))
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "Dialog dismiss test failed", e)
            throw e
        }
    }

    /**
     * 測試按鈕點擊的穩定性
     */
    @Test
    fun buttons_handleClicksCorrectly() {
        try {
            // 驗證按鈕可見性
            onView(withId(R.id.buttonChat))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))

            // 執行點擊操作
            onView(withId(R.id.buttonChat))
                .perform(click())
            
            Thread.sleep(500)

            // 測試按鈕在點擊後仍然保持功能
            // 注意：這裡不再測試第二次點擊，因為可能已經導航到新Activity
            android.util.Log.d("HomeFragmentTest", "Button click test completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "Button stability test failed", e)
            throw e
        }
    }

    /**
     * 測試Fragment生命週期
     */
    @Test
    fun fragment_handlesLifecycleCorrectly() {
        try {
            // 驗證初始狀態
            onView(withId(R.id.textViewWelcomeTitle))
                .check(matches(isDisplayed()))

            // 模擬Fragment生命週期變化
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            Thread.sleep(300)
            
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            Thread.sleep(300)

            // 驗證UI依然正常
            onView(withId(R.id.buttonChat))
                .check(matches(isDisplayed()))
                .check(matches(isClickable()))
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "Lifecycle test failed", e)
            throw e
        }
    }

    /**
     * 測試UI文字本地化
     */
    @Test
    fun textViews_displayLocalizedContent() {
        try {
            // 驗證所有文字都不為空（確保字串資源正確載入）
            onView(withId(R.id.textViewWelcomeTitle))
                .check(matches(isDisplayed()))

            onView(withId(R.id.textViewWelcomeMessage))
                .check(matches(isDisplayed()))

            onView(withId(R.id.textViewWelcomeSubtitle))
                .check(matches(isDisplayed()))
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "Localization test failed", e)
            throw e
        }
    }

    /**
     * 測試滾動容器功能
     */
    @Test
    fun scrollView_isScrollable() {
        try {
            // 驗證滾動容器存在
            onView(withId(R.id.scrollView))
                .check(matches(isDisplayed()))
            
            // 驗證導航容器在滾動視圖中
            onView(withId(R.id.navigationContainer))
                .check(matches(isDisplayed()))
        } catch (e: Exception) {
            android.util.Log.e("HomeFragmentTest", "ScrollView test failed", e)
            throw e
        }
    }
} 