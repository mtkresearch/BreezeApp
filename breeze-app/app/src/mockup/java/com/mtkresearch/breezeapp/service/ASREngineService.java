package com.mtkresearch.breezeapp.service;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ASREngineService extends BaseEngineService {


    public class LocalBinder extends BaseEngineService.LocalBinder<ASREngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.completedFuture(true);
    }


    public void startListening(Consumer<String> callback) {
        //TBD should create a thread and response callback in a random order
    }


    public void stopListening() {
        //TBD should join the thread of response callback

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

} 