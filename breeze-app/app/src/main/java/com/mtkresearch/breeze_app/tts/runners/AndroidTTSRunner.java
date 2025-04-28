package com.mtkresearch.breeze_app.tts.runners;

import android.content.Context;
import android.media.AudioFormat;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;

import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSRunner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * TTSRunner implementation that uses Android's built-in TextToSpeech API.
 * This runner provides access to all system-installed voices.
 */
public class AndroidTTSRunner implements TTSRunner {
    private static final String TAG = "AndroidTTSRunner";

    private TextToSpeech tts;
    private boolean isInitialized = false;
    
    /**
     * Creates a new AndroidTTSRunner instance
     * @param context Android context for TextToSpeech initialization
     */
    public AndroidTTSRunner(Context context, TTSConfig config) {
        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
        
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    // TTS engine is successfully initialized.
                    isInitialized = true;
                    Log.d(TAG, "TTS engine initialized successfully");

                    setModel(config);
                } else {
                    // Failed to initialize TTS engine.
                    Log.e(TAG, "Failed to initialize TTS engine");
                }
            }
        });
    }
    
    
    @Override
    public void setModel(TTSConfig config) {
        if (!isInitialized || tts == null) {
            Log.e(TAG, "Cannot set model: TextToSpeech not initialized");
            return;
        }
        
        try {
            // Set speech rate
            tts.setSpeechRate(config.speed);
            
            // Get language from extras
            String languageStr = config.extra.get("language");
            if (languageStr != null) {
                try {
                    // Parse the language value if it's a Locale object's string representation
                    if (languageStr.contains("zh_TW")) {
                        tts.setLanguage(Locale.TAIWAN);
                    } else if (languageStr.contains("zh_CN")) {
                        tts.setLanguage(Locale.CHINA);
                    } else if (languageStr.contains("en")) {
                        tts.setLanguage(Locale.US);
                    } else {
                        // Try to create locale from language string if provided
                        String[] langParts = languageStr.split("_");
                        Locale locale;
                        if (langParts.length > 1) {
                            locale = new Locale(langParts[0], langParts[1]);
                        } else {
                            locale = new Locale(languageStr);
                        }
                        tts.setLanguage(locale);
                    }
                    Log.d(TAG, "Set locale from config: " + languageStr);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting language", e);
                }
            }
            
            // Get voiceID from extras
            String voiceIDStr = config.extra.get("voiceID");
            if (voiceIDStr != null && !voiceIDStr.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    // Set voice by ID if available
                    Set<Voice> voices = tts.getVoices();
                    if (voices != null) {
                        for (Voice voice : voices) {
                            if (voice.getName().contains(voiceIDStr)) {
                                tts.setVoice(voice);
                                Log.d(TAG, "Set voice from config: " + voiceIDStr);
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error setting voice", e);
                }
            }
            
            // Handle pitch from extras
            String pitchStr = config.extra.get("pitch");
            if (pitchStr != null) {
                try {
                    float pitch = Float.parseFloat(pitchStr);
                    tts.setPitch(pitch);
                    Log.d(TAG, "Set pitch from config: " + pitch);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Invalid pitch value: " + pitchStr);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error setting TTS configuration", e);
        }
    }
    
    @Override
    public void synthesize(String text, Consumer<float[]> callback) {
        try {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } catch (Exception e) {
            Log.e(TAG, "Failed to synthesize text", e);
        }
    }

    @Override
    public int getSampleRate() {
        return 16000;
    }


    @Override
    public void release() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
            isInitialized = false;
            Log.d(TAG, "TTS engine released");
        }
    }

} 