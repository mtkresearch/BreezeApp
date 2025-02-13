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
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.CheckBox;
import android.app.AlertDialog;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
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
import com.mtkresearch.gai_android.utils.IntroDialog;
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
import com.mtkresearch.gai_android.utils.PromptManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import android.os.Handler;
import android.os.Looper;

import com.mtkresearch.gai_android.utils.ModelUtils;

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

    // Add these fields at the top of the class with other fields
    private boolean llmServiceReady = false;
    private boolean vlmServiceReady = false;
    private boolean asrServiceReady = false;
    private boolean ttsServiceReady = false;
    private View inputBlockerOverlay;

    // Add a flag to track MTK support status
    private static boolean mtkBackendChecked = false;
    private static boolean mtkBackendSupported = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViews();
        initializeHandlers();
        
        // Show intro dialog immediately
        showIntroDialog();
        
        // Check for RECORD_AUDIO permission before initializing services
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        } else {
            initializeServices();
        }
        
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
        // Ensure services are unbound and cleaned up
        try {
            cleanup();
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    private void initializeViews() {
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Add input blocker overlay to the root view to cover everything
        inputBlockerOverlay = new View(this);
        inputBlockerOverlay.setBackgroundColor(getResources().getColor(R.color.background, getTheme()));
        inputBlockerOverlay.setAlpha(0.5f);  // Make it slightly more transparent
        
        // Add the overlay to the root view to cover all components
        ViewGroup rootView = binding.getRoot();
        ConstraintLayout.LayoutParams overlayParams = new ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.MATCH_PARENT
        );
        overlayParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        overlayParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        overlayParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        overlayParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        rootView.addView(inputBlockerOverlay, overlayParams);
        
        initializeChat();
        setupButtons();
        setupInputHandling();
        setupTitleTapCounter();
        
        // Initially disable all interactive components
        updateInteractionState();
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
        // Add copy message listener
        chatAdapter.setOnMessageLongClickListener((message, position) -> {
            showMessageOptions(message);
            return true;
        });
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
        // Show loading indicator with Toast
        Toast.makeText(this, "Initializing services...", Toast.LENGTH_SHORT).show();
        
        // Create handler for background operations
        Handler initHandler = new Handler(Looper.getMainLooper());
        ExecutorService initExecutor = Executors.newFixedThreadPool(4); // Use multiple threads
        
        // Start services in background with proper sequencing
        CompletableFuture.runAsync(() -> {
            try {
                // Start LLM service first with retry mechanism
                CountDownLatch llmLatch = new CountDownLatch(1);
                AtomicBoolean llmSuccess = new AtomicBoolean(false);
                
                // Prepare LLM intent with automatically selected backend
                Intent llmIntent = new Intent(this, LLMEngineService.class);
                llmIntent.putExtra("model_path", "/data/local/tmp/llama/breeze-tiny-instruct_0203.pte");
                String preferredBackend = ModelUtils.getPreferredBackend();
                llmIntent.putExtra("preferred_backend", preferredBackend);
                
                // Show toast with selected backend
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, 
                    "Initializing model with " + preferredBackend.toUpperCase() + " backend...", 
                    Toast.LENGTH_SHORT).show());
                
                // Bind LLM service on main thread
                initHandler.post(() -> {
                    try {
                        startService(llmIntent);
                        if (bindService(llmIntent, llmConnection, Context.BIND_AUTO_CREATE)) {
                            llmSuccess.set(true);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error binding LLM service", e);
                    } finally {
                        llmLatch.countDown();
                    }
                });
                
                // Wait for LLM binding with timeout
                if (!llmLatch.await(10, TimeUnit.SECONDS)) {
                    Log.e(TAG, "LLM service binding timed out");
                    throw new TimeoutException("LLM service binding timed out");
                }
                
                if (!llmSuccess.get()) {
                    Log.e(TAG, "LLM service binding failed");
                    throw new Exception("LLM service binding failed");
                }
                
                // Add delay to allow LLM service to start
                Thread.sleep(1000);
                
                // Start other services in parallel
                CompletableFuture<Void> vlmFuture = null;
                CompletableFuture<Void> asrFuture = null;
                CompletableFuture<Void> ttsFuture = null;
                
                // Start VLM service if needed
                if (BuildConfig.FLAVOR.equals("vlm")) {
                    vlmFuture = CompletableFuture.runAsync(() -> {
                        try {
                            CountDownLatch latch = new CountDownLatch(1);
                            initHandler.post(() -> {
                                try {
                                    bindService(new Intent(this, VLMEngineService.class),
                                        vlmConnection, Context.BIND_AUTO_CREATE);
                                } finally {
                                    latch.countDown();
                                }
                            });
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            Log.e(TAG, "Error initializing VLM service", e);
                        }
                    }, initExecutor);
                }
                
                // Start ASR and TTS services if we have audio permission
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) 
                    == PackageManager.PERMISSION_GRANTED) {
                    
                    // Start ASR service
                    asrFuture = CompletableFuture.runAsync(() -> {
                        try {
                            CountDownLatch latch = new CountDownLatch(1);
                            initHandler.post(() -> {
                                try {
                                    Intent asrIntent = new Intent(this, ASREngineService.class);
                                    startService(asrIntent);
                                    bindService(asrIntent, asrConnection, Context.BIND_AUTO_CREATE);
                                } finally {
                                    latch.countDown();
                                }
                            });
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            Log.e(TAG, "Error initializing ASR service", e);
                        }
                    }, initExecutor);
                    
                    // Start TTS service
                    ttsFuture = CompletableFuture.runAsync(() -> {
                        try {
                            CountDownLatch latch = new CountDownLatch(1);
                            initHandler.post(() -> {
                                try {
                                    Intent ttsIntent = new Intent(this, TTSEngineService.class);
                                    startService(ttsIntent);
                                    bindService(ttsIntent, ttsConnection, Context.BIND_AUTO_CREATE);
                                } finally {
                                    latch.countDown();
                                }
                            });
                            latch.await(5, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            Log.e(TAG, "Error initializing TTS service", e);
                        }
                    }, initExecutor);
                }
                
                // Wait for all services to complete initialization
                if (vlmFuture != null) vlmFuture.join();
                if (asrFuture != null) asrFuture.join();
                if (ttsFuture != null) ttsFuture.join();
                
                Log.d(TAG, "All services initialized");
                
            } catch (Exception e) {
                Log.e(TAG, "Error during service initialization", e);
                initHandler.post(() -> {
                    Toast.makeText(ChatActivity.this, 
                        "Error initializing services: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            } finally {
                initExecutor.shutdown();
            }
        }, initExecutor);
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
        // Don't add the "Thinking..." message to conversation history yet
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
                private boolean isGenerating = true;

                @Override
                public void onToken(String token) {
                    if (!isGenerating || token == null || token.isEmpty()) {
                        return;
                    }

                    if (!hasReceivedResponse) {
                        // Add the AI message to conversation history only when we get the first real response
                        hasReceivedResponse = true;
                        conversationManager.addMessage(aiMessage);
                    }

                    runOnUiThread(() -> {
                        currentResponse.append(token);
                        aiMessage.updateText(currentResponse.toString());
                        chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                        UiUtils.scrollToLatestMessage(binding.recyclerView, chatAdapter.getItemCount(), false);
                    });
                }
            }).thenAccept(finalResponse -> {
                runOnUiThread(() -> {
                    if (finalResponse != null && !finalResponse.equals(LLMEngineService.DEFAULT_ERROR_RESPONSE)) {
                        String response = finalResponse.trim();
                        // Only update with error message if we haven't received any real response
                        if (response.isEmpty() && !aiMessage.hasContent()) {
                            aiMessage.updateText("I apologize, but I couldn't generate a proper response. Please try rephrasing your question.");
                        } else if (!response.isEmpty()) {
                            aiMessage.updateText(finalResponse);
                        }
                        // Increment promptId after successful response
                        promptId++;
                    } else {
                        // Only show error if we haven't received any content
                        if (!aiMessage.hasContent()) {
                            aiMessage.updateText("I apologize, but I encountered an issue generating a response. Please try again.");
                        }
                    }
                    
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    UiUtils.scrollToLatestMessage(binding.recyclerView, chatAdapter.getItemCount(), true);
                    setSendButtonsAsStop(false);
                    
                    // Only save and refresh after AI response is complete
                    saveCurrentChat();
                    refreshHistoryList();
                });
            }).exceptionally(throwable -> {
                Log.e(TAG, "Error generating response", throwable);
                runOnUiThread(() -> {
                    // Only show error if we haven't received any content
                    if (!aiMessage.hasContent()) {
                        aiMessage.updateText("Error: Unable to generate response. Please try again later.");
                    }
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    Toast.makeText(ChatActivity.this, "Error generating response", Toast.LENGTH_SHORT).show();
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
                    // Initialize services after permission is granted
                    initializeServices();
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
        if (ttsService == null) {
            Toast.makeText(this, "Text-to-speech service not available", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!ttsService.isReady()) {
            Toast.makeText(this, "Text-to-speech is still initializing...", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        Toast.makeText(this, "Converting text to speech...", Toast.LENGTH_SHORT).show();

        // Run TTS in background
        CompletableFuture.runAsync(() -> {
            try {
                ttsService.speak(messageText)
                    .thenRun(() -> {
                        // Success - no need for notification
                    })
                    .exceptionally(throwable -> {
                        Log.e(TAG, "Error converting text to speech", throwable);
                        runOnUiThread(() -> Toast.makeText(this, 
                            "Error playing audio", Toast.LENGTH_SHORT).show());
                        return null;
                    });
            } catch (Exception e) {
                Log.e(TAG, "Error initiating text to speech", e);
                runOnUiThread(() -> Toast.makeText(this, 
                    "Error starting audio playback", Toast.LENGTH_SHORT).show());
            }
        }).exceptionally(throwable -> {
            Log.e(TAG, "Error in TTS background task", throwable);
            return null;
        });
    }

    private void cleanup() {
        // Run cleanup in background to prevent ANR
        CompletableFuture.runAsync(() -> {
            try {
                // Save current chat before cleanup
                saveCurrentChat();
                
                // Unbind services with timeout
                ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();
                Future<?> cleanupFuture = cleanupExecutor.submit(() -> {
                    unbindAllServices();
                });
                
                try {
                    cleanupFuture.get(5, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    Log.w(TAG, "Service unbinding timed out", e);
                    cleanupFuture.cancel(true);
                }
                
                cleanupExecutor.shutdownNow();
                
                // Release other resources
                mediaHandler.release();
                binding = null;
                conversationManager = null;
                historyManager = null;
                historyAdapter = null;
                drawerLayout = null;
                
                // Force garbage collection
                System.gc();
                
            } catch (Exception e) {
                Log.e(TAG, "Error during cleanup", e);
            }
        });
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
            if (llmService != null) {
                Toast.makeText(ChatActivity.this, "Initializing model...", Toast.LENGTH_SHORT).show();
                
                llmService.initialize().thenAccept(success -> {
                    llmServiceReady = success;
                    if (success) {
                        runOnUiThread(() -> {
                            String modelName = llmService.getModelName();
                            String backend = llmService.getCurrentBackend();
                            if (modelName != null && !modelName.isEmpty()) {
                                String displayText = String.format("%s (%s)", modelName, backend);
                                binding.modelNameText.setText(displayText);
                                binding.modelNameText.setTextColor(getResources().getColor(R.color.text_primary, getTheme()));
                            } else {
                                binding.modelNameText.setText("Unknown model");
                            }
                        });
                    } else {
                        runOnUiThread(() -> {
                            binding.modelNameText.setText("Model error");
                            binding.modelNameText.setTextColor(getResources().getColor(R.color.error, getTheme()));
                            Toast.makeText(ChatActivity.this, "Failed to initialize model", Toast.LENGTH_SHORT).show();
                        });
                    }
                    updateInteractionState();
                }).exceptionally(throwable -> {
                    Log.e(TAG, "Error initializing model", throwable);
                    llmServiceReady = false;
                    runOnUiThread(() -> {
                        binding.modelNameText.setText("Model error");
                        binding.modelNameText.setTextColor(getResources().getColor(R.color.error, getTheme()));
                        Toast.makeText(ChatActivity.this, "Error initializing model", Toast.LENGTH_SHORT).show();
                        updateInteractionState();
                    });
                    return null;
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            llmService = null;
            llmServiceReady = false;
            runOnUiThread(() -> {
                binding.modelNameText.setText("Model disconnected");
                binding.modelNameText.setTextColor(getResources().getColor(R.color.error, getTheme()));
                Toast.makeText(ChatActivity.this, "Model service disconnected", Toast.LENGTH_SHORT).show();
                updateInteractionState();
            });
        }
    };

    private final ServiceConnection vlmConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            vlmService = ((VLMEngineService.LocalBinder) service).getService();
            vlmServiceReady = vlmService != null;
            updateInteractionState();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            vlmService = null;
            vlmServiceReady = false;
            updateInteractionState();
        }
    };

    private final ServiceConnection asrConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            asrService = ((ASREngineService.LocalBinder) service).getService();
            if (asrService != null) {
                asrService.initialize().thenAccept(success -> {
                    asrServiceReady = success;
                    updateInteractionState();
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            asrService = null;
            asrServiceReady = false;
            updateInteractionState();
        }
    };

    private final ServiceConnection ttsConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ttsService = ((TTSEngineService.LocalBinder) service).getService();
            if (ttsService != null) {
                runOnUiThread(() -> Toast.makeText(ChatActivity.this, 
                    "Initializing text-to-speech...", Toast.LENGTH_SHORT).show());
                
                CompletableFuture.runAsync(() -> {
                    try {
                        ttsService.initialize()
                            .thenAccept(success -> {
                                ttsServiceReady = success;
                                if (success) {
                                    runOnUiThread(() -> Toast.makeText(ChatActivity.this, 
                                        "Text-to-speech ready", Toast.LENGTH_SHORT).show());
                                } else {
                                    runOnUiThread(() -> Toast.makeText(ChatActivity.this, 
                                        "Failed to initialize text-to-speech", Toast.LENGTH_SHORT).show());
                                }
                                updateInteractionState();
                            })
                            .exceptionally(throwable -> {
                                Log.e(TAG, "Error initializing TTS", throwable);
                                ttsServiceReady = false;
                                runOnUiThread(() -> {
                                    Toast.makeText(ChatActivity.this, 
                                        "Error initializing text-to-speech", Toast.LENGTH_SHORT).show();
                                    updateInteractionState();
                                });
                                return null;
                            });
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting TTS initialization", e);
                        ttsServiceReady = false;
                        updateInteractionState();
                    }
                });
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ttsService = null;
            ttsServiceReady = false;
            updateInteractionState();
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

    private String getFormattedPrompt(String userMessage) {
        // Get messages excluding the current message
        List<ChatMessage> allMessages = conversationManager.getMessages();
        List<ChatMessage> historyMessages = new ArrayList<>();
        
        if (!allMessages.isEmpty()) {
            // Get messages up to but not including the last one (which would be the current query)
            int endIndex = allMessages.size() - 1;
            int startIndex = Math.max(0, endIndex - (CONVERSATION_HISTORY_MESSAGE_LOOKBACK * 2));
            for (int i = startIndex; i < endIndex; i++) {
                historyMessages.add(allMessages.get(i));
            }
        }
        
        // Use PromptManager to format the complete prompt
        return PromptManager.formatCompletePrompt(userMessage, historyMessages, ModelType.LLAMA_3_2);
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

    private void showMessageOptions(ChatMessage message) {
        PopupMenu popup = new PopupMenu(this, binding.recyclerView);
        popup.getMenu().add(0, 1, 0, "Copy text");
        if (message.hasImage()) {
            popup.getMenu().add(0, 2, 0, "Save image");
        }

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    // Copy text to clipboard
                    android.content.ClipboardManager clipboard = 
                        (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    android.content.ClipData clip = 
                        android.content.ClipData.newPlainText("Message", message.getText());
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                    return true;
                case 2:
                    // Save image
                    if (message.hasImage()) {
                        saveImage(message.getImageUri());
                    }
                    return true;
            }
            return false;
        });

        popup.show();
    }

    private void saveImage(Uri imageUri) {
        try {
            // Create a copy of the image in the Pictures directory
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String fileName = "GAI_" + timestamp + ".jpg";
            
            // Get the content resolver
            android.content.ContentResolver resolver = getContentResolver();
            
            // Create image collection for API 29 and above
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, 
                android.os.Environment.DIRECTORY_PICTURES);

            // Insert the image
            Uri destUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (destUri != null) {
                try (java.io.InputStream in = resolver.openInputStream(imageUri);
                     java.io.OutputStream out = resolver.openOutputStream(destUri)) {
                    if (in != null && out != null) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                        }
                        Toast.makeText(this, "Image saved to Pictures", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error saving image", e);
            Toast.makeText(this, "Error saving image", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateInteractionState() {
        boolean allServicesReady = llmServiceReady && 
            (vlmServiceReady || !BuildConfig.FLAVOR.equals("vlm")) && 
            (asrServiceReady || !hasAudioPermission()) && 
            (ttsServiceReady || !hasAudioPermission());

        runOnUiThread(() -> {
            float enabledAlpha = 1.0f;
            float disabledAlpha = 0.5f;
            
            // Update UI elements based on service state
            binding.inputContainer.setEnabled(allServicesReady);
            binding.inputContainer.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            
            binding.newConversationButton.setEnabled(allServicesReady);
            binding.newConversationButton.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            
            binding.historyButton.setEnabled(allServicesReady);
            binding.historyButton.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            
            binding.attachButton.setEnabled(allServicesReady);
            binding.attachButton.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            binding.attachButtonExpanded.setEnabled(allServicesReady);
            binding.attachButtonExpanded.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            
            binding.voiceButton.setEnabled(allServicesReady);
            binding.voiceButton.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            binding.voiceButtonExpanded.setEnabled(allServicesReady);
            binding.voiceButtonExpanded.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            
            binding.sendButton.setEnabled(allServicesReady);
            binding.sendButton.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            binding.sendButtonExpanded.setEnabled(allServicesReady);
            binding.sendButtonExpanded.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            
            binding.messageInput.setEnabled(allServicesReady);
            binding.messageInput.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            binding.messageInputExpanded.setEnabled(allServicesReady);
            binding.messageInputExpanded.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
            
            // Enable history recycler view and its items
            RecyclerView historyRecyclerView = findViewById(R.id.historyRecyclerView);
            if (historyRecyclerView != null) {
                historyRecyclerView.setEnabled(allServicesReady);
                historyRecyclerView.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
                // Make sure the adapter is clickable
                if (historyAdapter != null) {
                    historyAdapter.notifyDataSetChanged();
                }
            }
            
            // Show/hide loading overlay with animation
            if (inputBlockerOverlay != null) {
                if (allServicesReady) {
                    // Fade out and remove the overlay
                    inputBlockerOverlay.animate()
                        .alpha(0f)
                        .setDuration(500)
                        .withEndAction(() -> {
                            ViewGroup parent = (ViewGroup) inputBlockerOverlay.getParent();
                            if (parent != null) {
                                parent.removeView(inputBlockerOverlay);
                                inputBlockerOverlay = null;
                            }
                        })
                        .start();
                } else {
                    inputBlockerOverlay.setVisibility(View.VISIBLE);
                    inputBlockerOverlay.setAlpha(0.5f);
                }
            }

            // Update model name and status
            if (llmService != null) {
                String modelName = llmService.getModelName();
                String backend = llmService.getCurrentBackend();
                if (modelName != null && !modelName.isEmpty()) {
                    String displayText = String.format("%s (%s)", modelName, backend);
                    binding.modelNameText.setText(displayText);
                    binding.modelNameText.setTextColor(getResources().getColor(
                        allServicesReady ? R.color.text_primary : R.color.text_secondary, 
                        getTheme()
                    ));
                    binding.modelNameText.setAlpha(allServicesReady ? enabledAlpha : disabledAlpha);
                } else {
                    binding.modelNameText.setText("Unknown model");
                    binding.modelNameText.setTextColor(getResources().getColor(R.color.error, getTheme()));
                }
            } else {
                binding.modelNameText.setText("Model not available");
                binding.modelNameText.setTextColor(getResources().getColor(R.color.error, getTheme()));
            }
        });
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void showIntroDialog() {
        Log.d(TAG, "Showing intro dialog");
        try {
            // Ensure we're on the main thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                new Handler(Looper.getMainLooper()).post(this::showIntroDialog);
                return;
            }

            // Check if activity is finishing
            if (isFinishing()) {
                Log.w(TAG, "Activity is finishing, skipping dialog");
                return;
            }

            IntroDialog dialog = new IntroDialog(this);
            dialog.setOnDismissListener(dialogInterface -> {
                Log.d(TAG, "Intro dialog dismissed");
            });
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing intro dialog", e);
        }
    }
}