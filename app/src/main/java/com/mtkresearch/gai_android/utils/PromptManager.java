package com.mtkresearch.gai_android.utils;

import com.executorch.ModelType;
import com.executorch.PromptFormat;
import java.util.List;
import java.util.ArrayList;

/**
 * Manages all prompt-related functionality including formatting, history management,
 * and conversation context handling.
 */
public class PromptManager {
    private static final int DEFAULT_HISTORY_LOOKBACK = 20;
    private static final int MAX_SEQUENCE_LENGTH = 2048;  // Maximum sequence length for the model
    private static final double TOKENS_PER_CHAR_ESTIMATE = 0.4;  // Rough estimate of tokens per character
    
    /**
     * Estimates if the prompt would exceed the maximum sequence length.
     * Uses a rough estimation based on character count.
     */
    public static boolean wouldExceedMaxLength(String userMessage, List<ChatMessage> conversationHistory, ModelType modelType) {
        String fullPrompt = formatCompletePrompt(userMessage, conversationHistory, modelType);
        int estimatedTokens = (int)(fullPrompt.length() * TOKENS_PER_CHAR_ESTIMATE);
        return estimatedTokens >= MAX_SEQUENCE_LENGTH;
    }
    
    /**
     * Gets the maximum sequence length supported by the model.
     */
    public static int getMaxSequenceLength() {
        return MAX_SEQUENCE_LENGTH;
    }
    
    /**
     * Formats a complete prompt including system instructions, conversation history, and user input.
     */
    public static String formatCompletePrompt(String userMessage, List<ChatMessage> conversationHistory, ModelType modelType) {
        String systemPrompt = PromptFormat.getSystemPromptTemplate(modelType)
            .replace(PromptFormat.SYSTEM_PLACEHOLDER, PromptFormat.DEFAULT_SYSTEM_PROMPT);
            
        String conversationContext = getFormattedConversationHistory(conversationHistory, modelType);
        
        String userPrompt = PromptFormat.getUserPromptTemplate(modelType)
            .replace(PromptFormat.USER_PLACEHOLDER, userMessage);
            
        return systemPrompt + conversationContext + userPrompt;
    }
    
    /**
     * Formats the conversation history with proper turn structure and lookback window.
     */
    public static String getFormattedConversationHistory(List<ChatMessage> allMessages, ModelType modelType) {
        if (allMessages == null || allMessages.isEmpty()) {
            return "";
        }
        
        // Get recent messages based on lookback window
        List<ChatMessage> recentMessages = new ArrayList<>();
        int startIndex = Math.max(0, allMessages.size() - (DEFAULT_HISTORY_LOOKBACK * 2));
        for (int i = startIndex; i < allMessages.size(); i++) {
            recentMessages.add(allMessages.get(i));
        }
        
        if (recentMessages.isEmpty()) {
            return "";
        }

        StringBuilder history = new StringBuilder();
        int prevPromptId = recentMessages.get(0).getPromptId();
        String conversationFormat = PromptFormat.getConversationFormat(modelType);
        String currentFormat = conversationFormat;
        
        for (ChatMessage message : recentMessages) {
            int currentPromptId = message.getPromptId();
            
            if (currentPromptId != prevPromptId) {
                history.append(currentFormat);
                currentFormat = conversationFormat;
                prevPromptId = currentPromptId;
            }
            
            if (message.isUser()) {
                currentFormat = currentFormat.replace(PromptFormat.USER_PLACEHOLDER, message.getText());
            } else {
                currentFormat = currentFormat.replace(PromptFormat.ASSISTANT_PLACEHOLDER, message.getText());
            }
        }
        
        history.append(currentFormat);
        return history.toString();
    }
    
    /**
     * Gets the stop token for the specified model type.
     */
    public static String getStopToken(ModelType modelType) {
        return PromptFormat.getStopToken(modelType);
    }
    
    /**
     * Gets the default lookback window size for conversation history.
     */
    public static int getDefaultHistoryLookback() {
        return DEFAULT_HISTORY_LOOKBACK;
    }
} 