package com.mtkresearch.breeze_app.service;

import android.content.Intent;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;
import android.os.Handler;

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
import java.io.File;

public class LLMEngineService extends BaseEngineService implements LlamaCallback {
    private static final String TAG = AppConstants.MTK_SERVICE_TAG;
    
    // MTK Backend static variables
    private static int mtkInitCount = 0;
    private static boolean isCleaningUp = false;
    private static final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();
    
    // Add Handler for UI thread operations
    private final Handler handler = new Handler();
    
    // Add bridge instance for JNI calls
    private static com.mtkresearch.gai_android.service.LLMEngineService bridgeInstance = null;
    
    static {
        try {
            // Load libraries in order
            System.loadLibrary("sigchain");  // Load signal handler first
            Thread.sleep(100);  // Give time for signal handlers to initialize
            
            System.loadLibrary("breeze_llm_jni");
            
            // Try to initialize the bridge for JNI calls
            try {
                // Create a bridge instance - this will create the class with the package name the native code expects
                bridgeInstance = com.mtkresearch.gai_android.service.LLMEngineService.class.newInstance();
                
                // Test if native methods work by trying to call one
                boolean nativeMethodsAvailable = false;
                try {
                    // Try calling nativeResetLlm to verify it exists
                    bridgeInstance.nativeResetLlm();
                    nativeMethodsAvailable = true;
                    Log.d(TAG, "Successfully verified native methods through bridge");
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "Native methods implemented incorrectly: " + e.getMessage());
                    nativeMethodsAvailable = false;
                }
                
                AppConstants.MTK_BACKEND_AVAILABLE = nativeMethodsAvailable;
                Log.d(TAG, "Successfully loaded llm_jni library, native methods available: " + nativeMethodsAvailable);
            } catch (Exception e) {
                Log.e(TAG, "Error creating bridge instance: " + e.getMessage(), e);
                AppConstants.MTK_BACKEND_AVAILABLE = false;
            }
            
            // Register shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    cleanupMTKResources();
                    cleanupExecutor.shutdownNow();
                } catch (Exception e) {
                    Log.e(TAG, "Error in shutdown hook", e);
                }
            }));
        } catch (UnsatisfiedLinkError | Exception e) {
            AppConstants.MTK_BACKEND_AVAILABLE = false;
            Log.w(TAG, "Failed to load native libraries, MTK backend will be disabled", e);
        }
    }
    
    // Add static method to check MTK backend availability
    public static boolean isMTKBackendAvailable() {
        return AppConstants.MTK_BACKEND_AVAILABLE;
    }
    
    // Service state
    private String currentBackend = AppConstants.BACKEND_NONE;
    private String preferredBackend = AppConstants.BACKEND_MTK;
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
        
        // Register this instance with the bridge if available
        if (bridgeInstance != null) {
            try {
                com.mtkresearch.gai_android.service.LLMEngineService.registerInstance(this);
                Log.d(TAG, "Registered this instance with the JNI bridge");
            } catch (Exception e) {
                Log.e(TAG, "Failed to register instance with bridge", e);
            }
        }
    }
    
    @Override
    public void onResult(String result) {
        if (result == null || result.isEmpty() || !isGenerating.get()) {
            return;
        }
        
        // Check for stop token
        if (result.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2)) || 
            result.equals(AppConstants.LLM_STOP_TOKEN_EOT) || 
            result.equals(AppConstants.LLM_STOP_TOKEN_EOT_ALT)) {
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
                            Thread.sleep(AppConstants.MTK_STOP_DELAY_MS);
                            
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
                Log.d(TAG, "Intent specified backend: " + newBackend);
                // Force MTK backend regardless of intent value (for testing)
                preferredBackend = AppConstants.BACKEND_MTK;
                // Force reinitialization
                releaseResources();
                isInitialized = false;
                Log.d(TAG, "Forcing preferred backend to: " + preferredBackend);
            } else {
                // If no backend specified, default to MTK
                preferredBackend = AppConstants.BACKEND_MTK;
                Log.d(TAG, "No backend specified in intent, defaulting to: " + preferredBackend);
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
                
                Log.d(TAG, "MTK_BACKEND_AVAILABLE = " + AppConstants.MTK_BACKEND_AVAILABLE);
                
                // Try MTK backend first regardless of preference for testing
                if (AppConstants.MTK_BACKEND_AVAILABLE) {
                    // Add delay before trying MTK initialization
                    Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS);
                    
                    Log.d(TAG, "Attempting MTK initialization...");
                    if (initializeMTKBackend()) {
                        currentBackend = AppConstants.BACKEND_MTK;
                        isInitialized = true;
                        Log.d(TAG, "Successfully initialized MTK backend");
                        future.complete(true);
                        return true;
                    }
                    Log.w(TAG, "MTK backend initialization failed, would normally fall back to CPU but will retry MTK");
                    
                    // Try MTK one more time before giving up
                    Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS * 2);
                    Log.d(TAG, "Retrying MTK initialization...");
                    if (initializeMTKBackend()) {
                        currentBackend = AppConstants.BACKEND_MTK;
                        isInitialized = true;
                        Log.d(TAG, "Successfully initialized MTK backend on second attempt");
                        future.complete(true);
                        return true;
                    }
                    Log.e(TAG, "MTK backend initialization failed on second attempt");
                } else {
                    Log.e(TAG, "MTK backend is not available. Check if native libraries were loaded properly.");
                }

                // For testing, we'll only try MTK and fail if it's not available rather than falling back to CPU
                Log.d(TAG, "MTK initialization failed, falling back to CPU backend");
                
                // Try CPU backend if MTK failed or CPU is preferred
                loadLocalModel();
                
                if (mModule != null) {
                    currentBackend = AppConstants.BACKEND_CPU;
                    isInitialized = true;
                    Log.d(TAG, "Successfully initialized CPU backend");
                    future.complete(true);
                    return true;
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
        if (!isInitialized) {
            if (callback != null) {
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE);
            }
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }
        
        // Check which backend to use
        if (currentBackend.equals(AppConstants.BACKEND_MTK)) {
            // Calculate MTK output length
            int maxTokens = Math.min(
                AppConstants.getLLMMaxSeqLength(this),
                256  // Default max tokens for MTK
            );
            
            return generateResponseMTK(prompt, maxTokens, callback);
        }
        
        // If not MTK backend, use CPU backend (LlamaModule)
        if (mModule == null) {
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
        
        // Handle MTK backend stop
        if (currentBackend.equals(AppConstants.BACKEND_MTK)) {
            try {
                // When stopping, always reset and restore to prompt mode
                safeNativeResetLlm();
                Log.d(TAG, "Stopping MTK generation and restoring to prompt mode");
                try {
                    safeNativeSwapModel(AppConstants.MTK_PROMPT_TOKEN_SIZE);
                } catch (Exception e) {
                    Log.e(TAG, "Error swapping to prompt token size after stopping", e);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MTK generation", e);
            }
        }
        // Handle CPU backend stop
        else if (mModule != null) {
            try {
                // Use a separate thread to ensure the stop command is sent immediately
                // and doesn't get blocked by other operations
                new Thread(() -> {
                    try {
                        Log.d(TAG, "Forcefully stopping LlamaModule");
                        mModule.stop();
                        
                        // Sleep briefly to give the module time to process the stop command
                        Thread.sleep(AppConstants.MTK_STOP_DELAY_MS);
                        
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
            
            // Release MTK resources if using MTK backend
            if (currentBackend.equals(AppConstants.BACKEND_MTK)) {
                synchronized (AppConstants.MTK_LOCK) {
                    if (isCleaningUp) {
                        Log.w(TAG, "Cleanup already in progress");
                        return;
                    }
                    
                    isCleaningUp = true;
                    try {
                        // Add delay before cleanup
                        Thread.sleep(AppConstants.BACKEND_CLEANUP_DELAY_MS);
                        nativeResetLlm();
                        Thread.sleep(AppConstants.BACKEND_CLEANUP_DELAY_MS);
                        nativeReleaseLlm();
                        mtkInitCount = 0; // Reset init count
                        Log.d(TAG, "Released MTK resources");
                    } catch (Exception e) {
                        Log.e(TAG, "Error releasing MTK resources", e);
                        cleanupAfterError();
                    } finally {
                        isCleaningUp = false;
                    }
                }
            }
            
            // Release CPU resources if present
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

    private boolean initializeMTKBackend() {
        if (!AppConstants.MTK_BACKEND_AVAILABLE) {
            Log.e(TAG, "MTK backend disabled, skipping. Native libraries may not have loaded properly.");
            return false;
        }
        
        // For safety, always initialize once
        synchronized (AppConstants.MTK_LOCK) {
            if (isCleaningUp) {
                Log.w(TAG, "Cannot initialize while cleanup is in progress");
                return false;
            }

            // Verify native methods are available
            try {
                nativeResetLlm();
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "Native methods unavailable: " + e.getMessage());
                AppConstants.MTK_BACKEND_AVAILABLE = false;
                return false;
            }

            try {
                // Force cleanup if we've hit the max init attempts
                if (mtkInitCount >= AppConstants.MAX_MTK_INIT_ATTEMPTS) {
                    Log.w(TAG, "MTK init count exceeded limit, forcing cleanup");
                    forceCleanupMTKResources();
                    mtkInitCount = 0;
                    Thread.sleep(AppConstants.MTK_CLEANUP_TIMEOUT_MS);  // Wait for cleanup to complete
                }

                // Add delay before initialization
                Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS);
                
                // Initialize signal handlers first
                try {
                    Log.d(TAG, "Loading sigchain library...");
                    System.loadLibrary("sigchain");
                    Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS);
                    Log.d(TAG, "Sigchain library loaded successfully");
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "Failed to load sigchain library", e);
                }

                Log.d(TAG, "Attempting MTK backend initialization with config path: " + AppConstants.MTK_CONFIG_PATH);
                
                boolean success = false;
                try {
                    // Reset state before initialization
                    Log.d(TAG, "Resetting LLM state before initialization");
                    try {
                        nativeResetLlm();
                    } catch (UnsatisfiedLinkError e) {
                        Log.e(TAG, "Native method nativeResetLlm() not found: " + e.getMessage());
                        return false;
                    }
                    Thread.sleep(100);
                    
                    // Initialize with MTK config path
                    Log.d(TAG, "Calling nativeInitLlm with path: " + AppConstants.MTK_CONFIG_PATH);
                    try {
                        success = nativeInitLlm(AppConstants.MTK_CONFIG_PATH, true);
                    } catch (UnsatisfiedLinkError e) {
                        Log.e(TAG, "Native method nativeInitLlm() not found: " + e.getMessage());
                        return false;
                    }
                    
                    if (!success) {
                        Log.e(TAG, "MTK initialization returned false");
                        cleanupAfterError();
                        return false;
                    }
                    
                    // After successful initialization, set the prompt token size
                    Log.d(TAG, "Setting initial prompt token size: " + AppConstants.MTK_PROMPT_TOKEN_SIZE);
                    try {
                        safeNativeSwapModel(AppConstants.MTK_PROMPT_TOKEN_SIZE);
                        Thread.sleep(100); // Brief delay after model swap
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting initial token size", e);
                        // Continue anyway since initialization was successful
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

    // MTK Backend cleanup methods
    private static void cleanupMTKResources() {
        synchronized (AppConstants.MTK_LOCK) {
            if (isCleaningUp) return;
            isCleaningUp = true;
            
            try {
                Log.d(TAG, "Performing emergency cleanup of MTK resources");
                LLMEngineService tempInstance = new LLMEngineService();
                
                // Reset with timeout
                Future<?> resetFuture = cleanupExecutor.submit(() -> {
                    try {
                        tempInstance.nativeResetLlm();
                    } catch (Exception e) {
                        Log.w(TAG, "Error during emergency reset", e);
                    }
                });
                
                try {
                    resetFuture.get(AppConstants.MTK_NATIVE_OP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    Log.w(TAG, "Reset operation timed out");
                    resetFuture.cancel(true);
                }
                
                Thread.sleep(AppConstants.BACKEND_CLEANUP_DELAY_MS);
                
                // Release with timeout
                Future<?> releaseFuture = cleanupExecutor.submit(() -> {
                    try {
                        tempInstance.nativeReleaseLlm();
                    } catch (Exception e) {
                        Log.w(TAG, "Error during emergency release", e);
                    }
                });
                
                try {
                    releaseFuture.get(AppConstants.MTK_NATIVE_OP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    Log.w(TAG, "Release operation timed out");
                    releaseFuture.cancel(true);
                }
                
                // Reset state
                mtkInitCount = 0;
                
                // Force garbage collection
                System.gc();
                Thread.sleep(AppConstants.BACKEND_CLEANUP_DELAY_MS);
                
            } catch (Exception e) {
                Log.e(TAG, "Error during MTK cleanup", e);
            } finally {
                isCleaningUp = false;
            }
        }
    }

    private void forceCleanupMTKResources() {
        synchronized (AppConstants.MTK_LOCK) {
            if (isCleaningUp) return;
            isCleaningUp = true;
            
            try {
                Log.d(TAG, "Forcing cleanup of MTK resources");
                
                // Multiple cleanup attempts with timeouts
                for (int i = 0; i < 3; i++) {
                    Future<?> cleanupFuture = cleanupExecutor.submit(() -> {
                        try {
                            nativeResetLlm();
                            Thread.sleep(AppConstants.BACKEND_CLEANUP_DELAY_MS);
                            nativeReleaseLlm();
                        } catch (Exception e) {
                            Log.e(TAG, "Error during forced cleanup attempt", e);
                        }
                    });
                    
                    try {
                        cleanupFuture.get(AppConstants.MTK_NATIVE_OP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        Log.w(TAG, "Cleanup attempt " + (i+1) + " timed out");
                        cleanupFuture.cancel(true);
                    }
                    
                    Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS);
                }
                
                // Reset state
                mtkInitCount = 0;
                
                // Force garbage collection
                System.gc();
                Thread.sleep(AppConstants.BACKEND_INIT_DELAY_MS);
                
            } catch (Exception e) {
                Log.e(TAG, "Error during forced cleanup", e);
            } finally {
                isCleaningUp = false;
            }
        }
    }

    private void cleanupAfterError() {
        try {
            // Force cleanup in a separate thread with timeout
            Thread cleanupThread = new Thread(() -> {
                try {
                    try {
                        nativeResetLlm();
                    } catch (UnsatisfiedLinkError e) {
                        Log.e(TAG, "Native method nativeResetLlm() missing during cleanup: " + e.getMessage());
                    }
                    Thread.sleep(AppConstants.BACKEND_CLEANUP_DELAY_MS);
                    try {
                        nativeReleaseLlm();
                    } catch (UnsatisfiedLinkError e) {
                        Log.e(TAG, "Native method nativeReleaseLlm() missing during cleanup: " + e.getMessage());
                    }
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

    // Replace native method declarations with calls to the bridge
    private boolean nativeInitLlm(String yamlConfigPath, boolean preloadSharedWeights) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeInitLlm");
            return false;
        }
        try {
            return bridgeInstance.nativeInitLlm(yamlConfigPath, preloadSharedWeights);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeInitLlm() not available: " + e.getMessage());
            return false;
        }
    }
    
    private String nativeInference(String inputString, int maxResponse, boolean parsePromptTokens) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeInference");
            return AppConstants.LLM_ERROR_RESPONSE;
        }
        try {
            return bridgeInstance.nativeInference(inputString, maxResponse, parsePromptTokens);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeInference() not available: " + e.getMessage());
            return AppConstants.LLM_ERROR_RESPONSE;
        }
    }
    
    private String nativeStreamingInference(String inputString, int maxResponse, boolean parsePromptTokens, 
                                            final TokenCallback callback) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeStreamingInference");
            return AppConstants.LLM_ERROR_RESPONSE;
        }
        try {
            // Create adapter for the callback
            com.mtkresearch.gai_android.service.LLMEngineService.TokenCallback bridgeCallback = 
                new com.mtkresearch.gai_android.service.LLMEngineService.TokenCallback() {
                    @Override
                    public void onToken(String token) {
                        if (callback != null) {
                            callback.onToken(token);
                        }
                    }
                };
            return bridgeInstance.nativeStreamingInference(inputString, maxResponse, parsePromptTokens, bridgeCallback);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeStreamingInference() not available: " + e.getMessage());
            return AppConstants.LLM_ERROR_RESPONSE;
        }
    }
    
    private void nativeReleaseLlm() {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeReleaseLlm");
            return;
        }
        try {
            bridgeInstance.nativeReleaseLlm();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeReleaseLlm() not available: " + e.getMessage());
        }
    }
    
    private boolean nativeResetLlm() {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeResetLlm");
            return false;
        }
        try {
            return bridgeInstance.nativeResetLlm();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeResetLlm() not available: " + e.getMessage());
            return false;
        }
    }
    
    private boolean nativeSwapModel(int tokenSize) {
        if (bridgeInstance == null) {
            Log.e(TAG, "Bridge instance is null, cannot call nativeSwapModel");
            return false;
        }
        try {
            return bridgeInstance.nativeSwapModel(tokenSize);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeSwapModel() not available: " + e.getMessage());
            return false;
        }
    }

    // Interface for token callbacks (from original implementation)
    public interface TokenCallback {
        void onToken(String token);
    }
    
    // Generate response using the MTK backend - using the original implementation's approach
    private CompletableFuture<String> generateResponseMTK(String context, int maxTokens, StreamingResponseCallback callback) {
        if (!isInitialized) {
            if (callback != null) {
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE);
            }
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }

        // Check if native methods are available
        try {
            // Test if nativeStreamingInference exists
            nativeStreamingInference("test", 1, false, null);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeStreamingInference() not available: " + e.getMessage());
            if (callback != null) {
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE + " (Native methods missing)");
            }
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE + " (Native methods missing)");
        } catch (Exception e) {
            // This is expected as we passed null callback
            // Just checking if the method exists
        }

        currentCallback = callback;
        currentStreamingResponse.setLength(0);
        isGenerating.set(true);
        
        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        
        try {
            // Run in executor to keep UI responsive
            executor.execute(() -> {
                try {
                    // Always ensure we're using the PROMPT token size before processing a new prompt
                    Log.d(TAG, "Setting model to prompt processing mode with token size: " + AppConstants.MTK_PROMPT_TOKEN_SIZE);
                    try {
                        safeNativeResetLlm();
                        safeNativeSwapModel(AppConstants.MTK_PROMPT_TOKEN_SIZE);
                        Thread.sleep(100); // Brief delay after model swap
                    } catch (Exception e) {
                        Log.e(TAG, "Error swapping to prompt model", e);
                    }
                    
                    // Now perform the streaming inference
                    String response = nativeStreamingInference(context, maxTokens, false, new TokenCallback() {
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
                        resultFuture.complete(response);
                    }
                    
                    // Always swap back to PROMPT token size after generation
                    // This ensures we're ready for the next prompt
                    try {
                        Log.d(TAG, "Resetting model back to prompt mode with token size: " + AppConstants.MTK_PROMPT_TOKEN_SIZE);
                        safeNativeResetLlm();
                        safeNativeSwapModel(AppConstants.MTK_PROMPT_TOKEN_SIZE);
                    } catch (Exception e) {
                        Log.e(TAG, "Error resetting MTK state after generation", e);
                    }
                } catch (UnsatisfiedLinkError e) {
                    Log.e(TAG, "Missing native method during generation: " + e.getMessage());
                    if (!resultFuture.isDone()) {
                        resultFuture.complete(AppConstants.LLM_ERROR_RESPONSE + " (Native methods missing)");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error in MTK streaming generation", e);
                    if (!resultFuture.isDone()) {
                        resultFuture.completeExceptionally(e);
                    }
                } finally {
                    isGenerating.set(false);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error scheduling MTK response", e);
            resultFuture.completeExceptionally(e);
        }
        
        return resultFuture;
    }

    // Interface for generation callbacks from MTK native code (but this won't be used here)
    public interface GenerationCallback {
        void onToken(String token);
        void onCompletion(String fullText);
        void onError(String errorMessage);
    }
    
    // Reset state before generation
    private void resetState() {
        currentStreamingResponse.setLength(0);
        
        // Ensure we're in PROMPT mode
        if (currentBackend.equals(AppConstants.BACKEND_MTK)) {
            try {
                Log.d(TAG, "Resetting to prompt processing mode");
                safeNativeResetLlm();
                safeNativeSwapModel(AppConstants.MTK_PROMPT_TOKEN_SIZE);
            } catch (Exception e) {
                Log.e(TAG, "Error resetting to prompt mode", e);
            }
        }
    }
    
    private ExecutorService generationExecutor = Executors.newSingleThreadExecutor();

    // Process response for consistency
    private String processResponse(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text;
    }

    // Check if response contains stop tokens
    private boolean checkForStopTokens(String text) {
        return text.contains(AppConstants.LLM_STOP_TOKEN_EOT) || 
               text.contains(AppConstants.LLM_STOP_TOKEN_EOT_ALT) ||
               text.contains(PromptFormat.getStopToken(ModelType.LLAMA_3_2));
    }

    // Add safe versions of native methods that won't crash when implementations are missing
    private boolean safeNativeResetLlm() {
        try {
            return nativeResetLlm();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeResetLlm() not available: " + e.getMessage());
            return false;
        }
    }

    private boolean safeNativeInitLlm(String path, boolean preload) {
        try {
            return nativeInitLlm(path, preload);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeInitLlm() not available: " + e.getMessage());
            return false;
        }
    }

    private void safeNativeReleaseLlm() {
        try {
            nativeReleaseLlm();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeReleaseLlm() not available: " + e.getMessage());
        }
    }

    private boolean safeNativeSwapModel(int tokenSize) {
        try {
            return nativeSwapModel(tokenSize);
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Native method nativeSwapModel() not available: " + e.getMessage());
            return false;
        }
    }
} 