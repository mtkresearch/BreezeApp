package com.mtkresearch.breeze_app.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.content.Context;

import java.util.concurrent.CompletableFuture;

public abstract class BaseEngineService extends Service {
    protected static final String TAG = "BaseEngineService";
    protected Context context;
    protected boolean isInitialized = false;

    public class LocalBinder<T extends BaseEngineService> extends Binder {
        @SuppressWarnings("unchecked")
        public T getService() {
            return (T) BaseEngineService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // This should be overridden by child classes
    }

    public abstract CompletableFuture<Boolean> initialize();
    
    public boolean isReady() {
        return isInitialized;
    }
} 