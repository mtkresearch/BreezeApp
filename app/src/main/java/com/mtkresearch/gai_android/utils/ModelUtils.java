package com.mtkresearch.gai_android.utils;

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
        String modelName = getModelNameFromPath(modelPath);
        String backendType = getBackendDisplayName(backend);
        return String.format("%s (%s)", modelName, backendType);
    }

    /**
     * Converts backend identifier to display name.
     * @param backend Backend identifier string
     * @return User-friendly backend display name
     */
    public static String getBackendDisplayName(String backend) {
        if (backend == null) {
            return "Unknown";
        }
        switch (backend.toLowerCase()) {
            case "mtk":
                return "NPU";
            case "localcpu":
                return "CPU";
            case "none":
                return "None";
            default:
                return backend.toUpperCase();
        }
    }
} 