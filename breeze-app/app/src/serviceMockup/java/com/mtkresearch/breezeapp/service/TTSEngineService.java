package com.mtkresearch.breezeapp.service;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

import java.lang.ref.WeakReference;

public class TTSEngineService extends BaseEngineService {
    private static final String TAG = "TTSEngineService";
    private static final long INIT_TIMEOUT_MS = 20000; // 20 seconds timeout
    private static final String TEST_TEXT = "Hello, this is a test.";
    
    // TTS components
    private TextToSpeech textToSpeech;

    private String backend = "none";
    private boolean isTextToSpeechInitialized = false;
    private AudioTrack audioTrack;

    public class LocalBinder extends BaseEngineService.LocalBinder<TTSEngineService> {
        private final WeakReference<TTSEngineService> serviceRef;
        
        public LocalBinder() {
            this.serviceRef = new WeakReference<>(TTSEngineService.this);
        }
        
        @Override
        public TTSEngineService getService() {
            return serviceRef.get();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return new LocalBinder();
    }

    private CompletableFuture<Boolean> initializeDefaultTTS() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language not supported");
                    future.complete(false);
                } else {
                    textToSpeech.setPitch(1.0f);
                    textToSpeech.setSpeechRate(1.0f);
                    setupUtteranceProgressListener();
                    isTextToSpeechInitialized = true;
                    future.complete(true);
                }
            } else {
                Log.e(TAG, "TTS Initialization failed");
                future.complete(false);
            }
        });

        // Add timeout
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!future.isDone()) {
                future.complete(false);
            }
        }, INIT_TIMEOUT_MS);

        return future;
    }
    private void setupUtteranceProgressListener() {
        if (textToSpeech != null) {
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    Log.d(TAG, "Started speaking: " + utteranceId);
                }

                @Override public void onDone(String utteranceId) {
                    Log.d(TAG, "Finished speaking: " + utteranceId);
                }

                @Override public void onError(String utteranceId) {
                    Log.e(TAG, "Error speaking: " + utteranceId);
                }
            });
        }
    }
    @Override
    public CompletableFuture<Boolean> initialize() {
        return initializeDefaultTTS();
    }

    @Override
    public boolean isReady() {
        return true;
    }

    public CompletableFuture<Boolean> speak(String text) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            //TBD, should start playback on a default audio
            defaultSpeak(text, future);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }
    private void defaultSpeak(String text, CompletableFuture<Boolean> future) {
        try {
            String utteranceId = "TTS_" + System.currentTimeMillis();

            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) {
                    Log.d(TAG, "Started speaking: " + utteranceId);
                }

                @Override public void onDone(String utteranceId) {
                    Log.d(TAG, "Finished speaking: " + utteranceId);
                    future.complete(true);  // ✅ Success
                }

                @Override public void onError(String utteranceId) {
                    Log.e(TAG, "Error speaking: " + utteranceId);
                    future.complete(false); // ✅ Treat as failure
                }
            });

            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);

            if (result != TextToSpeech.SUCCESS) {
                throw new IllegalStateException("TTS speak call failed");
            }

        } catch (Exception e) {
            Log.e(TAG, "Error in default TTS", e);
            future.completeExceptionally(e);  // ✅ Failure
        }
    }    

    public void stopSpeaking() {
        // TBD, cancel the thread of audio playback
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

}