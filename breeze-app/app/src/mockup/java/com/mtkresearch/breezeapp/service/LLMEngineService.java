package com.mtkresearch.breezeapp.service;

import static com.mtkresearch.breezeapp.utils.AppConstants.DEFAULT_LLM_MAX_TOKEN;
import static com.mtkresearch.breezeapp.utils.AppConstants.DEFAULT_LLM_TEMPERATURE;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_MAX_TOKEN_VALUE;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_TEMPERATURE_VALUE;

import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import com.mtkresearch.breezeapp.utils.LLMInferenceParams;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.lang.ref.WeakReference;
import java.nio.file.Paths;


public class LLMEngineService extends BaseEngineService {
    private static final String TAG = "LLMEngineService";
    
    // Service state

    
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


        CompletableFuture<String> resultFuture = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                // TBD , return same response
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming response", e);
                resultFuture.completeExceptionally(e);
            }
        });

        return resultFuture;
    }

    public void stopGeneration() {
        Log.d(TAG, "Manual stopping of generation requested");

        //stop generation thread
        
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

