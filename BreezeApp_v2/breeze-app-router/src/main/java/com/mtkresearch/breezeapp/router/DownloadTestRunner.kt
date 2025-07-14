package com.mtkresearch.breezeapp.router

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.mtkresearch.breezeapp.router.data.manager.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DownloadTestRunner {
    fun downloadNpuModel(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val registry = ModelRegistryImpl(context)
            val versionStore = ModelVersionStoreImpl(context)
            val manager = ModelManagerImpl(context, registry, versionStore)
            val modelId = "Breeze2-3B-8W16A-250630-npu"
            val listener = object : DownloadListener {
                override fun onProgress(modelId: String, percent: Int, speed: Long, eta: Long) {
                    Log.i("ModelDownload", "Progress: $percent% (speed=$speed, eta=$eta)")
                }
                override fun onFileProgress(
                    modelId: String, fileName: String, fileIndex: Int, fileCount: Int,
                    bytesDownloaded: Long, totalBytes: Long, speed: Long, eta: Long
                ) {
                    Log.i("ModelDownload", "File: $fileName [$fileIndex/$fileCount] $bytesDownloaded/$totalBytes")
                }
                override fun onFileCompleted(modelId: String, fileName: String, fileIndex: Int, fileCount: Int) {
                    Log.i("ModelDownload", "File completed: $fileName")
                }
                override fun onCompleted(modelId: String) {
                    Log.i("ModelDownload", "Model $modelId download completed!")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "模型下載完成", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onError(modelId: String, error: Throwable, fileName: String?) {
                    Log.e("ModelDownload", "Error: $error, file=$fileName")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "下載失敗: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onPaused(modelId: String) {}
                override fun onResumed(modelId: String) {}
                override fun onCancelled(modelId: String) {}
            }
            manager.downloadModel(modelId, listener)
        }
    }
}
