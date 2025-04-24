package com.mtkresearch.breeze_app.tts.runners;

import android.content.Context;
import android.util.Log;

import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSRunner;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Simple example implementation of a TTS Runner
 * This runner doesn't actually perform any text-to-speech conversion,
 * it simply returns a fixed text as PCM data when synthesize is called.
 */
public class MtkTTSRunner implements TTSRunner {
    private static final String TAG = "MtkTTSRunner";
    
    /** Current configuration */
    private TTSConfig config;
    
    /** Dummy flag to simulate successful initialization */
    private boolean isInitialized = false;
    
    /** Cached message to return */
    private final String fixedSpeechText = "This is dummy speech from MTK TTS Runner";
    
    /** Application context - not used in this example but included for API consistency */
    private final Context context;

    /**
     * Constructor requiring context (not used in this implementation but kept for API consistency)
     * @param context Android application context
     */
    public MtkTTSRunner(Context context) {
        this.context = context;
        Log.i(TAG, "MTK TTS Runner created");
    }
    
    /**
     * Constructor with initial configuration
     * @param context Android application context
     * @param config Initial TTS configuration
     */
    public MtkTTSRunner(Context context, TTSConfig config) {
        this.context = context;
        setModel(config);
    }

    @Override
    public void setModel(TTSConfig config) {
        Log.i(TAG, "Setting MTK TTS model configuration");
        
        // Just store the config, no actual model loading
        this.config = config;
        
        // Always report success
        isInitialized = true;
        
        // Log some info about the received config
        Log.d(TAG, "Configured with model path: " + 
              (config.modelPath != null ? config.modelPath : "none") + 
              ", speed: " + config.speed);
    }

    @Override
    public void synthesize(String text, Consumer<byte[]> callback) {
        if (!isInitialized) {
            Log.e(TAG, "MTK TTS not initialized");
            callback.accept(new byte[0]);
            return;
        }
        
        Log.i(TAG, "Synthesizing text: " + text);
        
        // Just convert the fixed string to bytes
        String responseText = "[MTK TTS] Would speak: " + text + "\n" + fixedSpeechText;
        byte[] dummyAudio = responseText.getBytes(StandardCharsets.UTF_8);
        
        // Simulate processing time
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        // Return the dummy data to callback
        callback.accept(dummyAudio);
        
        Log.i(TAG, "Synthesizing complete, returned " + dummyAudio.length + " bytes");
    }

    @Override
    public void release() {
        Log.i(TAG, "Releasing MTK TTS Runner resources");
        // Nothing to actually release, just reset state
        isInitialized = false;
    }
} 