package com.mtkresearch.breeze_app.utils;

import android.util.Log;

/**
 * Utility class for handling model-related operations.
 */
public class ModelUtils {
    private static final String TAG = "ModelUtils";

    /**
     * Extracts a clean model name from a model path.
     * @param modelPath Full path to the model file
     * @return Clean model name without path and extension, or "Unknown" if path is invalid
     */
    public static String getModelNameFromPath(String modelPath) {
        if (modelPath == null) {
            return "Unknown";
        }
        try {
            // Extract filename from path
            String[] parts = modelPath.split("/");
            String filename = parts.length > 0 ? parts[parts.length - 1] : "Unknown";
            
            // Remove file extension
            int lastDotIndex = filename.lastIndexOf('.');
            if (lastDotIndex > 0) {
                filename = filename.substring(0, lastDotIndex);
            }
            
            return filename;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting model name from path: " + modelPath, e);
            return "Unknown";
        }
    }

    /**
     * Gets a formatted display string including model name and backend type.
     * @param modelPath Full path to the model file
     * @param backend Backend type string
     * @return Formatted string with model name and backend type
     */
    public static String getModelDisplayString(String modelPath, String backend) {
        return getModelNameFromPath(modelPath);
    }

    /**
     * Gets the model type based on the model file name.
     * @param modelPath Full path to the model file
     * @return ModelType enum value
     */
    public static com.executorch.ModelType getModelType(String modelPath) {
        String modelName = getModelNameFromPath(modelPath).toLowerCase();
        // Both Breeze and Llama models will use LLAMA_3_2 since BREEZE_2 is not available
        if (modelName.contains("llama") || modelName.contains("breeze")) {
            return com.executorch.ModelType.LLAMA_3_2;
        }
        return com.executorch.ModelType.LLAMA_3_2; // Default to LLAMA_3_2
    }

    /**
     * Gets a user-friendly display name for the model.
     * @param modelPath Full path to the model file or config file
     * @return User-friendly model name
     */
    public static String getModelDisplayName(String modelPath) {
        if (modelPath == null) return "Unknown";
        
        String lowerPath = modelPath.toLowerCase();
        // Check for breeze in either model file name or config file path
        if (lowerPath.contains("breeze")) {
            return "Breeze2";
        } else if (lowerPath.contains("llama")) {
            return "llama3_2";
        }
        return getModelNameFromPath(modelPath);
    }

    /**
     * Converts backend identifier to display name.
     * @param backend Backend identifier string
     * @return User-friendly backend display name
     */
    public static String getBackendDisplayName(String backend) {
        if (backend == null) return "Unknown";
        switch (backend.toLowerCase()) {
            case "cpu":
            case "localcpu":
            case "local_cpu":
                return "CPU";
            default:
                return backend.toUpperCase();
        }
    }

    /**
     * Determines the preferred backend.
     * @return The preferred backend identifier (always "cpu")
     */
    public static String getPreferredBackend() {
        // Always return CPU backend
        return "cpu";
    }
} 