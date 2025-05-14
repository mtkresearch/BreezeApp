package com.mtkresearch.breezeapp.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class LLMInferenceParams {
    private int maxToken;
    private float temperature;
    private int topK;
    private float repetitionPenalty;
    private float frequencyPenalty;
    private float topP;

    public LLMInferenceParams(int maxToken, float temperature, int topK, float repetitionPenalty, float frequencyPenalty, float topP) {
        this.maxToken = maxToken;
        this.temperature = temperature;
        this.topK = topK;
        this.repetitionPenalty = repetitionPenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.topP = topP;
    }

    public int getMaxToken() { return maxToken; }
    public void setMaxToken(int maxToken) { this.maxToken = maxToken; }

    public float getTemperature() { return temperature; }
    public void setTemperature(float temperature) { this.temperature = temperature; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public float getRepetitionPenalty() { return repetitionPenalty; }
    public void setRepetitionPenalty(float repetitionPenalty) { this.repetitionPenalty = repetitionPenalty; }

    public float getFrequencyPenalty() { return frequencyPenalty; }
    public void setFrequencyPenalty(float frequencyPenalty) { this.frequencyPenalty = frequencyPenalty; }

    public float getTopP() { return topP; }
    public void setTopP(float topP) { this.topP = topP; }

    /**
     * 由 SharedPreferences 取得目前所有推論參數
     */
    public static LLMInferenceParams fromSharedPreferences(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        int maxToken = prefs.getInt(AppConstants.KEY_MAX_TOKEN_VALUE, AppConstants.DEFAULT_LLM_MAX_TOKEN);
        float temperature = prefs.getFloat(AppConstants.KEY_TEMPERATURE_VALUE, AppConstants.DEFAULT_LLM_TEMPERATURE);
        String topKStr = prefs.getString(AppConstants.KEY_TOP_K_VALUE, String.valueOf(AppConstants.DEFAULT_LLM_TOP_K));
        int topK;
        try {
            topK = Integer.parseInt(topKStr);
        } catch (NumberFormatException e) {
            topK = AppConstants.DEFAULT_LLM_TOP_K;
        }
        float repetitionPenalty = prefs.getFloat(AppConstants.KEY_REPETITION_PENALTY_VALUE, AppConstants.DEFAULT_LLM_REPETITION_PENALTY);
        float frequencyPenalty = prefs.getFloat(AppConstants.KEY_FREQUENCY_PENALTY_VALUE, AppConstants.DEFAULT_LLM_FREQUENCY_PENALTY);
        float topP = prefs.getFloat(AppConstants.KEY_TOP_P_VALUE, AppConstants.DEFAULT_LLM_TOP_P);
        return new LLMInferenceParams(maxToken, temperature, topK, repetitionPenalty, frequencyPenalty, topP);
    }

    @Override
    public String toString() {
        return "LLMInferenceParams{" +
                "maxToken=" + maxToken +
                ", temperature=" + temperature +
                ", topK=" + topK +
                ", repetitionPenalty=" + repetitionPenalty +
                ", frequencyPenalty=" + frequencyPenalty +
                ", topP=" + topP +
                '}';
    }
} 