package com.mtkresearch.breeze_app.tts.runners;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;

import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSRunner;

import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * TTS Runner implementation using Android's built-in Google TTS
 */
public class GoogleTTSRunner implements TTSRunner {
    private static final String TAG = "GoogleTTSRunner";
    
    /** Android TTS instance */
    private TextToSpeech tts;
    
    /** Application context */
    private final Context context;
    
    /** Current configuration */
    private TTSConfig config;
    
    /** Background thread for synthesizing speech */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    /** Initialization completion flag */
    private boolean isInitialized = false;

    /**
     * Constructor
     * @param context Android application context
     */
    public GoogleTTSRunner(Context context) {
        this.context = context;
    }
    
    /**
     * Constructor with initial configuration
     * @param context Android application context
     * @param config Initial TTS configuration
     */
    public GoogleTTSRunner(Context context, TTSConfig config) {
        this.context = context;
        setModel(config);
    }

    @Override
    public void setModel(TTSConfig config) {
        if (!"google".equals(config.backend)) {
            throw new IllegalArgumentException("GoogleTTSRunner only supports 'google' backend");
        }
        
        this.config = config;
        
        // Release existing instance if any
        if (tts != null) {
            tts.shutdown();
            tts = null;
            isInitialized = false;
        }
        
        initTTS();
    }
    
    /**
     * Initialize the Google TTS engine
     */
    private void initTTS() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Initialize parameters like voice, language, and speed
                if (config.voiceName != null && !config.voiceName.isEmpty()) {
                    for (Voice voice : tts.getVoices()) {
                        if (voice.getName().equals(config.voiceName)) {
                            tts.setVoice(voice);
                            break;
                        }
                    }
                } else {
                    // Default to US English if no voice specified
                    tts.setLanguage(Locale.US);
                }
                
                // Set speech rate
                tts.setSpeechRate(config.speed);
                
                isInitialized = true;
                Log.i(TAG, "Google TTS initialized successfully");
            } else {
                Log.e(TAG, "Failed to initialize Google TTS: " + status);
                isInitialized = false;
            }
        });
    }

    @Override
    public void synthesize(String text, Consumer<byte[]> callback) {
        if (tts == null || !isInitialized) {
            Log.e(TAG, "TTS engine not initialized");
            callback.accept(new byte[0]);
            return;
        }
        
        // Google TTS doesn't provide direct access to raw audio data
        // This is a limitation of the Android TTS API
        Log.w(TAG, "GoogleTTSRunner doesn't support direct PCM data access");
        Log.w(TAG, "Only speaking the text directly through the device speakers");
        
        // Use Bundle instead of HashMap for speak
        Bundle params = new Bundle();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utterId");
        
        // Return empty byte array since we can't get audio data
        callback.accept(new byte[0]);
    }

    @Override
    public void release() {
        executor.shutdown();
        
        if (tts != null) {
            try {
                tts.stop();
                tts.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing TTS resources", e);
            } finally {
                tts = null;
                isInitialized = false;
            }
        }
    }
} 