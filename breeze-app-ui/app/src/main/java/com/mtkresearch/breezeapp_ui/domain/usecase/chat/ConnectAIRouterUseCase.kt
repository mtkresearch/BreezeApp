package com.mtkresearch.breezeapp_ui.domain.usecase.chat

import com.mtkresearch.breezeapp_ui.domain.model.router.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * 連接 AI Router Use Case (v2.2 - Mocked for UI Development)
 *
 * This is a temporary, fake implementation to allow UI development without a real AI Router.
 * It simulates a consistently successful connection.
 */
class ConnectAIRouterUseCase @Inject constructor() { // Repository removed

    suspend fun connect(): Result<Unit> = Result.success(Unit)

    suspend fun disconnect(): Result<Unit> = Result.success(Unit)

    fun isConnected(): Boolean = true

    fun getConnectionState(): Flow<ConnectionState> = flowOf(ConnectionState.CONNECTED)

    suspend fun getStatus(): Result<Any> {
        val fakeStatus = object {
            val isRunning = true
            val currentModel = "mock_model"
        }
        return Result.success(fakeStatus)
    }
} 