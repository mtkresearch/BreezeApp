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
        return getModelNameFromPath(modelPath);
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
                return "mtk";
            case "localcpu":
                return "CPU";
            case "none":
                return "None";
            default:
                return backend.toUpperCase();
        }
    }

    /**
     * Determines the preferred backend based on device hardware capabilities.
     * @return The preferred backend identifier ("mtk" or "cpu")
     */
    public static String getPreferredBackend() {
        try {
            // Get the device's chipset information from multiple sources
            String hardware = android.os.Build.HARDWARE.toLowerCase();
            String processor = System.getProperty("os.arch", "").toLowerCase();
            String cpuInfo = readCpuInfo();

            // Check if the device has MT6991 chipset
            if (isMTKChipset(hardware, processor, cpuInfo)) {
                Log.i(TAG, "MT6991 chipset detected, using MTK backend");
                return "mtk";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error detecting chipset", e);
        }
        
        Log.i(TAG, "MT6991 chipset not detected, using CPU backend");
        return "cpu";
    }

    /**
     * Reads CPU information from /proc/cpuinfo.
     * @return CPU information as a string, or empty string if reading fails
     */
    private static String readCpuInfo() {
        try {
            StringBuilder cpuInfo = new StringBuilder();
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.FileReader("/proc/cpuinfo"));
            String line;
            while ((line = reader.readLine()) != null) {
                cpuInfo.append(line.toLowerCase()).append("\n");
            }
            reader.close();
            return cpuInfo.toString();
        } catch (Exception e) {
            Log.w(TAG, "Error reading CPU info", e);
            return "";
        }
    }

    /**
     * Checks if the device has an MTK chipset based on hardware information.
     * @param hardware Hardware string from Build.HARDWARE
     * @param processor Processor architecture string
     * @param cpuInfo CPU information from /proc/cpuinfo
     * @return true if MTK chipset is detected, false otherwise
     */
    private static boolean isMTKChipset(String hardware, String processor, String cpuInfo) {
        return hardware.contains("mt6991") || 
               processor.contains("mt6991") || 
               cpuInfo.contains("mt6991");
    }
} 