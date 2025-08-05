package com.mtkresearch.breezeapp.utils;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mtkresearch.breezeapp.R;

import java.io.Serializable;

public class ChatMessage implements Serializable {
    private String text;
    private final boolean isUser;
    private transient Uri imageUri; // Mark as transient since Uri is not serializable
    private int promptId; // Added to group messages in the same conversation
    private String imageUriString; // Store image URI as string for serialization
    private int customTextColor = 0;
    private boolean isCompleted = true; // Default to true since most messages are completed
    private boolean isError = false; // Flag to indicate if this message is an error response

    public ChatMessage(@NonNull String text, boolean isUser) {
        this(text, isUser, 0);
    }

    public ChatMessage(@NonNull String text, boolean isUser, int promptId) {
        this.text = text != null ? text : "";
        this.isUser = isUser;
        this.promptId = promptId;
    }

    @NonNull
    public String getText() {
        return text != null ? text : "";
    }

    public void updateText(@Nullable String newText) {
        this.text = newText != null ? newText : "";
    }

    public boolean hasText() {
        return text != null && !text.trim().isEmpty();
    }

    public boolean isUser() {
        return isUser;
    }

    @Nullable
    public Uri getImageUri() {
        if (imageUri == null && imageUriString != null) {
            imageUri = Uri.parse(imageUriString);
        }
        return imageUri;
    }

    public void setImageUri(@Nullable Uri imageUri) {
        this.imageUri = imageUri;
        this.imageUriString = imageUri != null ? imageUri.toString() : null;
    }

    public boolean hasImage() {
        return imageUri != null;
    }

    public int getPromptId() {
        return promptId;
    }

    public void setPromptId(int promptId) {
        this.promptId = promptId;
    }

    public boolean hasContent() {
        return text != null && !text.isEmpty() && !text.equals("Thinking...");
    }

    public void setCustomTextColor(int color) {
        this.customTextColor = color;
    }

    public int getCustomTextColor() {
        return customTextColor;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        this.isCompleted = completed;
    }

    /**
     * Set whether this message is an error response
     * @param isError true if this message is an error response
     */
    public void setError(boolean isError) {
        this.isError = isError;
    }

    /**
     * Check if this message is marked as an error response
     * @return true if this message is marked as an error
     */
    public boolean isError() {
        return isError;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "text='" + text + '\'' +
                ", isUser=" + isUser +
                ", promptId=" + promptId +
                ", hasImage=" + hasImage() +
                ", isError=" + isError +
                '}';
    }
} 