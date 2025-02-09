package com.mtkresearch.gai_android.utils;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;

public class ChatMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private String text;
    private final boolean isUser;
    private transient Uri imageUri; // Mark as transient since Uri is not serializable
    private int promptId; // Added to group messages in the same conversation
    private String imageUriString; // Store image URI as string for serialization

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

    public void appendText(@Nullable String newText) {
        if (newText != null) {
            this.text = getText() + newText;
        }
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
        return getImageUri() != null;
    }

    public int getPromptId() {
        return promptId;
    }

    public void setPromptId(int promptId) {
        this.promptId = promptId;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "text='" + text + '\'' +
                ", isUser=" + isUser +
                ", promptId=" + promptId +
                ", hasImage=" + hasImage() +
                '}';
    }
} 