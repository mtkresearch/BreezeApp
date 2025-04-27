package com.mtkresearch.breeze_app.tts;

import java.util.function.Consumer;

/**
 * TTSRunner interface defines methods that any text-to-speech implementation must support.
 */
public interface TTSRunner {
    /**
     * Set the TTS model configuration
     * @param config The TTS configuration
     */
    void setModel(TTSConfig config);
    
    /**
     * Synthesize speech from the given text
     * @param text The text to convert to speech
     * @param callback Callback for receiving the generated audio data as float samples
     */
    void synthesize(String text, Consumer<float[]> callback);
    
    /**
     * Get the sample rate of the audio produced by this runner
     * @return Sample rate in Hz
     */
    int getSampleRate();
    
    /**
     * Release resources used by this runner
     */
    void release();
} 