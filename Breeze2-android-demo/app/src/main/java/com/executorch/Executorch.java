package com.executorch;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import org.pytorch.executorch.LlamaModule;
import org.pytorch.executorch.LlamaCallback;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.File;

public class Executorch implements ModelRunnerCallback {
    private static final String TAG = "Executorch";
    private LlamaModule mModule;
    private final Context context;
    private final SettingsFields settings;
    private final ExecutorCallback callback;
    private long startPos = 0;
    private final ExecutorService executor;

    public interface ExecutorCallback {
        void onInitialized(boolean success);
        void onGenerating(String token);
        void onGenerationComplete();
        void onError(String error);
        void onMetrics(float tokensPerSecond, long totalTime);
    }

    public Executorch(Context context, SettingsFields settings, ExecutorCallback callback) {
        this.context = context;
        this.settings = settings;
        this.callback = callback;
        this.executor = Executors.newSingleThreadExecutor();
    }

    private String getConversationHistory() {
        String conversationHistory = "";

        // TODO: Organize the conversation history

        return conversationHistory;
    }

    private String getTotalFormattedPrompt(String conversationHistory, String rawPrompt) {
        if (conversationHistory.isEmpty()) {
            return settings.getFormattedSystemAndUserPrompt(rawPrompt);
        }

        return settings.getFormattedSystemPrompt()
                + conversationHistory
                + settings.getFormattedUserPrompt(rawPrompt);
    }

    public void initialize() {
        executor.execute(() -> {
            try {
                if (mModule != null) {
                    mModule.resetNative();
                    mModule = null;
                }

                // Check if running on emulator first
                boolean isEmulator = android.os.Build.PRODUCT.contains("sdk") ||
                                   android.os.Build.MODEL.contains("Emulator");

                if (isEmulator) {
                    // Use emulator-specific settings
                    System.setProperty("PYTORCH_DISABLE_SVE", "1");  // Disable SVE instructions
                    System.setProperty("PYTORCH_DISABLE_HARDWARE_OPTIMIZATIONS", "1");

                    // Force CPU delegate for emulator
                    System.setProperty("EXECUTORCH_DELEGATE", "cpu");
                    Log.d(TAG, "Running on emulator - using CPU delegate with optimizations disabled");
                }

                int modelCategory = ModelUtils.getModelCategory(settings.getModelType());
                String modelPath = settings.getModelFilePath();
                String tokenizerPath = settings.getTokenizerFilePath();
                float temperature = (float) settings.getTemperature();

                // Add extra logging for debugging
                Log.d(TAG, String.format("Initializing LlamaModule - Category: %d, Temperature: %.2f",
                    modelCategory, temperature));

                try {
                    // For emulator, we'll use a different initialization approach
                    if (isEmulator) {
                        mModule = new LlamaModule(
                            modelPath,
                            tokenizerPath,
                            temperature  // Use direct temperature initialization for emulator
                        );
                    } else {
                        mModule = new LlamaModule(
                            modelCategory,
                            modelPath,
                            tokenizerPath,
                            temperature
                        );
                    }
                    callback.onInitialized(true);
                } catch (Exception e) {
                    String errorMsg = String.format("LlamaModule init failed - Emulator: %b, Error: %s",
                        isEmulator, e.getMessage());
                    Log.e(TAG, errorMsg, e);
                    callback.onError(errorMsg);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error initializing model", e);
                callback.onError("Error initializing model: " + e.getMessage());
            }
        });
    }

    public void generate(String rawPrompt) {
        if (mModule == null) {
            callback.onError("Model not initialized");
            return;
        }

        String finalPrompt;
        if (ModelUtils.getModelCategory(settings.getModelType()) == ModelUtils.VISION_MODEL) {
            finalPrompt = settings.getFormattedSystemAndUserPrompt(rawPrompt);
        } else if (settings.getModelType() == ModelType.LLAMA_GUARD_3) {
            finalPrompt = PromptFormat.getFormattedLlamaGuardPrompt(rawPrompt);
            Log.d(TAG, "Running LlamaGuard inference with prompt: " + finalPrompt);
        } else {
            finalPrompt = getTotalFormattedPrompt(getConversationHistory(), rawPrompt);
        }

        executor.execute(() -> {
            try {
                long generateStartTime = System.currentTimeMillis();

                if (ModelUtils.getModelCategory(settings.getModelType()) == ModelUtils.VISION_MODEL) {
                    mModule.generate(finalPrompt, ModelUtils.VISION_MODEL_SEQ_LEN, new LlamaCallback() {
                        @Override
                        public void onResult(String token) {
                            callback.onGenerating(token);
                        }

                        @Override
                        public void onStats(float tps) {
                            callback.onMetrics(tps, System.currentTimeMillis() - generateStartTime);
                        }
                    });
                } else if (settings.getModelType() == ModelType.LLAMA_GUARD_3) {
                    // For LlamaGuard, use length + fixed token count
                    int maxTokens = finalPrompt.length() + 64;
                    mModule.generate(finalPrompt, maxTokens, new LlamaCallback() {
                        @Override
                        public void onResult(String token) {
                            callback.onGenerating(token);
                        }

                        @Override
                        public void onStats(float tps) {
                            callback.onMetrics(tps, System.currentTimeMillis() - generateStartTime);
                        }
                    });
                } else {
                    // For text models, use proportional length + fixed token count
                    int maxTokens = (int)(finalPrompt.length() * 0.75) + 64;
                    mModule.generate(finalPrompt, maxTokens, new LlamaCallback() {
                        @Override
                        public void onResult(String token) {
                            callback.onGenerating(token);
                        }

                        @Override
                        public void onStats(float tps) {
                            callback.onMetrics(tps, System.currentTimeMillis() - generateStartTime);
                        }
                    });
                }

                callback.onGenerationComplete();
            } catch (Exception e) {
                Log.e(TAG, "Error generating response", e);
                callback.onError("Error generating response: " + e.getMessage());
            }
        });
    }

    public void processImage(Uri imageUri) {
        if (settings.getModelType() != ModelType.LLAVA_1_5) {
            callback.onError("Current model does not support image processing");
            return;
        }

        executor.execute(() -> {
            try {
                ETImage image = new ETImage(context.getContentResolver(), imageUri);
                startPos = mModule.prefillImages(
                        image.getInts(),
                        image.getWidth(),
                        image.getHeight(),
                        ModelUtils.VISION_MODEL_IMAGE_CHANNELS,
                        startPos
                );
            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
                callback.onError("Error processing image: " + e.getMessage());
            }
        });
    }

    public void stop() {
        if (mModule != null) {
            mModule.stop();
        }
    }

    public void release() {
        if (mModule != null) {
            mModule.resetNative();
            mModule = null;
        }
    }

    // ModelRunnerCallback implementation
    @Override
    public void onModelLoaded(int status) {
        callback.onInitialized(status == 0);
    }

    @Override
    public void onTokenGenerated(String token) {
        callback.onGenerating(token);
    }

    @Override
    public void onStats(String stats) {
        // Parse tokens per second from stats string
        try {
            float tps = Float.parseFloat(stats.split(":")[1].trim());
            callback.onMetrics(tps, System.currentTimeMillis());
        } catch (Exception e) {
            Log.e(TAG, "Error parsing stats", e);
        }
    }

    @Override
    public void onGenerationStopped() {
        callback.onGenerationComplete();
    }
}