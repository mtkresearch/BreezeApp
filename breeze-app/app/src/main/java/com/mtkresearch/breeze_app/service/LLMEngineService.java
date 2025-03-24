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
    private String preferredBackend = AppConstants.BACKEND_DEFAULT;
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
                
                // Try CPU backend
                if (initializeCPUBackend()) {
                    currentBackend = AppConstants.BACKEND_CPU;
                    isInitialized = true;
                    Log.d(TAG, "Successfully initialized CPU backend");
                    future.complete(true);
                    return true;
                }
                Log.w(TAG, "CPU backend initialization failed");

                Log.e(TAG, "Backend initialization failed");
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

    private boolean initializeCPUBackend() {
        try {
            Log.d(TAG, "Attempting CPU backend initialization...");

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
                ModelUtils.getModelCategory(ModelType.LLAMA_3_2),
                modelPath,
                AppConstants.getTokenizerPath(this),
                AppConstants.LLM_TEMPERATURE
            );

            // Load the model
            int loadResult = mModule.load();
            if (loadResult != 0) {
                Log.e(TAG, "Failed to load model: " + loadResult);
                return false;
            }

            Log.d(TAG, "CPU backend initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing CPU backend", e);
            return false;
        }
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        if (!isInitialized) {
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
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
                                    isGenerating.set(false);
                                }
                            });
                            
                            return future.get(60000, TimeUnit.MILLISECONDS);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in CPU streaming response", e);
                            throw e;
                        }
                    default:
                        return AppConstants.LLM_ERROR_RESPONSE;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error generating response", e);
                return AppConstants.LLM_ERROR_RESPONSE;
            }
        });
    }

    public CompletableFuture<String> generateStreamingResponse(String prompt, StreamingResponseCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE);
            }
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }

        hasSeenAssistantMarker = false;
        currentCallback = callback;
        currentResponse = new CompletableFuture<>();
        currentStreamingResponse.setLength(0);
        isGenerating.set(true);
        
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        
        CompletableFuture.runAsync(() -> {
            try {
                switch (currentBackend) {
                    case AppConstants.BACKEND_CPU:
                        // Only apply prompt formatting for CPU backend
                        Log.d(TAG, "Formatted prompt for CPU: " + prompt);
                        
                        // Calculate sequence length with more generous output space
                        int seqLen = Math.min(
                            AppConstants.getLLMMaxSeqLength(context),
                            prompt.length() + AppConstants.getLLMMinOutputLength(context)
                        );
                        
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
                                        if (token.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2))) {
                                            Log.d(TAG, "Stop token detected: " + token);
                                            String finalResponse = currentStreamingResponse.toString();
                                            if (!currentResponse.isDone()) {
                                                currentResponse.complete(finalResponse);
                                                resultFuture.complete(finalResponse);
                                            }
                                            isGenerating.set(false);
                                            // Explicitly stop the module when we detect a stop token
                                            try {
                                                mModule.stop();
                                            } catch (Exception e) {
                                                Log.e(TAG, "Error stopping module after stop token", e);
                                            }
                                            return;
                                        }

                                        // Handle streaming response
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
                                
                                // Only complete if we haven't been stopped and have a response
                                if (!currentResponse.isDone() && currentStreamingResponse.length() > 0) {
                                    String finalResponse = currentStreamingResponse.toString();
                                    currentResponse.complete(finalResponse);
                                    resultFuture.complete(finalResponse);
                                }
                                
                            } catch (Exception e) {
                                Log.e(TAG, "Error in CPU streaming generation", e);
                                if (!currentResponse.isDone()) {
                                    currentResponse.completeExceptionally(e);
                                    resultFuture.completeExceptionally(e);
                                }
                            } finally {
                                isGenerating.set(false);
                            }
                        });
                        break;
                        
                    default:
                        String error = "Unsupported backend: " + currentBackend;
                        Log.e(TAG, error);
                        resultFuture.completeExceptionally(new IllegalStateException(error));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming response", e);
                resultFuture.completeExceptionally(e);
            }
        });
        
        return resultFuture;
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
        
        if (mModule != null) {
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
            if (isGenerating.get()) {
                stopGeneration();
            }
            
            if (mModule != null) {
                mModule.resetNative();
                mModule = null;
            }
            
            isInitialized = false;
            currentBackend = AppConstants.BACKEND_NONE;
        } catch (Exception e) {
            Log.e(TAG, "Error releasing resources", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    public String getCurrentBackend() {
        return currentBackend;
    }

    public String getPreferredBackend() {
        return preferredBackend;
    }

    private int getMaxSequenceLength() {
        return AppConstants.getLLMMaxSeqLength(this);
    }

    private int getMinOutputLength() {
        return AppConstants.getLLMMinOutputLength(this);
    }
} 