package com.mtkresearch.breezeapp.router.infrastructure

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.mtkresearch.breezeapp.router.config.MTKConfig
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * 模型路徑解析器
 * 負責解析和驗證模型路徑，支援 YAML 配置文件載入
 * 
 * 改進點：
 * - 統一的模型路徑管理
 * - 支援 assets 和檔案系統路徑
 * - YAML 配置文件解析
 * - 路徑驗證和存在性檢查
 * - 錯誤處理和回退機制
 */
class ModelPathResolver(
    private val context: Context,
    private val config: MTKConfig
) {
    
    companion object {
        private const val TAG = "ModelPathResolver"
        private const val ASSETS_PREFIX = "file:///android_asset/"
        private const val DEFAULT_CONFIG_NAME = "config_breezetiny_3b_instruct.yaml"
        private const val MODELS_DIR = "models"
        private const val CONFIG_DIR = "configs"
    }
    
    /**
     * 解析模型路徑
     * 
     * @return String 解析後的模型路徑
     * @throws ModelPathException 路徑解析失敗
     */
    fun resolveModelPath(): String {
        val modelPath = config.modelPath
        
        Log.d(TAG, "Resolving model path: $modelPath")
        
        return when {
            // Assets 路徑
            modelPath.startsWith(ASSETS_PREFIX) -> {
                resolveAssetsPath(modelPath.removePrefix(ASSETS_PREFIX))
            }
            
            // 絕對路徑
            modelPath.startsWith("/") -> {
                resolveAbsolutePath(modelPath)
            }
            
            // 相對路徑，假設在 assets 中
            else -> {
                resolveAssetsPath(modelPath)
            }
        }
    }
    
    /**
     * 驗證模型文件
     * 
     * @return ValidationResult 驗證結果
     */
    fun validateModelFile(): ValidationResult {
        return try {
            val resolvedPath = resolveModelPath()
            val validationResult = validatePath(resolvedPath)
            
            if (validationResult.isValid()) {
                Log.i(TAG, "Model file validation successful: $resolvedPath")
                validationResult
            } else {
                val errorList = if (validationResult is ValidationResult.Invalid) validationResult.errors else emptyList<String>()
                Log.e(TAG, "Model file validation failed: $errorList")
                validationResult
            }
            
        } catch (e: ModelPathException) {
            Log.e(TAG, "Model path resolution failed", e)
            ValidationResult.Invalid(listOf("Model path resolution failed: ${e.message}"))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during model validation", e)
            ValidationResult.Invalid(listOf("Unexpected validation error: ${e.message}"))
        }
    }
    
    /**
     * 獲取 YAML 配置
     * 
     * @return MTKYAMLConfig YAML 配置物件
     * @throws ModelPathException 配置載入失敗
     */
    fun getYAMLConfig(): MTKYAMLConfig {
        val configPath = config.configPath ?: DEFAULT_CONFIG_NAME
        
        Log.d(TAG, "Loading YAML config: $configPath")
        
        return try {
            val configContent = loadConfigFile(configPath)
            parseYAMLConfig(configContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load YAML config: $configPath", e)
            throw ModelPathException("Failed to load YAML config: ${e.message}", e)
        }
    }
    
    /**
     * 解析 Assets 路徑
     */
    private fun resolveAssetsPath(assetPath: String): String {
        val fullPath = ASSETS_PREFIX + assetPath
        
        if (!isAssetExists(assetPath)) {
            throw ModelPathException("Asset not found: $assetPath")
        }
        
        Log.d(TAG, "Resolved assets path: $fullPath")
        return fullPath
    }
    
    /**
     * 解析絕對路徑
     */
    private fun resolveAbsolutePath(absolutePath: String): String {
        val file = File(absolutePath)
        
        if (!file.exists()) {
            throw ModelPathException("File not found: $absolutePath")
        }
        
        if (!file.canRead()) {
            throw ModelPathException("File not readable: $absolutePath")
        }
        
        Log.d(TAG, "Resolved absolute path: $absolutePath")
        return absolutePath
    }
    
    /**
     * 檢查 Asset 是否存在
     */
    private fun isAssetExists(assetPath: String): Boolean {
        return try {
            val assetManager = context.assets
            val inputStream = assetManager.open(assetPath)
            inputStream.close()
            true
        } catch (e: IOException) {
            false
        }
    }
    
    /**
     * 驗證路徑
     */
    private fun validatePath(path: String): ValidationResult {
        val errors = mutableListOf<String>()
        
        when {
            path.startsWith(ASSETS_PREFIX) -> {
                val assetPath = path.removePrefix(ASSETS_PREFIX)
                if (!isAssetExists(assetPath)) {
                    errors.add("Asset not found: $assetPath")
                }
            }
            
            path.startsWith("/") -> {
                val file = File(path)
                if (!file.exists()) {
                    errors.add("File not found: $path")
                }
                if (!file.canRead()) {
                    errors.add("File not readable: $path")
                }
                if (file.length() == 0L) {
                    errors.add("File is empty: $path")
                }
            }
            
            else -> {
                errors.add("Invalid path format: $path")
            }
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(errors)
        }
    }
    
    /**
     * 載入配置文件
     */
    private fun loadConfigFile(configPath: String): String {
        return try {
            val assetManager = context.assets
            val inputStream: InputStream = assetManager.open(configPath)
            val content = inputStream.bufferedReader().use { it.readText() }
            inputStream.close()
            content
        } catch (e: IOException) {
            throw ModelPathException("Failed to load config file: $configPath", e)
        }
    }
    
    /**
     * 解析 YAML 配置
     * 簡化版本的 YAML 解析，實際使用時可以整合專門的 YAML 解析庫
     */
    private fun parseYAMLConfig(yamlContent: String): MTKYAMLConfig {
        val config = MTKYAMLConfig()
        
        try {
            val lines = yamlContent.split("\n")
            
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue
                }
                
                when {
                    trimmedLine.startsWith("model_path:") -> {
                        config.modelPath = extractValue(trimmedLine)
                    }
                    trimmedLine.startsWith("preloadSharedWeights:") -> {
                        config.preloadSharedWeights = extractValue(trimmedLine).toBoolean()
                    }
                    trimmedLine.startsWith("max_tokens:") -> {
                        config.maxTokens = extractValue(trimmedLine).toInt()
                    }
                    trimmedLine.startsWith("temperature:") -> {
                        config.temperature = extractValue(trimmedLine).toFloat()
                    }
                    trimmedLine.startsWith("top_k:") -> {
                        config.topK = extractValue(trimmedLine).toInt()
                    }
                    trimmedLine.startsWith("repetition_penalty:") -> {
                        config.repetitionPenalty = extractValue(trimmedLine).toFloat()
                    }
                }
            }
            
            Log.d(TAG, "YAML config parsed successfully")
            return config
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse YAML config", e)
            throw ModelPathException("Failed to parse YAML config: ${e.message}", e)
        }
    }
    
    /**
     * 從 YAML 行中提取值
     */
    private fun extractValue(line: String): String {
        val colonIndex = line.indexOf(':')
        if (colonIndex == -1) {
            throw IllegalArgumentException("Invalid YAML line: $line")
        }
        
        return line.substring(colonIndex + 1).trim().removeSurrounding("\"", "'")
    }
    
    /**
     * 獲取模型檔案大小
     */
    fun getModelFileSize(): Long {
        return try {
            val resolvedPath = resolveModelPath()
            
            when {
                resolvedPath.startsWith(ASSETS_PREFIX) -> {
                    val assetPath = resolvedPath.removePrefix(ASSETS_PREFIX)
                    val assetFileDescriptor = context.assets.openFd(assetPath)
                    val size = assetFileDescriptor.length
                    assetFileDescriptor.close()
                    size
                }
                
                resolvedPath.startsWith("/") -> {
                    File(resolvedPath).length()
                }
                
                else -> -1L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get model file size", e)
            -1L
        }
    }
    
    /**
     * 模型路徑異常
     */
    class ModelPathException(message: String, cause: Throwable? = null) : Exception(message, cause)
    
    /**
     * 驗證結果密封類
     */
    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val errors: List<String>) : ValidationResult()
        
        fun isValid(): Boolean = this is Valid
        fun isInvalid(): Boolean = this is Invalid
        
        // 移除 fun getErrors(): List<String>
        // 直接用 errors 屬性即可
    }
}

/**
 * MTK YAML 配置數據類別
 */
data class MTKYAMLConfig(
    var modelPath: String = "",
    var preloadSharedWeights: Boolean = true,
    var maxTokens: Int = 2048,
    var temperature: Float = 0.8f,
    var topK: Int = 40,
    var repetitionPenalty: Float = 1.1f
) {
    
    /**
     * 驗證配置有效性
     */
    fun validate(): Boolean {
        return modelPath.isNotEmpty() &&
                maxTokens > 0 &&
                temperature >= 0.0f && temperature <= 2.0f &&
                topK > 0 &&
                repetitionPenalty >= 0.0f
    }
    
    /**
     * 轉換為 MTKConfig
     */
    fun toMTKConfig(): MTKConfig {
        return MTKConfig(
            modelPath = modelPath,
            preloadSharedWeights = preloadSharedWeights,
            defaultMaxTokens = maxTokens,
            defaultTemperature = temperature,
            defaultTopK = topK,
            defaultRepetitionPenalty = repetitionPenalty
        )
    }
} 