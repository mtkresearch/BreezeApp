# 🚀 Getting Started with AI Router

Welcome! This guide will help you set up, understand, and start using the BreezeApp AI Router in just a few minutes.

## 📋 Prerequisites

- **Android Studio**: 2023.1.1 or later
- **Android SDK**: API 34 or higher  
- **Kotlin**: 1.9.0 or later
- **Java**: JDK 8 or later

## ⚡ Quick Setup

### 1. Clone and Build

```bash
git clone <repository-url>
cd BreezeApp/BreezeApp_v2
./gradlew :breeze-app-router:assembleDebug
```

### 2. Install the Router Service

```bash
adb install breeze-app-router/build/outputs/apk/debug/breeze-app-router-debug.apk
```

### 3. Add to Your Project

```kotlin
// In your app's build.gradle.kts
dependencies {
    implementation(project(":BreezeApp_v2:shared-contracts"))
}
```

## 🎯 Your First AI Request

### Step 1: Bind to the Service

```kotlin
class MainActivity : AppCompatActivity() {
    private var aiRouterService: IAIRouterService? = null
    private var isServiceBound = false
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aiRouterService = IAIRouterService.Stub.asInterface(service)
            isServiceBound = true
            
            // Register listener for responses
            aiRouterService?.registerListener(responseListener)
            
            Log.d(TAG, "AI Router service connected!")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            aiRouterService = null
            isServiceBound = false
            Log.d(TAG, "AI Router service disconnected")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindToAIRouterService()
    }
    
    private fun bindToAIRouterService() {
        val intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService")
        intent.setPackage("com.mtkresearch.breezeapp.router")
        
        if (!bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            Log.e(TAG, "Failed to bind to AI Router service")
        }
    }
}
```

### Step 2: Handle Responses

```kotlin
private val responseListener = object : IAIRouterListener.Stub() {
    override fun onResponse(response: AIResponse) {
        runOnUiThread {
            Log.d(TAG, "Received response: ${response.text}")
            // Update your UI here
            showAIResponse(response.text)
        }
    }
    
    override fun onError(requestId: String, error: String) {
        runOnUiThread {
            Log.e(TAG, "AI Error [$requestId]: $error")
            showError("AI request failed: $error")
        }
    }
    
    override fun onCapabilityChanged(capability: String, available: Boolean) {
        Log.d(TAG, "Capability $capability is now ${if (available) "available" else "unavailable"}")
    }
}
```

### Step 3: Send Your First Request

```kotlin
private fun sendTextRequest(userMessage: String) {
    if (!isServiceBound) {
        Log.w(TAG, "Service not bound")
        return
    }
    
    val request = AIRequest(
        id = "request-${System.currentTimeMillis()}",
        sessionId = "session-${userId}",
        text = userMessage,
        timestamp = System.currentTimeMillis(),
        options = mapOf(
            "request_type" to "text_generation",
            "streaming" to "false"
        )
    )
    
    try {
        aiRouterService?.sendMessage(request)
        Log.d(TAG, "Sent AI request: $userMessage")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to send AI request", e)
    }
}
```

## 🎨 Different AI Capabilities

### 💬 Text Generation (LLM)

```kotlin
private fun sendLLMRequest(prompt: String) {
    val request = AIRequest(
        id = generateRequestId(),
        sessionId = currentSessionId,
        text = prompt,
        timestamp = System.currentTimeMillis(),
        options = mapOf(
            "request_type" to "text_generation",
            "temperature" to "0.7",
            "max_tokens" to "150"
        )
    )
    aiRouterService?.sendMessage(request)
}
```

### 🖼️ Image Analysis (VLM)

```kotlin
private fun sendImageAnalysisRequest(imageBytes: ByteArray, question: String) {
    val imageBase64 = Base64.encodeToString(imageBytes, Base64.DEFAULT)
    
    val request = AIRequest(
        id = generateRequestId(),
        sessionId = currentSessionId,
        text = question,
        timestamp = System.currentTimeMillis(),
        options = mapOf(
            "request_type" to "image_analysis",
            "image_data" to imageBase64
        )
    )
    aiRouterService?.sendMessage(request)
}
```

### 🎤 Speech Recognition (ASR)

```kotlin
private fun sendSpeechRecognitionRequest(audioBytes: ByteArray) {
    val audioBase64 = Base64.encodeToString(audioBytes, Base64.DEFAULT)
    
    val request = AIRequest(
        id = generateRequestId(),
        sessionId = currentSessionId,
        text = "", // Empty for ASR
        timestamp = System.currentTimeMillis(),
        options = mapOf(
            "request_type" to "speech_recognition",
            "audio_data" to audioBase64,
            "audio_format" to "wav"
        )
    )
    aiRouterService?.sendMessage(request)
}
```

### 🔊 Text-to-Speech (TTS)

```kotlin
private fun sendTTSRequest(textToSpeak: String) {
    val request = AIRequest(
        id = generateRequestId(),
        sessionId = currentSessionId,
        text = textToSpeak,
        timestamp = System.currentTimeMillis(),
        options = mapOf(
            "request_type" to "speech_synthesis",
            "voice" to "female",
            "speed" to "1.0"
        )
    )
    aiRouterService?.sendMessage(request)
}
```

### 🛡️ Content Moderation (Guardian)

```kotlin
private fun sendContentModerationRequest(content: String) {
    val request = AIRequest(
        id = generateRequestId(),
        sessionId = currentSessionId,
        text = content,
        timestamp = System.currentTimeMillis(),
        options = mapOf(
            "request_type" to "content_moderation",
            "check_type" to "safety"
        )
    )
    aiRouterService?.sendMessage(request)
}
```

## 🌊 Streaming Responses

For real-time responses (like LLM text generation):

```kotlin
private fun sendStreamingRequest(prompt: String) {
    val request = AIRequest(
        id = generateRequestId(),
        sessionId = currentSessionId,
        text = prompt,
        timestamp = System.currentTimeMillis(),
        options = mapOf(
            "request_type" to "text_generation",
            "streaming" to "true",  // Enable streaming
            "temperature" to "0.7"
        )
    )
    
    aiRouterService?.sendMessage(request)
}

// Your listener will receive multiple partial responses
private val streamingListener = object : IAIRouterListener.Stub() {
    override fun onResponse(response: AIResponse) {
        runOnUiThread {
            if (response.metadata["partial"] == "true") {
                // Partial response - append to existing text
                appendToCurrentResponse(response.text)
            } else {
                // Final response - complete
                finalizeResponse(response.text)
            }
        }
    }
}
```

## 🔧 Configuration & Debugging

### Check Available Capabilities

```kotlin
private fun checkCapabilities() {
    val capabilities = listOf("binary_data", "streaming", "image_processing", "audio_processing", "mock_runners")
    
    capabilities.forEach { capability ->
        val isAvailable = aiRouterService?.hasCapability(capability) ?: false
        Log.d(TAG, "Capability '$capability': $isAvailable")
    }
}
```

### Request Cancellation

```kotlin
private fun cancelRequest(requestId: String) {
    val success = aiRouterService?.cancelRequest(requestId) ?: false
    Log.d(TAG, "Cancel request $requestId: ${if (success) "success" else "failed"}")
}
```

### Service Cleanup

```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    if (isServiceBound) {
        aiRouterService?.unregisterListener(responseListener)
        unbindService(serviceConnection)
        isServiceBound = false
    }
}
```

## 📱 Testing with Mock Runners

The AI Router includes **realistic mock implementations** that work out of the box:

- **MockLLMRunner**: Generates believable text responses
- **MockVLMRunner**: Analyzes images and answers questions  
- **MockASRRunner**: Converts audio to text
- **MockTTSRunner**: Generates speech audio data
- **MockGuardrailRunner**: Performs content safety checks

These mocks allow you to:
- ✅ Test your integration without real AI models
- ✅ Develop offline without internet connectivity
- ✅ Simulate various response scenarios
- ✅ Verify your UI/UX flows

## 🚨 Common Issues & Solutions

### Service Binding Fails
```kotlin
// Check if the router app is installed
private fun isRouterInstalled(): Boolean {
    return try {
        packageManager.getPackageInfo("com.mtkresearch.breezeapp.router", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
```

### Permission Issues
Ensure your app's signing certificate matches the router's for signature-level permissions in production.

### No Response Received
- Check that you've registered the listener before sending requests
- Verify the request format matches the expected schema
- Check logcat for error messages from the router service

## 🎯 Next Steps

Now that you have the basics working:

1. **[Architecture Guide](./ARCHITECTURE_GUIDE.md)** - Understand the system design
2. **[Integration Guide](./INTEGRATION_GUIDE.md)** - Advanced integration patterns  
3. **[Runner Development](./RUNNER_DEVELOPMENT.md)** - Create custom AI runners
4. **[Configuration Guide](./CONFIGURATION_GUIDE.md)** - Customize behavior

## 💡 Pro Tips

- **Use unique session IDs** to maintain conversation context
- **Implement request timeouts** for better UX
- **Handle service disconnections** gracefully
- **Test with mock runners first** before integrating real AI
- **Monitor memory usage** with image/audio requests

---

🎉 **Congratulations!** You're now ready to build AI-powered Android apps with the BreezeApp Router! 