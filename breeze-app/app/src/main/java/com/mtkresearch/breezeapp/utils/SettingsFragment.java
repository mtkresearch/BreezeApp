package com.mtkresearch.breezeapp.utils;

import static com.mtkresearch.breezeapp.utils.AppConstants.DEFAULT_LLM_FREQUENCY_PENALTY;
import static com.mtkresearch.breezeapp.utils.AppConstants.DEFAULT_LLM_MAX_TOKEN;
import static com.mtkresearch.breezeapp.utils.AppConstants.DEFAULT_LLM_REPETITION_PENALTY;
import static com.mtkresearch.breezeapp.utils.AppConstants.DEFAULT_LLM_TEMPERATURE;
import static com.mtkresearch.breezeapp.utils.AppConstants.DEFAULT_LLM_TOP_K;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_FREQUENCY_PENALTY;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_FREQUENCY_PENALTY_VALUE;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_MAX_TOKEN_VALUE;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_REPETITION_PENALTY;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_REPETITION_PENALTY_VALUE;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_TEMPERATURE;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_TEMPERATURE_VALUE;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_TOP_K_VALUE;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_TOP_P;
import static com.mtkresearch.breezeapp.utils.AppConstants.KEY_TOP_P_VALUE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

import com.mtkresearch.breezeapp.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.HashMap;
import android.app.ActivityManager;
import java.util.Comparator;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    
    private static final String TAG = "SettingsFragment";
    
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


    // Default values as integers (for the SeekBar preferences)
    private static final int DEFAULT_TEMPERATURE_INT = (int)(DEFAULT_LLM_TEMPERATURE * TEMPERATURE_SCALE);
    private static final int DEFAULT_REPETITION_PENALTY_INT = (int)(DEFAULT_LLM_REPETITION_PENALTY * REPETITION_PENALTY_SCALE);
    private static final int DEFAULT_FREQUENCY_PENALTY_INT = (int)(DEFAULT_LLM_FREQUENCY_PENALTY * FREQUENCY_PENALTY_SCALE);
    private static final int DEFAULT_TOP_P_INT = (int)(DEFAULT_LLM_TOP_K * TOP_P_SCALE);
    
    // Handler for delayed operations
    private final Handler handler = new Handler(Looper.getMainLooper());
    
    // Preferences
    private SeekBarPreference temperaturePreference;
    private SeekBarPreference maxTokenPreference;
    private SeekBarPreference repetitionPenaltyPreference;
    private SeekBarPreference frequencyPenaltyPreference;
    private EditTextPreference topKPreference;
    private SeekBarPreference topPPreference;
    private ListPreference modelIdPreference;
    
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
                prefs.getInt(KEY_MAX_TOKEN_VALUE, DEFAULT_LLM_MAX_TOKEN);
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
                editor.putInt(KEY_MAX_TOKEN_VALUE, DEFAULT_LLM_MAX_TOKEN);
                editor.putInt(KEY_REPETITION_PENALTY, DEFAULT_REPETITION_PENALTY_INT);
                editor.putInt(KEY_FREQUENCY_PENALTY, DEFAULT_FREQUENCY_PENALTY_INT);
                editor.putString(KEY_TOP_K_VALUE, String.valueOf(DEFAULT_LLM_TOP_K));
                editor.putInt(KEY_TOP_P, DEFAULT_TOP_P_INT);
                
                // Apply changes
                editor.apply();
                
                Log.d(TAG, "Fixed corrupted preferences");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fixing preferences", e);
        }
    }
    
    @SuppressLint("DefaultLocale")
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // 設置首選項檔案名稱
        getPreferenceManager().setSharedPreferencesName(AppConstants.PREFS_NAME);
        setPreferencesFromResource(R.xml.preferences, rootKey);
        
        // Apply current values immediately
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs != null) {
            // Get integer values from the SeekBar preferences
            int tempValue = prefs.getInt(KEY_TEMPERATURE, DEFAULT_TEMPERATURE_INT);
            int repPenaltyValue = prefs.getInt(KEY_REPETITION_PENALTY, DEFAULT_REPETITION_PENALTY_INT);
            int maxTokenValue = prefs.getInt(KEY_MAX_TOKEN_VALUE, DEFAULT_LLM_MAX_TOKEN);
            int freqPenaltyValue = prefs.getInt(KEY_FREQUENCY_PENALTY, DEFAULT_FREQUENCY_PENALTY_INT);
            String topKValue = prefs.getString(KEY_TOP_K_VALUE, String.valueOf(DEFAULT_LLM_TOP_K));
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
            int topKInt = DEFAULT_LLM_TOP_K;
            try {
                topKInt = Integer.parseInt(topKValue);
                if (topKInt < 1) topKInt = 1;
            } catch (NumberFormatException e) {
                Log.e(TAG, "Invalid top_k value: " + topKValue);
            }
            
            // Store the SeekBar integer values for the UI
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_TEMPERATURE, tempValue);
            editor.putInt(KEY_REPETITION_PENALTY, repPenaltyValue);
            editor.putInt(KEY_MAX_TOKEN_VALUE, maxTokenValue);
            editor.putInt(KEY_FREQUENCY_PENALTY, freqPenaltyValue);
            editor.putString(KEY_TOP_K_VALUE, String.valueOf(topKInt));
            editor.putInt(KEY_TOP_P, topPValue);
            
            // Also store the real values with different keys for direct access
            editor.putFloat(KEY_TEMPERATURE_VALUE, tempValue / (float)TEMPERATURE_SCALE);
            editor.putFloat(KEY_REPETITION_PENALTY_VALUE, repPenaltyValue / (float)REPETITION_PENALTY_SCALE);
            editor.putFloat(KEY_FREQUENCY_PENALTY_VALUE, freqPenaltyValue / (float)FREQUENCY_PENALTY_SCALE);
            editor.putFloat(KEY_TOP_P_VALUE, topPValue / (float)TOP_P_SCALE);
            // MAX_TOKEN and TOP_K don't need conversion
            
            editor.apply();
            
            Log.d(TAG, "Initial values - Temperature: " + tempValue / (float)TEMPERATURE_SCALE
                    + ", Repetition Penalty: " + repPenaltyValue / (float)REPETITION_PENALTY_SCALE
                    + ", Max Tokens: " + maxTokenValue
                    + ", Frequency Penalty: " + freqPenaltyValue / (float)FREQUENCY_PENALTY_SCALE
                    + ", Top K: " + topKInt
                    + ", Top P: " + topPValue / (float)TOP_P_SCALE);
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
        maxTokenPreference = findPreference(KEY_MAX_TOKEN_VALUE);
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
        topKPreference = findPreference(KEY_TOP_K_VALUE);
        if (topKPreference != null) {
            topKPreference.setSummaryProvider(preference -> {
                String value = topKPreference.getText();
                try {
                    int topK = Integer.parseInt(value);
                    return String.valueOf(topK); // 直接顯示數值
                } catch (NumberFormatException e) {
                    return String.valueOf(DEFAULT_LLM_TOP_K);
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

                    return true;
                } catch (NumberFormatException e) {
                    // 如果輸入的不是數字，恢復默認值
                    topKPreference.setText(String.valueOf(DEFAULT_LLM_TOP_K));
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
        
        modelIdPreference = findPreference("llm_model_id");
        updateModelIdList();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        prefs.registerOnSharedPreferenceChangeListener(this);
        
        // 所有滑桿都使用snapToStep確保步進值正確
        snapToStep(prefs, KEY_TEMPERATURE, TEMPERATURE_STEP_SIZE);
        snapToStep(prefs, KEY_REPETITION_PENALTY, REPETITION_PENALTY_STEP_SIZE);
        snapToStep(prefs, KEY_MAX_TOKEN_VALUE, MAX_TOKEN_STEP_SIZE);
        snapToStep(prefs, KEY_FREQUENCY_PENALTY, FREQUENCY_PENALTY_STEP_SIZE);
        snapToStep(prefs, KEY_TOP_P, TOP_P_STEP_SIZE);
        
        // 確保top_k是有效的整數
        String topKValue = prefs.getString(KEY_TOP_K_VALUE, String.valueOf(DEFAULT_LLM_TOP_K));
        try {
            int topK = Integer.parseInt(topKValue);
            if (topK < 1) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(KEY_TOP_K_VALUE, "1");
                editor.apply();
                
                // 更新UI
                if (topKPreference != null) {
                    topKPreference.setText("1");
                }
            }
        } catch (NumberFormatException e) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEY_TOP_K_VALUE, String.valueOf(DEFAULT_LLM_TOP_K));
            editor.apply();
            
            // 更新UI
            if (topKPreference != null) {
                topKPreference.setText(String.valueOf(DEFAULT_LLM_TOP_K));
            }
        }
        
        // Update the model list when returning to settings
        updateModelIdList();
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
            if (key.equals(KEY_MAX_TOKEN_VALUE)) {
                snappedValue = (value / stepSize) * stepSize; // 向下取整以保持128的倍數
                if (snappedValue < MAX_TOKEN_MIN) snappedValue = MAX_TOKEN_MIN;
            } else {
                snappedValue = Math.round((float) value / stepSize) * stepSize;
            }
            
            if (value != snappedValue) {
                isInternalUpdate = true;
                SharedPreferences.Editor editor = prefs.edit();
                editor.putInt(key, snappedValue);
                
                // Also update the direct value if applicable
                float snappedFloatValue = 0f;
                if (key.equals(KEY_TEMPERATURE)) {
                    snappedFloatValue = snappedValue / (float)TEMPERATURE_SCALE;
                    editor.putFloat(KEY_TEMPERATURE_VALUE, snappedFloatValue);
                } else if (key.equals(KEY_REPETITION_PENALTY)) {
                    snappedFloatValue = snappedValue / (float)REPETITION_PENALTY_SCALE;
                    editor.putFloat(KEY_REPETITION_PENALTY_VALUE, snappedFloatValue);
                } else if (key.equals(KEY_MAX_TOKEN_VALUE)) {
                    // No float conversion needed
                } else if (key.equals(KEY_FREQUENCY_PENALTY)) {
                    snappedFloatValue = snappedValue / (float)FREQUENCY_PENALTY_SCALE;
                    editor.putFloat(KEY_FREQUENCY_PENALTY_VALUE, snappedFloatValue);
                } else if (key.equals(KEY_TOP_P)) {
                    snappedFloatValue = snappedValue / (float)TOP_P_SCALE;
                    editor.putFloat(KEY_TOP_P_VALUE, snappedFloatValue);
                }
                
                // Log changes only in debug mode
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    if (key.equals(KEY_MAX_TOKEN_VALUE)) {
                        Log.d(TAG, "Snapped " + key + " from " + value + " to " + snappedValue);
                    } else if (key.equals(KEY_TEMPERATURE) || key.equals(KEY_REPETITION_PENALTY) || 
                              key.equals(KEY_FREQUENCY_PENALTY) || key.equals(KEY_TOP_P)) {
                        Log.d(TAG, "Snapped " + key + " from " + value + " to " + snappedValue + 
                               " (float value: " + snappedFloatValue + ")");
                    }
                }
                
                editor.apply();
                isInternalUpdate = false;
                
                // Update UI if we have the preference
                SeekBarPreference pref = findPreference(key);
                if (pref != null) {
                    pref.setValue(snappedValue);
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
            case KEY_TOP_K_VALUE:
                // 處理文本輸入 - top_k
                String topKValue = sharedPreferences.getString(KEY_TOP_K_VALUE, String.valueOf(DEFAULT_LLM_TOP_K));
                try {
                    int topK = Integer.parseInt(topKValue);
                    if (topK < 1) topK = 1;
                    // No need to store a separate direct value since it's already an integer
                    Log.d(TAG, "Top K updated to: " + topK);
                } catch (NumberFormatException e) {
                    // 輸入值無效，恢復默認值
                    isInternalUpdate = true;
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    editor.putString(KEY_TOP_K_VALUE, String.valueOf(DEFAULT_LLM_TOP_K));
                    editor.apply();
                    isInternalUpdate = false;
                    
                    if (topKPreference != null) {
                        topKPreference.setText(String.valueOf(DEFAULT_LLM_TOP_K));
                    }
                    
                    Log.e(TAG, "Invalid top_k value: " + topKValue + ", reset to default: " + DEFAULT_LLM_TOP_K);
                }
                break;
                
            // Let the snapToStep method handle the other settings which will update both values
            case KEY_TEMPERATURE:
            case KEY_MAX_TOKEN_VALUE:
            case KEY_REPETITION_PENALTY:
            case KEY_FREQUENCY_PENALTY:
            case KEY_TOP_P:
                // Schedule a snap to step immediately
                handler.post(() -> {
                    int stepSize = getStepSizeForKey(key);
                    if (stepSize > 0) {
                        snapToStep(sharedPreferences, key, stepSize);
                    }
                });
                break;
                
            case "llm_model_id":
                String newModelId = sharedPreferences.getString(key, "");
                Log.d(TAG, "Model ID changed to: " + newModelId);
                break;
        }
    }
    
    /**
     * Get the step size for a preference key
     */
    private int getStepSizeForKey(String key) {
        switch (key) {
            case KEY_TEMPERATURE:
                return TEMPERATURE_STEP_SIZE;
            case KEY_MAX_TOKEN_VALUE:
                return MAX_TOKEN_STEP_SIZE;
            case KEY_REPETITION_PENALTY:
                return REPETITION_PENALTY_STEP_SIZE;
            case KEY_FREQUENCY_PENALTY:
                return FREQUENCY_PENALTY_STEP_SIZE;
            case KEY_TOP_P:
                return TOP_P_STEP_SIZE;
            default:
                return 0; // unknown key
        }
    }
    
    private void updateModelIdList() {
        if (modelIdPreference != null && getContext() != null) {
            // Get downloaded models from downloadedModelList.json
            List<String> modelIds = new ArrayList<>();
            
            try {
                File modelsFile = new File(getContext().getFilesDir(), "downloadedModelList.json");
                if (modelsFile.exists()) {
                    JSONObject json = new JSONObject(new String(Files.readAllBytes(modelsFile.toPath())));
                    JSONArray models = json.getJSONArray("models");
                    for (int i = 0; i < models.length(); i++) {
                        JSONObject model = models.getJSONObject(i);
                        String id = model.getString("id");
                        modelIds.add(id);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading downloadedModelList.json", e);
            }

            if (!modelIds.isEmpty()) {
                // Set up preference entries with all downloaded models
                String[] modelArray = modelIds.toArray(new String[0]);
                modelIdPreference.setEntries(modelArray);
                modelIdPreference.setEntryValues(modelArray);

                // Validate current value
                String currentValue = modelIdPreference.getValue();
                if (currentValue == null || currentValue.isEmpty() || !modelIds.contains(currentValue)) {
                    // Get the first model from SharedPreferences
                    SharedPreferences prefs = getContext().getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
                    String defaultModel = prefs.getString("llm_model_id", modelIds.get(0));
                    
                    // Ensure the default model exists in our list
                    if (!modelIds.contains(defaultModel)) {
                        defaultModel = modelIds.get(0);
                    }
                    
                    Log.d(TAG, "Setting model to: " + defaultModel);
                    modelIdPreference.setValue(defaultModel);
                }
            } else {
                Log.w(TAG, "No downloaded models found");
                String[] emptyIds = {};
                modelIdPreference.setEntries(emptyIds);
                modelIdPreference.setEntryValues(emptyIds);
                modelIdPreference.setValue("");
            }
        }
    }
}