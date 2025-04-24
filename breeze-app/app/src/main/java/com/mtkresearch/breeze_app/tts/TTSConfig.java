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
    
    /** Path to the TTS model */
    public String modelPath;
    
    /** Path to the vocoder model (if applicable) */
    public String vocoderPath;
    
    /** Speaker ID for multi-speaker models */
    public int speakerId;
    
    /** Playback speed multiplier */
    public float speed;
    
    /** Voice name (for backends like Google TTS) */
    public String voiceName;
    
    /** API key (for cloud-based services) */
    public String apiKey;
    
    /** Additional parameters specific to the backend */
    public Map<String, String> extra;

    /**
     * Constructor for TTSConfig
     */
    public TTSConfig(String backend, String modelPath, String vocoderPath,
                     int speakerId, float speed, String voiceName, String apiKey,
                     Map<String, String> extra) {
        this.backend = backend;
        this.modelPath = modelPath;
        this.vocoderPath = vocoderPath;
        this.speakerId = speakerId;
        this.speed = speed;
        this.voiceName = voiceName;
        this.apiKey = apiKey;
        this.extra = extra != null ? extra : new HashMap<>();
    }
    
    /**
     * Create a basic Sherpa TTS configuration
     */
    public static TTSConfig createSherpaTTS(String modelPath, String vocoderPath, int speakerId, float speed) {
        return new TTSConfig("sherpa", modelPath, vocoderPath, speakerId, speed, null, null, new HashMap<>());
    }
    
    /**
     * Create a basic Google TTS configuration
     */
    public static TTSConfig createGoogleTTS(String voiceName, float speed) {
        return new TTSConfig("google", null, null, 0, speed, voiceName, null, new HashMap<>());
    }
} 