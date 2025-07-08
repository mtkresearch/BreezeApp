# `shared-contracts` API Reference

Welcome to the API reference for the `shared-contracts` module. This document provides a detailed overview of the data models used for communication between a client application and the `breeze-app-router` service.

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
