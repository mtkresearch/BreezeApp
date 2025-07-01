package com.mtkresearch.breezeapp.router.data.runner

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.RunnerInfo
import com.mtkresearch.breezeapp.router.domain.model.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * MockGuardrailRunner
 * 
 * 模擬內容安全檢測 (Guardrail) 的 Runner 實作
 * 支援內容審查、毒性檢測和合規性驗證
 * 
 * 功能特性：
 * - 文字內容毒性檢測
 * - 關鍵字過濾
 * - 內容分類和風險評估
 * - 可配置的檢測嚴格程度
 * - 快速回應設計
 */
class MockGuardrailRunner : BaseRunner {
    
    companion object {
        private const val TAG = "MockGuardrailRunner"
        private const val DEFAULT_SCAN_DELAY = 50L // 快速檢測
    }
    
    private val isLoaded = AtomicBoolean(false)
    private var scanDelay = DEFAULT_SCAN_DELAY
    private var strictnessLevel = "medium" // low, medium, high
    
    // 風險關鍵字庫 (簡化版本)
    private val toxicKeywords = setOf(
        // 仇恨言論
        "仇恨", "歧視", "偏見", "hate", "discrimination",
        // 暴力內容
        "暴力", "傷害", "攻擊", "violence", "harm", "attack",
        // 不當內容
        "不當", "不適", "inappropriate", "unsuitable",
        // 隱私相關
        "個資", "隱私", "privacy", "personal"
    )
    
    private val spamKeywords = setOf(
        "廣告", "推銷", "spam", "promotion", "marketing",
        "免費", "中獎", "free", "winner", "lottery"
    )
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            Log.d(TAG, "Loading MockGuardrailRunner with config: ${config.modelName}")
            
            // 模擬檢測引擎載入 (快速載入)
            Thread.sleep(200)
            
            // 從配置中讀取參數
            config.parameters["scan_delay_ms"]?.let { delay ->
                scanDelay = (delay as? Number)?.toLong() ?: DEFAULT_SCAN_DELAY
            }
            
            config.parameters["strictness_level"]?.let { level ->
                strictnessLevel = level as? String ?: "medium"
            }
            
            isLoaded.set(true)
            Log.d(TAG, "MockGuardrailRunner loaded successfully with strictness: $strictnessLevel")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load MockGuardrailRunner", e)
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
                    RunnerError.invalidInput("Text content required for safety analysis")
                )
            }
            
            // 模擬快速安全檢測
            Thread.sleep(scanDelay)
            
            val safetyResult = analyzeSafety(text)
            
            InferenceResult.success(
                outputs = mapOf(
                    "safety_status" to safetyResult.status,
                    "risk_score" to safetyResult.riskScore,
                    "risk_categories" to safetyResult.riskCategories,
                    "action_required" to safetyResult.actionRequired,
                    "filtered_text" to safetyResult.filteredText
                ),
                metadata = mapOf(
                    InferenceResult.META_CONFIDENCE to safetyResult.confidence,
                    InferenceResult.META_PROCESSING_TIME_MS to scanDelay,
                    InferenceResult.META_MODEL_NAME to "mock-guardrail-v1",
                    "strictness_level" to strictnessLevel,
                    "text_length" to text.length,
                    "detected_issues" to safetyResult.detectedIssues.size,
                    InferenceResult.META_SESSION_ID to input.sessionId
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in MockGuardrailRunner.run", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }
    
    override fun unload() {
        Log.d(TAG, "Unloading MockGuardrailRunner")
        isLoaded.set(false)
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.GUARDIAN)
    
    override fun isLoaded(): Boolean = isLoaded.get()
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "MockGuardrailRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Mock implementation for content safety and guardrail analysis",
        isMock = true
    )
    
    /**
     * 安全分析結果
     */
    data class SafetyAnalysisResult(
        val status: String,           // "safe", "warning", "blocked"
        val riskScore: Double,        // 0.0 - 1.0
        val riskCategories: List<String>,
        val actionRequired: String,   // "none", "review", "block"
        val filteredText: String,
        val detectedIssues: List<String>,
        val confidence: Double
    )
    
    /**
     * 執行內容安全分析
     */
    private fun analyzeSafety(text: String): SafetyAnalysisResult {
        val detectedIssues = mutableListOf<String>()
        val riskCategories = mutableListOf<String>()
        var riskScore = 0.0
        
        // 1. 毒性內容檢測
        val toxicMatches = toxicKeywords.filter { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
        if (toxicMatches.isNotEmpty()) {
            detectedIssues.add("toxic_content")
            riskCategories.add("toxicity")
            riskScore += 0.4 + (toxicMatches.size * 0.1)
        }
        
        // 2. 垃圾內容檢測
        val spamMatches = spamKeywords.filter { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
        if (spamMatches.isNotEmpty()) {
            detectedIssues.add("spam_content")
            riskCategories.add("spam")
            riskScore += 0.2 + (spamMatches.size * 0.05)
        }
        
        // 3. 長度異常檢測
        when {
            text.length > 5000 -> {
                detectedIssues.add("excessive_length")
                riskCategories.add("length_anomaly")
                riskScore += 0.1
            }
            text.length < 3 -> {
                detectedIssues.add("insufficient_content")
                riskScore += 0.05
            }
        }
        
        // 4. 特殊字符檢測
        val specialCharRatio = text.count { !it.isLetterOrDigit() && !it.isWhitespace() }.toDouble() / text.length
        if (specialCharRatio > 0.3) {
            detectedIssues.add("excessive_special_chars")
            riskCategories.add("format_anomaly")
            riskScore += 0.15
        }
        
        // 5. 重複內容檢測
        val words = text.split("\\s+".toRegex())
        val uniqueWords = words.toSet()
        val repetitionRatio = 1.0 - (uniqueWords.size.toDouble() / words.size)
        if (repetitionRatio > 0.7 && words.size > 10) {
            detectedIssues.add("excessive_repetition")
            riskCategories.add("repetition")
            riskScore += 0.2
        }
        
        // 根據嚴格程度調整風險分數
        riskScore = when (strictnessLevel) {
            "low" -> riskScore * 0.7
            "high" -> riskScore * 1.3
            else -> riskScore // medium
        }.coerceIn(0.0, 1.0)
        
        // 決定安全狀態和行動
        val (status, actionRequired) = when {
            riskScore < 0.3 -> "safe" to "none"
            riskScore < 0.7 -> "warning" to "review"
            else -> "blocked" to "block"
        }
        
        // 生成過濾後的文字 (如果需要)
        val filteredText = if (actionRequired == "block") {
            "[內容因安全原因被過濾]"
        } else if (actionRequired == "review") {
            filterSensitiveContent(text, toxicMatches + spamMatches)
        } else {
            text
        }
        
        // 計算置信度
        val confidence = when {
            detectedIssues.isEmpty() -> 0.95
            detectedIssues.size == 1 -> 0.90
            detectedIssues.size <= 3 -> 0.85
            else -> 0.80
        }
        
        return SafetyAnalysisResult(
            status = status,
            riskScore = riskScore,
            riskCategories = riskCategories,
            actionRequired = actionRequired,
            filteredText = filteredText,
            detectedIssues = detectedIssues,
            confidence = confidence
        )
    }
    
    /**
     * 過濾敏感內容
     */
    private fun filterSensitiveContent(text: String, sensitiveWords: List<String>): String {
        var filtered = text
        sensitiveWords.forEach { word ->
            val replacement = "*".repeat(word.length)
            filtered = filtered.replace(word, replacement, ignoreCase = true)
        }
        return filtered
    }
} 