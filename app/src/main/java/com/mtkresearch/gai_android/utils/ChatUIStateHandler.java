package com.mtkresearch.gai_android.utils;

import android.content.Context;
import android.net.Uri;
import android.view.View;
import android.app.Activity;

import com.mtkresearch.gai_android.R;
import com.mtkresearch.gai_android.databinding.ActivityChatBinding;

public class ChatUIStateHandler {
    private final ActivityChatBinding binding;
    private Uri pendingImageUri;
    private boolean isGenerating = false;

    public ChatUIStateHandler(ActivityChatBinding binding) {
        this.binding = binding;
    }

    public void expandInputSection() {
        binding.collapsedInput.setVisibility(View.GONE);
        binding.expandedInput.setVisibility(View.VISIBLE);
        binding.messageInputExpanded.requestFocus();
        binding.messageInputExpanded.setText(binding.messageInput.getText());

        scrollToBottom();
    }

    public void collapseInputSection() {
        binding.expandedInput.setVisibility(View.GONE);
        binding.collapsedInput.setVisibility(View.VISIBLE);
    }

    public void updateSendButton(boolean hasContent) {
        int iconRes = isGenerating ? R.drawable.ic_stop : 
                     (hasContent ? R.drawable.ic_send : R.drawable.ic_audio_wave);
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
        binding.recyclerView.postDelayed(() ->
            binding.recyclerView.smoothScrollToPosition(
                binding.recyclerView.getAdapter().getItemCount()), 100);
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
        updateSendButtonState();
    }
} 