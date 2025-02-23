package com.mtkresearch.breeze_app.utils;

import android.util.Log;

public class NativeLibraryLoader {
    private static final String TAG = "NativeLibraryLoader";
    private static boolean isLoaded = false;

    public static synchronized void loadLibraries() {
        if (isLoaded) {
            return;
        }

        try {
            // Load Sherpa ONNX library
            System.loadLibrary("sherpa-onnx-jni");
            
            isLoaded = true;
            Log.d(TAG, "Native libraries loaded successfully");
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native libraries: " + e.getMessage());
            throw e;
        }
    }
} 