package com.mtkresearch.breezeapp_kotlin.presentation.settings.model

/**
 * AI推論層設定模型
 * 
 * 負責管理AI推論相關的參數設定
 * 生命週期: 會話級別，可即時調整
 * 影響範圍: 當前AI推論會話
 * 儲存方式: 會話狀態 + 可選持久化
 * 變更生效: 下一次推論立即生效
 */
data class RuntimeSettings(
    val llmParams: LLMParameters = LLMParameters(),
    val vlmParams: VLMParameters = VLMParameters(),
    val asrParams: ASRParameters = ASRParameters(),
    val ttsParams: TTSParameters = TTSParameters(),
    val generalParams: GeneralParameters = GeneralParameters()
) {
    /**
     * 檢查是否為預設設定
     */
    fun isDefault(): Boolean = this == RuntimeSettings()
    
    /**
     * 重置為預設設定
     */
    fun resetToDefault(): RuntimeSettings = RuntimeSettings()
}

/**
 * LLM (大語言模型) 參數
 */
data class LLMParameters(
    val temperature: Float = 0.7f,           // 創造性控制 (0.0-1.0)
    val topK: Int = 5,                       // Token選擇範圍 (1-100)
    val topP: Float = 0.9f,                  // 累積機率閾值 (0.0-1.0)
    val maxTokens: Int = 2048,               // 最大輸出長度 (128-4096)
    val repetitionPenalty: Float = 1.1f,     // 重複懲罰 (1.0-2.0)
    val frequencyPenalty: Float = 1.0f,      // 頻率懲罰 (1.0-2.0)
    val systemPrompt: String = "",           // 系統提示詞
    val enableStreaming: Boolean = true      // 串流輸出
)

/**
 * VLM (視覺語言模型) 參數
 */
data class VLMParameters(
    val imageResolution: ImageResolution = ImageResolution.MEDIUM,  // 圖像解析度
    val visionTemperature: Float = 0.7f,                           // 視覺溫度 (0.0-1.0)
    val maxImageTokens: Int = 512,                                 // 最大圖像Token數 (256-1024)
    val enableImageAnalysis: Boolean = true,                       // 啟用圖像分析
    val cropImages: Boolean = true                                 // 自動裁切圖像
)

/**
 * ASR (語音識別) 參數
 */
data class ASRParameters(
    val languageModel: String = "zh-TW",      // 語言模型
    val beamSize: Int = 5,                    // Beam搜索大小 (1-10)
    val vadThreshold: Float = 0.5f,           // 語音活動檢測閾值 (0.1-0.9)
    val enableNoiseSuppression: Boolean = true, // 噪音抑制
    val enableEchoCancellation: Boolean = true  // 回音消除
)

/**
 * TTS (文字轉語音) 參數
 */
data class TTSParameters(
    val speakerId: Int = 0,                   // 說話者ID (0-10)
    val speedRate: Float = 1.0f,              // 語速比例 (0.5-2.0)
    val pitch: Float = 1.0f,                  // 音調比例 (0.5-2.0)
    val volume: Float = 1.0f,                 // 音量 (0.0-1.0)
    val enableSpeechEnhancement: Boolean = true // 語音增強
)

/**
 * 通用推論參數
 */
data class GeneralParameters(
    val enableGPUAcceleration: Boolean = true,  // GPU加速
    val enableNPUAcceleration: Boolean = false, // NPU加速
    val maxConcurrentTasks: Int = 2,            // 最大並發任務數
    val timeoutSeconds: Int = 30,               // 推論超時時間 (秒)
    val enableDebugLogging: Boolean = false     // 除錯日誌
)

/**
 * 圖像解析度選項
 */
enum class ImageResolution(val width: Int, val height: Int, val displayName: String) {
    LOW(224, 224, "低 (224x224)"),
    MEDIUM(512, 512, "中 (512x512)"),
    HIGH(1024, 1024, "高 (1024x1024)")
} 