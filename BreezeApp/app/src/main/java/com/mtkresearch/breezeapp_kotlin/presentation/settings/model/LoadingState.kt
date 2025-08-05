package com.mtkresearch.breezeapp_kotlin.presentation.settings.model

/**
 * 載入狀態枚舉
 * 用於追蹤異步操作的執行狀態
 */
sealed class LoadingState {
    object Loading : LoadingState()
    object Success : LoadingState()
    data class Error(val message: String) : LoadingState()
} 