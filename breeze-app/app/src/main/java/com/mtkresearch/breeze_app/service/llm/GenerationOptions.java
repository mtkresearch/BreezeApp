package com.mtkresearch.breeze_app.service.llm;

/**
 * Configuration options for LLM response generation.
 * Uses builder pattern for easy configuration.
 */
public class GenerationOptions {
    // Default values
    private static final int DEFAULT_MAX_TOKENS = 256;
    private static final float DEFAULT_TEMPERATURE = 0.7f;
    private static final boolean DEFAULT_PARSE_PROMPT_TOKENS = false;
    
    // Generation parameters
    private final int maxTokens;
    private final float temperature;
    private final boolean parsePromptTokens;
    
    private GenerationOptions(Builder builder) {
        this.maxTokens = builder.maxTokens;
        this.temperature = builder.temperature;
        this.parsePromptTokens = builder.parsePromptTokens;
    }
    
    /**
     * Get the maximum number of tokens to generate
     */
    public int getMaxTokens() {
        return maxTokens;
    }
    
    /**
     * Get the temperature parameter (higher = more creative, lower = more deterministic)
     */
    public float getTemperature() {
        return temperature;
    }
    
    /**
     * Whether to parse prompt tokens
     */
    public boolean shouldParsePromptTokens() {
        return parsePromptTokens;
    }
    
    /**
     * Create a default options object
     */
    public static GenerationOptions getDefault() {
        return new Builder().build();
    }
    
    /**
     * Builder for GenerationOptions
     */
    public static class Builder {
        // Default values
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private float temperature = DEFAULT_TEMPERATURE;
        private boolean parsePromptTokens = DEFAULT_PARSE_PROMPT_TOKENS;
        
        /**
         * Set maximum number of tokens to generate
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }
        
        /**
         * Set temperature parameter (0.0 - 1.0)
         */
        public Builder temperature(float temperature) {
            this.temperature = Math.max(0.0f, Math.min(1.0f, temperature));
            return this;
        }
        
        /**
         * Set whether to parse prompt tokens
         */
        public Builder parsePromptTokens(boolean parsePromptTokens) {
            this.parsePromptTokens = parsePromptTokens;
            return this;
        }
        
        /**
         * Build the options object
         */
        public GenerationOptions build() {
            return new GenerationOptions(this);
        }
    }
} 