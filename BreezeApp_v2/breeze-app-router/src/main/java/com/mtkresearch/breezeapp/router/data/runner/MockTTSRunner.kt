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
import kotlin.random.Random

/**
 * MockTTSRunner
 * 
 * 模擬文字轉語音 (TTS) 推論的 Runner 實作
 * 支援語音合成模擬、可配置的合成參數和音訊格式
 * 
 * 功能特性：
 * - 支援串流和非串流模式
 * - 模擬音訊資料生成
 * - 可配置的合成延遲
 * - 不同語音參數模擬
 * - 音訊格式支援
 */
class MockTTSRunner : BaseRunner, FlowStreamingRunner {
    
    companion object {
        private const val TAG = "MockTTSRunner"
        private const val DEFAULT_SYNTHESIS_DELAY = 250L
        private const val DEFAULT_STREAM_CHUNK_DELAY = 100L
        private const val MOCK_SAMPLE_RATE = 16000 // 16kHz
        private const val BYTES_PER_SAMPLE = 2 // 16-bit
    }
    
    private val isLoaded = AtomicBoolean(false)
    private var synthesisDelay = DEFAULT_SYNTHESIS_DELAY
    private var voiceId = "default"
    private var speakingRate = 1.0f
    private var pitch = 1.0f
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading MockTTSRunner with config: ${config.modelName}")
            
            // 模擬 TTS 引擎載入時間
            Thread.sleep(600)
            
            // 從配置中讀取參數
            config.parameters["synthesis_delay_ms"]?.let { delay ->
                synthesisDelay = (delay as? Number)?.toLong() ?: DEFAULT_SYNTHESIS_DELAY
            }
            
            config.parameters["voice_id"]?.let { voice ->
                voiceId = voice as? String ?: "default"
            }
            
            config.parameters["speaking_rate"]?.let { rate ->
                speakingRate = (rate as? Number)?.toFloat() ?: 1.0f
            }
            
            config.parameters["pitch"]?.let { p ->
                pitch = (p as? Number)?.toFloat() ?: 1.0f
            }
            
            isLoaded.set(true)
            Log.d(TAG, "MockTTSRunner loaded successfully with voice: $voiceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MockTTSRunner", e)
            isLoaded.set(false)
            false
        }
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        return try {
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            
            if (text.isNullOrBlank()) {
                return InferenceResult.error(
                    RunnerError.invalidInput("Text input required for TTS synthesis")
                )
            }
            
            // 模擬語音合成處理時間
            val actualDelay = (synthesisDelay / speakingRate).toLong()
            Thread.sleep(actualDelay)
            
            val audioData = generateMockAudioData(text)
            val duration = calculateDuration(text)
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_AUDIO to audioData),
                metadata = mapOf(
                    InferenceResult.META_PROCESSING_TIME_MS to actualDelay,
                    InferenceResult.META_MODEL_NAME to "mock-tts-v1",
                    "voice_id" to voiceId,
                    "speaking_rate" to speakingRate,
                    "pitch" to pitch,
                    "audio_duration_ms" to duration,
                    "audio_format" to "pcm_16khz",
                    "sample_rate" to MOCK_SAMPLE_RATE,
                    "channels" to 1,
                    InferenceResult.META_SESSION_ID to input.sessionId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockTTSRunner.run", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        if (!isLoaded.get()) {
            emit(InferenceResult.error(RunnerError.modelNotLoaded()))
            return@flow
        }
        
        try {
            val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            
            if (text.isNullOrBlank()) {
                emit(InferenceResult.error(
                    RunnerError.invalidInput("Text input required for TTS synthesis")
                ))
                return@flow
            }
            
            Log.d(TAG, "Starting stream TTS synthesis for session: ${input.sessionId}")
            
            // 將文字分段進行串流合成
            val sentences = text.split("。", "！", "？", ".", "!", "?").filter { it.isNotBlank() }
            var accumulatedAudio = byteArrayOf()
            
            for ((index, sentence) in sentences.withIndex()) {
                delay(DEFAULT_STREAM_CHUNK_DELAY)
                
                val sentenceAudio = generateMockAudioData(sentence.trim())
                accumulatedAudio += sentenceAudio
                
                val isPartial = index < sentences.size - 1
                val progress = (index + 1).toFloat() / sentences.size
                
                emit(InferenceResult.success(
                    outputs = mapOf(InferenceResult.OUTPUT_AUDIO to accumulatedAudio),
                    metadata = mapOf(
                        "synthesis_progress" to progress,
                        "current_sentence" to (index + 1),
                        "total_sentences" to sentences.size,
                        "voice_id" to voiceId,
                        InferenceResult.META_SESSION_ID to input.sessionId,
                        InferenceResult.META_MODEL_NAME to "mock-tts-v1"
                    ),
                    partial = isPartial
                ))
                
                if (Thread.currentThread().isInterrupted) {
                    Log.d(TAG, "TTS stream interrupted for session: ${input.sessionId}")
                    break
                }
            }
            
            Log.d(TAG, "TTS stream completed for session: ${input.sessionId}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockTTSRunner.runAsFlow", e)
            emit(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading MockTTSRunner")
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.TTS)
    
    override fun isLoaded(): Boolean = isLoaded.get()
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "MockTTSRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Mock implementation for Text-to-Speech synthesis",
        isMock = true
    )
    
    /**
     * 生成模擬的音訊資料
     */
    private fun generateMockAudioData(text: String): ByteArray {
        // 基於文字長度計算音訊長度
        val durationMs = calculateDuration(text)
        val samplesCount = (MOCK_SAMPLE_RATE * durationMs / 1000).toInt()
        val audioData = ByteArray(samplesCount * BYTES_PER_SAMPLE)
        
        // 生成簡單的正弦波模擬音訊
        for (i in 0 until samplesCount) {
            val frequency = getFrequencyForText(text)
            val sample = (Short.MAX_VALUE * 0.3 * 
                kotlin.math.sin(2 * kotlin.math.PI * frequency * i / MOCK_SAMPLE_RATE)).toInt().toShort()
            
            // 將 16-bit sample 轉換為 byte array (little-endian)
            audioData[i * 2] = (sample.toInt() and 0xFF).toByte()
            audioData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        
        return audioData
    }
    
    /**
     * 計算語音持續時間 (毫秒)
     */
    private fun calculateDuration(text: String): Long {
        // 基於文字長度和語速計算持續時間
        val baseWpm = 150 // 基礎每分鐘字數
        val wordsCount = text.split(" ").size.coerceAtLeast(1)
        val baseDurationMs = (wordsCount * 60000L / baseWpm)
        
        return (baseDurationMs / speakingRate).toLong().coerceAtLeast(100)
    }
    
    /**
     * 根據文字內容決定基礎頻率
     */
    private fun getFrequencyForText(text: String): Double {
        val baseFreq = when (voiceId) {
            "male" -> 110.0
            "female" -> 220.0
            else -> 165.0 // 中性音調
        }
        
        // 根據文字內容調整音調
        val textHash = text.hashCode()
        val variation = (textHash % 20 - 10) * 0.1 // -1.0 to 1.0
        
        return baseFreq * (1.0 + variation * 0.2) * pitch
    }
} 