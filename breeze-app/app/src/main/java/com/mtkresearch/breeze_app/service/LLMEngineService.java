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
import com.mtkresearch.breeze_app.service.bridge.MTKNativeBridge;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;

import com.mtkresearch.breeze_app.service.llm.LLMBackend;
import com.mtkresearch.breeze_app.service.llm.LLMBackendFactory;
import com.mtkresearch.breeze_app.service.llm.backends.CPUBackend;
import com.mtkresearch.breeze_app.service.llm.backends.MTKBackend;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutionException;

public class LLMEngineService extends BaseEngineService implements LlamaCallback {
    private static final String TAG = AppConstants.MTK_SERVICE_TAG;
    
    // MTK Backend static variables
    private static int mtkInitCount = 0;
    private static boolean isCleaningUp = false;
    private static final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();
    
    // Add Handler for UI thread operations
    private final Handler handler = new Handler();
    
    // The bridge for native method calls
    private MTKNativeBridge mtkBridge;

    // Current active backend - can be switched at runtime
    private LLMBackend currentBackend = null;
    private LLMBackendFactory backendFactory;
    
    static {
        try {
            // Initialize the MTK native bridge
            boolean success = MTKNativeBridge.initialize();
            Log.d(TAG, "MTK Native Bridge initialization result: " + success + 
                  ", MTK_BACKEND_AVAILABLE=" + AppConstants.MTK_BACKEND_AVAILABLE);
            
            // Register shutdown hook for cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    cleanupMTKResources();
                    cleanupExecutor.shutdownNow();
                } catch (Exception e) {
                    Log.e(TAG, "Error in shutdown hook", e);
                }
            }));
        } catch (Exception e) {
            AppConstants.MTK_BACKEND_AVAILABLE = false;
            Log.w(TAG, "Failed to initialize MTK native bridge, MTK backend will be disabled", e);
        }
    }
    
    // Add static method to check MTK backend availability
    public static boolean isMTKBackendAvailable() {
        return AppConstants.MTK_BACKEND_AVAILABLE;
    }
    
    // Service state
    private String currentBackendName = AppConstants.BACKEND_NONE;
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

    public interface StreamingResponseCallback {
        void onToken(String token);
    }

    public LLMEngineService() {
        this.conversationManager = new ConversationManager();
        
        // Initialize the MTK bridge
        this.mtkBridge = MTKNativeBridge.getInstance();
        
        // Register this instance with the bridge
        if (AppConstants.MTK_BACKEND_AVAILABLE) {
            mtkBridge.registerService(this);
        }
        
        // Initialize backend factory
        this.backendFactory = new LLMBackendFactory.Default(this);
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
                
                // Try MTK backend first if it's preferred and available
                if (AppConstants.MTK_BACKEND_AVAILABLE && preferredBackend.equals(AppConstants.BACKEND_MTK)) {
                    Log.d(TAG, "Attempting to initialize MTK backend (MTK_BACKEND_AVAILABLE=" + 
                          AppConstants.MTK_BACKEND_AVAILABLE + ")");
                          
                    // Check if MTK config path exists
                    File configFile = new File(AppConstants.MTK_CONFIG_PATH);
                    if (!configFile.exists() || !configFile.isFile()) {
                        Log.e(TAG, "MTK config file not found at: " + AppConstants.MTK_CONFIG_PATH + 
                              ". Falling back to CPU backend.");
                        AppConstants.MTK_BACKEND_AVAILABLE = false;
                    } else {
                        Log.d(TAG, "MTK config file exists at " + AppConstants.MTK_CONFIG_PATH);
                    
                        currentBackend = backendFactory.createMTKBackend();
                        CompletableFuture<Boolean> initResult = currentBackend.initialize();
                        
                        try {
                            boolean success = initResult.get(AppConstants.LLM_INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                            if (success) {
                                currentBackendName = AppConstants.BACKEND_MTK;
                                isInitialized = true;
                                Log.d(TAG, "Successfully initialized MTK backend, setting current backend name to: " + 
                                      currentBackendName);
                                future.complete(true);
                                return true;
                            }
                            
                            Log.w(TAG, "MTK backend initialization failed, falling back to CPU");
                        } catch (Exception e) {
                            Log.e(TAG, "Error waiting for MTK backend initialization", e);
                            Log.w(TAG, "MTK backend initialization error, falling back to CPU");
                        }
                    }
                } else {
                    Log.d(TAG, "Skipping MTK backend initialization. MTK_BACKEND_AVAILABLE=" +
                          AppConstants.MTK_BACKEND_AVAILABLE + ", preferredBackend=" + preferredBackend);
                }

                // If MTK failed or wasn't preferred, try CPU backend
                currentBackend = backendFactory.createCPUBackend(modelPath);
                CompletableFuture<Boolean> initResult = currentBackend.initialize();
                
                boolean success = initResult.get(AppConstants.LLM_INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (success) {
                    currentBackendName = AppConstants.BACKEND_CPU;
                    isInitialized = true;
                    Log.d(TAG, "Successfully initialized CPU backend");
                    future.complete(true);
                    return true;
                }
                
                Log.e(TAG, "All backend initialization attempts failed");
                currentBackend = null;
                currentBackendName = AppConstants.BACKEND_NONE;
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
    
    public CompletableFuture<String> generateStreamingResponse(String prompt, StreamingResponseCallback callback) {
        if (!isInitialized || currentBackend == null) {
            if (callback != null) {
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE);
            }
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }
        
        currentCallback = callback;
        currentStreamingResponse.setLength(0);
        isGenerating.set(true);
        
        // Delegate to the current backend, adapting our callback to their interface
        return currentBackend.generateStreamingResponse(prompt, token -> {
            if (callback != null) {
                callback.onToken(token);
            }
            currentStreamingResponse.append(token);
        })
        .thenApply(response -> {
            isGenerating.set(false);
            currentCallback = null;
            return response;
        })
        .exceptionally(e -> {
            Log.e(TAG, "Error during streaming response generation", e);
            isGenerating.set(false);
            currentCallback = null;
            return AppConstants.LLM_ERROR_RESPONSE;
        });
    }
    
    public void stopGeneration() {
        Log.d(TAG, "Manual stopping of generation requested");
        
        // First, mark that we're no longer generating to prevent further tokens from being processed
        isGenerating.set(false);
        
        // Delegate to the current backend
        if (currentBackend != null) {
            currentBackend.stopGeneration();
        }
        
        // Handle CPU backend stop (legacy code)
        if (mModule != null) {
            try {
                // Use a separate thread to ensure the stop command is sent immediately
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
            
            // Release backend resources
            if (currentBackend != null) {
                currentBackend.releaseResources();
                currentBackend = null;
            }
            
            // Release CPU resources (legacy code)
            if (mModule != null) {
                mModule.resetNative();
                mModule = null;
            }
            
            isInitialized = false;
            currentBackendName = AppConstants.BACKEND_NONE;
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
        return currentBackendName;
    }
    
    public String getPreferredBackend() {
        return preferredBackend;
    }
    
    // Switch to a different backend at runtime
    public CompletableFuture<Boolean> switchBackend(String backendName) {
        if (backendName.equals(currentBackendName)) {
            return CompletableFuture.completedFuture(true);
        }
        
        if (isGenerating.get()) {
            stopGeneration();
        }
        
        // Release current backend
        if (currentBackend != null) {
            currentBackend.releaseResources();
            currentBackend = null;
        }
        
        // Create and initialize the new backend
        final String finalBackendName;
        if (backendName.equals(AppConstants.BACKEND_MTK) && AppConstants.MTK_BACKEND_AVAILABLE) {
            currentBackend = backendFactory.createMTKBackend();
            finalBackendName = AppConstants.BACKEND_MTK;
        } else {
            currentBackend = backendFactory.createCPUBackend(modelPath);
            finalBackendName = AppConstants.BACKEND_CPU; // Force to CPU if MTK requested but not available
        }
        
        return currentBackend.initialize()
            .thenApply(success -> {
                if (success) {
                    currentBackendName = finalBackendName;
                    isInitialized = true;
                    Log.d(TAG, "Successfully switched to " + finalBackendName + " backend");
                } else {
                    currentBackend = null;
                    currentBackendName = AppConstants.BACKEND_NONE;
                    isInitialized = false;
                    Log.e(TAG, "Failed to initialize " + finalBackendName + " backend");
                }
                return success;
            });
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

    // MTK Backend cleanup methods
    private static void cleanupMTKResources() {
        synchronized (AppConstants.MTK_LOCK) {
            if (isCleaningUp) return;
            isCleaningUp = true;
            
            try {
                Log.d(TAG, "Performing emergency cleanup of MTK resources");
                MTKNativeBridge bridge = MTKNativeBridge.getInstance();
                
                // Reset with timeout
                Future<?> resetFuture = cleanupExecutor.submit(() -> {
                    try {
                        bridge.resetLlm();
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
                        bridge.releaseLlm();
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
                            mtkBridge.resetLlm();
                            Thread.sleep(AppConstants.BACKEND_CLEANUP_DELAY_MS);
                            mtkBridge.releaseLlm();
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
                    mtkBridge.resetLlm();
                    Thread.sleep(AppConstants.BACKEND_CLEANUP_DELAY_MS);
                    mtkBridge.releaseLlm();
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

    // Add methods required by the native code that will be called through JNI
    // These are essential for the bridge pattern to work correctly
    public void onNativeToken(String token) {
        if (currentCallback != null && isGenerating.get()) {
            currentCallback.onToken(token);
            currentStreamingResponse.append(token);
        }
    }
    
    public void onNativeError(String error) {
        Log.e(TAG, "Native error: " + error);
        
        if (currentCallback != null) {
            currentCallback.onToken("[Error: " + error + "]");
        }
        
        // Make sure generation is completed
        if (isGenerating.get()) {
            completeGeneration();
        }
    }
    
    public void onNativeCompletion(String text) {
        Log.d(TAG, "Native completion received, length: " + (text != null ? text.length() : 0));
        
        // Make sure generation is completed
        if (isGenerating.get()) {
            completeGeneration();
        }
    }
} 