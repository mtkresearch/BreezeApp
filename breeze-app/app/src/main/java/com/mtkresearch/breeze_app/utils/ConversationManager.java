package com.mtkresearch.breeze_app.utils;

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
    private static final int DEFAULT_HISTORY_LOOKBACK = 10;

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

    /**
     * Gets formatted conversation history for the specified model type.
     */
    public String getFormattedHistory(ModelType modelType, int lookback) {
        if (messages.isEmpty()) {
            Log.d(TAG, "No messages in history");
            return "";
        }

        Log.d(TAG, String.format("Getting history: total messages=%d, lookback=%d", 
            messages.size(), lookback));

        return PromptManager.getFormattedConversationHistory(messages, modelType);
    }

    /**
     * Gets formatted conversation history with default lookback.
     */
    public String getFormattedHistory(ModelType modelType) {
        return getFormattedHistory(modelType, DEFAULT_HISTORY_LOOKBACK);
    }

    /**
     * Gets the complete formatted prompt including system instructions, conversation history, and user input.
     */
    public String getFormattedPrompt(String rawPrompt, ModelType modelType) {
        return PromptManager.formatCompletePrompt(rawPrompt, messages, modelType);
    }
} 