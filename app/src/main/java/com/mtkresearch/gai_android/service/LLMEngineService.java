package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.executorch.Executorch;

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

    private Executorch executorch;

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
                if (initializeMTKBackend()) {
                    backend = "mtk";
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

    private boolean initializeLocalCPUBackend() {
        try {
            Log.d(TAG, "Attempting Local CPU backend initialization...");
            executorch = new Executorch();
            return executorch.initialize();
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
            switch (backend) {
                case "mtk":
                    nativeReleaseLlm();
                    break;
                case "localCPU":
                    if (executorch != null) {
                        executorch.cleanup();
                        executorch = null;
                    }
                    break;
            }
            backend = "none";
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        if (!isInitialized) {
            return CompletableFuture.completedFuture(DEFAULT_ERROR_RESPONSE);
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
        return executorch.generateResponse(prompt);
    }

    private CompletableFuture<String> generateLocalCPUStreamingResponse(String prompt, StreamingResponseCallback callback) {
        // Add local CPU inference implementation
        return executorch.generateStreamingResponse(prompt, token -> callback.onToken(token));
    }

    public void releaseResources() {
        if (isInitialized) {
            switch (backend) {
                case "mtk":
                    nativeReleaseLlm();
                case "localCPU":
                    if (executorch != null) {
                    executorch.cleanup();
                    executorch = null;
                }
                default:
                    // TODO
            }

            isInitialized = false;
        }
    }

    // Interface for token callback
    public interface TokenCallback {
        void onToken(String token);
    }

    // ======================= MTK backend service =======================
    private native boolean nativeInitLlm(String yamlConfigPath, boolean preloadSharedWeights);
    private native String nativeInference(String inputString, int maxResponse, boolean parsePromptTokens);
    private native String nativeStreamingInference(String inputString, int maxResponse, boolean parsePromptTokens, TokenCallback callback);
    private native void nativeReleaseLlm();
    private native boolean nativeResetLlm();
    private native boolean nativeSwapModel(int tokenSize);
    
} 