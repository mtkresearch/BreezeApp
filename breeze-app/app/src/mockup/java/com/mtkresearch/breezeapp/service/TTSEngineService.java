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


    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    public CompletableFuture<Boolean> speak(String text) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            //TBD, should start playback on a default audio
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
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