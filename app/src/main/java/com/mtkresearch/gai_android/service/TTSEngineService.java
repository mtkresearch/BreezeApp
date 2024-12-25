package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class TTSEngineService extends BaseEngineService {
    private static final String TAG = "TTSEngineService";
    private TextToSpeech textToSpeech;
    private boolean isTextToSpeechInitialized = false;
    private static final int TTS_DATA_CHECK_CODE = 1000;
    private static final long INIT_TIMEOUT_MS = 20000; // 20 seconds timeout
    private String backend = null;

    public class LocalBinder extends BaseEngineService.LocalBinder<TTSEngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        // Try MTK TTS first
        initializeMTKTTS()
            .thenAccept(success -> {
                if (success) {
                    backend = "mtk";
                    future.complete(true);
                } else {
                    // If MTK fails, try OpenAI TTS
                    initializeOpenAITTS()
                        .thenAccept(openAISuccess -> {
                            if (openAISuccess) {
                                backend = "openai";
                                future.complete(true);
                            } else {
                                // If both fail, try default Android TTS
                                initializeDefaultTTS()
                                    .thenAccept(defaultSuccess -> {
                                        if (defaultSuccess) {
                                            backend = "default";
                                            future.complete(true);
                                        } else {
                                            future.complete(false);
                                        }
                                    });
                            }
                        });
                }
            });
        
        return future;
    }

    private CompletableFuture<Boolean> initializeMTKTTS() {
        Log.d(TAG, "Attempting to initialize MTK TTS");
        // TODO: Implement MTK TTS initialization
        return CompletableFuture.completedFuture(false); // For now, return false to try next option
    }

    private CompletableFuture<Boolean> initializeOpenAITTS() {
        Log.d(TAG, "Attempting to initialize OpenAI TTS");
        // TODO: Implement OpenAI TTS initialization
        return CompletableFuture.completedFuture(false); // For now, return false to try next option
    }

    private CompletableFuture<Boolean> initializeDefaultTTS() {
        Log.d(TAG, "Attempting to initialize default Android TTS");
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // First check if TTS engine is installed
        if (!isTTSEngineInstalled()) {
            Log.e(TAG, "No TTS engine installed");
//            promptInstallTTSEngine();
            future.complete(false);
            return future;
        }

        // Then check and prepare TTS data
        prepareTTSData().thenAccept(prepared -> {
            if (!prepared) {
                future.complete(false);
                return;
            }

            // Initialize TTS after data is prepared
            initializeTTSEngine(future);
        });

        return future;
    }

    private boolean isTTSEngineInstalled() {
        PackageManager pm = getApplicationContext().getPackageManager();
        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        List<ResolveInfo> list = pm.queryIntentActivities(checkIntent, 
            PackageManager.MATCH_DEFAULT_ONLY);
        return !list.isEmpty();
    }

    private void promptInstallTTSEngine() {
        Intent installIntent = new Intent();
        installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getApplicationContext().startActivity(installIntent);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not launch TTS installation", e);
        }
    }

    private CompletableFuture<Boolean> prepareTTSData() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Check if language data is available
                int result = textToSpeech.isLanguageAvailable(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.d(TAG, "Language data missing, initiating download");
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        getApplicationContext().startActivity(installIntent);
                        // We return false here as the user needs to complete the installation
                        future.complete(false);
                    } catch (ActivityNotFoundException e) {
                        Log.e(TAG, "Could not launch language data installation", e);
                        future.complete(false);
                    }
                } else {
                    Log.d(TAG, "Language data is available");
                    future.complete(true);
                }
            } else {
                Log.e(TAG, "TTS initialization failed in preparation phase");
                future.complete(false);
            }
            // Cleanup this temporary TTS instance
            textToSpeech.shutdown();
        });
        
        return future;
    }

    private void initializeTTSEngine(CompletableFuture<Boolean> future) {
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.US);
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "Language still not available after preparation");
                    isTextToSpeechInitialized = false;
                    future.complete(false);
                } else {
                    textToSpeech.setPitch(1.0f);
                    textToSpeech.setSpeechRate(1.0f);
                    setupUtteranceProgressListener();
                    isTextToSpeechInitialized = true;
                    isInitialized = true;
                    Log.d(TAG, "TTS successfully initialized");
                    future.complete(true);
                }
            } else {
                Log.e(TAG, "Final TTS Initialization failed");
                isTextToSpeechInitialized = false;
                future.complete(false);
            }
        });

        // Add timeout for initialization
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!future.isDone()) {
                Log.e(TAG, "TTS initialization timeout");
                future.complete(false);
            }
        }, INIT_TIMEOUT_MS);
    }

    @Override
    public boolean isReady() {
        return isInitialized && isTextToSpeechInitialized;
    }

    public CompletableFuture<Boolean> speak(String text) {
        if (!isReady()) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("TTS not initialized"));
            return future;
        }

        switch (backend) {
            case "mtk":
                return mtkSpeak(text);
            case "openai":
                return openaiSpeak(text);
            case "default":
                return defaultSpeak(text);
            default:
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("No TTS backend available"));
                return future;
        }
    }

    private CompletableFuture<Boolean> mtkSpeak(String text) {
        // TODO: Implement MTK speak
        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> openaiSpeak(String text) {
        // TODO: Implement OpenAI speak
        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> defaultSpeak(String text) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            String utteranceId = "TTS_" + System.currentTimeMillis();
            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            
            if (result == TextToSpeech.SUCCESS) {
                Log.d(TAG, "Started speaking text using default TTS");
                future.complete(true);
            } else {
                Log.e(TAG, "Failed to speak text using default TTS");
                future.complete(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error speaking text using default TTS", e);
            future.completeExceptionally(e);
        }
        
        return future;
    }

    @Override
    public void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    private CompletableFuture<File> mtkConvertTextToSpeech(String text) {
        // MTK TTS implementation
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<File> openaiConvertTextToSpeech(String text) {
        // OpenAI TTS implementation
        return CompletableFuture.completedFuture(null);
    }

    private void setupUtteranceProgressListener() {
        if (textToSpeech != null) {
            textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override
                public void onStart(String utteranceId) {
                    Log.d(TAG, "Started speaking: " + utteranceId);
                }

                @Override
                public void onDone(String utteranceId) {
                    Log.d(TAG, "Finished speaking: " + utteranceId);
                }

                @Override
                public void onError(String utteranceId) {
                    Log.e(TAG, "Error speaking: " + utteranceId);
                }

                @Override
                public void onStop(String utteranceId, boolean interrupted) {
                    Log.d(TAG, "Stopped speaking: " + utteranceId + ", interrupted: " + interrupted);
                }
            });
        } else {
            Log.e(TAG, "Cannot setup UtteranceProgressListener: TTS is null");
        }
    }
}