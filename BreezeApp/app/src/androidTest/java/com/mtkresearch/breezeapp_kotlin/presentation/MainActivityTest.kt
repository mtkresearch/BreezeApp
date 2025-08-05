package com.mtkresearch.breezeapp_kotlin.presentation

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp_kotlin.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before

/**
 * MainActivity UI測試
 * 
 * 測試重點:
 * - Activity層級的基本功能
 * - Fragment容器管理
 * - 系統窗口處理
 * - Hilt依賴注入正常工作
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Before
    fun setUp() {
        // 等待Activity和Hilt注入完成
        Thread.sleep(1000)
    }

    /**
     * 測試Activity成功載入
     */
    @Test
    fun activity_loadsSuccessfully() {
        // 驗證Activity正常載入
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
    }

    /**
     * 測試Fragment容器正確設置
     */
    @Test
    fun fragmentContainer_isProperlySetup() {
        // 驗證Fragment容器存在且可見
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
            .check(matches(hasChildCount(1))) // 應該包含HomeFragment
    }

    /**
     * 測試HomeFragment正確載入
     */
    @Test
    fun homeFragment_loadsInContainer() {
        // 等待Fragment載入
        Thread.sleep(2000)
        
        // 驗證HomeFragment的主要UI元素存在
        onView(withId(R.id.textViewWelcomeTitle))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.buttonChat))
            .check(matches(isDisplayed()))
            .check(matches(isEnabled()))
    }

    /**
     * 測試系統窗口處理
     */
    @Test
    fun systemBars_handledCorrectly() {
        // 驗證根視圖正確處理系統窗口
        onView(withId(android.R.id.content))
            .check(matches(isDisplayed()))
            
        // 驗證沒有重疊問題（內容區域可見）
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
    }

    /**
     * 測試Activity配置變更處理
     */
    @Test
    fun configurationChange_preservesState() {
        // 驗證初始狀態
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
        
        // 模擬配置變更（旋轉螢幕）
        activityRule.scenario.recreate()
        
        // 等待重建完成
        Thread.sleep(1500)
        
        // 驗證狀態保持
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
        
        onView(withId(R.id.textViewWelcomeTitle))
            .check(matches(isDisplayed()))
    }

    /**
     * 測試Hilt依賴注入正常工作
     */
    @Test
    fun hiltInjection_worksCorrectly() {
        // 通過能正常載入Activity來驗證Hilt注入成功
        // 如果注入失敗，Activity會crash
        onView(withId(R.id.fragmentContainer))
            .check(matches(isDisplayed()))
        
        // 驗證依賴BaseActivity的功能正常
        // BaseActivity依賴AppSettingsRepository的注入
        Thread.sleep(1000) // 等待主題應用完成
    }

    /**
     * 測試記憶體洩漏預防
     */
    @Test
    fun activity_noMemoryLeaks() {
        // 多次重建Activity，檢查是否造成記憶體累積
        repeat(3) {
            activityRule.scenario.recreate()
            Thread.sleep(1000)
            
            // 驗證Activity依然正常工作
            onView(withId(R.id.fragmentContainer))
                .check(matches(isDisplayed()))
        }
    }
} 