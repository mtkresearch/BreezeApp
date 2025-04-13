package com.mtkresearch.breeze_app.service.bridge;

/**
 * Interface defining native methods that are implemented in the C++ code.
 * This provides a clear contract between Java and native code.
 */
public interface JNIBridge {
    /**
     * Callback interface for streaming inference tokens
     */
    interface StreamingCallback {
        /**
         * Called when a token is generated
         * @param token The generated token
         */
        void onToken(String token);
    }
    
    /**
     * Initialize the LLM with the given configuration
     * @param configPath Path to the YAML configuration file
     * @param preloadSharedWeights Whether to preload shared weights
     * @return true if initialization was successful
     */
    boolean nativeInitLlm(String configPath, boolean preloadSharedWeights);
    
    /**
     * Run inference with the given input
     * @param promptText The input text
     * @param maxResponseTokens Maximum number of tokens to generate
     * @param parsePromptTokens Whether to parse prompt tokens
     * @return The generated text
     */
    String nativeInference(String promptText, int maxResponseTokens, boolean parsePromptTokens);
    
    /**
     * Run streaming inference with the given input
     * @param promptText The input text
     * @param callback The callback to receive tokens
     * @return true if the streaming inference started successfully
     */
    boolean nativeRunStreamingInference(String promptText, StreamingCallback callback);
    
    /**
     * Release the LLM resources
     */
    void nativeReleaseLlm();
    
    /**
     * Reset the LLM state
     * @return true if reset was successful
     */
    boolean nativeResetLlm();
    
    /**
     * Swap to a different token size model
     * @param tokenSize The new token size
     * @return true if swap was successful
     */
    boolean nativeSwapModel(int tokenSize);
    
    /**
     * Set the token size for the model
     * @param tokenSize The token size to set
     * @return true if the operation was successful
     */
    boolean nativeSetTokenSize(int tokenSize);
} 