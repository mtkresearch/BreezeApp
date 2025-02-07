package com.mtkresearch.gai_android.utils;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ChatMessage {
    private String text;
    private final boolean isUser;
    private Uri imageUri;

    public ChatMessage(@NonNull String text, boolean isUser) {
        this.text = text != null ? text : "";
        this.isUser = isUser;
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
        return imageUri;
    }

    public void setImageUri(@Nullable Uri imageUri) {
        this.imageUri = imageUri;
    }

    public boolean hasImage() {
        return imageUri != null;
    }

    @Override
    public String toString() {
        return "ChatMessage{" +
                "text='" + text + '\'' +
                ", isUser=" + isUser +
                ", hasImage=" + hasImage() +
                '}';
    }
} 