# AI Router API Reference

This document provides a comprehensive reference for the BreezeApp AI Router Client API.

## Table of Contents

- [Core Classes](#core-classes)
  - [AIRouterClient](#airouterclient)
  - [TextGenerationRequest](#textgenerationrequest)
  - [SpeechRecognitionRequest](#speechrecognitionrequest)
  - [TextResponse](#textresponse)
  - [SpeechRecognitionResult](#speechrecognitionresult)
- [Enums and Constants](#enums-and-constants)
  - [ConnectionState](#connectionstate)
  - [ErrorCode](#errorcode)
  - [LogLevel](#loglevel)
  - [FallbackStrategy](#fallbackstrategy)
- [Interfaces](#interfaces)
  - [ConnectionListener](#connectionlistener)
  - [ProgressListener](#progresslistener)
- [Exceptions](#exceptions)
  - [AIRouterException](#airouterexception)
- [Advanced Features](#advanced-features)
  - [ModelCapabilities](#modelcapabilities)
  - [AudioStream](#audiostream)

## Core Classes

### AIRouterClient

The main client class for interacting with the AI Router Service.

#### Constructor

```kotlin
// Use the Builder pattern to create an instance
val client = AIRouterClient.Builder(context)
    .setConnectionListener(listener)
    .setLogLevel(LogLevel.INFO)
    .build()
```

#### Methods

| Method | Description | Parameters | Return Type | Throws |
|--------|-------------|------------|------------|--------|
| `connect()` | Connects to the AI Router Service | None | `suspend Unit` | `AIRouterException` |
| `disconnect()` | Disconnects from the AI Router Service | None | `Unit` | None |
| `isConnected()` | Checks if connected to the service | None | `Boolean` | None |
| `generateText(prompt: String)` | Generates text from a prompt | `prompt`: The text prompt | `suspend TextResponse` | `AIRouterException` |
| `generateText(request: TextGenerationRequest)` | Generates text with custom parameters | `request`: The request configuration | `suspend TextResponse` | `AIRouterException` |
| `generateTextStream(prompt: String)` | Streams text generation results | `prompt`: The text prompt | `Flow<TextResponse>` | `AIRouterException` |
| `generateTextStream(request: TextGenerationRequest)` | Streams text with custom parameters | `request`: The request configuration | `Flow<TextResponse>` | `AIRouterException` |
| `recognizeSpeech(audioStream: AudioStream)` | Performs speech recognition | `audioStream`: Audio data stream | `Flow<SpeechRecognitionResult>` | `AIRouterException` |
| `recognizeSpeech(request: SpeechRecognitionRequest)` | Performs speech recognition with custom parameters | `request`: The request configuration | `Flow<SpeechRecognitionResult>` | `AIRouterException` |
| `getAvailableModels()` | Gets list of available AI models | None | `suspend List<ModelInfo>` | `AIRouterException` |
| `getServiceInfo()` | Gets information about the service | None | `suspend ServiceInfo` | `AIRouterException` |
| `cancelOperation(operationId: String)` | Cancels an ongoing operation | `operationId`: The ID of the operation to cancel | `suspend Boolean` | `AIRouterException` |

### TextGenerationRequest

Configuration for text generation requests.

#### Constructor

```kotlin
// Use the Builder pattern
val request = TextGenerationRequest.Builder("What is the capital of France?")
    .setModelId("gpt-3.5-turbo")
    .setTemperature(0.7f)
    .setMaxTokens(100)
    .build()
```

#### Properties

| Property | Type | Description | Default |
|----------|------|-------------|---------|
| `prompt` | `String` | The text prompt | Required |
| `modelId` | `String?` | Specific model to use | `null` (use default) |
| `temperature` | `Float` | Randomness of output (0.0-1.0) | `0.7f` |
| `maxTokens` | `Int` | Maximum response length | `1024` |
| `topP` | `Float` | Nucleus sampling parameter (0.0-1.0) | `0.9f` |
| `stopSequences` | `List<String>` | Sequences that stop generation | Empty list |
| `systemPrompt` | `String?` | System prompt for context | `null` |
| `operationId` | `String` | Unique operation identifier | Auto-generated UUID |
| `timeout` | `Long` | Operation timeout in milliseconds | `30000` (30 seconds) |

### SpeechRecognitionRequest

Configuration for speech recognition requests.

#### Constructor

```kotlin
// Use the Builder pattern
val request = SpeechRecognitionRequest.Builder(audioStream)
    .setModelId("whisper-small")
    .setLanguage("en-US")
    .setPartialResults(true)
    .build()
```

#### Properties

| Property | Type | Description | Default |
|----------|------|-------------|---------|
| `audioStream` | `AudioStream` | The audio data stream | Required |
| `modelId` | `String?` | Specific model to use | `null` (use default) |
| `language` | `String?` | Language code (e.g., "en-US") | `null` (auto-detect) |
| `partialResults` | `Boolean` | Whether to return partial results | `true` |
| `operationId` | `String` | Unique operation identifier | Auto-generated UUID |
| `timeout` | `Long` | Operation timeout in milliseconds | `60000` (60 seconds) |

### TextResponse

Response from text generation operations.

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `text` | `String` | The generated text |
| `isComplete` | `Boolean` | Whether the response is complete |
| `operationId` | `String` | The operation identifier |
| `modelId` | `String` | The model that generated the response |
| `timestamp` | `Long` | Timestamp of the response |
| `tokenCount` | `Int` | Number of tokens in the response |
| `metadata` | `Map<String, String>` | Additional metadata |

### SpeechRecognitionResult

Result from speech recognition operations.

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `text` | `String` | The recognized text |
| `isFinal` | `Boolean` | Whether this is a final result |
| `confidence` | `Float` | Confidence score (0.0-1.0) |
| `operationId` | `String` | The operation identifier |
| `modelId` | `String` | The model that performed recognition |
| `timestamp` | `Long` | Timestamp of the result |
| `metadata` | `Map<String, String>` | Additional metadata |

## Enums and Constants

### ConnectionState

Represents the current connection state with the AI Router Service.

```kotlin
enum class ConnectionState {
    CONNECTED,
    DISCONNECTED,
    CONNECTING,
    ERROR
}
```

### ErrorCode

Error codes for AI Router exceptions.

```kotlin
enum class ErrorCode {
    // Connection errors
    SERVICE_UNAVAILABLE,
    CONNECTION_ERROR,
    CONNECTION_TIMEOUT,
    
    // Operation errors
    OPERATION_FAILED,
    OPERATION_TIMEOUT,
    OPERATION_CANCELLED,
    
    // Model errors
    MODEL_UNAVAILABLE,
    MODEL_LOADING_FAILED,
    
    // Input errors
    INVALID_REQUEST,
    INVALID_PARAMETERS,
    
    // System errors
    INSUFFICIENT_RESOURCES,
    INTERNAL_ERROR,
    
    // Unknown error
    UNKNOWN_ERROR
}
```

### LogLevel

Controls the verbosity of client logging.

```kotlin
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    NONE
}
```

### FallbackStrategy

Defines the strategy for model selection and fallback.

```kotlin
enum class FallbackStrategy {
    LOCAL_FIRST,      // Try local models first, then cloud
    CLOUD_FIRST,      // Try cloud models first, then local
    PERFORMANCE_FIRST, // Select fastest available model
    QUALITY_FIRST,    // Select highest quality model
    NO_FALLBACK       // Only use specified model, no fallback
}
```

## Interfaces

### ConnectionListener

Interface for receiving connection state updates.

```kotlin
fun interface ConnectionListener {
    fun onConnectionStateChanged(state: ConnectionState)
}
```

### ProgressListener

Interface for receiving operation progress updates.

```kotlin
interface ProgressListener {
    fun onProgressUpdate(operationId: String, progress: Float, message: String?)
}
```

## Exceptions

### AIRouterException

Exception thrown for all AI Router related errors.

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `errorCode` | `ErrorCode` | The error code |
| `message` | `String?` | Human-readable error message |
| `cause` | `Throwable?` | The underlying cause |

## Advanced Features

### ModelCapabilities

Describes the capabilities of an AI model.

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `supportsTextGeneration` | `Boolean` | Whether the model supports text generation |
| `supportsSpeechRecognition` | `Boolean` | Whether the model supports speech recognition |
| `supportsImageAnalysis` | `Boolean` | Whether the model supports image analysis |
| `maxInputTokens` | `Int` | Maximum input tokens supported |
| `maxOutputTokens` | `Int` | Maximum output tokens supported |
| `supportedLanguages` | `List<String>` | List of supported language codes |

### AudioStream

Interface for streaming audio data to the service.

```kotlin
interface AudioStream {
    val sampleRate: Int
    val channels: Int
    val encoding: AudioEncoding
    
    fun start()
    fun stop()
    fun pause()
    fun resume()
    
    // Read audio data into the provided buffer
    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}
```

#### AudioEncoding

```kotlin
enum class AudioEncoding {
    PCM_16BIT,
    PCM_FLOAT,
    OPUS,
    AAC
}
```

## Usage Examples

### Basic Text Generation

```kotlin
// Initialize the client
val client = AIRouterClient.Builder(context).build()

// Connect to the service
lifecycleScope.launch {
    try {
        client.connect()
        
        // Generate text
        val response = client.generateText("What is the capital of France?")
        textView.text = response.text
        
    } catch (e: AIRouterException) {
        Log.e(TAG, "Error: ${e.message}", e)
    } finally {
        client.disconnect()
    }
}
```

### Streaming Text Generation

```kotlin
lifecycleScope.launch {
    try {
        client.connect()
        
        // Create a request with custom parameters
        val request = TextGenerationRequest.Builder("Tell me a story about a robot.")
            .setTemperature(0.8f)
            .setMaxTokens(500)
            .build()
        
        // Stream the results
        client.generateTextStream(request)
            .collect { response ->
                textView.append(response.text)
            }
            
    } catch (e: AIRouterException) {
        Log.e(TAG, "Error: ${e.message}", e)
    }
}
```

### Speech Recognition

```kotlin
// Create an audio recorder
val audioRecorder = AudioRecorder(context)

lifecycleScope.launch {
    try {
        client.connect()
        
        // Start recording
        audioRecorder.start()
        
        // Create a request with the audio stream
        val request = SpeechRecognitionRequest.Builder(audioRecorder)
            .setLanguage("en-US")
            .build()
        
        // Process the speech
        client.recognizeSpeech(request)
            .collect { result ->
                textView.text = result.text
                
                if (result.isFinal) {
                    audioRecorder.stop()
                }
            }
            
    } catch (e: AIRouterException) {
        Log.e(TAG, "Error: ${e.message}", e)
        audioRecorder.stop()
    }
}
```

## Error Handling Best Practices

```kotlin
try {
    client.connect()
    val response = client.generateText("What is the capital of France?")
    textView.text = response.text
} catch (e: AIRouterException) {
    when (e.errorCode) {
        ErrorCode.SERVICE_UNAVAILABLE -> {
            // Show service installation prompt
            showInstallServiceDialog()
        }
        ErrorCode.CONNECTION_ERROR, ErrorCode.CONNECTION_TIMEOUT -> {
            // Show retry button
            showRetryButton()
        }
        ErrorCode.MODEL_UNAVAILABLE -> {
            // Try with a different model
            tryWithDefaultModel()
        }
        ErrorCode.OPERATION_TIMEOUT -> {
            // Show timeout message
            showTimeoutMessage()
        }
        else -> {
            // Show generic error
            showErrorMessage(e.message ?: "Unknown error occurred")
        }
    }
}
``` 