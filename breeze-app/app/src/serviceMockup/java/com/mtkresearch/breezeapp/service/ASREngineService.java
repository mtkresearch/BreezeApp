package com.mtkresearch.breezeapp.service;

import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ASREngineService extends BaseEngineService {

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> callbackTask;
    private Consumer<String> dataCallback;
    private int counter = 0; // Just for example data
    public class LocalBinder extends BaseEngineService.LocalBinder<ASREngineService> { }
    String[] mockupArray = {"This ", "is ", "a ", "mockup. "};


    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.completedFuture(true);
    }


    public void startListening(Consumer<String> callback) {
        //TBD should create a thread and response callback in a random order
        if (scheduler != null && !scheduler.isShutdown()) {
            // Already listening, or a previous scheduler is active.
            // Consider if you want to throw an exception, log a warning, or restart.
            // For simplicity, we'll stop the existing one and restart.
            stopListening();
        }

        this.dataCallback = callback;
        // Create a single-threaded scheduled executor service
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Schedule the task to run every second
        callbackTask = scheduler.scheduleAtFixedRate(() -> {
            // This code runs on the scheduler's thread
            if (dataCallback != null) {
                // Simulate generating some data
                String data = mockupArray[(counter++) % mockupArray.length];
                // Deliver the data via the callback.
                // IMPORTANT: If this callback needs to update UI, you MUST switch to the main thread.
                // Example for UI update (requires Android context):
                // new Handler(Looper.getMainLooper()).post(() -> dataCallback.accept(data));
                // For this example, we'll assume the callback can be called on the background thread.
                dataCallback.accept(data);
            }
        }, 0, 1, TimeUnit.SECONDS); // Initial delay 0, repeat every 1 second        
    }


    public void stopListening() {
        //TBD should join the thread of response callback
        if (callbackTask != null) {
            // Attempt to cancel the scheduled task.
            // true allows interruption if the task is currently running.
            callbackTask.cancel(true);
            callbackTask = null;
        }

        if (scheduler != null) {
            scheduler.shutdown(); // Initiates an orderly shutdown in which previously submitted tasks are executed, but no new tasks will be accepted.
            try {
                // Wait a reasonable amount of time for tasks to terminate.
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow(); // Forcefully shut down if tasks don't terminate quickly.
                    // Optionally, wait a bit longer for forced shutdown.
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        System.err.println("Scheduler did not terminate."); // Log an error
                    }
                }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                scheduler.shutdownNow();
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } finally {
                scheduler = null;
                dataCallback = null; // Clear the callback to prevent stale references
            }
        }
        counter = 0; // Reset counter
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

} 