package com.mtkresearch.breeze_app.service;

import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CompletableFuture;

import org.pytorch.executorch.LlamaCallback;
import org.pytorch.executorch.LlamaModule;
import com.executorch.ETImage;
import com.executorch.PromptFormat;
import com.mtkresearch.breeze_app.utils.AppConstants;

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
    private String modelPath;
    private String tokenizerPath;

    @Override
    public IBinder onBind(Intent intent) {
        return new LocalBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.hasExtra("model_path")) {
                modelPath = intent.getStringExtra("model_path");
                Log.d(TAG, "Using model path: " + modelPath);
            } else {
                // Use default model path
                modelPath = "/data/local/tmp/llava/llava.pte";
                Log.d(TAG, "Using default model path: " + modelPath);
            }
            
            if (intent.hasExtra("tokenizer_path")) {
                tokenizerPath = intent.getStringExtra("tokenizer_path");
            } else {
                tokenizerPath = "/data/local/tmp/llava/tokenizer.bin";
            }
        }
        
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (initializeLocalCPUBackend()) {
                    isInitialized = true;
                    return true;
                }

                Log.e(TAG, "CPU backend initialization failed");
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error during initialization", e);
                return false;
            }
        });
    }

    private boolean initializeLocalCPUBackend() {
        try {
            Log.d(TAG, "Initializing CPU backend...");
            
            File modelFile = new File(modelPath);
            File tokenizerFile = new File(tokenizerPath);

            if (!modelFile.exists() || !tokenizerFile.exists()) {
                Log.e(TAG, "Model or tokenizer files not found at paths: " + 
                      modelPath + ", " + tokenizerPath);
                return false;
            }

            mModule = new LlamaModule(MODEL_TYPE, modelPath, tokenizerPath, TEMPERATURE);
            int loadResult = mModule.load();
            
            if (loadResult != 0) {
                Log.e(TAG, "Failed to load model: " + loadResult);
                return false;
            }
            
            Log.i(TAG, "CPU model initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize CPU backend", e);
            return false;
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
        if (!isInitialized) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Engine not initialized"));
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

                // Format the prompt with user's question
                String formattedPrompt = userPrompt != null && !userPrompt.isEmpty() 
                    ? formatVisionPrompt(userPrompt)
                    : PromptFormat.getLlavaPresetPrompt();
                    
                Log.d(TAG, "Using formatted prompt: " + formattedPrompt);

                mModule.generateFromPos(formattedPrompt, SEQ_LEN, startPos, new LlamaCallback() {
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

    /**
     * Helper method to format a vision prompt with user input
     * This replaces the need for PromptFormat.formatVisionPrompt which doesn't exist
     */
    private String formatVisionPrompt(String userPrompt) {
        // Use the LLaVA preset prompt and append the user's question
        return PromptFormat.getLlavaPresetPrompt() + userPrompt + " ASSISTANT:";
    }

    public void resetModel() {
        if (mModule != null) {
            mModule.resetNative();
            startPos = 0;
        }
    }
    
    public void releaseResources() {
        if (mModule != null) {
            try {
                mModule.resetNative();
                mModule = null;
                startPos = 0;
                isInitialized = false;
                Log.d(TAG, "Successfully released resources");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing resources", e);
            }
        }
    }

    @Override
    public void onDestroy() {
        releaseResources();
        super.onDestroy();
    }
} 