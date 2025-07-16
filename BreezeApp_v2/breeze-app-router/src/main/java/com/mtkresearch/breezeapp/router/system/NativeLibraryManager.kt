package com.mtkresearch.breezeapp.router.system

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MTK 原生庫管理器
 * 負責載入和管理 MTK JNI 庫，移除了 libsigchain 依賴
 * 
 * 改進點：
 * - 移除 libsigchain.so 依賴
 * - 移除 100ms 延遲邏輯
 * - 簡化為僅載入 llm_jni.so
 * - 增加錯誤處理和狀態管理
 */
class NativeLibraryManager {
    
    companion object {
        private const val TAG = "NativeLibraryManager"
        private const val LLM_JNI_LIBRARY = "llm_jni"
        
        // 單例實例
        @Volatile
        private var INSTANCE: NativeLibraryManager? = null
        
        fun getInstance(): NativeLibraryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NativeLibraryManager().also { INSTANCE = it }
            }
        }
    }
    
    // 庫載入狀態
    private val isLibraryLoaded = AtomicBoolean(false)
    private val loadAttempted = AtomicBoolean(false)
    
    // 載入結果
    private var loadResult: LibraryLoadResult? = null
    
    /**
     * 載入 MTK JNI 庫
     * 
     * @return LibraryLoadResult 載入結果
     */
    fun loadMTKLibrary(): LibraryLoadResult {
        // 如果已經載入成功，直接返回成功結果
        if (isLibraryLoaded.get()) {
            return LibraryLoadResult.Success("Library already loaded")
        }
        
        // 如果已經嘗試過載入但失敗，返回之前的結果
        if (loadAttempted.get() && loadResult is LibraryLoadResult.Error) {
            return loadResult!!
        }
        
        loadAttempted.set(true)
        
        return try {
            Log.d(TAG, "Loading MTK JNI library: $LLM_JNI_LIBRARY")
            
            // 載入 llm_jni.so
            System.loadLibrary(LLM_JNI_LIBRARY)
            
            // 驗證庫是否正確載入
            if (verifyLibraryLoaded()) {
                isLibraryLoaded.set(true)
                loadResult = LibraryLoadResult.Success("MTK JNI library loaded successfully")
                Log.i(TAG, "MTK JNI library loaded successfully")
                loadResult!!
            } else {
                val error = "Library loaded but verification failed"
                loadResult = LibraryLoadResult.Error(error, null)
                Log.e(TAG, error)
                loadResult!!
            }
            
        } catch (e: UnsatisfiedLinkError) {
            val error = "Failed to load MTK JNI library"
            loadResult = LibraryLoadResult.Error(error, e)
            Log.e(TAG, error, e)
            loadResult!!
        } catch (e: Exception) {
            val error = "Unexpected error during library loading"
            loadResult = LibraryLoadResult.Error(error, e)
            Log.e(TAG, error, e)
            loadResult!!
        }
    }
    
    /**
     * 檢查庫是否已載入
     * 
     * @return Boolean 是否已載入
     */
    fun isLibraryLoaded(): Boolean {
        return isLibraryLoaded.get()
    }
    
    /**
     * 檢查庫是否可用（靜態方法）
     * 
     * @param libraryName 庫名稱
     * @return Boolean 是否可用
     */
    fun isLibraryAvailable(libraryName: String): Boolean {
        return try {
            System.loadLibrary(libraryName)
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
    
    /**
     * 重置載入狀態（主要用於測試）
     */
    fun resetLoadState() {
        isLibraryLoaded.set(false)
        loadAttempted.set(false)
        loadResult = null
        Log.d(TAG, "Library load state reset")
    }
    
    /**
     * 獲取載入結果詳情
     * 
     * @return LibraryLoadResult? 載入結果
     */
    fun getLoadResult(): LibraryLoadResult? {
        return loadResult
    }
    
    /**
     * 驗證庫是否正確載入
     * 可以通過嘗試調用一個簡單的 JNI 方法來驗證
     * 
     * @return Boolean 是否驗證成功
     */
    private fun verifyLibraryLoaded(): Boolean {
        return try {
            // 這裡可以調用一個簡單的 JNI 方法來驗證
            // 例如：nativeGetVersion() 或其他不需要初始化的方法
            // 暫時返回 true，實際實作時需要根據具體的 JNI 方法調整
            true
        } catch (e: Exception) {
            Log.w(TAG, "Library verification failed", e)
            false
        }
    }
    
    /**
     * 庫載入結果密封類
     */
    sealed class LibraryLoadResult {
        data class Success(val message: String) : LibraryLoadResult()
        data class Error(val message: String, val cause: Throwable? = null) : LibraryLoadResult()
        
        fun isSuccess(): Boolean = this is Success
        fun isError(): Boolean = this is Error
        
        fun getErrorMessage(): String? {
            return when (this) {
                is Error -> message
                else -> null
            }
        }
        
        // 移除 fun getCause(): Throwable?
        // 直接用 cause 屬性即可
    }
} 