package com.mtkresearch.breezeapp.router.domain.interfaces

import com.mtkresearch.breezeapp.router.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.router.domain.model.InferenceResult
import kotlinx.coroutines.flow.Flow

/**
 * StreamingRunner 擴展介面
 * 為支援串流推論的 Runner 提供額外的能力
 * 
 * 實作此介面的 Runner 可以提供即時的部分結果回傳
 * 適用於 LLM 文字生成、ASR 即時識別等場景
 */
interface StreamingRunner : BaseRunner {
    
    /**
     * 串流推論執行 (Callback 版本)
     * @param input 推論請求
     * @param onResult 部分結果回調
     * @param onComplete 完成回調
     * @param onError 錯誤回調
     */
    fun runStream(
        input: InferenceRequest,
        onResult: (InferenceResult) -> Unit,    // 部分結果回調
        onComplete: () -> Unit,                 // 完成回調
        onError: (Throwable) -> Unit           // 錯誤回調
    )
}

/**
 * FlowStreamingRunner 擴展介面
 * 使用 Kotlin Flow 提供更現代化的串流 API
 * 
 * 建議優先使用此介面而非 StreamingRunner
 */
interface FlowStreamingRunner : BaseRunner {
    
    /**
     * 串流推論執行 (Flow 版本)
     * @param input 推論請求
     * @return 推論結果的 Flow
     */
    fun runAsFlow(input: InferenceRequest): Flow<InferenceResult>
} 