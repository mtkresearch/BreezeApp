package com.mtkresearch.gai_android.service;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.pytorch.executorch.LlamaModule;
import org.pytorch.executorch.LlamaCallback;
import com.executorch.ModelUtils;
import com.executorch.PromptFormat;
import com.executorch.ModelType;
import com.mtkresearch.gai_android.utils.ChatMessage;
import com.mtkresearch.gai_android.utils.ConversationManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

public class LLMEngineService extends BaseEngineService {
    private static boolean MTK_BACKEND_AVAILABLE = false;
    
    static {
        try {
            System.loadLibrary("llm_jni");
            MTK_BACKEND_AVAILABLE = true;
            Log.d("LLMEngineService", "Successfully loaded llm_jni library");
        } catch (UnsatisfiedLinkError e) {
            MTK_BACKEND_AVAILABLE = false;
            Log.w("LLMEngineService", "Failed to load llm_jni library, MTK backend will be disabled", e);
        }
    }

    // Add static method to check MTK backend availability
    public static boolean isMTKBackendAvailable() {
        return MTK_BACKEND_AVAILABLE;
    }

    private static final String TAG = "LLMEngineService";
    private static final long INIT_TIMEOUT_MS = 120000;
    private static final long GENERATION_TIMEOUT_MS = 60000;  // Increased timeout
    public static final String DEFAULT_ERROR_RESPONSE = "[!!!] LLM engine backend failed";
    private String backend = "none";
    private String preferredBackend = "cpu";  // Default to CPU backend
    
    // Local CPU backend (LlamaModule)
    private LlamaModule mModule = null;
    private static final String TOKENIZER_PATH = "/data/local/tmp/llama/tokenizer.bin";
    private static final float TEMPERATURE = 0.8f;
    private String modelPath = null;  // Will be set from intent
    private CompletableFuture<String> currentResponse = new CompletableFuture<>();
    private StreamingResponseCallback currentCallback = null;
    private StringBuilder currentStreamingResponse = new StringBuilder();
    private boolean hasSeenAssistantMarker = false;
    private Executor executor;
    private AtomicBoolean isGenerating = new AtomicBoolean(false);
    private final ConversationManager conversationManager;

    // Add method to get model name
    public String getModelName() {
        return com.mtkresearch.gai_android.utils.ModelUtils.getModelDisplayString(modelPath, backend);
    }

    public LLMEngineService() {
        this.conversationManager = new ConversationManager();
    }

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
        if (intent != null) {
            if (intent.hasExtra("model_path")) {
                modelPath = intent.getStringExtra("model_path");
                Log.d(TAG, "Using model path: " + modelPath);
            }
            if (intent.hasExtra("preferred_backend")) {
                String newBackend = intent.getStringExtra("preferred_backend");
                if (!newBackend.equals(preferredBackend)) {
                    preferredBackend = newBackend;
                    // Force reinitialization if backend changed
                    releaseResources();
                    isInitialized = false;
                }
                Log.d(TAG, "Setting preferred backend to: " + preferredBackend);
            }
        }
        
        if (modelPath == null) {
            Log.e(TAG, "No model path provided in intent");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Always release existing resources before initialization
                releaseResources();
                
                // Try MTK backend only if it's preferred
                if (preferredBackend.equals("mtk")) {
                    if (initializeMTKBackend()) {
                        backend = "mtk";
                        isInitialized = true;
                        Log.d(TAG, "Successfully initialized MTK backend");
                        return true;
                    }
                    Log.w(TAG, "MTK backend initialization failed");
                }

                // Try CPU backend if MTK failed or CPU is preferred
                if (preferredBackend.equals("cpu") || preferredBackend.equals("localCPU")) {
                    if (initializeLocalCPUBackend()) {
                        backend = "localCPU";
                        isInitialized = true;
                        Log.d(TAG, "Successfully initialized CPU backend");
                        return true;
                    }
                    Log.w(TAG, "CPU backend initialization failed");
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

            if (mModule != null) {
                mModule.resetNative();
                mModule = null;
            }

            if (modelPath == null) {
                Log.e(TAG, "Model path is null, cannot initialize");
                return false;
            }

            // Initialize LlamaModule with model parameters
            mModule = new LlamaModule(
                ModelUtils.getModelCategory(ModelType.LLAMA_3_2),  // Using LLAMA_3_2 model type
                modelPath,
                TOKENIZER_PATH,
                TEMPERATURE
            );

            // Load the model
            int loadResult = mModule.load();
            if (loadResult != 0) {
                Log.e(TAG, "Failed to load model: " + loadResult);
                return false;
            }

            Log.d(TAG, "Local CPU backend initialized successfully");
            return true;
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
                        
                        // Calculate sequence length based on prompt length
                        int seqLen = (int)(prompt.length() * 0.75) + 64;
                        
                        executor.execute(() -> {
                            mModule.generate(prompt, seqLen, new LlamaCallback() {
                                @Override
                                public void onResult(String result) {
                                    if (result.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2))) {
                                        return;
                                    }
                                    currentStreamingResponse.append(result);
                                }

                                @Override
                                public void onStats(float tps) {
                                    Log.d(TAG, String.format("Generation speed: %.2f tokens/sec", tps));
                                }
                            }, false);
                        });
                        
                        return future.get(GENERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
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
            if (callback != null) {
                callback.onToken(DEFAULT_ERROR_RESPONSE);
            }
            return CompletableFuture.completedFuture(DEFAULT_ERROR_RESPONSE);
        }

        hasSeenAssistantMarker = false;
        currentCallback = callback;
        currentResponse = new CompletableFuture<>();
        currentStreamingResponse.setLength(0);
        isGenerating.set(true);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                switch (backend) {
                    case "mtk":
                        try {
                            // MTK backend uses raw prompt without formatting
                            executor.execute(() -> {
                                try {
                                    String response = nativeStreamingInference(prompt, 256, false, new TokenCallback() {
                                        @Override
                                        public void onToken(String token) {
                                            if (callback != null && isGenerating.get()) {
                                                callback.onToken(token);
                                                currentStreamingResponse.append(token);
                                            }
                                        }
                                    });
                                    
                                    // Only complete if we haven't been stopped
                                    if (isGenerating.get()) {
                                        currentResponse.complete(response);
                                    }
                                    
                                    // Clean up MTK state
                                    try {
                                        nativeResetLlm();
                                        nativeSwapModel(128);
                                    } catch (Exception e) {
                                        Log.e(TAG, "Error resetting MTK state after generation", e);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in MTK streaming generation", e);
                                    if (!currentResponse.isDone()) {
                                        currentResponse.completeExceptionally(e);
                                    }
                                } finally {
                                    isGenerating.set(false);
                                }
                            });
                            
                            return currentResponse.get(GENERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in MTK streaming response", e);
                            throw e;
                        }
                        
                    case "localCPU":
                        // Only apply prompt formatting for local CPU backend
                        Log.d(TAG, "Formatted prompt for local CPU: " + prompt);
                        
                        // Calculate sequence length based on prompt length
                        int seqLen = (int)(prompt.length() * 0.75) + 256;  // Increased for longer context
                        
                        executor.execute(() -> {
                            try {
                                mModule.generate(prompt, seqLen, new LlamaCallback() {
                                    @Override
                                    public void onResult(String token) {
                                        if (!isGenerating.get()) {
                                            return;
                                        }

                                        if (token == null || token.isEmpty()) {
                                            return;
                                        }

                                        // Handle both stop tokens - filter out both EOS tokens
                                        if (token.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2)) ||
                                            token.equals("<|end_of_text|>") || token.equals("<|eot_id|>")) {
                                            Log.d(TAG, "Stop token detected: " + token);
                                            completeGeneration();
                                            return;
                                        }

                                        // Process token
                                        if (callback != null) {
                                            callback.onToken(token);
                                        }
                                        currentStreamingResponse.append(token);
                                    }

                                    @Override
                                    public void onStats(float tps) {
                                        Log.d(TAG, String.format("Generation speed: %.2f tokens/sec", tps));
                                    }
                                }, false);
                                
                                // Complete generation when finished
                                completeGeneration();
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error during generation", e);
                                if (!currentResponse.isDone()) {
                                    currentResponse.completeExceptionally(e);
                                }
                            } finally {
                                isGenerating.set(false);
                            }
                        });
                        
                        return currentResponse.get(GENERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        
                    default:
                        if (callback != null) {
                            callback.onToken(DEFAULT_ERROR_RESPONSE);
                        }
                        currentResponse.complete(DEFAULT_ERROR_RESPONSE);
                        return DEFAULT_ERROR_RESPONSE;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming response", e);
                if (callback != null) {
                    callback.onToken(DEFAULT_ERROR_RESPONSE);
                }
                currentResponse.complete(DEFAULT_ERROR_RESPONSE);
                return DEFAULT_ERROR_RESPONSE;
            }
        });
    }

    private void completeGeneration() {
        if (isGenerating.compareAndSet(true, false)) {
            String finalResponse = currentStreamingResponse.toString();
            if (currentResponse != null && !currentResponse.isDone()) {
                currentResponse.complete(finalResponse);
            }
            // Clean up resources
            currentCallback = null;
            System.gc(); // Request garbage collection for any lingering resources
        }
    }

    public void stopGeneration() {
        isGenerating.set(false);
        
        if (backend.equals("mtk")) {
            try {
                nativeResetLlm();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MTK generation", e);
            }
        } else if (mModule != null) {
            try {
                mModule.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping CPU generation", e);
            }
        }

        if (currentResponse != null && !currentResponse.isDone()) {
            String finalResponse = currentStreamingResponse.toString();
            if (finalResponse.isEmpty()) {
                finalResponse = "[Generation stopped by user]";
            }
            currentResponse.complete(finalResponse);
        }
        
        // Clean up resources
        currentCallback = null;
        System.gc();
    }

    public void releaseResources() {
        try {
            stopGeneration();
            
            // Release MTK resources if using MTK backend
            if (backend.equals("mtk")) {
                try {
                    nativeResetLlm();
                    nativeReleaseLlm();
                    Log.d(TAG, "Released MTK resources");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing MTK resources", e);
                }
            }
            
            // Release CPU resources if using CPU backend
            if (mModule != null) {
                try {
                    mModule.resetNative();
                    mModule = null;
                    Log.d(TAG, "Released CPU resources");
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing CPU resources", e);
                }
            }
            
            // Reset state
            backend = "none";
            isInitialized = false;
            System.gc(); // Request garbage collection
            
            Log.d(TAG, "All resources released");
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseResources();
        if (executor != null) {
            executor.execute(() -> {});
            executor = null;
        }
    }

    public String getCurrentBackend() {
        return backend;
    }

    public String getPreferredBackend() {
        return preferredBackend;
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