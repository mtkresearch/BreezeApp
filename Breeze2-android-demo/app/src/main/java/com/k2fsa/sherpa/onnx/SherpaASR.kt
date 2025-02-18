package com.k2fsa.sherpa.onnx

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlin.concurrent.thread

class SherpaASR(private val context: Context) {
    companion object {
        private const val TAG = "SherpaASR"
        private const val SAMPLE_RATE = 16000
    }

    interface ASRListener {
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(error: String)
    }

    private var recognizer: OnlineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var listener: ASRListener? = null

    private val audioSource = MediaRecorder.AudioSource.MIC
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    @Volatile
    private var isRecording = false

    fun initialize() {
        if (!checkPermission()) {
            throw IllegalStateException("RECORD_AUDIO permission not granted")
        }
        initModel()
    }

    fun startRecognition(listener: ASRListener) {
        if (isRecording) return
        this.listener = listener

        if (!initMicrophone()) {
            listener.onError("Failed to initialize microphone")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = thread(start = true) {
            processSamples()
        }
        Log.i(TAG, "Started recording")
    }

    fun stopRecognition() {
        if (!isRecording) return

        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        Log.i(TAG, "Stopped recording")
    }

    fun release() {
        stopRecognition()
        recognizer?.release()
        recognizer = null
    }

    private fun processSamples() {
        Log.i(TAG, "Processing samples")
        val stream = recognizer?.createStream() ?: return

        val interval = 0.1 // 100ms
        val bufferSize = (interval * SAMPLE_RATE).toInt()
        val buffer = ShortArray(bufferSize)

        while (isRecording) {
            val ret = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (ret > 0) {
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                stream.acceptWaveform(samples, SAMPLE_RATE)

                while (recognizer?.isReady(stream) == true) {
                    recognizer?.decode(stream)
                }

                val isEndpoint = recognizer?.isEndpoint(stream) == true
                var text = recognizer?.getResult(stream)?.text ?: ""

                // Handle streaming paraformer
                if (isEndpoint && recognizer?.config?.modelConfig?.paraformer?.encoder?.isNotBlank() == true) {
                    val tailPaddings = FloatArray((0.8 * SAMPLE_RATE).toInt())
                    stream.acceptWaveform(tailPaddings, SAMPLE_RATE)
                    while (recognizer?.isReady(stream) == true) {
                        recognizer?.decode(stream)
                    }
                    text = recognizer?.getResult(stream)?.text ?: ""
                }

                if (text.isNotBlank()) {
                    listener?.onPartialResult(text)
                }

                if (isEndpoint) {
                    recognizer?.reset(stream)
                    if (text.isNotBlank()) {
                        listener?.onFinalResult(text)
                    }
                }
            }
        }
        stream.release()
    }

    private fun initMicrophone(): Boolean {
        if (!checkPermission()) return false

        val numBytes = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioFormat)
        Log.i(TAG, "Buffer size in milliseconds: ${numBytes * 1000.0f / SAMPLE_RATE}")

        audioRecord = AudioRecord(
            audioSource,
            SAMPLE_RATE,
            channelConfig,
            audioFormat,
            numBytes * 2 // a sample has two bytes as we are using 16-bit PCM
        )

        return audioRecord?.state == AudioRecord.STATE_INITIALIZED
    }

    private fun initModel() {
        val type = 0 // default model type

        val config = OnlineRecognizerConfig(
            featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = getModelConfig(type = type)!!,
            endpointConfig = getEndpointConfig(),
            enableEndpoint = true
        )

        recognizer = OnlineRecognizer(
            assetManager = context.assets,
            config = config
        )
    }

    private fun checkPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Transcribe audio from a file in assets folder
     */
    fun transcribeAsset(assetPath: String, listener: ASRListener) {
        try {
            val waveData = WaveReader.readWave(context.assets, assetPath)
            processWaveData(waveData, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing asset: $assetPath", e)
            listener.onError("Failed to transcribe audio file: ${e.message}")
        }
    }

    /**
     * Transcribe audio from a file on device storage
     */
    fun transcribeFile(filePath: String, listener: ASRListener) {
        try {
            val waveData = WaveReader.readWave(filePath)
            processWaveData(waveData, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Error transcribing file: $filePath", e)
            listener.onError("Failed to transcribe audio file: ${e.message}")
        }
    }

    private fun processWaveData(waveData: WaveData, listener: ASRListener) {
        if (recognizer == null) {
            initModel()
        }

        val stream = recognizer?.createStream() ?: return
        try {
            // Process audio in chunks to simulate real-time processing
            val chunkSize = (0.1 * SAMPLE_RATE).toInt() // 100ms chunks
            val samples = waveData.samples
            var offset = 0

            while (offset < samples.size) {
                val length = minOf(chunkSize, samples.size - offset)
                val chunk = samples.copyOfRange(offset, offset + length)
                
                stream.acceptWaveform(chunk, waveData.sampleRate)

                while (recognizer?.isReady(stream) == true) {
                    recognizer?.decode(stream)
                }

                val isEndpoint = recognizer?.isEndpoint(stream) == true
                var text = recognizer?.getResult(stream)?.text ?: ""

                if (text.isNotBlank()) {
                    listener.onPartialResult(text)
                }

                if (isEndpoint) {
                    recognizer?.reset(stream)
                    if (text.isNotBlank()) {
                        listener.onFinalResult(text)
                    }
                }

                offset += length
            }

            // Process any remaining audio
            val finalText = recognizer?.getResult(stream)?.text ?: ""
            if (finalText.isNotBlank()) {
                listener.onFinalResult(finalText)
            }

        } finally {
            stream.release()
        }
    }
}