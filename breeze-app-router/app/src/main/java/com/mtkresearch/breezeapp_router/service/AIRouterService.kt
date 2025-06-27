package com.mtkresearch.breezeapp_router.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.core.app.NotificationCompat
import com.mtkresearch.breezeapp.contracts.AIRouterContract
import com.mtkresearch.breezeapp_router.R
import com.mtkresearch.breezeapp_router.domain.model.AIRequest
import com.mtkresearch.breezeapp_router.domain.model.AIResponse
import com.mtkresearch.breezeapp_router.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * AI Router 前景服務 - 處理 AI 推理請求
 */
class AIRouterService : Service() {
    
    private lateinit var serviceMessenger: Messenger
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var connectionState = ConnectionState.DISCONNECTED
    
    companion object {
        const val CHANNEL_ID = "ai_router_service_channel"
        const val NOTIFICATION_ID = 1001
        
        // 與 UI App 通信的訊息類型
        const val MSG_SEND_MESSAGE = 1
        const val MSG_RESPONSE_MESSAGE = 2
        const val MSG_SERVICE_STATUS = 3
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceMessenger = Messenger(IncomingHandler())
        connectionState = ConnectionState.CONNECTED
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == AIRouterContract.SERVICE_ACTION) {
            return serviceMessenger.binder
        }
        return null
    }
    
    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_SEND_MESSAGE -> {
                    handleAIRequest(msg)
                }
                MSG_SERVICE_STATUS -> {
                    sendServiceStatus(msg.replyTo)
                }
                else -> super.handleMessage(msg)
            }
        }
    }
    
    private fun handleAIRequest(msg: Message) {
        val replyTo = msg.replyTo ?: return
        
        msg.data?.let { bundle ->
            val crossAppMessage = bundle.getParcelable<AIRouterContract.CrossAppMessage>("message")
            crossAppMessage?.let { message ->
                
                // 轉換為內部 AI 請求模型
                val aiRequest = AIRequest(
                    id = message.id,
                    text = message.text,
                    author = message.author,
                    timestamp = message.timestamp
                )
                
                // 在背景執行 AI 推理
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val aiResponse = processAIRequest(aiRequest)
                        sendAIResponse(replyTo, aiResponse)
                    } catch (e: Exception) {
                        val errorResponse = AIResponse(
                            text = "AI 處理錯誤: ${e.message}",
                            isError = true,
                            errorMessage = e.message
                        )
                        sendAIResponse(replyTo, errorResponse)
                    }
                }
            }
        }
    }
    
    private suspend fun processAIRequest(request: AIRequest): AIResponse {
        // 模擬 AI 處理延遲
        delay(1000)
        
        // TODO: 整合真實的 AI 引擎
        val responseText = when {
            request.text.contains("你好") || request.text.contains("hello") -> {
                "你好！我是 AI 助手，很高興為您服務！"
            }
            request.text.contains("天氣") -> {
                "抱歉，我目前無法查詢天氣資訊。請稍後再試。"
            }
            request.text.contains("時間") -> {
                "現在時間是 ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"
            }
            else -> {
                "我收到了您的訊息：「${request.text}」。這是一個模擬回應，真實的 AI 引擎尚未整合。"
            }
        }
        
        return AIResponse(
            text = responseText,
            author = "AI",
            timestamp = System.currentTimeMillis()
        )
    }
    
    private fun sendAIResponse(replyTo: Messenger, response: AIResponse) {
        try {
            val crossAppMessage = AIRouterContract.CrossAppMessage(
                id = response.id,
                text = response.text,
                author = response.author,
                timestamp = response.timestamp,
                isError = response.isError,
                errorMessage = response.errorMessage
            )
            
            val msg = Message.obtain(null, MSG_RESPONSE_MESSAGE)
            val bundle = Bundle()
            bundle.putParcelable("message", crossAppMessage)
            msg.data = bundle
            
            replyTo.send(msg)
        } catch (e: RemoteException) {
            // 客戶端已斷線
        }
    }
    
    private fun sendServiceStatus(replyTo: Messenger?) {
        replyTo?.let { messenger ->
            try {
                val status = AIRouterContract.ServiceStatus(
                    isReady = true,
                    connectionState = connectionState.name,
                    capabilities = listOf("chat", "text_generation")
                )
                
                val msg = Message.obtain(null, MSG_SERVICE_STATUS)
                val bundle = Bundle()
                bundle.putParcelable("status", status)
                msg.data = bundle
                
                messenger.send(msg)
            } catch (e: RemoteException) {
                // 客戶端已斷線
            }
        }
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AI Router Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "AI Router 背景服務通知"
        }
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Router 服務")
            .setContentText("AI Router 正在運行中...")
            .setSmallIcon(R.drawable.ic_ai_router)
            .setOngoing(true)
            .build()
    }
    
    override fun onDestroy() {
        connectionState = ConnectionState.DISCONNECTED
        serviceScope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }
} 