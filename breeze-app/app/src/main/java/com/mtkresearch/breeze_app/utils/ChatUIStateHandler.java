package com.mtkresearch.breeze_app.utils;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.os.Build;
import android.util.Log;

import com.mtkresearch.breeze_app.R;
import com.mtkresearch.breeze_app.databinding.ActivityChatBinding;

public class ChatUIStateHandler {
    private static final String TAG = "ChatUIStateHandler";
    private final ActivityChatBinding binding;
    private Uri pendingImageUri;
    private boolean isGenerating = false;
    private static final int MAX_LINES = 6;
    private static final long VIBRATION_DURATION = 20; // 20ms for subtle feedback
    
    // Store original click listeners
    private View.OnClickListener sendButtonListener;
    private View.OnClickListener voiceButtonListener;
    private View.OnClickListener attachButtonListener;

    public ChatUIStateHandler(ActivityChatBinding binding) {
        this.binding = binding;
        setupInputHandling();
    }

    // Add methods to set click listeners
    public void setSendButtonListener(View.OnClickListener listener) {
        this.sendButtonListener = listener;
        setupButtonVibration();
    }
    
    public void setVoiceButtonListener(View.OnClickListener listener) {
        this.voiceButtonListener = listener;
        setupButtonVibration();
    }
    
    public void setAttachButtonListener(View.OnClickListener listener) {
        this.attachButtonListener = listener;
        setupButtonVibration();
    }
    
    /**
     * Call this method after all button click listeners are set up in the activity
     */
    public void setupButtonVibration() {
        Log.d(TAG, "Setting up button vibration");
        
        try {
            // Add touch listeners to all buttons to provide vibration feedback
            // These listeners will be called before the click listeners
            
            // Send buttons
            if (binding.sendButton != null) {
                binding.sendButton.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && v.isEnabled()) {
                        vibrateButton();
                    }
                    // Return false to allow the click event to be processed
                    return false;
                });
            }
            
            if (binding.sendButtonExpanded != null) {
                binding.sendButtonExpanded.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && v.isEnabled()) {
                        vibrateButton();
                    }
                    return false;
                });
            }
            
            // Voice buttons
            if (binding.voiceButton != null) {
                binding.voiceButton.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && v.isEnabled()) {
                        vibrateButton();
                    }
                    return false;
                });
            }
            
            if (binding.voiceButtonExpanded != null) {
                binding.voiceButtonExpanded.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && v.isEnabled()) {
                        vibrateButton();
                    }
                    return false;
                });
            }
            
            // Attach buttons
            if (binding.attachButton != null) {
                binding.attachButton.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && v.isEnabled()) {
                        vibrateButton();
                    }
                    return false;
                });
            }
            
            if (binding.attachButtonExpanded != null) {
                binding.attachButtonExpanded.setOnTouchListener((v, event) -> {
                    if (event.getAction() == android.view.MotionEvent.ACTION_DOWN && v.isEnabled()) {
                        vibrateButton();
                    }
                    return false;
                });
            }
            
            Log.d(TAG, "Button vibration setup complete");
        } catch (Exception e) {
            Log.e(TAG, "Error setting up button vibration", e);
        }
    }
    
    private void vibrateButton() {
        try {
            Context context = binding.getRoot().getContext();
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            
            // Check if vibrator exists and is enabled
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // For Android 8.0 (API 26) and above
                    vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_DURATION, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    // For older versions
                    legacyVibrate(vibrator);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error vibrating button", e);
        }
    }
    
    @SuppressWarnings("deprecation")
    private void legacyVibrate(Vibrator vibrator) {
        // This method uses deprecated API for older Android versions
        vibrator.vibrate(VIBRATION_DURATION);
    }

    private void setupInputHandling() {
        // Set up text change listeners for both input fields
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSendButtonState();
                
                // Auto-expand if text exceeds one line
                if (binding.collapsedInput.getVisibility() == View.VISIBLE && 
                    binding.messageInput.getLineCount() > 1) {
                    expandInputSection();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        binding.messageInput.addTextChangedListener(textWatcher);
        binding.messageInputExpanded.addTextChangedListener(textWatcher);

        // Handle focus changes
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (hasFocus && v == binding.messageInput && 
                binding.messageInput.getLineCount() > 1) {
                expandInputSection();
            }
        };

        binding.messageInput.setOnFocusChangeListener(focusListener);
        binding.messageInputExpanded.setOnFocusChangeListener(focusListener);
    }

    public void expandInputSection() {
        binding.collapsedInput.setVisibility(View.GONE);
        binding.expandedInput.setVisibility(View.VISIBLE);
        binding.messageInputExpanded.requestFocus();
        binding.messageInputExpanded.setText(binding.messageInput.getText());
        
        // Place cursor at the end of text
        binding.messageInputExpanded.setSelection(
            binding.messageInputExpanded.length());

        scrollToBottom();
    }

    public void collapseInputSection() {
        // Only collapse if text is short enough
        if (binding.messageInputExpanded.getLineCount() <= 1) {
            binding.expandedInput.setVisibility(View.GONE);
            binding.collapsedInput.setVisibility(View.VISIBLE);
            binding.messageInput.setText(binding.messageInputExpanded.getText());
        }
    }

    public void updateSendButton(boolean hasContent) {
        // During generation, always show stop icon
        if (isGenerating) {
            binding.sendButton.setImageResource(R.drawable.ic_stop);
            binding.sendButtonExpanded.setImageResource(R.drawable.ic_stop);
            binding.sendButton.setEnabled(true);
            binding.sendButtonExpanded.setEnabled(true);
            binding.sendButton.setAlpha(1.0f);
            binding.sendButtonExpanded.setAlpha(1.0f);
            return;
        }
        
        // Normal mode icon selection
        int iconRes = hasContent || !AppConstants.AUDIO_CHAT_ENABLED ? 
            R.drawable.ic_send : R.drawable.ic_audio_wave;
        binding.sendButton.setImageResource(iconRes);
        binding.sendButtonExpanded.setImageResource(iconRes);
    }

    public void updateRecordingState(boolean isRecording) {
        int iconRes = isRecording ? R.drawable.ic_pause : R.drawable.ic_mic;
        binding.voiceButton.setImageResource(iconRes);
        binding.voiceButtonExpanded.setImageResource(iconRes);
    }

    public void setImagePreview(Uri imageUri) {
        this.pendingImageUri = imageUri;
        if (imageUri != null) {
            expandInputSection();
            UiUtils.showImagePreview(binding.getRoot().getContext(), imageUri, binding.expandedInput, () -> {
                clearImagePreview();
                updateSendButtonState();
            });
        }
    }

    public void clearImagePreview() {
        if (pendingImageUri != null) {
            binding.expandedInput.findViewById(R.id.imagePreviewContainer)
                .setVisibility(View.GONE);
            pendingImageUri = null;
        }
    }

    public String getCurrentInputText() {
        return binding.expandedInput.getVisibility() == View.VISIBLE ?
            binding.messageInputExpanded.getText().toString() :
            binding.messageInput.getText().toString();
    }

    public void clearInput() {
        binding.messageInput.setText("");
        binding.messageInputExpanded.setText("");
        clearImagePreview();
        collapseInputSection();
        updateSendButtonState();
        
        // Clear focus from EditText
        binding.messageInput.clearFocus();
        binding.messageInputExpanded.clearFocus();
        
        // Hide keyboard
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                binding.getRoot().getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        View currentFocus = ((Activity) binding.getRoot().getContext()).getCurrentFocus();
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    public void updateSendButtonState() {
        String message = getCurrentInputText();
        boolean shouldShowSend = !message.isEmpty() || pendingImageUri != null;
        updateSendButton(shouldShowSend);
    }

    private void scrollToBottom() {
        binding.recyclerView.post(() -> {
            int itemCount = binding.recyclerView.getAdapter().getItemCount();
            if (itemCount > 0) {
                binding.recyclerView.smoothScrollToPosition(itemCount - 1);
            }
        });
    }

    public Uri getPendingImageUri() {
        return pendingImageUri;
    }

    public void enableUI() {
        binding.messageInput.setEnabled(true);
        binding.messageInputExpanded.setEnabled(true);
        binding.sendButton.setEnabled(true);
        binding.sendButtonExpanded.setEnabled(true);
        binding.voiceButton.setEnabled(true);
        binding.voiceButtonExpanded.setEnabled(true);
        binding.attachButton.setEnabled(true);
        binding.attachButtonExpanded.setEnabled(true);
    }

    public void setGeneratingState(boolean generating) {
        this.isGenerating = generating;
        // Always update button when generation state changes
        updateSendButton(true);
    }
} 