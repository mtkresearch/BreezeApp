package com.mtkresearch.breezeapp.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.core.widget.addTextChangedListener
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.button.MaterialButton
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.data.models.ChatMessage
import com.mtkresearch.breezeapp.data.models.MediaType
import com.mtkresearch.breezeapp.data.models.SavedConversation
import com.mtkresearch.breezeapp.databinding.ActivityChatBinding
import com.mtkresearch.breezeapp.ui.history.ConversationHistoryFragment
import com.mtkresearch.breezeapp.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "ChatActivity"
        private const val REQUEST_CAMERA = 100
        private const val REQUEST_GALLERY = 101
        private const val REQUEST_AUDIO = 102
    }
    
    // ViewBinding instance
    private lateinit var binding: ActivityChatBinding
    
    // ViewModel
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
    
    // Views
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var searchConversations: EditText
    private lateinit var btnNewChat: MaterialButton
    private lateinit var settingsOption: View
    private lateinit var aboutOption: View
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: MaterialButton
    private lateinit var attachButton: MaterialButton
    private lateinit var voiceInputButton: MaterialButton
    private lateinit var attachmentPreviewLayout: LinearLayout
    private lateinit var attachmentPreview: ImageView
    private lateinit var removeAttachmentButton: MaterialButton
    
    // History fragment
    private var historyFragment: ConversationHistoryFragment? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize views
        drawerLayout = findViewById(R.id.drawerLayout)
        searchConversations = findViewById(R.id.searchConversations)
        btnNewChat = findViewById(R.id.btnNewChat)
        settingsOption = findViewById(R.id.settingsOption)
        aboutOption = findViewById(R.id.aboutOption)
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView)
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        attachButton = findViewById(R.id.attachButton)
        voiceInputButton = findViewById(R.id.voiceInputButton)
        attachmentPreviewLayout = findViewById(R.id.attachmentPreviewLayout)
        attachmentPreview = findViewById(R.id.attachmentPreview)
        removeAttachmentButton = findViewById(R.id.removeAttachmentButton)
        
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        setupNavigation()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }
    
    private fun setupNavigation() {
        // Initialize drawer components
        drawerLayout = findViewById(R.id.drawerLayout)
        
        // Get references from the drawer layout
        val drawerContent = findViewById<View>(R.id.drawerContent)
        searchConversations = drawerContent.findViewById(R.id.searchConversations)
        btnNewChat = drawerContent.findViewById(R.id.btnNewChat)
        settingsOption = drawerContent.findViewById(R.id.settingsOption)
        aboutOption = drawerContent.findViewById(R.id.aboutOption)

        // Toggle button for drawer
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, binding.toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        // Set up click listeners for drawer elements
        btnNewChat.setOnClickListener {
            viewModel.clearConversation()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        settingsOption.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        aboutOption.setOnClickListener {
            showAboutDialog()
            drawerLayout.closeDrawer(GravityCompat.START)
        }
        
        // Search functionality
        searchConversations.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                historyFragment?.filterConversations(query)
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        setupHistoryFragment()
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
            // If we have media but no text, send as a media-only message
            if (message.isBlank()) {
                viewModel.sendMediaMessage(currentAttachmentUri!!, currentAttachmentType!!)
            } else {
                // Otherwise, send as a message with media
                viewModel.sendMessageWithMedia(message, currentAttachmentUri!!, currentAttachmentType!!)
            }
            clearAttachment()
        } else {
            viewModel.sendMessage(message)
        }
        
        binding.messageEditText.text?.clear()
    }
    
    private fun updateSendButtonState() {
        val hasText = binding.messageEditText.text?.isNotEmpty() ?: false
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
                REQUEST_CAMERA
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
                REQUEST_AUDIO
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
            REQUEST_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    captureImage()
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_AUDIO -> {
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
            R.id.action_save -> {
                showSaveDialog()
                true
            }
            R.id.action_system_prompt -> {
                showSystemPromptDialog()
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
    
    private fun showSaveDialog() {
        val input = EditText(this)
        input.hint = "Conversation name"
        
        AlertDialog.Builder(this)
            .setTitle("Save Conversation")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    val conversationId = viewModel.saveConversation(name)
                    
                    // Log saved conversations for debugging
                    Log.d(TAG, "Saved conversation with ID: $conversationId and name: $name")
                    viewModel.getConversationRepository().getSavedConversations().forEach { conversation ->
                        Log.d(TAG, "Saved conversation: ${conversation.id} - ${conversation.title}")
                    }
                    
                    Toast.makeText(this, "Conversation saved as: $name", Toast.LENGTH_SHORT).show()
                    
                    // Update the history fragment
                    historyFragment?.setRepository(viewModel.getConversationRepository())
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showSystemPromptDialog() {
        val input = EditText(this)
        input.setText(viewModel.getSystemPrompt())
        
        AlertDialog.Builder(this)
            .setTitle("System Prompt")
            .setMessage("Edit the system instructions for the AI:")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newPrompt = input.text.toString().trim()
                if (newPrompt.isNotEmpty()) {
                    viewModel.updateSystemPrompt(newPrompt)
                    Toast.makeText(this, "System prompt updated", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
    
    private fun setupHistoryFragment() {
        // Create a new instance if needed
        if (historyFragment == null) {
            historyFragment = ConversationHistoryFragment()
        }
        
        // Set repository and callbacks
        historyFragment?.apply {
            // Set repository first so it's available before the fragment is attached
            setRepository(viewModel.getConversationRepository())
            
            // Set callbacks
            onConversationSelected = { conversation ->
                loadConversation(conversation)
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            
            onConversationDeleted = { conversation ->
                Toast.makeText(
                    this@ChatActivity,
                    "Deleted: ${conversation.title}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        // Add the fragment to the UI only if it hasn't been added yet
        val currentFragment = supportFragmentManager.findFragmentById(R.id.conversationListContainer)
        if (currentFragment == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.conversationListContainer, historyFragment!!)
                .commit()
        }
    }
    
    private fun loadConversation(conversation: SavedConversation) {
        // Load the conversation
        viewModel.loadConversation(conversation.id)
        Toast.makeText(
            this,
            "Loaded: ${conversation.title}",
            Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("About Breeze AI")
            .setMessage("Version 1.0.0\n\nA cutting-edge AI chat application powered by on-device language models.")
            .setPositiveButton("OK", null)
            .show()
    }
} 