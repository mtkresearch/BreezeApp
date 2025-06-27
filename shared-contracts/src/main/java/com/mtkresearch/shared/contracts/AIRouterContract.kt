package com.mtkresearch.shared.contracts

/**
 * 定義 UI App 與 AI Router App 之間的通信契約
 * 這個介面確保兩個獨立應用程式之間的一致性通信
 */
object AIRouterContract {
    
    /**
     * AI Router Service 的 Intent Actions
     */
    object Actions {
        const val ACTION_ROUTER_SERVICE = "com.mtkresearch.breezeapp_router.ACTION_ROUTER_SERVICE"
        const val ACTION_CONNECT = "com.mtkresearch.breezeapp_router.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.mtkresearch.breezeapp_router.ACTION_DISCONNECT"
        const val ACTION_SEND_MESSAGE = "com.mtkresearch.breezeapp_router.ACTION_SEND_MESSAGE"
        const val ACTION_GET_STATUS = "com.mtkresearch.breezeapp_router.ACTION_GET_STATUS"
    }
    
    /**
     * Intent Extras 的 Key 定義
     */
    object Extras {
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_MESSAGE_TEXT = "message_text"
        const val EXTRA_MESSAGE_AUTHOR = "message_author"
        const val EXTRA_RESPONSE_TEXT = "response_text"
        const val EXTRA_ERROR_CODE = "error_code"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_CONNECTION_STATE = "connection_state"
    }
    
    /**
     * Service 組件名稱
     */
    object Components {
        const val AI_ROUTER_SERVICE = "com.mtkresearch.breezeapp_router.service.AIRouterService"
        const val AI_ROUTER_PACKAGE = "com.mtkresearch.breezeapp_router"
    }
    
    /**
     * 連線狀態定義
     */
    object ConnectionStates {
        const val DISCONNECTED = 0
        const val CONNECTING = 1
        const val CONNECTED = 2
        const val ERROR = 3
    }
    
    /**
     * 錯誤代碼定義
     */
    object ErrorCodes {
        const val CONNECTION_FAILED = 1001
        const val SERVICE_UNAVAILABLE = 1002
        const val INVALID_REQUEST = 1003
        const val AI_ENGINE_ERROR = 1004
        const val TIMEOUT = 1005
    }
    
    /**
     * 訊息作者類型
     */
    object MessageAuthors {
        const val USER = "USER"
        const val AI = "AI"
        const val SYSTEM = "SYSTEM"
    }
} 