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

import com.k2fsa.sherpa.onnx.SherpaTTS;

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
    private SherpaTTS cpuTTS;
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

    // Callback interface for CPU TTS
    private interface SynthesisCallback {
        void onStart();
        void onComplete();
        void onError(String error);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return initializeBackends()
            .thenApply(success -> {
                isInitialized = success;
                Log.d(TAG, String.format("TTS initialization %s using %s", 
                    success ? "SUCCESS ✅" : "FAILED ❌", backend));
                return success;
            });
    }

    private CompletableFuture<Boolean> initializeBackends() {
        return tryInitializeBackend("MTK", this::initializeMTKTTS)
            .thenCompose(success -> success ? CompletableFuture.completedFuture(true)
                : tryInitializeBackend("CPU", this::initializeCPUTTS))
            .thenCompose(success -> success ? CompletableFuture.completedFuture(true)
                : tryInitializeBackend("Default", this::initializeDefaultTTS));
    }

    private CompletableFuture<Boolean> tryInitializeBackend(String backendName, 
            Supplier<CompletableFuture<Boolean>> initializer) {
        return initializer.get()
            .thenCompose(success -> {
                if (success) {
                    Log.d(TAG, "✅ " + backendName + " TTS initialized");
                    backend = backendName.toLowerCase();
                    return testTTSEngine();
                }
                Log.d(TAG, "❌ " + backendName + " TTS failed");
                return CompletableFuture.completedFuture(false);
            });
    }

    private CompletableFuture<Boolean> initializeMTKTTS() {
        return CompletableFuture.completedFuture(false); // Placeholder
    }

    private CompletableFuture<Boolean> initializeCPUTTS() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            Log.d(TAG, "Initializing CPU TTS...");
            cpuTTS = SherpaTTS.Companion.getInstance(getApplicationContext());
            future.complete(true);
            Log.d(TAG, "CPU TTS initialized with " + cpuTTS.getNumSpeakers() + " speakers");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CPU TTS", e);
            future.complete(false);
        }
        return future;
    }

    private void copyAssetFolder(String path) throws IOException {
        String[] files = getApplicationContext().getAssets().list(path);
        if (files == null) return;

        File externalDir = getApplicationContext().getExternalFilesDir(null);
        File destDir = new File(externalDir, path);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        for (String file : files) {
            String subPath = path + "/" + file;
            String[] subFiles = getApplicationContext().getAssets().list(subPath);
            
            if (subFiles != null && subFiles.length > 0) {
                copyAssetFolder(subPath);
            } else {
                copyAssetFile(subPath);
            }
        }
    }

    private void copyAssetFile(String assetPath) throws IOException {
        InputStream in = getApplicationContext().getAssets().open(assetPath);
        File externalDir = getApplicationContext().getExternalFilesDir(null);
        File outFile = new File(externalDir, assetPath);
        
        outFile.getParentFile().mkdirs();
        OutputStream out = new FileOutputStream(outFile);
        
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        
        in.close();
        out.flush();
        out.close();
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

    private CompletableFuture<Boolean> testTTSEngine() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            if (cpuTTS == null || !cpuTTS.isInitialized()) {
                future.complete(false);
                return future;
            }
            
            boolean testResult = cpuTTS.testTTS();
            future.complete(testResult);
        } catch (Exception e) {
            Log.e(TAG, "TTS test failed", e);
            future.complete(false);
        }
        return future;
    }

    @Override
    public boolean isReady() {
        return isInitialized && (
            (backend.equals("cpu") && cpuTTS != null) ||
            (backend.equals("default") && isTextToSpeechInitialized)
        );
    }

    public CompletableFuture<Boolean> speak(String text) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        if (cpuTTS == null || !cpuTTS.isInitialized()) {
            future.completeExceptionally(new IllegalStateException("TTS not initialized"));
            return future;
        }

        try {
            switch (backend) {
                case "mtk":
                    mtkSpeak(text, future);
                    break;
                case "cpu":
                    cpuSpeak(text, future);
                    break;
                case "default":
                    defaultSpeak(text, future);
                    break;
                default:
                    future.completeExceptionally(new IllegalStateException("No TTS backend available"));
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private void mtkSpeak(String text, CompletableFuture<Boolean> future) {
        // Placeholder for MTK TTS implementation
        future.completeExceptionally(new UnsupportedOperationException("MTK TTS not implemented yet"));
    }

    private void cpuSpeak(String text, CompletableFuture<Boolean> future) {
        try {
            initAudioTrack(cpuTTS.getSampleRate());

            final boolean[] gotValidSamples = { false };  // Add a flag
            final boolean[] isSynthesisComplete = { false };

            cpuTTS.synthesize(
                text,
                0, // speakerId
                1.0f, // speed
                new Function1<float[], Unit>() {
                    @Override
                    public Unit invoke(float[] samples) {
                        if (samples != null && samples.length > 0) {
                            gotValidSamples[0] = true; // ✅ Mark we got something valid
                            if (audioTrack != null) {
                                playAudioSamples(samples);
                            }
                        }
                        return Unit.INSTANCE;
                    }
                },
                new Function0<Unit>() {
                    @Override
                    public Unit invoke() {
                        isSynthesisComplete[0] = true;

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            releaseAudioTrack();
                            if (gotValidSamples[0]) {
                                future.complete(true);   // ✅ Normal success
                            } else {
                                future.complete(false);  // ❌ Treat as failed
                            }
                        }, 1000);

                        return Unit.INSTANCE;
                    }
                }
            );

        } catch (Exception e) {
            Log.e(TAG, "Error in CPU TTS", e);
            releaseAudioTrack();
            future.completeExceptionally(e);
        }
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

    private void initAudioTrack(int sampleRate) {
        // Use a larger buffer size for better audio quality
        int minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        );
        int bufferSize = Math.max(minBufferSize * 4, 32768); // Use larger buffer

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // Enforce audibility
            .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build();

        audioTrack = new AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) // Better performance
            .build();

        // Set maximum volume
        audioTrack.setVolume(AudioTrack.getMaxVolume());
        audioTrack.play();
    }

    private void playAudioSamples(float[] samples) {
        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            try {
                // Amplify the samples to increase volume
                float[] amplifiedSamples = new float[samples.length];
                for (int i = 0; i < samples.length; i++) {
                    // Amplify by 3x while preventing clipping
                    amplifiedSamples[i] = Math.max(-1.0f, Math.min(1.0f, samples[i] * 3.0f));
                }
                
                // Write samples with timeout to prevent blocking
                int result = audioTrack.write(amplifiedSamples, 0, amplifiedSamples.length, AudioTrack.WRITE_BLOCKING);
                if (result < 0) {
                    Log.e(TAG, "Error writing audio samples: " + result);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error playing audio samples", e);
            }
        }
    }

    private void releaseAudioTrack() {
        if (audioTrack != null) {
            try {
                // Ensure all queued audio is played before stopping
                audioTrack.stop();
                Thread.sleep(100); // Small delay to ensure clean stop
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioTrack", e);
            }
            audioTrack = null;
        }
    }

    public void stopSpeaking() {
        if (backend.equals("cpu") && cpuTTS != null) {
            cpuTTS.stop();
            releaseAudioTrack();
        } else if (backend.equals("default") && textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (cpuTTS != null) {
            cpuTTS.release();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        releaseAudioTrack();
        super.onDestroy();
    }

}