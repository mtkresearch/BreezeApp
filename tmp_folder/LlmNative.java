package com.mtkresearch.gai_android.service;

import com.example.llmapp.data.model.InferenceResult;

public class LlmNative {
    static {
        setLibraryPath("/data/local/tmp/llm_sdk/");

        // Load the remaining shared libraries
        System.loadLibrary("common");
        System.loadLibrary("mtk_llm");
        System.loadLibrary("tokenizer");
        System.loadLibrary("yaml-cpp");
        System.loadLibrary("main_llm");
        System.loadLibrary("re2");
        System.loadLibrary("sentencepiece");
        System.loadLibrary("llm_jni");
    }

    // Existing native methods
    public native boolean nativeInitLlm(String yamlConfigPath, boolean preloadSharedWeights);
    public native InferenceResult nativeGenResponse(String inputString, int maxResponse, int firstInputToken);
    public native InferenceResult nativeInference(String inputString, int maxResponse, boolean parsePromptTokens);
    public native void nativeReleaseLlm();

    // New native methods for reset and model swap
    public native boolean nativeResetLlm();
    public native boolean nativeSwapModel(int tokenSize);

    // New native method for streaming inference
    public native InferenceResult nativeStreamingInference(String inputString, int maxResponse, boolean parsePromptTokens, TokenCallback callback);

    // Native method to set the library path
    private static native void setLibraryPath(String path);

    // Interface for token callback
    public interface TokenCallback {
        void onToken(String token);
    }
}