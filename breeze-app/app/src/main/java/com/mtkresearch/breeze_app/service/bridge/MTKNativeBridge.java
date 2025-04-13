package com.mtkresearch.breeze_app.service.bridge;

import android.util.Log;
import com.mtkresearch.breeze_app.utils.AppConstants;

/**
 * Bridge class to handle all native method calls to the MTK backend.
 * This isolates the bridging logic from the main service implementation.
 */
public class MTKNativeBridge {
    private static final String TAG = AppConstants.MTK_SERVICE_TAG;
    
    // Bridge instance from the gai_android package that the native library expects
    private static com.mtkresearch.gai_android.service.LLMEngineService bridgeInstance = null;
    
    // Interface that mirrors the callback in the LLMEngineService
    public interface TokenCallback {
        void onToken(String token);
    }
    
    // Singleton instance to ensure we only have one bridge
    private static MTKNativeBridge instance;
    
    /**
     * Get the singleton instance of the bridge
     */
    public static synchronized MTKNativeBridge getInstance() {
        if (instance == null) {
            instance = new MTKNativeBridge();
        }
        return instance;
    }
    
    /**
     * Private constructor to prevent direct instantiation
     */
    private MTKNativeBridge() {
        // Initialize bridgeInstance if not already created during static initialization
        if (bridgeInstance == null) {
            try {
                bridgeInstance = com.mtkresearch.gai_android.service.LLMEngineService.class.newInstance();
                Log.d(TAG, "Created bridge instance in constructor");
            } catch (Exception e) {
                Log.e(TAG, "Failed to create bridge instance in constructor", e);
            }
        }
    }
    
    /**
     * Initialize the bridge by loading native libraries
     * @return true if initialization was successful
     */
    public static boolean initialize() {
        try {
            // Load libraries in order
            System.loadLibrary("sigchain");  // Load signal handler first
            Thread.sleep(100);  // Give time for signal handlers to initialize
            
            System.loadLibrary("llm_jni");
            
            // Create a bridge instance - this will create the class with the package name the native code expects
            try {
                bridgeInstance = com.mtkresearch.gai_android.service.LLMEngineService.class.newInstance();
                
                // Test if native methods work by trying to call one
                boolean nativeMethodsAvailable = false;
                try {
                    // Try calling nativeResetLlm to verify it exists
                    bridgeInstance.nativeResetLlm();
                    nativeMethodsAvailable = true;
                    Log.d(TAG, "Successfully verified native methods through bridge");
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "Native methods implemented incorrectly: " + e.getMessage());
                    nativeMethodsAvailable = false;
                }
                
                AppConstants.MTK_BACKEND_AVAILABLE = nativeMethodsAvailable;
                Log.d(TAG, "Successfully loaded llm_jni library, native methods available: " + nativeMethodsAvailable);
                return nativeMethodsAvailable;
            } catch (Exception e) {
                Log.e(TAG, "Error creating bridge instance: " + e.getMessage(), e);
                AppConstants.MTK_BACKEND_AVAILABLE = false;
                return false;
            }
        } catch (UnsatisfiedLinkError | Exception e) {
            AppConstants.MTK_BACKEND_AVAILABLE = false;
            Log.w(TAG, "Failed to load native libraries, MTK backend will be disabled", e);
            return false;
        }
    }
    
    /**
     * Register a service instance with the bridge
     * @param service The LLMEngineService instance to register
     * @return true if registration was successful
     */
    public boolean registerService(com.mtkresearch.breeze_app.service.LLMEngineService service) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot register service");
            return false;
        }
        
        try {
            // The registerInstance method expects a com.mtkresearch.breeze_app.service.LLMEngineService
            // We pass the service parameter, which is of the expected type
            com.mtkresearch.gai_android.service.LLMEngineService.registerInstance(service);
            Log.d(TAG, "Registered service instance with the JNI bridge");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to register service instance with bridge", e);
            return false;
        }
    }
    
    /**
     * Initialize the LLM with the given configuration
     */
    public boolean initLlm(String yamlConfigPath, boolean preloadSharedWeights) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeInitLlm");
            return false;
        }
        try {
            return bridgeInstance.nativeInitLlm(yamlConfigPath, preloadSharedWeights);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeInitLlm() not available: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Run inference with the given input
     */
    public String inference(String inputString, int maxResponse, boolean parsePromptTokens) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeInference");
            return AppConstants.LLM_ERROR_RESPONSE;
        }
        try {
            return bridgeInstance.nativeInference(inputString, maxResponse, parsePromptTokens);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeInference() not available: " + e.getMessage());
            return AppConstants.LLM_ERROR_RESPONSE;
        }
    }
    
    /**
     * Run streaming inference with the given input and callback
     */
    public String streamingInference(String inputString, int maxResponse, boolean parsePromptTokens, 
                                    final TokenCallback callback) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeStreamingInference");
            return AppConstants.LLM_ERROR_RESPONSE;
        }
        try {
            // Create adapter for the callback
            com.mtkresearch.gai_android.service.LLMEngineService.TokenCallback bridgeCallback = 
                new com.mtkresearch.gai_android.service.LLMEngineService.TokenCallback() {
                    @Override
                    public void onToken(String token) {
                        if (callback != null) {
                            callback.onToken(token);
                        }
                    }
                };
            return bridgeInstance.nativeStreamingInference(inputString, maxResponse, parsePromptTokens, bridgeCallback);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeStreamingInference() not available: " + e.getMessage());
            return AppConstants.LLM_ERROR_RESPONSE;
        }
    }
    
    /**
     * Release the LLM resources
     */
    public void releaseLlm() {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeReleaseLlm");
            return;
        }
        try {
            bridgeInstance.nativeReleaseLlm();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeReleaseLlm() not available: " + e.getMessage());
        }
    }
    
    /**
     * Reset the LLM state
     */
    public boolean resetLlm() {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeResetLlm");
            return false;
        }
        try {
            return bridgeInstance.nativeResetLlm();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeResetLlm() not available: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Swap the model with the given token size
     */
    public boolean swapModel(int tokenSize) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeSwapModel");
            return false;
        }
        try {
            return bridgeInstance.nativeSwapModel(tokenSize);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeSwapModel() not available: " + e.getMessage());
            return false;
        }
    }
} 