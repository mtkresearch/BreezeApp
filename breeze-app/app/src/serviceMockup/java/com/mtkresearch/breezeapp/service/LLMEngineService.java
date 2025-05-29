package com.mtkresearch.breezeapp.service;


import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import com.mtkresearch.breezeapp.utils.LLMInferenceParams;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;
import java.util.Random;
import java.nio.file.Paths;


public class LLMEngineService extends BaseEngineService {
    private static final String TAG = "LLMEngineService";
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> currentGenerationTask;
    private int counter = 0; // Just for example data
    // Service state
    String[] mockupArray = {"This ", "is ", "a ", "mockup. "};
    private final StringBuilder currentStreamingResponse = new StringBuilder();
    private volatile boolean isGenerating = false; // Volatile for thread safety

    static {
        // Only try to load MTK libraries if MTK backend is enabled
    }


    public static boolean isMTKBackendAvailable() {
        return false;
    }

    public String getModelName() {

        return "mockup";
    }

    public interface StreamingResponseCallback {
        void onToken(String token);
    }

    public LLMEngineService() {

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
        Log.d(TAG, "onBind");
        return new LocalBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand flag="+flags+", startId= "+startId);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        Log.d(TAG, "initialize");

        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<String> generateStreamingResponse(String prompt, LLMInferenceParams params, StreamingResponseCallback callback) {
        Log.d(TAG, "generateStreamingResponse for prompt: " + prompt);

        // Ensure only one generation task runs at a time
        stopGeneration(); // Stop any previous generation

        CompletableFuture<String> resultFuture = new CompletableFuture<>();
        isGenerating = true; // Set the flag to indicate generation is active
        currentStreamingResponse.setLength(0);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        Random random = new Random();
        final long startTime = System.currentTimeMillis();

        // Schedule the task to send responses with random delays
        currentGenerationTask = scheduler.scheduleWithFixedDelay(() -> {
            if (!isGenerating) { // Check the cancellation flag
                Log.d(TAG, "Generation stopped internally.");
                // If we've been told to stop, complete the future and break the loop
                resultFuture.complete("Generation stopped."); // Or completeExceptionally if that's more appropriate
                cancelScheduledTaskAndShutdownScheduler();
                return;
            }

            try {
                // Simulate LLM generating a chunk of response
                if(counter <= params.getMaxToken()){
                    String chunk = mockupArray[(counter++) % mockupArray.length];
                    // Deliver callback on the main thread if it involves UI updates
                    if (isGenerating && callback != null) { // Double-check isGenerating before calling back
                        callback.onToken(chunk);
                        currentStreamingResponse.append(chunk);
                    }

                    // Simulate random delay before next chunk
                    long delayMs = 100 + random.nextInt(900); // 0.1 to 1.0 seconds
                    Thread.sleep(delayMs);
                }
                else {
                    resultFuture.complete(currentStreamingResponse.toString());
                }

            } catch (InterruptedException e) {
                // Thread was interrupted (likely by stopGeneration)
                Log.d(TAG, "Generation thread interrupted.");
                Thread.currentThread().interrupt(); // Restore the interrupted status
                resultFuture.completeExceptionally(new RuntimeException("Streaming interrupted", e));

                cancelScheduledTaskAndShutdownScheduler();
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming response", e);
                resultFuture.completeExceptionally(e);

                cancelScheduledTaskAndShutdownScheduler();
            }
        }, 0, 100, TimeUnit.MILLISECONDS); // Initial delay 0, check every 100ms for response sending

        // We also need a mechanism to complete the resultFuture when the "streaming" naturally ends.
        // For a true streaming LLM, this would be when the LLM signals "end of stream".
        // Here, let's simulate it after a certain number of chunks or time.
        // You would replace this with actual LLM stream completion logic.
        scheduler.schedule(() -> {
            if (isGenerating && !resultFuture.isDone()) { // Only complete if not already stopped or completed
                resultFuture.complete(currentStreamingResponse.toString());
                
                cancelScheduledTaskAndShutdownScheduler();
            }
        }, 10, TimeUnit.SECONDS); // Simulate ending after 10 seconds

        return resultFuture;
    }

    public void stopGeneration() {
        Log.d(TAG, "Manual stopping of generation requested");
        isGenerating = false; // Signal the background task to stop

        // Attempt to cancel the scheduled task
        if (currentGenerationTask != null) {
            boolean cancelled = currentGenerationTask.cancel(true); // true to interrupt if running
            Log.d(TAG, "Scheduled task cancelled: " + cancelled);
            currentGenerationTask = null;
        }

        // Shutdown the scheduler gracefully
        cancelScheduledTaskAndShutdownScheduler();
        
    }

    private void cancelScheduledTaskAndShutdownScheduler() {
        if (scheduler != null) {
            scheduler.shutdown(); // Initiates an orderly shutdown
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    Log.w(TAG, "Scheduler did not terminate in time, forcing shutdown.");
                    scheduler.shutdownNow(); // Forcefully shut down
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Scheduler termination interrupted.", e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } finally {
                scheduler = null;
            }
        }
    }

    
    public void releaseResources() {
        Log.d(TAG, "releaseResources");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");   
    
        super.onDestroy();
                
    }
    

    public String getCurrentBackend() {
        return "MOCKUP";
    }
    
    public String getPreferredBackend() {
        return "MOCKUP";
    }

    

    public interface TokenCallback {
        void onToken(String token);
    }

}

