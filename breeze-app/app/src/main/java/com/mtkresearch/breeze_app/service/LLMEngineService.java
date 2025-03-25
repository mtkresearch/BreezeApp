package com.mtkresearch.breeze_app.service;

import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class LLMEngineService extends BaseEngineService implements LlamaCallback {
    private static final String TAG = "LLMEngineService";
    
    // Service state
    private String currentBackend = AppConstants.BACKEND_NONE;
    private String preferredBackend = AppConstants.BACKEND_DEFAULT;
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
    private String resultMessage = "";
    private long modelLoadTime = 0;
    
    // UTF-8 validation buffer - temporarily removed
    // private final StringBuilder tokenBuffer = new StringBuilder();
    // private static final int MAX_BUFFER_SIZE = 100; // Max buffer size to prevent memory issues

    public interface StreamingResponseCallback {
        void onToken(String token);
    }

    public LLMEngineService() {
        this.conversationManager = new ConversationManager();
    }
    
    @Override
    public void onResult(String result) {
        if (result == null || result.isEmpty() || !isGenerating.get()) {
            return;
        }
        
        // Check for stop token
        if (result.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2)) || result.equals("<|eot_id|>") || result.equals("<|end_of_text|>")) {
            Log.d(TAG, "Stop token detected: " + result);
            
            // First mark that we're no longer generating to prevent more tokens from being processed
            isGenerating.set(false);
            
            // Stop the model in a standalone thread to ensure it's not blocked
            if (mModule != null) {
                try {
                    new Thread(() -> {
                        try {
                            Log.d(TAG, "Forcefully stopping LlamaModule after stop token");
                            mModule.stop();
                            
                            // Sleep briefly to give the module time to process the stop command
                            Thread.sleep(100);
                            
                            // Call stop again to ensure it takes effect
                            mModule.stop();
                            Log.d(TAG, "Second stop call completed after stop token");
                        } catch (Exception e) {
                            Log.e(TAG, "Error in forceful stopping thread after stop token", e);
                        }
                    }).start();
                } catch (Exception e) {
                    Log.e(TAG, "Error initiating stop process after stop token", e);
                }
            }
            
            // Then complete the generation
            completeGeneration();
            return;
        }
        
        // Directly append token to the response without UTF-8 checking
        // Log.d(TAG, "Received token: \"" + result + "\"");
        currentStreamingResponse.append(result);
        
        // Send token to callback if streaming
        if (currentCallback != null) {
            currentCallback.onToken(result);
        }
    }

    @Override
    public void onStats(float tps) {
        Log.d(TAG, String.format("Generation speed: %.2f tokens/sec", tps));
    }

    public class LocalBinder extends BaseEngineService.LocalBinder<LLMEngineService> { }

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
                
                // Try to load the model
                loadLocalModel();
                
                if (mModule != null) {
                    currentBackend = AppConstants.BACKEND_CPU;
                    isInitialized = true;
                    Log.d(TAG, "Successfully initialized CPU backend");
                    future.complete(true);
                    return true;
                }
                
                Log.e(TAG, "Model initialization failed");
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
    
    private void loadLocalModel() {
        try {
            Log.d(TAG, "Loading model: " + modelPath);
            
            if (mModule != null) {
                Log.d(TAG, "Start deallocating existing module instance");
                mModule.resetNative();
                mModule = null;
                Log.d(TAG, "Completed deallocating existing module instance");
            }
            
            if (modelPath == null) {
                Log.e(TAG, "Model path is null, cannot initialize");
                return;
            }
            
            // Get temperature from constants
            float temperature = AppConstants.LLM_TEMPERATURE;
            
            long runStartTime = System.currentTimeMillis();
            
            // Initialize LlamaModule with model parameters
            mModule = new LlamaModule(
                ModelUtils.getModelCategory(ModelType.LLAMA_3_2),
                modelPath,
                AppConstants.getTokenizerPath(this),
                temperature
            );
            
            // Load the model
            int loadResult = mModule.load();
            
            modelLoadTime = System.currentTimeMillis() - runStartTime;
            
            if (loadResult != 0) {
                Log.e(TAG, "Failed to load model: " + loadResult);
                mModule = null;
                return;
            }
            
            // Print model info for debugging
            String[] modelSegments = modelPath.split("/");
            String modelName = modelSegments[modelSegments.length - 1];
            
            String[] tokenizerSegments = AppConstants.getTokenizerPath(this).split("/");
            String tokenizerName = tokenizerSegments[tokenizerSegments.length - 1];
            
            String modelInfo = String.format(
                "Successfully loaded model %s and tokenizer %s in %.2f sec",
                modelName, 
                tokenizerName,
                (float) modelLoadTime / 1000.0f
            );
            
            Log.d(TAG, modelInfo);
            
            String modelLoggingInfo = 
                "Model path: " + modelPath +
                "\nTokenizer path: " + AppConstants.getTokenizerPath(this) +
                "\nBackend: CPU" +
                "\nModelType: " + ModelUtils.getModelCategory(ModelType.LLAMA_3_2) +
                "\nTemperature: " + temperature +
                "\nModel loaded time: " + modelLoadTime + " ms";
            
            Log.d(TAG, "Load complete. " + modelLoggingInfo);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading model", e);
            mModule = null;
        }
    }
    
    public String getModelName() {
        if (modelPath == null) {
            return "Unknown";
        }
        return com.mtkresearch.breeze_app.utils.ModelUtils.getModelDisplayName(modelPath);
    }
    
    public CompletableFuture<String> generateResponse(String prompt) {
        if (!isInitialized || mModule == null) {
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }
        
        CompletableFuture<String> future = new CompletableFuture<>();
        currentResponse = future;
        currentStreamingResponse.setLength(0);
        isGenerating.set(true);
        
        executor.execute(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
                Log.d(TAG, "Starting response generation with prompt: " + prompt);
                
                long generateStartTime = System.currentTimeMillis();
                
                // Calculate sequence length
                int seqLen = (int)(prompt.length() * 0.75) + 64;
                
                mModule.generate(prompt, seqLen, this, false);
                
                long generateDuration = System.currentTimeMillis() - generateStartTime;
                Log.d(TAG, "Generation completed in " + generateDuration + " ms");
                
                // Make sure generation is complete, especially if no stop token was detected
                if (isGenerating.get()) {
                    Log.d(TAG, "Generation still active after generate call - calling completeGeneration()");
                    completeGeneration();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in response generation", e);
                if (!currentResponse.isDone()) {
                    currentResponse.completeExceptionally(e);
                }
            } finally {
                // Always make sure we've properly completed
                if (isGenerating.get()) {
                    Log.d(TAG, "Cleaning up incomplete generation in finally block");
                    completeGeneration();
                }
            }
        });
        
        return future;
    }
    
    public CompletableFuture<String> generateStreamingResponse(String prompt, StreamingResponseCallback callback) {
        if (!isInitialized || mModule == null) {
            if (callback != null) {
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE);
            }
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }
        
        currentCallback = callback;
        currentResponse = new CompletableFuture<>();
        currentStreamingResponse.setLength(0);
        isGenerating.set(true);
        
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        
        executor.execute(() -> {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE);
                Log.d(TAG, "Starting streaming response generation with prompt: " + prompt);
                
                long generateStartTime = System.currentTimeMillis();
                
                // Calculate sequence length with more generous output space for Chinese characters
                int seqLen = Math.min(
                    AppConstants.getLLMMaxSeqLength(this),
                    prompt.length() + AppConstants.getLLMMinOutputLength(this)
                );
                
                mModule.generate(prompt, seqLen, this, false);
                
                long generateDuration = System.currentTimeMillis() - generateStartTime;
                Log.d(TAG, "Generation completed in " + generateDuration + " ms");
                
                // Make sure generation is complete, especially if no stop token was detected
                if (isGenerating.get()) {
                    Log.d(TAG, "Generation still active after generate call - calling completeGeneration()");
                    completeGeneration();
                }
                
                // Complete future if not already done
                if (!resultFuture.isDone()) {
                    String finalResponse = currentStreamingResponse.toString();
                    resultFuture.complete(finalResponse);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming response generation", e);
                if (!currentResponse.isDone()) {
                    currentResponse.completeExceptionally(e);
                }
                if (!resultFuture.isDone()) {
                    resultFuture.completeExceptionally(e);
                }
            } finally {
                // Always make sure we've properly completed
                if (isGenerating.get()) {
                    Log.d(TAG, "Cleaning up incomplete generation in finally block");
                    completeGeneration();
                }
                currentCallback = null;
            }
        });
        
        return resultFuture;
    }
    
    public void stopGeneration() {
        Log.d(TAG, "Manual stopping of generation requested");
        
        // First, mark that we're no longer generating to prevent further tokens from being processed
        isGenerating.set(false);
        
        if (mModule != null) {
            try {
                // Use a separate thread to ensure the stop command is sent immediately
                // and doesn't get blocked by other operations
                new Thread(() -> {
                    try {
                        Log.d(TAG, "Forcefully stopping LlamaModule");
                        mModule.stop();
                        
                        // Sleep briefly to give the module time to process the stop command
                        Thread.sleep(100);
                        
                        // Call stop again to ensure it takes effect
                        mModule.stop();
                        Log.d(TAG, "Second stop call completed");
                    } catch (Exception e) {
                        Log.e(TAG, "Error in forceful stopping thread", e);
                    }
                }).start();
            } catch (Exception e) {
                Log.e(TAG, "Error initiating stop process", e);
            }
        }
        
        // Complete any pending futures
        String finalResponse = currentStreamingResponse.toString();
        if (finalResponse.isEmpty()) {
            finalResponse = "[Generation stopped by user]";
        }
        
        // Ensure callback is cleared
        currentCallback = null;
        
        // Complete the response future if it's still pending
        if (currentResponse != null && !currentResponse.isDone()) {
            final String responseToComplete = finalResponse;
            Log.d(TAG, "Completing response with length: " + responseToComplete.length());
            currentResponse.complete(responseToComplete);
        }
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
        
        releaseResources();
        
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

    private void completeGeneration() {
        if (isGenerating.compareAndSet(true, false)) {
            Log.d(TAG, "Completing generation process");
            
            // Ensure the model stops generating by explicitly calling stop
            if (mModule != null) {
                try {
                    Log.d(TAG, "Stopping model in completeGeneration");
                    mModule.stop();
                } catch (Exception e) {
                    Log.e(TAG, "Error stopping model in completeGeneration", e);
                }
            }
            
            String finalResponse = currentStreamingResponse.toString();
            Log.d(TAG, "Final response length: " + finalResponse.length());
            
            // Complete the futures
            if (currentResponse != null && !currentResponse.isDone()) {
                currentResponse.complete(finalResponse);
            }
            
            currentCallback = null;
        }
    }
} 