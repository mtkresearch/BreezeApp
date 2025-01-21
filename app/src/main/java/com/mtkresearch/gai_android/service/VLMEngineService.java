package com.mtkresearch.gai_android.service;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.pytorch.executorch.LlamaCallback;
import org.pytorch.executorch.LlamaModule;
import com.executorch.ETImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.File;

public class VLMEngineService extends BaseEngineService {
    private static final String TAG = "VLMEngineService";

    public class LocalBinder extends BaseEngineService.LocalBinder<VLMEngineService> { }

    // LLaVA configuration
    private static final int MODEL_TYPE = LlamaModule.MODEL_TYPE_TEXT_VISION;
    private static final int SEQ_LEN = 512;
    private static final int IMAGE_CHANNELS = 3;
    private static final float TEMPERATURE = 0.8f;
    
    private LlamaModule mModule;
    private long startPos = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (initializeMTKBackend()) {
                    backend = "mtk";
                    isInitialized = true;
                    return true;
                }

                if (initializeLocalCPUBackend()) {
                    backend = "local_cpu";
                    isInitialized = true;
                    return true;
                }

                Log.e(TAG, "All backend initialization attempts failed");
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error during initialization", e);
                return false;
            }
        });
    }

    private boolean initializeMTKBackend() {
        try {
            Log.d(TAG, "Attempting MTK backend initialization...");
            // TODO: Implement MTK backend initialization
            return false; // For now, return false to fall back to local_cpu
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MTK backend", e);
            return false;
        }
    }

    private boolean initializeLocalCPUBackend() {
        try {
            Log.d(TAG, "Attempting Local CPU backend initialization...");
            initializeLocalCpuModel();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize local CPU backend", e);
            return false;
        }
    }

    private void initializeLocalCpuModel() {
        try {
            String modelPath = "/data/local/tmp/llava/llava.pte";
            String tokenizerPath = "/data/local/tmp/llava/tokenizer.bin";

            File modelFile = new File(modelPath);
            File tokenizerFile = new File(tokenizerPath);

            if (!modelFile.exists() || !tokenizerFile.exists()) {
                throw new IllegalStateException("Model or tokenizer files not found");
            }

            mModule = new LlamaModule(MODEL_TYPE, modelPath, tokenizerPath, TEMPERATURE);
            mModule.load();
            Log.i(TAG, "Local CPU model initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize local CPU model", e);
            throw e;
        }
    }

    private void prefillImage(int[] imageData, int width, int height) {
        if (imageData == null || imageData.length == 0) {
            throw new IllegalArgumentException("Invalid image data");
        }

        try {
            // Create a direct ByteBuffer for integers
            ByteBuffer buffer = ByteBuffer.allocateDirect(imageData.length * 4); // 4 bytes per int
            buffer.order(ByteOrder.nativeOrder());
            
            // Put the int array into the buffer
            for (int value : imageData) {
                buffer.putInt(value);
            }
            buffer.rewind();

            // Get the int array back from the buffer
            int[] contiguousData = new int[imageData.length];
            for (int i = 0; i < imageData.length; i++) {
                contiguousData[i] = buffer.getInt();
            }

            long result = mModule.prefillImages(contiguousData, width, height, IMAGE_CHANNELS, 0);
            if (result < 0) {
                throw new RuntimeException("Prefill failed with error code: " + result);
            }
            startPos = result;
            
        } catch (Exception e) {
            Log.e(TAG, "Error during image prefill", e);
            throw e;
        }
    }

    @Override
    public boolean isReady() {
        return isInitialized;
    }

    public CompletableFuture<String> analyzeImage(Uri imageUri, String userPrompt) {
        if (!isInitialized || !backend.equals("local_cpu")) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Engine not initialized or wrong backend"));
            return future;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                ETImage processedImage = new ETImage(getContentResolver(), imageUri);
                if (processedImage.getWidth() == 0 || processedImage.getHeight() == 0) {
                    throw new IllegalStateException("Failed to process image");
                }

                int[] imageData = processedImage.getInts();
                prefillImage(imageData, processedImage.getWidth(), processedImage.getHeight());

                CompletableFuture<String> resultFuture = new CompletableFuture<>();
                StringBuilder result = new StringBuilder();

                mModule.generateFromPos(userPrompt, SEQ_LEN, startPos, new LlamaCallback() {
                    public void onToken(String token) {
                        result.append(token);
                    }

                    @Override
                    public void onResult(String s) {
                        result.append(s);
                        resultFuture.complete(result.toString());
                    }

                    @Override
                    public void onStats(float tokensPerSecond) {
                        Log.i(TAG, "Generation speed: " + tokensPerSecond + " tokens/sec");
                    }
                }, false);

                return resultFuture.get(); // Remove timeout

            } catch (Exception e) {
                Log.e(TAG, "Error analyzing image", e);
                throw new RuntimeException("Failed to analyze image: " + e.getMessage(), e);
            }
        });
    }

    private void resetModel() {
        if (mModule != null) {
            mModule.resetNative();
            startPos = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        if (mModule != null) {
            mModule.resetNative();
        }
        super.finalize();
    }
} 