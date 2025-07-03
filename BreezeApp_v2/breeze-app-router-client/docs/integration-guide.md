# AI Router Client Integration Guide

This guide provides detailed instructions for integrating the BreezeApp AI Router Client into your Android application.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Basic Usage](#basic-usage)
- [Advanced Configuration](#advanced-configuration)
- [Error Handling](#error-handling)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)
- [API Reference](#api-reference)

## Overview

The BreezeApp AI Router Client provides a simple interface to connect to the AI Router Service, which manages AI model execution across different backends (NPU, CPU, Cloud). The client handles all the complexities of service binding, IPC communication, and error handling.

### Key Features

- Simple API for text generation and speech recognition
- Automatic service discovery and binding
- Stream-based responses for real-time UI updates
- Configurable fallback strategies
- Comprehensive error handling

## Prerequisites

- Android API level 24+ (Android 7.0 Nougat or higher)
- The AI Router Service installed on the device
- Kotlin 1.8+ for the best development experience

## Installation

### 1. Add the dependency

Add the following to your app's `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("com.mtkresearch.breezeapp:router-client:1.0.0")
}
```

### 2. Add required permissions

Add the following permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE" />
```

## Basic Usage

### Initialize the client

```kotlin
// In your Application class or Activity
val routerClient = AIRouterClient.Builder(context)
    .setConnectionListener { state ->
        when (state) {
            ConnectionState.CONNECTED -> Log.d(TAG, "Connected to AI Router Service")
            ConnectionState.DISCONNECTED -> Log.d(TAG, "Disconnected from AI Router Service")
            ConnectionState.ERROR -> Log.e(TAG, "Error connecting to AI Router Service")
        }
    }
    .build()

// Connect to the service
lifecycleScope.launch {
    routerClient.connect()
}
```

### Generate text

```kotlin
// Simple text generation
lifecycleScope.launch {
    try {
        val response = routerClient.generateText("What is the capital of France?")
        textView.text = response.text
    } catch (e: AIRouterException) {
        handleError(e)
    }
}

// Streaming text generation
lifecycleScope.launch {
    try {
        routerClient.generateTextStream("Tell me a story about a robot.")
            .collect { partialResponse ->
                textView.append(partialResponse.text)
            }
    } catch (e: AIRouterException) {
        handleError(e)
    }
}
```

### Speech recognition

```kotlin
// Start speech recognition
val audioRecorder = AudioRecorder(context)
lifecycleScope.launch {
    try {
        audioRecorder.start()
        routerClient.recognizeSpeech(audioRecorder.audioStream)
            .collect { result ->
                textView.text = result.text
                if (result.isFinal) {
                    audioRecorder.stop()
                }
            }
    } catch (e: AIRouterException) {
        handleError(e)
        audioRecorder.stop()
    }
}
```

### Cleanup

```kotlin
override fun onDestroy() {
    super.onDestroy()
    routerClient.disconnect()
}
```

## Advanced Configuration

### Custom model selection

```kotlin
val request = TextGenerationRequest.Builder("What is the weather today?")
    .setModelId("gpt-3.5-turbo")  // Request a specific model
    .setTemperature(0.7f)         // Control randomness
    .setMaxTokens(100)            // Limit response length
    .build()

lifecycleScope.launch {
    val response = routerClient.generateText(request)
    textView.text = response.text
}
```

### Fallback strategy

```kotlin
val routerClient = AIRouterClient.Builder(context)
    .setFallbackStrategy(FallbackStrategy.CLOUD_FIRST)  // Prefer cloud models
    .build()
```

### Timeout configuration

```kotlin
val routerClient = AIRouterClient.Builder(context)
    .setConnectionTimeout(10000)  // 10 seconds
    .setOperationTimeout(30000)   // 30 seconds
    .build()
```

## Error Handling

The client uses `AIRouterException` to encapsulate all errors:

```kotlin
try {
    val response = routerClient.generateText("What is the capital of France?")
    textView.text = response.text
} catch (e: AIRouterException) {
    when (e.errorCode) {
        ErrorCode.SERVICE_UNAVAILABLE -> {
            // The AI Router Service is not installed or not running
            showServiceInstallPrompt()
        }
        ErrorCode.CONNECTION_ERROR -> {
            // Could not connect to the service
            showRetryButton()
        }
        ErrorCode.MODEL_UNAVAILABLE -> {
            // The requested model is not available
            fallbackToDefaultModel()
        }
        ErrorCode.OPERATION_TIMEOUT -> {
            // The operation took too long
            showTimeoutMessage()
        }
        else -> {
            // Other errors
            showGenericErrorMessage(e.message)
        }
    }
}
```

## Best Practices

### Memory Management

- Always call `disconnect()` when you're done with the client
- Use application-scoped instances for long-lived connections
- Consider using dependency injection to manage the client lifecycle

### Performance Optimization

- Reuse the client instance instead of creating new ones
- Use streaming APIs for better user experience with long responses
- Set appropriate timeouts based on your use case

### UI Integration

- Always update UI on the main thread
- Show loading indicators during operations
- Provide cancel options for long-running operations

## Troubleshooting

### Common Issues

1. **Service not found**
   - Ensure the AI Router Service is installed
   - Check that your app has the correct permissions

2. **Connection timeouts**
   - Increase the connection timeout
   - Check for network connectivity issues

3. **Operation failures**
   - Check logs for detailed error messages
   - Verify that the requested model is available

### Debugging

Enable debug logging for detailed information:

```kotlin
val routerClient = AIRouterClient.Builder(context)
    .setLogLevel(LogLevel.DEBUG)
    .build()
```

## API Reference

For a complete API reference, see the [API Documentation](../api-reference.md).

---

For more information or support, please [file an issue](https://github.com/your-org/BreezeApp/issues) or contact the development team. 