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

import androidx.appcompat.app.AppCompatActivity;

import com.mtkresearch.gai_android.utils.AudioListAdapter;
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

    // Adapters
    private ChatMessageAdapter chatAdapter;
    private AudioListAdapter audioListAdapter;

    // Services
    private LLMEngineService llmService;
    private VLMEngineService vlmService;
    private ASREngineService asrService;
    private TTSEngineService ttsService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeViews();
        initializeHandlers();
        initializeServices();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reinitializeLLMIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
    }

    private void initializeHandlers() {
        mediaHandler = new ChatMediaHandler(this);
        uiHandler = new ChatUIStateHandler(binding);
        conversationManager = new ConversationManager();
    }

    private void initializeChat() {
        chatAdapter = new ChatMessageAdapter();
        chatAdapter.setSpeakerClickListener(this);
        UiUtils.setupChatRecyclerView(binding.recyclerView, chatAdapter);
    }

    private void setupButtons() {
        setupNavigationButton();
        setupAttachmentButton();
        setupVoiceButton();
        setupSendButton();
    }

    private void setupNavigationButton() {
        binding.homeButton.setOnClickListener(v -> navigateToHome());
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
        
        // Add user message to conversation
        ChatMessage userMessage = new ChatMessage(message, true);
        conversationManager.addMessage(userMessage);
        chatAdapter.addMessage(userMessage);
        
        // Create empty AI message
        ChatMessage aiMessage = new ChatMessage("", false);
        conversationManager.addMessage(aiMessage);
        chatAdapter.addMessage(aiMessage);
        
        // Generate AI response
        if (llmService != null) {
            llmService.generateStreamingResponse(message, new LLMEngineService.StreamingResponseCallback() {
                private final StringBuilder currentResponse = new StringBuilder();

                @Override
                public void onToken(String token) {
                    if (token == null || token.isEmpty()) {
                        return;
                    }

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
                        aiMessage.updateText(finalResponse);
                        chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                        UiUtils.scrollToLatestMessage(binding.recyclerView, chatAdapter.getItemCount(), true);
                    });
                }
            }).exceptionally(throwable -> {
                Log.e(TAG, "Error generating response", throwable);
                runOnUiThread(() -> {
                    aiMessage.updateText("Error: " + throwable.getMessage());
                    chatAdapter.notifyItemChanged(chatAdapter.getItemCount() - 1);
                    Toast.makeText(this, "Error generating response", Toast.LENGTH_SHORT).show();
                });
                return null;
            });
        }
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
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            llmService = null;
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
}