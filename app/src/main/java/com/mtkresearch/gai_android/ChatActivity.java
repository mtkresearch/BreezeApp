package com.mtkresearch.gai_android;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextWatcher;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.CheckBox;
import android.app.AlertDialog;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.GravityCompat;

import com.mtkresearch.gai_android.utils.AudioListAdapter;
import com.mtkresearch.gai_android.utils.ChatHistory;
import com.mtkresearch.gai_android.utils.ChatMediaHandler;
import com.mtkresearch.gai_android.utils.ChatMessageAdapter;
import com.mtkresearch.gai_android.databinding.ActivityChatBinding;
import com.mtkresearch.gai_android.utils.ChatMessage;

import java.io.File;

import com.mtkresearch.gai_android.service.ASREngineService;
import com.mtkresearch.gai_android.service.LLMEngineService;
import com.mtkresearch.gai_android.service.TTSEngineService;
import com.mtkresearch.gai_android.service.VLMEngineService;
import com.mtkresearch.gai_android.utils.UiUtils;

import java.io.IOException;
import android.content.pm.PackageManager;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import android.util.Log;

import com.mtkresearch.gai_android.utils.FileUtils;
import com.mtkresearch.gai_android.utils.ChatUIStateHandler;
import com.mtkresearch.gai_android.utils.ConversationManager;
import com.mtkresearch.gai_android.utils.ChatHistoryManager;
import com.mtkresearch.gai_android.utils.ChatHistoryAdapter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import android.graphics.Color;

import com.executorch.ModelType;
import com.executorch.PromptFormat;

public class ChatActivity extends AppCompatActivity implements ChatMessageAdapter.OnSpeakerClickListener {
    private static final String TAG = "ChatActivity";

    // Request codes
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private static final int PICK_FILE_REQUEST = 3;

    // View Binding
    private ActivityChatBinding binding;

    // Handlers
    private ChatMediaHandler mediaHandler;
    private ChatUIStateHandler uiHandler;
    private ConversationManager conversationManager;
    private ChatHistoryManager historyManager;

    // Adapters
    private ChatMessageAdapter chatAdapter;
    private AudioListAdapter audioListAdapter;
    private ChatHistoryAdapter historyAdapter;

    // Services
    private LLMEngineService llmService;
    private VLMEngineService vlmService;
    private ASREngineService asrService;
    private TTSEngineService ttsService;

    private DrawerLayout drawerLayout;

    private static final int CONVERSATION_HISTORY_MESSAGE_LOOKBACK = 2;

    // Add promptId field at the top of the class
    private int promptId = 0;

    private int titleTapCount = 0;
    private static final int TAPS_TO_SHOW_MAIN = 7;
    private static final long TAP_TIMEOUT_MS = 3000; // Reset counter after 3 seconds
    private long lastTapTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViews();
        initializeHandlers();
        initializeServices();
        setupHistoryDrawer();
        
        // Clear any previous active history and start fresh
        historyManager.clearCurrentActiveHistory();
        clearCurrentConversation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reinitializeLLMIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveCurrentChat();
        releaseLLMResources();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanup();
    }

    private void initializeViews() {
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        initializeChat();
        setupButtons();
        setupInputHandling();
        setupTitleTapCounter();
        
        // Initialize model name as "Loading..."
        binding.modelNameText.setText("Loading...");
    }

    private void initializeHandlers() {
        mediaHandler = new ChatMediaHandler(this);
        uiHandler = new ChatUIStateHandler(binding);
        conversationManager = new ConversationManager();
        historyManager = new ChatHistoryManager(this);
    }

    private void initializeChat() {
        chatAdapter = new ChatMessageAdapter();
        chatAdapter.setSpeakerClickListener(this);
        UiUtils.setupChatRecyclerView(binding.recyclerView, chatAdapter);
        
        // Set initial watermark visibility
        updateWatermarkVisibility();
    }

    private void updateWatermarkVisibility() {
        if (binding.watermarkContainer != null) {
            binding.watermarkContainer.setVisibility(
                chatAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE
            );
        }
    }

    private void setupButtons() {
        setupNavigationButton();
        setupAttachmentButton();
        setupVoiceButton();
        setupSendButton();
        setupNewConversationButton();
    }

    private void setupNavigationButton() {
        binding.historyButton.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });
    }

    private void setupAttachmentButton() {
        View.OnClickListener attachClickListener = v -> showAttachmentOptions();
        binding.attachButton.setOnClickListener(attachClickListener);
        binding.attachButtonExpanded.setOnClickListener(attachClickListener);
    }

    private void setupVoiceButton() {
        View.OnClickListener voiceClickListener = v -> toggleRecording();
        binding.voiceButton.setOnClickListener(voiceClickListener);
        binding.voiceButtonExpanded.setOnClickListener(voiceClickListener);

        View.OnLongClickListener voiceLongClickListener = v -> {
            showAudioList();
            return true;
        };
        binding.voiceButton.setOnLongClickListener(voiceLongClickListener);
        binding.voiceButtonExpanded.setOnLongClickListener(voiceLongClickListener);

        setupRecordingControls();
    }

    private void setupRecordingControls() {
        binding.recordingInput.cancelRecordingButton.setOnClickListener(v -> {
            stopRecording(false);
        });

        binding.recordingInput.finishRecordingButton.setOnClickListener(v -> {
            stopRecording(true);
        });
    }

    private void setupSendButton() {
        View.OnClickListener sendClickListener = v -> handleSendAction();
        binding.sendButton.setOnClickListener(sendClickListener);
        binding.sendButtonExpanded.setOnClickListener(sendClickListener);

        // Set initial send icon
        binding.sendButton.setBackgroundResource(R.drawable.bg_send_button);
        binding.sendButtonExpanded.setBackgroundResource(R.drawable.bg_send_button);
        binding.sendButton.setImageResource(R.drawable.ic_audio_wave);
        binding.sendButtonExpanded.setImageResource(R.drawable.ic_audio_wave);
    }

    private void setupNewConversationButton() {
        binding.newConversationButton.setOnClickListener(v -> {
            // Save current chat if needed
            saveCurrentChat();
            // Clear current conversation
            clearCurrentConversation();
            // Clear active history
            historyManager.clearCurrentActiveHistory();
            // Refresh history list to show the newly saved chat
            refreshHistoryList();
        });
    }

    private void setupInputHandling() {
        TextWatcher textWatcher = UiUtils.createTextWatcher(() -> uiHandler.updateSendButtonState());
        binding.messageInput.addTextChangedListener(textWatcher);
        binding.messageInputExpanded.addTextChangedListener(textWatcher);
    }

    private void initializeServices() {
        bindService(new Intent(this, LLMEngineService.class), llmConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, VLMEngineService.class), vlmConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, ASREngineService.class), asrConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, TTSEngineService.class), ttsConnection, Context.BIND_AUTO_CREATE);
    }

    private void handleSendAction() {
        String message = uiHandler.getCurrentInputText();
        Uri pendingImage = uiHandler.getPendingImageUri();

        if (!message.isEmpty() || pendingImage != null) {
            if (pendingImage != null) {
                handleImageMessage(pendingImage, message);
            } else {
                handleTextMessage(message);
            }
            uiHandler.clearInput();
        } else {
            startAudioChat();
        }
    }

    private void handleTextMessage(String message) {
        if (message.trim().isEmpty()) return;
        
        // Hide keyboard
        hideKeyboard();
        
        // Add user message to conversation
        ChatMessage userMessage = new ChatMessage(message, true);
        userMessage.setPromptId(promptId);
        conversationManager.addMessage(userMessage);
        chatAdapter.addMessage(userMessage);
        
        // Hide watermark when conversation starts
        updateWatermarkVisibility();
        
        // Create empty AI message with loading indicator
        ChatMessage aiMessage = new ChatMessage("Thinking...", false);
        aiMessage.setPromptId(promptId);
        conversationManager.addMessage(aiMessage);
        chatAdapter.addMessage(aiMessage);
        
        // Save chat after adding both messages
        saveCurrentChat();
        
        // Change send buttons to stop icons
        setSendButtonsAsStop(true);
        
        // Generate AI response with formatted prompt
        if (llmService != null) {
            String formattedPrompt = getFormattedPrompt(message);
            
            llmService.generateStreamingResponse(formattedPrompt, new LLMEngineService.StreamingResponseCallback() {
                private final StringBuilder currentResponse = new StringBuilder();
                private boolean hasReceivedResponse = false;

                @Override
                public void onToken(String token) {
                    if (token == null || token.isEmpty()) {
                        return;
                    }

                    hasReceivedResponse = true;
                    runOnUiThread(() -> {
                        currentResponse.append(token);
                        aiMessage.updateText(currentResponse.toString());
                        chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                        UiUtils.scrollToLatestMessage(binding.recyclerView, chatAdapter.getItemCount(), false);
                    });
                }
            }).thenAccept(finalResponse -> {
                if (finalResponse != null && !finalResponse.equals(LLMEngineService.DEFAULT_ERROR_RESPONSE)) {
                    runOnUiThread(() -> {
                        String response = finalResponse.trim();
                        if (response.isEmpty()) {
                            aiMessage.updateText("I apologize, but I couldn't generate a proper response. Please try rephrasing your question.");
                        } else {
                            aiMessage.updateText(finalResponse);
                        }
                        chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                        UiUtils.scrollToLatestMessage(binding.recyclerView, chatAdapter.getItemCount(), true);
                        setSendButtonsAsStop(false);
                        
                        // Increment promptId after successful response
                        promptId++;
                        
                        // Only save and refresh after AI response is complete
                        saveCurrentChat();
                        refreshHistoryList();
                    });
                } else {
                    runOnUiThread(() -> {
                        aiMessage.updateText("I apologize, but I encountered an issue generating a response. Please try again.");
                        chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                        setSendButtonsAsStop(false);
                    });
                }
            }).exceptionally(throwable -> {
                Log.e(TAG, "Error generating response", throwable);
                runOnUiThread(() -> {
                    aiMessage.updateText("Error: Unable to generate response. Please try again later.");
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    Toast.makeText(this, "Error generating response", Toast.LENGTH_SHORT).show();
                    setSendButtonsAsStop(false);
                });
                return null;
            });
        }
    }

    private void setSendButtonsAsStop(boolean isStop) {
        runOnUiThread(() -> {
            // Update UI state
            uiHandler.setGeneratingState(isStop);
            
            // Update click listeners
            View.OnClickListener listener = isStop ? 
                v -> {
                    if (llmService != null) {
                        llmService.stopGeneration();
                        setSendButtonsAsStop(false);
                    }
                } :
                v -> handleSendAction();

            binding.sendButton.setOnClickListener(listener);
            binding.sendButtonExpanded.setOnClickListener(listener);
        });
    }

    private void handleImageMessage(Uri imageUri, String message) {
        ChatMessage userMessage = new ChatMessage(message, true);
        userMessage.setImageUri(imageUri);
        conversationManager.addMessage(userMessage);
        chatAdapter.addMessage(userMessage);
        
        if (vlmService != null) {
            vlmService.analyzeImage(imageUri, message)
                .thenAccept(response -> {
                    runOnUiThread(() -> {
                        ChatMessage aiMessage = new ChatMessage(response, false);
                        conversationManager.addMessage(aiMessage);
                        chatAdapter.addMessage(aiMessage);
                        UiUtils.scrollToLatestMessage(binding.recyclerView, chatAdapter.getItemCount(), true);
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error analyzing image", throwable);
                    runOnUiThread(() -> Toast.makeText(this, "Error analyzing image", Toast.LENGTH_SHORT).show());
                    return null;
                });
        }
    }

    private void toggleRecording() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
            return;
        }

        if (mediaHandler.isRecording()) {
            stopRecording(true);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        if (asrService == null) {
            Toast.makeText(this, "ASR service not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        // Start ASR service first
        asrService.startListening(result -> {
            runOnUiThread(() -> {
                if (result.startsWith("Partial: ")) {
                    String partialText = result.substring(9);
                    binding.messageInput.setText(partialText);
                } else if (!result.startsWith("Error: ") && !result.equals("Ready for speech...")) {
                    binding.messageInput.setText(result);
                    binding.messageInputExpanded.setText(result);
                    uiHandler.updateSendButtonState();
                    stopRecording(false);
                } else if (result.startsWith("Error: ")) {
                    Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
                    stopRecording(false);
                }
            });
        });

        // Then start audio recording
        mediaHandler.startRecording();
        uiHandler.updateRecordingState(true);
    }

    private void stopRecording(boolean shouldSave) {
        if (asrService != null) {
            asrService.stopListening();
        }
        mediaHandler.stopRecording(shouldSave);
        uiHandler.updateRecordingState(false);
    }

    private void showAttachmentOptions() {
        PopupMenu popup = new PopupMenu(this, 
            binding.expandedInput.getVisibility() == View.VISIBLE ? 
            binding.attachButtonExpanded : binding.attachButton);

        popup.getMenu().add(0, 1, 0, "Attach Photos");
        popup.getMenu().add(0, 2, 0, "Take Photo");
        popup.getMenu().add(0, 3, 0, "Attach Files");

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    startActivityForResult(mediaHandler.createImageSelectionIntent(), PICK_IMAGE_REQUEST);
                    break;
                case 2:
                    handleCameraCapture();
                    break;
                case 3:
                    startActivityForResult(mediaHandler.createFileSelectionIntent(), PICK_FILE_REQUEST);
                    break;
            }
            return true;
        });

        popup.show();
    }

    private void handleCameraCapture() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            Intent intent = mediaHandler.createCameraCaptureIntent();
            if (intent != null) {
                startActivityForResult(intent, CAPTURE_IMAGE_REQUEST);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error creating camera intent", e);
            Toast.makeText(this, "Error launching camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAudioList() {
        File[] files = FileUtils.getAudioRecordings(this);
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No recordings found", Toast.LENGTH_SHORT).show();
            return;
        }

        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        List<File> audioFiles = new ArrayList<>(Arrays.asList(files));

        UiUtils.showAudioListDialog(this, audioFiles, new AudioListAdapter.OnAudioActionListener() {
            @Override
            public void onReplayClick(File file) {
                // TODO: Implement audio playback
            }

            @Override
            public void onDeleteClick(File file) {
                if (file.delete()) {
                    Toast.makeText(ChatActivity.this, "Recording deleted", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void navigateToHome() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void startAudioChat() {
        Intent intent = new Intent(this, AudioChatActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        switch (requestCode) {
            case PICK_IMAGE_REQUEST:
                if (data != null && data.getData() != null) {
                    uiHandler.setImagePreview(data.getData());
                }
                break;
            case CAPTURE_IMAGE_REQUEST:
                if (mediaHandler.getCurrentPhotoPath() != null) {
                    uiHandler.setImagePreview(Uri.fromFile(new File(mediaHandler.getCurrentPhotoPath())));
                }
                break;
            case PICK_FILE_REQUEST:
                if (data != null && data.getData() != null) {
                    ChatMessage fileMessage = mediaHandler.handleSelectedFile(data.getData());
                    if (fileMessage != null) {
                        chatAdapter.addMessage(fileMessage);
                    }
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (permissions[0].equals(android.Manifest.permission.CAMERA)) {
                    handleCameraCapture();
                } else if (permissions[0].equals(android.Manifest.permission.RECORD_AUDIO)) {
                    startRecording();
                }
            } else {
                String message = permissions[0].equals(android.Manifest.permission.CAMERA) ?
                    "Camera permission is required for taking photos" :
                    "Microphone permission is required for voice input";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onSpeakerClick(String messageText) {
        if (ttsService != null && ttsService.isReady()) {
            ttsService.speak(messageText)
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error converting text to speech", throwable);
                    runOnUiThread(() -> Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show());
                    return null;
                });
        } else {
            Toast.makeText(this, "TTS service not ready", Toast.LENGTH_SHORT).show();
        }
    }

    private void cleanup() {
        unbindAllServices();
        mediaHandler.release();
        binding = null;
        conversationManager = null;
        historyManager = null;
        historyAdapter = null;
        drawerLayout = null;
    }

    private void unbindAllServices() {
        if (llmService != null) {
            llmService.releaseResources();
            unbindService(llmConnection);
        }
        if (vlmService != null) unbindService(vlmConnection);
        if (asrService != null) unbindService(asrConnection);
        if (ttsService != null) unbindService(ttsConnection);
    }

    private void releaseLLMResources() {
        if (llmService != null) {
            llmService.releaseResources();
        }
    }

    private void reinitializeLLMIfNeeded() {
        if (llmService != null && !llmService.isReady()) {
            llmService.initialize()
                .thenAccept(success -> {
                    if (!success) {
                        Log.e(TAG, "Failed to reinitialize LLM");
                        runOnUiThread(() -> Toast.makeText(this, "Failed to reinitialize LLM", Toast.LENGTH_SHORT).show());
                    }
                });
        }
    }

    // Service Connections
    private final ServiceConnection llmConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            llmService = ((LLMEngineService.LocalBinder) service).getService();
            // Update model name when service is connected
            if (llmService != null) {
                runOnUiThread(() -> {
                    String modelName = llmService.getModelName();
                    if (modelName != null && !modelName.isEmpty()) {
                        binding.modelNameText.setText(modelName);
                    } else {
                        binding.modelNameText.setText("Unknown");
                    }
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            llmService = null;
            // Update UI when service is disconnected
            runOnUiThread(() -> binding.modelNameText.setText("Disconnected"));
        }
    };

    private final ServiceConnection vlmConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            vlmService = ((VLMEngineService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            vlmService = null;
        }
    };

    private final ServiceConnection asrConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            asrService = ((ASREngineService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            asrService = null;
        }
    };

    private final ServiceConnection ttsConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ttsService = ((TTSEngineService.LocalBinder) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ttsService = null;
        }
    };

    // Add this new method to handle keyboard hiding
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void setupHistoryDrawer() {
        drawerLayout = binding.drawerLayout;
        historyManager = new ChatHistoryManager(this);
        historyAdapter = new ChatHistoryAdapter();
        
        // Configure drawer to slide the main content
        drawerLayout.setScrimColor(Color.TRANSPARENT);
        drawerLayout.setDrawerElevation(0f);
        
        // Enable sliding content behavior
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        
        // Set drawer listener for animation and dimming effect
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            private final View mainContent = binding.getRoot().findViewById(R.id.mainContent);
            private final View contentOverlay = binding.getRoot().findViewById(R.id.contentOverlay);

            @Override
            public void onDrawerSlide(View drawerView, float slideOffset) {
                // Move the main content with the drawer
                mainContent.setTranslationX(drawerView.getWidth() * slideOffset);
                
                // Show and update overlay opacity
                if (slideOffset > 0) {
                    contentOverlay.setVisibility(View.VISIBLE);
                    contentOverlay.setAlpha(0.6f * slideOffset);
                } else {
                    contentOverlay.setVisibility(View.GONE);
                }
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                contentOverlay.setVisibility(View.GONE);
                contentOverlay.setAlpha(0f);
                
                // Exit selection mode when drawer is closed
                if (historyAdapter.isSelectionMode() && historyAdapter.getSelectedHistories().isEmpty()) {
                    exitSelectionMode();
                }
            }

            @Override
            public void onDrawerStateChanged(int newState) {
                // Handle back press in selection mode
                if (newState == DrawerLayout.STATE_DRAGGING && historyAdapter.isSelectionMode()) {
                    if (historyAdapter.getSelectedHistories().isEmpty()) {
                        exitSelectionMode();
                    }
                }
            }
        });
        
        RecyclerView historyRecyclerView = findViewById(R.id.historyRecyclerView);
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        historyRecyclerView.setAdapter(historyAdapter);

        ImageButton deleteButton = findViewById(R.id.deleteHistoryButton);
        CheckBox selectAllCheckbox = findViewById(R.id.selectAllCheckbox);

        deleteButton.setOnClickListener(v -> {
            if (historyAdapter.isSelectionMode()) {
                // If in selection mode and has selections, show delete confirmation
                Set<String> selectedIds = historyAdapter.getSelectedHistories();
                if (!selectedIds.isEmpty()) {
                    showDeleteConfirmation();
                } else {
                    // Exit selection mode if no histories are selected
                    exitSelectionMode();
                }
            } else {
                // Enter selection mode
                historyAdapter.setSelectionMode(true);
                selectAllCheckbox.setVisibility(View.VISIBLE);
                deleteButton.setImageResource(R.drawable.ic_delete);
            }
        });

        // Update delete button appearance when selection changes
        historyAdapter.setOnSelectionChangeListener(selectedCount -> {
            if (selectedCount > 0) {
                deleteButton.setColorFilter(getResources().getColor(R.color.error, getTheme()));
            } else {
                deleteButton.clearColorFilter();
            }
        });

        selectAllCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            historyAdapter.selectAll(isChecked);
        });

        // Load and display chat histories
        refreshHistoryList();

        // Set click listener for history items
        historyAdapter.setOnHistoryClickListener(history -> {
            // First save the current conversation if it exists
            saveCurrentChat();
            
            // Clear the current conversation display
            clearCurrentConversation();
            
            // Load the selected chat history
            for (ChatMessage message : history.getMessages()) {
                conversationManager.addMessage(message);
                chatAdapter.addMessage(message);
            }
            
            // Set this as the current active history
            historyManager.setCurrentActiveHistory(history);
            drawerLayout.closeDrawers();
            updateWatermarkVisibility();
        });
    }

    private void showDeleteConfirmation() {
        Set<String> selectedIds = historyAdapter.getSelectedHistories();
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "No histories selected", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Delete Selected Histories")
            .setMessage("Are you sure you want to delete " + selectedIds.size() + " selected histories?")
            .setPositiveButton("Delete", (dialog, which) -> {
                // Delete selected histories
                for (String id : selectedIds) {
                    historyManager.deleteHistory(id);
                    // If the deleted history was the current active one, clear the conversation
                    ChatHistory currentHistory = historyManager.getCurrentActiveHistory();
                    if (currentHistory != null && currentHistory.getId().equals(id)) {
                        historyManager.clearCurrentActiveHistory();
                        clearCurrentConversation();
                    }
                }
                
                // Exit selection mode and refresh the list
                exitSelectionMode();
                refreshHistoryList();
                Toast.makeText(this, selectedIds.size() > 1 ? 
                    "Selected histories deleted" : "History deleted", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void exitSelectionMode() {
        historyAdapter.setSelectionMode(false);
        findViewById(R.id.selectAllCheckbox).setVisibility(View.GONE);
        ImageButton deleteButton = findViewById(R.id.deleteHistoryButton);
        deleteButton.setImageResource(R.drawable.ic_delete);
        deleteButton.clearColorFilter();  // Remove the tint
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START) && historyAdapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        super.onBackPressed();
    }

    private void refreshHistoryList() {
        List<ChatHistory> histories = historyManager.loadAllHistories();
        historyAdapter.setHistories(histories);
    }

    private void saveCurrentChat() {
        List<ChatMessage> messages = conversationManager.getMessages();
        if (!messages.isEmpty()) {
            // Use first message as title, or a default title if it's empty
            String title = messages.get(0).getText();
            if (title.isEmpty() || title.length() > 50) {
                title = "Chat " + new SimpleDateFormat("yyyy-MM-dd HH:mm", 
                    Locale.getDefault()).format(new Date());
            }
            
            // Create or update history
            historyManager.createNewHistory(title, messages);
        }
    }

    private void clearCurrentConversation() {
        // Clear the conversation manager
        conversationManager.clearMessages();
        // Clear the chat adapter
        chatAdapter.clearMessages();
        // Update watermark visibility
        updateWatermarkVisibility();
    }

    private String getConversationHistory() {
        String conversationHistory = "";
        List<ChatMessage> conversations = new ArrayList<>();
        
        // Get recent messages based on lookback window
        List<ChatMessage> allMessages = conversationManager.getMessages();
        int startIndex = Math.max(0, allMessages.size() - (CONVERSATION_HISTORY_MESSAGE_LOOKBACK * 2));
        for (int i = startIndex; i < allMessages.size(); i++) {
            conversations.add(allMessages.get(i));
        }
        
        if (conversations.isEmpty()) {
            return conversationHistory;
        }

        int prevPromptId = conversations.get(0).getPromptId();
        String conversationFormat = PromptFormat.getConversationFormat(ModelType.LLAMA_3_2);
        String format = conversationFormat;
        
        for (int i = 0; i < conversations.size(); i++) {
            ChatMessage conversation = conversations.get(i);
            int currentPromptId = conversation.getPromptId();
            
            if (currentPromptId != prevPromptId) {
                conversationHistory = conversationHistory + format;
                format = conversationFormat;
                prevPromptId = currentPromptId;
            }
            
            if (conversation.isUser()) {
                format = format.replace(PromptFormat.USER_PLACEHOLDER, conversation.getText());
            } else {
                format = format.replace(PromptFormat.ASSISTANT_PLACEHOLDER, conversation.getText());
            }
        }
        conversationHistory = conversationHistory + format;
        
        return conversationHistory;
    }

    private String getFormattedPrompt(String userMessage) {
        String conversationHistory = getConversationHistory();
        
        if (conversationHistory.isEmpty()) {
            // If no history, just format with system prompt and user message
            String systemPrompt = PromptFormat.getSystemPromptTemplate(ModelType.LLAMA_3_2)
                .replace(PromptFormat.SYSTEM_PLACEHOLDER, PromptFormat.DEFAULT_SYSTEM_PROMPT);
                
            String userPrompt = PromptFormat.getUserPromptTemplate(ModelType.LLAMA_3_2)
                .replace(PromptFormat.USER_PLACEHOLDER, userMessage);
                
            return systemPrompt + userPrompt;
        }
        
        // If there is history, include it in the format
        String systemPrompt = PromptFormat.getSystemPromptTemplate(ModelType.LLAMA_3_2)
            .replace(PromptFormat.SYSTEM_PLACEHOLDER, PromptFormat.DEFAULT_SYSTEM_PROMPT);
            
        String userPrompt = PromptFormat.getUserPromptTemplate(ModelType.LLAMA_3_2)
            .replace(PromptFormat.USER_PLACEHOLDER, userMessage);
            
        return systemPrompt + conversationHistory + userPrompt;
    }

    private void setupTitleTapCounter() {
        binding.modelNameText.setOnClickListener(v -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTapTime > TAP_TIMEOUT_MS) {
                titleTapCount = 0;
            }
            lastTapTime = currentTime;
            
            titleTapCount++;
            if (titleTapCount == TAPS_TO_SHOW_MAIN) {
                titleTapCount = 0;
                // Launch MainActivity
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
            } else if (titleTapCount >= TAPS_TO_SHOW_MAIN - 2) {
                // Show feedback when close to activation
                int remaining = TAPS_TO_SHOW_MAIN - titleTapCount;
                Toast.makeText(this, remaining + " more tap" + (remaining == 1 ? "" : "s") + "...", 
                    Toast.LENGTH_SHORT).show();
            }
        });
    }
}