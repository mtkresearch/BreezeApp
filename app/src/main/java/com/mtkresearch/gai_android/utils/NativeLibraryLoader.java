package com.mtkresearch.gai_android.utils;

import android.util.Log;

public class NativeLibraryLoader {
    private static final String TAG = "NativeLibraryLoader";
    private static boolean isLoaded = false;

    public static synchronized void loadLibraries() {
        if (isLoaded) {
            return;
        }

        try {
            // Load MTK LLM dependencies first
//            System.loadLibrary("common");
//            System.loadLibrary("tokenizer");
//            System.loadLibrary("yaml-cpp");
//            System.loadLibrary("re2");
//            System.loadLibrary("sentencepiece");
//            System.loadLibrary("mtk_llm");
//            System.loadLibrary("main_llm");
//            System.loadLibrary("llm_jni");
            
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