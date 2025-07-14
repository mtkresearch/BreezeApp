package com.mtkresearch.breezeapp.router.data.manager

import com.mtkresearch.breezeapp.router.domain.model.ModelConfig
import com.mtkresearch.breezeapp.router.domain.model.ModelInfo
import com.mtkresearch.breezeapp.router.domain.model.ModelFile
import java.io.File

/**
 * [核心規範]：所有模型路徑與 metadata 操作必須統一由 ModelManager/ModelVersionStore 提供，
 * 嚴禁於 Runner 或 util class 內自行拼接或查詢！
 */
interface ModelManager {
    fun listAvailableModels(): List<ModelInfo>
    fun listDownloadedModels(): List<ModelInfo>
    fun getCurrentModel(runner: String): ModelInfo?
    fun downloadModel(modelId: String, listener: DownloadListener): Unit
    fun switchModel(modelId: String): Boolean
    fun deleteModel(modelId: String): Boolean
    fun cleanupOldVersions(keepLatest: Int = 1): Int
}

interface ModelVersionStore {
    fun getDownloadedModels(): List<ModelInfo>
    fun getModelFiles(modelId: String): List<java.io.File>
    fun saveModelMetadata(modelInfo: ModelInfo): Boolean
    fun removeModel(modelId: String): Boolean
    fun getCurrentModelId(runner: String): String?
    fun setCurrentModelId(runner: String, modelId: String): Boolean
    fun validateModelFiles(modelInfo: ModelInfo): Boolean
}

interface ModelRegistry {
    fun listAllModels(): List<ModelInfo>
    fun getModelInfo(modelId: String): ModelInfo?
    fun filterByHardware(hw: String): List<ModelInfo>
}

/**
 * 下載進度/錯誤回報介面（進階版）
 * 維持 router 一致風格，並支援多檔案、細緻進度、異常、暫停/續傳等事件
 */
interface DownloadListener {
    /**
     * 單一檔案進度回報
     * @param modelId 模型ID
     * @param fileName 檔案名稱
     * @param fileIndex 檔案索引（從0起）
     * @param fileCount 總檔案數
     * @param bytesDownloaded 已下載位元組數
     * @param totalBytes 檔案總大小
     * @param speed 下載速度（bytes/sec）
     * @param eta 預估剩餘秒數
     */
    fun onFileProgress(
        modelId: String,
        fileName: String,
        fileIndex: Int,
        fileCount: Int,
        bytesDownloaded: Long,
        totalBytes: Long,
        speed: Long,
        eta: Long
    )

    /**
     * 整體進度回報
     * @param modelId 模型ID
     * @param percent 0~100
     * @param speed 總下載速度（bytes/sec）
     * @param eta 預估剩餘秒數
     */
    fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long)

    /**
     * 單一檔案下載完成
     */
    fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int)

    /**
     * 所有檔案下載完成
     */
    fun onCompleted(modelId: String)

    /**
     * 發生錯誤（可細分型別）
     * @param modelId 模型ID
     * @param error 錯誤例外
     * @param fileName 若為單檔錯誤則帶入
     */
    fun onError(modelId: String, error: Throwable, fileName: String? = null)

    /**
     * 下載暫停
     */
    fun onPaused(modelId: String)

    /**
     * 下載恢復
     */
    fun onResumed(modelId: String)

    /**
     * 下載被取消
     */
    fun onCancelled(modelId: String)
} 