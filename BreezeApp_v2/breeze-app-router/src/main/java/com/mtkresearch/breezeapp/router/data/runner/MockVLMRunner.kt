package com.mtkresearch.breezeapp.router.data.runner

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.FlowStreamingRunner
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
class MockVLMRunner : BaseRunner, FlowStreamingRunner {
    
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
        "photo" to "這是一張真實照片，展現了生動的色彩和細節。",
        "compressed" to "這是一張經過壓縮的圖片，在檔案大小和品質之間取得平衡。",
        "high_quality" to "這是一張高品質圖片，保留了豐富的細節和色彩資訊。",
        "camera_photo" to "這是用相機拍攝的照片，具有自然的光照和構圖。",
        "gallery_photo" to "這是從圖庫選擇的照片，可能是之前儲存的影像。",
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
            
            val imageAnalysis = analyzeImageData(imageData)
            val description = generateImageDescription(imageData, question, imageAnalysis)
            val confidence = calculateAnalysisConfidence(imageData.size, question)
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to description),
                metadata = mapOf(
                    InferenceResult.META_CONFIDENCE to confidence,
                    InferenceResult.META_PROCESSING_TIME_MS to analysisDelay,
                    InferenceResult.META_MODEL_NAME to "mock-vlm-v1",
                    "image_size_bytes" to imageData.size,
                    "image_type" to imageAnalysis.type,
                    "image_format" to imageAnalysis.format,
                    "estimated_resolution" to imageAnalysis.estimatedResolution,
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

    override fun runAsFlow(input: InferenceRequest): kotlinx.coroutines.flow.Flow<InferenceResult> = kotlinx.coroutines.flow.flow {
        emit(run(input, stream = false))
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
     * 圖像分析結果數據類
     */
    private data class ImageAnalysis(
        val type: String,
        val format: String,
        val estimatedResolution: String,
        val quality: String
    )
    
    /**
     * 分析圖像數據
     */
    private fun analyzeImageData(imageData: ByteArray): ImageAnalysis {
        val format = detectImageFormat(imageData)
        val type = classifyImageType(imageData)
        val resolution = estimateResolution(imageData.size)
        val quality = assessImageQuality(imageData.size, format)
        
        return ImageAnalysis(type, format, resolution, quality)
    }
    
    /**
     * 檢測圖像格式
     */
    private fun detectImageFormat(imageData: ByteArray): String {
        if (imageData.size < 4) return "unknown"
        
        val header = imageData.take(4).map { it.toUByte().toInt() }
        return when {
            header[0] == 0xFF && header[1] == 0xD8 -> "JPEG"
            header[0] == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47 -> "PNG"
            header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46 -> "GIF"
            header[0] == 0x42 && header[1] == 0x4D -> "BMP"
            else -> "unknown"
        }
    }
    
    /**
     * 分類圖像類型
     */
    private fun classifyImageType(imageData: ByteArray): String {
        return when {
            imageData.size < 5000 -> "small"
            imageData.size < 50000 -> "medium"
            imageData.size < 200000 -> "compressed"
            imageData.size > 500000 -> "high_quality"
            else -> "photo"
        }
    }
    
    /**
     * 估算圖像解析度
     */
    private fun estimateResolution(fileSize: Int): String {
        return when {
            fileSize < 10000 -> "低解析度 (~200x200)"
            fileSize < 100000 -> "中解析度 (~800x600)"
            fileSize < 500000 -> "高解析度 (~1920x1080)"
            else -> "超高解析度 (>1920x1080)"
        }
    }
    
    /**
     * 評估圖像品質
     */
    private fun assessImageQuality(fileSize: Int, format: String): String {
        val baseQuality = when (format) {
            "PNG" -> "高品質"
            "JPEG" -> if (fileSize > 100000) "高品質" else "壓縮品質"
            "GIF" -> "動畫/低色彩"
            else -> "未知"
        }
        return baseQuality
    }
    
    /**
     * 生成圖像描述
     */
    private fun generateImageDescription(imageData: ByteArray, question: String, analysis: ImageAnalysis): String {
        val baseDescription = imageDescriptions[analysis.type] ?: imageDescriptions["default"]!!
        val formatInfo = "圖片格式為${analysis.format}，${analysis.estimatedResolution}，${analysis.quality}。"
        
        return when {
            question.contains("什麼", ignoreCase = true) || question.contains("what", ignoreCase = true) -> {
                "根據圖像分析，$baseDescription $formatInfo 具體來說，這張圖片包含了豐富的視覺元素和特徵。"
            }
            question.contains("顏色", ignoreCase = true) || question.contains("color", ignoreCase = true) -> {
                "從顏色分析的角度來看，$formatInfo 這張圖片展現了豐富的色彩組合，整體色調協調且富有層次感。"
            }
            question.contains("品質", ignoreCase = true) || question.contains("quality", ignoreCase = true) -> {
                "圖片品質分析：$formatInfo 檔案大小為${imageData.size}位元組，視覺品質${analysis.quality}。"
            }
            question.contains("大小", ignoreCase = true) || question.contains("size", ignoreCase = true) -> {
                "圖片尺寸資訊：$formatInfo 檔案大小約${String.format("%.1f", imageData.size / 1024.0)}KB。"
            }
            question.isNotBlank() -> {
                "針對您的問題「$question」，基於圖像分析：$baseDescription $formatInfo"
            }
            else -> "$baseDescription $formatInfo"
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