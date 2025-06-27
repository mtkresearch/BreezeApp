package com.mtkresearch.breezeapp_router

import android.app.Application
import android.content.Intent
import android.util.Log
import com.mtkresearch.breezeapp_router.service.AIRouterService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AIRouterApplication : Application() {

    companion object {
        private const val TAG = "AIRouterApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AI Router Application 啟動")
        
        // 啟動 AI Router 服務
        startAIRouterService()
    }

    private fun startAIRouterService() {
        try {
            val serviceIntent = Intent(this, AIRouterService::class.java)
            startForegroundService(serviceIntent)
            Log.d(TAG, "AI Router Service 已啟動")
        } catch (e: Exception) {
            Log.e(TAG, "無法啟動 AI Router Service", e)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "AI Router Application 關閉")
    }
} 