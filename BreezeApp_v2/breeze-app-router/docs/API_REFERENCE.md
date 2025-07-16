# API Reference

## 🎯 Overview

BreezeApp Router provides AI capabilities through an AIDL service interface. This document covers the complete API for client applications.

## 🔌 Service Connection

### 1. Add AIDL Dependency
```kotlin
// In your app's build.gradle
dependencies {
    implementation project(':breeze-app-router')
}
```

### 2. Bind to Service
```kotlin
class YourActivity : AppCompatActivity() {
    private var aiRouterService: IAIRouterService? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aiRouterService = IAIRouterService.Stub.asInterface(service)
            // Service ready to use
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            aiRouterService = null
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindToAIRouterService()
    }
    
    private fun bindToAIRouterService() {
        val intent = Intent().apply {
            component = ComponentName(
                "com.mtkresearch.breezeapp.router",
                "com.mtkresearch.breezeapp.router.AIRouterService"
            )
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}
```

## 📡 API Methods

### Chat Completion
```kotlin
// Send chat request
val chatRequest = ChatRequest().apply {
    messages = listOf(
        ChatMessage().apply {
            role = "user"
            content = "Hello, how are you?"
        }
    )
    model = "llama-3.2-1b"
    stream = true  // Enable streaming
    temperature = 0.7f
    maxCompletionTokens = 150
}

aiRouterService?.sendChatRequest("unique-request-id", chatRequest)
```

### Text-to-Speech
```kotlin
val ttsRequest = TTSRequest().apply {
    input = "Hello, this is a test message"
    model = "tts-1"
    voice = "alloy"
    speed = 1.0f
    responseFormat = "mp3"
}

aiRouterService?.sendTTSRequest("tts-request-id", ttsRequest)
```

### Speech-to-Text
```kotlin
val asrRequest = ASRRequest().apply {
    file = audioByteArray  // Your audio data
    model = "whisper-1"
    language = "en"
    responseFormat = "json"
    temperature = 0.0f
}

aiRouterService?.sendASRRequest("asr-request-id", asrRequest)
```

## 📥 Response Handling

### Register Listener
```kotlin
private val aiListener = object : IAIRouterListener.Stub() {
    override fun onResponse(response: AIResponse?) {
        response?.let { handleResponse(it) }
    }
}

// Register listener
aiRouterService?.registerListener(aiListener)

private fun handleResponse(response: AIResponse) {
    when (response.state) {
        AIResponse.ResponseState.STREAMING -> {
            // Partial response - update UI incrementally
            appendToChat(response.text)
        }
        AIResponse.ResponseState.COMPLETED -> {
            // Final response
            if (response.audioData != null) {
                // Handle TTS audio data
                playAudio(response.audioData)
            } else {
                // Handle text response
                finalizeChat(response.text)
            }
        }
        AIResponse.ResponseState.ERROR -> {
            // Handle error
            showError(response.error ?: "Unknown error")
        }
    }
}
```

## 🛠️ Utility Methods

### Check Capabilities
```kotlin
val hasStreaming = aiRouterService?.hasCapability("streaming") ?: false
val hasBinaryData = aiRouterService?.hasCapability("binary_data") ?: false
```

### Cancel Request
```kotlin
val cancelled = aiRouterService?.cancelRequest("request-id") ?: false
```

### API Version
```kotlin
val apiVersion = aiRouterService?.apiVersion ?: 0
```

## 📊 Data Models

### ChatRequest
```kotlin
data class ChatRequest(
    var messages: List<ChatMessage> = emptyList(),
    var model: String = "llama-3.2-1b",
    var stream: Boolean? = null,
    var temperature: Float? = null,
    var maxCompletionTokens: Int? = null
)
```

### AIResponse
```kotlin
data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean,
    val state: ResponseState,
    val audioData: ByteArray? = null,
    val error: String? = null
) {
    enum class ResponseState {
        STREAMING, COMPLETED, ERROR
    }
}
```

## ⚠️ Best Practices

### Error Handling
```kotlin
try {
    aiRouterService?.sendChatRequest(requestId, chatRequest)
} catch (e: RemoteException) {
    // Handle service disconnection
    Log.e("AI", "Service disconnected", e)
    rebindService()
}
```

### Memory Management
```kotlin
override fun onDestroy() {
    super.onDestroy()
    // Always unregister listeners
    aiRouterService?.unregisterListener(aiListener)
    unbindService(serviceConnection)
}
```

### Request IDs
- Use unique IDs for each request
- Consider using UUID.randomUUID().toString()
- Store mapping between requests and UI components

## 🔧 Troubleshooting

### Common Issues

1. **Service Not Found**
   - Ensure router app is installed
   - Check component name is correct

2. **Permission Denied**
   - Verify signature-level permission
   - Check app signing certificates match

3. **No Response**
   - Verify listener is registered
   - Check request ID matches
   - Enable debug logging

### Debug Logging
```kotlin
// Enable verbose logging
adb shell setprop log.tag.AIRouterService VERBOSE
```