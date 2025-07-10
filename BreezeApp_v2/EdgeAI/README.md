# `EdgeAI` API Reference

> **🚀 Future Integration: Publishing to Maven**
>
> To improve the integration experience, we plan to publish this `EdgeAI` library to a Maven repository (such as Maven Central or JitPack).
>
> Currently, you need to manually include this module in your project. In the near future, you will be able to add it as a simple Gradle dependency, making the setup process much faster and easier.
>
> ```kotlin
> // Example for future integration
> dependencies {
>     implementation("com.mtkresearch.breezeapp:edgeai:1.0.0")
> }
> ```

Welcome to the API reference for the `EdgeAI` module. This document provides a detailed overview of the data models used for communication between a client application and the `breeze-app-router` service.

The core principle of this API is **type safety**. We have moved away from loosely-typed `Map` objects to strongly-typed `Parcelable` sealed interfaces (`RequestPayload` and `ResponseMetadata`). This change eliminates a whole class of runtime errors, improves code completion, and makes the developer experience significantly better.

## Core Data Models

The communication revolves around two primary data classes: `AIRequest` and `AIResponse`.

### `AIRequest`

This is the object you send **to** the service. It acts as a wrapper containing routing information and a specific, type-safe payload.

```kotlin
@Parcelize
data class AIRequest(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val payload: RequestPayload,
    val apiVersion: Int = 1
) : Parcelable
```
- **`id`**: A unique ID for the request, generated automatically.
- **`sessionId`**: An ID for the conversation session, generated automatically.
- **`timestamp`**: The creation timestamp, generated automatically.
- **`payload`**: The most important field. This is a `sealed interface` that holds the specific data for your request. See `RequestPayload` section below.
- **`apiVersion`**: The API version you are targeting. Defaults to `1`.

### `AIResponse`

This is the object you receive **from** the service. It contains the result of the AI operation and type-safe metadata.

```kotlin
@Parcelize
data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean,
    val state: ResponseState,
    val metadata: ResponseMetadata? = null,
    val error: String? = null,
    val apiVersion: Int = 1
) : Parcelable
```
- **`requestId`**: The ID of the original `AIRequest` this response corresponds to.
- **`text`**: The primary text output from the model.
- **`isComplete`**: `true` if this is the final message for a request (especially in streaming scenarios).
- **`state`**: An `enum` indicating the current status (`PROCESSING`, `STREAMING`, `COMPLETED`, `ERROR`).
- **`metadata`**: An optional `sealed interface` containing detailed, type-safe metadata about the response. See `ResponseMetadata` section below.
- **`error`**: A non-null string if an error occurred.

---

## `RequestPayload`: Defining Your Request

To send a request, you must create an implementation of the `RequestPayload` sealed interface. This tells the router what kind of AI task you want to perform.

### `RequestPayload.TextChat`
For standard text generation (LLM).

**Parameters**:
- `prompt: String`: The main text input.
- `modelName: String?`: (Optional) A specific model to use.
- `temperature: Float?`: (Optional) Controls creativity (0.0-1.0).
- `maxTokens: Int?`: (Optional) The maximum length of the response.
- `streaming: Boolean`: `true` to receive the response as a stream of tokens; `false` for a single response. Defaults to `false`.

**Example**:
```kotlin
// Create a payload for a single-response chat
val chatPayload = RequestPayload.TextChat(prompt = "Tell me a joke about programming.")

// Create a payload for a streaming request
val streamingPayload = RequestPayload.TextChat(
    prompt = "Write a long story about a space explorer.",
    streaming = true,
    temperature = 0.8f
)

// Send it
val request = AIRequest(payload = chatPayload)
service.sendMessage(request)
```

### `RequestPayload.ImageAnalysis`
For analyzing an image, optionally with a text prompt (VLM).

**Parameters**:
- `image: ByteArray`: The image data.
- `prompt: String?`: (Optional) A question or instruction about the image.
- `modelName: String?`: (Optional) A specific model to use.
- `streaming: Boolean`: (Optional) `true` to stream the text response. Defaults to `false`.

**Example**:
```kotlin
val imageBytes: ByteArray = // ... get from file or camera ...
val imagePayload = RequestPayload.ImageAnalysis(
    image = imageBytes,
    prompt = "What is the main subject in this image?"
)
val request = AIRequest(payload = imagePayload)
service.sendMessage(request)
```

### `RequestPayload.AudioTranscription`
For converting speech to text (ASR).

**Parameters**:
- `audio: ByteArray`: The audio data.
- `language: String?`: (Optional) The language of the audio (e.g., "en-US").
- `modelName: String?`: (Optional) A specific model to use.
- `streaming: Boolean`: (Optional) `true` to get real-time transcription results. Defaults to `false`.

**Example**:
```kotlin
val audioBytes: ByteArray = // ... get from microphone or file ...
val asrPayload = RequestPayload.AudioTranscription(
    audio = audioBytes,
    language = "en-US"
)
val request = AIRequest(payload = asrPayload)
service.sendMessage(request)
```

### `RequestPayload.SpeechSynthesis`
For converting text to speech (TTS).

**Parameters**:
- `text: String`: The text to synthesize.
- `voiceId: String?`: (Optional) The specific voice to use.
- `speed: Float?`: (Optional) The speaking rate (1.0 is normal).
- `modelName: String?`: (Optional) A specific model to use.
- `streaming: Boolean`: (Optional) `true` to stream the audio data as it's generated. Defaults to `false`.

**Example**:
```kotlin
val ttsPayload = RequestPayload.SpeechSynthesis(
    text = "Hello, world! This is a test of the text-to-speech engine.",
    voiceId = "alloy"
)
val request = AIRequest(payload = ttsPayload)
service.sendMessage(request)
```

### `RequestPayload.ContentModeration`
For checking text against safety guidelines.

**Parameters**:
- `text: String`: The text to analyze.
- `checkType: String?`: (Optional) The specific type of check (e.g., "hate_speech", "pii").

**Example**:
```kotlin
val moderationPayload = RequestPayload.ContentModeration(
    text = "An example sentence to check for safety."
)
val request = AIRequest(payload = moderationPayload)
service.sendMessage(request)
```

---

## `ResponseMetadata`: Understanding the Response

The `metadata` field in `AIResponse` provides rich, type-safe information. You can check its type to get specific details.

### `ResponseMetadata.Standard`
This is the base metadata included in most other metadata types.
- `modelName: String`: The model that processed the request.
- `processingTimeMs: Long`: Total processing time.
- `backend: String?`: The hardware used (e.g., "CPU", "GPU").

### `ResponseMetadata.TextGeneration`
Specific to `TextChat` responses.
- `standard: Standard`: The base metadata.
- `tokenCount: Int`: The number of tokens in the response.

### `ResponseMetadata.AudioTranscription`
Specific to `AudioTranscription` responses.
- `standard: Standard`: The base metadata.
- `confidence: Float`: Transcription confidence score (0.0 to 1.0).
- `audioDurationMs: Long`: Duration of the processed audio.

### `ResponseMetadata.SpeechSynthesis`
Specific to `SpeechSynthesis` responses.
- `standard: Standard`: The base metadata.
- `audioDurationMs: Long`: Duration of the synthesized audio.
- `voiceId: String?`: The voice used.

**Example of Handling Metadata**:
```kotlin
// In your response listener
when (val metadata = response.metadata) {
    is ResponseMetadata.TextGeneration -> {
        Log.d(TAG, "LLM Response: ${metadata.tokenCount} tokens processed in ${metadata.standard.processingTimeMs} ms.")
    }
    is ResponseMetadata.AudioTranscription -> {
        Log.d(TAG, "ASR Response: Confidence is ${metadata.confidence}.")
    }
    is ResponseMetadata.Standard -> {
        // Fallback for other types
        Log.d(TAG, "Standard Response: Processed by ${metadata.modelName}.")
    }
    null -> {
        // No metadata provided
    }
}
```

---

## `Configuration` (Optional)

The `Configuration` object is an optional, advanced feature that can be used to initialize the router service with specific settings. **In most use cases, you do not need to create or send this object**, as the service will use a sensible default configuration.

It is primarily used by a privileged client application to remotely configure the service's behavior, such as setting default models, changing log levels, or specifying which AI runner backends to use for each task.

**Key Parameters**:
- `logLevel: Int`: Controls logging verbosity (0=OFF to 5=VERBOSE).
- `preferredRuntime: RuntimeBackend`: The preferred hardware backend (`AUTO`, `CPU`, `GPU`, `NPU`).
- `runnerConfigurations: Map<AITaskType, RunnerType>`: Maps a task (e.g., `TEXT_GENERATION`) to a specific runner (e.g., `MOCK`, `EXECUTORCH`, `ONNX`).
- `defaultModelName: String`: A fallback model name if not specified in a request.
- `timeoutMs: Long`: Default request timeout.
- `temperature: Float`: Default creativity setting for text generation.

For a full list of parameters and enums, please refer to the `Configuration.kt` source file.

---

## Error Handling

The EdgeAI SDK provides comprehensive error handling through a hierarchy of exception classes. All EdgeAI exceptions inherit from `EdgeAIException`, allowing you to catch all EdgeAI-related errors or handle specific error types.

### Exception Types

| Exception Type | Description | Common Scenarios |
| :--- | :--- | :--- |
| `InvalidInputException` | Input parameters are invalid or exceed limits | Text exceeds max length, null/empty required fields |
| `ModelNotFoundException` | Specified model does not exist or is not loaded | Incorrect model name, model loading failed |
| `ModelInferenceException` | Error during model inference | Out-of-memory, model internal errors |
| `AudioProcessingException` | Audio processing failure | Corrupted audio, unsupported format |
| `ResourceLimitException` | Insufficient system resources | GPU memory exhausted, CPU overload |
| `TimeoutException` | Operation timed out | Inference timeout, service unresponsive |
| `NotSupportedException` | Feature/parameter not supported | Model doesn't support parameter |
| `InternalErrorException` | Unexpected internal error | SDK bugs, unexpected failures |
| `ServiceConnectionException` | Service connection issues | SDK not initialized, service unavailable |

### Error Handling Examples

```kotlin
try {
    EdgeAI.chat(prompt = "Hello")
        .collect { response ->
            // Handle successful response
            println(response.text)
        }
} catch (e: InvalidInputException) {
    // Handle input validation errors
    Log.e(TAG, "Invalid input: ${e.message}")
} catch (e: ModelNotFoundException) {
    // Handle model not found
    Log.e(TAG, "Model not available: ${e.message}")
} catch (e: EdgeAIException) {
    // Handle any other EdgeAI errors
    Log.e(TAG, "EdgeAI error: ${e.message}")
}
```

### Error Response in Flow

For streaming APIs, errors can also be communicated through the response objects:

```kotlin
EdgeAI.chat(prompt = "Hello")
    .collect { response ->
        if (response.error != null) {
            // Handle error in response
            Log.e(TAG, "Response error: ${response.error}")
        } else {
            // Handle successful response
            println(response.text)
        }
    }
```

---

## EdgeAI SDK Upgrade Progress Tracker

We are actively upgrading the EdgeAI module from a simple contract layer to a full-featured SDK with high-level, intention-driven APIs. This table tracks our progress:

| Phase | Task | Status | Notes |
| :--- | :--- | :--- | :--- |
| **1. Foundation** | 建立 `EdgeAI.kt` 作為 SDK 進入點 | ✅ Complete | Built singleton object with `initialize` and `shutdown` methods. |
| | 在 `EdgeAI.kt` 中定義高階 API 函式簽名 | ✅ Complete | Updated to OpenAI-compatible APIs with proper request/response models. |
| | 建立具體的 Request/Response Models | ✅ Complete | Created `ChatCompletionRequest/Response`, `TTSRequest`, `ASRRequest/Response` following OpenAI specs. |
| | 定義錯誤處理體系 | ✅ Complete | Created comprehensive exception hierarchy in `EdgeAIExceptions.kt`. |
| **2. Implementation** | 實作 `initialize` 與 `shutdown` | ✅ Complete | Implemented `ServiceConnection` binding, listener registration, and cleanup logic. |
| | 實作 `chat` API | ✅ Complete | Implemented request conversion, AIDL calls, streaming support, and response conversion. |
| | 實作 `tts` API | ✅ Complete | Implemented TTS request/response handling with blocking audio stream return. |
| | 實作 `asr` API | ✅ Complete | Implemented ASR request/response with multiple format support and streaming. |
| | 建立使用範例與文檔 | ✅ Complete | Created `EdgeAIUsageExample.kt` with comprehensive usage examples and error handling patterns. |
| | 修復編譯問題 | ✅ Complete | Added Kotlin Coroutines dependency and fixed Parcelize annotations with @RawValue. |
| **3. Integration & Refactoring**| 更新 `breeze-app-router-client` | ⬜️ To-Do | Migrate Client App from direct AIDL usage to new `EdgeAI` SDK. |
| | 更新所有相關的開發者文件 | ⬜️ To-Do | Update `README`, API Reference, etc., to reflect new SDK usage patterns. |
| **4. Finalization** | 撰寫單元測試與儀器測試 | ⬜️ To-Do | Ensure SDK stability and reliability. |
| | 將 `EdgeAI` 發佈到 Maven | ⬜️ To-Do | Complete the final delivery goal. |

### New SDK Usage Preview

Once implementation is complete, developers will be able to use the EdgeAI SDK like this:

#### Simple Usage (Convenience Functions)

```kotlin
// Initialize once in Application.onCreate()
EdgeAI.initialize(this)

// Simple chat - most common use case
EdgeAI.chat(chatRequest(prompt = "Tell me a joke about programming."))
    .collect { response ->
        Log.d(TAG, "Chat: ${response.choices.first().message?.content}")
    }

// Simple TTS
val audioStream = EdgeAI.tts(ttsRequest(
    input = "Hello, world!",
    voice = "alloy"
))

// Simple ASR
EdgeAI.asr(asrRequest(audioBytes, language = "en"))
    .collect { response ->
        Log.d(TAG, "Transcription: ${response.text}")
    }
```

#### Advanced Usage (Full OpenAI Compatibility)

```kotlin
// Chat with conversation history using DSL
val messages = conversation {
    system("You are a helpful programming assistant.")
    user("What's the difference between val and var in Kotlin?")
    assistant("In Kotlin, `val` declares read-only properties while `var` declares mutable properties.")
    user("Can you give me an example?")
}

EdgeAI.chat(chatRequestWithHistory(
    messages = messages,
    temperature = 0.7f,
    stream = true
))
    .collect { response ->
        Log.d(TAG, "Chat: ${response.choices.first().delta?.content}")
    }

// TTS with advanced options
val ttsRequest = TTSRequest(
    input = "Hello, world!",
    model = "tts-1-hd",
    voice = "nova",
    speed = 1.2f,
    responseFormat = "wav"
)

val audioStream = EdgeAI.tts(ttsRequest)

// ASR with detailed options and word-level timestamps
EdgeAI.asr(asrRequestDetailed(
    audioBytes = audioBytes,
    language = "en",
    includeWordTimestamps = true,
    format = "verbose_json"
))
    .collect { response ->
        Log.d(TAG, "Transcription: ${response.text}")
        response.segments?.forEach { segment ->
            Log.d(TAG, "Segment: ${segment.text} (${segment.start}s - ${segment.end}s)")
            segment.words?.forEach { word ->
                Log.d(TAG, "  Word: ${word.word} (${word.start}s - ${word.end}s)")
            }
        }
    }
```
