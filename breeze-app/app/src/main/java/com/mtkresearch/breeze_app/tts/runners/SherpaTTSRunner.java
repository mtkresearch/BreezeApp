package com.mtkresearch.breeze_app.tts.runners;

import android.content.Context;
import android.util.Log;

import com.k2fsa.sherpa.onnx.SherpaTTS;
import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSRunner;
import com.mtkresearch.breeze_app.tts.PCMUtils;

import java.util.function.Consumer;

/**
 * TTSRunner implementation using SherpaTTS
 */
public class SherpaTTSRunner implements TTSRunner {
    private static final String TAG = "SherpaTTSRunner";
    
    /** SherpaTTS instance */
    private SherpaTTS sherpaTTS;
    
    /** Current configuration */
    private TTSConfig config;
    
    /** Application context */
    private final Context context;

    /**
     * Constructor requiring context
     * @param context Android application context
     */
    public SherpaTTSRunner(Context context) {
        this.context = context;
    }
    
    /**
     * Constructor with initial configuration
     * @param context Android application context
     * @param config Initial TTS configuration
     */
    public SherpaTTSRunner(Context context, TTSConfig config) {
        this.context = context;
        setModel(config);
    }

    @Override
    public void setModel(TTSConfig config) {
        if (!"sherpa".equals(config.backend)) {
            throw new IllegalArgumentException("SherpaTTSRunner only supports 'sherpa' backend");
        }
        
        try {
            // Initialize SherpaTTS if needed
            if (sherpaTTS == null) {
                sherpaTTS = SherpaTTS.Companion.getInstance(context);
                
                if (!sherpaTTS.isInitialized()) {
                    throw new RuntimeException("Failed to initialize SherpaTTS");
                }
            }
            
            this.config = config;
            Log.i(TAG, "SherpaTTS initialized successfully");
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize SherpaTTS", e);
            throw new RuntimeException("Failed to initialize SherpaTTS", e);
        }
    }

    @Override
    public void synthesize(String text, Consumer<byte[]> callback) {
        if (sherpaTTS == null || !sherpaTTS.isInitialized()) {
            Log.e(TAG, "SherpaTTS not initialized");
            callback.accept(new byte[0]);
            return;
        }
        
        try {
            Log.i(TAG, "Generating speech for text: " + text);
            
            // Extract speaker ID and speed from config
            int speakerId = config != null ? config.speakerId : 0;
            float speed = config != null ? config.speed : 1.0f;
            
            // Use a thread to avoid blocking the main thread
            new Thread(() -> {
                try {
                    // Generate audio samples using SherpaTTS
                    float[] samples = sherpaTTS.speak(text, speakerId, speed);
                    
                    // Convert float samples to 16-bit PCM
                    byte[] pcm = PCMUtils.floatArrayToPCM16(samples);
                    
                    // Pass the PCM data to callback
                    callback.accept(pcm);
                    
                    Log.i(TAG, "Speech generated successfully. Length: " + pcm.length + " bytes");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to synthesize speech", e);
                    callback.accept(new byte[0]);
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to synthesize speech", e);
            callback.accept(new byte[0]);
        }
    }

    @Override
    public void release() {
        if (sherpaTTS != null) {
            try {
                sherpaTTS.stop();
                Log.i(TAG, "SherpaTTS resources released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing SherpaTTS resources", e);
            }
        }
    }
} 