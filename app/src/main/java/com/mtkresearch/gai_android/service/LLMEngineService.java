package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import javax.security.auth.callback.Callback;

public class LLMEngineService extends BaseEngineService {
//    static {
//        System.loadLibrary("llm_jni");
//    }

    private static final String TAG = "LLMEngineService";
    private static final long INIT_TIMEOUT_MS = 30000;
    private static final boolean MTK_BACKEND_AVAILABLE = false;
    private static final String DEFAULT_ERROR_RESPONSE = "[!!!] LLM engine backend failed";
    private String backend = "none";

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
        if (isInitialized) {
            return CompletableFuture.completedFuture(true);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                if (initializeMTKBackend()) {
                    backend = "mtk";
                    isInitialized = true;
                    return true;
                }
                
                if (initializeLocalBackend()) {
                    backend = "local";
                    isInitialized = true;
                    return true;
                }
                
                if (initializeLocalCPUBackend()) {
                    backend = "localCPU";
                    isInitialized = true;
                    return true;
                }

                Log.e(TAG, "All backend initialization attempts failed");
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error during initialization", e);
                return false;
            }
        });
    }

    private boolean initializeMTKBackend() {
        if (!MTK_BACKEND_AVAILABLE) {
            Log.d(TAG, "MTK backend disabled, skipping");
            return false;
        }

        try {
            Log.d(TAG, "Attempting MTK backend initialization...");
            return nativeInitLlm("/data/local/tmp/llm_sdk/config_breezetiny_3b_instruct.yaml", false);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MTK backend", e);
            return false;
        }
    }

    private boolean initializeLocalBackend() {
        try {
            Log.d(TAG, "Attempting Local GPU backend initialization...");
            // Add your local GPU initialization code here
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Local GPU backend", e);
            return false;
        }
    }

    private boolean initializeLocalCPUBackend() {
        try {
            Log.d(TAG, "Attempting Local CPU backend initialization...");
            // Add your local CPU initialization code here
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Local CPU backend", e);
            return false;
        }
    }

    @Override
    public boolean isReady() {
        return isInitialized;
    }

    protected void cleanupEngine() {
        try {
            if (backend.equals("mtk")) {
                nativeReleaseLlm();
            }
            backend = "none";
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        if (!isInitialized) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Engine not initialized"));
            return future;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (backend) {
                    case "mtk":
                        return generateMTKResponse(prompt).get();
                    case "localCPU":
                        return generateLocalCPUResponse(prompt).get();
                    default:
                        return DEFAULT_ERROR_RESPONSE;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error generating response", e);
                return DEFAULT_ERROR_RESPONSE;
            }
        });
    }

    public CompletableFuture<String> generateStreamingResponse(String prompt, StreamingResponseCallback callback) {
        if (!isInitialized) {
            // Even when not initialized, we should send the default error response
            callback.onToken(DEFAULT_ERROR_RESPONSE);  // Send error message as a token
            return CompletableFuture.completedFuture(DEFAULT_ERROR_RESPONSE);
        }

        switch (backend) {
            case "mtk":
                return generateMTKStreamingResponse(prompt, callback);
            case "localCPU":
                return generateLocalCPUStreamingResponse(prompt, callback);
            default:
                callback.onToken(DEFAULT_ERROR_RESPONSE);
                return CompletableFuture.completedFuture(DEFAULT_ERROR_RESPONSE);
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
            try {
                String response = nativeStreamingInference(prompt, 256, false, new TokenCallback() {
                    @Override
                    public void onToken(String token) {
                        callback.onToken(token);
                    }
                });
                nativeResetLlm();
                nativeSwapModel(128);
                return response;
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming response", e);
                return DEFAULT_ERROR_RESPONSE;
            }
        });
    }

    private CompletableFuture<String> generateLocalCPUResponse(String prompt) {
        // Add local CPU inference implementation
        return CompletableFuture.completedFuture("Local CPU not implemented");
    }

    private CompletableFuture<String> generateLocalCPUStreamingResponse(String prompt, StreamingResponseCallback callback) {
        // Add local CPU inference implementation
        return CompletableFuture.completedFuture("Local CPU not implemented");
    }

    public void releaseResources() {
        if (isInitialized) {
            switch (backend) {
                case "mtk":
                    nativeReleaseLlm();
                case "local":
                    // TODO
                case "localCPU":
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