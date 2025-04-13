package com.mtkresearch.breeze_app.utils;

import android.util.Log;
import com.mtkresearch.gai_android.service.LLMEngineService;

public class NativeLibraryLoader {
    private static final String TAG = "NativeLibraryLoader";
    private static boolean isLoaded = false;

    public static synchronized void loadLibraries() {
        if (isLoaded) {
            return;
        }

        try {
            // Load Sherpa ONNX library for speech recognition
            System.loadLibrary("sherpa-onnx-jni");
            Log.d(TAG, "sherpa-onnx-jni library loaded successfully");
            
            // Check if MTK JNI library is available through compatibility class
            // The static initializer in LLMEngineService will have already attempted to load it
            boolean mtkAvailable = LLMEngineService.isMTKBackendAvailable();
            
            if (mtkAvailable) {
                Log.d(TAG, "MTK JNI library is available through compatibility class");
            } else {
                Log.d(TAG, "MTK acceleration will not be available, app will continue with CPU backend");
            }
            
            isLoaded = true;
            Log.d(TAG, "Native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load essential native libraries: " + e.getMessage());
            throw e;
        }
    }
} 