package com.mtkresearch.gai_android.service;

import static com.k2fsa.sherpa.onnx.TtsKt.getOfflineTtsConfig;

import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import com.k2fsa.sherpa.onnx.ModelConfig;
import com.k2fsa.sherpa.onnx.SherpaTTS;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;


public class TTSEngineService extends BaseEngineService {
    private static final String TAG = "TTSEngineService";
    private static final long INIT_TIMEOUT_MS = 20000; // 20 seconds timeout
    private static final String TEST_TEXT = "Hello, this is a test.";
    
    // Model configuration
    private static final String MODEL_DIR = "vits-melo-tts-zh_en";
    private static final String MODEL_NAME = "model.onnx";
    private static final String LEXICON = "lexicon.txt";
    private static final String DICT_DIR = MODEL_DIR + "/dict";
    private static final String RULE_FSTS = String.join(",",
        MODEL_DIR + "/date.fst",
        MODEL_DIR + "/new_heteronym.fst",
        MODEL_DIR + "/number.fst",
        MODEL_DIR + "/phone.fst"
    );
    
    // TTS components
    private TextToSpeech textToSpeech;
    private SherpaTTS localTTS;
    private String backend = "none";
    private boolean isTextToSpeechInitialized = false;
    private AudioTrack audioTrack;

    public class LocalBinder extends BaseEngineService.LocalBinder<TTSEngineService> { }

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    // Callback interface for local TTS
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
                : tryInitializeBackend("Local", this::initializeLocalTTS))
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

    private CompletableFuture<Boolean> initializeLocalTTS() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            Log.d(TAG, "Initializing Local TTS...");
            localTTS = SherpaTTS.Companion.getInstance(
                getApplicationContext()
            );
            future.complete(true);
            Log.d(TAG, "Local TTS initialized with " + localTTS.getNumSpeakers() + " speakers");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Local TTS", e);
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
            if (localTTS == null || !localTTS.isInitialized()) {
                future.complete(false);
                return future;
            }
            
            boolean testResult = localTTS.testTTS();
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
            (backend.equals("local") && localTTS != null) ||
            (backend.equals("default") && isTextToSpeechInitialized)
        );
    }

    public CompletableFuture<Void> speak(String text) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (localTTS == null || !localTTS.isInitialized()) {
            future.completeExceptionally(new IllegalStateException("TTS not initialized"));
            return future;
        }

        try {
            switch (backend) {
                case "mtk":
                    mtkSpeak(text);
                    break;
                case "local":
                    localSpeak(text);
                    break;
                case "default":
                    defaultSpeak(text);
                    break;
                default:
                    throw new IllegalStateException("No TTS backend available");
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private void mtkSpeak(String text) {
        // Placeholder for MTK TTS implementation
        throw new UnsupportedOperationException("MTK TTS not implemented yet");
    }

    private void localSpeak(String text) {
        try {
            initAudioTrack(localTTS.getSampleRate());
            
            localTTS.synthesize(
                text,
                0,  // speakerId
                1.0f,  // speed
                new Function1<float[], Unit>() {
                    @Override
                    public Unit invoke(float[] samples) {
                        playAudioSamples(samples);
                        return Unit.INSTANCE;
                    }
                },
                new Function0<Unit>() {
                    @Override
                    public Unit invoke() {
                        releaseAudioTrack();
                        return Unit.INSTANCE;
                    }
                }
            );
        } catch (Exception e) {
            Log.e(TAG, "Error in local TTS", e);
            releaseAudioTrack();
        }
    }

    private void defaultSpeak(String text) {
        try {
            String utteranceId = "TTS_" + System.currentTimeMillis();
            int result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            
            if (result != TextToSpeech.SUCCESS) {
                throw new IllegalStateException("TTS initialization failed");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in default TTS", e);
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
        int minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        );

        audioTrack = new AudioTrack.Builder()
            .setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build();

        audioTrack.play();
    }

    private void playAudioSamples(float[] samples) {
        if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
        }
    }

    private void releaseAudioTrack() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioTrack", e);
            }
            audioTrack = null;
        }
    }

    public void stopSpeaking() {
        if (backend.equals("local") && localTTS != null) {
            localTTS.stop();
            releaseAudioTrack();
        } else if (backend.equals("default") && textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    @Override
    public void onDestroy() {
        if (localTTS != null) {
            localTTS.release();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        releaseAudioTrack();
        super.onDestroy();
    }

}