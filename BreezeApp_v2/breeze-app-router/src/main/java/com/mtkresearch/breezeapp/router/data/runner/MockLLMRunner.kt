package com.mtkresearch.breezeapp.router.data.runner

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.FlowStreamingRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.RunnerInfo
import com.mtkresearch.breezeapp.router.domain.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MockLLMRunner
 * 
 * 模擬大語言模型推論的 Runner 實作
 * 支援串流文字生成、可配置延遲和預定義回應
 * 
 * 功能特性：
 * - 支援串流和非串流模式
 * - 可配置的回應延遲
 * - 預定義回應庫
 * - 錯誤情況模擬
 * - 線程安全設計
 */
class MockLLMRunner : BaseRunner, FlowStreamingRunner {
    
    companion object {
        private const val TAG = "MockLLMRunner"
        private const val DEFAULT_RESPONSE_DELAY = 100L
        private const val DEFAULT_STREAM_CHUNK_DELAY = 50L
    }
    
    private val isLoaded = AtomicBoolean(false)
    private var responseDelay = DEFAULT_RESPONSE_DELAY
    private var predefinedResponses = listOf(
        "這是一個模擬的 LLM 回應。我正在協助您測試 AI Router 架構的功能。",
        "我是 Mock LLM Runner，專門用於驗證系統的擴展性和穩定性。",
        "AI Router 架構運作正常！您的訊息已被成功處理。",
        "感謝您使用 BreezeApp AI Router。系統正在使用模擬引擎進行回應。",
        "這是一個測試回應，用於驗證 Mock Runner 的串流功能是否正常運作。"
    )
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading MockLLMRunner with config: ${config.modelName}")
            
            // 模擬載入時間
            Thread.sleep(500)
            
            // 從配置中讀取參數
            config.parameters["response_delay_ms"]?.let { delay ->
                responseDelay = (delay as? Number)?.toLong() ?: DEFAULT_RESPONSE_DELAY
            }
            
            config.parameters["predefined_responses"]?.let { responses ->
                @Suppress("UNCHECKED_CAST")
                predefinedResponses = (responses as? List<String>) ?: predefinedResponses
            }
            
            isLoaded.set(true)
            Log.d(TAG, "MockLLMRunner loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MockLLMRunner", e)
            isLoaded.set(false)
            false
        }
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        
        return try {
            // 模擬處理延遲
            Thread.sleep(responseDelay)
            
            val response = selectResponseFor(prompt)
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to response),
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to "mock-llm-v1",
                    InferenceResult.META_PROCESSING_TIME_MS to responseDelay,
                    InferenceResult.META_TOKEN_COUNT to response.split(" ").size,
                    InferenceResult.META_SESSION_ID to input.sessionId,
                    InferenceResult.META_STREAM_MODE to stream
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockLLMRunner.run", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        if (!isLoaded.get()) {
            emit(InferenceResult.error(RunnerError.modelNotLoaded()))
            return@flow
        }
        
        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
        
        try {
            val response = selectResponseFor(prompt)
            val words = response.split(" ")
            
            Log.d(TAG, "Starting stream response for session: ${input.sessionId}")
            
            // 模擬串流回應
            for ((index, word) in words.withIndex()) {
                delay(DEFAULT_STREAM_CHUNK_DELAY)
                
                val partialText = words.take(index + 1).joinToString(" ")
                val isPartial = index < words.size - 1
                
                emit(InferenceResult.success(
                    outputs = mapOf(InferenceResult.OUTPUT_TEXT to partialText),
                    metadata = mapOf(
                        InferenceResult.META_PARTIAL_TOKENS to index + 1,
                        InferenceResult.META_SESSION_ID to input.sessionId,
                        InferenceResult.META_MODEL_NAME to "mock-llm-v1"
                    ),
                    partial = isPartial
                ))
                
                // 提前結束條件檢查
                if (Thread.currentThread().isInterrupted) {
                    Log.d(TAG, "Stream interrupted for session: ${input.sessionId}")
                    break
                }
            }
            
            Log.d(TAG, "Stream completed for session: ${input.sessionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockLLMRunner.runAsFlow", e)
            emit(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading MockLLMRunner")
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
    
    override fun isLoaded(): Boolean = isLoaded.get()
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "MockLLMRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Mock implementation for Large Language Model inference",
        isMock = true
    )
    
    /**
     * 根據輸入選擇適當的回應
     */
    private fun selectResponseFor(prompt: String): String {
        return when {
            prompt.contains("測試", ignoreCase = true) -> 
                "這是一個測試回應，用於驗證 Mock LLM Runner 的功能。測試進行中..."
            
            prompt.contains("錯誤", ignoreCase = true) -> 
                throw RuntimeException("模擬錯誤：這是一個測試用的錯誤情況，用於驗證錯誤處理機制。")
            
            prompt.contains("串流", ignoreCase = true) || prompt.contains("stream", ignoreCase = true) -> 
                "這是串流模式的測試回應。每個詞語都會逐步發送，模擬真實的 LLM 推論過程。"
            
            prompt.contains("BreezeApp", ignoreCase = true) -> 
                "BreezeApp 是一個先進的 AI 應用程式，使用模組化的 AI Router 架構來管理不同的 AI 引擎。"
            
            prompt.isEmpty() -> 
                "您好！我是 AI 助手。請問有什麼我可以協助您的嗎？"
            
            else -> predefinedResponses.random()
        }
    }
}