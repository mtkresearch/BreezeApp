package com.mtkresearch.breezeapp.utils;

import android.util.Log;
import com.executorch.ModelType;

import java.util.List;
import java.util.ArrayList;
import androidx.annotation.NonNull;

/**
 * Manages conversation history and prompt formatting for different model types.
 * This class is the single source of truth for conversation state and formatting.
 */
public class ConversationManager {
    private static final String TAG = "ConversationManager";

    // Internal message storage
    private final List<ChatMessage> messages = new ArrayList<>();

    // Message management methods
    public void addMessage(@NonNull ChatMessage message) {
        messages.add(message);
        Log.d(TAG, String.format("Added message to history: total=%d, isUser=%b, text='%s'", 
            messages.size(), message.isUser(), message.getText()));
    }

    public void removeLastMessage() {
        if (!messages.isEmpty()) {
            messages.remove(messages.size() - 1);
        }
    }

    @NonNull
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }

    public void clearMessages() {
        messages.clear();
    }

} 