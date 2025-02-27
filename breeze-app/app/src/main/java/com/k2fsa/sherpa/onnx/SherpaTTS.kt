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
                // Check if models exist
                if (!AppConstants.hasTTSModels(context)) {
                    throw IOException("TTS models not found. Please download them first.")
                }

                // Get model path and determine if we should use assets or file system
                val modelPath = AppConstants.getTTSModelPath(context)
                if (modelPath == null) {
                    throw IOException("Could not determine TTS model path")
                }

                // If the model is in the app's private storage, use the full path
                val useAssets = !modelPath.startsWith(context.filesDir.absolutePath)
                val modelDir = if (useAssets) {
                    AppConstants.TTS_MODEL_DIR
                } else {
                    AppConstants.getAppTTSModelDir(context)
                }

                // Create TTS config with the appropriate paths
                val config = getOfflineTtsConfig(
                    modelDir = modelDir,
                    modelName = AppConstants.TTS_MODEL_FILE,
                    lexicon = AppConstants.TTS_LEXICON_FILE,
                    dataDir = "",  // Empty string for unused parameters
                    dictDir = "",  // Empty string for unused parameters
                    ruleFsts = "", // Empty string for unused parameters
                    ruleFars = ""  // Empty string for unused parameters
                )

                Log.d(TAG, "Initializing TTS with config: $config")
                val tts = OfflineTts(if (useAssets) context.assets else null, config)
                return SherpaTTS(tts, tts.sampleRate()).also {
                    it.isInitialized.set(true)
                    Log.d(TAG, "TTS initialization completed. Speakers: ${it.getNumSpeakers()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize TTS", e)
                throw e
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
    val dataDir: String? = null
) 