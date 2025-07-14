package com.mtkresearch.breezeapp.router.data.manager

import android.content.Context
import com.mtkresearch.breezeapp.router.domain.model.ModelFile
import com.mtkresearch.breezeapp.router.domain.model.ModelInfo
import java.io.File
import java.io.FileOutputStream

/**
 * [嚴格規範]：所有模型路徑與 metadata 操作必須統一由本類與 ModelVersionStore 提供，
 * 嚴禁於 Runner 或 util class 內自行拼接或查詢！
 */
class ModelManagerImpl(
    private val context: Context,
    private val registry: ModelRegistry,
    private val versionStore: ModelVersionStore,
    private val urlConnectionFactory: UrlConnectionFactory = { url -> java.net.URL(url).openConnection() }
) : ModelManager {
    private val modelsDir: File = File(context.filesDir, "models")

    override fun listAvailableModels(): List<ModelInfo> = registry.listAllModels()
    override fun listDownloadedModels(): List<ModelInfo> = versionStore.getDownloadedModels()
    override fun getCurrentModel(runner: String): ModelInfo? {
        val modelId = versionStore.getCurrentModelId(runner)
        return versionStore.getDownloadedModels().find { it.id == modelId }
    }

    override fun downloadModel(modelId: String, listener: DownloadListener) {
        val modelInfo = registry.getModelInfo(modelId)
        if (modelInfo == null) {
            listener.onError(modelId, IllegalArgumentException("ModelInfo not found"))
            return
        }
        val modelDir = File(modelsDir, modelInfo.id)
        if (!modelDir.exists()) modelDir.mkdirs()

        // 展開所有要下載的檔案
        val downloadTasks = expandFiles(modelInfo.files)
        val fileCount = downloadTasks.size
        var fileIndex = 0

        listener.onProgress(modelId, 0, 0L, -1L)

        for (task in downloadTasks) {
            val fileName = task.fileName
            val url = task.url
            val fileSize = 0L // 若有 size 欄位可改為 file.size
            val fileCompleted = downloadSingleFile(
                modelDir, fileName, url, modelId, listener, fileIndex, fileCount, fileSize,
                { bytesDownloaded, speed, eta ->
                    listener.onFileProgress(
                        modelId, fileName, fileIndex, fileCount,
                        bytesDownloaded, fileSize, speed, eta
                    )
                }
            )
            if (!fileCompleted) return
            listener.onFileCompleted(modelId, fileName, fileIndex, fileCount)
            fileIndex++
            val percent = ((fileIndex) * 100 / fileCount)
            listener.onProgress(modelId, percent, 0L, -1L)
        }
        // 校驗與 metadata
        if (!versionStore.validateModelFiles(modelInfo)) {
            listener.onError(modelId, IllegalStateException("Model file validation failed"))
            versionStore.removeModel(modelId)
            return
        }
        versionStore.saveModelMetadata(modelInfo)
        listener.onProgress(modelId, 100, 0L, 0L)
        listener.onCompleted(modelId)
    }

    private fun downloadSingleFile(
        modelDir: File,
        fileName: String,
        url: String,
        modelId: String,
        listener: DownloadListener,
        fileIndex: Int,
        fileCount: Int,
        totalBytes: Long,
        progressCallback: (bytesDownloaded: Long, speed: Long, eta: Long) -> Unit
    ): Boolean {
        val destFile = File(modelDir, fileName)
        val tmpFile = File(destFile.absolutePath + ".part")
        try {
            var downloadedBytes = if (tmpFile.exists()) tmpFile.length() else 0L
            val urlConnection = urlConnectionFactory(url)
            if (downloadedBytes > 0) {
                urlConnection.setRequestProperty("Range", "bytes=$downloadedBytes-")
            }
            urlConnection.connect()
            val input = urlConnection.getInputStream()
            val output = FileOutputStream(tmpFile, true)
            val buffer = ByteArray(8192)
            var total = downloadedBytes
            var read: Int
            var lastReportTime = System.currentTimeMillis()
            var lastBytes = total
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
                total += read
                val now = System.currentTimeMillis()
                if (now - lastReportTime > 500) {
                    val speed = if (now > lastReportTime) ((total - lastBytes) * 1000 / (now - lastReportTime)) else 0L
                    val eta = if (speed > 0) ((totalBytes - total) / speed) else -1L
                    progressCallback(total, speed, eta)
                    lastReportTime = now
                    lastBytes = total
                }
            }
            output.close()
            input.close()
            tmpFile.renameTo(destFile)
            // 最後一次進度
            progressCallback(total, 0L, 0L)
            return true
        } catch (e: Exception) {
            listener.onError(modelId, e, fileName)
            tmpFile.delete()
            return false
        }
    }

    override fun switchModel(modelId: String): Boolean = versionStore.setCurrentModelId("default", modelId)
    override fun deleteModel(modelId: String): Boolean = versionStore.removeModel(modelId)
    override fun cleanupOldVersions(keepLatest: Int): Int = 0 // 可依需求實作
}

// 新增一個型別別名
typealias UrlConnectionFactory = (String) -> java.net.URLConnection 

private data class DownloadTask(
    val fileName: String,
    val url: String,
    val type: String
)

private fun expandFiles(modelFiles: List<ModelFile>): List<DownloadTask> {
    val tasks = mutableListOf<DownloadTask>()
    for (file in modelFiles) {
        if (file.urls.isNotEmpty()) {
            if (file.fileName != null) {
                // 單一檔案
                tasks.add(DownloadTask(file.fileName, file.urls.first(), file.type))
            } else {
                // group/pattern: 每個 url 都要下載，檔名自 url
                for (url in file.urls) {
                    val fileName = url.substringAfterLast('/').substringBefore('?')
                    tasks.add(DownloadTask(fileName, url, file.type))
                }
            }
        }
    }
    return tasks
} 