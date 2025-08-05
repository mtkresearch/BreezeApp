package com.mtkresearch.breezeapp_kotlin.core.utils

/**
 * 錯誤類型枚舉
 * 定義應用中不同類型的錯誤
 */
enum class ErrorType {
    NETWORK,        // 網路錯誤
    SERVER,         // 伺服器錯誤
    VALIDATION,     // 驗證錯誤
    PERMISSION,     // 權限錯誤
    MODEL_LOADING,  // 模型載入錯誤
    AI_PROCESSING,  // AI處理錯誤
    FILE_ACCESS,    // 檔案存取錯誤
    UNKNOWN         // 未知錯誤
} 