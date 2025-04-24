package com.mtkresearch.breeze_app.tts;

/**
 * Main service for text-to-speech functionality.
 * Manages TTSRunners and provides a unified interface for speech synthesis.
 */
public class TTSService {
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
     * Switch to a different TTSRunner implementation
     * @param newRunner The new TTSRunner to use
     */
    public void switchRunner(TTSRunner newRunner) {
        if (runner != null) {
            runner.release();
        }
        this.runner = newRunner;
    }

    /**
     * Update the current TTSRunner with a new configuration
     * @param config The new configuration to use
     */
    public void updateModel(TTSConfig config) {
        if (runner != null) {
            runner.setModel(config);
        }
    }

    /**
     * Synthesize speech from the given text
     * @param text The text to convert to speech
     * @param callback Callback for receiving the generated audio data
     */
    public void speak(String text, java.util.function.Consumer<byte[]> callback) {
        if (runner != null) {
            runner.synthesize(text, callback);
        }
    }

    /**
     * Release resources used by the current TTSRunner
     */
    public void release() {
        if (runner != null) {
            runner.release();
            runner = null;
        }
    }
} 