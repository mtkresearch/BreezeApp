package com.mtkresearch.breezeapp.utils;
import android.util.Log;

public class HWCompatibility {
    private static final String TAG = "HWCompatibility";

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
        return (hardware.contains("mt6991") || 
                 processor.contains("mt6991") || 
                 cpuInfo.contains("mt6991")) ;
    }

    public static String isSupportedHW(){
        // Get the device's chipset information from multiple sources
        String hardware = android.os.Build.HARDWARE.toLowerCase();
        String processor = System.getProperty("os.arch", "").toLowerCase();
        String cpuInfo = readCpuInfo();

        Log.d(TAG, "Chipset detection - Hardware: " + hardware + 
                    ", Processor: " + processor + 
                    ", CPU Info: " + cpuInfo);

        // Check if the device has MT6991 chipset
        if (HWCompatibility.isMTKChipset(hardware, processor, cpuInfo)&& !android.os.Build.MANUFACTURER.equals("OPPO")) {
            Log.i(TAG, "MT6991/MT6989 chipset detected, using MTK backend");
            return "mtk";
        }
        return null;
    }
}
