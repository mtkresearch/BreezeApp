package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CompletableFuture;

public class LLMEngineService extends BaseEngineService {
    static {
        System.loadLibrary("llm_jni");
    }

    private static final String TAG = "LLMEngineService";

    public class LocalBinder extends BaseEngineService.LocalBinder<LLMEngineService> { }

    public interface StreamingResponseCallback {
        void onToken(String token);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                nativeInitLlm("/data/local/tmp/llm_sdk/config_breezetiny_3b_instruct.yaml", false);
                this.backend = "mtk";
                Log.d(TAG, "Initializing LLM with backend: " + backend);
                // Initialize LLM model and resources
                isInitialized = true;
                Log.d(TAG, "LLM initialization successful");
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize LLM", e);
                return false;
            }
        });
    }

    @Override
    public boolean isReady() {
        return isInitialized;
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        if (!isInitialized) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Engine not initialized"));
            return future;
        }

        switch (backend) {
            case "mtk":
                return generateMTKResponse(prompt);
            case "openai":
                return generateOpenAIResponse(prompt);
            default:
                return CompletableFuture.completedFuture("I'm currently unavailable.");
        }
    }

    public CompletableFuture<String> generateStreamingResponse(String prompt, StreamingResponseCallback callback) {
        if (!isInitialized) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Engine not initialized"));
            return future;
        }

        switch (backend) {
            case "mtk":
                return generateMTKStreamingResponse(prompt, callback);
            case "openai":
                return generateOpenAIResponse(prompt); // Implement streaming for OpenAI if needed
            default:
                return CompletableFuture.completedFuture("I'm currently unavailable.");
        }
    }

    private CompletableFuture<String> generateMTKResponse(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            String response = nativeInference(prompt, 256, false);
            nativeResetLlm();
            nativeSwapModel(128);
            return response;
        });
    }

    private CompletableFuture<String> generateMTKStreamingResponse(String prompt, StreamingResponseCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            String response = nativeStreamingInference(prompt, 256, false, new TokenCallback() {
                @Override
                public void onToken(String token) {
                    callback.onToken(token);
                }
            });
            nativeResetLlm();
            nativeSwapModel(128);
            return response;
        });
    }

    private CompletableFuture<String> generateOpenAIResponse(String prompt) {
        return CompletableFuture.completedFuture("OpenAI response");
    }

    public void releaseResources() {
        if (isInitialized) {
            switch (backend) {
                case "mtk":
                    nativeReleaseLlm();
                case "openai":
                    // TODO
                default:
                    // TODO
            }

            isInitialized = false;
        }
    }

    // ======================= MTK backend service =======================
    // Existing native methods
    public native boolean nativeInitLlm(String yamlConfigPath, boolean preloadSharedWeights);
//    public native String nativeGenResponse(String inputString, int maxResponse, int firstInputToken);
    public native String nativeInference(String inputString, int maxResponse, boolean parsePromptTokens);
    public native void nativeReleaseLlm();

    // New native methods for reset and model swap
    public native boolean nativeResetLlm();
    public native boolean nativeSwapModel(int tokenSize);

    // New native method for streaming inference
    public native String nativeStreamingInference(String inputString, int maxResponse, boolean parsePromptTokens, TokenCallback callback);

    // Interface for token callback
    public interface TokenCallback {
        void onToken(String token);
    }
} 