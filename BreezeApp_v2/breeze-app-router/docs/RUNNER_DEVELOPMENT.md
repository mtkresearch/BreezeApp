# 🧩 Runner Development Guide

This guide is for developers who want to extend the BreezeApp AI Router by creating new AI capabilities or adding support for new models.

A **Runner** is a self-contained component that implements a specific AI task. To contribute a runner, you need to understand its lifecycle and how it fits into the overall architecture.

> **Prerequisite**: Before reading this, please make sure you have read and understood our main **[README.md](../README.md)**, as this guide builds upon the concepts explained there.

## 🎯 The Core Philosophy: From Payload to Runner

Our architecture is designed to be highly extensible. The journey of a request is always:

`RequestPayload` → `UseCase` → `Runner`

-   **`RequestPayload`**: A specific, type-safe data class defining what the client wants.
-   **`UseCase`**: Contains the business logic for a single capability (e.g., `TextGenerationUseCase`). It knows *what* to do, but not *how*.
-   **`Runner`**: The implementation that knows *how* to do it (e.g., `LlamaRunner`, `MockLLMRunner`).

To add a new capability, you'll need to touch all three layers.

## 🚀 Tutorial: Creating a New "Echo" Capability

Let's walk through creating a brand-new capability from scratch. Our goal is to create an `EchoRunner` that simply echoes back the text it receives.

This example will teach you everything you need to know to create a real runner (e.g., for a new model from Hugging Face).

### Step 1: Define the `RequestPayload`

First, we define the contract for our new capability in the `EdgeAI` module.

```kotlin
// In: EdgeAI/src/.../model/RequestPayload.kt

@Parcelize
sealed interface RequestPayload : Parcelable {
    // ... other payloads (TextChat, ImageAnalysis, etc.)

    @Parcelize
    data class Echo(
        val textToEcho: String
    ) : RequestPayload
}
```

### Step 2: Implement the `BaseRunner`

Next, we create the actual runner in the `breeze-app-router` module. It must implement the `BaseRunner` interface.

The most important method is `run()`, which takes the specific `payload` and returns a `Flow<AIResponse>`.

```kotlin
// In: breeze-app-router/src/.../runner/EchoRunner.kt

import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.RequestPayload
import com.mtkresearch.breezeapp.shared.contracts.model.ResponseMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class EchoRunner : BaseRunner {
    override val id: String = "echo-runner-${UUID.randomUUID()}"
    private var isLoaded = false

    override suspend fun load(): Boolean {
        // For real runners, you would load model files here.
        // For our simple echo runner, we just set a flag.
        isLoaded = true
        return true
    }

    override fun run(payload: RequestPayload, requestId: String): Flow<AIResponse> = flow {
        if (!isLoaded) {
            emit(AIResponse.error(requestId, "EchoRunner model not loaded"))
            return@flow
        }

        // Type-check the payload to ensure we got the right kind.
        if (payload !is RequestPayload.Echo) {
            emit(AIResponse.error(requestId, "Invalid payload type for EchoRunner"))
            return@flow
        }

        val echoedText = "Echo: ${payload.textToEcho}"

        // Emit a single, complete response.
        emit(AIResponse(
            requestId = requestId,
            text = echoedText,
            isComplete = true,
            state = AIResponse.ResponseState.COMPLETED,
            metadata = ResponseMetadata.Standard(
                modelName = "Echo-v1",
                processingTimeMs = 2, // It's fast!
                backend = "CPU"
            )
        ))
    }

    override suspend fun unload() {
        isLoaded = false
    }

    override fun isLoaded(): Boolean = isLoaded
}
```

### Step 3: Create the `UseCase`

The Use Case acts as the bridge between the routing logic and the runner.

```kotlin
// In: breeze-app-router/src/.../usecase/EchoUseCase.kt

import com.mtkresearch.breezeapp.router.engine.RunnerRegistry
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.RequestPayload
import kotlinx.coroutines.flow.Flow

class EchoUseCase(private val runnerRegistry: RunnerRegistry) {
    
    suspend fun execute(requestId: String, payload: RequestPayload.Echo): Flow<AIResponse> {
        // "ECHO" is the capability name we'll define in the config.
        val runner = runnerRegistry.getRunnerForCapability("ECHO") 
            ?: throw IllegalStateException("No runner found for ECHO capability")
        
        return runner.run(payload, requestId)
    }
}
```

### Step 4: Wire Everything Together

1.  **Instantiate the UseCase**: In `AIEngineManager.kt` (or your DI framework), create an instance of your new `EchoUseCase`.
    ```kotlin
    val echoUseCase = EchoUseCase(runnerRegistry)
    ```

2.  **Update the Dispatcher**: In `AIRequestDispatcher.kt`, add a new branch to the `when` block to handle the `Echo` payload.
    ```kotlin
    // In AIRequestDispatcher.kt
    suspend fun dispatch(request: AIRequest): Flow<AIResponse> {
        return when (val payload = request.payload) {
            is RequestPayload.TextChat -> // ...
            is RequestPayload.ImageAnalysis -> // ...
            
            // Our new capability
            is RequestPayload.Echo -> echoUseCase.execute(request.id, payload)
        }
    }
    ```

### Step 5: Register in `runner_config.json`

Finally, tell the router about your new runner by adding it to the config file.

```json
// In: breeze-app-router/src/main/assets/runner_config.json
{
  "runners": [
    // ... other runners
    {
      "name": "echo_runner_v1",
      "class": "com.mtkresearch.breezeapp.router.runner.EchoRunner",
      "capabilities": ["ECHO"],
      "priority": 10,
      "is_real": true
    }
  ]
}
```
-   **`"class"`**: The fully-qualified class name of your runner.
-   **`"capabilities"`**: A list of capabilities this runner can handle. This **must match** the string used in your `UseCase`.

That's it! You have successfully added a new, end-to-end AI capability to the router.

## Implementing Streaming

For runners that generate responses incrementally (like LLMs), you can emit multiple `AIResponse` objects from your `run()` method's `Flow`.

```kotlin
// Inside a streaming runner's run() method...
override fun run(payload: RequestPayload, requestId: String): Flow<AIResponse> = flow {
    val fullText = "This is a streaming response."
    val words = fullText.split(" ")
    var currentText = ""

    words.forEachIndexed { index, word ->
        currentText += "$word "
        val isLast = (index == words.size - 1)
        
        // Emit a partial response
        emit(AIResponse(
            requestId = requestId,
            text = currentText.trim(),
            isComplete = isLast,
            state = if (isLast) AIResponse.ResponseState.COMPLETED else AIResponse.ResponseState.STREAMING
        ))
        
        delay(100) // Simulate processing time
    }
}
```
The client will receive each emission as a separate `onResponse` call, allowing for a real-time UI update. The `isComplete` flag signals when the stream has ended. 