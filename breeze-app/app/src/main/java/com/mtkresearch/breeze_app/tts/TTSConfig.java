package com.mtkresearch.breeze_app.tts;

import java.util.Map;
import java.util.HashMap;

/**
 * Configuration class for Text-to-Speech services.
 * Support multiple backends with their specific configuration parameters.
 */
public class TTSConfig {
    /** The TTS backend type (e.g., "sherpa", "google") */
    public String backend;
    
    /** Playback speed multiplier */
    public float speed;
    
    /** Additional parameters specific to the backend */
    public Map<String, String> extra;

    /**
     * Constructor for TTSConfig
     */
    public TTSConfig(String backend, float speed,
                     Map<String, String> extra) {
        this.backend = backend;
        this.speed = speed;
        this.extra = extra != null ? extra : new HashMap<>();
    }
    
    /**
     * Create a basic Sherpa TTS configuration
     */
    public static TTSConfig createSherpaTTS(String modelPath, String vocoderPath, float speed) {
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("modelPath", modelPath);
        extraParams.put("vocoderPath", vocoderPath);
        
        return new TTSConfig(
            "sherpa",     // backend identifier
            speed,        // speech rate
            extraParams   // extra parameters
        );
    }
    
    
    /**
     * Create a configuration for Android's built-in TextToSpeech
     * @param language The language to use (format varies by device)
     * @param voiceID The voice name to use (format varies by device)
     * @param speed The speech rate multiplier (1.0 = normal speed)
     * @param pitch The pitch multiplier (1.0 = normal pitch)
     * @return A TTSConfig configured for Android's system TTS
     */
    public static TTSConfig createAndroidTTS(String language, String voiceID, float speed, float pitch) {
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("pitch", String.valueOf(pitch));
        extraParams.put("language", language);
        extraParams.put("voiceID", voiceID);
        
        return new TTSConfig(
            "android",     // backend identifier
            speed,         // speech rate
            extraParams    // extra parameters including pitch
        );
    }
} 