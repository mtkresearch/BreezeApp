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
            // Try to initialize the bridge (load libraries)
            boolean success = MTKNativeBridge.initialize(); // Assuming this loads native libs
            Log.d(TAG, "MTK Native Bridge library load result: " + success);
            // We determine actual availability during runtime initialization attempt
            // AppConstants.MTK_BACKEND_AVAILABLE should be set based on successful native init later
        } catch (UnsatisfiedLinkError | Exception e) { // Catch specific link errors too
            Log.w(TAG, "Failed to load MTK native libraries, MTK backend may be unavailable", e);
            // Don't set AppConstants.MTK_BACKEND_AVAILABLE = false here yet.
        }
        // --- Removed ShutdownHook ---
    }

    // Method to check availability (could be refined based on actual init success)
    public static boolean isMTKBackendAvailable() {
        // This might need refinement. Perhaps check if native bridge loaded successfully?
        // Or better, check after a successful *initialization* attempt.
        // For now, assume loaded means potentially available.
        return MTKNativeBridge.isLibraryLoaded(); // Assuming bridge has such a method
    }

    // Service state
    private String currentBackendName = AppConstants.BACKEND_NONE;
    private String preferredBackend = AppConstants.BACKEND_MTK; // Default preference
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
        // Get bridge instance IF libraries loaded successfully
        if (MTKNativeBridge.isLibraryLoaded()) {
            this.mtkBridge = MTKNativeBridge.getInstance();
            this.mtkBridge.registerService(this); // If bridge needs a reference back
        } else {
            this.mtkBridge = null;
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

        // Determine preferred backend (looks okay, but remove forced MTK)
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
             // If no preference specified, use default (e.g., MTK if available, else CPU)
             // We can decide this more intelligently during initialize()
             Log.d(TAG, "No preferred backend specified in intent, will decide during init.");
             // Don't set preferredBackend here, let initialize() decide.
        }
        
        // Check if model needs to be downloaded (looks okay)
        if (AppConstants.needsModelDownload(this)) {
            Log.e(TAG, "Model not found, download required");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        if (executor == null) {
            executor = Executors.newSingleThreadExecutor();
        }
        
        // Ensure initialization is called if needed
        synchronized (initLock) {
            if (!isInitialized && !initializationInProgress) {
                Log.d(TAG, "Starting initialization process");
                initialize(); // Trigger initialization
            } else if (initializationInProgress) {
                Log.d(TAG, "Initialization already in progress, skipping duplicate call");
            } else {
                Log.d(TAG, "Already initialized, skipping initialization");
            }
        }

        return super.onStartCommand(intent, flags, startId); // Or START_STICKY depending on desired behavior
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        Log.d(TAG, "initialize() called. Current state: isInitialized=" + isInitialized + ", currentBackend=" + currentBackendName);
        
        synchronized (initLock) {
            // Already initialized - return completed future
            if (isInitialized) {
                Log.d(TAG, "Already initialized with backend: " + currentBackendName);
                return CompletableFuture.completedFuture(true);
            }
            
            // Check if initialization is already in progress
            if (initializationInProgress && currentInitFuture != null) {
                Log.d(TAG, "Initialization already in progress, returning existing future");
                return currentInitFuture;
            }
            
            // Mark initialization as in progress
            initializationInProgress = true;
            
            if (executor == null) { // Ensure executor exists
                executor = Executors.newSingleThreadExecutor();
            }
            
            // Create a new future for this initialization attempt
            CompletableFuture<Boolean> overallInitFuture = new CompletableFuture<>();
            currentInitFuture = overallInitFuture;
            
            // Run the actual backend selection and initialization asynchronously
            executor.submit(() -> {
                try {
                    // Double-check inside the executor thread in case initialization completed
                    // between when we scheduled this task and when it actually runs
                    synchronized (initLock) {
                        if (isInitialized) {
                            Log.d(TAG, "Backend was initialized while waiting in executor queue");
                            overallInitFuture.complete(true);
                            initializationInProgress = false;
                            return;
                        }
                    }
                    
                    // Only release resources if we're replacing an existing backend
                    // If we're initializing from scratch, there's no need to release anything
                    if (currentBackend != null) {
                        Log.d(TAG, "Releasing existing backend resources before initialization");
                        synchronized (this) {
                            releaseResourcesInternal(); // Use an internal method to avoid double-logging etc.
                        }
                    } else {
                        Log.d(TAG, "No existing backend to release, proceeding with initialization");
                    }
    
                    Log.d(TAG, "Attempting initialization...");
                    boolean mtkAvailable = MTKNativeBridge.isLibraryLoaded(); // Check if libs are loaded
                    boolean triedMtk = false;
    
                    // Try MTK backend first if preferred OR if no preference is set and it's available
                    if (mtkAvailable && (preferredBackend.equals(AppConstants.BACKEND_MTK) || preferredBackend.equals(AppConstants.BACKEND_NONE))) {
                        Log.d(TAG, "Attempting to initialize MTK backend...");
                        triedMtk = true;
                        File configFile = new File(AppConstants.MTK_CONFIG_PATH); // Check config file existence
                        if (!configFile.exists() || !configFile.isFile()) {
                            Log.e(TAG, "MTK config file not found at: " + AppConstants.MTK_CONFIG_PATH + ". Cannot use MTK backend.");
                        } else {
                            currentBackend = backendFactory.createMTKBackend(); // Pass necessary params if any
                            try {
                                 // Backend's initialize should handle its own timeout internally if needed
                                boolean success = currentBackend.initialize().get(); // Wait for MTK init future
                                if (success) {
                                    currentBackendName = AppConstants.BACKEND_MTK;
                                    // Set initialized flag inside lock to prevent race conditions
                                    synchronized (initLock) {
                                        isInitialized = true;
                                        initializationInProgress = false;
                                    }
                                    AppConstants.MTK_BACKEND_AVAILABLE = true; // Mark as actually available now
                                    Log.i(TAG, "Successfully initialized MTK backend.");
                                    overallInitFuture.complete(true);
                                    return; // Success, exit task
                                } else {
                                    Log.w(TAG, "MTK backend initialization failed (returned false). Falling back to CPU.");
                                    currentBackend.releaseResources(); // Clean up failed MTK attempt
                                    currentBackend = null;
                                }
                            } catch (ExecutionException e) {
                                 Log.e(TAG, "Exception during MTK backend initialization.", e.getCause());
                                 Log.w(TAG, "MTK backend initialization failed (exception). Falling back to CPU.");
                                 if (currentBackend != null) {
                                     currentBackend.releaseResources(); // Clean up failed MTK attempt
                                     currentBackend = null;
                                 }
                                 // Consider setting MTK_BACKEND_AVAILABLE = false here if init consistently fails
                            } catch (InterruptedException e) {
                                 Log.w(TAG, "MTK Initialization interrupted", e);
                                 Thread.currentThread().interrupt();
                                 synchronized (initLock) {
                                     initializationInProgress = false;
                                 }
                                 overallInitFuture.complete(false); // Treat as failure
                                 return;
                            }
                        }
                    }
    
                    // Fallback to CPU backend if MTK wasn't tried, failed, or wasn't available/preferred
                    Log.d(TAG, "Attempting to initialize CPU backend.");
                    currentBackend = backendFactory.createCPUBackend(modelPath); // Use factory
                    try {
                        // CPU backend should also return a future
                        boolean success = currentBackend.initialize().get();
                        if (success) {
                            currentBackendName = AppConstants.BACKEND_CPU;
                            // Set initialized flag inside lock to prevent race conditions
                            synchronized (initLock) {
                                isInitialized = true;
                                initializationInProgress = false;
                            }
                            Log.i(TAG, "Successfully initialized CPU backend.");
                            overallInitFuture.complete(true);
                        } else {
                            Log.e(TAG, "CPU backend initialization failed (returned false).");
                            currentBackend.releaseResources(); // Clean up failed CPU attempt
                            currentBackend = null;
                            currentBackendName = AppConstants.BACKEND_NONE;
                            synchronized (initLock) {
                                initializationInProgress = false;
                            }
                            overallInitFuture.complete(false);
                        }
                    } catch (ExecutionException e) {
                         Log.e(TAG, "Exception during CPU backend initialization.", e.getCause());
                         if (currentBackend != null) {
                             currentBackend.releaseResources(); // Clean up failed CPU attempt
                             currentBackend = null;
                         }
                         currentBackendName = AppConstants.BACKEND_NONE;
                         synchronized (initLock) {
                             initializationInProgress = false;
                         }
                         overallInitFuture.complete(false);
                    } catch (InterruptedException e) {
                         Log.w(TAG, "CPU Initialization interrupted", e);
                         Thread.currentThread().interrupt();
                         synchronized (initLock) {
                             initializationInProgress = false;
                         }
                         overallInitFuture.complete(false); // Treat as failure
                    }
    
                } catch (Exception e) { // Catch any unexpected errors during the process
                    Log.e(TAG, "Unexpected error during initialization task", e);
                    synchronized (initLock) {
                        isInitialized = false;
                        initializationInProgress = false;
                    }
                    currentBackendName = AppConstants.BACKEND_NONE;
                    if (currentBackend != null) {
                         currentBackend.releaseResources(); // Ensure cleanup on error
                         currentBackend = null;
                    }
                    overallInitFuture.completeExceptionally(e);
                }
            });
    
            // Return the future that will be completed by the async task
            return overallInitFuture;
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
        if (!isInitialized || currentBackend == null) {
            Log.e(TAG, "generateStreamingResponse called but service not initialized or no backend.");
            if (callback != null) {
                // Maybe post to handler if callback needs UI thread?
                callback.onToken(AppConstants.LLM_ERROR_RESPONSE);
            }
            return CompletableFuture.completedFuture(AppConstants.LLM_ERROR_RESPONSE);
        }
        if (isGenerating.get()) {
            Log.w(TAG, "Already generating, ignoring new request.");
            // Maybe return a specific error future?
             return CompletableFuture.failedFuture(new IllegalStateException("Already generating"));
        }

        currentCallback = callback;
        currentStreamingResponse.setLength(0);
        isGenerating.set(true);
        currentResponse = new CompletableFuture<>(); // Create new future for this request

        // Delegate to the current backend
        // Backend's method should handle calling the callback AND completing the future
        executor.submit(() -> { // Ensure backend generation runs on the executor thread
            try {
                 currentBackend.generateStreamingResponse(prompt, token -> {
                     // This callback might be called from a native thread (for MTK)
                     // or the executor thread (for CPU).
                     // If UI updates are needed, the callback implementation itself
                     // should post to the main thread Handler.
                     if (callback != null && isGenerating.get()) {
                         callback.onToken(token);
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
        if (isGenerating.get()) {
            stopGeneration(); // Request stop if generating
            // Consider waiting briefly or using a lock if stop needs to fully complete first
        }

        // Release backend resources
        if (currentBackend != null) {
            try {
                // Run release on executor thread to avoid blocking caller
                // and handle potential native hangs within the backend's release
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

        // --- Release legacy CPU module if needed ---
        // if (mModule != null) {
        //    mModule.resetNative();
        //    mModule = null;
        // }

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

        // Unregister service from bridge if needed
        if (mtkBridge != null) {
             mtkBridge.unregisterService(this); // Assuming such a method exists
        }

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
        if (requestedBackendName.equals(currentBackendName)) {
            Log.d(TAG, "Already using requested backend.");
            return CompletableFuture.completedFuture(true);
        }

        CompletableFuture<Boolean> switchFuture = new CompletableFuture<>();

        executor.submit(() -> {
            synchronized(this) { // Synchronize to prevent concurrent modification
                if (isGenerating.get()) {
                    Log.d(TAG, "Stopping current generation before switching backend.");
                    stopGeneration(); // Needs to properly wait or handle async stop
                    // This part is tricky - how to wait for stop to complete?
                    // For now, assume stopGeneration sets flag and backend handles it.
                }

                Log.d(TAG, "Releasing current backend: " + currentBackendName);
                releaseResourcesInternal(); // Release the current one

                // Set the new preference
                preferredBackend = requestedBackendName;

                Log.d(TAG, "Initiating initialization for new backend: " + requestedBackendName);
                // Call initialize again, it will pick up the new preference
                initialize().thenAccept(success -> {
                     Log.d(TAG, "Switch backend completed. Success: " + success + ". Final backend: " + currentBackendName);
                    switchFuture.complete(success);
                }).exceptionally(e -> {
                     Log.e(TAG, "Error during backend switch initialization", e);
                    switchFuture.complete(false);
                     return null;
                });
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