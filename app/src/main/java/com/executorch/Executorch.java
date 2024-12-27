package com.executorch;

import android.util.Log;
import org.pytorch.executorch.LlamaModule;
import org.pytorch.executorch.LlamaCallback;
import java.util.concurrent.CompletableFuture;

public class Executorch {
    private static final String TAG = "ExecutorchLLM";
    private LlamaModule mModule = null;

    // Configuration
    private static final String MODEL_PATH = "/data/local/tmp/llama/llama3_2.pte";
    private static final String TOKENIZER_PATH = "/data/local/tmp/llama/tokenizer.bin";
    private static final float TEMPERATURE = 0.8f;
    private static final int MAX_SEQUENCE_LENGTH = 256;

    public interface TokenCallback {
        void onToken(String token);
        default void onStats(float tokensPerSecond) {
            Log.d(TAG, "Generation speed: " + tokensPerSecond + " tokens/sec");
        }
    }

    public boolean initialize() {
        try {
            Log.d(TAG, "Initializing ExecutorchLLM...");

            if (mModule != null) {
                mModule.resetNative();
                mModule = null;
            }

            mModule = new LlamaModule(
                    ModelUtils.TEXT_MODEL,
                    MODEL_PATH,
                    TOKENIZER_PATH,
                    TEMPERATURE
            );

            int loadResult = mModule.load();
            if (loadResult != 0) {
                Log.e(TAG, "Failed to load model: " + loadResult);
                return false;
            }

            Log.d(TAG, "ExecutorchLLM initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ExecutorchLLM", e);
            return false;
        }
    }

    public CompletableFuture<String> generateResponse(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder fullResponse = new StringBuilder();

                LlamaCallback callback = new LlamaCallback() {
                    @Override
                    public void onResult(String token) {
                        fullResponse.append(token);
                    }

                    @Override
                    public void onStats(float tokensPerSecond) {
                        Log.d(TAG, "Generation speed: " + tokensPerSecond + " tokens/sec");
                    }
                };

                mModule.generate(prompt, MAX_SEQUENCE_LENGTH, callback, false);
                return fullResponse.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error in response generation", e);
                throw new RuntimeException("Generation failed", e);
            }
        });
    }

    public CompletableFuture<String> generateStreamingResponse(String prompt, TokenCallback callback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                StringBuilder fullResponse = new StringBuilder();

                LlamaCallback llamaCallback = new LlamaCallback() {
                    @Override
                    public void onResult(String token) {
                        fullResponse.append(token);
                        callback.onToken(token);
                    }

                    @Override
                    public void onStats(float tokensPerSecond) {
                        callback.onStats(tokensPerSecond);
                    }
                };

                mModule.generate(prompt, MAX_SEQUENCE_LENGTH, llamaCallback, false);
                return fullResponse.toString();
            } catch (Exception e) {
                Log.e(TAG, "Error in streaming response", e);
                throw new RuntimeException("Streaming generation failed", e);
            }
        });
    }

    public void cleanup() {
        if (mModule != null) {
            try {
                mModule.resetNative();
                mModule = null;
            } catch (Exception e) {
                Log.e(TAG, "Error during cleanup", e);
            }
        }
    }

    private static class ModelUtils {
        static final int TEXT_MODEL = 1;
        static final int VISION_MODEL = 2;
    }
}