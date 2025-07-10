# Breeze App Router Client - EdgeAI SDK Integration Example

This application serves as a **reference implementation** and **testing tool** for the new **EdgeAI SDK**. It demonstrates how to integrate EdgeAI SDK into Android applications for seamless AI capabilities including chat completion, text-to-speech, and speech recognition.

> **🎉 Now Using EdgeAI SDK v1.0**
>
> This client has been modernized to use the new EdgeAI SDK instead of direct AIDL calls. The EdgeAI SDK provides:
> - 🚀 **OpenAI-compatible APIs** for familiar developer experience
> - 🔧 **Type-safe requests and responses** for better code quality
> - 🔄 **Automatic connection management** - no more manual service binding
> - 📡 **Built-in streaming support** with Kotlin Flows
> - ⚡ **Better error handling** with specific exception types

## Quick Start

### 1. Add EdgeAI SDK Dependency

Add the EdgeAI module to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":EdgeAI"))
    
    // Required for coroutines support
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
}
```

### 2. Initialize EdgeAI SDK

```kotlin
// In your Application.onCreate() or Activity
EdgeAI.initialize(this)
```

### 3. Start Using AI Features

```kotlin
// Chat completion
EdgeAI.chat(chatRequest(prompt = "Tell me a joke"))
    .collect { response ->
        println(response.choices.first().message?.content)
    }

// Text-to-Speech
val audioStream = EdgeAI.tts(ttsRequest(
    input = "Hello, world!",
    voice = "alloy"
))

// Speech Recognition
EdgeAI.asr(asrRequest(audioBytes, language = "en"))
    .collect { response ->
        println("Transcription: ${response.text}")
    }
```

## Architecture Overview

The modernized architecture is significantly simpler thanks to EdgeAI SDK:

```mermaid
graph TD
    A[MainActivity - View] -->|User Actions| B(MainViewModel)
    B -->|Direct API Calls| C[EdgeAI SDK]
    C -->|AIDL| D[AIRouterService]

    subgraph EdgeAI_SDK
        C -->|OpenAI APIs| C1[chat()]
        C -->|OpenAI APIs| C2[tts()]
        C -->|OpenAI APIs| C3[asr()]
    end

    subgraph Data_Flow
        direction LR
        D -->|Responses| C
        C -->|Kotlin Flows| B
        B -->|UiState StateFlow| A
    end

    style A fill:#D6EAF8,stroke:#3498DB
    style B fill:#D1F2EB,stroke:#1ABC9C
    style C fill:#FDEDEC,stroke:#E74C3C
    style D fill:#E8DAEF,stroke:#8E44AD
```

### Key Improvements

- **Eliminated Complex Repository Layer**: EdgeAI SDK handles all connection management internally
- **Removed Manual AIDL Binding**: No more `ServiceConnection`, `AIRouterClient`, or `RouterRepository`
- **Direct API Calls**: Simple, intention-driven API calls like `EdgeAI.chat()`, `EdgeAI.tts()`, `EdgeAI.asr()`
- **Better Error Handling**: Specific exception types for different error scenarios

## Example Usage Patterns

### 1. Simple Chat Completion

```kotlin
viewModelScope.launch {
    try {
        EdgeAI.chat(chatRequest(prompt = "What is Kotlin?"))
            .collect { response ->
                response.choices.forEach { choice ->
                    choice.message?.content?.let { content ->
                        logMessage("✅ Response: $content")
                    }
                }
            }
    } catch (e: EdgeAIException) {
        logMessage("❌ Error: ${e.message}")
    }
}
```

### 2. Streaming Chat

```kotlin
EdgeAI.chat(chatRequest(
    prompt = "Write a story about AI",
    stream = true
))
    .collect { response ->
        response.choices.forEach { choice ->
            choice.delta?.content?.let { content ->
                // Display streaming content in real-time
                appendToChat(content)
            }
        }
    }
```

### 3. Text-to-Speech

```kotlin
try {
    val audioStream = EdgeAI.tts(ttsRequest(
        input = "Hello from EdgeAI SDK!",
        voice = "alloy",
        speed = 1.0f
    ))
    
    // Play the audio stream
    audioPlayer.play(audioStream)
    
} catch (e: EdgeAIException) {
    handleError(e)
}
```

### 4. Speech Recognition

```kotlin
EdgeAI.asr(asrRequest(
    audioBytes = recordedAudio,
    language = "en",
    includeWordTimestamps = true
))
    .collect { response ->
        logMessage("Transcription: ${response.text}")
        
        // Show word-level timestamps if available
        response.segments?.forEach { segment ->
            segment.words?.forEach { word ->
                logMessage("Word: ${word.word} (${word.start}s)")
            }
        }
    }
```

### 5. Advanced Chat with History

```kotlin
val messages = conversation {
    system("You are a helpful programming assistant.")
    user("What's the difference between val and var in Kotlin?")
    assistant("In Kotlin, `val` declares read-only properties...")
    user("Can you give me an example?")
}

EdgeAI.chat(chatRequestWithHistory(
    messages = messages,
    temperature = 0.7f,
    stream = true
))
    .collect { response ->
        // Handle streaming response
    }
```

## Error Handling

EdgeAI SDK provides comprehensive error handling:

```kotlin
try {
    EdgeAI.chat(request)
        .catch { e ->
            when (e) {
                is InvalidInputException -> logError("Invalid input: ${e.message}")
                is ModelNotFoundException -> logError("Model not available: ${e.message}")
                is ServiceConnectionException -> logError("Connection failed: ${e.message}")
                is AudioProcessingException -> logError("Audio error: ${e.message}")
                is ResourceLimitException -> logError("Resource limit: ${e.message}")
                is TimeoutException -> logError("Request timeout: ${e.message}")
                else -> logError("Unknown error: ${e.message}")
            }
        }
        .collect { response ->
            // Handle successful response
        }
} catch (e: EdgeAIException) {
    // Handle initialization or other errors
}
```

## Migration from Old AIDL Approach

If you're migrating from the old direct AIDL approach:

### Before (Deprecated)
```kotlin
// Complex setup required
val client = AIRouterClient(context)
val repository = RouterRepository(client, scope)
repository.connect()

// Manual request creation
val request = AIRequest(payload = RequestPayload.TextChat(...))
repository.sendRequest(request)

// Manual response handling
repository.responses.collect { response ->
    // Handle AIResponse manually
}
```

### After (EdgeAI SDK)
```kotlin
// Simple initialization
EdgeAI.initialize(context)

// Direct API calls
EdgeAI.chat(chatRequest(prompt = "Hello"))
    .collect { response ->
        // Type-safe ChatCompletionResponse
    }
```

## Key Features Demonstrated

This client application showcases:

- ✅ **Chat Completion**: Both single and streaming responses
- ✅ **Text-to-Speech**: Audio generation with different voices
- ✅ **Speech Recognition**: Audio transcription with timestamps
- ✅ **Error Handling**: Comprehensive exception handling
- ✅ **Connection Management**: Automatic service binding
- ✅ **Type Safety**: Strongly-typed requests and responses
- ⚠️ **Image Analysis**: Planned for future SDK versions
- ⚠️ **Content Moderation**: Planned for future SDK versions

## Building and Running

1. Ensure you have the `breeze-app-router` service installed on your device:
   ```bash
   adb shell pm list packages | grep breezeapp.router
   ```

2. Build and install the client:
   ```bash
   ./gradlew :breeze-app-router-client:installDebug
   ```

3. Launch the app and test AI features through the clean UI.

## Configuration

The EdgeAI SDK automatically handles:
- ✅ Service discovery and binding
- ✅ Connection state management
- ✅ Request routing and response handling
- ✅ Error recovery and timeouts

No manual configuration required!

## Permissions

The EdgeAI SDK handles permissions automatically. Your `AndroidManifest.xml` should include:

```xml
<uses-permission android:name="com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE" />

<queries>
    <package android:name="com.mtkresearch.breezeapp.router" />
</queries>
```

## Troubleshooting

### Common Issues

**EdgeAI SDK fails to initialize**
- Verify the router service is installed: `adb shell pm list packages | grep breezeapp.router`
- Check that permissions are correctly declared in `AndroidManifest.xml`
- Ensure your app is signed with the same certificate as the router service (for debug builds)

**Requests timeout or fail**
- Check device logs: `adb logcat | grep EdgeAI`
- Verify the router service is running: `adb shell ps | grep breezeapp`
- Try restarting the router service or reinstalling it

**Model not found errors**
- The router service may not have the required models loaded
- Check router service logs for model loading status
- Verify the router service configuration

### Debug Tips

1. **Enable verbose logging**:
   ```kotlin
   EdgeAI.setLogLevel(Log.VERBOSE)
   ```

2. **Check SDK readiness**:
   ```kotlin
   if (!EdgeAI.isReady()) {
       // SDK not ready, check initialization
   }
   ```

3. **Monitor connection state**:
   ```kotlin
   // SDK handles connection automatically, but you can check:
   // Check logs for connection status updates
   ```

## Next Steps

- 📖 **Read the Full API Documentation**: Check `EdgeAI/README.md` for complete API reference
- 🔧 **Explore Usage Examples**: See `EdgeAIUsageExample.kt` for comprehensive usage patterns
- 🚀 **Integrate into Your App**: Use this client as a reference for your own integration
- 📝 **Provide Feedback**: Help us improve the EdgeAI SDK

## About

This modernized client demonstrates the power and simplicity of the EdgeAI SDK. By abstracting away the complexities of AIDL communication, the SDK allows developers to focus on building great AI-powered experiences rather than dealing with service connection boilerplate.

Welcome to the future of Android AI development! 🚀 