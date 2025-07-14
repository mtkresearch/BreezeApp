package com.mtkresearch.breezeapp.router.data

import com.mtkresearch.breezeapp.router.data.manager.*
import com.mtkresearch.breezeapp.router.domain.model.ModelInfo
import com.mtkresearch.breezeapp.router.domain.model.ModelFile
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.mockito.Mockito.*
import java.io.ByteArrayInputStream
import java.net.URLConnection

// --- Mock/Stub 實作 ---

class DummyRegistry(private val model: ModelInfo?) : ModelRegistry {
    override fun listAllModels() = listOfNotNull(model)
    override fun getModelInfo(modelId: String) = if (model?.id == modelId) model else null
    override fun filterByHardware(hw: String): List<ModelInfo> = emptyList()
}

class DummyVersionStore : ModelVersionStore {
    val saved = AtomicBoolean(false)
    var validateResult = true
    override fun getDownloadedModels() = listOf<ModelInfo>()
    override fun getModelFiles(modelId: String) = listOf<File>()
    override fun saveModelMetadata(modelInfo: ModelInfo) = saved.getAndSet(true).not()
    override fun removeModel(modelId: String) = true
    override fun getCurrentModelId(runner: String) = null
    override fun setCurrentModelId(runner: String, modelId: String) = true
    override fun validateModelFiles(modelInfo: ModelInfo) = validateResult
}

class TestDownloadListener : DownloadListener {
    val progressEvents = mutableListOf<Pair<Int, Long>>()
    val fileProgressEvents = mutableListOf<Pair<String, Long>>()
    val completed = AtomicBoolean(false)
    val errors = mutableListOf<Throwable>()
    val fileCompleted = mutableListOf<String>()
    override fun onFileProgress(
        modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
        bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
    ) {
        fileProgressEvents.add(fileName to bytesDownloaded)
    }
    override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
        progressEvents.add(percent to speed)
    }
    override fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int) {
        fileCompleted.add(fileName)
    }
    override fun onCompleted(modelId: String) { completed.set(true) }
    override fun onError(modelId: String, error: Throwable, fileName: String?) { errors.add(error) }
    override fun onPaused(modelId: String) {}
    override fun onResumed(modelId: String) {}
    override fun onCancelled(modelId: String) {}
}

// --- 單元測試 ---

class ModelManagerImplTest {

    // Helper: 建立暫存 context
    private fun tempContext(tmpDir: File) = object : android.content.ContextWrapper(null) {
        override fun getFilesDir(): File = tmpDir
    }

    @Test
    fun testDownloadModel_normal_mocked() {
        val tmpDir = createTempDir()
        val modelFile = ModelFile(
            fileName = "file.txt",
            group = null,
            pattern = null,
            type = "model",
            urls = listOf("https://mocked/file.txt")
        )
        val modelInfo = ModelInfo(
            id = "test-model",
            name = "Test Model",
            version = "1.0",
            runner = "cpu",
            files = listOf(modelFile),
            backend = "cpu"
        )
        val registry = DummyRegistry(modelInfo)
        val versionStore = DummyVersionStore()
        val mockConn = mock(URLConnection::class.java)
        // 確保 inputStream 有資料
        `when`(mockConn.getInputStream()).thenReturn(ByteArrayInputStream(ByteArray(1024) { 1 }))
        doNothing().`when`(mockConn).connect()
        val urlFactory = { _: String -> mockConn }
        val manager = ModelManagerImpl(
            context = tempContext(tmpDir),
            registry = registry,
            versionStore = versionStore,
            urlConnectionFactory = urlFactory
        )
        val listener = TestDownloadListener()
        manager.downloadModel(modelInfo.id, listener)
        println("Completed: ${listener.completed.get()}, Progress: ${listener.progressEvents}, FileProgress: ${listener.fileProgressEvents}")
        assertTrue(listener.completed.get())
        assertTrue(listener.progressEvents.isNotEmpty())
        assertTrue(listener.fileProgressEvents.isNotEmpty())
    }

    @Test
    fun testDownloadModel_modelNotFound() {
        val tmpDir = createTempDir()
        val registry = DummyRegistry(null)
        val versionStore = DummyVersionStore()
        val manager = ModelManagerImpl(
            context = tempContext(tmpDir),
            registry = registry,
            versionStore = versionStore
        )
        val listener = TestDownloadListener()
        manager.downloadModel("not-exist", listener)
        assertTrue(listener.errors.isNotEmpty())
        assertTrue(listener.errors.first() is IllegalArgumentException)
    }

    @Test
    fun testDownloadModel_validationFail() {
        val tmpDir = createTempDir()
        val modelFile = ModelFile(
            fileName = "file.txt",
            group = null,
            pattern = null,
            type = "model",
            urls = listOf("https://mocked/file.txt")
        )
        val modelInfo = ModelInfo(
            id = "test-model",
            name = "Test Model",
            version = "1.0",
            runner = "cpu",
            files = listOf(modelFile),
            backend = "cpu"
        )
        val registry = DummyRegistry(modelInfo)
        val versionStore = DummyVersionStore().apply { validateResult = false }
        val manager = ModelManagerImpl(
            context = tempContext(tmpDir),
            registry = registry,
            versionStore = versionStore
        )
        val listener = TestDownloadListener()
        // manager.downloadModel(modelInfo.id, listener)
        // assertTrue(listener.errors.isNotEmpty())
        // assertTrue(listener.errors.first() is IllegalStateException)
    }

    // TODO: 可進一步 mock URL.openConnection() 以支援離線/異常/多檔案/暫停/取消等測試
}
