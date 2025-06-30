# BreezeApp Shared Contracts API Documentation

This document provides detailed information about the shared contract interfaces and data models used for communication between the BreezeApp UI application and the AI Router Service.

## Overview

The shared contracts module defines the communication protocol between the UI application (`breeze-app-ui`) and the AI Router Service (`breeze-app-router`). It consists of:

1. **AIDL Interfaces**: Define the methods for inter-process communication
2. **Data Models**: Parcelable objects that can be passed between processes

## API Versioning

The API uses explicit versioning to ensure compatibility between the client and service. Each major component includes an `apiVersion` field:

- The `Configuration` class includes the client's requested API version
- The `AIRequest` class includes the client's API version for each request
- The `AIResponse` class includes the service's API version for each response
- The `IAIRouterService` interface provides a `getApiVersion()` method to query the service's supported version

Clients should check the service's API version before proceeding with other calls to ensure compatibility. If the versions are incompatible, clients should either adapt to the service's capabilities or notify the user of the incompatibility.

## AIDL Interfaces

### IAIRouterService

The main interface implemented by the AI Router Service to handle requests from the UI application.

```java
interface IAIRouterService {
    int getApiVersion();
    void initialize(in Configuration config);
    void sendMessage(in AIRequest request);
    boolean cancelRequest(String requestId);
    void registerListener(IAIRouterListener listener);
    void unregisterListener(IAIRouterListener listener);
    boolean hasCapability(String capabilityName);
}
```

#### Methods

| Method | Description | Parameters | Return Value |
|--------|-------------|------------|--------------|
| `getApiVersion` | Returns the API version supported by the service | None | `int`: The API version number |
| `initialize` | Initializes the service with configuration settings. This should be called before any other methods. | `Configuration config`: Configuration settings for the service | `void` |
| `sendMessage` | Sends a message request to the AI service for processing | `AIRequest request`: The request containing the message to process | `void` |
| `cancelRequest` | Cancels an in-progress request | `String requestId`: The ID of the request to cancel | `boolean`: True if successfully canceled, false otherwise |
| `registerListener` | Registers a callback listener to receive responses | `IAIRouterListener listener`: The callback interface implementation | `void` |
| `unregisterListener` | Unregisters a previously registered listener | `IAIRouterListener listener`: The listener to unregister | `void` |
| `hasCapability` | Checks if the service supports a specific capability | `String capabilityName`: The name of the capability to check | `boolean`: True if supported, false otherwise |

### IAIRouterListener

Callback interface implemented by the UI application to receive responses from the AI Router Service.

```java
oneway interface IAIRouterListener {
    void onResponse(in AIResponse response);
}
```

#### Methods

| Method | Description | Parameters | Return Value |
|--------|-------------|------------|--------------|
| `onResponse` | Called when the service has a response to a request. May be called multiple times for streaming responses. | `AIResponse response`: The response from the AI service | `void` |

## Data Models

### Configuration

Configuration settings for initializing the AI Router Service.

```kotlin
@Parcelize
data class Configuration(
    val apiVersion: Int = 1,
    val logLevel: Int = 0,
    val preferredRuntime: RuntimeBackend = RuntimeBackend.AUTO,
    val runnerConfigurations: Map<AITaskType, RunnerType> = mapOf(
        AITaskType.TEXT_GENERATION to RunnerType.DEFAULT,
        AITaskType.IMAGE_ANALYSIS to RunnerType.DEFAULT,
        AITaskType.SPEECH_RECOGNITION to RunnerType.DEFAULT
    ),
    val defaultModelName: String = "",
    val languagePreference: String = "en",
    val timeoutMs: Long = 30000,
    val maxTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val additionalSettings: Map<String, String> = emptyMap()
) : Parcelable
```

#### Properties

| Property | Type | Description | Default Value |
|----------|------|-------------|---------------|
| `apiVersion` | `Int` | The API version to use | `1` |
| `logLevel` | `Int` | Controls the verbosity of logging (0=OFF, 1=ERROR, 2=WARN, 3=INFO, 4=DEBUG, 5=VERBOSE) | `0` |
| `preferredRuntime` | `RuntimeBackend` | The preferred hardware backend to use (AUTO, CPU, GPU, NPU) | `AUTO` |
| `runnerConfigurations` | `Map<AITaskType, RunnerType>` | Specifies which runner implementation to use for each AI task type | Default runners |
| `defaultModelName` | `String` | The default model to use if not specified in requests | `""` |
| `languagePreference` | `String` | The preferred language for responses | `"en"` |
| `timeoutMs` | `Long` | Request timeout in milliseconds | `30000` |
| `maxTokens` | `Int` | Maximum number of tokens to generate in responses | `1024` |
| `temperature` | `Float` | Controls randomness in generation (0.0-1.0) | `0.7f` |
| `additionalSettings` | `Map<String, String>` | Additional configuration settings | `emptyMap()` |

#### RuntimeBackend Enum

| Value | Description |
|-------|-------------|
| `AUTO` | Automatically select the best available runtime |
| `CPU` | Force CPU execution |
| `GPU` | Use GPU acceleration if available |
| `NPU` | Use Neural Processing Unit if available |

#### AITaskType Enum

| Value | Description |
|-------|-------------|
| `TEXT_GENERATION` | Text generation (chat, completion) |
| `IMAGE_ANALYSIS` | Image analysis (vision) |
| `SPEECH_RECOGNITION` | Speech recognition (ASR) |
| `SPEECH_SYNTHESIS` | Text-to-speech synthesis |
| `CONTENT_MODERATION` | Content moderation |

#### RunnerType Enum

| Value | Description |
|-------|-------------|
| `DEFAULT` | Use the default runner for the task |
| `MOCK` | Use a mock runner that returns predefined responses |
| `EXECUTORCH` | Use ExecuTorch-based runner |
| `ONNX` | Use ONNX Runtime-based runner |
| `MTK_APU` | Use MediaTek APU-optimized runner |
| `LLAMA_CPP` | Use llama.cpp-based runner |
| `SYSTEM` | Use system APIs where available |

#### Common Setting Keys

| Key | Description |
|-----|-------------|
| `ENABLE_LOGGING` | Whether to enable logging |
| `CACHE_MODELS` | Whether to cache models in memory |
| `MAX_CACHE_SIZE_MB` | Maximum cache size in megabytes |
| `ENABLE_STREAMING` | Whether to enable streaming responses |
| `ENABLE_GUARDRAILS` | Whether to enable content safety checks |
| `MODEL_PATH_PREFIX` | Prefix for model path settings (e.g., `model_path_text_generation`) |
| `RUNNER_CONFIG_PREFIX` | Prefix for runner-specific configuration (e.g., `runner_config_executorch`) |

### AIRequest

Represents a request sent from the UI application to the AI Router Service.

```kotlin
@Parcelize
data class AIRequest(
    val id: String,
    val text: String,
    val sessionId: String,
    val timestamp: Long,
    val apiVersion: Int = 1,
    val binaryAttachments: List<BinaryData> = emptyList(),
    val options: Map<String, String> = emptyMap()
) : Parcelable
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `id` | `String` | Unique identifier for the request |
| `text` | `String` | The text content of the request |
| `sessionId` | `String` | Identifier for the conversation session |
| `timestamp` | `Long` | Unix timestamp when the request was created |
| `apiVersion` | `Int` | The version of the API being used by the client |
| `binaryAttachments` | `List<BinaryData>` | Optional binary data attachments (images, audio, etc.) |
| `options` | `Map<String, String>` | Additional request-specific options |

#### Request Types

| Value | Description |
|-------|-------------|
| `TEXT_CHAT` | Standard text chat request |
| `IMAGE_ANALYSIS` | Request to analyze an image |
| `AUDIO_TRANSCRIPTION` | Request to transcribe audio |
| `MULTIMODAL` | Request involving multiple modalities (text, image, etc.) |

#### Common Option Keys

| Key | Description |
|-----|-------------|
| `REQUEST_TYPE` | The type of request (see Request Types) |
| `MODEL_NAME` | The specific model to use for this request |
| `MAX_TOKENS` | Maximum tokens to generate for this request |
| `TEMPERATURE` | Temperature setting for this request |
| `LANGUAGE` | Language preference for this request |

### AIResponse

Represents a response from the AI Router Service to the UI application.

```kotlin
@Parcelize
data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean,
    val state: ResponseState,
    val apiVersion: Int = 1,
    val binaryAttachments: List<BinaryData> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val error: String? = null
) : Parcelable
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `requestId` | `String` | ID of the original request this response corresponds to |
| `text` | `String` | The text content of the response |
| `isComplete` | `Boolean` | Whether this is the final response for the request |
| `state` | `ResponseState` | Current state of the response processing |
| `apiVersion` | `Int` | The version of the API used by the service |
| `binaryAttachments` | `List<BinaryData>` | Optional binary data attachments (images, audio, etc.) |
| `metadata` | `Map<String, String>` | Additional response metadata |
| `error` | `String?` | Optional error message if processing failed |

#### ResponseState Enum

| Value | Description |
|-------|-------------|
| `PROCESSING` | The service is processing the request |
| `STREAMING` | The service is streaming back a response |
| `COMPLETED` | The service has completed the request successfully |
| `ERROR` | An error occurred while processing the request |

#### Common Metadata Keys

| Key | Description |
|-----|-------------|
| `MODEL_NAME` | The model used to generate the response |
| `PROCESSING_TIME_MS` | Time taken to process the request in milliseconds |
| `TOKEN_COUNT` | Number of tokens in the response |
| `RUNTIME_BACKEND` | The runtime backend used (CPU, GPU, NPU) |

### BinaryData

Represents binary data that can be transferred between processes.

```kotlin
@Parcelize
data class BinaryData(
    val type: String,
    val data: ByteArray,
    val metadata: Map<String, String> = emptyMap()
) : Parcelable
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `type` | `String` | The type of binary data (e.g., "image/jpeg", "audio/wav") |
| `data` | `ByteArray` | The actual binary content |
| `metadata` | `Map<String, String>` | Additional information about the binary data |

#### Content Types

| Constant | Value | Description |
|----------|-------|-------------|
| `IMAGE_JPEG` | `"image/jpeg"` | JPEG image format |
| `IMAGE_PNG` | `"image/png"` | PNG image format |
| `AUDIO_WAV` | `"audio/wav"` | WAV audio format |
| `AUDIO_MP3` | `"audio/mp3"` | MP3 audio format |
| `GENERIC_BINARY` | `"application/octet-stream"` | Generic binary data |

## Usage Examples

### Checking API Version and Capabilities

```kotlin
// In the UI application
val serviceApiVersion = aiRouterService.getApiVersion()
if (serviceApiVersion < 2) {
    // Handle older API version
    Log.w(TAG, "Service API version $serviceApiVersion is older than expected (2)")
}

// Check for specific capabilities
val supportsBinaryData = aiRouterService.hasCapability("binary_data")
val supportsImageProcessing = aiRouterService.hasCapability("image_processing")
```

### Initializing the Service with Enhanced Configuration

```kotlin
// In the UI application
val configuration = Configuration(
    apiVersion = 2,
    logLevel = 3, // INFO level
    preferredRuntime = Configuration.RuntimeBackend.GPU,
    runnerConfigurations = mapOf(
        Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.EXECUTORCH,
        Configuration.AITaskType.IMAGE_ANALYSIS to Configuration.RunnerType.ONNX,
        Configuration.AITaskType.SPEECH_RECOGNITION to Configuration.RunnerType.SYSTEM
    ),
    defaultModelName = "gpt-3.5-turbo",
    languagePreference = "zh-TW",
    timeoutMs = 45000,
    maxTokens = 2048,
    temperature = 0.8f,
    additionalSettings = mapOf(
        Configuration.SettingKeys.ENABLE_LOGGING to "true",
        Configuration.SettingKeys.CACHE_MODELS to "true",
        Configuration.SettingKeys.MODEL_PATH_PREFIX + "text_generation" to "/data/local/tmp/models/llm.bin",
        Configuration.SettingKeys.RUNNER_CONFIG_PREFIX + "executorch" to "optimize_for_mobile=true"
    )
)
aiRouterService.initialize(configuration)
```

### Sending a Text Request

```kotlin
// In the UI application
val request = AIRequest(
    id = UUID.randomUUID().toString(),
    text = "Tell me about BreezeApp",
    sessionId = "user-session-123",
    timestamp = System.currentTimeMillis(),
    apiVersion = 2,
    options = mapOf(
        AIRequest.OptionKeys.REQUEST_TYPE to AIRequest.RequestType.TEXT_CHAT,
        AIRequest.OptionKeys.MODEL_NAME to "gpt-4"
    )
)
aiRouterService.sendMessage(request)
```

### Sending an Image Analysis Request

```kotlin
// In the UI application
// Load image from a file or resource
val bitmap = BitmapFactory.decodeResource(resources, R.drawable.my_image)
val outputStream = ByteArrayOutputStream()
bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
val imageBytes = outputStream.toByteArray()

val imageData = BinaryData(
    type = BinaryData.ContentType.IMAGE_JPEG,
    data = imageBytes,
    metadata = mapOf(
        "width" to bitmap.width.toString(),
        "height" to bitmap.height.toString()
    )
)

val request = AIRequest(
    id = UUID.randomUUID().toString(),
    text = "What's in this image?",
    sessionId = "user-session-123",
    timestamp = System.currentTimeMillis(),
    apiVersion = 2,
    binaryAttachments = listOf(imageData),
    options = mapOf(
        AIRequest.OptionKeys.REQUEST_TYPE to AIRequest.RequestType.IMAGE_ANALYSIS,
        AIRequest.OptionKeys.MODEL_NAME to "vision-model"
    )
)
aiRouterService.sendMessage(request)
```

### Receiving Responses with Binary Data

```kotlin
// In the UI application
val listener = object : IAIRouterListener.Stub() {
    override fun onResponse(response: AIResponse) {
        // Handle text response
        when (response.state) {
            ResponseState.STREAMING -> {
                updateChatWithPartialResponse(response.text)
            }
            ResponseState.COMPLETED -> {
                finalizeChatResponse(response.text)
                
                // Check for binary attachments
                if (response.binaryAttachments.isNotEmpty()) {
                    for (attachment in response.binaryAttachments) {
                        when (attachment.type) {
                            BinaryData.ContentType.IMAGE_JPEG, 
                            BinaryData.ContentType.IMAGE_PNG -> {
                                // Display the image
                                val bitmap = BitmapFactory.decodeByteArray(
                                    attachment.data, 0, attachment.data.size)
                                displayImage(bitmap)
                            }
                            BinaryData.ContentType.AUDIO_WAV,
                            BinaryData.ContentType.AUDIO_MP3 -> {
                                // Play the audio
                                playAudio(attachment.data)
                            }
                        }
                    }
                }
                
                // Check for metadata
                if (response.metadata.isNotEmpty()) {
                    val modelName = response.metadata[AIResponse.MetadataKeys.MODEL_NAME]
                    val processingTime = response.metadata[AIResponse.MetadataKeys.PROCESSING_TIME_MS]
                    Log.d(TAG, "Response from model $modelName took $processingTime ms")
                }
            }
            ResponseState.ERROR -> {
                showErrorMessage(response.error ?: "Unknown error")
            }
            else -> {
                // Handle other states
            }
        }
    }
}

// Register the listener
aiRouterService.registerListener(listener)
```

### Canceling a Request

```kotlin
// In the UI application
val requestId = "request-123"
val canceled = aiRouterService.cancelRequest(requestId)
if (canceled) {
    Log.d(TAG, "Request $requestId was canceled successfully")
} else {
    Log.w(TAG, "Failed to cancel request $requestId")
}
```

## Runner Selection and Configuration

The `runnerConfigurations` map in the `Configuration` class allows clients to specify which implementation to use for each AI task type. This provides flexibility in choosing the most appropriate backend for each task based on the device capabilities and requirements.

### Selecting Runners

```kotlin
// Select ExecuTorch for text generation and ONNX for image analysis
val runnerConfig = mapOf(
    Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.EXECUTORCH,
    Configuration.AITaskType.IMAGE_ANALYSIS to Configuration.RunnerType.ONNX
)

// Any unspecified task types will use the DEFAULT runner
val config = Configuration(
    runnerConfigurations = runnerConfig,
    // Other settings...
)
```

### Runner-Specific Configuration

Runner-specific settings can be passed via the `additionalSettings` map using the `RUNNER_CONFIG_PREFIX`:

```kotlin
val additionalSettings = mapOf(
    // ExecuTorch-specific settings
    Configuration.SettingKeys.RUNNER_CONFIG_PREFIX + "executorch" to "optimize_for_mobile=true",
    Configuration.SettingKeys.RUNNER_CONFIG_PREFIX + "executorch" + "_threads" to "4",
    
    // ONNX-specific settings
    Configuration.SettingKeys.RUNNER_CONFIG_PREFIX + "onnx" + "_provider" to "cpu",
    
    // Model paths for specific tasks
    Configuration.SettingKeys.MODEL_PATH_PREFIX + "text_generation" to "/data/local/tmp/models/llm.bin",
    Configuration.SettingKeys.MODEL_PATH_PREFIX + "image_analysis" to "/data/local/tmp/models/vision.onnx"
)
```

### Runner Selection Strategy

The service follows this strategy when selecting a runner for a task:

1. Use the runner specified in `runnerConfigurations` for the task type
2. If not specified, check if the request has a specific runner in its options
3. If not specified in the request, use the DEFAULT runner for that task type
4. If the requested runner is not available, fall back to a compatible runner

## Error Handling

The `AIResponse` object includes an `error` field that will be populated with an error message when the `state` is `ERROR`. Clients should always check the `state` field before processing the response content.

Common error scenarios:

1. **Version incompatibility**: The client and service API versions are incompatible
2. **Missing capabilities**: The client requests a capability the service doesn't support
3. **Invalid request**: The request contains invalid or unsupported parameters
4. **Processing error**: An error occurred during request processing
5. **Timeout**: The request processing exceeded the configured timeout
6. **Runner not available**: The requested runner implementation is not available

## Thread Safety

All AIDL interface methods are potentially called from different threads. Implementations should ensure thread safety when handling these calls.

## Binary Data Handling

When working with binary data:

1. **Size limitations**: Large binary data may cause performance issues or even transaction failures. Keep binary data under 1MB when possible.
2. **Memory management**: Release binary data as soon as it's no longer needed to avoid memory leaks.
3. **Type checking**: Always check the `type` field of `BinaryData` before processing to ensure proper handling.

## Versioning

This API is currently in version 2.0. Future versions will maintain backward compatibility where possible.

### Version History

| Version | Changes |
|---------|---------|
| 1.0 | Initial release with basic text request/response |
| 2.0 | Added binary data support, enhanced configuration, and versioning |
| 2.1 | Added runner configuration support |

## Capabilities

The service may support various capabilities that can be queried using the `hasCapability` method:

| Capability | Description |
|------------|-------------|
| `binary_data` | Support for binary data transfer |
| `streaming` | Support for streaming responses |
| `image_processing` | Support for image analysis |
| `audio_processing` | Support for audio processing |
| `executorch_runner` | Support for ExecuTorch runner |
| `onnx_runner` | Support for ONNX Runtime runner |
| `mtk_apu_runner` | Support for MediaTek APU runner | 