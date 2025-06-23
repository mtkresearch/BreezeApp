package com.mtkresearch.breezeapp_kotlin.presentation.settings.fragment

import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.ParameterCategory
import com.mtkresearch.breezeapp_kotlin.presentation.settings.model.RuntimeApplyStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * RuntimeSettingsFragment 單元測試
 *
 * 測試範圍:
 * - Fragment 實例創建
 * - 枚舉類別驗證
 * - 基本功能測試
 */
@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class RuntimeSettingsFragmentTest {
    
    @Before
    fun setup() {
        // Setup for basic tests
    }

    @After
    fun teardown() {
        // Cleanup
    }

    // ================================
    // ParameterCategory 枚舉測試
    // ================================

    @Test
    fun `parameter category should have correct values`() {
        val categories = ParameterCategory.values()
        
        assertEquals("Should have 5 categories", 5, categories.size)
        assertEquals("LLM category display name", "大語言模型", ParameterCategory.LLM.displayName)
        assertEquals("VLM category display name", "視覺語言模型", ParameterCategory.VLM.displayName)
        assertEquals("ASR category display name", "語音識別", ParameterCategory.ASR.displayName)
        assertEquals("TTS category display name", "語音合成", ParameterCategory.TTS.displayName)
        assertEquals("GENERAL category display name", "通用設定", ParameterCategory.GENERAL.displayName)
    }

    @Test
    fun `parameter category should find by display name`() {
        assertEquals("Should find LLM by display name", 
            ParameterCategory.LLM, ParameterCategory.fromDisplayName("大語言模型"))
        assertEquals("Should find VLM by display name", 
            ParameterCategory.VLM, ParameterCategory.fromDisplayName("視覺語言模型"))
        assertNull("Should return null for invalid display name", 
            ParameterCategory.fromDisplayName("不存在的類別"))
    }

    // ================================
    // RuntimeApplyStatus 枚舉測試
    // ================================

    @Test
    fun `runtime apply status should have correct values`() {
        val statuses = RuntimeApplyStatus.values()
        
        assertEquals("Should have 5 statuses", 5, statuses.size)
        assertEquals("IDLE status display name", "就緒", RuntimeApplyStatus.IDLE.displayName)
        assertEquals("APPLYING status display name", "應用中", RuntimeApplyStatus.APPLYING.displayName)
        assertEquals("SUCCESS status display name", "應用成功", RuntimeApplyStatus.SUCCESS.displayName)
        assertEquals("ERROR status display name", "應用失敗", RuntimeApplyStatus.ERROR.displayName)
        assertEquals("VALIDATION_ERROR status display name", "驗證錯誤", RuntimeApplyStatus.VALIDATION_ERROR.displayName)
    }

    @Test
    fun `runtime apply status should have correct state methods`() {
        assertTrue("APPLYING should be in progress", RuntimeApplyStatus.APPLYING.isInProgress())
        assertFalse("IDLE should not be in progress", RuntimeApplyStatus.IDLE.isInProgress())
        
        assertTrue("ERROR should be error", RuntimeApplyStatus.ERROR.isError())
        assertTrue("VALIDATION_ERROR should be error", RuntimeApplyStatus.VALIDATION_ERROR.isError())
        assertFalse("SUCCESS should not be error", RuntimeApplyStatus.SUCCESS.isError())
        
        assertTrue("SUCCESS should be success", RuntimeApplyStatus.SUCCESS.isSuccess())
        assertFalse("ERROR should not be success", RuntimeApplyStatus.ERROR.isSuccess())
    }

    // ================================
    // Fragment 實例創建測試
    // ================================

    @Test
    fun `should create fragment instance successfully`() {
        val fragment = RuntimeSettingsFragment()
        assertNotNull("Fragment instance should be created", fragment)
    }

    @Test
    fun `fragment should implement correct interfaces`() {
        val fragment = RuntimeSettingsFragment()
        // 基本檢查 - 確保是 Fragment 的子類
        assertTrue("Should be a Fragment", fragment is androidx.fragment.app.Fragment)
    }

    @Test
    fun `newInstance should create fragment with correct type`() {
        val fragment = RuntimeSettingsFragment.newInstance()
        
        assertNotNull("Fragment should be created", fragment)
        assertTrue("Should be RuntimeSettingsFragment instance", 
            fragment is RuntimeSettingsFragment)
    }

    @Test
    fun `multiple instances should be independent`() {
        val fragment1 = RuntimeSettingsFragment.newInstance()
        val fragment2 = RuntimeSettingsFragment.newInstance()
        
        assertNotSame("Instances should be different", fragment1, fragment2)
        
        // 確保兩個實例都是有效的
        assertNotNull("First instance should be valid", fragment1)
        assertNotNull("Second instance should be valid", fragment2)
    }

    // ================================
    // 基本功能測試
    // ================================

    @Test
    fun `should handle basic operations without crashing`() {
        val fragment = RuntimeSettingsFragment()
        
        // 測試基本操作不會導致崩潰
        assertDoesNotThrow("Should create fragment safely") {
            fragment.toString()
        }
    }

    @Test
    fun `should create multiple fragments without issues`() {
        val fragments = mutableListOf<RuntimeSettingsFragment>()
        
        assertDoesNotThrow("Should create multiple fragments") {
            repeat(5) {
                fragments.add(RuntimeSettingsFragment.newInstance())
            }
        }
        
        assertEquals("Should create correct number of fragments", 5, fragments.size)
        fragments.forEach { fragment ->
            assertNotNull("Each fragment should be valid", fragment)
        }
    }

    // ================================
    // 輔助方法
    // ================================

    private fun assertDoesNotThrow(message: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            fail("$message, but threw: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "RuntimeSettingsFragmentTest"
    }
} 