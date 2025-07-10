# 🚀 Getting Started with EdgeAI SDK

This guide provides a comprehensive walkthrough for setting up the EdgeAI SDK in your Android project and making your first API call.

## 1. Prerequisites

Before you begin, ensure you have the following:

-   An existing Android project with Kotlin support.
-   The **BreezeApp Router** application installed on your target device or emulator. This is a hard requirement as the EdgeAI SDK communicates with this separate application to perform AI tasks.

## 2. Installation

Add the EdgeAI module as a dependency in your app-level `build.gradle.kts` file:

```kotlin
// In your app's build.gradle.kts
dependencies {
    // Other dependencies...
    implementation(project(":EdgeAI"))
}
```
Sync your project with Gradle to apply the changes.

## 3. SDK Initialization

The SDK must be initialized before any other API calls can be made. Initialization establishes a connection with the `BreezeApp Router` service. The recommended way to do this is using the `initializeAndWait()` suspending function.

### Recommended Approach: Initialize in your ViewModel or a central repository

It's best practice to tie the SDK's lifecycle to your application's lifecycle. A common pattern is to initialize it within a `ViewModel` that is shared across your app.

```kotlin
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val context: Context) : ViewModel() {

    private val _isSdkReady = MutableStateFlow(false)
    val isSdkReady: StateFlow<Boolean> = _isSdkReady

    init {
        initializeSdk()
    }

    private fun initializeSdk() {
        viewModelScope.launch {
            try {
                EdgeAI.initializeAndWait(context, timeoutMs = 10000)
                _isSdkReady.value = true
                // You can now safely make API calls
            } catch (e: ServiceConnectionException) {
                _isSdkReady.value = false
                // Handle the error, e.g., show a dialog to the user
                // asking them to install the BreezeApp Router.
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        EdgeAI.shutdown() // Important: Clean up resources when the ViewModel is destroyed
    }
}
```

## 4. Making Your First API Call

Once the SDK is initialized (`isSdkReady` is `true`), you can start making API calls. Here is a simple example of a chat request.

```kotlin
// Inside your ViewModel, after confirming the SDK is ready
fun sendSimpleMessage(prompt: String) {
    if (!isSdkReady.value) {
        // Handle case where SDK is not ready
        return
    }
    
    viewModelScope.launch {
        val request = chatRequest(prompt = prompt)
        
        EdgeAI.chat(request)
            .catch { e ->
                // Handle API-specific errors
            }
            .collect { response ->
                val content = response.choices.firstOrNull()?.message?.content
                // Update your UI with the response
            }
    }
}
```

## 5. Resource Management

It is crucial to release the SDK's resources when your application is closing to prevent memory leaks.

-   **`EdgeAI.shutdown()`**: This method disconnects from the `BreezeApp Router` service and cleans up all associated resources. Call this in a component whose lifecycle matches your application's, such as the `onCleared()` method of a shared `ViewModel` or `Application.onTerminate()`.

You are now ready to explore the other APIs. Refer to the **[API Reference](./API_REFERENCE.md)** for detailed information on Chat, TTS, and ASR functionalities. 