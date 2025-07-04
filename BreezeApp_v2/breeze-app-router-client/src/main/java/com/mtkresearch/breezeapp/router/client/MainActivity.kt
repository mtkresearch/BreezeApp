package com.mtkresearch.breezeapp.router.client

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mtkresearch.breezeapp.router.client.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var logAdapter: LogAdapter

    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var cameraImageUri: Uri? = null

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.values.all { it }
            viewModel.logMessage(if (granted) "✅ Permissions granted" else "❌ Permissions denied")
            if (granted) {
                when {
                    permissions.containsKey(Manifest.permission.CAMERA) -> openCamera()
                    permissions.containsKey(Manifest.permission.RECORD_AUDIO) -> startRecording()
                }
            }
        }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            cameraImageUri?.let { viewModel.setSelectedImageUri(it) }
        } else {
            viewModel.logMessage("Camera operation was cancelled.")
        }
    }

    private val selectImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.setSelectedImageUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel.logMessage("Breeze Router Client Initialized.")
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.connectButton.setOnClickListener { 
            if (viewModel.uiState.value.isConnected) viewModel.disconnectFromService()
            else viewModel.connectToService()
        }
        binding.getApiVersionButton.setOnClickListener { viewModel.getApiVersion() }
        binding.hasCapabilityButton.setOnClickListener { viewModel.checkCapabilities() }
        binding.cancelRequestButton.setOnClickListener { viewModel.cancelRequest() }
        
        binding.sendLLMRequestButton.setOnClickListener {
            val prompt = binding.llmInputText.text.toString().ifEmpty { "Hello, world!" }
            viewModel.sendLLMRequest(prompt)
        }
        binding.sendStreamingRequestButton.setOnClickListener {
            val prompt = binding.llmInputText.text.toString().ifEmpty { "Tell me a long story." }
            viewModel.sendLLMRequest(prompt)
        }
        binding.selectImageButton.setOnClickListener { showImageSourceDialog() }
        binding.analyzeImageButton.setOnClickListener {
            val prompt = "Describe this image."
            viewModel.uiState.value.selectedImageUri?.let { viewModel.analyzeImage(prompt, it) }
        }
        binding.recordAudioButton.setOnClickListener {
            if (viewModel.uiState.value.isRecording) stopRecording()
            else startRecording()
        }
        binding.sendAsrButton.setOnClickListener {
            currentAudioFile?.let { viewModel.transcribeAudio(it) }
        }
        binding.sendTtsButton.setOnClickListener {
            val text = binding.ttsInputText.text.toString().ifEmpty { "This is a text-to-speech test." }
            viewModel.sendTTSRequest(text)
        }
        binding.sendGuardrailButton.setOnClickListener {
            val text = binding.guardrailInputText.text.toString().ifEmpty { "This is a test for the guardrail." }
            viewModel.sendGuardrailRequest(text)
        }
        binding.clearLogButton.setOnClickListener { viewModel.clearLogs() }

        logAdapter = LogAdapter()
        binding.logRecyclerView.apply {
            adapter = logAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.connectionStatus.text = state.connectionStatus
                    binding.connectButton.text = if (state.isConnected) "Disconnect" else "Connect"
                    
                    val isReady = state.isConnected
                    binding.getApiVersionButton.isEnabled = isReady
                    binding.hasCapabilityButton.isEnabled = isReady
                    binding.cancelRequestButton.isEnabled = isReady
                    binding.sendLLMRequestButton.isEnabled = isReady
                    binding.sendStreamingRequestButton.isEnabled = isReady
                    binding.selectImageButton.isEnabled = isReady
                    binding.analyzeImageButton.isEnabled = isReady && state.selectedImageUri != null
                    binding.recordAudioButton.isEnabled = isReady
                    binding.sendAsrButton.isEnabled = isReady && state.hasRecordedAudio
                    binding.sendTtsButton.isEnabled = isReady
                    binding.sendGuardrailButton.isEnabled = isReady

                    binding.imagePreview.setImageURI(state.selectedImageUri)
                    binding.imagePreview.visibility = if (state.selectedImageUri != null) android.view.View.VISIBLE else android.view.View.GONE
                    
                    binding.recordAudioButton.text = if (state.isRecording) "⏹️ Stop Recording" else "🎤 Record Audio"
                    
                    logAdapter.submitList(state.logMessages.asReversed())
                    binding.logRecyclerView.post {
                        binding.logRecyclerView.smoothScrollToPosition(0)
                    }
                }
            }
        }
    }

    private fun showImageSourceDialog() {
        val options = arrayOf("Take Photo (Camera)", "Choose from Gallery")
        AlertDialog.Builder(this).setTitle("Image Source")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkPermissionAndOpenCamera()
                    1 -> selectImageLauncher.launch("image/*")
                }
            }.show()
    }

    private fun checkPermissionAndOpenCamera() {
        if (checkSinglePermission(Manifest.permission.CAMERA)) {
            openCamera()
        }
    }

    private fun openCamera() {
        try {
            val file = createImageFile()
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            cameraImageUri = uri
            takePictureLauncher.launch(uri)
        } catch (e: IOException) {
            viewModel.logMessage("❌ Failed to create image file: ${e.message}")
        }
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
    }

    private fun startRecording() {
        if (!checkSinglePermission(Manifest.permission.RECORD_AUDIO)) return
        
        currentAudioFile = File(externalCacheDir, "recording.3gp")
        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(currentAudioFile?.absolutePath)
            try {
                prepare()
                start()
                viewModel.setRecordingState(true)
            } catch (e: IOException) {
                viewModel.logMessage("❌ Recording failed: ${e.message}")
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.run {
            stop()
            release()
        }
        mediaRecorder = null
        viewModel.setRecordingState(false)
        viewModel.setHasRecordedAudio(true)
    }

    private fun checkSinglePermission(permission: String): Boolean {
        return if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(arrayOf(permission))
            false
        } else {
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (viewModel.uiState.value.isRecording) {
            stopRecording()
        }
        viewModel.disconnectFromService()
    }
} 