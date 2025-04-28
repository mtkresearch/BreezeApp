package com.mtkresearch.breeze_app.tts;

import android.util.Log;
import java.util.function.Consumer;

/**
 * Main service for text-to-speech functionality.
 * Provides a simplified interface for speech synthesis.
 */
public class TTSService {
    private static final String TAG = "TTSService";
    
    /** The current TTSRunner implementation */
    private TTSRunner runner;

    /**
     * Create a TTSService with the specified runner
     * @param runner The TTSRunner implementation to use
     */
    public TTSService(TTSRunner runner) {
        this.runner = runner;
    }

    /**
     * Update the model configuration
     * @param config The configuration to use
     */
    public void updateModel(TTSConfig config) {
        if (runner != null) {
            runner.setModel(config);
        }
    }

    /**
     * Synthesize speech from text and receive float samples
     * @param text The text to speak
     * @param callback Callback for receiving float audio samples
     */
    public void speak(String text, Consumer<float[]> callback) {
        if (runner != null) {
            runner.synthesize(text, callback);
        } else {
            Log.e(TAG, "No TTS runner available");
            callback.accept(new float[0]);
        }
    }

    /**
     * Get the sample rate of the current TTS runner
     * @return Sample rate in Hz, or 16000 if no runner available
     */
    public int getSampleRate() {
        if (runner != null) {
            return runner.getSampleRate();
        }
        return 16000; // Default fallback
    }

    /**
     * Set a new runner
     * @param newRunner The new runner to use
     */
    public void setRunner(TTSRunner newRunner) {
        if (runner != null) {
            runner.release();
        }
        this.runner = newRunner;
    }
    
    /**
     * Get the current runner instance
     * @return The current TTSRunner implementation
     */
    public TTSRunner getRunner() {
        return runner;
    }

    /**
     * Release resources
     */
    public void release() {
        if (runner != null) {
            runner.release();
            runner = null;
        }
    }
} 