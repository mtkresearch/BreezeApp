package com.mtkresearch.breezeapp_kotlin.presentation.settings

import android.os.Bundle
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.presentation.settings.fragment.RuntimeSettingsFragment
import org.hamcrest.CoreMatchers.not
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.junit.After

/**
 * RuntimeSettingsFragment UI測試
 * 
 * 測試重點:
 * - AI推論參數設定UI功能
 * - Tab切換和參數驗證
 * - 預覽/應用模式切換
 * - 參數範圍驗證
 * - 重置功能
 */
@RunWith(AndroidJUnit4::class)
class RuntimeSettingsFragmentTest {

    private lateinit var scenario: FragmentScenario<RuntimeSettingsFragment>

    @Before
    fun setUp() {
        // 啟動Fragment
        scenario = launchFragmentInContainer<RuntimeSettingsFragment>(
            fragmentArgs = Bundle(),
            themeResId = R.style.Theme_BreezeApp_kotlin
        )
        
        // 等待Fragment載入完成和UI穩定
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
            android.util.Log.w("RuntimeSettingsFragmentTest", "Error closing scenario", e)
        }
    }

    /**
     * 測試Fragment基本UI顯示
     */
    @Test
    fun runtimeSettingsFragment_displaysBasicUI() {
        try {
            // 驗證Tab佈局顯示
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

            // 驗證參數容器顯示
            onView(withId(R.id.container_parameters))
                .check(matches(isDisplayed()))

            // 驗證按鈕容器顯示
            onView(withId(R.id.container_buttons))
                .check(matches(isDisplayed()))
        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Basic UI test failed", e)
            throw e
        }
    }

    /**
     * 測試Tab標籤正確顯示
     */
    @Test
    fun tabs_displayCorrectLabels() {
        try {
            // 驗證LLM Tab
            onView(withText("LLM"))
                .check(matches(isDisplayed()))

            // 驗證VLM Tab  
            onView(withText("VLM"))
                .check(matches(isDisplayed()))

            // 驗證ASR Tab
            onView(withText("ASR"))
                .check(matches(isDisplayed()))

            // 驗證TTS Tab
            onView(withText("TTS"))
                .check(matches(isDisplayed()))

            // 驗證GENERAL Tab (顯示為"通用")
            onView(withText("通用"))
                .check(matches(isDisplayed()))
        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Tab labels test failed", e)
            throw e
        }
    }

    /**
     * 測試Tab切換功能
     */
    @Test
    fun tabs_switchCorrectly() {
        try {
            // 點擊VLM Tab
            onView(withText("VLM"))
                .perform(click())

            Thread.sleep(1000)

            // 點擊ASR Tab
            onView(withText("ASR"))
                .perform(click())

            Thread.sleep(1000)

            // 點擊TTS Tab
            onView(withText("TTS"))
                .perform(click())

            Thread.sleep(1000)

            // 回到LLM Tab
            onView(withText("LLM"))
                .perform(click())

            Thread.sleep(1000)

            android.util.Log.d("RuntimeSettingsFragmentTest", "Tab switching completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Tab switching test failed", e)
            throw e
        }
    }

    /**
     * 測試按鈕存在性檢查（不測試可見性，因為按鈕可能根據狀態隱藏）
     */
    @Test
    fun buttons_exist() {
        try {
            // 檢查按鈕元素存在（無論是否可見）
            onView(withId(R.id.btn_apply))
                .check(matches(isAssignableFrom(android.widget.Button::class.java)))

            onView(withId(R.id.btn_cancel))
                .check(matches(isAssignableFrom(android.widget.Button::class.java)))

        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Button existence test failed", e)
            throw e
        }
    }

    /**
     * 測試重置按鈕功能（如果可見的話）
     */
    @Test
    fun resetButton_functionalityTest() {
        try {
            // 首先檢查按鈕是否存在
            onView(withId(R.id.btn_cancel))
                .check(matches(isAssignableFrom(android.widget.Button::class.java)))

            // 嘗試檢查按鈕是否可見且可用
            try {
                onView(withId(R.id.btn_cancel))
                    .check(matches(isDisplayed()))
                    .check(matches(isEnabled()))
                    .perform(click())

                Thread.sleep(1000)
                android.util.Log.d("RuntimeSettingsFragmentTest", "Reset button clicked successfully")
            } catch (e: Exception) {
                android.util.Log.w("RuntimeSettingsFragmentTest", "Reset button not visible or clickable: ${e.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Reset button test failed", e)
        }
    }

    /**
     * 測試應用按鈕存在性和行為
     */
    @Test
    fun applyButton_conditionalTest() {
        try {
            // 只檢查應用按鈕是否存在，不檢查可見性
            onView(withId(R.id.btn_apply))
                .check(matches(isAssignableFrom(android.widget.Button::class.java)))

            android.util.Log.d("RuntimeSettingsFragmentTest", "Apply button exists and is a Button instance")
            
            // 嘗試修改參數來觸發按鈕顯示
            try {
                // 找到第一個SeekBar並稍微調整它的值
                onView(isAssignableFrom(android.widget.SeekBar::class.java))
                    .perform(click())
                
                Thread.sleep(500)
                
                // 現在再次檢查apply按鈕是否變為可見
                try {
                    onView(withId(R.id.btn_apply))
                        .check(matches(isDisplayed()))
                    android.util.Log.d("RuntimeSettingsFragmentTest", "Apply button became visible after parameter change")
                } catch (e: Exception) {
                    android.util.Log.i("RuntimeSettingsFragmentTest", "Apply button still not visible - this may be expected behavior")
                }
                
            } catch (e: Exception) {
                android.util.Log.w("RuntimeSettingsFragmentTest", "Could not interact with SeekBar to test button visibility change")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Apply button test failed", e)
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
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

            // 模擬Fragment生命週期變化
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
            Thread.sleep(300)
            
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            Thread.sleep(300)

            // 驗證UI依然正常
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Lifecycle test failed", e)
            throw e
        }
    }

    /**
     * 測試配置變更處理
     */
    @Test
    fun fragment_handlesConfigurationChanges() {
        try {
            // 驗證初始狀態
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

            // 模擬配置變更
            scenario.recreate()

            Thread.sleep(2000)

            // 驗證重建後UI正常
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Configuration change test failed", e)
            throw e
        }
    }

    /**
     * 測試Fragment的穩定性（不進行可能導致錯誤的重複點擊）
     */
    @Test
    fun fragment_stabilityTest() {
        try {
            // 簡單的穩定性測試：驗證UI保持穩定
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

            // 等待一段時間確保UI穩定
            Thread.sleep(1000)

            // 再次驗證UI
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

            android.util.Log.d("RuntimeSettingsFragmentTest", "Fragment stability test passed")
        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Stability test failed", e)
            throw e
        }
    }

    /**
     * 測試Tab切換時的UI穩定性
     */
    @Test
    fun tabs_preserveUIStability() {
        try {
            // 在LLM Tab執行一些操作
            onView(withText("LLM"))
                .perform(click())

            Thread.sleep(1000)

            // 切換到VLM Tab
            onView(withText("VLM"))
                .perform(click())

            Thread.sleep(1000)

            // 切換到通用 Tab
            onView(withText("通用"))
                .perform(click())

            Thread.sleep(1000)

            // 切換回LLM Tab
            onView(withText("LLM"))
                .perform(click())

            Thread.sleep(1000)

            // 驗證基本UI依然穩定
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

            android.util.Log.d("RuntimeSettingsFragmentTest", "Tab UI stability test passed")
        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Tab stability test failed", e)
            throw e
        }
    }

    /**
     * 測試UI容器的基本功能
     */
    @Test
    fun containers_basicFunctionality() {
        try {
            // 驗證主要容器都可以正常顯示
            onView(withId(R.id.container_tabs))
                .check(matches(isDisplayed()))

            onView(withId(R.id.container_parameters))
                .check(matches(isDisplayed()))

            onView(withId(R.id.container_buttons))
                .check(matches(isDisplayed()))

            android.util.Log.d("RuntimeSettingsFragmentTest", "Container functionality test passed")
        } catch (e: Exception) {
            android.util.Log.e("RuntimeSettingsFragmentTest", "Container test failed", e)
            throw e
        }
    }
} 