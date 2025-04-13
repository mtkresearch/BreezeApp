package com.mtkresearch.gai_android.service;

import android.util.Log;

/**
 * JNI bridge class with package name exactly matching what the native library expects.
 * This is a minimal implementation providing just what's needed for JNI binding.
 */
public class LLMEngineService {
    private static final String TAG = "LLMEngineService";
    private static volatile boolean MTK_BACKEND_AVAILABLE = false;
    private static final Object MTK_LOCK = new Object();
    
    static {
        try {
            // Load libraries in order
            try {
                System.loadLibrary("sigchain");  // Load signal handler first
                Log.d(TAG, "Loaded sigchain library for signal handling");
                Thread.sleep(100);  // Give time for signal handlers to initialize
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "Failed to load sigchain library, continuing anyway: " + e.getMessage());
            }
            
            // Load the main MTK JNI library
            try {
                System.loadLibrary("llm_jni");
                MTK_BACKEND_AVAILABLE = true;
                Log.d(TAG, "Successfully loaded llm_jni library");
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Failed to load llm_jni library: " + e.getMessage(), e);
                MTK_BACKEND_AVAILABLE = false;
            }
            
            // Register shutdown hook for cleanup if we successfully loaded the library
            if (MTK_BACKEND_AVAILABLE) {
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        // Simple cleanup on shutdown
                        try {
                            nativeResetLlm();
                            Thread.sleep(100);
                            nativeReleaseLlm();
                            Log.d(TAG, "Emergency cleanup completed successfully");
                        } catch (Exception e) {
                            Log.w(TAG, "Error during emergency cleanup", e);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in shutdown hook", e);
                    }
                }));
            }
        } catch (UnsatisfiedLinkError | Exception e) {
            MTK_BACKEND_AVAILABLE = false;
            Log.w(TAG, "Failed to load native libraries, MTK backend will be disabled: " + e.getMessage(), e);
        }
    }
    
    /**
     * Check if MTK backend is available
     * @return true if the native library was loaded successfully
     */
    public static boolean isMTKBackendAvailable() {
        return MTK_BACKEND_AVAILABLE;
    }
    
    /**
     * Initialize the LLM with the given configuration
     * @param configPath Path to the YAML configuration file
     * @param preloadSharedWeights Whether to preload shared weights
     * @return true if initialization was successful
     */
    public static native boolean nativeInitLlm(String configPath, boolean preloadSharedWeights);
    
    /**
     * Run inference with the given input
     * @param promptText The input text
     * @param maxResponseTokens Maximum number of tokens to generate
     * @param parsePromptTokens Whether to parse prompt tokens
     * @return The generated text
     */
    public static native String nativeInference(String promptText, int maxResponseTokens, boolean parsePromptTokens);
    
    /**
     * Run streaming inference with callback for tokens
     * @param promptText The input text
     * @param maxResponseTokens Maximum number of tokens to generate
     * @param parsePromptTokens Whether to parse prompt tokens
     * @param callback Callback for receiving tokens
     * @return The final generated text
     */
    public static native String nativeStreamingInference(String promptText, int maxResponseTokens, 
                                                       boolean parsePromptTokens, TokenCallback callback);
    
    /**
     * Release the LLM resources
     */
    public static native void nativeReleaseLlm();
    
    /**
     * Reset the LLM state
     * @return true if reset was successful
     */
    public static native boolean nativeResetLlm();
    
    /**
     * Swap to a different token size model
     * @param tokenSize The token size to swap to (1 or 128)
     * @return true if swap was successful
     */
    public static native boolean nativeSwapModel(int tokenSize);
    
    /**
     * Token callback interface used by the native code to stream tokens
     */
    public interface TokenCallback {
        void onToken(String token);
    }
    
    // Wrapper methods with proper error handling
    
    /**
     * Safe wrapper for nativeInitLlm
     */
    public static boolean safeInitLlm(String configPath, boolean preloadSharedWeights) {
        if (!MTK_BACKEND_AVAILABLE) {
            Log.e(TAG, "Cannot initialize LLM - library not loaded");
            return false;
        }
        
        try {
            return nativeInitLlm(configPath, preloadSharedWeights);
        } catch (Exception e) {
            Log.e(TAG, "Error in nativeInitLlm: " + e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Safe wrapper for nativeResetLlm
     */
    public static boolean safeResetLlm() {
        if (!MTK_BACKEND_AVAILABLE) {
            Log.e(TAG, "Cannot reset LLM - library not loaded");
            return false;
        }
        
        try {
            return nativeResetLlm();
        } catch (Exception e) {
            Log.e(TAG, "Error in nativeResetLlm: " + e.getMessage(), e);
            return false;
        }
    }
} 