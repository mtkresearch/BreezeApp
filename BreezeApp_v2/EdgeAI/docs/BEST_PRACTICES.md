# ✨ EdgeAI SDK Best Practices

This document outlines best practices for integrating the EdgeAI SDK into a production Android application to ensure stability, performance, and a good user experience.

## 1. Service Lifecycle Management

Properly managing the connection to the `BreezeApp Router` is critical.

-   **Initialize Early, Release Late**: The best place to call `EdgeAI.initializeAndWait()` is in a component with an application-like lifecycle, such as a singleton repository or a `ViewModel` tied to the main activity. Similarly, `EdgeAI.shutdown()` should be called when this component is destroyed. See the [Getting Started Guide](./GETTING_STARTED.md) for a `ViewModel` example.
-   **Handle Connection Failures**: Always wrap your `initializeAndWait` call in a `try-catch` block. If you catch a `ServiceConnectionException`, your UI should react accordingly, for instance, by disabling AI-related features and showing a message asking the user to install or launch the `BreezeApp Router`.
-   **App Backgrounding**: When your app goes into the background, you generally don't need to shut down the SDK. The connection is lightweight. However, you should cancel any ongoing `Flow` collections from the SDK to stop receiving data and save battery. The `viewModelScope` or `lifecycleScope` will often handle this automatically if you launch your coroutines correctly.

## 2. Conversation and State Management

For chat applications, managing the conversation history is key.

-   **Use a `StateFlow`**: In your `ViewModel`, store the list of `ChatMessage` objects in a `MutableStateFlow`. This allows your UI (e.g., a Jetpack Compose `LazyColumn`) to reactively update as new messages are added.
-   **Isolate UI State**: Create a dedicated UI state data class that includes not just the messages, but also loading status, errors, etc.

    ```kotlin
    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val isLoading: Boolean = false,
        val error: String? = null
    )
    ```
-   **Token Limit Awareness**: LLMs have a context window limit. Very long conversations can exceed this limit. While the SDK itself doesn't enforce a limit, your application should. A simple strategy is to truncate the history sent in the `ChatRequest` if the message list grows too large (e.g., keep only the last 20-30 messages).

## 3. UI/UX for AI Features

AI responses can be slow or unpredictable. Your UI must account for this.

-   **Indicate Loading States**: Never make an API call without giving the user visual feedback. Show a loading indicator, a "Bot is typing..." animation, or disable the send button while waiting for a response.
-   **Streaming for Chat**: For the best user experience, always use `stream = true` for chat. As new `delta` chunks arrive, append them to the last message in your UI state. This makes the app feel much more responsive than waiting for the full response.
-   **Graceful Error Display**: When an API call fails, display a clear, non-technical error message to the user. For example, if ASR fails, show "Could not process audio, please try again" instead of logging the raw exception to the screen.

## 4. Performance Considerations

-   **Avoid Blocking the Main Thread**: The SDK's APIs are `suspend` functions and `Flow`s, so they are main-thread-safe. Always call them from a background-safe coroutine scope like `viewModelScope` or `lifecycleScope.launch`.
-   **Handle Large Audio Data**: For ASR, loading a large audio file into a `ByteArray` can consume significant memory. If dealing with very large files, consider streaming the audio data if the underlying API supports it in the future. For now, be mindful of the potential memory impact on low-end devices. 