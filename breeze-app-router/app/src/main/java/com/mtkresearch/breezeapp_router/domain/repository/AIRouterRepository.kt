package com.mtkresearch.breezeapp_router.domain.repository

import kotlinx.coroutines.flow.Flow
import com.mtkresearch.breezeapp_router.domain.ConnectionState
import com.mtkresearch.breezeapp_router.domain.model.AIRequest
import com.mtkresearch.breezeapp_router.domain.model.AIResponse
import com.mtkresearch.breezeapp_router.domain.model.AIRouterStatus
import com.mtkresearch.breezeapp_router.domain.model.AICapability

/**
 * AI Router Repository 接口
 * 
 * 定義AI Router的數據訪問抽象層
 */
interface AIRouterRepository {
    
    // 連線管理
    suspend fun connect(): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    fun isConnected(): Boolean
    fun getConnectionState(): Flow<ConnectionState>
    
    // 訊息處理
    fun generateChatCompletion(request: AIRequest): Flow<AIResponse>
    
    // AI Router狀態查詢
    suspend fun getAIRouterStatus(): Result<AIRouterStatus>
    suspend fun getAvailableCapabilities(): Result<List<AICapability>>
    
    // 錯誤處理
    fun getErrorEvents(): Flow<com.mtkresearch.breezeapp_router.domain.AIRouterError>
} 