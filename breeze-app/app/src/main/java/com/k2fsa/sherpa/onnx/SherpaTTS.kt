package com.k2fsa.sherpa.onnx;

import android.content.Context
import android.util.Log
import com.mtkresearch.breeze_app.utils.AppConstants
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class SherpaTTS private constructor(
    private val tts: OfflineTts,
    private val sampleRate: Int
) {
    private var isInitialized = AtomicBoolean(false)
    private var currentCallback: ((FloatArray) -> Int)? = null
    private var isStopped = AtomicBoolean(false)
    private var isReleased = AtomicBoolean(false)

    companion object {
        private const val TAG = "SherpaTTS"
        
        @Volatile
        private var instance: SherpaTTS? = null
        private val instanceLock = Any()

        fun getInstance(context: Context): SherpaTTS {
            val currentInstance = instance
            if (currentInstance != null && !currentInstance.isReleased.get()) {
                return currentInstance
            }

            synchronized(instanceLock) {
                var localInstance = instance
                if (localInstance == null || localInstance.isReleased.get()) {
                    localInstance = createInstance(context)
                    instance = localInstance
                }
                return localInstance
            }
        }

        private fun createInstance(context: Context): SherpaTTS {
            try {
                val modelConfig = ModelConfig(
                    modelDir = AppConstants.TTS_MODEL_DIR,
                    modelName = AppConstants.TTS_MODEL_FILE,
                    lexicon = AppConstants.TTS_LEXICON_FILE
                )

                val assets = context.assets
                val modelDir = modelConfig.modelDir

                // Create TTS config
                val config = getOfflineTtsConfig(
                    modelDir = modelDir,
                    modelName = modelConfig.modelName,
                    lexicon = modelConfig.lexicon ?: "",
                    dataDir = modelConfig.dataDir ?: "",
                    dictDir = "",
                    ruleFsts = "",
                    ruleFars = ""
                )

                Log.d(TAG, "Initializing TTS with config: $config")
                val tts = OfflineTts(assets, config)
                return SherpaTTS(tts, tts.sampleRate()).also {
                    it.isInitialized.set(true)
                    Log.d(TAG, "TTS initialization completed. Speakers: ${it.getNumSpeakers()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SherpaTTS instance", e)
                throw e
            }
        }

        private fun copyDataDir(context: Context, dataDir: String): String {
            Log.i(TAG, "Copying data dir: $dataDir")
            copyAssets(context, dataDir)
            val newDataDir = context.getExternalFilesDir(null)!!.absolutePath
            Log.i(TAG, "New data dir: $newDataDir")
            return newDataDir
        }

        private fun copyAssets(context: Context, path: String) {
            try {
                val assets = context.assets.list(path)
                if (assets.isNullOrEmpty()) {
                    copyFile(context, path)
                } else {
                    val fullPath = "${context.getExternalFilesDir(null)}/$path"
                    File(fullPath).mkdirs()
                    assets.forEach { asset ->
                        val subPath = if (path.isEmpty()) asset else "$path/$asset"
                        copyAssets(context, subPath)
                    }
                }
            } catch (ex: IOException) {
                Log.e(TAG, "Failed to copy $path", ex)
            }
        }

        private fun copyFile(context: Context, filename: String) {
            try {
                context.assets.open(filename).use { input ->
                    val newFilename = "${context.getExternalFilesDir(null)}/$filename"
                    FileOutputStream(newFilename).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to copy $filename", ex)
            }
        }
    }

    fun isInitialized(): Boolean = isInitialized.get()

    fun checkInitialized() {
        if (!isInitialized.get()) {
            throw IllegalStateException("TTS not initialized")
        }
    }

    fun speak(text: String, speakerId: Int = 0, speed: Float = 1.0f): FloatArray {
        checkInitialized()
        return tts.generate(text, speakerId, speed).samples
    }

    fun getSampleRate(): Int {
        checkInitialized()
        return sampleRate
    }

    fun getNumSpeakers(): Int {
        checkInitialized()
        return tts.numSpeakers()
    }

    fun testTTS(): Boolean {
        try {
            checkInitialized()
            val testText = "Test."
            val samples = speak(testText)
            return samples.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "TTS test failed", e)
            return false
        }
    }

    fun release() {
        synchronized(instanceLock) {
            if (!isReleased.get()) {
                try {
                    if (isInitialized.get()) {
                        tts.release()
                    }
                    isReleased.set(true)
                    isInitialized.set(false)
                    instance = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing TTS resources", e)
                }
            }
        }
    }

    protected fun finalize() {
        release()
    }

    fun synthesize(
        text: String,
        speakerId: Int = 0,
        speed: Float = 1.0f,
        onSamples: (FloatArray) -> Unit,
        onComplete: () -> Unit
    ) {
        checkInitialized()
        try {
            val samples = tts.generate(text, speakerId, speed).samples
            onSamples(samples)
            onComplete()
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            throw e
        }
    }

    fun stop() {
        isStopped.set(true)
        currentCallback = null
    }
}

// Data class to hold model configuration
data class ModelConfig(
    val modelDir: String,
    val modelName: String,
    val lexicon: String? = null,
    val dataDir: String? = null,
    var dictDir: String? = null,
    var ruleFsts: String? = null,
    val ruleFars: String? = null,
) 