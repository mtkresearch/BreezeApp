package com.mtkresearch.breezeapp.utils;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ModelFilter {
    private static final String TAG = "ModelFilter";

    /**
     * Filters models based on hardware compatibility
     * @param context Application context
     * @return JSONObject containing the filtered model list
     */
    public static JSONObject getFilteredModelList(Context context) {
        try {
            // Read the full model list from assets
            String fullModelList = readAssetFile(context, "fullModelList.json");
            JSONObject fullModelJson = new JSONObject(fullModelList);
            JSONArray models = fullModelJson.getJSONArray("models");
            
            // Create filtered JSON with the same structure
            JSONObject filteredJson = new JSONObject();
            JSONArray filteredModels = new JSONArray();
            
            // Check hardware compatibility
            String hwSupport = HWCompatibility.isSupportedHW();
            boolean isMtkSupported = "mtk".equals(hwSupport);
            
            Log.d(TAG, "Hardware support: " + hwSupport + ", MTK supported: " + isMtkSupported);
            
            // Filter models based on hardware compatibility
            for (int i = 0; i < models.length(); i++) {
                JSONObject model = models.getJSONObject(i);
                String runner = model.getString("runner");
                
                // Always include CPU models (executorch)
                // For MTK models (mediatek), only include if hardware supports it
                if ("executorch".equals(runner) || (isMtkSupported && "mediatek".equals(runner))) {
                    filteredModels.put(model);
                    Log.d(TAG, "Including model: " + model.getString("id"));
                }
            }
            
            filteredJson.put("models", filteredModels);
            return filteredJson;
            
        } catch (Exception e) {
            Log.e(TAG, "Error filtering models", e);
            return null;
        }
    }
    
    /**
     * Writes the filtered model list to a file in the app's files directory
     * @param context Application context
     * @param fileName Name of the file to write to
     * @return True if the write was successful, false otherwise
     */
    public static boolean writeFilteredModelListToFile(Context context, String fileName) {
        try {
            // Get the filtered model list
            JSONObject filteredJson = getFilteredModelList(context);
            if (filteredJson == null) {
                Log.e(TAG, "Failed to get filtered model list");
                return false;
            }
            
            // Convert to string
            String jsonContent = filteredJson.toString(2); // Pretty print with indentation
            
            // Write to file
            File file = new File(context.getFilesDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
                Log.d(TAG, "Successfully wrote filtered model list to " + file.getAbsolutePath());
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error writing filtered model list to file", e);
            return false;
        }
    }
    
    /**
     * Gets a list of model IDs that are compatible with the device
     * @param context Application context
     * @return List of compatible model IDs
     */
    public static List<String> getCompatibleModelIds(Context context) {
        List<String> modelIds = new ArrayList<>();
        try {
            JSONObject filteredJson = getFilteredModelList(context);
            if (filteredJson != null) {
                JSONArray models = filteredJson.getJSONArray("models");
                for (int i = 0; i < models.length(); i++) {
                    modelIds.add(models.getJSONObject(i).getString("id"));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting compatible model IDs", e);
        }
        return modelIds;
    }
    
    /**
     * Reads a file from the assets folder
     * @param context Application context
     * @param fileName Name of the file in assets folder
     * @return Content of the file as a string
     */
    private static String readAssetFile(Context context, String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            AssetManager assetManager = context.getAssets();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(assetManager.open(fileName), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line).append('\n');
            }
            reader.close();
        } catch (Exception e) {
            Log.e(TAG, "Error reading asset file: " + fileName, e);
        }
        return stringBuilder.toString();
    }
} 