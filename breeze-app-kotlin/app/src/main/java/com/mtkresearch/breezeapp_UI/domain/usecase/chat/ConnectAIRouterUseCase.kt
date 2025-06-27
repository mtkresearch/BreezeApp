package com.mtkresearch.breezeapp_UI.domain.usecase.chat

import kotlinx.coroutines.flow.Flow
import com.mtkresearch.breezeapp_router.domain.repository.AIRouterRepository
import com.mtkresearch.breezeapp_router.domain.ConnectionState
import javax.inject.Inject

/**
 * 連接 AI Router Use Case
 * 
 * 負責處理 AI Router 連接相關的業務邏輯，包括：
 * - 連接管理
 * - 狀態監控
 * - 錯誤處理
 */
class ConnectAIRouterUseCase @Inject constructor(
    private val aiRouterRepository: AIRouterRepository
) {
    
    /**
     * 連接到 AI Router Service
     */
    suspend fun connect(): Result<Unit> {
        return aiRouterRepository.connect()
    }
    
    /**
     * 斷開 AI Router 連接
     */
    suspend fun disconnect(): Result<Unit> {
        return aiRouterRepository.disconnect()
    }
    
    /**
     * 檢查 AI Router 是否已連接
     */
    fun isConnected(): Boolean {
        return aiRouterRepository.isConnected()
    }
    
    /**
     * 獲取連接狀態的 Flow
     */
    fun getConnectionState(): Flow<ConnectionState> {
        return aiRouterRepository.getConnectionState()
    }
    
    /**
     * 獲取 AI Router 狀態
     */
    suspend fun getStatus() = aiRouterRepository.getAIRouterStatus()
} 