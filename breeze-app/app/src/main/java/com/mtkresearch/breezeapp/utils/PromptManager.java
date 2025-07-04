package com.mtkresearch.breezeapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import com.executorch.ModelType;
import com.executorch.PromptFormat;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages all prompt-related functionality including formatting, history management,
 * and conversation context handling.
 */
public class PromptManager {

    private static Context appContext;
    
    public static void initialize(Context context) {
        appContext = context.getApplicationContext();
    }

    private static int getHistoryLookback() {
        return AppConstants.CONVERSATION_HISTORY_LOOKBACK;
    }

    /**
     * Formats a complete prompt including system instructions, conversation history, and user input.
     */
    public static String formatCompletePrompt(String userMessage, List<ChatMessage> conversationHistory, ModelType modelType) {
        // 1. Get system prompt with template
        String systemPrompt = PromptFormat.getSystemPromptTemplate(modelType)
                .replace(PromptFormat.SYSTEM_PLACEHOLDER, PromptFormat.DEFAULT_SYSTEM_PROMPT);
                
        // 2. Get conversation history
        String history = getFormattedConversationHistory(conversationHistory, modelType);
        
        // 3. Format user message using template
        String userPrompt = PromptFormat.getUserPromptTemplate(modelType)
                .replace(PromptFormat.USER_PLACEHOLDER, userMessage);
        
        // Combine all parts
        return systemPrompt + history + userPrompt;
    }
    
    /**
     * Formats the conversation history with proper turn structure and lookback window.
     */
    public static String getFormattedConversationHistory(List<ChatMessage> allMessages, ModelType modelType) {
        if (allMessages == null || allMessages.isEmpty()) {
            return "";
        }
        
        int historyLookback = getHistoryLookback();
        
        // Get recent messages based on lookback window
        List<ChatMessage> recentMessages = new ArrayList<>();
        int startIndex = Math.max(0, allMessages.size() - (historyLookback * 2));
        for (int i = startIndex; i < allMessages.size(); i++) {
            recentMessages.add(allMessages.get(i));
        }
        
        if (recentMessages.isEmpty()) {
            return "";
        }

        StringBuilder history = new StringBuilder();
        String conversationFormat = PromptFormat.getConversationFormat(modelType);
        String currentFormat = conversationFormat;
        
        for (ChatMessage message : recentMessages) {
            if (message.isUser()) {
                currentFormat = currentFormat.replace(PromptFormat.USER_PLACEHOLDER, message.getText());
            } else {
                currentFormat = currentFormat.replace(PromptFormat.ASSISTANT_PLACEHOLDER, message.getText());
                history.append(currentFormat);
                currentFormat = conversationFormat;
            }
        }
        
        return history.toString();
    }
} 