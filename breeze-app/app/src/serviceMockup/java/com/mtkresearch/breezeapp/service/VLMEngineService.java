package com.mtkresearch.breezeapp.service;

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CompletableFuture;



import java.io.File;

public class VLMEngineService extends BaseEngineService {
    private static final String TAG = "VLMEngineService";

    public class LocalBinder extends BaseEngineService.LocalBinder<VLMEngineService> { }

    private long startPos = 0;

    @Override
    public IBinder onBind(Intent intent) {
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

    public CompletableFuture<String> analyzeImage(Uri imageUri, String userPrompt) {

        return CompletableFuture.completedFuture("This is mockup.");
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
} 