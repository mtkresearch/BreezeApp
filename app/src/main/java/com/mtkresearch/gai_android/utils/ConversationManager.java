package com.mtkresearch.gai_android.utils;

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

    // Placeholders for prompt formatting
    public static final String USER_PLACEHOLDER = "{user}";
    public static final String ASSISTANT_PLACEHOLDER = "{assistant}";

    // System prompts for different model types
    private static final String LLAMA_SYSTEM_PROMPT = 
        "You are a helpful AI assistant. You should provide accurate, informative, and engaging responses while being direct and staying on topic.\n\n";
    private static final String LLAVA_SYSTEM_PROMPT = 
        "You are a helpful vision assistant. You should provide accurate, informative, and engaging responses about the images while being direct and staying on topic.\n\n";

    // Conversation format templates
    private static final String LLAMA_CONVERSATION_FORMAT = 
        "User: {user}\nAssistant: {assistant}\n\n";
    private static final String LLAVA_CONVERSATION_FORMAT = 
        "Human: {user}\nAssistant: {assistant}\n\n";

    // Stop tokens
    private static final String LLAMA_STOP_TOKEN = "</s>";
    private static final String LLAVA_STOP_TOKEN = "</s>";

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
     * Gets the conversation format template for the specified model type.
     */
    public static String getConversationFormat(ModelType modelType) {
        switch (modelType) {
            case LLAVA_1_5:
                return LLAVA_CONVERSATION_FORMAT;
            default:
                return LLAMA_CONVERSATION_FORMAT;
        }
    }

    /**
     * Gets the system prompt for the specified model type.
     */
    public static String getSystemPrompt(ModelType modelType) {
        switch (modelType) {
            case LLAVA_1_5:
                return LLAVA_SYSTEM_PROMPT;
            default:
                return LLAMA_SYSTEM_PROMPT;
        }
    }

    /**
     * Gets the stop token for the specified model type.
     */
    public static String getStopToken(ModelType modelType) {
        switch (modelType) {
            case LLAVA_1_5:
                return LLAVA_STOP_TOKEN;
            default:
                return LLAMA_STOP_TOKEN;
        }
    }

    /**
     * Gets formatted conversation history for the specified model type.
     */
    public String getFormattedHistory(ModelType modelType, int lookback) {
        if (messages.isEmpty()) {
            Log.d(TAG, "No messages in history");
            return "";
        }

        StringBuilder history = new StringBuilder();
        String conversationFormat = getConversationFormat(modelType);
        int startIndex = Math.max(0, messages.size() - lookback);
        
        Log.d(TAG, String.format("Getting history: total messages=%d, lookback=%d, startIndex=%d", 
            messages.size(), lookback, startIndex));

        for (int i = startIndex; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            String format = conversationFormat;
            if (message.isUser()) {
                format = format.replace(USER_PLACEHOLDER, message.getText());
                format = format.replace(ASSISTANT_PLACEHOLDER, "");
            } else {
                format = format.replace(USER_PLACEHOLDER, "");
                format = format.replace(ASSISTANT_PLACEHOLDER, message.getText());
            }
            history.append(format);
            Log.d(TAG, String.format("Added message %d to history: isUser=%b, text='%s'", 
                i, message.isUser(), message.getText()));
        }

        String formattedHistory = history.toString();
        Log.d(TAG, "Final formatted history: " + formattedHistory);
        return formattedHistory;
    }

    /**
     * Gets formatted conversation history with default lookback.
     */
    public String getFormattedHistory(ModelType modelType) {
        return getFormattedHistory(modelType, DEFAULT_HISTORY_LOOKBACK);
    }

    /**
     * Gets formatted user prompt.
     */
    public static String getFormattedUserPrompt(ModelType modelType, String rawPrompt) {
        String format = getConversationFormat(modelType);
        return format.replace(USER_PLACEHOLDER, rawPrompt)
                    .replace(ASSISTANT_PLACEHOLDER, "");
    }

    /**
     * Gets formatted system prompt.
     */
    public static String getFormattedSystemPrompt(ModelType modelType) {
        return getSystemPrompt(modelType);
    }

    /**
     * Gets formatted system and user prompt combined.
     */
    public static String getFormattedSystemAndUserPrompt(ModelType modelType, String rawPrompt) {
        return getSystemPrompt(modelType) + getFormattedUserPrompt(modelType, rawPrompt);
    }

    /**
     * Gets the complete formatted prompt including system instructions, conversation history, and user input.
     */
    public String getFormattedPrompt(String rawPrompt, ModelType modelType) {
        String history = getFormattedHistory(modelType);
        
        if (history.isEmpty()) {
            return getFormattedSystemAndUserPrompt(modelType, rawPrompt);
        }

        return getFormattedSystemPrompt(modelType) +
               history +
               getFormattedUserPrompt(modelType, rawPrompt);
    }
} 