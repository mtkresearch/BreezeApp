package com.mtkresearch.breezeapp_kotlin.presentation

import com.mtkresearch.breezeapp_kotlin.presentation.common.widget.MessageBubbleViewTest
import com.mtkresearch.breezeapp_kotlin.presentation.common.widget.LoadingViewTest
import com.mtkresearch.breezeapp_kotlin.presentation.common.widget.ErrorViewTest
import com.mtkresearch.breezeapp_kotlin.presentation.home.HomeFragmentTest
import com.mtkresearch.breezeapp_kotlin.presentation.settings.RuntimeSettingsFragmentTest
import com.mtkresearch.breezeapp_kotlin.presentation.settings.AppSettingsLayoutTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * BreezeApp Kotlin UI測試套件
 * 
 * 整合所有Presentation Layer的UI測試，確保應用程式UI功能的穩定性
 * 
 * 測試覆蓋範圍：
 * - 核心Activity和Fragment功能
 * - 用戶導航流程
 * - UI組件互動
 * - 設定系統功能（應用層+AI推論層）
 * - 聊天核心功能
 * - 通用UI組件（載入、錯誤、訊息氣泡）
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // P0 - 核心流程測試
    HomeFragmentTest::class,
    RuntimeSettingsFragmentTest::class,
    AppSettingsLayoutTest::class,
    
    // P1 - UI組件測試
    MessageBubbleViewTest::class,
    LoadingViewTest::class,
    ErrorViewTest::class
    
    // 待實作：
    // MainActivityTest::class,
    // ChatActivityTest::class,
    // ChatFragmentTest::class,
    // BaseActivityTest::class,
    // NavigationFlowTest::class,
    // SettingsIntegrationTest::class,
    // ThemeAndFontTest::class
)
class UITestSuite

/**
 * 測試執行指令：
 * 
 * 執行完整UI測試套件：
 * ./gradlew connectedAndroidTest --tests "com.mtkresearch.breezeapp_kotlin.presentation.UITestSuite"
 * 
 * 執行特定優先級測試：
 * ./gradlew connectedAndroidTest --tests "*Test" --info
 * 
 * 執行特定模組測試：
 * ./gradlew connectedAndroidTest --tests "*ChatActivityTest*"
 * ./gradlew connectedAndroidTest --tests "*HomeFragmentTest*"
 */ 