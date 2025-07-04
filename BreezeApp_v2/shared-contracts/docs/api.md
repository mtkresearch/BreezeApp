# BreezeApp Shared Contracts API Documentation

This document provides detailed information about the shared contract interfaces and data models used for communication between a client application and the AI Router Service.

## Overview

The `shared-contracts` module defines the communication protocol, which consists of:

1.  **AIDL Interfaces**: Define the methods for inter-process communication (IPC).
2.  **Data Models**: Strongly-typed, `Parcelable` objects that are passed between processes.

The core principle of this architecture is **type safety**. Instead of relying on error-prone `Map<String, String>` for passing parameters, we use `sealed interface`s (`RequestPayload`, `ResponseMetadata`) to ensure that all data is structured, explicit, and validated at compile time.

## API Versioning

The API uses an `apiVersion` field in both `AIRequest` and `AIResponse` to ensure compatibility between the client and service. Clients should check the service's API version to handle any potential feature differences.

## AIDL Interfaces

### IAIRouterService

The main interface implemented by the AI Router Service.

```java
interface IAIRouterService {
    int getApiVersion();
    void initialize(in Configuration config);
    void sendMessage(in AIRequest request);
    boolean cancelRequest(String requestId);
    void registerListener(IAIRouterListener listener);
    void unregisterListener(IAIRouterListener listener);
}
```

#### Methods

| Method               | Description                                                                                             |
| -------------------- | ------------------------------------------------------------------------------------------------------- |
| `getApiVersion`      | Returns the API version supported by the service.                                                       |
| `initialize`         | Initializes the service with configuration settings. Must be called before other methods.               |
| `sendMessage`        | Sends an `AIRequest` to the service for processing. Responses are sent via the `IAIRouterListener`.     |
| `cancelRequest`      | Cancels an in-progress request by its ID.                                                               |
| `registerListener`   | Registers a callback listener to receive responses.                                                     |
| `unregisterListener` | Unregisters a previously registered listener.                                                           |

### IAIRouterListener

The callback interface implemented by the client to receive responses.

```java
oneway interface IAIRouterListener {
    void onResponse(in AIResponse response);
}
```

#### Methods

| Method       | Description                                                                              |
| ------------ | ---------------------------------------------------------------------------------------- |
| `onResponse` | Called when the service has a response. May be called multiple times for streaming data. |

## Core Data Models

### Configuration

Configuration settings for initializing the AI Router Service. This object is passed once during the `initialize()` call.

```kotlin
@Parcelize
data class Configuration(
    val apiVersion: Int = 1,
    val logLevel: Int = 0,
    val preferredRuntime: RuntimeBackend = RuntimeBackend.AUTO,
    val runnerConfigurations: Map<AITaskType, RunnerType> = emptyMap(),
    val defaultModelName: String = "",
    val languagePreference: String = "en",
    val timeoutMs: Long = 30000,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val additionalSettings: Map<String, String> = emptyMap()
) : Parcelable
```

#### Enums for Configuration

##### `RuntimeBackend`
Specifies the preferred hardware backend for processing.
`AUTO`, `CPU`, `GPU`, `NPU`

##### `AITaskType`
Defines the types of AI tasks the service can perform.
`TEXT_GENERATION`, `IMAGE_ANALYSIS`, `SPEECH_RECOGNITION`, `SPEECH_SYNTHESIS`, `CONTENT_MODERATION`

##### `RunnerType`
Specifies which runner (engine implementation) to use for a task.
`DEFAULT`, `MOCK`, `EXECUTORCH`, `ONNX`, `MTK_APU`, `LLAMA_CPP`, `SYSTEM`

---

### AIRequest

Represents a request sent from a client to the AI Router Service. It uses a strongly-typed `payload` to define the specific task.

```kotlin
@Parcelize
data class AIRequest(
    val id: String,
    val sessionId: String,
    val timestamp: Long,
    val payload: RequestPayload,
    val apiVersion: Int = 1
) : Parcelable
```

#### Properties

| Property    | Type             | Description                                                  |
| ----------- | ---------------- | ------------------------------------------------------------ |
| `id`        | `String`         | A unique identifier for the request.                         |
| `sessionId` | `String`         | An identifier for the conversation session.                  |
| `timestamp` | `Long`           | The Unix timestamp when the request was created.             |
| `payload`   | `RequestPayload` | The specific, type-safe payload defining the request task.   |
| `apiVersion`| `Int`            | The API version used by the client.                          |

---

### RequestPayload Sealed Interface

The `RequestPayload` is a `sealed interface` that ensures all request types are well-defined and type-safe. Each data class below represents a specific type of AI task.

```kotlin
@Parcelize
sealed interface RequestPayload : Parcelable {
    // See subtypes below
}
```

#### Subtypes

##### 1. `TextChat`
Payload for a text-based chat or instruction-following task.
```kotlin
@Parcelize
data class TextChat(
    val prompt: String,
    val modelName: String? = null,
    val temperature: Float? = null,
    val maxTokens: Int? = null
) : RequestPayload
```
-   `prompt`: The primary text input.
-   `modelName`: (Optional) The specific model to target.
-   `temperature`: (Optional) Controls the creativity of the response (e.g., 0.0 to 1.0).
-   `maxTokens`: (Optional) The maximum number of tokens for the response.

##### 2. `ImageAnalysis`
Payload for an image analysis task.
```kotlin
@Parcelize
data class ImageAnalysis(
    val image: ByteArray,
    val prompt: String? = null,
    val modelName: String? = null
) : RequestPayload
```
-   `image`: The image data as a byte array.
-   `prompt`: (Optional) A question or instruction related to the image.
-   `modelName`: (Optional) The specific model to target.

##### 3. `AudioTranscription`
Payload for an audio transcription task (ASR).
```kotlin
@Parcelize
data class AudioTranscription(
    val audio: ByteArray,
    val language: String? = null,
    val modelName: String? = null
) : RequestPayload
```
-   `audio`: The audio data as a byte array.
-   `language`: (Optional) The language of the audio (e.g., "en-US").
-   `modelName`: (Optional) The specific model to target.

##### 4. `SpeechSynthesis`
Payload for a text-to-speech (TTS) task.
```kotlin
@Parcelize
data class SpeechSynthesis(
    val text: String,
    val voiceId: String? = null,
    val speed: Float? = null
) : RequestPayload
```
-   `text`: The text to be converted to speech.
-   `voiceId`: (Optional) The ID of the voice to use.
-   `speed`: (Optional) The speaking rate (e.g., 1.0 for normal).

##### 5. `ContentModeration`
Payload for a content moderation or safety check.
```kotlin
@Parcelize
data class ContentModeration(
    val text: String,
    val checkType: String? = null
) : RequestPayload
```
-   `text`: The text to be analyzed.
-   `checkType`: (Optional) The specific type of safety check to perform.

---

### AIResponse

Represents a response from the AI Router Service. It includes the response data and optional, strongly-typed `metadata`.

```kotlin
@Parcelize
@TypeParceler<AIResponse.ResponseState, ResponseStateParceler>()
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

#### Properties

| Property     | Type                | Description                                                          |
| ------------ | ------------------- | -------------------------------------------------------------------- |
| `requestId`  | `String`            | The ID of the original request this response corresponds to.         |
| `text`       | `String`            | The text content of the response.                                    |
| `isComplete` | `Boolean`           | `true` if this is the final response for the request.                |
| `state`      | `ResponseState`     | The current processing state of the response.                        |
| `metadata`   | `ResponseMetadata?` | (Optional) Type-safe metadata about the response.                    |
| `error`      | `String?`           | (Optional) An error message if processing failed.                    |
| `apiVersion` | `Int`               | The API version used by the service.                                 |

#### ResponseState Enum

| Value        | Description                                       |
| ------------ | ------------------------------------------------- |
| `PROCESSING` | The service is processing the request.            |
| `STREAMING`  | The service is streaming back a partial response. |
| `COMPLETED`  | The service has completed the request successfully. |
| `ERROR`      | An error occurred during processing.              |

---

### ResponseMetadata Sealed Interface

The `ResponseMetadata` is a `sealed interface` that provides type-safe, detailed information about the response.

```kotlin
@Parcelize
sealed interface ResponseMetadata : Parcelable {
    // See subtypes below
}
```

#### Subtypes

##### 1. `Standard`
Standard metadata included in most responses.
```kotlin
@Parcelize
data class Standard(
    val modelName: String,
    val processingTimeMs: Long,
    val backend: String? = null
) : ResponseMetadata
```
-   `modelName`: The model that processed the request.
-   `processingTimeMs`: The time taken for inference in milliseconds.
-   `backend`: (Optional) The hardware backend used (e.g., "CPU", "GPU", "NPU").

##### 2. `TextGeneration`
Metadata specific to text generation responses. It nests the `Standard` metadata.
```kotlin
@Parcelize
data class TextGeneration(
    val standard: Standard,
    val tokenCount: Int
) : ResponseMetadata
```
-   `standard`: The common `Standard` metadata.
-   `tokenCount`: The number of tokens in the generated response.

##### 3. `AudioTranscription`
Metadata specific to audio transcription responses.
```kotlin
@Parcelize
data class AudioTranscription(
    val standard: Standard,
    val confidence: Float,
    val audioDurationMs: Long
) : ResponseMetadata
```
-   `standard`: The common `Standard` metadata.
-   `confidence`: The confidence score of the transcription (0.0 to 1.0).
-   `audioDurationMs`: The duration of the processed audio in milliseconds.

##### 4. `SpeechSynthesis`
Metadata specific to text-to-speech responses.
```kotlin
@Parcelize
data class SpeechSynthesis(
    val standard: Standard,
    val audioDurationMs: Long,
    val voiceId: String?
) : ResponseMetadata
```
-   `standard`: The common `Standard` metadata.
-   `audioDurationMs`: The duration of the synthesized audio in milliseconds.
-   `voiceId`: (Optional) The voice ID used for synthesis.
