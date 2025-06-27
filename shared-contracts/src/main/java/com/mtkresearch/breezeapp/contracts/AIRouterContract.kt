package com.mtkresearch.breezeapp.contracts

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * AI Router 跨應用程式通信契約
 * 定義 UI App 與 Router App 之間的資料交換格式
 */
object AIRouterContract {
    
    const val SERVICE_ACTION = "com.mtkresearch.breezeapp_router.service.AI_ROUTER_SERVICE"
    const val SERVICE_PACKAGE = "com.mtkresearch.breezeapp_router"
    
    /**
     * AI Router 連接狀態
     * 這是跨應用程式通信的標準狀態，不是 Router App 的內部實作
     */
    enum class ConnectionState {
        DISCONNECTED,    // 未連接
        CONNECTING,      // 連接中  
        CONNECTED,       // 已連接
        ERROR,           // 連接錯誤
        RECONNECTING     // 重新連接中
    }
    
    /**
     * 跨應用程式的訊息模型
     */
    @Parcelize
    data class CrossAppMessage(
        val id: String,
        val text: String,
        val author: String, // "USER" 或 "AI"
        val timestamp: Long,
        val isError: Boolean = false,
        val errorMessage: String? = null
    ) : Parcelable
    
    /**
     * AI Router 服務狀態
     */
    @Parcelize
    data class ServiceStatus(
        val isReady: Boolean,
        val connectionState: ConnectionState,
        val capabilities: List<String>,
        val currentModel: String? = null,
        val errorMessage: String? = null
    ) : Parcelable
    
    /**
     * 跨應用程式的錯誤類型
     */
    enum class ErrorType {
        SERVICE_UNAVAILABLE,
        CONNECTION_FAILED,
        AI_ENGINE_ERROR,
        TIMEOUT,
        INVALID_REQUEST
    }
    
    /**
     * 服務通信的 Message 類型
     */
    object MessageTypes {
        const val SEND_MESSAGE = 1
        const val MESSAGE_RESPONSE = 2
        const val CONNECTION_STATE_CHANGED = 3
        const val SERVICE_STATUS_REQUEST = 4
        const val SERVICE_STATUS_RESPONSE = 5
    }
} 