package com.mtkresearch.breeze_app.service.llm;

import java.util.concurrent.CompletableFuture;
import com.mtkresearch.breeze_app.service.LLMEngineService;

/**
 * Interface for LLM backends that can perform text generation.
 */
public interface LLMBackend {
    
    /**
     * Token callback interface used for streaming responses
     */
    interface TokenCallback {
        /**
         * Called when a new token is generated.
         * @param token The newly generated token.
         */
        void onToken(String token);
    }
    
    /**
     * Initialize the backend
     * @return Future that completes when initialization is done
     */
    CompletableFuture<Boolean> initialize();
    
    /**
     * Check if this backend is available on the current device
     * @return true if available
     */
    boolean isAvailable();
    
    /**
     * Generate a response for the given prompt
     * @param prompt The input prompt
     * @return Future with the complete generated response
     */
    CompletableFuture<String> generateResponse(String prompt);
    
    /**
     * Generate a streaming response, with tokens delivered via callback
     * @param prompt The input prompt
     * @param callback Callback to receive tokens as they're generated
     * @return Future with the complete generated response
     */
    CompletableFuture<String> generateStreamingResponse(String prompt, 
                                                      LLMEngineService.StreamingResponseCallback callback);
    
    /**
     * Stop ongoing generation
     */
    void stopGeneration();
    
    /**
     * Reset the backend state
     * @return true if reset was successful
     */
    boolean reset();
    
    /**
     * Release all resources used by this backend
     */
    void releaseResources();
    
    /**
     * Get the name of this backend
     * @return Backend name
     */
    String getName();
    
    /**
     * Get the current memory usage of this backend in bytes
     * @return Memory usage in bytes
     */
    long getMemoryUsage();
} 