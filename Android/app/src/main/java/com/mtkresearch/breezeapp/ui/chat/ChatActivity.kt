package com.mtkresearch.breezeapp.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.data.models.ChatMessage
import com.mtkresearch.breezeapp.data.models.MediaType
import com.mtkresearch.breezeapp.databinding.ActivityChatBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    
    // Adapter for chat messages
    private val chatAdapter = ChatMessageAdapter(
        onMediaClicked = { message ->
            message.mediaUri?.let { showFullImage(it) }
        },
        onTtsRequested = { message ->
            // TTS feature would be implemented here
            Toast.makeText(this, "TTS not implemented yet", Toast.LENGTH_SHORT).show()
        }
    )
    
    // URI for captured images
    private var capturedImageUri: Uri? = null
    private var currentAttachmentUri: Uri? = null
    private var currentAttachmentType: MediaType? = null
    
    // Activity result launchers
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            setAttachment(it, MediaType.IMAGE)
        }
    }
    
    private val captureImageLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            capturedImageUri?.let {
                setAttachment(it, MediaType.IMAGE)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }
    
    private fun setupClickListeners() {
        // Send button
        binding.sendButton.setOnClickListener {
            val message = binding.messageEditText.text.toString().trim()
            sendMessage(message)
        }
        
        // Voice input button
        binding.voiceInputButton.setOnClickListener {
            requestAudioPermission()
        }
        
        // Attachment button
        binding.attachButton.setOnClickListener {
            showAttachmentOptions()
        }
        
        // Remove attachment button
        binding.removeAttachmentButton.setOnClickListener {
            clearAttachment()
        }
        
        // Edit text changes
        binding.messageEditText.addTextChangedListener {
            updateSendButtonState()
        }
    }
    
    private fun observeViewModel() {
        // Observe UI state
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ChatUiState.Loading -> {
                        binding.loadingIndicator.visibility = View.VISIBLE
                    }
                    is ChatUiState.Ready -> {
                        binding.loadingIndicator.visibility = View.GONE
                    }
                    is ChatUiState.Error -> {
                        binding.loadingIndicator.visibility = View.GONE
                        Toast.makeText(this@ChatActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        
        // Observe messages
        lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                chatAdapter.submitList(messages)
                if (messages.isNotEmpty()) {
                    binding.messagesRecyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }
    }
    
    private fun sendMessage(message: String) {
        if (message.isBlank() && currentAttachmentUri == null) return
        
        if (currentAttachmentUri != null && currentAttachmentType != null) {
            viewModel.sendMessageWithMedia(message, currentAttachmentUri!!, currentAttachmentType!!)
            clearAttachment()
        } else {
            viewModel.sendMessage(message)
        }
        
        binding.messageEditText.text.clear()
    }
    
    private fun updateSendButtonState() {
        val hasText = binding.messageEditText.text.isNotEmpty()
        val hasAttachment = currentAttachmentUri != null
        
        binding.sendButton.isEnabled = hasText || hasAttachment
        binding.sendButton.alpha = if (hasText || hasAttachment) 1.0f else 0.5f
    }
    
    private fun setAttachment(uri: Uri, type: MediaType) {
        currentAttachmentUri = uri
        currentAttachmentType = type
        
        binding.attachmentPreview.setImageURI(uri)
        binding.attachmentPreviewLayout.visibility = View.VISIBLE
        
        updateSendButtonState()
    }
    
    private fun clearAttachment() {
        currentAttachmentUri = null
        currentAttachmentType = null
        
        binding.attachmentPreviewLayout.visibility = View.GONE
        binding.attachmentPreview.setImageURI(null)
        
        updateSendButtonState()
    }
    
    private fun showAttachmentOptions() {
        val options = arrayOf("Take Photo", "Choose from Gallery")
        
        AlertDialog.Builder(this)
            .setTitle("Add Attachment")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermission()
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }
    
    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_CAMERA_REQUEST_CODE
            )
        } else {
            captureImage()
        }
    }
    
    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_AUDIO_REQUEST_CODE
            )
        } else {
            startVoiceInput()
        }
    }
    
    private fun captureImage() {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFile = File(getExternalFilesDir(null), "JPEG_${timeStamp}.jpg")
        
        capturedImageUri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            imageFile
        )
        
        captureImageLauncher.launch(capturedImageUri)
    }
    
    private fun startVoiceInput() {
        // Voice input would be implemented here with ASR
        Toast.makeText(this, "Voice input not implemented yet", Toast.LENGTH_SHORT).show()
    }
    
    private fun showFullImage(uri: Uri) {
        // Image viewer would be implemented here
        Toast.makeText(this, "Image viewer not implemented yet", Toast.LENGTH_SHORT).show()
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PERMISSION_CAMERA_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    captureImage()
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                }
            }
            PERMISSION_AUDIO_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startVoiceInput()
                } else {
                    Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_clear -> {
                showClearConfirmation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showClearConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear Conversation")
            .setMessage("Are you sure you want to clear this conversation? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                viewModel.clearConversation()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    companion object {
        private const val PERMISSION_CAMERA_REQUEST_CODE = 101
        private const val PERMISSION_AUDIO_REQUEST_CODE = 102
    }
} 