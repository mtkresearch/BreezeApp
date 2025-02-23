package com.mtkresearch.breeze_app.utils;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.app.Activity;
import android.text.Editable;
import android.text.TextWatcher;

import com.mtkresearch.breeze_app.R;
import com.mtkresearch.breeze_app.databinding.ActivityChatBinding;

public class ChatUIStateHandler {
    private final ActivityChatBinding binding;
    private Uri pendingImageUri;
    private boolean isGenerating = false;
    private static final int MAX_LINES = 6;

    public ChatUIStateHandler(ActivityChatBinding binding) {
        this.binding = binding;
        setupInputHandling();
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