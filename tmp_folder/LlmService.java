package com.mtkresearch.gai_android.service;

import android.content.Context;
import android.util.Log;

import com.example.llmapp.data.model.InferenceResult;
import com.example.llmapp.data.model.ServiceProvider;

import java.util.Map;

public class LlmService {
    private static LlmService instance;

    private final Context context;
    private LlmProvider provider;
    private ServiceProvider activeProvider;
    private volatile boolean isCancelled = false;
    private static final int MAX_RESPONSE_TOKENS = 128; // TODO: You might want to make this configurable

    public LlmService(Context context) {
        this.context = context;
        this.activeProvider = initializeLlm();
    }

    public static LlmService getInstance(Context context) {
        if (instance == null) {
            instance = new LlmService(context);
        }
        return instance;
    }

    public static void resetInstance() {
        if (instance != null) {
            instance.releaseLlm();
        }
        instance = null;
    }

    public interface LlmProvider {
        boolean initialize();
        InferenceResult generateResponse(String input) throws Exception;
        void generateStreamingResponse(String input, TokenCallback tokenCallback, InferenceCallback inferenceCallback);
        void cancelGeneration();
        void release();
        void updateSettings(Map<String, Object> settings);
    }

    private LlmProvider createProvider(ServiceProvider type) {
        switch (type) {
            case MTK:
                return new MtkLlmProvider();
            case THIRD_PARTY:
                return new ThirdPartyLlmProvider();
            case API:
                return new ApiLlmProvider();
            case MOCK:
                return new MockLlmProvider();
            default:
                throw new IllegalArgumentException("Unsupported service provider: " + type);
        }
    }

    public ServiceProvider initializeLlm() {
        ServiceProvider[] providerTypes = {ServiceProvider.MTK, ServiceProvider.THIRD_PARTY, ServiceProvider.API, ServiceProvider.MOCK};
        for (ServiceProvider type : providerTypes) {
            try {
                provider = createProvider(type);
                if (provider.initialize()) {
                    return type;
                }
            } catch (Exception e) {
                Log.e("LlmService", type + " initialization failed: " + e.getMessage());
            }
        }
        return null;
    }

    public InferenceResult generateResponse(String input) throws Exception {
        if (provider == null) {
            throw new IllegalStateException("LLM is not initialized");
        }
        return provider.generateResponse(input);
    }

    public void generateStreamingResponse(String input, TokenCallback tokenCallback, InferenceCallback inferenceCallback) {
        if (provider == null) {
            inferenceCallback.onError("LLM is not initialized");
            return;
        }
        provider.generateStreamingResponse(input, tokenCallback, inferenceCallback);
    }

    public interface TokenCallback {
        void onToken(String token);
    }

    public interface InferenceCallback {
        void onComplete(InferenceResult result);
        void onError(String errorMessage);
    }

    public void cancelGeneration() {
        isCancelled = true;
        if (provider != null) {
            provider.cancelGeneration();
        }
    }

    public void releaseLlm() {
        if (provider != null) {
            provider.release();
            provider = null;
        }
        activeProvider = null;
    }

    public void updateSettings(Map<String, Object> settings) {
        if (provider != null) {
            provider.updateSettings(settings);
        } else {
            throw new IllegalStateException("LLM is not initialized");
        }
    }

    public boolean isInitialized() {
        return provider != null;
    }

    public ServiceProvider getActiveProvider() {
        return activeProvider;
    }

    // Implement provider classes
    private class MtkLlmProvider implements LlmProvider {
        private final LlmNative llmNative = new LlmNative();

        @Override
        public boolean initialize() {
            String configPath = "/data/local/tmp/llm_sdk/config_breezetiny_3b_instruct.yaml";  // TODO
            boolean preloadSharedWeights = false; // TODO: find a better place to save these config
            return llmNative.nativeInitLlm(configPath, preloadSharedWeights);
        }

        @Override
        public InferenceResult generateResponse(String input) throws Exception {
            InferenceResult response = llmNative.nativeInference(input, MAX_RESPONSE_TOKENS, false);
            llmNative.nativeResetLlm();
            llmNative.nativeSwapModel(128);
            return response;
        }

        @Override
        public void generateStreamingResponse(String input, TokenCallback tokenCallback, InferenceCallback inferenceCallback) {
            new Thread(() -> {
                try {
                    InferenceResult result = llmNative.nativeStreamingInference(input, MAX_RESPONSE_TOKENS, false, new LlmNative.TokenCallback() {
                        @Override
                        public void onToken(String token) {
                            tokenCallback.onToken(token);
                        }
                    });

                    // Reset and swap model after inference
                    llmNative.nativeResetLlm();
                    llmNative.nativeSwapModel(128);

                    inferenceCallback.onComplete(result);
                } catch (Exception e) {
                    inferenceCallback.onError(e.getMessage());
                }
            }).start();
        }

        @Override
        public void cancelGeneration() {
        }

        @Override
        public void release() {
            llmNative.nativeReleaseLlm();
        }

        @Override
        public void updateSettings(Map<String, Object> settings) {
        }
    }

    private class ThirdPartyLlmProvider implements LlmProvider {
        @Override
        public boolean initialize() {
            // Implement third-party initialization
            throw new UnsupportedOperationException("Third-party LLM not yet implemented");
        }

        @Override
        public InferenceResult generateResponse(String input) throws Exception {
            // Implement third-party response generation
            throw new UnsupportedOperationException("Third-party LLM not yet implemented");
        }

        @Override
        public void generateStreamingResponse(String input, TokenCallback tokenCallback, InferenceCallback inferenceCallback) {
            // Implement third-party streaming response generation
            throw new UnsupportedOperationException("Third-party LLM streaming not yet implemented");
        }

        @Override
        public void cancelGeneration() {
        }

        @Override
        public void release() {
        }

        @Override
        public void updateSettings(Map<String, Object> settings) {
        }
    }

    private class ApiLlmProvider implements LlmProvider {
        @Override
        public boolean initialize() {
            // Implement API initialization
            throw new UnsupportedOperationException("API LLM not yet implemented");
        }

        @Override
        public InferenceResult generateResponse(String input) throws Exception {
            // Implement API response generation
            throw new UnsupportedOperationException("API LLM not yet implemented");
        }

        @Override
        public void generateStreamingResponse(String input, TokenCallback tokenCallback, InferenceCallback inferenceCallback) {
            // Implement API streaming response generation
            throw new UnsupportedOperationException("API LLM streaming not yet implemented");
        }

        @Override
        public void cancelGeneration() {
        }

        @Override
        public void release() {
        }

        @Override
        public void updateSettings(Map<String, Object> settings) {
        }
    }

    // Add MockLlmProvider class
    private class MockLlmProvider implements LlmProvider {
        private final String MOCK_RESPONSE = "This is a mock response from the LLM service.";

        @Override
        public boolean initialize() {
            // Mock initialization always succeeds
            return true;
        }

        @Override
        public InferenceResult generateResponse(String input) {
            // Create a mock InferenceResult with the constant response
            return new InferenceResult(MOCK_RESPONSE, 0.0);
        }

        @Override
        public void generateStreamingResponse(String input, TokenCallback tokenCallback, InferenceCallback inferenceCallback) {
            new Thread(() -> {
                try {
                    // Simulate streaming by sending the mock response word by word
                    String[] words = MOCK_RESPONSE.split(" ");
                    for (String word : words) {
                        if (isCancelled) break;
                        tokenCallback.onToken(word + " ");
                        Thread.sleep(200); // Simulate delay between tokens
                    }
                    inferenceCallback.onComplete(new InferenceResult(MOCK_RESPONSE, 0.0));
                } catch (InterruptedException e) {
                    inferenceCallback.onError("Mock streaming interrupted");
                }
            }).start();
        }

        @Override
        public void cancelGeneration() {
            // No-op for mock provider
        }

        @Override
        public void release() {
            // No-op for mock provider
        }

        @Override
        public void updateSettings(Map<String, Object> settings) {
            // No-op for mock provider
        }
    }
}