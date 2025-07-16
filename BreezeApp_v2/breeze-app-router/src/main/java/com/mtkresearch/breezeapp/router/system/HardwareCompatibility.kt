package com.mtkresearch.breezeapp.router.system

import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.FileReader

/**
 * MTK Hardware Compatibility Detection Module
 * 
 * This module provides hardware compatibility detection specifically for MTK NPU support.
 * It's designed to be clean, testable, and focused on NPU availability detection.
 * 
 * Located in breeze-app-router as it's part of the AI service infrastructure,
 * not the client SDK.
 * 
 * Based on the original HWCompatibility.java but simplified and modernized.
 */
object HardwareCompatibility {
    
    private const val TAG = "HardwareCompatibility"
    
    // MTK chipset identifiers that support NPU
    private val MTK_NPU_SUPPORTED_CHIPSETS = setOf(
        "mt6991",
        "mt6989",
        "mt6988"  // Add more as needed
    )
    
    // Manufacturers that might have compatibility issues
    private val EXCLUDED_MANUFACTURERS = setOf(
        "OPPO"  // Based on original logic
    )
    
    /**
     * Check if MTK NPU is supported on this device
     * 
     * @return true if MTK NPU is supported, false otherwise
     */
    fun isMTKNPUSupported(): Boolean {
        try {
            val chipsetInfo = getMTKChipsetInfo()
            
            if (chipsetInfo == null) {
                Log.d(TAG, "No MTK chipset detected")
                return false
            }
            
            val isSupported = chipsetInfo.isMTKNPUCapable && 
                             !isExcludedManufacturer()
            
            Log.d(TAG, "MTK NPU support: $isSupported (chipset: ${chipsetInfo.chipsetModel})")
            return isSupported
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking MTK NPU support", e)
            return false
        }
    }
    
    /**
     * Get detailed MTK chipset information
     * 
     * @return ChipsetInfo if MTK chipset is detected, null otherwise
     */
    fun getMTKChipsetInfo(): ChipsetInfo? {
        try {
            val hardware = Build.HARDWARE.lowercase()
            val processor = System.getProperty("os.arch", "").lowercase()
            val cpuInfo = readCpuInfo()
            
            Log.d(TAG, "Hardware detection - Hardware: $hardware, Processor: $processor")
            
            // Check all sources for MTK chipset
            val detectedChipset = detectMTKChipset(hardware, processor, cpuInfo)
            
            return if (detectedChipset != null) {
                ChipsetInfo(
                    chipsetModel = detectedChipset,
                    manufacturer = Build.MANUFACTURER,
                    isMTKNPUCapable = MTK_NPU_SUPPORTED_CHIPSETS.contains(detectedChipset),
                    deviceModel = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE
                )
            } else {
                null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MTK chipset info", e)
            return null
        }
    }
    
    /**
     * Validate MTK execution environment
     * 
     * @return ValidationResult with detailed validation information
     */
    fun validateMTKEnvironment(): ValidationResult {
        val results = mutableListOf<ValidationResult.ValidationItem>()
        
        // Check chipset support
        val chipsetInfo = getMTKChipsetInfo()
        results.add(
            ValidationResult.ValidationItem(
                check = "MTK Chipset Detection",
                passed = chipsetInfo != null,
                details = chipsetInfo?.chipsetModel ?: "No MTK chipset detected"
            )
        )
        
        // Check NPU capability
        results.add(
            ValidationResult.ValidationItem(
                check = "NPU Capability",
                passed = chipsetInfo?.isMTKNPUCapable == true,
                details = if (chipsetInfo?.isMTKNPUCapable == true) {
                    "NPU supported on ${chipsetInfo.chipsetModel}"
                } else {
                    "NPU not supported or unknown chipset"
                }
            )
        )
        
        // Check manufacturer exclusion
        val isExcluded = isExcludedManufacturer()
        results.add(
            ValidationResult.ValidationItem(
                check = "Manufacturer Compatibility",
                passed = !isExcluded,
                details = if (isExcluded) {
                    "Manufacturer ${Build.MANUFACTURER} is excluded"
                } else {
                    "Manufacturer ${Build.MANUFACTURER} is compatible"
                }
            )
        )
        
        // Check Android version (minimum API level)
        val minApiLevel = 33  // Based on project configuration
        val currentApiLevel = Build.VERSION.SDK_INT
        results.add(
            ValidationResult.ValidationItem(
                check = "Android Version",
                passed = currentApiLevel >= minApiLevel,
                details = "API level $currentApiLevel (minimum: $minApiLevel)"
            )
        )
        
        val allPassed = results.all { it.passed }
        
        return ValidationResult(
            isValid = allPassed,
            validationItems = results,
            summary = if (allPassed) {
                "MTK environment validation passed"
            } else {
                "MTK environment validation failed: ${results.count { !it.passed }} issues found"
            }
        )
    }
    
    /**
     * Read CPU information from /proc/cpuinfo
     * 
     * @return CPU information as string, or empty string if reading fails
     */
    private fun readCpuInfo(): String {
        return try {
            val cpuInfo = StringBuilder()
            BufferedReader(FileReader("/proc/cpuinfo")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    cpuInfo.append(line!!.lowercase()).append("\n")
                }
            }
            cpuInfo.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Error reading CPU info", e)
            ""
        }
    }
    
    /**
     * Detect MTK chipset from hardware information
     * 
     * @param hardware Hardware string from Build.HARDWARE
     * @param processor Processor architecture string
     * @param cpuInfo CPU information from /proc/cpuinfo
     * @return Detected MTK chipset model, or null if not found
     */
    private fun detectMTKChipset(hardware: String, processor: String, cpuInfo: String): String? {
        val sources = listOf(hardware, processor, cpuInfo)
        
        for (chipset in MTK_NPU_SUPPORTED_CHIPSETS) {
            if (sources.any { it.contains(chipset) }) {
                return chipset
            }
        }
        
        return null
    }
    
    /**
     * Check if current manufacturer is excluded
     * 
     * @return true if manufacturer should be excluded, false otherwise
     */
    private fun isExcludedManufacturer(): Boolean {
        return EXCLUDED_MANUFACTURERS.contains(Build.MANUFACTURER)
    }
}

/**
 * Data class representing MTK chipset information
 */
data class ChipsetInfo(
    val chipsetModel: String,
    val manufacturer: String,
    val isMTKNPUCapable: Boolean,
    val deviceModel: String,
    val androidVersion: String
)

/**
 * Data class representing validation results
 */
data class ValidationResult(
    val isValid: Boolean,
    val validationItems: List<ValidationItem>,
    val summary: String
) {
    data class ValidationItem(
        val check: String,
        val passed: Boolean,
        val details: String
    )
} 