package com.mtkresearch.breeze_app.tts.runners;

import android.content.Context;
import android.util.Log;

import com.k2fsa.sherpa.onnx.SherpaTTS;
import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSRunner;

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

    /**
     * Get the sample rate from SherpaTTS
     * @return Sample rate in Hz, defaults to 16000 if not available
     */
    public int getSampleRate() {
        if (sherpaTTS != null && sherpaTTS.isInitialized()) {
            try {
                return sherpaTTS.getSampleRate();
            } catch (Exception e) {
                Log.w(TAG, "Failed to get sample rate from SherpaTTS", e);
            }
        }
        // Default sample rate if not available
        return 16000;
    }

    @Override
    public void setModel(TTSConfig config) {
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
    public void synthesize(String text, Consumer<float[]> callback) {
        if (sherpaTTS == null || !sherpaTTS.isInitialized()) {
            Log.e(TAG, "SherpaTTS not initialized");
            callback.accept(new float[0]);
            return;
        }
        
        try {
            Log.i(TAG, "Generating speech for text: " + text);
            
            // Extract speed from config
            float speed = config != null ? config.speed : 1.0f;
            
            // Use a thread to avoid blocking the main thread
            new Thread(() -> {
                try {
                    // Generate and return float samples directly from SherpaTTS
                    float[] samples = sherpaTTS.speak(text, 0, 1.0f);
                    
                    // Pass the float samples directly to callback
                    callback.accept(samples);
                    
                    Log.i(TAG, "Speech generated successfully. Length: " + samples.length + " samples");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to synthesize speech", e);
                    callback.accept(new float[0]);
                }
            }).start();
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to synthesize speech", e);
            callback.accept(new float[0]);
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