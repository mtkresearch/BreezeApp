package com.mtkresearch.breeze_app.tts;

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
     * @param callback Callback for receiving the generated audio data
     */
    void synthesize(String text, java.util.function.Consumer<byte[]> callback);
    
    /**
     * Release resources used by this runner
     */
    void release();
} 