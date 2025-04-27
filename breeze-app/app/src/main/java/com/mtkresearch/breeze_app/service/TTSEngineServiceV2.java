package com.mtkresearch.breeze_app.service;

import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSService;
import com.mtkresearch.breeze_app.tts.runners.SherpaTTSRunner;
import com.mtkresearch.breeze_app.utils.AudioPlayerUtils;

import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;

/**
 * Updated TTS Engine Service using the new TTSService interface
 */
public class TTSEngineServiceV2 extends BaseEngineService {
    private static final String TAG = "TTSEngineServiceV2";
    private static final long INIT_TIMEOUT_MS = 10000; // 10 seconds timeout
    
    // TTS components
    private TTSService ttsService;
    private String backend = "none";
    private AudioPlayerUtils audioPlayer;
    private boolean isSpeaking = false;

    public class LocalBinder extends BaseEngineService.LocalBinder<TTSEngineServiceV2> {
        private final WeakReference<TTSEngineServiceV2> serviceRef;
        
        public LocalBinder() {
            this.serviceRef = new WeakReference<>(TTSEngineServiceV2.this);
        }
        
        @Override
        public TTSEngineServiceV2 getService() {
            return serviceRef.get();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            Log.d(TAG, "Initializing TTS service...");
            
            // Create a SherpaTTSRunner
            SherpaTTSRunner runner = new SherpaTTSRunner(getApplicationContext());
            
            // Create the TTSService with the runner
            ttsService = new TTSService(runner);
            
            // Create a test configuration
            TTSConfig config = new TTSConfig(
                "sherpa", // backend
                null,     // modelPath will be provided by SherpaTTS internally
                null,     // outputPath
                0,        // speakerId
                1.0f,     // speed
                null,     // voice
                null,     // language
                null      // extra
            );
            
            // Update the model
            ttsService.updateModel(config);
            
            // Get sample rate from TTSService
            int sampleRate = ttsService.getSampleRate();
            Log.d(TAG, "Using sample rate from TTS engine: " + sampleRate + " Hz");
            
            // Initialize AudioPlayerUtils with the correct sample rate
            audioPlayer = new AudioPlayerUtils(getApplicationContext(), sampleRate);
            
            // Set backend
            backend = "sherpa";
            isInitialized = true;
            
            Log.d(TAG, "TTS service initialized successfully");
            future.complete(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize TTS service", e);
            future.complete(false);
        }
        
        // Add timeout
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!future.isDone()) {
                Log.e(TAG, "TTS initialization timed out");
                future.complete(false);
            }
        }, INIT_TIMEOUT_MS);
        
        return future;
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