package com.mtkresearch.breeze_app.service.llm.backends;

import android.util.Log;

import com.mtkresearch.breeze_app.service.LLMEngineService;
import com.mtkresearch.breeze_app.service.LLMEngineService.StreamingResponseCallback;
import com.mtkresearch.breeze_app.service.bridge.JNIBridge.StreamingCallback;
import com.mtkresearch.breeze_app.service.bridge.MTKNativeBridge;
import com.mtkresearch.breeze_app.service.llm.LLMBackend;
import com.mtkresearch.breeze_app.utils.AppConstants;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MTK-accelerated backend implementation for LLM inference.
 * This backend uses MTK's hardware acceleration capabilities.
 */
public class MTKBackend implements LLMBackend, StreamingCallback {
    private static final String TAG = "MTKBackend";
    private static final String BACKEND_NAME = "MTK";
    
    private final ExecutorService nativeExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean initializing = new AtomicBoolean(false);
    private final AtomicBoolean generating = new AtomicBoolean(false);
    private final ReentrantLock lock = new ReentrantLock();
    private CompletableFuture<Boolean> initializationFuture = null;
    private MTKNativeBridge mtkBridge;
    private String modelPath;
    private String configPath;
    
    // Constants for token sizes
    private static final int PROMPT_TOKEN_SIZE = 128; // For parsing prompts
    private static final int GENERATION_TOKEN_SIZE = 1; // For generating responses
    private static final int MAX_RESPONSE_TOKENS = 256; // Default max response tokens
    
    // Add token size tracking - start with prompt token size for parsing
    private boolean usingPromptTokenSize = true; // Starts as true since we initialize with prompt token size
    
    // Store the current callback
    private StreamingResponseCallback currentCallback;
    private StringBuilder responseBuffer = new StringBuilder();
    
    /**
     * Create a new MTKBackend instance.
     *
     * @param mtkBridge MTK native bridge
     * @param modelPath Path to the model file
     * @param configPath Path to the configuration file
     */
    public MTKBackend(MTKNativeBridge mtkBridge, String modelPath, String configPath) {
        this.mtkBridge = mtkBridge;
        this.modelPath = modelPath;
        this.configPath = configPath;
    }
    
    @Override
    public CompletableFuture<Boolean> initialize() {
        lock.lock();
        try {
            if (initialized.get()) {
                Log.d(TAG, "MTK backend already initialized");
                return CompletableFuture.completedFuture(true);
            }

            if (initializing.getAndSet(true)) {
                Log.d(TAG, "MTK backend already initializing");
                if (initializationFuture != null) {
                    return initializationFuture;
                }
                return CompletableFuture.completedFuture(false);
            }

            Log.d(TAG, "Starting MTK backend initialization");
            initializationFuture = new CompletableFuture<>();

            // Validate MTK bridge
            if (mtkBridge == null) {
                String error = "MTK Bridge is null";
                Log.e(TAG, error);
                initializing.set(false);
                initializationFuture.completeExceptionally(new IllegalStateException(error));
                return initializationFuture;
            }

            // Validate config file
            File configFile = new File(configPath);
            if (!configFile.exists() || !configFile.canRead()) {
                String error = "Config file at " + configPath + " does not exist or cannot be read";
                Log.e(TAG, error);
                initializing.set(false);
                initializationFuture.completeExceptionally(new IllegalStateException(error));
                return initializationFuture;
            }

            // Submit initialization task to executor with timeout
            CompletableFuture.runAsync(() -> {
                try {
                    Log.d(TAG, "Attempting to initialize MTK LLM with " + configPath);
                    boolean initSuccess = mtkBridge.nativeInitLlm(configPath, true);

                    if (initSuccess) {
                        Log.d(TAG, "MTK LLM initialization successful");
                        
                        // Set initial token size to prompt token size
                        boolean tokenSizeSuccess = mtkBridge.nativeSetTokenSize(PROMPT_TOKEN_SIZE);
                        if (tokenSizeSuccess) {
                            Log.d(TAG, "Successfully set initial token size to " + PROMPT_TOKEN_SIZE);
                            usingPromptTokenSize = true;
                        } else {
                            Log.e(TAG, "Failed to set initial token size to " + PROMPT_TOKEN_SIZE);
                            // Continue anyway as we'll try again during inference
                        }
                        
                        initialized.set(true);
                        initializing.set(false);
                        initializationFuture.complete(true);
                    } else {
                        String error = "MTK LLM initialization failed";
                        Log.e(TAG, error);
                        initializing.set(false);
                        initializationFuture.completeExceptionally(new IllegalStateException(error));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception during MTK LLM initialization", e);
                    initializing.set(false);
                    initializationFuture.completeExceptionally(e);
                }
            }, nativeExecutor).orTimeout(30, TimeUnit.SECONDS).exceptionally(e -> {
                if (e instanceof TimeoutException) {
                    Log.e(TAG, "Timeout during MTK LLM initialization");
                } else {
                    Log.e(TAG, "Exception during MTK LLM initialization", e);
                }
                initializing.set(false);
                initializationFuture.completeExceptionally(e);
                return null;
            });

            return initializationFuture;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public boolean isAvailable() {
        return mtkBridge != null && mtkBridge.isLibraryLoaded();
    }
    
    @Override
    public CompletableFuture<String> generateResponse(String prompt) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        if (!initialized.get()) {
            Log.e(TAG, "Cannot generate response - MTK backend not initialized");
            future.completeExceptionally(new IllegalStateException("MTK backend not initialized"));
            return future;
        }

        if (generating.getAndSet(true)) {
            Log.e(TAG, "MTK backend already generating");
            future.completeExceptionally(new IllegalStateException("MTK backend already generating"));
            return future;
        }

        Log.d(TAG, "Starting response generation with prompt length: " + prompt.length());
        
        CompletableFuture.runAsync(() -> {
            try {
                // First ensure we're using prompt token size 128 for parsing
                if (!usingPromptTokenSize) {
                    boolean swapSuccess = mtkBridge.nativeSetTokenSize(PROMPT_TOKEN_SIZE);
                    Log.d(TAG, "Setting prompt token size (" + PROMPT_TOKEN_SIZE + 
                          ") for prompt parsing: " + swapSuccess);
                    usingPromptTokenSize = swapSuccess;
                    
                    if (!swapSuccess) {
                        Log.e(TAG, "Failed to set prompt token size for parsing");
                        future.completeExceptionally(new RuntimeException("Failed to set prompt token size"));
                        generating.set(false);
                        return;
                    }
                }
                
                // For regular inference, the native side handles everything, we don't need to swap
                String result = mtkBridge.nativeInference(prompt, MAX_RESPONSE_TOKENS, false);
                Log.d(TAG, "MTK response generation completed");
                
                // Reset LLM state after generation
                try {
                    mtkBridge.nativeResetLlm();
                    
                    // Ensure we're using prompt token size 128 after generation
                    if (!usingPromptTokenSize) {
                        boolean swapBackSuccess = mtkBridge.nativeSetTokenSize(PROMPT_TOKEN_SIZE);
                        usingPromptTokenSize = swapBackSuccess;
                        Log.d(TAG, "Reset and switched back to prompt token size (" + 
                              PROMPT_TOKEN_SIZE + ") after generation: " + swapBackSuccess);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error resetting state after generation", e);
                    // Continue anyway to deliver result
                }
                
                future.complete(result);
            } catch (Exception e) {
                Log.e(TAG, "Exception during MTK response generation", e);
                
                // Attempt to reset state even after error
                try {
                    mtkBridge.nativeResetLlm();
                    
                    // Ensure we're back at prompt token size 128 after error
                    if (!usingPromptTokenSize) {
                        boolean swapBackSuccess = mtkBridge.nativeSetTokenSize(PROMPT_TOKEN_SIZE);
                        usingPromptTokenSize = swapBackSuccess;
                        Log.d(TAG, "Reset state after error and switched back to prompt token size: " + swapBackSuccess);
                    }
                } catch (Exception resetError) {
                    Log.e(TAG, "Error resetting state after generation error", resetError);
                }
                
                future.completeExceptionally(e);
            } finally {
                generating.set(false);
            }
        }, nativeExecutor);
        
        return future;
    }
    
    @Override
    public CompletableFuture<String> generateStreamingResponse(String prompt, StreamingResponseCallback callback) {
        lock.lock();
        try {
            if (!initialized.get()) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("MTK backend not initialized"));
            }

            if (generating.getAndSet(true)) {
                String error = "MTK backend is already generating";
                Log.e(TAG, error);
                return CompletableFuture.failedFuture(new IllegalStateException(error));
            }

            Log.d(TAG, "MTK backend generating streaming response");
            CompletableFuture<String> resultFuture = new CompletableFuture<>();
            
            // Store the callback for use in onToken
            this.currentCallback = callback;
            this.responseBuffer.setLength(0);

            CompletableFuture.runAsync(() -> {
                try {
                    // The MTKNativeBridge.nativeRunStreamingInference handles token size management:
                    // 1. It ensures token size 128 for prompt parsing
                    // 2. Native code then swaps to token size 1 for generation
                    // 3. It also ensures we return to token size 128 after generation
                    boolean inferenceSuccess = mtkBridge.nativeRunStreamingInference(prompt, this);
                    
                    if (inferenceSuccess) {
                        Log.d(TAG, "MTK streaming inference completed successfully");
                        String finalResponse = responseBuffer.toString();
                        resultFuture.complete(finalResponse);
                    } else {
                        String error = "MTK streaming inference failed";
                        Log.e(TAG, error);
                        resultFuture.completeExceptionally(new RuntimeException(error));
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception during MTK streaming inference", e);
                    resultFuture.completeExceptionally(e);
                } finally {
                    // Reset LLM state for next use
                    try {
                        boolean resetSuccess = mtkBridge.nativeResetLlm();
                        if (!resetSuccess) {
                            Log.e(TAG, "Failed to reset LLM state after streaming inference");
                        }
                        
                        // MTKNativeBridge already ensures we're back at token size 128,
                        // but we update our tracking flag to match
                        usingPromptTokenSize = true;
                    } catch (Exception e) {
                        Log.e(TAG, "Exception during LLM reset after streaming inference", e);
                    }
                    
                    generating.set(false);
                    currentCallback = null; // Clear the callback reference
                }
            }, nativeExecutor).exceptionally(e -> {
                Log.e(TAG, "Exception during streaming generation", e);
                resultFuture.completeExceptionally(e);
                generating.set(false);
                currentCallback = null; // Clear the callback reference
                return null;
            });

            return resultFuture;
        } finally {
            lock.unlock();
        }
    }
    
    @Override
    public void stopGeneration() {
        if (!initialized.get() || !generating.get()) {
            return;
        }

        Log.d(TAG, "Stopping MTK generation");
        
        // Set generating flag to false early to prevent further token processing
        generating.set(false);
        
        // Use try-finally to ensure we always update flags even if an exception occurs
        try {
            if (lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                try {
                    if (mtkBridge != null) {
                        // First reset the LLM
                        try {
                            boolean resetSuccess = mtkBridge.nativeResetLlm();
                            Log.d(TAG, "Reset LLM during stop generation: " + resetSuccess);
                            
                            // Give a small pause before trying to swap token size to let reset complete
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            
                            // Only try to swap token size back if we successfully reset
                            if (resetSuccess && !usingPromptTokenSize) {
                                try {
                                    boolean swapBackSuccess = mtkBridge.nativeSetTokenSize(PROMPT_TOKEN_SIZE);
                                    usingPromptTokenSize = swapBackSuccess;
                                    Log.d(TAG, "Reset and swapped back to prompt token size after stopping generation: " + swapBackSuccess);
                                } catch (Exception e) {
                                    Log.e(TAG, "Error swapping back to prompt token size", e);
                                    // Continue without failing - we'll try again later
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error resetting LLM during stop generation", e);
                            // Continue despite error - we need to ensure flags are reset
                        }
                    }
                } finally {
                    currentCallback = null; // Clear the callback when stopping
                    lock.unlock();
                }
            } else {
                Log.w(TAG, "Could not acquire lock for stopGeneration - continuing without reset");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while trying to acquire lock for stopGeneration", e);
            Thread.currentThread().interrupt();
        } finally {
            // Ensure callback is cleared even if we couldn't get the lock
            currentCallback = null;
        }
    }
    
    @Override
    public boolean reset() {
        Log.d(TAG, "Resetting MTK backend");
        boolean success = false;
        
        try {
            if (lock.tryLock(500, TimeUnit.MILLISECONDS)) {
                try {
                    if (!initialized.get()) {
                        Log.d(TAG, "Backend not initialized, nothing to reset");
                        return true; // Nothing to reset
                    }
                    
                    // Reset native state
                    try {
                        boolean resetSuccess = mtkBridge.nativeResetLlm();
                        Log.d(TAG, "LLM native reset result: " + resetSuccess);
                        success = resetSuccess;
                        
                        // Give a small delay for the reset to take effect
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // Always ensure we're back to prompt token size after reset
                        if (!usingPromptTokenSize) {
                            try {
                                Log.d(TAG, "Switching back to prompt token size after reset");
                                boolean swapped = mtkBridge.nativeSetTokenSize(PROMPT_TOKEN_SIZE);
                                usingPromptTokenSize = swapped;
                                if (!swapped) {
                                    Log.w(TAG, "Failed to switch back to prompt token size after reset");
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error switching token size after reset", e);
                                // Don't set success to false if only token size switching failed
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error during reset", e);
                        success = false;
                    }
                    
                    // Reset state flags regardless of success
                    generating.set(false);
                    currentCallback = null;
                    
                    return success;
                } finally {
                    lock.unlock();
                }
            } else {
                Log.w(TAG, "Could not acquire lock for reset - skipping");
                return false;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for lock during reset", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }
    
    @Override
    public void releaseResources() {
        Log.d(TAG, "Starting to release MTK backend resources");
        
        try {
            if (lock.tryLock(1000, TimeUnit.MILLISECONDS)) {
                try {
                    // First ensure no generation is running
                    if (generating.get()) {
                        Log.d(TAG, "Stopping active generation before releasing resources");
                        try {
                            stopGeneration();
                            
                            // Small delay to let stop take effect
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error stopping generation during resource release", e);
                            // Continue with release anyway
                        }
                    }
                    
                    if (initialized.get()) {
                        Log.d(TAG, "Releasing MTK backend resources");
                        
                        // First try to reset the LLM to ensure clean state
                        try {
                            boolean resetSuccess = mtkBridge.nativeResetLlm();
                            Log.d(TAG, "Reset LLM before resource release: " + resetSuccess);
                            
                            // Small delay to let reset take effect
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error resetting LLM before resource release", e);
                            // Continue with release anyway
                        }
                        
                        // Then release resources
                        try {
                            mtkBridge.nativeReleaseLlm();
                            Log.d(TAG, "MTK backend resources released successfully");
                        } catch (Exception e) {
                            Log.e(TAG, "Error releasing MTK backend resources", e);
                            
                            // Try one more time if the first attempt failed
                            try {
                                Thread.sleep(200); // Longer delay before retry
                                mtkBridge.nativeReleaseLlm();
                                Log.d(TAG, "MTK backend resources released on second attempt");
                            } catch (Exception e2) {
                                Log.e(TAG, "Failed to release MTK backend resources on second attempt", e2);
                            }
                        } finally {
                            // Always mark as uninitialized, even if exceptions occurred
                            initialized.set(false);
                            generating.set(false);
                            currentCallback = null;
                        }
                    } else {
                        Log.d(TAG, "Backend not initialized, nothing to release");
                    }
                } finally {
                    lock.unlock();
                }
            } else {
                Log.w(TAG, "Could not acquire lock for releaseResources - proceeding without lock");
                // Even without lock, try to release resources as best we can
                try {
                    if (mtkBridge != null && initialized.get()) {
                        mtkBridge.nativeReleaseLlm();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error releasing resources without lock", e);
                } finally {
                    initialized.set(false);
                    generating.set(false);
                    currentCallback = null;
                }
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted while waiting for lock during releaseResources", e);
            Thread.currentThread().interrupt();
        }
        
        Log.d(TAG, "MTK backend resource release process completed");
    }
    
    @Override
    public String getName() {
        return BACKEND_NAME;
    }
    
    @Override
    public long getMemoryUsage() {
        if (!initialized.get()) {
            return 0;
        }
        // No memory usage method available in MTKNativeBridge
        return 0;
    }
    
    /**
     * Called by the native bridge when a token is generated
     */
    @Override
    public void onToken(String token) {
        if (currentCallback != null) {
            try {
                currentCallback.onToken(token);
                responseBuffer.append(token); // Collect tokens for final response
            } catch (Exception e) {
                Log.e(TAG, "Error in token callback", e);
            }
        }
    }
} 