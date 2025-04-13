package com.mtkresearch.breeze_app.service;

import android.content.Intent;
import android.os.IBinder;
// import android.os.Process; // No longer needed for process ID
import android.util.Log;
import android.os.Handler; // Keep if used for UI updates, otherwise remove

// Keep other necessary imports...
import org.pytorch.executorch.LlamaModule;
import org.pytorch.executorch.LlamaCallback;
import com.executorch.ModelUtils;
import com.executorch.PromptFormat;
import com.executorch.ModelType;
import com.mtkresearch.breeze_app.utils.ConversationManager;
import com.mtkresearch.breeze_app.utils.AppConstants;
import com.mtkresearch.breeze_app.service.bridge.MTKNativeBridge; // Assuming this exists and works

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
// import java.util.concurrent.Future; // No longer needed for cleanup futures
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.File;

import com.mtkresearch.breeze_app.service.llm.LLMBackend;
import com.mtkresearch.breeze_app.service.llm.LLMBackendFactory;
// import com.mtkresearch.breeze_app.service.llm.backends.CPUBackend; // Imported via factory
// import com.mtkresearch.breeze_app.service.llm.backends.MTKBackend; // Imported via factory

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutionException;


public class LLMEngineService extends BaseEngineService implements LlamaCallback {
    private static final String TAG = AppConstants.MTK_SERVICE_TAG;
    
    // --- Removed MTK Backend static variables and cleanupExecutor ---
    // private static int mtkInitCount = 0;
    // private static boolean isCleaningUp = false;
    // private static final ExecutorService cleanupExecutor = Executors.newSingleThreadExecutor();

    // Keep Handler if needed for posting results/tokens to UI thread
    // private final Handler handler = new Handler();

    // The bridge for native method calls (instance variable is better)
    private MTKNativeBridge mtkBridge;

    // Current active backend - can be switched at runtime
    private LLMBackend currentBackend = null;
    private LLMBackendFactory backendFactory;

    // --- Static initializer simplified ---
    static {
        try {
            // Try to load sigchain library first for signal handling
            try {
                System.loadLibrary("sigchain");
                Thread.sleep(100);  // Give time for signal handlers to initialize
                Log.d(TAG, "Loaded sigchain library for signal handling");
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "Failed to load sigchain library", e);
            }
            
            // Now initialize MTK bridge which loads llm_jni
            boolean success = MTKNativeBridge.initialize(); // Attempt to load native libs
            Log.d(TAG, "MTK Native Bridge library load result: " + success);
            
            // We'll determine actual MTK backend availability during runtime initialization
            // If the native library isn't available, we'll fall back to CPU during initialization
        } catch (UnsatisfiedLinkError | Exception e) { // Catch specific link errors too
            Log.w(TAG, "Failed to load MTK native libraries, falling back to CPU backend", e);
            AppConstants.MTK_BACKEND_AVAILABLE = false;
        }
    }
    
    // Method to check availability (could be refined based on actual init success)
    public static boolean isMTKBackendAvailable() {
        // Delegate to AppConstants
        return AppConstants.isMTKBackendAvailable();
    }
    
    // Service state
    private String currentBackendName = AppConstants.BACKEND_NONE;
    private String preferredBackend = AppConstants.BACKEND_MTK; // Set MTK as default preference
    private final ConversationManager conversationManager;
    
    // Add a lock object and flag to track initialization in progress
    private final Object initLock = new Object();
    private volatile boolean initializationInProgress = false;
    private CompletableFuture<Boolean> currentInitFuture = null;
    
    // Generation state
    private final AtomicBoolean isGenerating = new AtomicBoolean(false);
    private CompletableFuture<String> currentResponse = null; // Initialize lazily
    private StreamingResponseCallback currentCallback = null;
    private final StringBuilder currentStreamingResponse = new StringBuilder();
    private ExecutorService executor; // Single thread for inference calls

    // CPU backend specific fields (Consider moving inside CPUBackend class)
    private LlamaModule mModule = null; // Legacy, should be managed by CPUBackend
    private String modelPath = null;  // Set from intent, needed by both backends potentially
    // private String resultMessage = ""; // Seems unused
    private long modelLoadTime = 0; // Potentially move to CPUBackend

    public interface StreamingResponseCallback {
        void onToken(String token);
    }

    public LLMEngineService() {
        this.conversationManager = new ConversationManager();
        // Initialize executor here to avoid null pointer exceptions
        this.executor = Executors.newSingleThreadExecutor();
        Log.d(TAG, "Created executor in constructor");
        
        // Get bridge instance IF libraries loaded successfully
        if (MTKNativeBridge.isLibraryLoaded()) {
            this.mtkBridge = MTKNativeBridge.getInstance();
            // No longer need to register service with bridge
            Log.d(TAG, "Created MTK bridge instance, MTK backend should be available");
        } else {
            this.mtkBridge = null;
            Log.d(TAG, "MTK Native Bridge not loaded, MTK backend will be unavailable");
        }
        this.backendFactory = new LLMBackendFactory.Default(this); // Pass context if needed
    }

    // --- onResult, onStats remain the same ---
    @Override
    public void onResult(String result) {
        // This is tied to the legacy CPU LlamaModule callback.
        // Ideally, both backends would use the generateStreamingResponse callback.
        if (currentBackendName.equals(AppConstants.BACKEND_CPU) && mModule != null) {
        if (result == null || result.isEmpty() || !isGenerating.get()) {
            return;
        }
        
        // Check for stop token
        if (result.equals(PromptFormat.getStopToken(ModelType.LLAMA_3_2)) || 
            result.equals(AppConstants.LLM_STOP_TOKEN_EOT) || 
            result.equals(AppConstants.LLM_STOP_TOKEN_EOT_ALT)) {
            Log.d(TAG, "Stop token detected: " + result);
                completeGeneration(false); // Don't add stop token itself
                return;
        }
        
        // Directly append token to the response without UTF-8 checking
        currentStreamingResponse.append(result);
        
        // Send token to callback if streaming
        if (currentCallback != null) {
            currentCallback.onToken(result);
            }
        } else {
             // If MTK backend is active, it should use the onNativeToken callback mechanism
             // This onResult might be incorrectly called or unnecessary for MTK.
             Log.w(TAG, "onResult called but current backend is " + currentBackendName);
        }
    }

    @Override
    public void onStats(float tps) {
        Log.d(TAG, String.format("Generation speed: %.2f tokens/sec", tps));
        // This is also likely tied to the legacy CPU LlamaModule.
        // MTK backend might need its own TPS reporting mechanism.
    }

    public class LocalBinder extends BaseEngineService.LocalBinder<LLMEngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand received");
        
        // Make sure executor is initialized
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
            Log.d(TAG, "Created new executor in onStartCommand");
        }
        
        // Determine model path (looks okay)
        if (intent != null && intent.hasExtra("model_path")) {
                modelPath = intent.getStringExtra("model_path");
            Log.d(TAG, "Using model path from intent: " + modelPath);
            } else {
                modelPath = AppConstants.getModelPath(this);
                Log.d(TAG, "Using default model path: " + modelPath);
            }
        if (modelPath == null || modelPath.isEmpty()) {
             Log.e(TAG, "Model path is invalid or not found!");
             // Handle error appropriately - maybe notify UI or stop service
             stopSelf();
             return START_NOT_STICKY;
        }

        // Determine preferred backend - Default to MTK if available
        if (intent != null && intent.hasExtra("preferred_backend")) {
                String newBackend = intent.getStringExtra("preferred_backend");
            Log.d(TAG, "Intent specified preferred backend: " + newBackend);
                if (!newBackend.equals(preferredBackend)) {
                 Log.d(TAG, "Preferred backend changed from " + preferredBackend + " to " + newBackend);
                    preferredBackend = newBackend;
                 // Force reinitialization if backend preference changed
                 if (isInitialized) { // Only if already initialized
                     releaseResources(); // Release old backend
                     synchronized (initLock) {
                    isInitialized = false;
                }
                 }
            }
        } else {
             // If no preference specified, prefer MTK if available, else CPU
             if (MTKNativeBridge.isLibraryLoaded() && AppConstants.MTK_BACKEND_AVAILABLE) {
                 preferredBackend = AppConstants.BACKEND_MTK;
                 Log.d(TAG, "No backend specified, defaulting to MTK backend");
             } else {
                 preferredBackend = AppConstants.BACKEND_CPU;
                 Log.d(TAG, "MTK backend not available, defaulting to CPU backend");
             }
        }

        // Start initialization if not already initialized
        if (!isInitialized && !initializationInProgress) {
            Log.d(TAG, "Starting initialization process");
            initialize();
        }

        return START_STICKY; // Keep service running
    }

    // Method to initialize the LLM backend
    public synchronized CompletableFuture<Boolean> initialize() {
        synchronized (initLock) {
            if (isInitialized) {
                Log.d(TAG, "Already initialized, returning success directly");
                return CompletableFuture.completedFuture(true);
            }
            
            if (initializationInProgress) {
                Log.d(TAG, "Initialization already in progress");
                if (currentInitFuture != null) {
                    return currentInitFuture;
                }
                return CompletableFuture.completedFuture(false);
            }
            
            initializationInProgress = true;
            currentInitFuture = new CompletableFuture<>();
        }
        
        Log.d(TAG, "Starting backend initialization with preferred backend: " + preferredBackend);
        
        // Default to preferred backend but handle missing libraries
        String backendToUse = preferredBackend;
        
        // Check if MTK is preferred but not available
        if (backendToUse.equals(AppConstants.BACKEND_MTK) && 
            !AppConstants.isMTKBackendAvailable()) {
            Log.w(TAG, "MTK backend was requested but is not available. Falling back to CPU backend.");
            backendToUse = AppConstants.BACKEND_CPU;
        }
        
        LLMBackend backend = null;
        
        try {
            switch (backendToUse) {
                case AppConstants.BACKEND_MTK:
                    if (AppConstants.isMTKBackendAvailable()) {
                        backend = backendFactory.createMTKBackend();
                        Log.d(TAG, "Created MTK backend instance");
                    } else {
                        Log.w(TAG, "MTK backend not available. Falling back to CPU backend.");
                        backend = backendFactory.createCPUBackend(modelPath);
                        backendToUse = AppConstants.BACKEND_CPU;
                        Log.d(TAG, "Created CPU backend instance (fallback from MTK)");
                    }
                    break;
                    
                case AppConstants.BACKEND_CPU:
                default:
                    backend = backendFactory.createCPUBackend(modelPath);
                    backendToUse = AppConstants.BACKEND_CPU;
                    Log.d(TAG, "Created CPU backend instance");
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating backend", e);
            completeInitialization(false);
            return currentInitFuture;
        }
        
        if (backend == null) {
            Log.e(TAG, "Failed to create backend, initialization failed");
            completeInitialization(false);
            return currentInitFuture;
        }
        
        // Store the selected backend and name
        final LLMBackend finalBackend = backend;
        final String finalBackendName = backendToUse;
        
        // Start initialization asynchronously
        return finalBackend.initialize()
            .thenApply(success -> {
                if (success) {
                    synchronized (initLock) {
                        currentBackend = finalBackend;
                        currentBackendName = finalBackendName;
                        isInitialized = true;
                    }
                    Log.i(TAG, "Successfully initialized " + finalBackendName + " backend");
                } else {
                    Log.e(TAG, "Failed to initialize " + finalBackendName + " backend");
                }
                
                completeInitialization(success);
                return success;
            })
            .exceptionally(e -> {
                Log.e(TAG, "Exception during " + finalBackendName + " initialization", e);
                completeInitialization(false);
                return false;
            });
    }
    
    // Helper method to complete initialization cleanly
    private void completeInitialization(boolean success) {
        synchronized (initLock) {
            if (!success) {
                isInitialized = false;
            }
            initializationInProgress = false;
            if (currentInitFuture != null && !currentInitFuture.isDone()) {
                currentInitFuture.complete(success);
            }
        }
    }

    // --- loadLocalModel is specific to the old CPU LlamaModule, should be part of CPUBackend ---
    // private void loadLocalModel() { ... } // Remove or move to CPUBackend
    
    public String getModelName() {
        // Maybe delegate to backend or use modelPath directly
        if (currentBackend != null) {
            // return currentBackend.getModelName(); // If backend provides this
        }
        if (modelPath != null) {
        return com.mtkresearch.breeze_app.utils.ModelUtils.getModelDisplayName(modelPath);
        }
        return "Unknown";
    }
    
    public CompletableFuture<String> generateStreamingResponse(String prompt, StreamingResponseCallback callback) {
        // Use the utility method to ensure executor is initialized
        executor = AppConstants.ensureExecutor(executor, TAG);
        
        if (!isInitialized || currentBackend == null) {
            Log.e(TAG, "generateStreamingResponse called but service not initialized or no backend.");
            if (callback != null) {
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE);
            }
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }
        if (isGenerating.get()) {
            Log.w(TAG, "Already generating, ignoring new request.");
            return CompletableFuture.failedFuture(new IllegalStateException("Already generating"));
        }
        
        currentCallback = callback;
        currentStreamingResponse.setLength(0);
        isGenerating.set(true);
        currentResponse = new CompletableFuture<>(); // Create new future for this request

        // Delegate to the current backend
        executor.submit(() -> { // Ensure backend generation runs on the executor thread
            try {
                 currentBackend.generateStreamingResponse(prompt, token -> {
                     // This callback might be called from a native thread (for MTK)
                     // or the executor thread (for CPU).
                     // If UI updates are needed, the callback implementation itself
                     // should post to the main thread Handler.
                     if (callback != null && isGenerating.get()) {
                         try {
                             callback.onToken(token);
                         } catch (Exception e) {
                             Log.e(TAG, "Error in stream callback", e);
                             // Don't let callback exceptions affect generation
                         }
                     }
                
                     if (isGenerating.get()) {
                         currentStreamingResponse.append(token);
                     }
                 }).thenAccept(finalResponse -> {
                     // Called when backend completes successfully
                     completeGeneration(true); // Mark as complete, use accumulated response
                 }).exceptionally(e -> {
                     // Called if backend future completes exceptionally
                     Log.e(TAG, "Error during streaming response generation in backend", e);
                     completeGenerationWithError(e);
                     return null; // Required for exceptionally
                 });
            } catch (Exception e) {
                 Log.e(TAG, "Error submitting generation task", e);
                 completeGenerationWithError(e);
            }
        });

        return currentResponse; // Return the future that will be completed later
    }
    
    public void stopGeneration() {
        Log.d(TAG, "stopGeneration called. isGenerating=" + isGenerating.get());
        if (!isGenerating.get()) {
            return; // Nothing to stop
        }
        
        // Set flag first to stop processing further tokens in callbacks
        isGenerating.set(false);
        
        // Delegate stop request to the backend
        if (currentBackend != null) {
            Log.d(TAG, "Requesting backend " + currentBackendName + " to stop generation.");
            
            // Use the utility method to ensure executor is initialized
            executor = AppConstants.ensureExecutor(executor, TAG);
            
            executor.submit(() -> { // Run stop on executor thread
                 currentBackend.stopGeneration();
                 Log.d(TAG, "Backend stopGeneration call completed.");
                 // Complete the response *after* backend acknowledges stop
                 completeGeneration(true); // Complete with whatever was generated so far
            });
        } else {
            // If no backend, just complete the future immediately
            completeGeneration(true);
        }
    }

    // Public method to release resources, e.g., called by UI before unbinding
    public void releaseResources() {
        Log.d(TAG, "Public releaseResources called.");
        // Use synchronized block if there's potential concurrency
        synchronized (this) {
            releaseResourcesInternal();
        }
    }

    // Internal method to perform actual resource release
    private void releaseResourcesInternal() {
        Log.d(TAG, "Releasing resources for backend: " + currentBackendName);
        
        // Don't proceed if no backend is set
        if (currentBackendName.equals(AppConstants.BACKEND_NONE) || currentBackend == null) {
            Log.d(TAG, "No active backend to release");
            return;
        }
        
        // If initialization is in progress, don't release yet
        if (initializationInProgress) {
            Log.d(TAG, "Initialization in progress, skipping immediate release");
            return;
        }
        
        if (isGenerating.get()) {
            stopGeneration(); // Request stop if generating
            // Give a brief moment for stop to take effect before proceeding with release
            try {
                Thread.sleep(100); // Small delay to let stop process
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Release backend resources
        if (currentBackend != null) {
            try {
                // Run release on executor thread to avoid blocking caller
                // and handle potential native hangs within the backend's release
                Log.d(TAG, "Creating release executor for backend: " + currentBackendName);
                ExecutorService releaseExecutor = Executors.newSingleThreadExecutor();
                releaseExecutor.submit(() -> {
                    Log.d(TAG, "Submitting release task for backend: " + currentBackendName);
                    currentBackend.releaseResources();
                    Log.d(TAG, "Backend release task completed for: " + currentBackendName);
                }).get(AppConstants.MTK_CLEANUP_TIMEOUT_MS, TimeUnit.MILLISECONDS); // Add timeout
                releaseExecutor.shutdown();

            } catch (Exception e) {
                Log.e(TAG, "Error during backend resource release", e);
                // Decide how critical this is. Maybe force kill native process?
            } finally {
                currentBackend = null; // Ensure backend reference is cleared
            }
        }
            
        isInitialized = false;
        currentBackendName = AppConstants.BACKEND_NONE;
        Log.d(TAG, "Resource release finished.");
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy() called. Releasing resources.");
        // Ensure resources are released when service is destroyed
        // Use synchronized block for thread safety if needed
        synchronized(this) {
            releaseResourcesInternal();
        }
        
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
            executor = null;
        }

        // No longer need to unregister since we're not registering

        super.onDestroy();
        Log.d(TAG, "onDestroy() finished.");
    }
    
    public String getCurrentBackend() {
        return currentBackendName;
    }
    
    public String getPreferredBackend() {
        return preferredBackend;
    }

    // Switch backend at runtime
    public CompletableFuture<Boolean> switchBackend(String requestedBackendName) {
         Log.d(TAG, "Request to switch backend to: " + requestedBackendName + ". Current: " + currentBackendName);
        
        // Safety check for null/empty backend name
        if (requestedBackendName == null || requestedBackendName.isEmpty()) {
            Log.e(TAG, "Invalid backend name requested");
            return CompletableFuture.completedFuture(false);
        }
        
        if (requestedBackendName.equals(currentBackendName)) {
            Log.d(TAG, "Already using requested backend.");
            return CompletableFuture.completedFuture(true);
        }

        // Prevent switching if initialization is in progress
        synchronized (initLock) {
            if (initializationInProgress) {
                Log.w(TAG, "Cannot switch backend while initialization is in progress");
                return CompletableFuture.completedFuture(false);
            }
            
            // Mark initialization as in progress to prevent concurrent switches
            initializationInProgress = true;
        }

        CompletableFuture<Boolean> switchFuture = new CompletableFuture<>();

        // Use the utility method to ensure executor is initialized
        executor = AppConstants.ensureExecutor(executor, TAG);

        executor.submit(() -> {
            try {
                synchronized(this) { // Synchronize to prevent concurrent modification
                    if (isGenerating.get()) {
                        Log.d(TAG, "Stopping current generation before switching backend.");
                        stopGeneration(); // Needs to properly wait or handle async stop
                        
                        // Give the stop operation a little time to take effect
                        try {
                            Thread.sleep(500); // Wait a bit for stop to complete
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            Log.w(TAG, "Interrupted while waiting for generation to stop");
                        }
                    }
    
                    Log.d(TAG, "Releasing current backend: " + currentBackendName);
                    
                    // Only release if we actually have a backend
                    if (currentBackend != null) {
                        releaseResourcesInternal(); // Release the current one
                    }
    
                    // Set the new preference
                    preferredBackend = requestedBackendName;
    
                    Log.d(TAG, "Initiating initialization for new backend: " + requestedBackendName);
                    // Call initialize again, it will pick up the new preference
                    initialize().thenAccept(success -> {
                        Log.d(TAG, "Switch backend completed. Success: " + success + ". Final backend: " + currentBackendName);
                        switchFuture.complete(success);
                        
                        // Make sure to reset the initialization flag
                        synchronized (initLock) {
                            initializationInProgress = false;
                        }
                    }).exceptionally(e -> {
                        Log.e(TAG, "Error during backend switch initialization", e);
                        switchFuture.complete(false);
                        
                        // Make sure to reset the initialization flag
                        synchronized (initLock) {
                            initializationInProgress = false;
                        }
                        return null;
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Error during backend switch", e);
                switchFuture.complete(false);
                
                // Make sure to reset the initialization flag
                synchronized (initLock) {
                    initializationInProgress = false;
                }
            }
        });

        return switchFuture;
    }

    // Internal method to finalize generation state, called on completion or stop
    private void completeGeneration(boolean wasStopped) {
        if (isGenerating.compareAndSet(true, false)) {
            Log.d(TAG, "Completing generation process. Was stopped: " + wasStopped);

            // Complete the response future
            if (currentResponse != null && !currentResponse.isDone()) {
                 String finalResponse = currentStreamingResponse.toString();
                 // Maybe add a "[Stopped]" message if wasStopped?
                 if (wasStopped && finalResponse.isEmpty()) {
                     finalResponse = "[Generation stopped]";
                 }
                 Log.d(TAG, "Final response length: " + finalResponse.length());
                currentResponse.complete(finalResponse);
            } else {
                 Log.d(TAG, "Generation completion called but future already done or null.");
            }
            currentCallback = null; // Clear callback
            currentStreamingResponse.setLength(0); // Clear buffer
        }
    }

    // Complete generation with an error
    private void completeGenerationWithError(Throwable error) {
        if (isGenerating.compareAndSet(true, false)) {
             Log.e(TAG, "Completing generation process with error", error);
             if (currentResponse != null && !currentResponse.isDone()) {
                  currentResponse.completeExceptionally(error);
             }
             currentCallback = null;
             currentStreamingResponse.setLength(0);
        }
    }


    // --- Removed MTK Backend specific cleanup methods ---
    // private static void cleanupMTKResources() { ... }
    // private void forceCleanupMTKResources() { ... }
    // private void cleanupAfterError() { ... }


    // --- Callbacks from Native Bridge (MTK Backend) ---
    // Ensure these methods exist if your MTKNativeBridge calls them

    /** Called by native code (via MTKNativeBridge) when a token is generated */
    public void onNativeToken(String token) {
        // Ensure this runs on a thread that can safely update state
        // Consider posting to handler or using concurrent collections if needed
        if (isGenerating.get()) {
            if (currentCallback != null) {
                // If callback needs UI thread, it should handle posting internally
                currentCallback.onToken(token);
            }
            currentStreamingResponse.append(token);
        } else {
            Log.w(TAG, "onNativeToken received but not generating: " + token);
        }
    }

    /** Called by native code (via MTKNativeBridge) on fatal error */
    public void onNativeError(String error) {
         Log.e(TAG, "Native error reported: " + error);
        completeGenerationWithError(new RuntimeException("Native Error: " + error));
    }

    /** Called by native code (via MTKNativeBridge) on normal completion */
    public void onNativeCompletion(String text) {
        Log.d(TAG, "Native completion received, length: " + (text != null ? text.length() : 0));
        
        // Make sure generation is completed
                    if (isGenerating.get()) {
            completeGeneration(false); // Treat as normal completion
        }
    }
} 