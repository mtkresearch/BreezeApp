package com.mtkresearch.breeze_app.service;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSService;
import com.mtkresearch.breeze_app.tts.runners.SherpaTTSRunner;

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
    private AudioTrack audioTrack;
    private int sampleRate = 16000; // Default sample rate
    private boolean audioPlaying = false;

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
        return isInitialized && ttsService != null;
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
        
        try {
            Log.d(TAG, "Speaking: " + text);
            
            // Synthesize speech
            ttsService.speak(text, pcmData -> {
                try {
                    // Play the audio
                    playAudio(pcmData);
                    future.complete(null);
                } catch (Exception e) {
                    Log.e(TAG, "Error playing audio", e);
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error in speak", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }
    
    /**
     * Play audio data using AudioTrack
     * @param pcmData PCM audio data to play
     */
    private synchronized void playAudio(byte[] pcmData) {
        if (pcmData == null || pcmData.length == 0) {
            Log.w(TAG, "No audio data to play");
            return;
        }
        
        // Clean up any existing AudioTrack
        releaseAudioTrack();
        
        try {
            // Initialize AudioTrack
            initAudioTrack();
            
            // Play the audio
            audioPlaying = true;
            audioTrack.write(pcmData, 0, pcmData.length);
            
            // Add a small delay to ensure all audio is played
            new Handler(Looper.getMainLooper()).postDelayed(this::releaseAudioTrack, 
                    1000 + (pcmData.length * 1000 / (sampleRate * 2)));
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio", e);
            releaseAudioTrack();
        }
    }
    
    /**
     * Initialize AudioTrack for playback
     */
    private void initAudioTrack() {
        int minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        
        AudioFormat format = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
        
        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        
        audioTrack.play();
    }
    
    /**
     * Release AudioTrack resources
     */
    private synchronized void releaseAudioTrack() {
        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioTrack", e);
            }
            audioTrack = null;
            audioPlaying = false;
        }
    }

    /**
     * Stop any ongoing speech
     */
    public void stopSpeaking() {
        releaseAudioTrack();
    }

    @Override
    public void onDestroy() {
        releaseAudioTrack();
        
        if (ttsService != null) {
            ttsService.release();
            ttsService = null;
        }
        
        super.onDestroy();
    }
} 