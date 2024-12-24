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

public class ASREngineService extends BaseEngineService {
    private static final String TAG = "ASREngineService";
    private static final String TEST_PHRASE = "with";
    private static final String TEST_AUDIO_PATH = "test_wavs/test_wavs_8k.wav";
    private static final int RECOGNITION_TIMEOUT = 10000; // 10 seconds
    
    // Default ASR components
    private SpeechRecognizer speechRecognizer;
    
    // Local ASR components
    private SherpaASR sherpaASR;
    
    // Shared state
    private String backend = "none";
    private boolean isListening = false;
    private Consumer<String> currentCallback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public class LocalBinder extends BaseEngineService.LocalBinder<ASREngineService> {
        @Override
        public ASREngineService getService() {
            return ASREngineService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        Log.d(TAG, "Starting ASR initialization sequence...");
        
        // Try backends in order: MTK -> Local -> Default
        initializeMTKASR()
            .thenCompose(mtkSuccess -> handleBackendInitialization("MTK", mtkSuccess))
            .thenCompose(success -> success ? CompletableFuture.completedFuture(true) 
                : initializeLocalASR().thenCompose(localSuccess -> 
                    handleBackendInitialization("Local", localSuccess)))
            .thenCompose(success -> success ? CompletableFuture.completedFuture(true)
                : initializeDefaultASR().thenCompose(defaultSuccess ->
                    handleBackendInitialization("Default", defaultSuccess)))
            .thenAccept(finalResult -> {
                isInitialized = finalResult;
                Log.d(TAG, String.format("ASR initialization complete. Result: %s using backend: %s",
                    finalResult ? "SUCCESS ✅" : "FAILED ❌", backend));
                future.complete(finalResult);
            });
        
        return future;
    }

    private CompletableFuture<Boolean> handleBackendInitialization(String backendType, boolean success) {
        if (success) {
            Log.d(TAG, "✅ " + backendType + " ASR initialized successfully");
            backend = backendType.toLowerCase();
            return testASREngine(backendType);
        }
        Log.d(TAG, "❌ " + backendType + " ASR failed");
        return CompletableFuture.completedFuture(false);
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

    public void stopListening() {
        switch (backend) {
            case "local":
                stopLocalListening();
                break;
            case "default":
                stopDefaultListening();
                break;
        }
        isListening = false;
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

    private void setupSpeechRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplicationContext());
        }
        speechRecognizer.setRecognitionListener(createRecognitionListener());
    }

    private void startRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        
        speechRecognizer.startListening(intent);
        Log.d(TAG, "Started listening with " + backend + " ASR");
    }

    private RecognitionListener createRecognitionListener() {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready for speech");
                isListening = true;
                notifyCallback("Ready for speech...");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Speech started");
            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "Speech ended");
                isListening = false;
            }

            @Override
            public void onError(int error) {
                String errorMessage = getErrorMessage(error);
                Log.e(TAG, "Speech recognition error: " + errorMessage);
                notifyCallback("Error: " + errorMessage);
                isListening = false;
            }

            @Override
            public void onResults(Bundle results) {
                processResults(results, false);
                isListening = false;
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                processResults(partialResults, true);
            }

            // Unused callbacks
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        };
    }

    private void processResults(Bundle results, boolean isPartial) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String result = matches.get(0);
            Log.d(TAG, (isPartial ? "Partial" : "Final") + " result: " + result);
            notifyCallback(isPartial ? "Partial: " + result : result);
        }
    }

    private void notifyCallback(String message) {
        if (currentCallback != null) {
            currentCallback.accept(message);
        }
    }

    private CompletableFuture<Boolean> testASREngine(String engineType) {
        Log.d(TAG, "Testing " + engineType + " ASR engine...");
        CompletableFuture<String> resultFuture;
        
        try {
            if (engineType.equalsIgnoreCase("local")) {
                // Use direct file transcription for local ASR testing
                resultFuture = new CompletableFuture<>();
                sherpaASR.transcribeAsset(TEST_AUDIO_PATH, new SherpaASR.ASRListener() {
                    @Override
                    public void onPartialResult(String text) {
                        Log.d(TAG, "Test partial result: " + text);
                    }

                    @Override
                    public void onFinalResult(String text) {
                        Log.d(TAG, "Test final result: " + text);
                        resultFuture.complete(text);
                    }

                    @Override
                    public void onError(String error) {
                        Log.e(TAG, "Test transcription error: " + error);
                        resultFuture.complete(null);
                    }
                });
            } else {
                // Use MediaPlayer + SpeechRecognizer for default ASR testing
                resultFuture = convertSpeechToText();
            }

            return resultFuture.thenApply(result -> {
                boolean matches = result != null && result.toLowerCase().contains(TEST_PHRASE);
                Log.d(TAG, String.format("%s %s ASR Test! %s",
                    matches ? "✅" : "❌",
                    engineType,
                    matches ? "Found \"test\" in: \"" + result + "\"" 
                           : "Could not find \"test\" in: \"" + result + "\""));
                return matches;
            });

        } catch (Exception e) {
            Log.e(TAG, "❌ Error running " + engineType + " ASR test", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    private CompletableFuture<String> convertSpeechToText() {
        CompletableFuture<String> future = new CompletableFuture<>();
        MediaPlayer mediaPlayer = new MediaPlayer();
        
        try {
            mediaPlayer.setDataSource(getAssets().openFd(TEST_AUDIO_PATH));
            mediaPlayer.prepare();
            
            final boolean[] isReleased = {false};
            
            mediaPlayer.setOnCompletionListener(mp -> {
                if (!isReleased[0]) {
                    mp.release();
                    isReleased[0] = true;
                }
            });
            
            setupSpeechRecognizer();
            speechRecognizer.setRecognitionListener(createTestRecognitionListener(future, mediaPlayer, isReleased));
            startRecognition();

            // Set timeout
            mainHandler.postDelayed(() -> {
                if (!future.isDone()) {
                    Log.d(TAG, "Recognition test timed out");
                    future.complete(null);
                    if (!isReleased[0]) {
                        mediaPlayer.release();
                        isReleased[0] = true;
                    }
                }
            }, RECOGNITION_TIMEOUT);

        } catch (Exception e) {
            Log.e(TAG, "Error during speech to text conversion", e);
            future.complete(null);
            mediaPlayer.release();
        }
        
        return future;
    }

    private RecognitionListener createTestRecognitionListener(
            CompletableFuture<String> future, 
            MediaPlayer mediaPlayer, 
            boolean[] isReleased) {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (!isReleased[0]) {
                    mediaPlayer.start();
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    future.complete(matches.get(0));
                } else {
                    future.complete(null);
                }
                
                if (!isReleased[0]) {
                    mediaPlayer.release();
                    isReleased[0] = true;
                }
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "Recognition error during test: " + getErrorMessage(error));
                future.complete(null);
                
                if (!isReleased[0]) {
                    mediaPlayer.release();
                    isReleased[0] = true;
                }
            }

            // Other required methods
            @Override public void onBeginningOfSpeech() {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        };
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

    private boolean checkPermission() {
        return ContextCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void copyAssetFile(String assetPath, File destFile) throws IOException {
        try (InputStream in = getApplicationContext().getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(destFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    // Placeholder methods for different backends
    private CompletableFuture<Boolean> initializeMTKASR() {
        return CompletableFuture.completedFuture(false);
    }

    private CompletableFuture<Boolean> initializeLocalASR() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            sherpaASR = new SherpaASR(getApplicationContext());
            sherpaASR.initialize(); // Initialize the model and check permissions
            future.complete(true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Local ASR", e);
            future.complete(false);
        }
        
        return future;
    }

    private void startLocalListening(Consumer<String> callback) {
        if (!validateListeningPrerequisites(callback)) return;
        
        try {
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
        } catch (Exception e) {
            Log.e(TAG, "Error starting local ASR", e);
            notifyError(callback, "Failed to start local ASR");
        }
    }

    private void stopLocalListening() {
        if (sherpaASR != null) {
            sherpaASR.stopRecognition();
        }
        isListening = false;
        currentCallback = null;
    }

    private CompletableFuture<Boolean> initializeDefaultASR() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        try {
            if (!checkPermission()) {
                Log.e(TAG, "Missing RECORD_AUDIO permission");
                return CompletableFuture.completedFuture(false);
            }

            boolean isAvailable = SpeechRecognizer.isRecognitionAvailable(getApplicationContext());
            if (isAvailable) {
                setupSpeechRecognizer();
                Log.d(TAG, "Default ASR initialized successfully");
            } else {
                Log.e(TAG, "Speech recognition is not available on this device");
            }
            future.complete(isAvailable);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing speech recognizer", e);
            future.complete(false);
        }
        
        return future;
    }

    private void startDefaultListening(Consumer<String> callback) {
        if (!validateListeningPrerequisites(callback)) return;
        
        try {
            setupSpeechRecognizer();
            speechRecognizer.setRecognitionListener(createRecognitionListener(callback));
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, 
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            
            speechRecognizer.startListening(intent);
            isListening = true;
            currentCallback = callback;
            Log.d(TAG, "Started listening with default ASR");
        } catch (Exception e) {
            Log.e(TAG, "Error starting default ASR", e);
            notifyError(callback, "Failed to start default ASR");
        }
    }

    private void stopDefaultListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        isListening = false;
        currentCallback = null;
    }

    private RecognitionListener createRecognitionListener(Consumer<String> callback) {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "Ready for speech");
                callback.accept("Ready for speech...");
            }

            @Override
            public void onBeginningOfSpeech() {
                Log.d(TAG, "Speech started");
            }

            @Override
            public void onEndOfSpeech() {
                Log.d(TAG, "Speech ended");
                isListening = false;
            }

            @Override
            public void onError(int error) {
                String errorMessage = getErrorMessage(error);
                Log.e(TAG, "Speech recognition error: " + errorMessage);
                notifyError(callback, errorMessage);
                isListening = false;
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

            // Unused callbacks
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        };
    }

    private void processResults(Bundle results, Consumer<String> callback, boolean isPartial) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String result = matches.get(0);
            Log.d(TAG, (isPartial ? "Partial" : "Final") + " result: " + result);
            callback.accept(isPartial ? "Partial: " + result : result);
        }
    }

    @Override
    public void onDestroy() {
        if (sherpaASR != null) {
            sherpaASR.release();
            sherpaASR = null;
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        super.onDestroy();
    }
} 