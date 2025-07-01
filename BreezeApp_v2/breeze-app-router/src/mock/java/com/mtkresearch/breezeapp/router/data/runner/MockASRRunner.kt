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
 * MockASRRunner
 * 
 * 模擬自動語音識別 (ASR) 推論的 Runner 實作
 * 支援串流語音識別、可配置的轉錄結果和置信度模擬
 * 
 * 功能特性：
 * - 支援串流和非串流模式
 * - 模擬真實的語音處理延遲
 * - 可配置的轉錄結果庫
 * - 置信度分數模擬
 * - 音訊格式驗證
 */
class MockASRRunner : BaseRunner, FlowStreamingRunner {
    
    companion object {
        private const val TAG = "MockASRRunner"
        private const val DEFAULT_PROCESSING_DELAY = 300L
        private const val DEFAULT_STREAM_SEGMENT_DELAY = 200L
    }
    
    private val isLoaded = AtomicBoolean(false)
    private var processingDelay = DEFAULT_PROCESSING_DELAY
    private val mockTranscriptions = mapOf(
        "test_audio_1" to "你好，這是一個測試音檔。",
        "test_audio_2" to "AI Router 語音識別功能測試。",
        "test_audio_3" to "BreezeApp 語音轉文字功能運作正常。",
        "test_audio_4" to "這是模擬的語音識別結果，用於驗證系統功能。",
        "default" to "這是預設的語音識別結果。"
    )
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading MockASRRunner with config: ${config.modelName}")
            
            // 模擬 ASR 模型載入時間
            Thread.sleep(800)
            
            // 從配置中讀取參數
            config.parameters["processing_delay_ms"]?.let { delay ->
                processingDelay = (delay as? Number)?.toLong() ?: DEFAULT_PROCESSING_DELAY
            }
            
            isLoaded.set(true)
            Log.d(TAG, "MockASRRunner loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MockASRRunner", e)
            isLoaded.set(false)
            false
        }
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        return try {
            val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            val audioId = input.inputs[InferenceRequest.INPUT_AUDIO_ID] as? String ?: "default"
            
            if (audioData == null) {
                return InferenceResult.error(
                    RunnerError.invalidInput("Audio data required for ASR processing")
                )
            }
            
            // 模擬音訊處理時間
            Thread.sleep(processingDelay)
            
            val transcription = mockTranscriptions[audioId] ?: mockTranscriptions["default"]!!
            val confidence = calculateMockConfidence(audioData.size, audioId)
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to transcription),
                metadata = mapOf(
                    InferenceResult.META_CONFIDENCE to confidence,
                    InferenceResult.META_PROCESSING_TIME_MS to processingDelay,
                    InferenceResult.META_MODEL_NAME to "mock-asr-v1",
                    "audio_length_ms" to (audioData.size * 8), // 模擬音檔長度
                    "audio_format" to "pcm_16khz",
                    InferenceResult.META_SESSION_ID to input.sessionId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockASRRunner.run", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        if (!isLoaded.get()) {
            emit(InferenceResult.error(RunnerError.modelNotLoaded()))
            return@flow
        }
        
        try {
            val audioData = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            val audioId = input.inputs[InferenceRequest.INPUT_AUDIO_ID] as? String ?: "default"
            
            if (audioData == null) {
                emit(InferenceResult.error(
                    RunnerError.invalidInput("Audio data required for ASR processing")
                ))
                return@flow
            }
            
            val fullTranscription = mockTranscriptions[audioId] ?: mockTranscriptions["default"]!!
            val segments = splitIntoSegments(fullTranscription)
            
            Log.d(TAG, "Starting stream ASR for session: ${input.sessionId}")
            
            // 模擬即時語音識別
            for ((index, segment) in segments.withIndex()) {
                delay(DEFAULT_STREAM_SEGMENT_DELAY)
                
                val partialResult = segments.take(index + 1).joinToString("")
                val isPartial = index < segments.size - 1
                val confidence = (0.7 + index * 0.05).coerceAtMost(0.95)
                
                emit(InferenceResult.success(
                    outputs = mapOf(InferenceResult.OUTPUT_TEXT to partialResult),
                    metadata = mapOf(
                        InferenceResult.META_CONFIDENCE to confidence,
                        InferenceResult.META_SEGMENT_INDEX to index,
                        InferenceResult.META_SESSION_ID to input.sessionId,
                        InferenceResult.META_MODEL_NAME to "mock-asr-v1",
                        "partial_segments" to index + 1,
                        "total_segments" to segments.size
                    ),
                    partial = isPartial
                ))
                
                if (Thread.currentThread().isInterrupted) {
                    Log.d(TAG, "ASR stream interrupted for session: ${input.sessionId}")
                    break
                }
            }
            
            Log.d(TAG, "ASR stream completed for session: ${input.sessionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockASRRunner.runAsFlow", e)
            emit(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading MockASRRunner")
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR)
    
    override fun isLoaded(): Boolean = isLoaded.get()
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "MockASRRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Mock implementation for Automatic Speech Recognition",
        isMock = true
    )
    
    /**
     * 將文字分割成語音片段模擬
     */
    private fun splitIntoSegments(text: String): List<String> {
        // 模擬語音識別的分段處理
        return text.split("。", "，", " ", "、").filter { it.isNotBlank() }
    }
    
    /**
     * 計算模擬的置信度分數
     */
    private fun calculateMockConfidence(audioDataSize: Int, audioId: String): Double {
        // 基於音訊大小和ID模擬置信度
        val baseConfidence = when {
            audioId.contains("clear") -> 0.95
            audioId.contains("noisy") -> 0.75
            audioId.contains("test") -> 0.90
            else -> 0.85
        }
        
        // 音訊長度影響置信度
        val lengthFactor = when {
            audioDataSize < 1000 -> -0.1  // 太短
            audioDataSize > 10000 -> 0.05 // 適中長度
            else -> 0.0
        }
        
        return (baseConfidence + lengthFactor).coerceIn(0.5, 0.99)
    }
} 