package com.mtkresearch.breezeapp.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModelArrayManager {
    private static final String TAG = "ModelArrayManager";
    private static final String DOWNLOADED_MODELS_FILE = "downloadedModelList.json";
    private static final String[] DEFAULT_MODEL_IDS = {};

    /**
     * Gets the list of model IDs from downloadedModelList.json
     * Falls back to default values from arrays.xml if no downloaded models are found
     */
    public static String[] getModelIds(Context context) {
        // First check SharedPreferences for the dynamic list
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> modelIdSet = prefs.getStringSet("llm_model_ids", new HashSet<>());
        
        if (!modelIdSet.isEmpty()) {
            return modelIdSet.toArray(new String[0]);
        }
        
        // If no models are found, return an empty array
        return new String[0];
    }

    private static String readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            fis.read(data);
            return new String(data, StandardCharsets.UTF_8);
        }
    }
} 