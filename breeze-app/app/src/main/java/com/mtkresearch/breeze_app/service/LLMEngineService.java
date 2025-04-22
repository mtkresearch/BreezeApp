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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.ref.WeakReference;

public class LLMEngineService extends BaseEngineService implements LlamaCallback {
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
    
    // MTK backend state
    private static final Object MTK_LOCK = new Object();
    private static int mtkInitCount = 0;
    private static boolean isCleaningUp = false;
    private static final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();
    
    static {
        // Only try to load MTK libraries if MTK backend is enabled
        if (AppConstants.MTK_BACKEND_ENABLED) {
            try {
                // Load libraries in order
                System.loadLibrary("sigchain");  // Load signal handler first
                Thread.sleep(100);  // Give time for signal handlers to initialize
                
                System.loadLibrary("llm_jni");
                AppConstants.MTK_BACKEND_AVAILABLE = true;
                Log.d(TAG, "Successfully loaded llm_jni library");
                
            } catch (UnsatisfiedLinkError | Exception e) {
                AppConstants.MTK_BACKEND_AVAILABLE = false;
                Log.w(TAG, "Failed to load native libraries, MTK backend will be disabled", e);
            }
        } else {
            Log.i(TAG, "MTK backend is disabled in AppConstants");
        }
    }

    public static boolean isMTKBackendAvailable() {
        return AppConstants.MTK_BACKEND_AVAILABLE && AppConstants.MTK_BACKEND_ENABLED;
    }

    public String getModelName() {
        if (modelPath == null) {
            if (currentBackend.equals(AppConstants.BACKEND_MTK)) {
                return "Breeze2";  // Default to Breeze2 for MTK backend
            }
            return "Unknown";
        }
        return com.mtkresearch.breeze_app.utils.ModelUtils.getModelDisplayName(modelPath);
    }

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

    public class LocalBinder extends BaseEngineService.LocalBinder<LLMEngineService> {
        private final WeakReference<LLMEngineService> serviceRef;
        
        public LocalBinder() {
            this.serviceRef = new WeakReference<>(LLMEngineService.this);
        }
        
        @Override
        public LLMEngineService getService() {
            return serviceRef.get();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand flag="+flags+", startId= "+startId);
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
        Log.d(TAG, "initialize");

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
                
                // Try MTK backend only if it's preferred
                if (preferredBackend.equals(AppConstants.BACKEND_MTK)) {
                    // Add delay before trying MTK initialization
                    Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS);
                    
                    if (initializeMTKBackend()) {
                        currentBackend = AppConstants.BACKEND_MTK;
                        isInitialized = true;
                        Log.d(TAG, "Successfully initialized MTK backend");
                        future.complete(true);
                        return true;
                    }
                    Log.w(TAG, "MTK backend initialization failed");
                    
                    // Add delay before trying fallback
                    Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS);
                }

                // Try CPU backend if MTK failed or CPU is preferred
                if (preferredBackend.equals(AppConstants.BACKEND_CPU)) {
                    if (initializeLocalCPUBackend()) {
                        currentBackend = AppConstants.BACKEND_CPU;
                        isInitialized = true;
                        Log.d(TAG, "Successfully initialized CPU backend");
                        future.complete(true);
                        return true;
                    }
                    Log.w(TAG, "CPU backend initialization failed");
                }

                Log.e(TAG, "All backend initialization attempts failed");
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

    private boolean initializeMTKBackend() {
        if (!AppConstants.MTK_BACKEND_AVAILABLE) {
            Log.d(TAG, "MTK backend disabled, skipping");
            return false;
        }

        synchronized (MTK_LOCK) {
            if (isCleaningUp) {
                Log.w(TAG, "Cannot initialize while cleanup is in progress");
                return false;
            }

            try {
                // Force cleanup if we've hit the max init attempts
                if (mtkInitCount >= AppConstants.MAX_MTK_INIT_ATTEMPTS) {
                    Log.w(TAG, "MTK init count exceeded limit, forcing cleanup");
                    mtkInitCount = 0;
                    Thread.sleep(1000);  // Wait for cleanup to complete
                }

                // Add delay before initialization
                Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS);
                
                // Initialize signal handlers first
                try {
                    System.loadLibrary("sigchain");
                    Thread.sleep(100);
                } catch (UnsatisfiedLinkError e) {
                    Log.w(TAG, "Failed to load sigchain library", e);
                }

                Log.d(TAG, "Attempting MTK backend initialization...");
                
                boolean success = false;
                try {
                    // Reset state before initialization
                    nativeResetLlm();
                    Thread.sleep(100);
                    
                    // Initialize with conservative settings
                    success = nativeInitLlm(AppConstants.getMtkConfigPath(context), true);

                    if (!success) {
                        Log.e(TAG, "MTK initialization returned false");
                        cleanupAfterError();
                        return false;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during MTK initialization", e);
                    cleanupAfterError();
                    return false;
                }
                
                if (success) {
                    mtkInitCount++;
                    Log.d(TAG, "MTK initialization successful. Init count: " + mtkInitCount);
                    return true;
                } else {
                    Log.e(TAG, "MTK initialization failed");
                    cleanupAfterError();
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing MTK backend", e);
                cleanupAfterError();
                return false;
            }
        }
    }

    private void cleanupAfterError() {
        try {
            // Force cleanup in a separate thread with timeout
            Thread cleanupThread = new Thread(() -> {
                try {
                    nativeResetLlm();
                    Thread.sleep(100);
                    nativeReleaseLlm();
                } catch (Exception e) {
                    Log.w(TAG, "Error during error cleanup", e);
                }
            });
            
            cleanupThread.start();
            cleanupThread.join(AppConstants.MTK_CLEANUP_TIMEOUT_MS);
            
            if (cleanupThread.isAlive()) {
                Log.w(TAG, "Cleanup thread timed out, interrupting");
                cleanupThread.interrupt();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error during cleanup after error", e);
        }
    }

    private boolean initializeLocalCPUBackend() {
        try {
            Log.d(TAG, "Attempting Local CPU backend initialization...");

            if (mModule != null) {
                Log.d(TAG, "Start deallocating existing module instance");
                mModule.resetNative();
                mModule = null;
                Log.d(TAG, "Completed deallocating existing module instance");
            }
            
            if (modelPath == null) {
                Log.e(TAG, "Model path is null, cannot initialize");
                return false;
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
            
            if (loadResult != 0) {
                Log.e(TAG, "Failed to load model: " + loadResult);
                mModule = null;
                return false;
            }

            Log.d(TAG, "Local CPU backend initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Local CPU backend", e);
            return false;
        }
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
                    case AppConstants.BACKEND_MTK:
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
                                        resultFuture.complete(response);
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
                                        resultFuture.completeExceptionally(e);
                                    }
                                } finally {
                                    isGenerating.set(false);
                                }
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Error in MTK streaming response", e);
                            throw e;
                        }
                        break;

                    case AppConstants.BACKEND_CPU:
                        // Only apply prompt formatting for local CPU backend
                        Log.d(TAG, "Formatted prompt for local CPU: " + prompt);

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
        Log.d(TAG, "Manual stopping of generation requested");

        // First, mark that we're no longer generating to prevent further tokens from being processed
        isGenerating.set(false);

        if (currentBackend.equals(AppConstants.BACKEND_MTK)) {
            try {
                nativeResetLlm();
                nativeSwapModel(128);
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
        Log.d(TAG, "releaseResources");
        synchronized (MTK_LOCK) {
            if (isCleaningUp) {
                Log.w(TAG, "Cleanup already in progress");
                return;
            }
            
            isCleaningUp = true;
            try {
                stopGeneration();
                
                // Release MTK resources if using MTK backend
                if (currentBackend.equals(AppConstants.BACKEND_MTK)) {
                    try {
                        nativeReleaseLlm();
                        mtkInitCount = 0; // Reset init count
                        Log.d(TAG, "Released MTK resources");
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing MTK resources", e);
                        cleanupAfterError();
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
                currentBackend = AppConstants.BACKEND_NONE;
                isInitialized = false;
                System.gc(); // Request garbage collection
                
                Log.d(TAG, "All resources released");
            } catch (Exception e) {
                Log.e(TAG, "Error during cleanup", e);
            } finally {
                isCleaningUp = false;
            }
        }
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");   
        super.onDestroy();
        
        // Run cleanup with timeout
        Future<?> cleanupFuture = cleanupExecutor.submit(() -> {
            try {
                releaseResources();
 Log.d(TAG, "releaseResources in onDestroy");                
            } catch (Exception e) {
                Log.e(TAG, "Error during service cleanup", e);
            }
        });
        
        try {
            cleanupFuture.get(AppConstants.MTK_CLEANUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.w(TAG, "Service cleanup timed out");
            cleanupFuture.cancel(true);
        } catch (Exception e) {
            Log.e(TAG, "Error waiting for cleanup", e);
        }
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

    private int getMaxSequenceLength() {
        return AppConstants.getLLMMaxSeqLength(this);
    }

    private int getMinOutputLength() {
        return AppConstants.getLLMMinOutputLength(this);
    }
} 