package com.mtkresearch.gai_android.service;

import android.app.Service;
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

import com.k2fsa.sherpa.onnx.SherpaASR;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ASREngineService extends BaseEngineService {
    private static final String TAG = "ASREngineService";
    private static final String TEST_PHRASE = "with";
    private static final String TEST_AUDIO_PATH = "test_wavs/test_wavs_8k.wav";
    private static final int RECOGNITION_TIMEOUT = 10000; // 10 seconds
    
    private SpeechRecognizer speechRecognizer;
    private SherpaASR sherpaASR;
    private String backend = "none";
    private boolean isListening = false;
    private Consumer<String> currentCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public class LocalBinder extends BaseEngineService.LocalBinder<ASREngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return initializeBackends()
            .thenApply(success -> {
                isInitialized = success;
                Log.d(TAG, String.format("ASR initialization %s using %s", 
                    success ? "SUCCESS ✅" : "FAILED ❌", backend));
                return success;
            });
    }

    private CompletableFuture<Boolean> initializeBackends() {
        return tryInitializeBackend("MTK", this::initializeMTKASR)
            .thenCompose(success -> success ? CompletableFuture.completedFuture(true)
                : tryInitializeBackend("Local", this::initializeLocalASR))
            .thenCompose(success -> success ? CompletableFuture.completedFuture(true)
                : tryInitializeBackend("Default", this::initializeDefaultASR));
    }

    private CompletableFuture<Boolean> tryInitializeBackend(String backendName, 
            Supplier<CompletableFuture<Boolean>> initializer) {
        return initializer.get()
            .thenCompose(success -> {
                if (success) {
                    Log.d(TAG, "✅ " + backendName + " ASR initialized");
                    backend = backendName.toLowerCase();
                    return testASREngine();
                }
                Log.d(TAG, "❌ " + backendName + " ASR failed");
                return CompletableFuture.completedFuture(false);
            });
    }

    private CompletableFuture<Boolean> initializeMTKASR() {
        return CompletableFuture.completedFuture(false); // Placeholder
    }

    private CompletableFuture<Boolean> initializeLocalASR() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            sherpaASR = new SherpaASR(getApplicationContext());
            sherpaASR.initialize();
            future.complete(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Local ASR", e);
            future.complete(false);
        }
        return future;
    }

    private CompletableFuture<Boolean> initializeDefaultASR() {
        if (!checkPermission()) {
            Log.e(TAG, "Missing RECORD_AUDIO permission");
            return CompletableFuture.completedFuture(false);
        }

        boolean isAvailable = SpeechRecognizer.isRecognitionAvailable(getApplicationContext());
        if (isAvailable) {
            setupSpeechRecognizer();
        }
        return CompletableFuture.completedFuture(isAvailable);
    }

    private CompletableFuture<Boolean> testASREngine() {
        Log.d(TAG, "Testing " + backend + " ASR engine...");
        return (backend.equals("local") ? testLocalASR() : testDefaultASR())
            .thenApply(result -> {
                boolean success = result != null && result.toLowerCase().contains(TEST_PHRASE);
                Log.d(TAG, String.format("%s ASR Test %s: %s",
                    backend.toUpperCase(),
                    success ? "PASSED" : "FAILED",
                    result != null ? "\"" + result + "\"" : "null"));
                return success;
            });
    }

    private CompletableFuture<String> testLocalASR() {
        CompletableFuture<String> future = new CompletableFuture<>();
        sherpaASR.transcribeAsset(TEST_AUDIO_PATH, new SherpaASR.ASRListener() {
            @Override public void onPartialResult(String text) {}
            @Override public void onFinalResult(String text) { future.complete(text); }
            @Override public void onError(String error) { future.complete(null); }
        });
        return future;
    }

    private CompletableFuture<String> testDefaultASR() {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(getAssets().openFd(TEST_AUDIO_PATH));
            player.prepare();
            
            setupSpeechRecognizer();
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) { player.start(); }
                
                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION);
                    future.complete(matches != null && !matches.isEmpty() ? matches.get(0) : null);
                    player.release();
                }
                
                @Override
                public void onError(int error) {
                    future.complete(null);
                    player.release();
                }
                
                // Required empty implementations
                @Override public void onBeginningOfSpeech() {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
            
            startRecognition();
            setTestTimeout(future);
            
        } catch (Exception e) {
            Log.e(TAG, "Error in default ASR test", e);
            future.complete(null);
        }
        return future;
    }

    public void startListening(Consumer<String> callback) {
        if (!validateListeningPrerequisites(callback)) return;
        
        try {
            switch (backend) {
                case "local":
                    startLocalListening(callback);
                    break;
                case "default":
                    startDefaultListening(callback);
                    break;
                default:
                    notifyError(callback, "No ASR backend available");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting ASR", e);
            notifyError(callback, "Failed to start ASR");
        }
    }

    private void startLocalListening(Consumer<String> callback) {
        sherpaASR.startRecognition(new SherpaASR.ASRListener() {
            @Override
            public void onPartialResult(String text) {
                callback.accept("Partial: " + text);
            }

            @Override
            public void onFinalResult(String text) {
                callback.accept(text);
            }

            @Override
            public void onError(String error) {
                notifyError(callback, error);
                isListening = false;
            }
        });
        isListening = true;
        currentCallback = callback;
    }

    private void startDefaultListening(Consumer<String> callback) {
        setupSpeechRecognizer();
        speechRecognizer.setRecognitionListener(createRecognitionListener(callback));
        startRecognition();
        isListening = true;
        currentCallback = callback;
    }

    private RecognitionListener createRecognitionListener(Consumer<String> callback) {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                callback.accept("Ready for speech...");
            }

            @Override
            public void onResults(Bundle results) {
                processResults(results, callback, false);
                isListening = false;
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                processResults(partialResults, callback, true);
            }

            @Override
            public void onError(int error) {
                notifyError(callback, getErrorMessage(error));
                isListening = false;
            }

            // Required empty implementations
            @Override public void onBeginningOfSpeech() {}
            @Override public void onEndOfSpeech() {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        };
    }

    public void stopListening() {
        if (isListening) {
            if (backend.equals("local")) {
                sherpaASR.stopRecognition();
            } else if (backend.equals("default")) {
                speechRecognizer.stopListening();
            }
            isListening = false;
            currentCallback = null;
        }
    }

    @Override
    public void onDestroy() {
        if (sherpaASR != null) sherpaASR.release();
        if (speechRecognizer != null) speechRecognizer.destroy();
        super.onDestroy();
    }

    private void setupSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        }
    }

    private void startRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        speechRecognizer.startListening(intent);
    }

    private void processResults(Bundle results, Consumer<String> callback, boolean isPartial) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String result = matches.get(0);
            callback.accept(isPartial ? "Partial: " + result : result);
        }
    }

    private void setTestTimeout(CompletableFuture<String> future) {
        mainHandler.postDelayed(() -> {
            if (!future.isDone()) {
                Log.d(TAG, "ASR test timed out");
                future.complete(null);
            }
        }, RECOGNITION_TIMEOUT);
    }

    private boolean validateListeningPrerequisites(Consumer<String> callback) {
        if (!isInitialized) {
            notifyError(callback, "ASR not initialized");
            return false;
        }
        if (isListening) {
            notifyError(callback, "Already listening");
            return false;
        }
        return true;
    }

    private void notifyError(Consumer<String> callback, String message) {
        Log.e(TAG, message);
        if (callback != null) {
            callback.accept("Error: " + message);
        }
    }

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(getApplicationContext(),
            android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private String getErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Audio recording error";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Client side error";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "Insufficient permissions";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Network error";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "Network timeout";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "No match found";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Recognition service busy";
            case SpeechRecognizer.ERROR_SERVER:
                return "Server error";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "No speech input";
            default:
                return "Unknown error: " + errorCode;
        }
    }
} 