package com.mtkresearch.breezeapp.service;

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CompletableFuture;

import org.pytorch.executorch.extension.llm.LlmCallback;
import org.pytorch.executorch.extension.llm.LlmModule;
import com.executorch.ETImage;
import com.executorch.PromptFormat;

import java.io.File;

public class VLMEngineService extends BaseEngineService {
    private static final String TAG = "VLMEngineService";

    public class LocalBinder extends BaseEngineService.LocalBinder<VLMEngineService> { }

    // LLaVA configuration
    private static final int MODEL_TYPE = LlmModule.MODEL_TYPE_TEXT_VISION;
    private static final int SEQ_LEN = 512;
    private static final int IMAGE_CHANNELS = 3;
    private static final float TEMPERATURE = 0.8f;
    
    private LlmModule mModule;
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

                if (initializeCPUBackend()) {
                    backend = "cpu";
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

    private boolean initializeCPUBackend() {
        try {
            Log.d(TAG, "Attempting CPU backend initialization...");
            initializeCPUModel();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CPU backend", e);
            return false;
        }
    }

    private void initializeCPUModel() {
        try {
            String modelPath = "/data/local/tmp/llava/llava.pte";
            String tokenizerPath = "/data/local/tmp/llava/tokenizer.bin";

            File modelFile = new File(modelPath);
            File tokenizerFile = new File(tokenizerPath);

            if (!modelFile.exists() || !tokenizerFile.exists()) {
                throw new IllegalStateException("Model or tokenizer files not found");
            }

            mModule = new LlmModule(MODEL_TYPE, modelPath, tokenizerPath, TEMPERATURE);
            mModule.load();
            Log.i(TAG, "CPU model initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CPU model", e);
            throw e;
        }
    }

    private void prefillImage(int[] imageData, int width, int height) {
        if (imageData == null || imageData.length == 0) {
            throw new IllegalArgumentException("Invalid image data");
        }

        try {
            Log.d(TAG, "Starting image prefill with dimensions: " + width + "x" + height);
            Log.d(TAG, "Image data length: " + imageData.length);

            // For LLaVA, we need to prefill a preset prompt first
            if (startPos == 0) {
                Log.d(TAG, "Prefilling preset prompt for LLaVA");
                startPos = mModule.prefillPrompt(PromptFormat.getLlavaPresetPrompt(), 0, 1, 0);
                Log.d(TAG, "Preset prompt prefill completed, startPos: " + startPos);
            }

            // Now prefill the image
            startPos = mModule.prefillImages(imageData, width, height, IMAGE_CHANNELS, startPos);
            if (startPos < 0) {
                throw new RuntimeException("Prefill failed with error code: " + startPos);
            }
            Log.d(TAG, "Image prefill successful, new startPos: " + startPos);
            
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
        if (!isInitialized || !backend.equals("cpu")) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Engine not initialized or wrong backend"));
            return future;
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Log.d(TAG, "Processing image: " + imageUri);
                ETImage processedImage = new ETImage(getContentResolver(), imageUri);
                if (processedImage.getWidth() == 0 || processedImage.getHeight() == 0) {
                    throw new IllegalStateException("Failed to process image");
                }

                int[] imageData = processedImage.getInts();
                Log.d(TAG, "Image processed, dimensions: " + processedImage.getWidth() + "x" + processedImage.getHeight());
                prefillImage(imageData, processedImage.getWidth(), processedImage.getHeight());

                CompletableFuture<String> resultFuture = new CompletableFuture<>();
                StringBuilder result = new StringBuilder();

//                String formattedPrompt = PromptFormat.getFormattedSystemAndUserPrompt(userPrompt);
                String formattedPrompt = PromptFormat.getLlavaPresetPrompt(); // TODO: Add the user custom prompt field in app
                Log.d(TAG, "Using formatted prompt: " + formattedPrompt);

                mModule.generateFromPos(formattedPrompt, SEQ_LEN, startPos, new LlmCallback() {
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

                return resultFuture.get();

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