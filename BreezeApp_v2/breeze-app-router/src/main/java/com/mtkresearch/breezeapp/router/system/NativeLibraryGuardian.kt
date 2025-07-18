package com.mtkresearch.breezeapp.router.system

import android.util.Log

/**
 * Simple native library guardian for resource tracking.
 */
object NativeLibraryGuardian {
    private const val TAG = "NativeLibraryGuardian"
    private var loadedLibraryCount = 0
    
    fun getInstance(): NativeLibraryGuardian = this
    
    fun getLoadedLibraryCount(): Int = loadedLibraryCount
    
    fun registerLibrary(libraryId: String, cleanupHandler: () -> Unit, metadata: Map<String, Any>) {
        loadedLibraryCount++
        Log.d(TAG, "Registered library: $libraryId")
    }
    
    fun unregisterLibrary(libraryId: String): Boolean {
        if (loadedLibraryCount > 0) loadedLibraryCount--
        Log.d(TAG, "Unregistered library: $libraryId")
        return true
    }
    
    fun forceCleanupAll(): Int {
        val cleaned = loadedLibraryCount
        loadedLibraryCount = 0
        Log.d(TAG, "Force cleaned $cleaned libraries")
        return cleaned
    }
}