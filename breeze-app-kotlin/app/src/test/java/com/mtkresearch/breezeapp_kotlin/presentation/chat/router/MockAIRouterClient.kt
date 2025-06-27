package com.mtkresearch.breezeapp_kotlin.presentation.chat.router

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * Mock AI Router Client for testing
 * 提供预设的 AI Router 行为用于单元测试
 */
class MockAIRouterClient : AIRouterClient {

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _errorEvents = MutableStateFlow<AIRouterError?>(null)
    
    private var isConnectedValue = false
    private var connectResult = Result.success(Unit)
    private var disconnectResult = Result.success(Unit)
    private var mockStatus = AIRouterStatus(
        isRunning = false,
        availableEngines = emptyList(),
        currentModel = null,
        memoryUsage = 0,
        processCount = 0
    )
    
    // 预设的回应
    private var mockResponses: List<AIResponse> = listOf(
        AIResponse(
            requestId = "test-123",
            text = "Mock AI Response",
            isComplete = true,
            state = AIResponse.ResponseState.COMPLETED
        )
    )

    override suspend fun connect(): Result<Unit> {
        if (connectResult.isSuccess) {
            _connectionState.value = ConnectionState.CONNECTED
            isConnectedValue = true
        } else {
            _connectionState.value = ConnectionState.ERROR
        }
        return connectResult
    }

    override suspend fun disconnect(): Result<Unit> {
        if (disconnectResult.isSuccess) {
            _connectionState.value = ConnectionState.DISCONNECTED
            isConnectedValue = false
        }
        return disconnectResult
    }

    override fun isConnected(): Boolean = isConnectedValue

    override fun getConnectionState(): StateFlow<ConnectionState> = _connectionState.asStateFlow()

    override suspend fun sendMessage(
        text: String,
        sessionId: String,
        messageType: MessageType
    ): Flow<AIResponse> {
        if (!isConnected()) {
            throw AIRouterException("Not connected to AI Router")
        }
        
        return flow {
            // 模拟网络延迟
            delay(100)
            
            mockResponses.forEach { response ->
                emit(response.copy(requestId = "test-${System.currentTimeMillis()}"))
                if (!response.isComplete) {
                    delay(50) // 模拟流式响应间隔
                }
            }
        }
    }

    override suspend fun getAIRouterStatus(): Result<AIRouterStatus> {
        return if (isConnected()) {
            Result.success(mockStatus)
        } else {
            Result.failure(AIRouterException("Not connected"))
        }
    }

    override suspend fun getAvailableCapabilities(): Result<List<AICapability>> {
        return Result.success(listOf(
            AICapability.TEXT_GENERATION,
            AICapability.IMAGE_ANALYSIS,
            AICapability.SPEECH_TO_TEXT,
            AICapability.TEXT_TO_SPEECH
        ))
    }

    override fun getErrorEvents(): Flow<AIRouterError> {
        return _errorEvents.asStateFlow().filterNotNull()
    }

    // 测试辅助方法
    
    fun setConnectionState(state: ConnectionState) {
        _connectionState.value = state
        isConnectedValue = (state == ConnectionState.CONNECTED)
    }
    
    fun emitError(error: AIRouterError) {
        _errorEvents.value = error
    }
    
    fun setMockResponses(responses: List<AIResponse>) {
        mockResponses = responses
    }
    
    fun setMockStatus(status: AIRouterStatus) {
        mockStatus = status
    }
    
    fun setConnectResult(result: Result<Unit>) {
        connectResult = result
    }
    
    fun setDisconnectResult(result: Result<Unit>) {
        disconnectResult = result
    }
} 