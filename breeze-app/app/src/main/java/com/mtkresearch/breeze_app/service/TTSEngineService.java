package com.mtkresearch.breeze_app.service;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSService;
import com.mtkresearch.breeze_app.tts.runners.AndroidTTSRunner;
import com.mtkresearch.breeze_app.tts.runners.SherpaTTSRunner;
import com.mtkresearch.breeze_app.utils.AudioPlayerUtils;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Updated TTS Engine Service using the new TTSService interface
 */
public class TTSEngineService extends BaseEngineService {
    private static final String TAG = "TTSEngineService";
    private static final long INIT_TIMEOUT_MS = 10000; // 10 seconds timeout
    
    public static final String BACKEND_ANDROID = "android";
    public static final String BACKEND_SHERPA = "sherpa";
    
    // TTS components
    private TTSService ttsService;
    private String backend = "none";
    private AudioPlayerUtils audioPlayer;
    private boolean isSpeaking = false;

    public class LocalBinder extends BaseEngineService.LocalBinder<TTSEngineService> {
        private final WeakReference<TTSEngineService> serviceRef;
        
        public LocalBinder() {
            this.serviceRef = new WeakReference<>(TTSEngineService.this);
        }
        
        @Override
        public TTSEngineService getService() {
            return serviceRef.get();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        // Default to Android TTS
        return initialize(BACKEND_ANDROID);
    }
    
    /**
     * Initialize the TTS service with a specific backend
     * @param backendType The backend to use (BACKEND_ANDROID or BACKEND_SHERPA)
     * @return CompletableFuture that completes with success status
     */
    public CompletableFuture<Boolean> initialize(String backendType) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            Log.d(TAG, "Initializing TTS service with backend: " + backendType);
            
            if (BACKEND_SHERPA.equals(backendType)) {
                initSherpaBackend(future);
            } else {
                initAndroidBackend(future);
            }
            
            // Add timeout
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!future.isDone()) {
                    Log.e(TAG, "TTS initialization timed out");
                    future.complete(false);
                }
            }, INIT_TIMEOUT_MS);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing TTS", e);
            future.complete(false);
        }
        
        return future;
    }
    
    /**
     * Initialize the Sherpa TTS backend
     */
    private void initSherpaBackend(CompletableFuture<Boolean> future) {
        try {
            // Create a SherpaTTSRunner
            Log.d(TAG, "Using Sherpa TTS...");
            SherpaTTSRunner runner = new SherpaTTSRunner(getApplicationContext());
            
            // Create the TTSService with the runner
            ttsService = new TTSService(runner);
            
            // Create a configuration for Sherpa TTS with required params in extras
            Map<String, String> extraParams = new HashMap<>();
            extraParams.put("modelPath", null); // SherpaTTS will use default internal paths
            extraParams.put("vocoderPath", null);
            extraParams.put("speakerId", "0");
            
            TTSConfig config = TTSConfig.createSherpaTTS(
                null,
                null,
                0,
                1.0f
            ) ;
            
            // Update the model
            ttsService.updateModel(config);
            
            // Get sample rate from TTSService
            int sampleRate = ttsService.getSampleRate();
            Log.d(TAG, "Using sample rate from TTS engine: " + sampleRate + " Hz");
            
            // Initialize AudioPlayerUtils with the correct sample rate
            audioPlayer = new AudioPlayerUtils(getApplicationContext(), sampleRate);
            
            // Set backend
            backend = BACKEND_SHERPA;
            isInitialized = true;
            
            Log.d(TAG, "Sherpa TTS service initialized successfully");
            future.complete(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Sherpa TTS", e);
            future.complete(false);
        }
    }
    
    /**
     * Initialize the Android TTS backend
     */
    private void initAndroidBackend(CompletableFuture<Boolean> future) {
        try {
            // Create Android TTS configuration with proper locale support
            String language = "zh_TW"; // Use string representation for Locale.TAIWAN
            TTSConfig config = TTSConfig.createAndroidTTS(
                language,   // Language (will be interpreted as Locale)
                "1",        // Voice ID
                1.0f,       // Normal speed
                1.0f        // Normal pitch
            );

            // Create AndroidTTSRunner
            Log.d(TAG, "Using Android System TTS...");
            AndroidTTSRunner runner = new AndroidTTSRunner(getApplicationContext(), config);

            // Create the TTSService with the runner
            ttsService = new TTSService(runner);

            
            // Get sample rate from TTSService
            int sampleRate = ttsService.getSampleRate();
            Log.d(TAG, "Using sample rate from TTS engine: " + sampleRate + " Hz");

            // Initialize AudioPlayerUtils with the correct sample rate
            audioPlayer = new AudioPlayerUtils(getApplicationContext(), sampleRate);
            
            // Set backend
            backend = BACKEND_ANDROID;
            isInitialized = true;
            
            Log.d(TAG, "Android TTS service initialized successfully");
            future.complete(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Android TTS", e);
            future.complete(false);
        }
    }
    
    /**
     * Switch to a different TTS backend
     * @param backendType The backend to switch to (BACKEND_ANDROID or BACKEND_SHERPA)
     * @return CompletableFuture that completes with success status
     */
    public CompletableFuture<Boolean> switchBackend(String backendType) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Stop any ongoing speech
        stopSpeaking();
        
        // Release current TTS resources
        if (ttsService != null) {
            ttsService.release();
            ttsService = null;
        }
        
        // Reset initialization flag
        isInitialized = false;
        
        // Initialize with the new backend
        return initialize(backendType);
    }
    
    /**
     * Get the current backend type
     * @return Current backend type (BACKEND_ANDROID or BACKEND_SHERPA)
     */
    public String getCurrentBackend() {
        return backend;
    }

    @Override
    public boolean isReady() {
        return isInitialized && ttsService != null && audioPlayer != null;
    }

    /**
     * Speak the provided text
     * @param text Text to convert to speech
     * @return CompletableFuture that completes when speech is done
     */
    public CompletableFuture<Void> speak(String text) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        
        if (!isReady()) {
            future.completeExceptionally(new IllegalStateException("TTS service not initialized"));
            return future;
        }
        
        // Stop any ongoing speech
        stopSpeaking();
        
        try {
            Log.d(TAG, "Speaking: " + text);
            isSpeaking = true;
            
            // Use the simplified API that directly provides float samples
            ttsService.speak(text, floatSamples -> {
                try {
                    if (floatSamples != null && floatSamples.length > 0) {
                        // Only proceed if we're still speaking (not stopped)
                        if (!isSpeaking) {
                            Log.d(TAG, "Speech was stopped, not playing audio");
                            future.complete(null);
                            return;
                        }
                        
                        // Play audio samples directly
                        boolean success = audioPlayer.playAudioSamples(floatSamples);
                        
                        if (!success) {
                            Log.w(TAG, "Failed to play audio samples");
                        }
                    } else {
                        // This is the end of speech signal
                        Log.d(TAG, "TTS synthesis complete");
                        isSpeaking = false;
                        future.complete(null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error playing audio", e);
                    isSpeaking = false;
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in speak", e);
            isSpeaking = false;
            future.completeExceptionally(e);
        }
        
        return future;
    }

    /**
     * Stop any ongoing speech
     */
    public void stopSpeaking() {
        isSpeaking = false;
        if (audioPlayer != null) {
            audioPlayer.stopPlayback();
        }
    }

    @Override
    public void onDestroy() {
        stopSpeaking();
        
        if (audioPlayer != null) {
            audioPlayer.release();
            audioPlayer = null;
        }
        
        if (ttsService != null) {
            ttsService.release();
            ttsService = null;
        }
        
        super.onDestroy();
    }

} 