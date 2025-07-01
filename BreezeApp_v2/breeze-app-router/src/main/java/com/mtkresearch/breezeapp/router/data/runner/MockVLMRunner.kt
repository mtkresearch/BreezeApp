package com.mtkresearch.breezeapp.router.data.runner

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.RunnerInfo
import com.mtkresearch.breezeapp.router.domain.model.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MockVLMRunner
 * 
 * 模擬視覺語言模型 (VLM) 推論的 Runner 實作
 * 支援圖像分析、圖像描述和圖像問答功能
 * 
 * 功能特性：
 * - 圖像內容分析模擬
 * - 基於圖像大小的描述生成
 * - 圖像問答模擬
 * - 圖像格式驗證
 * - 可配置的分析延遲
 */
class MockVLMRunner : BaseRunner {
    
    companion object {
        private const val TAG = "MockVLMRunner"
        private const val DEFAULT_ANALYSIS_DELAY = 400L
    }
    
    private val isLoaded = AtomicBoolean(false)
    private var analysisDelay = DEFAULT_ANALYSIS_DELAY
    
    private val imageDescriptions = mapOf(
        "small" to "這是一張小尺寸的圖片，看起來可能是一個圖標或縮圖。",
        "medium" to "這是一張中等尺寸的圖片，顯示了清晰的細節和內容。",
        "large" to "這是一張高解析度的大圖片，包含豐富的視覺資訊和細節。",
        "portrait" to "這是一張人像照片，顯示了一個人的面部特徵。",
        "landscape" to "這是一張風景照片，展現了自然環境的美麗景色。",
        "document" to "這看起來是一份文件或截圖，包含文字和結構化資訊。",
        "default" to "這是一張圖片，AI 正在分析其內容和特徵。"
    )
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading MockVLMRunner with config: ${config.modelName}")
            
            // 模擬 VLM 模型載入時間 (通常較長)
            Thread.sleep(1000)
            
            // 從配置中讀取參數
            config.parameters["analysis_delay_ms"]?.let { delay ->
                analysisDelay = (delay as? Number)?.toLong() ?: DEFAULT_ANALYSIS_DELAY
            }
            
            isLoaded.set(true)
            Log.d(TAG, "MockVLMRunner loaded successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MockVLMRunner", e)
            isLoaded.set(false)
            false
        }
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isLoaded.get()) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        return try {
            val imageData = input.inputs[InferenceRequest.INPUT_IMAGE] as? ByteArray
            val question = input.inputs[InferenceRequest.INPUT_TEXT] as? String ?: ""
            
            if (imageData == null) {
                return InferenceResult.error(
                    RunnerError.invalidInput("Image data required for VLM analysis")
                )
            }
            
            // 模擬圖像分析處理時間
            Thread.sleep(analysisDelay)
            
            val imageType = analyzeImageType(imageData)
            val description = generateImageDescription(imageData, question, imageType)
            val confidence = calculateAnalysisConfidence(imageData.size, question)
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to description),
                metadata = mapOf(
                    InferenceResult.META_CONFIDENCE to confidence,
                    InferenceResult.META_PROCESSING_TIME_MS to analysisDelay,
                    InferenceResult.META_MODEL_NAME to "mock-vlm-v1",
                    "image_size_bytes" to imageData.size,
                    "image_type" to imageType,
                    "has_question" to question.isNotBlank(),
                    "analysis_mode" to if (question.isNotBlank()) "qa" else "description",
                    InferenceResult.META_SESSION_ID to input.sessionId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockVLMRunner.run", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading MockVLMRunner")
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.VLM)
    
    override fun isLoaded(): Boolean = isLoaded.get()
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "MockVLMRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Mock implementation for Vision Language Model analysis",
        isMock = true
    )
    
    /**
     * 分析圖像類型
     */
    private fun analyzeImageType(imageData: ByteArray): String {
        return when {
            imageData.size < 10000 -> "small"
            imageData.size < 100000 -> "medium" 
            imageData.size > 500000 -> "large"
            // 簡單的格式檢測 (基於 magic bytes)
            imageData.take(4).joinToString("") { "%02x".format(it) }.startsWith("ffd8") -> "photo"
            imageData.take(4).joinToString("") { "%02x".format(it) }.startsWith("8950") -> "document"
            else -> "default"
        }
    }
    
    /**
     * 生成圖像描述
     */
    private fun generateImageDescription(imageData: ByteArray, question: String, imageType: String): String {
        val baseDescription = imageDescriptions[imageType] ?: imageDescriptions["default"]!!
        
        return when {
            question.contains("什麼", ignoreCase = true) || question.contains("what", ignoreCase = true) -> {
                "根據圖像分析，$baseDescription 具體來說，圖像中包含了多個視覺元素和特徵。"
            }
            question.contains("顏色", ignoreCase = true) || question.contains("color", ignoreCase = true) -> {
                "從顏色分析的角度來看，這張圖片主要包含了豐富的色彩組合，整體色調協調且富有層次感。"
            }
            question.contains("文字", ignoreCase = true) || question.contains("text", ignoreCase = true) -> {
                "經過文字識別分析，這張圖片${if (imageType == "document") "包含了可識別的文字內容" else "主要以視覺內容為主，文字較少"}。"
            }
            question.contains("人", ignoreCase = true) || question.contains("person", ignoreCase = true) -> {
                "從人物檢測的角度分析，${if (imageType == "portrait") "圖片中包含人物形象" else "這張圖片主要展現的是非人物內容"}。"
            }
            question.isNotBlank() -> {
                "針對您的問題「$question」，基於圖像分析的結果，$baseDescription"
            }
            else -> baseDescription
        }
    }
    
    /**
     * 計算分析置信度
     */
    private fun calculateAnalysisConfidence(imageSize: Int, question: String): Double {
        // 基礎置信度基於圖像大小
        val sizeConfidence = when {
            imageSize < 5000 -> 0.6   // 太小，細節不足
            imageSize < 50000 -> 0.85 // 適中
            imageSize < 200000 -> 0.9 // 良好
            else -> 0.95              // 高解析度
        }
        
        // 問題複雜度影響置信度
        val questionComplexity = when {
            question.isEmpty() -> 0.0
            question.split(" ").size <= 3 -> 0.05  // 簡單問題
            question.split(" ").size <= 8 -> 0.0   // 適中問題  
            else -> -0.05  // 複雜問題降低置信度
        }
        
        return (sizeConfidence + questionComplexity).coerceIn(0.5, 0.99)
    }
} 