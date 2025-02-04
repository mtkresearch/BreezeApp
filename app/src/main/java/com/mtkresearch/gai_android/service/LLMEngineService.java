package com.mtkresearch.gai_android.service;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.executorch.Executorch;
import com.executorch.PromptFormat;
import com.executorch.SettingsFields;
import com.executorch.ModelType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;


public class LLMEngineService extends BaseEngineService {
//    static {
//        System.loadLibrary("llm_jni");
//    }

    private static final String TAG = "LLMEngineService";
    private static final long INIT_TIMEOUT_MS = 120000;
    private static final long GENERATION_TIMEOUT_MS = 30000;
    private static final boolean MTK_BACKEND_AVAILABLE = false;
    private static final String DEFAULT_ERROR_RESPONSE = "[!!!] LLM engine backend failed";
    private String backend = "none";
    
    // Local CPU backend (executorch)
    private Executorch mExecutorch = null;
    private static final String TOKENIZER_PATH = "/data/local/tmp/llama/tokenizer.bin";
    private static final double TEMPERATURE = 0.8f;
    private String modelPath = null;  // Will be set from intent
    private CompletableFuture<String> currentResponse = new CompletableFuture<>();
    private StreamingResponseCallback currentCallback = null;
    private StringBuilder currentStreamingResponse = new StringBuilder();
    private boolean hasSeenAssistantMarker = false;

    public class LocalBinder extends BaseEngineService.LocalBinder<LLMEngineService> { }

    public interface StreamingResponseCallback {
        void onToken(String token);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.hasExtra("model_path")) {
            modelPath = intent.getStringExtra("model_path");
            Log.d(TAG, "Using model path: " + modelPath);
        } else {
            Log.e(TAG, "No model path provided in intent");
            stopSelf();
            return START_NOT_STICKY;
        }
        return super.onStartCommand(intent, flags, startId);
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

            if (mExecutorch != null) {
                mExecutorch.release();
            }

            if (modelPath == null) {
                Log.e(TAG, "Model path is null, cannot initialize");
                return false;
            }

            SettingsFields settings = new SettingsFields();
            settings.saveModelPath(modelPath);
            settings.saveTokenizerPath(TOKENIZER_PATH);
            settings.saveModelType(ModelType.LLAMA_3_2);  // Using default model type
            settings.saveParameters(TEMPERATURE);
            settings.saveLoadModelAction(true);

            CompletableFuture<Boolean> initFuture = new CompletableFuture<>();

            mExecutorch = new Executorch(getApplicationContext(), settings, new Executorch.ExecutorCallback() {
                @Override
                public void onInitialized(boolean success) {
                    initFuture.complete(success);
                }

                @Override
                public void onGenerating(String token) {
                    if (currentCallback != null) {
                        // Skip stop tokens
                        if (token.equals(PromptFormat.getStopToken(settings.getModelType()))) {
                            return;
                        }

                        // Check for assistant marker based on model type
                        if (!hasSeenAssistantMarker) {
                            if (settings.getModelType() == ModelType.LLAMA_3 || 
                                settings.getModelType() == ModelType.LLAMA_3_1 || 
                                settings.getModelType() == ModelType.LLAMA_3_2 ||
                                settings.getModelType() == ModelType.LLAMA_GUARD_3) {
                                if (token.contains("<|start_header_id|>assistant<|end_header_id|>")) {
                                    hasSeenAssistantMarker = true;
                                }
                                return; // Skip all tokens until we see assistant marker
                            } else if (settings.getModelType() == ModelType.LLAVA_1_5) {
                                if (token.contains("ASSISTANT:")) {
                                    hasSeenAssistantMarker = true;
                                }
                                return; // Skip all tokens until we see ASSISTANT: marker
                            }
                        }
                        
                        // Only process tokens after we've seen the assistant marker
                        if (hasSeenAssistantMarker) {
                            if (token.equals("\n\n") || token.equals("\n")) {
                                if (currentStreamingResponse.length() > 0) {
                                    currentStreamingResponse.append(token);
                                    currentCallback.onToken(token);
                                }
                            } else {
                                currentStreamingResponse.append(token);
                                currentCallback.onToken(token);
                            }
                        }
                    }
                }

                @Override
                public void onGenerationComplete() {
                    if (currentResponse != null) {
                        currentResponse.complete(currentStreamingResponse.toString());
                        currentStreamingResponse.setLength(0);  // Reset for next use
                    }
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Executorch error: " + error);
                    if (currentResponse != null) {
                        currentResponse.completeExceptionally(new RuntimeException(error));
                    }
                    currentStreamingResponse.setLength(0);  // Reset on error
                }

                @Override
                public void onMetrics(float tokensPerSecond, long totalTime) {
                    Log.d(TAG, String.format("Generation metrics: %.2f tokens/sec, %d ms total", 
                        tokensPerSecond, totalTime));
                }
            });

            mExecutorch.initialize();
            return initFuture.get(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Local CPU backend", e);
            return false;
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
                        String response = nativeInference(prompt, 256, false);
                        nativeResetLlm();
                        nativeSwapModel(128);
                        return response;
                    case "localCPU":
                        CompletableFuture<String> future = new CompletableFuture<>();
                        currentResponse = future;
                        mExecutorch.generate(prompt);
                        return future.get(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
        hasSeenAssistantMarker = false;
        currentCallback = callback;
        currentResponse = new CompletableFuture<>();
        currentStreamingResponse.setLength(0);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (backend) {
                    case "mtk":
                        String response = nativeStreamingInference(prompt, 256, false, new TokenCallback() {
                            @Override
                            public void onToken(String token) {
                                callback.onToken(token);
                            }
                        });
                        nativeResetLlm();
                        nativeSwapModel(128);
                        return response;
                    case "localCPU":
                        mExecutorch.generate(prompt);
                        return currentResponse.get(INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    default:
                        callback.onToken(DEFAULT_ERROR_RESPONSE);
                        return DEFAULT_ERROR_RESPONSE;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming response", e);
                callback.onToken(DEFAULT_ERROR_RESPONSE);
                return DEFAULT_ERROR_RESPONSE;
            }
        });
    }

    public void releaseResources() {
        try {
            if (backend.equals("mtk")) {
                nativeReleaseLlm();
            } else if (backend.equals("localCPU") && mExecutorch != null) {
                mExecutorch.release();
                mExecutorch = null;
            }
            backend = "none";
            isInitialized = false;
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseResources();
    }

    // Native methods for MTK backend
    private native boolean nativeInitLlm(String yamlConfigPath, boolean preloadSharedWeights);
    private native String nativeInference(String inputString, int maxResponse, boolean parsePromptTokens);
    private native String nativeStreamingInference(String inputString, int maxResponse, boolean parsePromptTokens, TokenCallback callback);
    private native void nativeReleaseLlm();
    private native boolean nativeResetLlm();
    private native boolean nativeSwapModel(int tokenSize);

    public interface TokenCallback {
        void onToken(String token);
    }
} 