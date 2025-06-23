package com.mtkresearch.breezeapp_kotlin.presentation.settings.model

/**
 * 運行時設定應用狀態枚舉
 */
enum class RuntimeApplyStatus(val displayName: String) {
    IDLE("就緒"),
    APPLYING("應用中"),
    SUCCESS("應用成功"),
    ERROR("應用失敗"),
    VALIDATION_ERROR("驗證錯誤");

    fun isInProgress(): Boolean {
        return this == APPLYING
    }

    fun isError(): Boolean {
        return this == ERROR || this == VALIDATION_ERROR
    }

    fun isSuccess(): Boolean {
        return this == SUCCESS
    }
} 