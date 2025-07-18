package com.mtkresearch.breezeapp.router.system

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Simple global library tracker stub for compilation.
 */
object GlobalLibraryTracker {
    private const val TAG = "GlobalLibraryTracker"
    private const val PREFS_NAME = "global_library_state"
    private const val KEY_INFERENCE_ACTIVE = "inference_active"
    private const val KEY_FORCE_STOP_COUNT = "force_stop_count"
    private const val KEY_LAST_LOAD_TIME = "last_load_time"
    
    private var prefs: SharedPreferences? = null
    private var isLibraryActuallyLoaded = false
    
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isLibraryActuallyLoaded = testLibraryLoaded()
        Log.d(TAG, "GlobalLibraryTracker initialized")
    }
    
    private fun testLibraryLoaded(): Boolean {
        return try {
            System.loadLibrary("llm_jni")
            true
        } catch (e: UnsatisfiedLinkError) {
            val errorMsg = e.message?.lowercase() ?: ""
            errorMsg.contains("already loaded")
        } catch (e: Exception) {
            false
        }
    }
    
    fun markInferenceStarted() {
        prefs?.edit()?.putBoolean(KEY_INFERENCE_ACTIVE, true)?.apply()
    }
    
    fun markInferenceCompleted() {
        prefs?.edit()?.putBoolean(KEY_INFERENCE_ACTIVE, false)?.apply()
    }
    
    fun isLibraryUsable(): Boolean {
        val wasInferenceActive = prefs?.getBoolean(KEY_INFERENCE_ACTIVE, false) ?: false
        return isLibraryActuallyLoaded && !wasInferenceActive
    }
    
    fun getDiagnosticInfo(): Map<String, Any> {
        return mapOf(
            "isLibraryActuallyLoaded" to isLibraryActuallyLoaded,
            "wasInferenceActive" to (prefs?.getBoolean(KEY_INFERENCE_ACTIVE, false) ?: false),
            "forceStopCount" to (prefs?.getInt(KEY_FORCE_STOP_COUNT, 0) ?: 0),
            "lastLoadTime" to (prefs?.getLong(KEY_LAST_LOAD_TIME, 0) ?: 0),
            "currentProcessId" to android.os.Process.myPid()
        )
    }
}