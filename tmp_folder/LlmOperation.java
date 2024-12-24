// LlmOperation.java
package com.mtkresearch.gai_android.service;

import android.os.Handler;
import android.os.Looper;

import com.example.llmapp.data.model.InferenceResult;
import com.example.llmapp.model.ModelManager;
import com.example.llmapp.model.ModelOperation;
import com.example.llmapp.service.LlmService;

public class LlmOperation implements ModelOperation {
    private final String input;
    private final ModelManager modelManager;
    private boolean isCancelled = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public LlmOperation(String input, ModelManager modelManager) {
        this.input = input;
        this.modelManager = modelManager;
    }

    @Override
    public void execute(ModelCallback callback) {
        if (modelManager.getLlmService().isInitialized()) {
            try {
                System.out.println("LLM is initialized. Generating streaming response for input: " + input);
                StringBuilder responseBuilder = new StringBuilder();

                modelManager.getLlmService().generateStreamingResponse(
                        input,
                        new LlmService.TokenCallback() {
                            @Override
                            public void onToken(String token) {
                                if (!isCancelled) {
                                    responseBuilder.append(token);
                                    mainHandler.post(() -> callback.onPartialResult(token));
                                }
                            }
                        },
                        new LlmService.InferenceCallback() {
                            @Override
                            public void onComplete(InferenceResult result) {
                                if (!isCancelled) {
                                    String formattedResponse = formatResponse(result, responseBuilder.toString());
                                    System.out.println("LLM response generated successfully: " + formattedResponse);
                                    mainHandler.post(() -> callback.onSuccess(formattedResponse));
                                }
                            }

                            @Override
                            public void onError(String errorMessage) {
                                if (!isCancelled) {
                                    System.err.println("Error generating LLM response: " + errorMessage);
                                    mainHandler.post(() -> callback.onError("Error generating response: " + errorMessage));
                                }
                            }
                        }
                );
            } catch (Exception e) {
                if (!isCancelled) {
                    System.err.println("Error generating LLM response: " + e.getMessage());
                    e.printStackTrace();
                    mainHandler.post(() -> callback.onError("Error generating response: " + e.getMessage()));
                }
            }
        } else {
            System.out.println("LLM is not initialized. Returning constant response.");
            String constantResponse = "I'm sorry, but the language model is not initialized at the moment. " +
                    "I can't provide a detailed response to your input: \"" + input + "\". " +
                    "Please try again later when the model is ready.";
            mainHandler.post(() -> callback.onSuccess(constantResponse));
        }
    }

    private String formatResponse(InferenceResult result, String fullResponse) {
        StringBuilder formattedResponse = new StringBuilder(fullResponse);
        formattedResponse.append("\n\n--- Performance Metrics ---");
        formattedResponse.append(String.format("\nPrompt Tokens/Second: %.2f", result.getPromptTokensPerSecond()));
        formattedResponse.append(String.format("\nGenerative Tokens/Second: %.2f", result.getGenerativeTokensPerSecond()));

        if (result.hasGeneratedTokens()) {
            formattedResponse.append(String.format("\nTotal Generated Tokens: %d", result.getGeneratedTokens().length));
        }

        return formattedResponse.toString();
    }

    @Override
    public void cancel() {
        isCancelled = true;
        modelManager.getLlmService().cancelGeneration();
    }
}