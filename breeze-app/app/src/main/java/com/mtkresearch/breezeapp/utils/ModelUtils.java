package com.mtkresearch.breezeapp.utils;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import androidx.preference.PreferenceManager;

/**
 * Utility class for handling model-related operations.
 */
public class ModelUtils {
    private static final String TAG = "ModelUtils";

    /**
     * Converts backend identifier to display name.
     * @param backend Backend identifier string
     * @return User-friendly backend display name
     */
    public static String getBackendDisplayName(String backend) {
        if (backend == null) return "Unknown";
        switch (backend.toLowerCase()) {
            case "mtk":
                return "NPU";
            case "cpu":
                return "CPU";
            default:
                return backend.toUpperCase();
        }
    }

    public static Map<String, String> getPrefModelInfo(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String modelId = prefs.getString("llm_model_id", AppConstants.DEFAULT_LLM_MODEL);
        String baseFolder = Paths.get(context.getFilesDir().getPath(), "models", modelId).toString();

        Map<String, String> modelInfo = new HashMap<>();
        modelInfo.put("baseFolder", baseFolder);
        modelInfo.put("modelEntryPath", baseFolder);
        modelInfo.put("backend", modelId.endsWith("-npu") ? "mtk" : "cpu");
        modelInfo.put("ramGB", "4");

        try {
            // Read downloadedModelList.json
            File modelsFile = new File(context.getFilesDir(), "downloadedModelList.json");
            if (!modelsFile.exists()) {
                Log.e(TAG, "downloadedModelList.json not found");
                return modelInfo;
            }

            JSONObject json = new JSONObject(new String(Files.readAllBytes(modelsFile.toPath())));
            JSONArray models = json.getJSONArray("models");

            // Find matching model
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.getJSONObject(i);
                if (model.getString("id").equals(modelId)) {
                    String modelPath = "";
                    if (model.getString("model_entry_path").isEmpty()) {
                        modelPath = baseFolder;
                    } else {
                        modelPath = Paths.get(baseFolder, model.getString("model_entry_path")).toString();
                    }
                    
                    modelInfo.put("baseFolder", baseFolder);
                    modelInfo.put("modelEntryPath", modelPath);
                    modelInfo.put("backend", model.getString("backend"));
                    modelInfo.put("ramGB", model.getString("ramGB"));
                    return modelInfo;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error reading downloadedModelList.json", e);
        }

        return modelInfo;
    }
} 