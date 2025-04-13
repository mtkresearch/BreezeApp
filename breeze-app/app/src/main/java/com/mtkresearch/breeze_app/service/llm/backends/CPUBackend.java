package com.mtkresearch.breeze_app.service.llm.backends;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import com.mtkresearch.breeze_app.service.llm.LLMBackend;
import com.mtkresearch.breeze_app.utils.AppConstants;
import org.pytorch.executorch.LlamaModule;
import org.pytorch.executorch.LlamaCallback;
import com.executorch.ModelType;
import com.executorch.PromptFormat;
import com.executorch.ModelUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.io.File;

/**
 * CPU-based backend implementation for LLM inference.
 * This backend uses standard CPU processing for model inference.
 */
public class CPUBackend implements LLMBackend {
    private static final String TAG = "CPUBackend";
    private static final String BACKEND_NAME = "CPU";
    private static final float TEMPERATURE = 0.2f; // Default temperature
    
    private final String modelPath;
    private final ExecutorService executorService;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean shouldStop = new AtomicBoolean(false);
    private final Context context;
    
    // Actual LlamaModule implementation
    private LlamaModule mModule = null;
    
    /**
     * Create a new CPUBackend instance.
     *
     * @param modelPath Path to the model file
     * @param executorService Executor service to run tasks on
     */
    public CPUBackend(String modelPath, ExecutorService executorService, Context context) {
        this.modelPath = modelPath;
        this.executorService = executorService;
        this.context = context;
        Log.d(TAG, "CPU backend created with model path: " + modelPath);
    }
    
    /**
     * Create a new CPUBackend instance (legacy constructor).
     *
     * @param modelPath Path to the model file
     * @param executorService Executor service to run tasks on
     */
    public CPUBackend(String modelPath, ExecutorService executorService) {
        this.modelPath = modelPath;
        this.executorService = executorService;
        this.context = null;
        Log.d(TAG, "CPU backend created with model path: " + modelPath);
    }
    
    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            if (isInitialized.get()) {
                Log.d(TAG, "CPU backend already initialized");
                return true;
            }
            
            lock.lock();
            try {
                if (isInitialized.get()) {
                    return true;
                }
                
                Log.d(TAG, "Initializing CPU backend with model at " + modelPath);
                
                try {
                    // Get tokenizer path - first try AppConstants if context is available
                    String tokenizerPath;
                    if (context != null) {
                        tokenizerPath = AppConstants.getTokenizerPath(context);
                        Log.d(TAG, "Using tokenizer path from AppConstants: " + tokenizerPath);
                    } else {
                        // Fall back to checking common locations
                        File legacyTokenizer = new File("/data/local/tmp/llama/tokenizer.bin");
                        if (legacyTokenizer.exists() && legacyTokenizer.isFile()) {
                            tokenizerPath = legacyTokenizer.getAbsolutePath();
                        } else {
                            // Try to infer from model path
                            File modelDir = new File(modelPath).getParentFile();
                            File inferredTokenizer = new File(modelDir, "tokenizer.bin");
                            if (inferredTokenizer.exists() && inferredTokenizer.isFile()) {
                                tokenizerPath = inferredTokenizer.getAbsolutePath();
                            } else {
                                Log.e(TAG, "Could not find tokenizer.bin in any location");
                                return false;
                            }
                        }
                        Log.d(TAG, "Using inferred tokenizer path: " + tokenizerPath);
                    }
                    
                    // Check if tokenizer file exists
                    File tokenizerFile = new File(tokenizerPath);
                    if (!tokenizerFile.exists() || !tokenizerFile.isFile()) {
                        Log.e(TAG, "Tokenizer file not found at: " + tokenizerPath);
                        return false;
                    }
                    
                    // Initialize LlamaModule with the correct parameters
                    mModule = new LlamaModule(
                        ModelUtils.getModelCategory(ModelType.LLAMA_3_2),  // Using LLAMA_3_2 model type
                        modelPath,
                        tokenizerPath,
                        TEMPERATURE
                    );
                    
                    // Load the model
                    int loadResult = mModule.load();
                    if (loadResult != 0) {
                        Log.e(TAG, "Failed to load model: " + loadResult);
                        return false;
                    }
                    
                    isInitialized.set(true);
                    Log.d(TAG, "CPU backend initialization successful");
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Error initializing CPU backend", e);
                    return false;
                }
            } finally {
                lock.unlock();
            }
        }, executorService);
    }
    
    @Override
    public boolean isAvailable() {
        // CPU backend is always available
        return true;
    }
    
    @Override
    public CompletableFuture<String> generateResponse(String prompt) {
        if (!isInitialized.get()) {
            return initialize().thenCompose(initialized -> {
                if (initialized) {
                    return doGenerateResponse(prompt);
                } else {
                    CompletableFuture<String> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalStateException("Failed to initialize CPU backend"));
                    return future;
                }
            });
        }
        
        return doGenerateResponse(prompt);
    }
    
    private CompletableFuture<String> doGenerateResponse(String prompt) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        return CompletableFuture.supplyAsync(() -> {
            if (!tryStartGeneration()) {
                throw new IllegalStateException("Generation already in progress");
            }
            
            try {
                Log.d(TAG, "Generating response with CPU backend for prompt: " + prompt);
                shouldStop.set(false);
                StringBuilder responseBuilder = new StringBuilder();
                CompletableFuture<String> responseFuture = new CompletableFuture<>();
                
                // Calculate sequence length based on prompt length
                int seqLen = (int)(prompt.length() * 0.75) + 256;
                
                mModule.generate(prompt, seqLen, new LlamaCallback() {
                    @Override
                    public void onResult(String token) {
                        if (shouldStop.get()) {
                            return;
                        }
                        
                        if (token == null || token.isEmpty()) {
                            return;
                        }
                        
                        // Check for stop tokens
                        if (token.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2)) ||
                            token.equals(AppConstants.LLM_STOP_TOKEN_EOT) ||
                            token.equals(AppConstants.LLM_STOP_TOKEN_EOT_ALT)) {
                            Log.d(TAG, "Stop token detected: " + token);
                            if (!responseFuture.isDone()) {
                                responseFuture.complete(responseBuilder.toString());
                            }
                            return;
                        }
                        
                        responseBuilder.append(token);
                    }
                    
                    @Override
                    public void onStats(float tps) {
                        Log.d(TAG, String.format("Generation speed: %.2f tokens/sec", tps));
                    }
                }, false);
                
                // Wait for the response future to complete
                String response = responseFuture.getNow(responseBuilder.toString());
                if (response.isEmpty()) {
                    // If response is empty, there might be an issue
                    Log.w(TAG, "Empty response from CPU backend");
                }
                
                return response;
            } catch (Exception e) {
                Log.e(TAG, "Error generating response with CPU backend", e);
                throw e;
            } finally {
                isGenerating.set(false);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<String> generateStreamingResponse(String prompt, 
                                                            com.mtkresearch.breeze_app.service.LLMEngineService.StreamingResponseCallback callback) {
        if (!isInitialized.get()) {
            return initialize().thenCompose(initialized -> {
                if (initialized) {
                    return doGenerateStreamingResponse(prompt, callback);
                } else {
                    CompletableFuture<String> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalStateException("Failed to initialize CPU backend"));
                    return future;
                }
            });
        }
        
        return doGenerateStreamingResponse(prompt, callback);
    }
    
    private CompletableFuture<String> doGenerateStreamingResponse(String prompt, 
                                                               com.mtkresearch.breeze_app.service.LLMEngineService.StreamingResponseCallback callback) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        return CompletableFuture.supplyAsync(() -> {
            if (!tryStartGeneration()) {
                throw new IllegalStateException("Generation already in progress");
            }
            
            try {
                Log.d(TAG, "Generating streaming response with CPU backend for prompt: " + prompt);
                shouldStop.set(false);
                StringBuilder responseBuilder = new StringBuilder();
                
                // Calculate sequence length based on prompt length
                int seqLen = (int)(prompt.length() * 0.75) + 256;
                
                mModule.generate(prompt, seqLen, new LlamaCallback() {
                    @Override
                    public void onResult(String token) {
                        if (shouldStop.get()) {
                            return;
                        }
                        
                        if (token == null || token.isEmpty()) {
                            return;
                        }
                        
                        // Check for stop tokens
                        if (token.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2)) ||
                            token.equals(AppConstants.LLM_STOP_TOKEN_EOT) ||
                            token.equals(AppConstants.LLM_STOP_TOKEN_EOT_ALT)) {
                            Log.d(TAG, "Stop token detected: " + token);
                            try {
                                future.complete(responseBuilder.toString());
                            } catch (Exception e) {
                                Log.e(TAG, "Error completing future", e);
                            }
                            return;
                        }
                        
                        // Add token to the response
                        responseBuilder.append(token);
                        
                        // Send token to callback
                        if (callback != null) {
                            try {
                                callback.onToken(token);
                            } catch (Exception e) {
                                Log.e(TAG, "Error in token callback", e);
                                // Don't let callback exceptions crash the generation thread
                            }
                        }
                    }
                    
                    @Override
                    public void onStats(float tps) {
                        Log.d(TAG, String.format("Generation speed: %.2f tokens/sec", tps));
                    }
                }, false);
                
                // If we get here, the generation completed normally
                if (!future.isDone()) {
                    future.complete(responseBuilder.toString());
                }
                
                Log.d(TAG, "Streaming generation completed successfully");
                return responseBuilder.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error generating streaming response with CPU backend", e);
                if (!future.isDone()) {
                    future.completeExceptionally(e);
                }
                throw e;
            } finally {
                isGenerating.set(false);
            }
        }, executorService);
    }
    
    @Override
    public void stopGeneration() {
        if (isGenerating.get()) {
            Log.d(TAG, "Stopping CPU backend generation");
            shouldStop.set(true);
            if (mModule != null) {
                try {
                    mModule.stop();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping CPU generation", e);
                }
            }
        }
    }
    
    @Override
    public boolean reset() {
        lock.lock();
        try {
            if (!isInitialized.get()) {
                return false;
            }
            
            Log.d(TAG, "Resetting CPU backend");
            if (isGenerating.get()) {
                stopGeneration();
            }
            
            if (mModule != null) {
                try {
                    // Reset the module state
                    mModule.resetNative();
                    return true;
                } catch (Exception e) {
                    Log.e(TAG, "Error resetting CPU module", e);
                    return false;
                }
            }
            
            return false;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void releaseResources() {
        lock.lock();
        try {
            if (isGenerating.get()) {
                stopGeneration();
            }
            
            if (isInitialized.get()) {
                Log.d(TAG, "Releasing CPU backend resources");
                if (mModule != null) {
                    try {
                        mModule.resetNative();
                        mModule = null;
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing CPU resources", e);
                    }
                }
                isInitialized.set(false);
            }
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public String getName() {
        return BACKEND_NAME;
    }
    
    @Override
    public long getMemoryUsage() {
        // TODO: Implement actual memory usage calculation
        return 0;
    }
    
    private boolean tryStartGeneration() {
        return !isGenerating.getAndSet(true);
    }
} 