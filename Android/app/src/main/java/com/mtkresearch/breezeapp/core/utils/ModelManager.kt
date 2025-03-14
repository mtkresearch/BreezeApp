package com.mtkresearch.breezeapp.core.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

/**
 * Utility class for managing AI model files
 */
class ModelManager(private val context: Context) {
    companion object {
        private const val TAG = "ModelManager"
    }
    
    // Model directory
    private val modelDir: File by lazy {
        File(AppConstants.getAppModelDir(context)).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    // Download state
    private val _downloadProgress = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadProgress: StateFlow<DownloadState> = _downloadProgress.asStateFlow()
    
    /**
     * Get all available model files in the app's model directory
     */
    fun getAvailableModels(modelType: ModelType): List<ModelInfo> {
        val models = mutableListOf<ModelInfo>()
        
        if (!modelDir.exists()) {
            return models
        }
        
        // Find all files with the appropriate extension for the model type
        val extension = when (modelType) {
            ModelType.LLM -> ".pte"
            ModelType.VLM -> ".pte"
            ModelType.ASR -> ".bin"
            ModelType.TTS -> ".bin"
        }
        
        modelDir.listFiles { file -> 
            file.isFile && file.name.endsWith(extension) 
        }?.forEach { file ->
            models.add(
                ModelInfo(
                    name = getModelDisplayName(file.name),
                    path = file.absolutePath,
                    size = file.length(),
                    type = modelType
                )
            )
        }
        
        return models
    }
    
    /**
     * Download a model from a URL
     */
    suspend fun downloadModel(url: String, fileName: String): Boolean {
        _downloadProgress.value = DownloadState.Downloading(0)
        
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                val contentLength = connection.contentLength.toLong()
                val inputStream = connection.getInputStream()
                val outputFile = File(modelDir, fileName)
                val outputStream = FileOutputStream(outputFile)
                
                val buffer = ByteArray(8192)
                var totalBytesRead = 0L
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    
                    // Update progress
                    val progress = if (contentLength > 0) {
                        (totalBytesRead * 100 / contentLength).toInt()
                    } else {
                        -1 // Indeterminate
                    }
                    
                    _downloadProgress.value = DownloadState.Downloading(progress)
                }
                
                outputStream.close()
                inputStream.close()
                
                _downloadProgress.value = DownloadState.Completed(outputFile.absolutePath)
                true
            } catch (e: IOException) {
                Log.e(TAG, "Failed to download model: $url", e)
                _downloadProgress.value = DownloadState.Error(e.message ?: "Download failed")
                false
            }
        }
    }
    
    /**
     * Check if a model file exists
     */
    fun modelExists(fileName: String): Boolean {
        return File(modelDir, fileName).exists()
    }
    
    /**
     * Get the path to a model file
     */
    fun getModelPath(fileName: String): String {
        return File(modelDir, fileName).absolutePath
    }
    
    /**
     * Delete a model file
     */
    fun deleteModel(fileName: String): Boolean {
        val file = File(modelDir, fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
    
    /**
     * Convert file name to display name
     */
    private fun getModelDisplayName(fileName: String): String {
        // Remove extension and replace hyphens with spaces
        return fileName.substringBeforeLast(".")
            .replace("-", " ")
            .replace("_", " ")
    }
}

/**
 * Enum for model types
 */
enum class ModelType {
    LLM,    // Language models
    VLM,    // Vision-language models
    ASR,    // Automatic speech recognition
    TTS     // Text-to-speech
}

/**
 * Data class for model information
 */
data class ModelInfo(
    val name: String,
    val path: String,
    val size: Long,
    val type: ModelType
)

/**
 * Sealed class for download states
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState() // -1 for indeterminate
    data class Completed(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
} 