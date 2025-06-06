package com.mtkresearch.breezeapp.utils;

/**
 * Utility class for estimating token counts in text for LLM input.
 * Uses character-based heuristics to approximate token counts.
 */
public class TokenEstimator {

    /**
     * Estimates the number of tokens in the input text.
     * 
     * @param text The text to estimate tokens for
     * @return Estimated token count
     */
    public static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int totalTokens = 0;
        int asciiCount = 0;
        int chineseCount = 0;
        int otherCount = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c <= 0x007F) {
                // ASCII characters (e.g., English letters, digits, punctuation)
                asciiCount++;
            } else if (c >= 0x4E00 && c <= 0x9FFF) {
                // Common Chinese characters
                chineseCount++;
            } else {
                // Other characters (e.g., emojis, symbols, other scripts)
                otherCount++;
            }
        }

        // Apply heuristic multipliers based on character types
        totalTokens += Math.round(asciiCount / 4.0); // Approx. 1 token per 4 ASCII characters
        totalTokens += chineseCount * 2;             // Approx. 2 tokens per Chinese character
        totalTokens += Math.round(otherCount * 1.5); // Approx. 1.5 tokens per other character

        // Add a small overhead for formatting tokens
        totalTokens += 20;

        return totalTokens;
    }
} 