package com.mtkresearch.breeze_app.service;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.pytorch.executorch.LlamaModule;
import org.pytorch.executorch.LlamaCallback;
import com.executorch.ModelUtils;
import com.executorch.PromptFormat;
import com.executorch.ModelType;
import com.mtkresearch.breeze_app.utils.ConversationManager;
import com.mtkresearch.breeze_app.utils.AppConstants;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class LLMEngineService extends BaseEngineService {
    private static final String TAG = "LLMEngineService";
    
    // Service state
    private String currentBackend = AppConstants.BACKEND_NONE;
    private boolean hasSeenAssistantMarker = false;
    private final ConversationManager conversationManager;
    
    // Generation state
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private CompletableFuture<String> currentResponse = new CompletableFuture<>();
    private StreamingResponseCallback currentCallback = null;
    private final StringBuilder currentStreamingResponse = new StringBuilder();
    private ExecutorService executor;
    
    // CPU backend (LlamaModule)
    private LlamaModule mModule = null;
    private String modelPath = null;  // Set from intent

    public String getModelName() {
        if (modelPath == null) {
            return "Unknown";
        }
        return com.mtkresearch.breeze_app.utils.ModelUtils.getModelDisplayName(modelPath);
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
            } else {
                // Use AppConstants to get the correct model path
                modelPath = AppConstants.getModelPath(this);
                Log.d(TAG, "Using default model path: " + modelPath);
            }
        }
        
        // Check if model needs to be downloaded
        if (AppConstants.needsModelDownload(this)) {
            Log.e(TAG, "Model not found in any location, download required");
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
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Create a timeout future
        CompletableFuture.delayedExecutor(AppConstants.LLM_INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .execute(() -> {
                if (!future.isDone()) {
                    future.complete(false);
                    Log.e(TAG, "Initialization timed out");
                }
            });
        
        // Run initialization in background
        CompletableFuture.supplyAsync(() -> {
            try {
                // Always release existing resources before initialization
                releaseResources();
                
                // Only CPU backend is available
                if (initializeLocalCPUBackend()) {
                    currentBackend = AppConstants.BACKEND_CPU;
                    isInitialized = true;
                    Log.d(TAG, "Successfully initialized CPU backend");
                    future.complete(true);
                    return true;
                }
                
                Log.e(TAG, "CPU backend initialization failed");
                future.complete(false);
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error during initialization", e);
                future.completeExceptionally(e);
                return false;
            }
        });
        
        return future;
    }

    private boolean initializeLocalCPUBackend() {
        try {
            Log.d(TAG, "Initializing local CPU backend...");
            String tokenizerPath = AppConstants.getTokenizerPath(this);
            
            Log.d(TAG, "Creating LlamaModule with model path: " + modelPath);
            Log.d(TAG, "Tokenizer path: " + tokenizerPath);
            
            float temperature = AppConstants.LLM_TEMPERATURE;
            
            // Initialize LlamaModule with the model path, tokenizer path, and temperature
            mModule = new LlamaModule(
                LlamaModule.MODEL_TYPE_TEXT,
                modelPath,
                tokenizerPath,
                temperature
            );
            
            // Load model - returns 0 on success, not a boolean
            int loadResult = mModule.load();
            if (loadResult != 0) {
                Log.e(TAG, "Failed to load model: " + loadResult);
                return false;
            }
            
            Log.i(TAG, "Successfully loaded model with LlamaModule");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing CPU backend", e);
            cleanupAfterError();
            return false;
        }
    }

    private void cleanupAfterError() {
        if (mModule != null) {
            try {
                mModule.resetNative();
            } catch (Exception e) {
                Log.e(TAG, "Error during module reset", e);
            }
            mModule = null;
        }
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        if (!isInitialized || !isGenerating.compareAndSet(false, true)) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException(
                "Engine not initialized or generation already in progress"));
            return future;
        }
        
        try {
            // Reset state for new generation
            currentStreamingResponse.setLength(0);
            hasSeenAssistantMarker = false;
            
            Log.d(TAG, "Generating response using backend: " + currentBackend);
            
            switch (currentBackend) {
                case AppConstants.BACKEND_CPU:
                    try {
                        // Calculate sequence length based on prompt length, matching original implementation
                        int seqLen = (int)(prompt.length() * 0.75) + 64;  // Original Llama runner formula
                        
                        CompletableFuture<String> future = new CompletableFuture<>();
                        currentResponse = future;
                        
                        executor.execute(() -> {
                            try {
                                mModule.generate(prompt, seqLen, new LlamaCallback() {
                                    @Override
                                    public void onResult(String result) {
                                        if (!isGenerating.get() || 
                                            result.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2))) {
                                            return;
                                        }
                                        currentStreamingResponse.append(result);
                                    }

                                    @Override
                                    public void onStats(float tps) {
                                        Log.d(TAG, String.format("Generation speed: %.2f tokens/sec", tps));
                                    }
                                }, false);
                                
                                // Only complete if we haven't been stopped
                                if (isGenerating.get()) {
                                    currentResponse.complete(currentStreamingResponse.toString());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in CPU generation", e);
                                if (!currentResponse.isDone()) {
                                    currentResponse.completeExceptionally(e);
                                }
                            } finally {
                                completeGeneration();
                            }
                        });
                        
                        return future;
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting CPU generation", e);
                        completeGeneration();
                        throw e;
                    }
                default:
                    completeGeneration();
                    throw new IllegalStateException("Invalid backend: " + currentBackend);
            }
        } catch (Exception e) {
            completeGeneration();
            CompletableFuture<String> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    public CompletableFuture<String> generateStreamingResponse(String prompt, StreamingResponseCallback callback) {
        if (!isInitialized || !isGenerating.compareAndSet(false, true) || callback == null) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException(
                "Engine not initialized, generation already in progress, or callback is null"));
            return future;
        }
        
        try {
            // Store callback for token delivery
            currentCallback = callback;
            
            // Reset state for new generation
            currentStreamingResponse.setLength(0);
            hasSeenAssistantMarker = false;
            
            CompletableFuture<String> future = new CompletableFuture<>();
            currentResponse = future;
            
            Log.d(TAG, "Generating streaming response using backend: " + currentBackend);
            
            switch (currentBackend) {
                case AppConstants.BACKEND_CPU:
                    try {
                        // Calculate sequence length based on prompt length
                        int seqLen = Math.max((int)(prompt.length() * 0.75) + 256, 512);
                        
                        executor.execute(() -> {
                            try {
                                mModule.generate(prompt, seqLen, new LlamaCallback() {
                                    public void onToken(String token) {
                                        if (!isGenerating.get()) {
                                            return;
                                        }
                                        
                                        // Check for stop token
                                        if (token.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2))) {
                                            if (currentCallback != null) {
                                                currentCallback.onToken(token);
                                            }
                                            
                                            // Stop module and complete response
                                            mModule.resetNative();
                                            completeGeneration();
                                            currentResponse.complete(currentStreamingResponse.toString());
                                            return;
                                        }
                                        
                                        // Skip special tokens for better UX
                                        if (token.startsWith("<") && token.endsWith(">")) {
                                            return;
                                        }
                                        
                                        // Deliver token to callback
                                        if (currentCallback != null) {
                                            currentCallback.onToken(token);
                                        }
                                        
                                        // Append to full response
                                        currentStreamingResponse.append(token);
                                    }

                                    @Override
                                    public void onResult(String token) {
                                        if (!isGenerating.get()) {
                                            return;
                                        }
                                        
                                        // Check if this is a stop token
                                        if (token.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2))) {
                                            completeGeneration();
                                            currentResponse.complete(currentStreamingResponse.toString());
                                            return;
                                        }
                                        
                                        // Skip special tokens
                                        if (token.startsWith("<") && token.endsWith(">")) {
                                            if (token.contains("assistant")) {
                                                hasSeenAssistantMarker = true;
                                            }
                                            return;
                                        }
                                        
                                        // Only add to response after seeing assistant marker
                                        if (hasSeenAssistantMarker && currentCallback != null) {
                                            currentCallback.onToken(token);
                                            currentStreamingResponse.append(token);
                                        }
                                    }

                                    @Override
                                    public void onStats(float tps) {
                                        Log.d(TAG, String.format("Generation speed: %.2f tokens/sec", tps));
                                    }
                                }, true);
                                
                                // Only complete if we haven't been stopped already
                                if (isGenerating.get()) {
                                    completeGeneration();
                                    currentResponse.complete(currentStreamingResponse.toString());
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error in CPU streaming generation", e);
                                completeGeneration();
                                if (!currentResponse.isDone()) {
                                    currentResponse.completeExceptionally(e);
                                }
                            }
                        });
                        
                        return future;
                    } catch (Exception e) {
                        Log.e(TAG, "Error starting CPU streaming generation", e);
                        completeGeneration();
                        throw e;
                    }
                default:
                    completeGeneration();
                    throw new IllegalStateException("Invalid backend: " + currentBackend);
            }
        } catch (Exception e) {
            completeGeneration();
            CompletableFuture<String> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(e);
            return errorFuture;
        }
    }

    private void completeGeneration() {
        isGenerating.set(false);
        currentCallback = null;
    }

    public void stopGeneration() {
        if (isGenerating.getAndSet(false)) {
            Log.d(TAG, "Stopping generation");
            
            // Cancel pending response if we were generating
            if (!currentResponse.isDone()) {
                currentResponse.cancel(true);
            }
            
            try {
                switch (currentBackend) {
                    case AppConstants.BACKEND_CPU:
                        if (mModule != null) {
                            mModule.resetNative();
                        }
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping generation", e);
            } finally {
                currentCallback = null;
            }
        }
    }

    public void releaseResources() {
        // Stop any ongoing generation
        stopGeneration();
        
        // Release resources based on current backend
        try {
            switch (currentBackend) {
                case AppConstants.BACKEND_CPU:
                    if (mModule != null) {
                        mModule.resetNative();
                        mModule = null;
                    }
                    break;
            }
            
            // Reset backend state
            currentBackend = AppConstants.BACKEND_NONE;
            isInitialized = false;
            
            Log.d(TAG, "Successfully released resources");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing resources", e);
        }
    }

    @Override
    public void onDestroy() {
        releaseResources();
        
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        
        super.onDestroy();
    }

    public String getCurrentBackend() {
        return currentBackend;
    }

    private int getMaxSequenceLength() {
        return AppConstants.getLLMMaxSeqLength(this);
    }
    
    private int getMinOutputLength() {
        return AppConstants.getLLMMinOutputLength(this);
    }
} 