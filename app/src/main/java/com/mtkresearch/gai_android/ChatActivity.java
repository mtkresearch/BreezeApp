package com.mtkresearch.gai_android;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.ContextThemeWrapper;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.mtkresearch.gai_android.utils.AudioListAdapter;
import com.mtkresearch.gai_android.utils.ChatMessageAdapter;
import com.mtkresearch.gai_android.databinding.ActivityChatBinding;
import com.mtkresearch.gai_android.utils.ChatMessage;

import java.io.File;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Handler;
import android.os.Looper;
import android.widget.ImageButton;

import androidx.recyclerview.widget.RecyclerView;

import com.mtkresearch.gai_android.utils.AudioRecorder;
import com.mtkresearch.gai_android.service.ASREngineService;
import com.mtkresearch.gai_android.service.LLMEngineService;
import com.mtkresearch.gai_android.service.TTSEngineService;
import com.mtkresearch.gai_android.service.VLMEngineService;

import java.io.IOException;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import android.util.Log;

/**
 * ChatActivity handles the main chat interface of the application.
 * It manages text input, voice recording, image/file attachments, and interactions with various services
 * (LLM, VLM, ASR, TTS) for processing user inputs and generating responses.
 */
public class ChatActivity extends AppCompatActivity implements ChatMessageAdapter.OnSpeakerClickListener {
    private static final String TAG = "ChatActivity";

    // Request codes for permissions and activities
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int CAPTURE_IMAGE_REQUEST = 2;
    private static final int PICK_FILE_REQUEST = 3;

    // UI Components
    private ActivityChatBinding binding;
    private ChatMessageAdapter adapter;
    private AudioListAdapter audioListAdapter;
    
    // Audio Recording Components
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private Runnable visualizationRunnable;
    private AudioRecorder audioRecorder;
    private File recordingFile;
    private boolean isRecording = false;
    private MediaPlayer currentMediaPlayer;
    
    // Camera/Image Related
    private String currentPhotoPath;
    private Uri pendingImageUri;

    // Engine Services
    private LLMEngineService llmService;
    private VLMEngineService vlmService;
    private ASREngineService asrService;
    private TTSEngineService ttsService;

    // Service Connection Declarations
    private final ServiceConnection llmConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LLMEngineService.LocalBinder binder = (LLMEngineService.LocalBinder) service;
            llmService = (LLMEngineService) binder.getService();
            // checkServicesReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            llmService = null;
        }
    };

    // Similar connections for other services...
    private final ServiceConnection vlmConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            VLMEngineService.LocalBinder binder = (VLMEngineService.LocalBinder) service;
            vlmService = (VLMEngineService) binder.getService();
            // checkServicesReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            vlmService = null;
        }
    };

    private final ServiceConnection asrConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ASREngineService.LocalBinder binder = (ASREngineService.LocalBinder) service;
            asrService = (ASREngineService) binder.getService();
            // checkServicesReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            asrService = null;
        }
    };

    private final ServiceConnection ttsConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TTSEngineService.LocalBinder binder = (TTSEngineService.LocalBinder) service;
            ttsService = (TTSEngineService) binder.getService();
            // checkServicesReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ttsService = null;
        }
    };

    //region Lifecycle Methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupButtons();
        audioRecorder = new AudioRecorder(this);
        setupRecyclerView();
        setupClickListeners();
        setupInputMode();
        setupAudioWaveButton();

        bindServices();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (llmService != null) {
            llmService.releaseResources();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reinitializeLLMIfNeeded();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupResources();
    }
    //endregion

    //region Service Management
    private void bindServices() {
        bindService(new Intent(this, LLMEngineService.class), llmConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, VLMEngineService.class), vlmConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, ASREngineService.class), asrConnection, Context.BIND_AUTO_CREATE);
        bindService(new Intent(this, TTSEngineService.class), ttsConnection, Context.BIND_AUTO_CREATE);
    }

    private void checkServicesReady() {
        if (llmService != null && vlmService != null && asrService != null && ttsService != null) {
            if (!llmService.isReady() || !vlmService.isReady() || !asrService.isReady() || !ttsService.isReady()) {
                Toast.makeText(this, "AI Services not ready", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            enableUI();
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

    private void cleanupResources() {
        // Release LLM resources
        if (llmService != null) {
            llmService.releaseResources();
            unbindService(llmConnection);
        }

        // Unbind other services
        if (vlmService != null) unbindService(vlmConnection);
        if (asrService != null) unbindService(asrConnection);
        if (ttsService != null) unbindService(ttsConnection);

        // Cleanup media resources
        if (currentMediaPlayer != null) {
            currentMediaPlayer.release();
            currentMediaPlayer = null;
        }
        audioRecorder.stopRecording();
        stopRecordingTimer();
        binding = null;
    }
    //endregion

    //region UI Setup and Management
    private void enableUI() {
        binding.messageInput.setEnabled(true);
        binding.messageInputExpanded.setEnabled(true);
        binding.sendButton.setEnabled(true);
        binding.sendButtonExpanded.setEnabled(true);
        binding.voiceButton.setEnabled(true);
        binding.voiceButtonExpanded.setEnabled(true);
        binding.attachButton.setEnabled(true);
        binding.attachButtonExpanded.setEnabled(true);
    }

    private void setupButtons() {
        binding.homeButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void setupRecyclerView() {
        adapter = new ChatMessageAdapter();
        adapter.setSpeakerClickListener(this);
        binding.recyclerView.setAdapter(adapter);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        binding.recyclerView.setLayoutManager(layoutManager);

        // Add padding to allow scrolling content up
        binding.recyclerView.setPadding(
                binding.recyclerView.getPaddingLeft(),
                binding.recyclerView.getHeight() / 2,
                binding.recyclerView.getPaddingRight(),
                binding.recyclerView.getPaddingBottom()
        );
        binding.recyclerView.setClipToPadding(false);
    }

    private void setupClickListeners() {
        setupInputContainerClickListener();
        setupAttachButtonListeners();
        setupVoiceButtonListeners();
        setupRecordingControlListeners();
    }

    private void setupInputContainerClickListener() {
        binding.inputContainer.setOnClickListener(v -> {
            // Consume the click event
        });
    }

    private void setupAttachButtonListeners() {
        View.OnClickListener attachClickListener = v -> showAttachmentOptions();
        binding.attachButton.setOnClickListener(attachClickListener);
        binding.attachButtonExpanded.setOnClickListener(attachClickListener);
    }

    private void setupVoiceButtonListeners() {
        View.OnClickListener voiceClickListener = v -> {
            if (isRecording) {
                stopRecording(true);
            } else {
                startRecording();
            }
        };
        binding.voiceButton.setOnClickListener(voiceClickListener);
        binding.voiceButtonExpanded.setOnClickListener(voiceClickListener);

        View.OnLongClickListener voiceLongClickListener = v -> {
            showAudioList();
            return true;
        };
        binding.voiceButton.setOnLongClickListener(voiceLongClickListener);
        binding.voiceButtonExpanded.setOnLongClickListener(voiceLongClickListener);
    }

    private void setupRecordingControlListeners() {
        binding.recordingInput.cancelRecordingButton.setOnClickListener(v -> {
            stopRecording(false);
            asrService.stopListening();
        });

        binding.recordingInput.finishRecordingButton.setOnClickListener(v -> {
            asrService.stopListening();
            stopRecording(true);
        });
    }

    private void setupInputMode() {
        setupTouchListeners();
        setupFocusListeners();
        setupTextWatchers();
    }

    private void setupTouchListeners() {
        View.OnTouchListener outsideTouchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (binding.expandedInput.getVisibility() == View.VISIBLE) {
                    String currentText = binding.messageInputExpanded.getText().toString();
                    if (currentText.isEmpty()) {
                        binding.messageInputExpanded.clearFocus();
                        collapseInputSection();
                        updateSendButton(false);
                    }
                }
            }
            return false;
        };

        binding.getRoot().setOnTouchListener(outsideTouchListener);
        binding.recyclerView.setOnTouchListener(outsideTouchListener);
        binding.inputContainer.setOnTouchListener((v, event) -> {
            v.performClick();
            return false;
        });
    }

    private void setupFocusListeners() {
        binding.messageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                expandInputSection();
            }
        });

        binding.messageInputExpanded.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String currentText = binding.messageInputExpanded.getText().toString();
                if (currentText.isEmpty()) {
                    collapseInputSection();
                    updateSendButton(false);
                }
            }
        });
    }

    private void setupTextWatchers() {
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        binding.messageInput.addTextChangedListener(textWatcher);
        binding.messageInputExpanded.addTextChangedListener(textWatcher);
    }

    private void expandInputSection() {
        binding.collapsedInput.setVisibility(View.GONE);
        binding.expandedInput.setVisibility(View.VISIBLE);
        binding.messageInputExpanded.requestFocus();
        binding.messageInputExpanded.setText(binding.messageInput.getText());

        binding.recyclerView.postDelayed(() ->
                binding.recyclerView.smoothScrollToPosition(adapter.getItemCount()), 100);
    }

    private void collapseInputSection() {
        binding.expandedInput.setVisibility(View.GONE);
        binding.collapsedInput.setVisibility(View.VISIBLE);
    }

    private void updateSendButton(boolean hasText) {
        int icon = hasText ? R.drawable.ic_send : R.drawable.ic_audio_wave;
        binding.sendButton.setImageResource(icon);
        binding.sendButtonExpanded.setImageResource(icon);
    }

    private void updateSendButtonState() {
        String message = binding.expandedInput.getVisibility() == View.VISIBLE ?
                binding.messageInputExpanded.getText().toString() :
                binding.messageInput.getText().toString();

        boolean shouldShowSend = !message.isEmpty() || pendingImageUri != null;
        updateSendButton(shouldShowSend);
    }

    private void showAttachmentOptions() {
        PopupMenu popup = new PopupMenu(new ContextThemeWrapper(this, R.style.PopupMenuStyle),
                binding.expandedInput.getVisibility() == View.VISIBLE ?
                        binding.attachButtonExpanded : binding.attachButton);

        popup.getMenu().add(0, 1, 0, "Attach Photos").setIcon(R.drawable.ic_gallery);
        popup.getMenu().add(0, 2, 0, "Take Photo").setIcon(R.drawable.ic_camera);
        popup.getMenu().add(0, 3, 0, "Attach Files").setIcon(R.drawable.ic_folder);

        // Force showing icons
        try {
            Method method = popup.getMenu().getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
            method.setAccessible(true);
            method.invoke(popup.getMenu(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    handleImageInput();
                    break;
                case 2:
                    handleCameraInput();
                    break;
                case 3:
                    handleFileInput();
                    break;
            }
            return true;
        });

        popup.show();
    }

    private void startRecording() {
        if (asrService == null) {
            Toast.makeText(this, "ASR service not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isRecording) {
            stopRecording(true);
            return;
        }

        // Start ASR streaming
        asrService.startListening(result -> {
            runOnUiThread(() -> {
                if (result.startsWith("Partial: ")) {
                    String partialText = result.substring(9);
                    binding.messageInput.setText(partialText);
                } else if (!result.startsWith("Error: ") && !result.equals("Ready for speech...")) {
                    binding.messageInput.setText(result);
                    binding.messageInputExpanded.setText(result);
                    updateSendButton(!result.isEmpty());
                    stopRecording(false);
                } else if (result.startsWith("Error: ")) {
                    Toast.makeText(ChatActivity.this, result, Toast.LENGTH_SHORT).show();
                    stopRecording(false);
                }
            });
        });

        updateRecordingUI(true);
    }

    private void stopRecording(boolean shouldSave) {
        if (asrService != null) {
            asrService.stopListening();
        }
        updateRecordingUI(false);
    }

    private void updateRecordingUI(boolean isRecording) {
        this.isRecording = isRecording;
        int iconResource = isRecording ? R.drawable.ic_pause : R.drawable.ic_mic;
        binding.voiceButton.setImageResource(iconResource);
        binding.voiceButtonExpanded.setImageResource(iconResource);
    }

    private void startRecordingTimer() {
        timerRunnable = new Runnable() {
            int seconds = 0;
            @Override
            public void run() {
                if (binding.recordingInput.getRoot().getVisibility() == View.VISIBLE) {
                    int minutes = seconds / 60;
                    int secs = seconds % 60;
                    binding.recordingInput.recordingTimer.setText(String.format("%d:%02d", minutes, secs));
                    seconds++;
                    handler.postDelayed(this, 1000);
                }
            }
        };
        handler.postDelayed(timerRunnable, 1000);
    }

    private void startAudioVisualization() {
        visualizationRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding.recordingInput.getRoot().getVisibility() == View.VISIBLE) {
                    int maxAmplitude = audioRecorder.getMaxAmplitude();
                    float amplitude = maxAmplitude > 0
                            ? (float) (1.0 / Math.log(32768) * Math.log(maxAmplitude))
                            : 0f;
                    binding.recordingInput.audioWaveView.updateAmplitude(amplitude);
                    handler.postDelayed(this, 100);
                }
            }
        };
        handler.postDelayed(visualizationRunnable, 100);
    }

    private void stopRecordingTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
        }
        if (visualizationRunnable != null) {
            handler.removeCallbacks(visualizationRunnable);
        }
    }
    //endregion

    //region Message Handling
    private void handleUserMessage(String message) {
        if (message.trim().isEmpty()) return;

        // Add user message to chat
        ChatMessage userMessage = new ChatMessage(message, true);
        adapter.addMessage(userMessage);
        scrollToLatestMessage(true);

        // Clear input
        binding.messageInput.setText("");
        binding.messageInputExpanded.setText("");

        // Add an empty AI message that will be updated with streaming tokens
        ChatMessage aiMessage = new ChatMessage("", false);
        adapter.addMessage(aiMessage);
        scrollToLatestMessage(true);

        // Generate response with streaming
        processLLMResponse(message, aiMessage);
    }

    private void processLLMResponse(String message, ChatMessage aiMessage) {
        llmService.generateStreamingResponse(message, new LLMEngineService.StreamingResponseCallback() {
            @Override
            public void onToken(String token) {
                runOnUiThread(() -> updateStreamingResponse(aiMessage, token));
            }
        }).thenAccept(fullResponse -> {
            runOnUiThread(() -> {
                aiMessage.updateText(fullResponse);
                adapter.notifyItemChanged(adapter.getItemCount() - 1);
                scrollToLatestMessage(true);
            });
        }).exceptionally(throwable -> {
            Log.e(TAG, "Error processing message", throwable);
            runOnUiThread(() -> {
                Toast.makeText(ChatActivity.this, "Error processing message", Toast.LENGTH_SHORT).show();
            });
            return null;
        });
    }

    private void updateStreamingResponse(ChatMessage aiMessage, String token) {
        aiMessage.appendText(token);
        adapter.notifyItemChanged(adapter.getItemCount() - 1);
        scrollToLatestMessage(false);
    }

    private void handleImageMessage(Uri imageUri, String message) {
        ChatMessage userMessage = new ChatMessage(message, true);
        userMessage.setImageUri(imageUri);
        adapter.addMessage(userMessage);
        scrollToLatestMessage(true);

        processImageWithVLM(imageUri, message);
    }

    private void processImageWithVLM(Uri imageUri, String message) {
        vlmService.analyzeImage(imageUri, message)
                .thenAccept(response -> {
                    runOnUiThread(() -> {
                        ChatMessage aiMessage = new ChatMessage(response, false);
                        adapter.addMessage(aiMessage);
                        scrollToLatestMessage(true);
                    });
                })
                .exceptionally(throwable -> {
                    Log.e(TAG, "Error analyzing image", throwable);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Error analyzing image", Toast.LENGTH_SHORT).show();
                    });
                    return null;
                });
    }

    private void handleImageInput() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    private void handleCameraInput() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
            return;
        }

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        // Create the File where the photo should go
        File photoFile = null;
        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            photoFile = File.createTempFile(imageFileName, ".jpg", storageDir);
            currentPhotoPath = photoFile.getAbsolutePath();
        } catch (IOException ex) {
            Log.e("ChatActivity", "Error creating image file", ex);
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
            return;
        }

        // Continue only if the File was successfully created
        if (photoFile != null) {
            try {
                Uri photoURI = FileProvider.getUriForFile(this,
                        BuildConfig.APPLICATION_ID + ".fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST);
            } catch (Exception e) {
                Log.e("ChatActivity", "Error launching camera", e);
                Toast.makeText(this, "Error launching camera", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleFileInput() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select File"), PICK_FILE_REQUEST);
    }

    private File createImageFile() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        try {
            File image = File.createTempFile(imageFileName, ".jpg", storageDir);
            currentPhotoPath = image.getAbsolutePath();
            return image;
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (permissions[0].equals(android.Manifest.permission.CAMERA)) {
                    Log.d("ChatActivity", "Camera permission granted, launching camera");
                    handleCameraInput();
                } else if (permissions[0].equals(android.Manifest.permission.RECORD_AUDIO)) {
                    startRecording();
                }
            } else {
                String message = permissions[0].equals(android.Manifest.permission.CAMERA) ?
                        "Camera permission is required for taking photos" :
                        "Microphone permission is required for voice input";
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                Log.d("ChatActivity", "Permission denied: " + permissions[0]);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("ChatActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case PICK_IMAGE_REQUEST:
                    if (data != null && data.getData() != null) {
                        handleSelectedImage(data.getData());
                    }
                    break;
                case CAPTURE_IMAGE_REQUEST:
                    if (currentPhotoPath != null) {
                        File photoFile = new File(currentPhotoPath);
                        pendingImageUri = Uri.fromFile(photoFile);
                        showImagePreview(pendingImageUri);
                        expandInputSection(); // Make sure expanded input is visible
                    }
                    break;
                case PICK_FILE_REQUEST:
                    if (data != null && data.getData() != null) {
                        handleSelectedFile(data.getData());
                    }
                    break;
            }
        }
    }

    @Override
    public void onSpeakerClick(String messageText) {
        if (ttsService != null && ttsService.isReady()) {
            ttsService.speak(messageText)
                    .exceptionally(throwable -> {
                        Log.e(TAG, "Error converting text to speech", throwable);
                        runOnUiThread(() ->
                                Toast.makeText(this, "Error playing audio", Toast.LENGTH_SHORT).show());
                        return null;
                    });
        } else {
            Toast.makeText(this, "TTS service not ready", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAudioList() {
        File recordingsDir = new File(getFilesDir(), "recordings");
        if (!recordingsDir.exists() || !recordingsDir.isDirectory()) {
            Toast.makeText(this, "No recordings found", Toast.LENGTH_SHORT).show();
            return;
        }

        File[] files = recordingsDir.listFiles((dir, name) -> name.endsWith(".m4a"));
        if (files == null || files.length == 0) {
            Toast.makeText(this, "No recordings found", Toast.LENGTH_SHORT).show();
            return;
        }

        Arrays.sort(files, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        List<File> audioFiles = new ArrayList<>(Arrays.asList(files));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_audio_list, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.audioListRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        audioListAdapter = new AudioListAdapter(audioFiles, new AudioListAdapter.OnAudioActionListener() {
            @Override
            public void onReplayClick(File file) {
                playAudioFile(file);
            }

            @Override
            public void onDeleteClick(File file) {
                if (file.delete()) {
                    Toast.makeText(ChatActivity.this, "Recording deleted", Toast.LENGTH_SHORT).show();
                }
            }
        });
        recyclerView.setAdapter(audioListAdapter);

        AlertDialog dialog = builder.setView(dialogView)
                .setTitle("Recorded Audio Files")
                .setNegativeButton("Close", null)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        dialog.show();
    }

    private void playAudioFile(File file) {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.release();
            currentMediaPlayer = null;
        }

        currentMediaPlayer = new MediaPlayer();
        try {
            currentMediaPlayer.setDataSource(file.getAbsolutePath());
            currentMediaPlayer.prepare();
            currentMediaPlayer.start();

            currentMediaPlayer.setOnCompletionListener(mp -> {
                mp.release();
                currentMediaPlayer = null;
                // Notify adapter to reset play icon
                if (audioListAdapter != null) {
                    audioListAdapter.onPlaybackCompleted();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to play audio", Toast.LENGTH_SHORT).show();
            if (audioListAdapter != null) {
                audioListAdapter.onPlaybackCompleted();
            }

            if (currentMediaPlayer != null) {
                currentMediaPlayer.release();
                currentMediaPlayer = null;
            }
        }
    }

    private void handleSelectedImage(Uri imageUri) {
        pendingImageUri = imageUri;
        showImagePreview(imageUri);
        expandInputSection();
    }

    private void handleSelectedFile(Uri fileUri) {
        try {
            String fileName = getFileName(fileUri);
            ChatMessage fileMessage = new ChatMessage("Attached file: " + fileName, true);
            adapter.addMessage(fileMessage);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to process file", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("Range")
    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private void showImagePreview(Uri imageUri) {
        View expandedInputView = binding.expandedInput;
        View imagePreviewContainer = expandedInputView.findViewById(R.id.imagePreviewContainer);
        ImageView imagePreview = expandedInputView.findViewById(R.id.imagePreview);
        ImageButton removeButton = expandedInputView.findViewById(R.id.removeImageButton);

        imagePreviewContainer.setVisibility(View.VISIBLE);
        imagePreview.setImageURI(imageUri);

        removeButton.setOnClickListener(v -> {
            imagePreviewContainer.setVisibility(View.GONE);
            pendingImageUri = null;
            updateSendButtonState();
        });

        updateSendButtonState();
    }

    private void scrollToLatestMessage(boolean smooth) {
        if (adapter.getItemCount() > 0) {
            int targetPosition = adapter.getItemCount() - 1;
            if (smooth) {
                binding.recyclerView.smoothScrollToPosition(targetPosition);
            } else {
                binding.recyclerView.scrollToPosition(targetPosition);
            }
        }
    }

    private void setupAudioWaveButton() {
        View.OnClickListener audioWaveClickListener = v -> {
            String message = binding.expandedInput.getVisibility() == View.VISIBLE ?
                    binding.messageInputExpanded.getText().toString() :
                    binding.messageInput.getText().toString();

            if (!message.isEmpty() || pendingImageUri != null) {
                if (pendingImageUri != null) {
                    handleImageMessage(pendingImageUri, message);
                } else {
                    handleUserMessage(message);
                }

                scrollToLatestMessage(true);
                clearInput();
            } else {
                Intent intent = new Intent(this, AudioChatActivity.class);
                startActivity(intent);
            }
        };

        binding.sendButton.setOnClickListener(audioWaveClickListener);
        binding.sendButtonExpanded.setOnClickListener(audioWaveClickListener);
    }

    private void clearInput() {
        binding.messageInput.setText("");
        binding.messageInputExpanded.setText("");
        if (pendingImageUri != null) {
            binding.expandedInput.findViewById(R.id.imagePreviewContainer)
                    .setVisibility(View.GONE);
            pendingImageUri = null;
        }
        collapseInputSection();
        updateSendButton(false);
    }
}