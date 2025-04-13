package com.mtkresearch.breeze_app.service.bridge;

import android.util.Log;
import com.mtkresearch.breeze_app.utils.AppConstants;
import com.mtkresearch.gai_android.service.LLMEngineService;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridge to MTK native LLM implementation.
 * This class provides a friendly interface to the native methods implemented in the NDK.
 */
public class MTKNativeBridge implements JNIBridge {
    private static final String TAG = AppConstants.MTK_SERVICE_TAG;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // Singleton instance
    private static MTKNativeBridge instance = null;
    
    /**
     * Get the singleton instance of MTKNativeBridge
     * @return The singleton instance
     */
    public static synchronized MTKNativeBridge getInstance() {
        if (instance == null) {
            instance = new MTKNativeBridge();
            Log.d(TAG, "Created new MTKNativeBridge instance");
        }
        return instance;
    }
    
    /**
     * Initialize the native library
     * @return true if initialization was successful
     */
    public static boolean initialize() {
        boolean isAvailable = LLMEngineService.isMTKBackendAvailable();
        Log.d(TAG, "MTK library available: " + isAvailable + 
              " (loaded: " + isLibraryLoaded() + ")");
        
        if (isAvailable) {
            try {
                // Force load of some classes that might be needed by the JNI code
                Class.forName("com.mtkresearch.gai_android.service.LLMEngineService$TokenCallback");
                Log.d(TAG, "Successfully loaded required classes for JNI");
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "Could not preload classes: " + e.getMessage());
            }
        }
        
        return isAvailable;
    }
    
    /**
     * Check if the native library is loaded
     * @return true if the library is loaded
     */
    public static boolean isLibraryLoaded() {
        return LLMEngineService.isMTKBackendAvailable();
    }

    public MTKNativeBridge() {
        Log.d(TAG, "MTKNativeBridge created, library loaded: " + isLibraryLoaded());
    }

    /**
     * Register the service with the native bridge
     * @param service The service to register
     */
    public void registerService(com.mtkresearch.breeze_app.service.LLMEngineService service) {
        // No operation needed - JNI functions are static
        Log.d(TAG, "Service registration not needed with direct JNI approach");
    }

    /**
     * Unregister the service from the native bridge
     * @param service The service to unregister
     */
    public void unregisterService(com.mtkresearch.breeze_app.service.LLMEngineService service) {
        // No operation needed - JNI functions are static
        Log.d(TAG, "Service unregistration not needed with direct JNI approach");
    }

    /**
     * Callback interface for receiving tokens during streaming inference
     */
    public interface TokenCallback {
        /**
         * Called when a new token is generated
         * @param token The token text
         */
        void onToken(String token);
    }

    /**
     * Initialize the LLM with the given configuration
     * @param configPath Path to the YAML configuration file
     * @param preloadSharedWeights Whether to preload shared weights
     * @return true if initialization was successful
     */
    @Override
    public boolean nativeInitLlm(String configPath, boolean preloadSharedWeights) {
        if (!isLibraryLoaded()) {
            Log.e(TAG, "Cannot initialize LLM - library not loaded");
            return false;
        }
        
        boolean result = false;
        try {
            // Call the safer native method using the compatibility class
            result = LLMEngineService.safeInitLlm(configPath, preloadSharedWeights);
            initialized.set(result);
            Log.d(TAG, "LLM initialization " + (result ? "successful" : "failed") + " with config: " + configPath);
        } catch (Exception e) {
            Log.e(TAG, "Exception during LLM initialization: " + e.getMessage(), e);
        }
        return result;
    }

    /**
     * Run inference with the given input
     * @param promptText The input text
     * @param maxResponseTokens Maximum number of tokens to generate
     * @param parsePromptTokens Whether to parse prompt tokens
     * @return The generated text
     */
    @Override
    public String nativeInference(String promptText, int maxResponseTokens, boolean parsePromptTokens) {
        if (!isLibraryLoaded() || !initialized.get()) {
            Log.e(TAG, "Cannot run inference - library not loaded or LLM not initialized");
            return "";
        }
        
        try {
            // Call the native method using the compatibility class
            return LLMEngineService.nativeInference(promptText, maxResponseTokens, parsePromptTokens);
        } catch (Exception e) {
            Log.e(TAG, "Exception during inference: " + e.getMessage(), e);
            return "";
        }
    }

    /**
     * Run streaming inference with the given input
     * @param promptText The input text
     * @param maxResponseTokens Maximum number of tokens to generate
     * @param parsePromptTokens Whether to parse prompt tokens
     * @param callback The callback for receiving tokens
     * @return The final generated text
     */
    public String streamingInference(String promptText, int maxResponseTokens, boolean parsePromptTokens,
                                    TokenCallback callback) {
        if (!isLibraryLoaded() || !initialized.get()) {
            Log.e(TAG, "Cannot run streaming inference - library not loaded or LLM not initialized");
            return "";
        }
        
        if (promptText == null || promptText.isEmpty()) {
            Log.e(TAG, "Empty prompt provided to streaming inference");
            return "";
        }
        
        Log.d(TAG, "Starting streaming inference with prompt length: " + promptText.length() + 
              ", max tokens: " + maxResponseTokens);
        
        try {
            // Create an adapter that converts our callback to the native library's callback
            LLMEngineService.TokenCallback nativeCallback = new LLMEngineService.TokenCallback() {
                @Override
                public void onToken(String token) {
                    if (callback != null) {
                        try {
                            callback.onToken(token);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in token callback: " + e.getMessage(), e);
                            // Continue despite callback error
                        }
                    }
                }
            };
            
            // Call the native method using the compatibility class with the adapter
            String result = LLMEngineService.nativeStreamingInference(
                promptText, maxResponseTokens, parsePromptTokens, nativeCallback);
            
            Log.d(TAG, "Streaming inference completed, result length: " + 
                  (result != null ? result.length() : 0));
            
            return result != null ? result : "";
        } catch (Exception e) {
            Log.e(TAG, "Exception during streaming inference: " + e.getMessage(), e);
            return "";
        }
    }

    /**
     * Run streaming inference with the given input and use the LLMEngineService for callbacks
     * @param promptText The input text
     * @param callback The callback for receiving tokens
     * @return true if the streaming inference started successfully
     */
    @Override
    public boolean nativeRunStreamingInference(String promptText, StreamingCallback callback) {
        if (!isLibraryLoaded() || !initialized.get()) {
            Log.e(TAG, "Cannot run streaming inference - library not loaded or LLM not initialized");
            return false;
        }
        
        if (promptText == null || promptText.isEmpty()) {
            Log.e(TAG, "Empty prompt provided to streaming inference");
            return false;
        }
        
        Log.d(TAG, "Starting streaming inference with prompt length: " + promptText.length());
        
        try {
            // First ensure we're using token size 128 for prompt parsing
            try {
                if (!nativeSetTokenSize(128)) {
                    Log.e(TAG, "Failed to set token size to 128 for prompt parsing, attempting to continue anyway");
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception while setting token size to 128 before inference", e);
                // Continue despite error - the native layer might still work
            }
            
            // Create an adapter that converts our callback to the native library's callback
            LLMEngineService.TokenCallback nativeCallback = token -> {
                if (callback != null) {
                    try {
                        callback.onToken(token);
                    } catch (Exception e) {
                        Log.e(TAG, "Error in token callback: " + e.getMessage(), e);
                        // Continue despite callback error
                    }
                }
            };
            
            // Call the native method with error handling
            try {
                // Call the native method using the compatibility class with the adapter
                LLMEngineService.nativeStreamingInference(
                    promptText, 
                    256, // Max response tokens - hardcoded to avoid dependency
                    false, // Don't parse prompt tokens by default
                    nativeCallback);
                    
                // After generation, ensure we switch back to token size 128
                try {
                    Log.d(TAG, "Streaming inference completed, switching back to token size 128");
                    if (!nativeSetTokenSize(128)) {
                        Log.w(TAG, "Failed to reset token size to 128 after successful inference");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception while resetting token size after inference", e);
                    // Continue despite error in token size reset
                }
                    
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Exception during native streaming inference call", e);
                // Continue to cleanup
                throw e; // Rethrow to outer handler
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during streaming inference: " + e.getMessage(), e);
            try {
                // Try to reset the LLM state after error
                if (initialized.get()) {
                    try {
                        Log.d(TAG, "Attempting to reset LLM after inference error");
                        nativeResetLlm();
                    } catch (Exception resetEx) {
                        Log.e(TAG, "Failed to reset LLM after inference error", resetEx);
                    }
                }
                
                // Ensure we switch back to token size 128 even after error
                try {
                    Log.d(TAG, "Attempting to set token size back to 128 after inference error");
                    nativeSetTokenSize(128);
                } catch (Exception tokenEx) {
                    Log.e(TAG, "Error setting token size back to 128 after failure", tokenEx);
                }
            } catch (Exception cleanup) {
                Log.e(TAG, "Critical error during cleanup after inference failure", cleanup);
            }
            return false;
        }
    }

    /**
     * Release the LLM resources
     */
    @Override
    public void nativeReleaseLlm() {
        if (!isLibraryLoaded()) {
            Log.e(TAG, "Cannot release LLM - library not loaded");
            return;
        }
        
        try {
            // Call the native method using the compatibility class
            LLMEngineService.nativeReleaseLlm();
            initialized.set(false);
            Log.d(TAG, "LLM resources released");
        } catch (Exception e) {
            Log.e(TAG, "Exception during LLM release: " + e.getMessage(), e);
        }
    }

    /**
     * Reset the LLM state
     * @return true if reset was successful
     */
    @Override
    public boolean nativeResetLlm() {
        if (!isLibraryLoaded() || !initialized.get()) {
            Log.e(TAG, "Cannot reset LLM - library not loaded or LLM not initialized");
            return false;
        }
        
        try {
            // Call the safer native method using the compatibility class
            return LLMEngineService.safeResetLlm();
        } catch (Exception e) {
            Log.e(TAG, "Exception during LLM reset: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Swap to a different model size
     * @param tokenSize The token size to swap to (1 or 128)
     * @return true if swap was successful
     */
    @Override
    public boolean nativeSwapModel(int tokenSize) {
        if (!isLibraryLoaded() || !initialized.get()) {
            Log.e(TAG, "Cannot swap model - library not loaded or LLM not initialized");
            return false;
        }
        
        try {
            // Call the native method using the compatibility class
            return LLMEngineService.nativeSwapModel(tokenSize);
        } catch (Exception e) {
            Log.e(TAG, "Exception during model swap: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Set the token size
     * @param tokenSize The token size to set (1 or 128)
     * @return true if setting token size was successful
     */
    @Override
    public boolean nativeSetTokenSize(int tokenSize) {
        if (!isLibraryLoaded() || !initialized.get()) {
            Log.e(TAG, "Cannot set token size - library not loaded or LLM not initialized");
            return false;
        }
        
        try {
            // Use the swap model method since no direct token size method exists
            return LLMEngineService.nativeSwapModel(tokenSize);
        } catch (Exception e) {
            Log.e(TAG, "Exception during setting token size: " + e.getMessage(), e);
            return false;
        }
    }
} 