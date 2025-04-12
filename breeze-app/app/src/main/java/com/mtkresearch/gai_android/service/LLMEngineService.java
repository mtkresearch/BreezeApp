package com.mtkresearch.gai_android.service;

import android.util.Log;
import com.mtkresearch.breeze_app.utils.AppConstants;

/**
 * This is a JNI bridge class that matches the package name expected by the native code.
 * It delegates all calls to the Breeze app's actual implementation.
 */
public class LLMEngineService {
    private static final String TAG = "LLMEngineBridge";
    private static com.mtkresearch.breeze_app.service.LLMEngineService INSTANCE;

    // Register the singleton instance from the actual implementation
    public static void registerInstance(com.mtkresearch.breeze_app.service.LLMEngineService instance) {
        INSTANCE = instance;
        Log.d(TAG, "Registered LLMEngineService instance from Breeze app");
    }

    // Native method declarations - these must match the signatures in the native code
    public native boolean nativeInitLlm(String yamlConfigPath, boolean preloadSharedWeights);
    public native String nativeInference(String inputString, int maxResponse, boolean parsePromptTokens);
    public native String nativeStreamingInference(String inputString, int maxResponse, boolean parsePromptTokens, TokenCallback callback);
    public native void nativeReleaseLlm();
    public native boolean nativeResetLlm();
    public native boolean nativeSwapModel(int tokenSize);

    // Token callback interface
    public interface TokenCallback {
        void onToken(String token);
    }

    // This class doesn't actually need to be instantiated since it's just a JNI bridge
    public LLMEngineService() {}
} 