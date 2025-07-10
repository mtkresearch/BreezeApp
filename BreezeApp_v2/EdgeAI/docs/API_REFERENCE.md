# 🔧 EdgeAI SDK API Reference

This document provides a detailed reference for all public APIs available in the EdgeAI SDK, including Chat, Text-to-Speech (TTS), and Automatic Speech Recognition (ASR).

## Table of Contents

1.  [**SDK Lifecycle & Utilities**](#1-sdk-lifecycle--utilities)
2.  [**Chat Completion API (`EdgeAI.chat`)**](#2-chat-completion-api-edgaichat)
3.  [**Text-to-Speech API (`EdgeAI.tts`)**](#3-text-to-speech-api-edgaitts)
4.  [**Automatic Speech Recognition API (`EdgeAI.asr`)**](#4-automatic-speech-recognition-api-edgaiasr)

---

## 1. SDK Lifecycle & Utilities

Properly managing the SDK's lifecycle is crucial for a stable application.

### `initializeAndWait(context: Context)`
Asynchronously initializes the SDK. This method suspends execution until a connection with the `BreezeApp Router` service is successfully established. This is the recommended way to start the SDK.

**Example:**
```kotlin
viewModelScope.launch {
    try {
        EdgeAI.initializeAndWait(applicationContext)
        // The SDK is now ready for use.
        Log.i("MyApp", "EdgeAI SDK initialized successfully.")
    } catch (e: ServiceConnectionException) {
        // Handle initialization failure, e.g., prompt user to install the router app.
        Log.e("MyApp", "Failed to initialize EdgeAI SDK", e)
    }
}
```

### `shutdown()`
Disconnects from the service and releases all resources used by the SDK. Call this method when the SDK is no longer needed, such as in a ViewModel's `onCleared()` method or an Activity's `onDestroy()`, to prevent resource leaks.

**Example:**
```kotlin
override fun onCleared() {
    super.onCleared()
    EdgeAI.shutdown()
    Log.i("MyApp", "EdgeAI SDK has been shut down.")
}
```

### `isReady(): Boolean`
Returns `true` if the SDK is initialized and connected to the service, and `false` otherwise. This is useful for checking the connection status before making an API call, although each API call has its own internal validation.

**Example:**
```kotlin
fun onSendRequestClicked() {
    if (EdgeAI.isReady()) {
        // Proceed with the API call
    } else {
        // Notify the user that the service is not available
        Toast.makeText(context, "AI Service is not ready.", Toast.LENGTH_SHORT).show()
    }
}
```

---

## 2. Chat Completion API (`EdgeAI.chat`)

Generate the next message in a chat with a provided model.

### 2.1 Request (`ChatRequest`)

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `messages` | `List<ChatMessage>` | Yes | - | Array of chat messages. Each must have a role and content. |
| `model` | `String` | Yes | - | The model ID to use for generating responses. |
| `frequencyPenalty`| `Float?` | No | `0f` | Range: -2.0 to 2.0. Positive values penalize new tokens based on their existing frequency. |
| `logitBias` | `Map<String, Float>?` | No | `null` | Adjusts the probability of specific tokens. Maps token IDs to bias values (-100 to 100). |
| `logprobs` | `Boolean?` | No | `false` | Whether to return log probabilities of output tokens. |
| `topLogprobs` | `Int?` | No | `null` | Range: 0 to 20. Specifies the number of most likely tokens to return at each position. |
| `maxCompletionTokens` | `Int?` | No | `null` | The maximum number of tokens to generate in the chat completion. |
| `n` | `Int?` | No | `1` | Number of chat completion choices to generate for each input message. |
| `presencePenalty` | `Float?` | No | `0f` | Range: -2.0 to 2.0. Positive values penalize new tokens based on whether they appear in the text so far. |
| `responseFormat`| `ResponseFormat?`| No | `null` | An object specifying the format that the model must output. E.g. `{"type": "json_object"}`. |
| `seed` | `Int?` | No | `null` | If specified, the system will make a best effort to sample deterministically. |
| `stop` | `List<String>?` | No | `null` | Up to 4 sequences where the API will stop generating further tokens. |
| `stream` | `Boolean?` | No | `false` | If set, partial message deltas will be sent, like in ChatGPT. |
| `streamOptions`| `StreamOptions?` | No | `null` | Options for streaming responses, e.g., including usage statistics. |
| `temperature` | `Float?` | No | `1.0f` | Range: 0 to 2. Controls randomness. Lower values are more deterministic. |
| `topP` | `Float?` | No | `1f` | Range: 0 to 1. Nucleus sampling. The model considers only tokens with `topP` probability mass. |
| `tools` | `List<Tool>?` | No | `null` | A list of tools the model may call. Currently, only functions are supported. |
| `toolChoice` | `ToolChoice?` | No | `null` | Controls which, if any, tool is called by the model. |
| `user` | `String?` | No | `null` | A unique identifier representing your end-user, to help monitor and detect abuse. |


### 2.2 Response (`Flow<ChatResponse>`)

The API returns a Kotlin `Flow` that emits `ChatResponse` objects.

-   **If `stream = false` (default)**: The flow will emit a single `ChatResponse` object containing the full completion and then close.
-   **If `stream = true`**: The flow will emit multiple `ChatResponse` objects, each being a "chunk" of the total response.

### 2.3 Kotlin Examples

**Example 1: Simple, Non-Streaming Chat**

```kotlin
viewModelScope.launch {
    val request = ChatRequest(
        model = "breeze-tiny-1b-instruct",
        messages = listOf(
            ChatMessage(role = "user", content = "Why is the sky blue?")
        ),
        temperature = 0.7f
    )
    
    EdgeAI.chat(request).collect { response ->
        val content = response.choices.firstOrNull()?.message?.content
        Log.d("ChatExample", "Full response received: $content")
    }
}
```

**Example 2: Streaming Chat with Conversation History**

```kotlin
val conversation = mutableListOf(
    ChatMessage(role = "system", content = "You are a helpful assistant."),
    ChatMessage(role = "user", content = "What is Kotlin?")
)

viewModelScope.launch {
    val request = ChatRequest(
        model = "breeze-tiny-1b-instruct",
        messages = conversation,
        stream = true
    )
    
    val fullResponse = StringBuilder()
    EdgeAI.chat(request).collect { chunk ->
        chunk.choices.firstOrNull()?.delta?.content?.let { deltaContent ->
            fullResponse.append(deltaContent)
            // Update UI with the latest fullResponse.toString()
        }
    }
}
```
--- 

## 3. Text-to-Speech API (`EdgeAI.tts`)

Generate natural-sounding speech from a given text input.

### 3.1 Request (`TTSRequest`)

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `input` | `String` | Yes | - | The text to synthesize. Max 4096 characters. |
| `model` | `String` | Yes | - | The TTS model to use. |
| `voice` | `String` | Yes | - | The voice style to use (e.g., "alloy", "nova"). |
| `speed` | `Float?` | No | `1.0f` | Playback speed, range: 0.25 to 4.0. |
| `responseFormat` | `String?`| No | `"mp3"` | The audio format (e.g., "mp3", "wav"). |

### 3.2 Response (`Flow<TTSResponse>`)

The API returns a `Flow` that emits a single `TTSResponse` object. This object contains the generated audio data as a `ByteArray`.

### 3.3 Kotlin Example

```kotlin
viewModelScope.launch {
    val request = TTSRequest(
        input = "Hello! This is a test of the text-to-speech system.",
        model = "tts-1",
        voice = "alloy"
    )

    EdgeAI.tts(request)
        .catch { e -> Log.e("TTSExample", "TTS failed", e) }
        .collect { response ->
            val audioData: ByteArray = response.audioData
            // Now you can play the audioData using Android's MediaPlayer or AudioTrack,
            // or save it to a file.
            Log.d("TTSExample", "Received ${audioData.size} bytes of audio data.")
        }
}
```
--- 

## 4. Automatic Speech Recognition API (`EdgeAI.asr`)

Transcribe audio into text.

### 4.1 Request (`ASRRequest`)

| Parameter | Type | Required | Default | Description |
|---|---|---|---|---|
| `file` | `ByteArray` | Yes | - | The audio data to transcribe, in a supported format. |
| `model` | `String` | Yes | - | The ASR model to use. |
| `language`| `String?`| No | `null` | Language of the audio in ISO-639-1 format (e.g., "en"). |
| `prompt` | `String?`| No | `null` | Text to guide the model's transcription style. |
| `responseFormat` | `String?`| No | `"json"` | The format of the response (`json`, `text`, `verbose_json`, etc.). |
| `temperature` | `Float?` | No | `0f` | Sampling temperature, from 0 to 1. Higher is more random. |

### 4.2 Response (`Flow<ASRResponse>`)

The API returns a `Flow` that emits a single `ASRResponse` containing the full transcription.

### 4.3 Kotlin Example

```kotlin
// Assume 'audioBytes' is a ByteArray loaded from an audio file.
viewModelScope.launch {
    val request = ASRRequest(
        model = "whisper-1",
        file = audioBytes,
        language = "en"
    )

    EdgeAI.asr(request)
        .catch { e -> Log.e("ASRExample", "ASR failed", e) }
        .collect { response ->
            // The full transcription is in response.text
            Log.d("ASRExample", "Transcription: ${response.text}")
        }
}
```