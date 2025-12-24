package com.mtkresearch.breezeapp.presentation.settings.test

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.databinding.ActivityFunctionalityTestBinding
import com.mtkresearch.breezeapp.presentation.common.base.BaseActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class FunctionalityTestActivity : BaseActivity() {

    private lateinit var binding: ActivityFunctionalityTestBinding
    private val viewModel: FunctionalityTestViewModel by viewModels()

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.values.all { it }
            viewModel.logMessage(if (granted) "✅ Permissions granted" else "❌ Permissions denied")
            if (granted && permissions.containsKey(Manifest.permission.RECORD_AUDIO)) {
                viewModel.startMicrophoneStreaming()
            }
        }

    private val selectAudioLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.setSelectedAudioFileUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFunctionalityTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupUI()
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupUI() {
        // Connection
        binding.connectButton.setOnClickListener {
            if (viewModel.uiState.value.isConnected) viewModel.disconnectFromService()
            else viewModel.connectToService()
        }

        // LLM
        binding.sendLLMRequestButton.setOnClickListener {
            if (!canPerformAIAction()) return@setOnClickListener
            val prompt = binding.llmInputText.text.toString().ifEmpty { "Hello" }
            viewModel.sendLLMRequest(prompt, false)
        }
        binding.sendStreamingRequestButton.setOnClickListener {
            if (!canPerformAIAction()) return@setOnClickListener
            val prompt = binding.llmInputText.text.toString().ifEmpty { "Hello" }
            viewModel.sendLLMRequest(prompt, true)
        }

        // TTS
        binding.sendTtsButton.setOnClickListener {
            if (!canPerformAIAction()) return@setOnClickListener
            val text = binding.ttsInputText.text.toString().ifEmpty { "Hello" }
            viewModel.sendTTSRequest(text)
        }

        // ASR - Audio source toggle
        binding.audioSourceToggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnSourceFile -> {
                        viewModel.setAudioSource(AudioSource.FILE)
                        binding.fileSourceLayout.visibility = View.VISIBLE
                        binding.recordAudioButton.visibility = View.GONE
                    }
                    R.id.btnSourceMic -> {
                        viewModel.setAudioSource(AudioSource.MICROPHONE)
                        binding.fileSourceLayout.visibility = View.GONE
                        binding.recordAudioButton.visibility = View.VISIBLE
                    }
                }
            }
        }
        // Set initial selection
        binding.audioSourceToggle.check(R.id.btnSourceFile)

        binding.selectAudioFileButton.setOnClickListener {
            selectAudioLauncher.launch("audio/*")
        }
        binding.sendAsrButton.setOnClickListener {
            viewModel.transcribeAudio()
        }
        binding.recordAudioButton.setOnClickListener {
            if (viewModel.uiState.value.isRecording) {
                viewModel.stopMicrophoneStreaming()
            } else {
                checkMicPermissionAndStart()
            }
        }

        // Logs
        binding.clearLogButton.setOnClickListener { viewModel.clearLogs() }

        // Setup scrolling for log view only (LLM and ASR use ScrollView wrapper)
        binding.logTextView.movementMethod = ScrollingMovementMethod.getInstance()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Connection status
                    binding.connectionStatus.text = state.connectionStatus
                    binding.connectButton.text = if (state.isConnected) "Disconnect" else "Connect"
                    binding.progressConnection.visibility = if (state.isConnecting) View.VISIBLE else View.GONE

                    // Connection status icon
                    val connectionIcon = when {
                        state.isConnected -> R.drawable.ic_chat  // Use chat icon for connected
                        state.isConnecting -> R.drawable.ic_refresh
                        else -> R.drawable.ic_error
                    }
                    binding.ivConnectionStatus.setImageResource(connectionIcon)

                    val connectionColor = when {
                        state.isConnected -> android.R.color.holo_green_dark
                        state.isConnecting -> R.color.primary
                        else -> android.R.color.holo_red_dark
                    }
                    binding.connectionStatus.setTextColor(ContextCompat.getColor(this@FunctionalityTestActivity, connectionColor))

                    // Enable/disable buttons
                    val isReady = state.isConnected
                    binding.sendLLMRequestButton.isEnabled = isReady && !state.isLlmLoading
                    binding.sendStreamingRequestButton.isEnabled = isReady && !state.isLlmLoading
                    binding.sendTtsButton.isEnabled = isReady && !state.isTtsLoading
                    binding.selectAudioFileButton.isEnabled = isReady
                    binding.sendAsrButton.isEnabled = isReady && state.selectedAudioFileUri != null && !state.isAsrLoading
                    binding.recordAudioButton.isEnabled = isReady

                    // LLM Response
                    binding.llmResponseTextView.text = state.llmResponse.ifEmpty { "Response will appear here..." }

                    // LLM Metrics
                    state.llmMetrics?.let { metrics ->
                        binding.llmMetricsLayout.visibility = View.VISIBLE
                        binding.tvLlmMetrics.text = formatLlmMetrics(metrics)
                    } ?: run {
                        if (!state.isLlmLoading) {
                            binding.llmMetricsLayout.visibility = View.GONE
                        }
                    }

                    // TTS Metrics
                    state.ttsMetrics?.let { metrics ->
                        binding.ttsMetricsLayout.visibility = View.VISIBLE
                        binding.tvTtsMetrics.text = formatTtsMetrics(metrics)
                    } ?: run {
                        if (!state.isTtsLoading) {
                            binding.ttsMetricsLayout.visibility = View.GONE
                        }
                    }

                    // ASR Response
                    binding.asrResponseTextView.text = state.asrResponse.ifEmpty { "Transcription will appear here..." }

                    // ASR Metrics
                    state.asrMetrics?.let { metrics ->
                        binding.asrMetricsLayout.visibility = View.VISIBLE
                        binding.tvAsrMetrics.text = formatAsrMetrics(metrics)
                    } ?: run {
                        if (!state.isAsrLoading) {
                            binding.asrMetricsLayout.visibility = View.GONE
                        }
                    }

                    // Mic button text
                    binding.recordAudioButton.text = if (state.isRecording) "⏹️ Stop Recording" else "🎤 Start Recording"

                    // Logs
                    val logs = state.logMessages.joinToString("\n")
                    if (binding.logTextView.text.toString() != logs) {
                        binding.logTextView.text = logs
                        binding.logTextView.post {
                            val layout = binding.logTextView.layout
                            if (layout != null) {
                                val scrollAmount = layout.getLineTop(binding.logTextView.lineCount) - binding.logTextView.height
                                if (scrollAmount > 0) {
                                    binding.logTextView.scrollTo(0, scrollAmount)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatLlmMetrics(metrics: LlmMetrics): String = buildString {
        if (metrics.success) {
            append("✓ ${if (metrics.isStreaming) "Streaming" else "Complete"}\n")
            append("⏱️ Total: ${metrics.totalLatencyMs}ms")
            metrics.timeToFirstTokenMs?.let { append(" | TTFT: ${it}ms") }
            append("\n")
            metrics.totalTokens?.let {
                append("🔢 Tokens: $it")
                metrics.promptTokens?.let { p -> append(" (P:$p") }
                metrics.completionTokens?.let { c -> append(" C:$c)") }
                append("\n")
            }
            append("📝 Length: ${metrics.responseLength} chars")
        } else {
            append("✗ Error: ${metrics.errorMessage ?: "Unknown"}")
        }
    }

    private fun formatTtsMetrics(metrics: TtsMetrics): String = buildString {
        if (metrics.success) {
            append("✓ Completed\n")
            append("⏱️ Total: ${metrics.totalLatencyMs}ms")
            metrics.timeToFirstAudioMs?.let { append(" | TTFA: ${it}ms") }
        } else {
            append("✗ Error: ${metrics.errorMessage ?: "Unknown"}")
        }
    }

    private fun formatAsrMetrics(metrics: AsrMetrics): String = buildString {
        if (metrics.success) {
            append("✓ Transcribed\n")
            append("⏱️ Total: ${metrics.totalLatencyMs}ms\n")
            append("📝 Length: ${metrics.transcriptionLength} chars")
            metrics.language?.let { append("\n🌐 Language: $it") }
            metrics.confidence?.let { append("\n🎯 Confidence: ${"%.1f".format(it * 100)}%") }
        } else {
            append("✗ Error: ${metrics.errorMessage ?: "Unknown"}")
        }
    }

    private fun checkMicPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        } else {
            viewModel.startMicrophoneStreaming()
        }
    }

    // Implement global action blocking
    override fun onDownloadStateChanged(isDownloading: Boolean, modelId: String?) {
        super.onDownloadStateChanged(isDownloading, modelId)
        
        runOnUiThread {
            // Disable/Enable buttons based on download state
            binding.sendLLMRequestButton.isEnabled = !isDownloading
            binding.sendStreamingRequestButton.isEnabled = !isDownloading
            binding.sendTtsButton.isEnabled = !isDownloading
            binding.recordAudioButton.isEnabled = !isDownloading
            
            if (!isDownloading) {
                val statusMsg = "✅ Download complete. Model ready: ${modelId ?: "Unknown"}"
                binding.logTextView.append("\n$statusMsg")
                val scrollAmount = binding.logTextView.layout?.getLineTop(binding.logTextView.lineCount) ?: 0
                if (scrollAmount > binding.logTextView.height) {
                    binding.logTextView.scrollTo(0, scrollAmount - binding.logTextView.height)
                }
            }
        }
    }

    override fun onDownloadProgressUpdate(modelId: String, fileName: String, currentFileIndex: Int, totalFiles: Int) {
        super.onDownloadProgressUpdate(modelId, fileName, currentFileIndex, totalFiles)
        runOnUiThread {
            val progressStr = if (currentFileIndex >= 0 && totalFiles > 0) {
                 "(${currentFileIndex + 1}/$totalFiles)"
            } else ""
            
            val statusMsg = "⚠️ Downloading model$progressStr: $fileName..."
            binding.logTextView.append("\n$statusMsg")
            val scrollAmount = binding.logTextView.layout?.getLineTop(binding.logTextView.lineCount) ?: 0
            if (scrollAmount > binding.logTextView.height) {
                binding.logTextView.scrollTo(0, scrollAmount - binding.logTextView.height)
            }
        }
    }
}
