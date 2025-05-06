package com.mtkresearch.breezeapp.utils;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;

import com.mtkresearch.breezeapp.R;
import com.mtkresearch.breezeapp.service.LLMEngineService;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = "SettingsFragment";
    
    // LLM preference keys
    private static final String KEY_TEMPERATURE = "temperature";
    private static final String KEY_MAX_TOKEN = "max_token";
    private static final String KEY_REPETITION_PENALTY = "repetition_penalty";
    private static final String KEY_FREQUENCY_PENALTY = "frequency_penalty";
    private static final String KEY_TOP_K = "top_k";
    private static final String KEY_TOP_P = "top_p";
    
    // Display precision for float values
    private static final int TEMPERATURE_SCALE = 100;     // Divide by 100 to get 0.00-1.00
    private static final int REPETITION_PENALTY_SCALE = 100;  // Divide by 100 to get 0.00-1.00
    private static final int FREQUENCY_PENALTY_SCALE = 100;  // Divide by 100 to get 0.00-1.00
    private static final int TOP_P_SCALE = 100;  // Divide by 100 to get 0.00-1.00
    
    // The number of steps for each preference
    private static final int TEMPERATURE_STEPS = 10;       // 0.0, 0.1, 0.2, ... 1.0
    private static final int REPETITION_PENALTY_STEPS = 10; // 0.0, 0.1, 0.2, ... 1.0
    private static final int FREQUENCY_PENALTY_STEPS = 10; // 0.0, 0.1, 0.2, ... 1.0
    private static final int TOP_P_STEPS = 10; // 0.0, 0.1, 0.2, ... 1.0
    
    // Step sizes
    private static final int TEMPERATURE_STEP_SIZE = TEMPERATURE_SCALE / TEMPERATURE_STEPS;
    private static final int REPETITION_PENALTY_STEP_SIZE = REPETITION_PENALTY_SCALE / REPETITION_PENALTY_STEPS;
    private static final int FREQUENCY_PENALTY_STEP_SIZE = FREQUENCY_PENALTY_SCALE / FREQUENCY_PENALTY_STEPS;
    private static final int TOP_P_STEP_SIZE = TOP_P_SCALE / TOP_P_STEPS;
    
    // Max token step size and range
    private static final int MAX_TOKEN_MIN = 128;
    private static final int MAX_TOKEN_MAX = 2048;
    private static final int MAX_TOKEN_STEP_SIZE = 128;
    
    // Default values as floats (actual values to use in calculations)
    private static final float DEFAULT_TEMPERATURE = 0.5f;
    private static final int DEFAULT_MAX_TOKEN = 256;
    private static final float DEFAULT_REPETITION_PENALTY = 0.2f;
    private static final float DEFAULT_FREQUENCY_PENALTY = 0.1f;
    private static final int DEFAULT_TOP_K = 1;
    private static final float DEFAULT_TOP_P = 0.9f;
    
    // Default values as integers (for the SeekBar preferences)
    private static final int DEFAULT_TEMPERATURE_INT = (int)(DEFAULT_TEMPERATURE * TEMPERATURE_SCALE);
    private static final int DEFAULT_REPETITION_PENALTY_INT = (int)(DEFAULT_REPETITION_PENALTY * REPETITION_PENALTY_SCALE);
    private static final int DEFAULT_FREQUENCY_PENALTY_INT = (int)(DEFAULT_FREQUENCY_PENALTY * FREQUENCY_PENALTY_SCALE);
    private static final int DEFAULT_TOP_P_INT = (int)(DEFAULT_TOP_P * TOP_P_SCALE);
    
    // Handler for delayed operations
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Preferences
    private SeekBarPreference temperaturePreference;
    private SeekBarPreference maxTokenPreference;
    private SeekBarPreference repetitionPenaltyPreference;
    private SeekBarPreference frequencyPenaltyPreference;
    private EditTextPreference topKPreference;
    private SeekBarPreference topPPreference;
    
    // Track when we're programmatically changing values
    private boolean isInternalUpdate = false;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Fix corrupted preferences first
        fixCorruptedPreferences();
        
        super.onCreate(savedInstanceState);
        // Apply our custom theme
        getContext().getTheme().applyStyle(R.style.PreferenceThemeOverlay_BreezeApp, true);
    }
    
    /**
     * Fix preferences that might have been saved as Float but need to be Integer
     */
    private void fixCorruptedPreferences() {
        try {
            Context context = getContext();
            if (context == null) return;
            
            SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
            boolean hasCorruption = false;
            
            // Check for corruption - try to read values as integers, if exception occurs, we need to fix
            try {
                prefs.getInt(KEY_TEMPERATURE, DEFAULT_TEMPERATURE_INT);
                prefs.getInt(KEY_MAX_TOKEN, DEFAULT_MAX_TOKEN);
                prefs.getInt(KEY_REPETITION_PENALTY, DEFAULT_REPETITION_PENALTY_INT);
                prefs.getInt(KEY_FREQUENCY_PENALTY, DEFAULT_FREQUENCY_PENALTY_INT);
                prefs.getInt(KEY_TOP_P, DEFAULT_TOP_P_INT);
            } catch (ClassCastException e) {
                hasCorruption = true;
                Log.w(TAG, "Detected corrupted preferences (wrong types)", e);
            }
            
            if (hasCorruption) {
                SharedPreferences.Editor editor = prefs.edit();
                
                // Clear all existing preferences
                editor.clear();
                
                // Set default values with correct types
                editor.putInt(KEY_TEMPERATURE, DEFAULT_TEMPERATURE_INT);
                editor.putInt(KEY_MAX_TOKEN, DEFAULT_MAX_TOKEN);
                editor.putInt(KEY_REPETITION_PENALTY, DEFAULT_REPETITION_PENALTY_INT);
                editor.putInt(KEY_FREQUENCY_PENALTY, DEFAULT_FREQUENCY_PENALTY_INT);
                editor.putString(KEY_TOP_K, String.valueOf(DEFAULT_TOP_K));
                editor.putInt(KEY_TOP_P, DEFAULT_TOP_P_INT);
                
                // Apply changes
                editor.apply();
                
                Log.d(TAG, "Fixed corrupted preferences");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fixing preferences", e);
        }
    }
    
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // 設置首選項檔案名稱
        getPreferenceManager().setSharedPreferencesName(AppConstants.PREFS_NAME);
        setPreferencesFromResource(R.xml.preferences, rootKey);
        
        // Apply current values immediately
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null) {
            // Update AppConstants with the correct values from preferences
            int tempValue = prefs.getInt(KEY_TEMPERATURE, DEFAULT_TEMPERATURE_INT);
            int repPenaltyValue = prefs.getInt(KEY_REPETITION_PENALTY, DEFAULT_REPETITION_PENALTY_INT);
            int maxTokenValue = prefs.getInt(KEY_MAX_TOKEN, DEFAULT_MAX_TOKEN);
            int freqPenaltyValue = prefs.getInt(KEY_FREQUENCY_PENALTY, DEFAULT_FREQUENCY_PENALTY_INT);
            String topKValue = prefs.getString(KEY_TOP_K, String.valueOf(DEFAULT_TOP_K));
            int topPValue = prefs.getInt(KEY_TOP_P, DEFAULT_TOP_P_INT);
            
            // 確保所有值都在正確的步進上
            tempValue = Math.round((float) tempValue / TEMPERATURE_STEP_SIZE) * TEMPERATURE_STEP_SIZE;
            repPenaltyValue = Math.round((float) repPenaltyValue / REPETITION_PENALTY_STEP_SIZE) * REPETITION_PENALTY_STEP_SIZE;
            maxTokenValue = Math.round((float) maxTokenValue / MAX_TOKEN_STEP_SIZE) * MAX_TOKEN_STEP_SIZE;
            freqPenaltyValue = Math.round((float) freqPenaltyValue / FREQUENCY_PENALTY_STEP_SIZE) * FREQUENCY_PENALTY_STEP_SIZE;
            topPValue = Math.round((float) topPValue / TOP_P_STEP_SIZE) * TOP_P_STEP_SIZE;
            
            // 確保最小值
            if (maxTokenValue < MAX_TOKEN_MIN) maxTokenValue = MAX_TOKEN_MIN;
            
            // 確保top_k為正整數
            int topKInt = DEFAULT_TOP_K;
            try {
                topKInt = Integer.parseInt(topKValue);
                if (topKInt < 1) topKInt = 1;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid top_k value: " + topKValue);
            }
            
            // 保存修正過的值
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_TEMPERATURE, tempValue);
            editor.putInt(KEY_REPETITION_PENALTY, repPenaltyValue);
            editor.putInt(KEY_MAX_TOKEN, maxTokenValue);
            editor.putInt(KEY_FREQUENCY_PENALTY, freqPenaltyValue);
            editor.putString(KEY_TOP_K, String.valueOf(topKInt));
            editor.putInt(KEY_TOP_P, topPValue);
            editor.apply();
            
            // 更新AppConstants
            AppConstants.LLM_TEMPERATURE = tempValue / (float)TEMPERATURE_SCALE;
            AppConstants.LLM_REPETITION_PENALTY = repPenaltyValue / (float)REPETITION_PENALTY_SCALE;
            AppConstants.LLM_MAX_TOKEN = maxTokenValue;
            AppConstants.LLM_FREQUENCY_PENALTY = freqPenaltyValue / (float)FREQUENCY_PENALTY_SCALE;
            AppConstants.LLM_TOP_K = topKInt;
            AppConstants.LLM_TOP_P = topPValue / (float)TOP_P_SCALE;
            
            Log.d(TAG, "Initial values - Temperature: " + AppConstants.LLM_TEMPERATURE 
                  + ", Repetition Penalty: " + AppConstants.LLM_REPETITION_PENALTY
                  + ", Max Tokens: " + AppConstants.LLM_MAX_TOKEN
                  + ", Frequency Penalty: " + AppConstants.LLM_FREQUENCY_PENALTY
                  + ", Top K: " + AppConstants.LLM_TOP_K
                  + ", Top P: " + AppConstants.LLM_TOP_P);
        }
        
        // Setup temperature preference with proper formatting
        temperaturePreference = findPreference(KEY_TEMPERATURE);
        if (temperaturePreference != null) {
            temperaturePreference.setUpdatesContinuously(true);
            temperaturePreference.setSummaryProvider(preference -> {
                float temp = temperaturePreference.getValue() / (float)TEMPERATURE_SCALE;
                return String.format("%.1f - %s", temp, 
                    temp < 0.3f ? "More focused" : 
                    temp > 0.7f ? "More creative" : "Balanced");
            });
        }
        
        // Setup max token preference
        maxTokenPreference = findPreference(KEY_MAX_TOKEN);
        if (maxTokenPreference != null) {
            maxTokenPreference.setUpdatesContinuously(true);
            maxTokenPreference.setSummaryProvider(preference -> {
                int value = maxTokenPreference.getValue();
                // 確保顯示128的倍數
                int snappedValue = (value / MAX_TOKEN_STEP_SIZE) * MAX_TOKEN_STEP_SIZE;
                if (snappedValue < MAX_TOKEN_MIN) snappedValue = MAX_TOKEN_MIN;
                return String.format("%d tokens", snappedValue);
            });
        }
        
        // Setup repetition penalty preference
        repetitionPenaltyPreference = findPreference(KEY_REPETITION_PENALTY);
        if (repetitionPenaltyPreference != null) {
            repetitionPenaltyPreference.setUpdatesContinuously(true);
            repetitionPenaltyPreference.setSummaryProvider(preference -> {
                // Convert from integer to float with two decimal places (0.00-1.00)
                float penalty = repetitionPenaltyPreference.getValue() / (float)REPETITION_PENALTY_SCALE;
                return String.format("%.1f", penalty);
            });
        }
        
        // Setup frequency penalty preference
        frequencyPenaltyPreference = findPreference(KEY_FREQUENCY_PENALTY);
        if (frequencyPenaltyPreference != null) {
            frequencyPenaltyPreference.setUpdatesContinuously(true);
            frequencyPenaltyPreference.setSummaryProvider(preference -> {
                float penalty = frequencyPenaltyPreference.getValue() / (float)FREQUENCY_PENALTY_SCALE;
                return String.format("%.1f", penalty);
            });
        }
        
        // Setup top_k preference
        topKPreference = findPreference(KEY_TOP_K);
        if (topKPreference != null) {
            topKPreference.setSummaryProvider(preference -> {
                String value = topKPreference.getText();
                try {
                    int topK = Integer.parseInt(value);
                    return String.valueOf(topK); // 直接顯示數值
                } catch (NumberFormatException e) {
                    return String.valueOf(DEFAULT_TOP_K);
                }
            });
            
            // 設置驗證器，確保輸入的是正整數
            topKPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                try {
                    int topK = Integer.parseInt((String) newValue);
                    if (topK < 1) {
                        // 如果小於1，設為1
                        topKPreference.setText("1");
                        return false;
                    }
                    // 更新AppConstants
                    AppConstants.LLM_TOP_K = topK;
                    Log.d(TAG, "Top K updated to: " + topK);
                    return true;
                } catch (NumberFormatException e) {
                    // 如果輸入的不是數字，恢復默認值
                    topKPreference.setText(String.valueOf(DEFAULT_TOP_K));
                    return false;
                }
            });
        }
        
        // Setup top_p preference
        topPPreference = findPreference(KEY_TOP_P);
        if (topPPreference != null) {
            topPPreference.setUpdatesContinuously(true);
            topPPreference.setSummaryProvider(preference -> {
                float topP = topPPreference.getValue() / (float)TOP_P_SCALE;
                return String.format("%.1f", topP);
            });
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);
        
        // 所有滑桿都使用snapToStep確保步進值正確
        snapToStep(prefs, KEY_TEMPERATURE, TEMPERATURE_STEP_SIZE);
        snapToStep(prefs, KEY_REPETITION_PENALTY, REPETITION_PENALTY_STEP_SIZE);
        snapToStep(prefs, KEY_MAX_TOKEN, MAX_TOKEN_STEP_SIZE);
        snapToStep(prefs, KEY_FREQUENCY_PENALTY, FREQUENCY_PENALTY_STEP_SIZE);
        snapToStep(prefs, KEY_TOP_P, TOP_P_STEP_SIZE);
        
        // 確保top_k是有效的整數
        String topKValue = prefs.getString(KEY_TOP_K, String.valueOf(DEFAULT_TOP_K));
        try {
            int topK = Integer.parseInt(topKValue);
            if (topK < 1) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_TOP_K, "1");
                editor.apply();
                
                // 更新UI
                if (topKPreference != null) {
                    topKPreference.setText("1");
                }
                
                // 更新AppConstants
                AppConstants.LLM_TOP_K = 1;
            }
        } catch (NumberFormatException e) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_TOP_K, String.valueOf(DEFAULT_TOP_K));
            editor.apply();
            
            // 更新UI
            if (topKPreference != null) {
                topKPreference.setText(String.valueOf(DEFAULT_TOP_K));
            }
            
            // 更新AppConstants
            AppConstants.LLM_TOP_K = DEFAULT_TOP_K;
        }
    }
    
    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }
    
    /**
     * Snap a preference value to the nearest step
     *
     * @param prefs The SharedPreferences
     * @param key The preference key
     * @param stepSize The step size
     */
    private void snapToStep(SharedPreferences prefs, String key, int stepSize) {
        try {
            int value = prefs.getInt(key, 0);
            int snappedValue;
            
            // 對於max_token，需要確保最小值為128
            if (key.equals(KEY_MAX_TOKEN)) {
                snappedValue = (value / stepSize) * stepSize; // 向下取整以保持128的倍數
                if (snappedValue < MAX_TOKEN_MIN) snappedValue = MAX_TOKEN_MIN;
            } else {
                snappedValue = Math.round((float) value / stepSize) * stepSize;
            }
            
            if (value != snappedValue) {
                isInternalUpdate = true;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(key, snappedValue);
                editor.apply();
                isInternalUpdate = false;
                
                // Update UI if we have the preference
                SeekBarPreference pref = findPreference(key);
                if (pref != null) {
                    pref.setValue(snappedValue);
                }
                
                // Update corresponding AppConstants values based on key
                if (key.equals(KEY_TEMPERATURE)) {
                    float snappedTemp = snappedValue / (float)TEMPERATURE_SCALE;
                    AppConstants.LLM_TEMPERATURE = snappedTemp;
                    Log.d(TAG, "Snapped temperature from " + value + " to " + snappedValue + 
                          " (float value: " + snappedTemp + ")");
                } else if (key.equals(KEY_REPETITION_PENALTY)) {
                    float snappedPenalty = snappedValue / (float)REPETITION_PENALTY_SCALE;
                    AppConstants.LLM_REPETITION_PENALTY = snappedPenalty;
                    Log.d(TAG, "Snapped repetition penalty from " + value + " to " + snappedValue + 
                          " (float value: " + snappedPenalty + ")");
                } else if (key.equals(KEY_MAX_TOKEN)) {
                    AppConstants.LLM_MAX_TOKEN = snappedValue;
                    Log.d(TAG, "Snapped max token from " + value + " to " + snappedValue);
                } else if (key.equals(KEY_FREQUENCY_PENALTY)) {
                    float snappedPenalty = snappedValue / (float)FREQUENCY_PENALTY_SCALE;
                    AppConstants.LLM_FREQUENCY_PENALTY = snappedPenalty;
                    Log.d(TAG, "Snapped frequency penalty from " + value + " to " + snappedValue + 
                          " (float value: " + snappedPenalty + ")");
                } else if (key.equals(KEY_TOP_P)) {
                    float snappedTopP = snappedValue / (float)TOP_P_SCALE;
                    AppConstants.LLM_TOP_P = snappedTopP;
                    Log.d(TAG, "Snapped top p from " + value + " to " + snappedValue + 
                          " (float value: " + snappedTopP + ")");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error snapping to step", e);
        }
    }
    
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Skip if this is an internal update
        if (isInternalUpdate) {
            return;
        }
        
        // Handle preference changes
        switch (key) {
            case KEY_TEMPERATURE:
                // Schedule a snap to step immediately
                handler.post(() -> {
                    snapToStep(sharedPreferences, KEY_TEMPERATURE, TEMPERATURE_STEP_SIZE);
                });
                break;
                
            case KEY_MAX_TOKEN:
                // 與其他slider一樣，統一使用snapToStep處理
                handler.post(() -> {
                    snapToStep(sharedPreferences, KEY_MAX_TOKEN, MAX_TOKEN_STEP_SIZE);
                });
                break;
                
            case KEY_REPETITION_PENALTY:
                // Schedule a snap to step immediately
                handler.post(() -> {
                    snapToStep(sharedPreferences, KEY_REPETITION_PENALTY, REPETITION_PENALTY_STEP_SIZE);
                });
                break;
                
            case KEY_FREQUENCY_PENALTY:
                // Schedule a snap to step immediately
                handler.post(() -> {
                    snapToStep(sharedPreferences, KEY_FREQUENCY_PENALTY, FREQUENCY_PENALTY_STEP_SIZE);
                });
                break;
                
            case KEY_TOP_K:
                // 處理文本輸入 - top_k
                String topKValue = sharedPreferences.getString(KEY_TOP_K, String.valueOf(DEFAULT_TOP_K));
                try {
                    int topK = Integer.parseInt(topKValue);
                    if (topK < 1) topK = 1;
                    AppConstants.LLM_TOP_K = topK;
                    Log.d(TAG, "Top K updated to: " + topK);
                } catch (NumberFormatException e) {
                    // 輸入值無效，恢復默認值
                    isInternalUpdate = true;
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(KEY_TOP_K, String.valueOf(DEFAULT_TOP_K));
                    editor.apply();
                    isInternalUpdate = false;
                    
                    if (topKPreference != null) {
                        topKPreference.setText(String.valueOf(DEFAULT_TOP_K));
                    }
                    
                    AppConstants.LLM_TOP_K = DEFAULT_TOP_K;
                    Log.e(TAG, "Invalid top_k value: " + topKValue + ", reset to default: " + DEFAULT_TOP_K);
                }
                break;
                
            case KEY_TOP_P:
                // Schedule a snap to step immediately
                handler.post(() -> {
                    snapToStep(sharedPreferences, KEY_TOP_P, TOP_P_STEP_SIZE);
                });
                break;
        }
    }
}