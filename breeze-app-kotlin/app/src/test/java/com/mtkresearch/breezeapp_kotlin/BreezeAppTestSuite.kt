package com.mtkresearch.breezeapp_kotlin

import com.mtkresearch.breezeapp_kotlin.presentation.chat.adapter.MessageAdapterTest
import com.mtkresearch.breezeapp_kotlin.presentation.chat.model.ChatMessageTest
import com.mtkresearch.breezeapp_kotlin.presentation.chat.viewmodel.ChatViewModelTest
import com.mtkresearch.breezeapp_kotlin.presentation.settings.viewmodel.RuntimeSettingsViewModelTest
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.ValidateRuntimeSettingsUseCaseTest
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.UpdateRuntimeParameterUseCaseTest
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.LoadRuntimeSettingsUseCaseTest
import com.mtkresearch.breezeapp_kotlin.domain.usecase.settings.SaveRuntimeSettingsUseCaseTest
import com.mtkresearch.breezeapp_kotlin.data.repository.RuntimeSettingsRepositoryTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.DisplayName

/**
 * BreezeApp 測試套件（JUnit 5 版）
 * 包含所有重要的單元測試
 * 
 * 測試覆蓋範圍：
 * - Presentation Layer: ViewModel 和 UI 邏輯測試
 * - Domain Layer: Use Case 業務邏輯測試  
 * - Data Layer: Repository 數據存取測試
 * 
 * Runtime Settings 模組測試：
 * - 完整的 MVVM + Use Case 架構測試
 * - 參數驗證和更新邏輯測試
 * - 數據持久化和載入測試
 * - 錯誤處理和邊界條件測試
 * 
 * JUnit 5 特性：
 * - 使用 @Nested 組織相關測試類別
 * - 支援 kotlinx.coroutines.test.runTest
 * - 改進的斷言和錯誤訊息
 * - 更好的測試報告和組織結構
 */
@DisplayName("BreezeApp 完整測試套件")
class BreezeAppTestSuite {
    
    @Nested
    @DisplayName("聊天模組測試")
    inner class ChatModuleTests {
        // ChatViewModelTest
        // MessageAdapterTest
        // ChatMessageTest
    }
    
    @Nested
    @DisplayName("Runtime Settings 模組測試")
    inner class RuntimeSettingsModuleTests {
        
        @Nested
        @DisplayName("Presentation Layer 測試")
        inner class PresentationLayerTests {
            // RuntimeSettingsViewModelTest
        }
        
        @Nested
        @DisplayName("Domain Layer Use Cases 測試")
        inner class DomainLayerTests {
            // ValidateRuntimeSettingsUseCaseTest
            // UpdateRuntimeParameterUseCaseTest
            // LoadRuntimeSettingsUseCaseTest
            // SaveRuntimeSettingsUseCaseTest
        }
        
        @Nested
        @DisplayName("Data Layer 測試")
        inner class DataLayerTests {
            // RuntimeSettingsRepositoryTest
        }
    }
}

// 注意：JUnit 5 不需要 @RunWith 和 @Suite.SuiteClasses 註解
// 測試類別會自動被測試引擎發現和執行
// 個別測試類別：
// - ChatViewModelTest
// - MessageAdapterTest  
// - ChatMessageTest
// - RuntimeSettingsViewModelTest
// - ValidateRuntimeSettingsUseCaseTest
// - UpdateRuntimeParameterUseCaseTest
// - LoadRuntimeSettingsUseCaseTest
// - SaveRuntimeSettingsUseCaseTest
// - RuntimeSettingsRepositoryTest 