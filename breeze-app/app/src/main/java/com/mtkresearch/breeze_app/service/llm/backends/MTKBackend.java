package com.mtkresearch.breeze_app.service.llm.backends;

import android.util.Log;

import com.mtkresearch.breeze_app.service.llm.LLMBackend;
import com.mtkresearch.breeze_app.utils.AppConstants;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * MTK-accelerated backend implementation for LLM inference.
 * This backend uses MTK's hardware acceleration capabilities.
 */
public class MTKBackend implements LLMBackend {
    private static final String TAG = "MTKBackend";
    private static final String BACKEND_NAME = "MTK";
    
    private final String modelPath;
    private final ExecutorService executorService;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean shouldStop = new AtomicBoolean(false);
    
    // Real implementation that interfaces with the native bridge
    private final NativeMTKBackend mtkBackend;
    
    /**
     * Create a new MTKBackend instance.
     *
     * @param modelPath Path to the model file
     * @param executorService Executor service to run tasks on
     */
    public MTKBackend(String modelPath, ExecutorService executorService) {
        this.modelPath = modelPath;
        this.executorService = executorService;
        this.mtkBackend = new NativeMTKBackend();
        
        Log.d(TAG, "Created MTKBackend with native bridge support. MTK backend available: " + 
              AppConstants.MTK_BACKEND_AVAILABLE);
    }
    
    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            if (isInitialized.get()) {
                Log.d(TAG, "MTK backend already initialized");
                return true;
            }
            
            lock.lock();
            try {
                if (isInitialized.get()) {
                    return true;
                }
                
                Log.d(TAG, "Initializing MTK backend with model at " + modelPath);
                boolean success = mtkBackend.initialize(modelPath);
                
                if (success) {
                    isInitialized.set(true);
                    Log.d(TAG, "MTK backend initialization successful");
                } else {
                    Log.e(TAG, "MTK backend initialization failed");
                }
                
                return success;
            } catch (Exception e) {
                Log.e(TAG, "Error initializing MTK backend", e);
                return false;
            } finally {
                lock.unlock();
            }
        }, executorService);
    }
    
    @Override
    public boolean isAvailable() {
        return AppConstants.MTK_BACKEND_AVAILABLE;
    }
    
    @Override
    public CompletableFuture<String> generateResponse(String prompt) {
        if (!isInitialized.get()) {
            return initialize().thenCompose(initialized -> {
                if (initialized) {
                    return doGenerateResponse(prompt);
                } else {
                    CompletableFuture<String> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalStateException("Failed to initialize MTK backend"));
                    return future;
                }
            });
        }
        
        return doGenerateResponse(prompt);
    }
    
    private CompletableFuture<String> doGenerateResponse(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            if (!tryStartGeneration()) {
                throw new IllegalStateException("Generation already in progress");
            }
            
            try {
                Log.d(TAG, "Generating response with MTK backend");
                shouldStop.set(false);
                String response = mtkBackend.generateResponse(prompt);
                return response != null ? response : "";
            } catch (Exception e) {
                Log.e(TAG, "Error generating response with MTK backend", e);
                throw e;
            } finally {
                isGenerating.set(false);
            }
        }, executorService);
    }
    
    @Override
    public CompletableFuture<String> generateStreamingResponse(String prompt, TokenCallback callback) {
        if (!isInitialized.get()) {
            return initialize().thenCompose(initialized -> {
                if (initialized) {
                    return doGenerateStreamingResponse(prompt, callback);
                } else {
                    CompletableFuture<String> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalStateException("Failed to initialize MTK backend"));
                    return future;
                }
            });
        }
        
        return doGenerateStreamingResponse(prompt, callback);
    }
    
    private CompletableFuture<String> doGenerateStreamingResponse(String prompt, TokenCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            if (!tryStartGeneration()) {
                throw new IllegalStateException("Generation already in progress");
            }
            
            try {
                Log.d(TAG, "Generating streaming response with MTK backend");
                shouldStop.set(false);
                
                final StringBuilder fullResponse = new StringBuilder();
                
                // Create a special adapter class that will notify both our callback
                // and check for stop conditions
                class MTKBridgeAdapter implements NativeMTKBackend.StreamingCallback {
                    @Override
                    public void onToken(String token) {
                        if (shouldStop.get()) {
                            return;
                        }
                        fullResponse.append(token);
                        if (callback != null) {
                            callback.onToken(token);
                        }
                    }
                }
                
                MTKBridgeAdapter bridgeAdapter = new MTKBridgeAdapter();
                
                // Generate the response
                mtkBackend.generateStreamingResponse(prompt, bridgeAdapter);
                
                // Return the full accumulated response
                return fullResponse.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error generating streaming response with MTK backend", e);
                throw e;
            } finally {
                isGenerating.set(false);
            }
        }, executorService);
    }
    
    @Override
    public void stopGeneration() {
        if (isGenerating.get()) {
            Log.d(TAG, "Stopping MTK backend generation");
            shouldStop.set(true);
            mtkBackend.stopGeneration();
        }
    }
    
    @Override
    public boolean reset() {
        lock.lock();
        try {
            if (!isInitialized.get()) {
                return false;
            }
            
            Log.d(TAG, "Resetting MTK backend");
            if (isGenerating.get()) {
                stopGeneration();
            }
            
            return mtkBackend.reset();
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
                Log.d(TAG, "Releasing MTK backend resources");
                mtkBackend.releaseResources();
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
        if (!isInitialized.get()) {
            return 0;
        }
        return mtkBackend.getMemoryUsage();
    }
    
    private boolean tryStartGeneration() {
        return !isGenerating.getAndSet(true);
    }
    
    /**
     * Implementation that connects to the native MTK libraries through the bridge
     */
    private static class NativeMTKBackend {
        // Reference to the MTK native bridge
        private final com.mtkresearch.breeze_app.service.bridge.MTKNativeBridge mtkBridge;
        
        public NativeMTKBackend() {
            // Get the singleton instance of the bridge
            this.mtkBridge = com.mtkresearch.breeze_app.service.bridge.MTKNativeBridge.getInstance();
        }
        
        public boolean initialize(String modelPath) {
            Log.d(TAG, "Initializing MTK backend with native bridge");
            
            // First check if the MTK backend is available
            if (!AppConstants.MTK_BACKEND_AVAILABLE) {
                Log.e(TAG, "MTK backend not available - native libraries failed to load");
                return false;
            }
            
            try {
                // Log both paths for debugging
                Log.d(TAG, "Model path provided: " + modelPath);
                Log.d(TAG, "Config path from constants: " + AppConstants.MTK_CONFIG_PATH);
                
                // Check if config file exists
                File configFile = new File(AppConstants.MTK_CONFIG_PATH);
                if (!configFile.exists() || !configFile.isFile()) {
                    Log.e(TAG, "MTK config file does not exist at path: " + AppConstants.MTK_CONFIG_PATH);
                    return false;
                }
                
                Log.d(TAG, "MTK config file exists and is readable");
                
                // Initialize the LLM with the config path
                // Note: The MTK backend uses the config path rather than the model path directly
                boolean initSuccess = mtkBridge.initLlm(AppConstants.MTK_CONFIG_PATH, true);
                
                if (initSuccess) {
                    Log.d(TAG, "Successfully initialized MTK native backend");
                    return true;
                } else {
                    Log.e(TAG, "Failed to initialize MTK native backend");
                    return false;
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception during MTK backend initialization", e);
                return false;
            }
        }
        
        public String generateResponse(String prompt) {
            Log.d(TAG, "Generate response using MTK native bridge for prompt: " + prompt);
            
            try {
                // Use a reasonable token size for generation
                int maxResponseTokens = AppConstants.MTK_TOKEN_SIZE;
                Log.d(TAG, "Using max response tokens: " + maxResponseTokens);
                
                return mtkBridge.inference(prompt, maxResponseTokens, true);
            } catch (Exception e) {
                Log.e(TAG, "Error during MTK inference", e);
                return AppConstants.LLM_ERROR_RESPONSE;
            }
        }
        
        public void generateStreamingResponse(String prompt, StreamingCallback callback) {
            Log.d(TAG, "Generate streaming response using MTK native bridge for prompt: " + prompt);
            
            try {
                // Use a reasonable token size for generation
                int maxResponseTokens = AppConstants.MTK_TOKEN_SIZE;
                Log.d(TAG, "Using max response tokens (streaming): " + maxResponseTokens);
                
                // Create a special adapter class that will notify both our callback
                // and check for stop conditions
                class MTKBridgeAdapter implements com.mtkresearch.breeze_app.service.bridge.MTKNativeBridge.TokenCallback {
                    private final AtomicBoolean shouldStop = new AtomicBoolean(false);
                    
                    public void requestStop() {
                        shouldStop.set(true);
                    }
                    
                    @Override
                    public void onToken(String token) {
                        if (callback != null && !shouldStop.get()) {
                            callback.onToken(token);
                        }
                    }
                }
                
                MTKBridgeAdapter bridgeAdapter = new MTKBridgeAdapter();
                
                // Start generation in a separate thread so we can monitor for stop requests
                Thread generationThread = new Thread(() -> {
                    mtkBridge.streamingInference(prompt, maxResponseTokens, true, bridgeAdapter);
                });
                generationThread.start();
                
                // Wait for generation to complete or be stopped
                try {
                    generationThread.join(30000); // 30 second timeout
                    if (generationThread.isAlive()) {
                        Log.w(TAG, "Generation thread timed out, forcing stop");
                        bridgeAdapter.requestStop();
                        generationThread.interrupt();
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "Generation thread interrupted");
                    bridgeAdapter.requestStop();
                    generationThread.interrupt();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during MTK streaming inference", e);
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE);
            }
        }
        
        public void stopGeneration() {
            Log.d(TAG, "Stopping MTK generation through native bridge");
            try {
                mtkBridge.resetLlm();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MTK generation", e);
            }
        }
        
        public boolean reset() {
            Log.d(TAG, "Resetting MTK backend through native bridge");
            try {
                return mtkBridge.resetLlm();
            } catch (Exception e) {
                Log.e(TAG, "Error resetting MTK backend", e);
                return false;
            }
        }
        
        public void releaseResources() {
            Log.d(TAG, "Releasing MTK resources through native bridge");
            try {
                mtkBridge.releaseLlm();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing MTK resources", e);
            }
        }
        
        public long getMemoryUsage() {
            // Not implemented in the native bridge, return a placeholder value
            return 0;
        }
        
        public interface StreamingCallback {
            void onToken(String token);
        }
    }
} 