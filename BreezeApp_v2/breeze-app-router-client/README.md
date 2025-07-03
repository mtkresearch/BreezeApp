# Breeze App Router Client - A Developer's Guide & Example

This application serves as a reference implementation and a testing tool for the `breeze-app-router` service. It's designed to guide developers in the open-source community on how to connect their own Android applications with our AI routing service, initialize it, and invoke various AI capabilities.

This client is built with modern Android practices, featuring a **Clean Architecture** approach using **MVVM (Model-View-ViewModel)** to separate concerns, making the code clean, scalable, and easy to understand.

## Architecture Overview

The client's architecture is simple yet robust:

- **`MainActivity.kt` (View)**: The UI layer. It's responsible only for displaying data and forwarding user actions to the `MainViewModel`. It knows nothing about the underlying business logic.
- **`MainViewModel.kt` (ViewModel)**: The logic hub. It prepares and manages all data for the UI, handles user actions, and communicates with the `breeze-app-router` service. It contains all the core logic of the client application.
- **`breeze-app-router` (Service)**: An external AIDL service that this client binds to. It performs the actual AI inference.

This separation makes it easy for you to see exactly what's needed to integrate the router, by focusing on the `MainViewModel`.

## Key Integration Steps for Your App

Here's a breakdown of the essential code you'll need to integrate the `breeze-app-router` into your own application. All relevant logic can be found in `MainViewModel.kt`.

### 1. Add the AIDL Contracts (shared-contracts)

The `shared-contracts` module contains the AIDL interface files (`.aidl`) and Parcelable data models required for communication with the router service. There are two ways to integrate these contracts into your project:

#### Option A: Include as a Module (Recommended)

If you're developing within the BreezeApp ecosystem or have access to the source code:

1. Add the module to your `settings.gradle.kts`:
   ```kotlin
   include(":shared-contracts")
   ```

2. Add the dependency to your app's `build.gradle.kts`:
   ```kotlin
   dependencies {
       implementation(project(":shared-contracts"))
   }
   ```

> **Note for Future Development**: In the future, we plan to publish the `shared-contracts` module to Maven Central or JitPack, which will simplify integration for third-party developers. This would allow developers to include the module via standard dependency management without needing direct access to the source code.

#### Option B: Copy the Contracts (Standalone Development)

If you're developing a standalone application:

1. Create a similar structure in your project:
   ```
   your-app/
   ├── app/
   └── shared-contracts/
       └── src/main/
           ├── aidl/com/mtkresearch/breezeapp/shared/contracts/
           │   ├── IAIRouterService.aidl
           │   ├── IAIRouterListener.aidl
           │   └── model/
           │       ├── AIRequest.aidl
           │       ├── AIResponse.aidl
           │       ├── BinaryData.aidl
           │       └── Configuration.aidl
           └── java/com/mtkresearch/breezeapp/shared/contracts/model/
               ├── AIRequest.kt
               ├── AIResponse.kt
               ├── BinaryData.kt
               └── Configuration.kt
   ```

2. Copy the AIDL files and their corresponding Kotlin implementations.

3. Configure your `shared-contracts/build.gradle.kts` with:
   ```kotlin
   plugins {
       id("com.android.library")
       id("org.jetbrains.kotlin.android")
       id("kotlin-parcelize")
   }

   android {
       buildFeatures {
           aidl = true
       }
   }
   ```

**IMPORTANT**: The package structure must be maintained exactly as `com.mtkresearch.breezeapp.shared.contracts` for compatibility with the service.

### 2. Configure Your AndroidManifest.xml

Your app needs proper permissions and queries to interact with the service:

```xml
<!-- Add to your AndroidManifest.xml -->
<!-- Permission to bind to the AI Router Service -->
<uses-permission android:name="com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE" />

<!-- Queries to discover the service -->
<queries>
    <package android:name="com.mtkresearch.breezeapp.router" />
    <intent>
        <action android:name="com.mtkresearch.breezeapp.router.AIRouterService" />
    </intent>
</queries>
```

### 3. Connect to the AI Router Service

To communicate with the service, you must bind to it. This is an asynchronous operation.

**Key Code (`MainViewModel.kt`)**:
```kotlin
fun connectToService() {
    logMessage("🔄 Connecting to AI Router Service...")

    val intent = Intent("com.mtkresearch.breezeapp.router.AIRouterService").apply {
        setPackage("com.mtkresearch.breezeapp.router")
    }

    val success = getApplication<Application>().bindService(intent, connection, Context.BIND_AUTO_CREATE)

    if (!success) {
        logMessage("❌ Failed to bind to AI Router Service. Is it installed?")
    }
}

private val connection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        // Once connected, you receive an IBinder object.
        // Cast it to your AIDL interface.
        routerService = IAIRouterService.Stub.asInterface(service)
        // Register a listener to receive responses.
        routerService?.registerListener(callback)
    }
    // ...
}
```

### 4. Initialize the Service

After connecting, you must initialize the router with a `Configuration` object. This tells the service which models to load and how to behave.

**Key Code (`MainViewModel.kt`)**:
```kotlin
fun initializeService() {
    if (!isConnected) return
    
    // Create a configuration object
    val config = Configuration(
        apiVersion = 1,
        logLevel = 3,  // 0=OFF, 1=ERROR, 2=WARN, 3=INFO, 4=DEBUG, 5=VERBOSE
        timeoutMs = 30000L,
        maxTokens = 1024,
        temperature = 0.7f,
        languagePreference = "en-US",
        preferredRuntime = Configuration.RuntimeBackend.AUTO,
        runnerConfigurations = mapOf(
            Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.MOCK,
            Configuration.AITaskType.IMAGE_ANALYSIS to Configuration.RunnerType.MOCK,
            Configuration.AITaskType.SPEECH_RECOGNITION to Configuration.RunnerType.MOCK,
            Configuration.AITaskType.SPEECH_SYNTHESIS to Configuration.RunnerType.MOCK,
            Configuration.AITaskType.CONTENT_MODERATION to Configuration.RunnerType.MOCK
        )
    )
    
    // Send the configuration to the service
    routerService?.initialize(config)
}
```

### 5. Send an AI Request (Example: Image Analysis)

All AI tasks are initiated by sending an `AIRequest` object. The `options` map is crucial for specifying the task type and providing data.

**Key Code (`MainViewModel.kt`)**:
```kotlin
fun analyzeImage(prompt: String, imageUri: Uri) {
    viewModelScope.launch {
        // 1. Process your data if necessary (e.g., encode image to Base64)
        val base64Image = encodeImageToBase64(imageUri)
        
        // 2. Define task-specific options
        val options = mapOf(
            "request_type" to "image_analysis", // Specifies the AI task
            "model_name" to "mock-vlm",         // (Optional) Specify a model
            "image_data" to base64Image         // Provide the data payload
        )
        
        // 3. Create and send the request
        sendRequest(prompt, options)
    }
}

private fun sendRequest(text: String, options: Map<String, String>) {
    val request = AIRequest(
        id = UUID.randomUUID().toString(),
        text = text,
        sessionId = "session-${options["request_type"]}",
        timestamp = System.currentTimeMillis(),
        options = options
    )
    routerService?.sendMessage(request)
}
```

### 6. Handle Responses

You'll receive responses through the `IAIRouterListener` you registered upon connection. These calls arrive on a background thread, so be sure to switch to the main thread for UI updates.

**Key Code (`MainViewModel.kt`)**:
```kotlin
private val callback = object : IAIRouterListener.Stub() {
    override fun onResponse(response: AIResponse) {
        // This is called by the service from a background thread
        val state = if(response.isComplete) "Completed" else "Streaming"
        logMessage("✅ Response [${state}]: ${response.text}")
        
        // In a real app, you would post this to your UI thread:
        // viewModelScope.launch(Dispatchers.Main) {
        //     // Update UI with response
        // }
    }
}
```

## Understanding the AIDL Contracts

The `shared-contracts` module defines the interface between your app and the AI Router Service using Android Interface Definition Language (AIDL). These contracts are critical for proper IPC communication.

### Key AIDL Files

1. **`IAIRouterService.aidl`**: The main service interface with methods like:
   - `initialize(Configuration config)`
   - `sendMessage(AIRequest request)`
   - `registerListener(IAIRouterListener listener)`

2. **`IAIRouterListener.aidl`**: The callback interface for receiving responses.

3. **Model classes**:
   - `AIRequest.aidl` & `AIRequest.kt`: Request data structure
   - `AIResponse.aidl` & `AIResponse.kt`: Response data structure
   - `Configuration.aidl` & `Configuration.kt`: Service configuration
   - `BinaryData.aidl` & `BinaryData.kt`: Binary data transfer

### Important Considerations

- **Package Structure**: The package name `com.mtkresearch.breezeapp.shared.contracts` must be preserved exactly.
- **Versioning**: The `apiVersion` field helps manage compatibility between client and service.
- **Thread Safety**: AIDL callbacks occur on background threads; use proper thread handling for UI updates.

## API Documentation

### IAIRouterService Methods

| Method | Description | Parameters | Return Value |
|--------|-------------|------------|-------------|
| `getApiVersion()` | Returns the API version supported by the service. | None | `int`: API version number |
| `initialize(Configuration config)` | Initializes the service with the provided configuration. | `config`: Configuration object with runtime settings | `void` |
| `sendMessage(AIRequest request)` | Sends an AI request to the service for processing. | `request`: AIRequest object containing the query and options | `void` |
| `cancelRequest(String requestId)` | Attempts to cancel an in-progress request. | `requestId`: ID of the request to cancel | `boolean`: true if cancellation succeeded |
| `registerListener(IAIRouterListener listener)` | Registers a callback to receive responses. | `listener`: IAIRouterListener implementation | `void` |
| `unregisterListener(IAIRouterListener listener)` | Unregisters a previously registered listener. | `listener`: IAIRouterListener to remove | `void` |
| `hasCapability(String capabilityName)` | Checks if a specific capability is supported. | `capabilityName`: Name of the capability to check | `boolean`: true if supported |

### Configuration Options

The `Configuration` class provides several important options:

| Field | Type | Description |
|-------|------|-------------|
| `apiVersion` | `Int` | API version to use (default: 1) |
| `logLevel` | `Int` | Controls logging verbosity (0=OFF to 5=VERBOSE) |
| `preferredRuntime` | `RuntimeBackend` | Preferred hardware for inference (AUTO, CPU, GPU, NPU) |
| `runnerConfigurations` | `Map<AITaskType, RunnerType>` | Maps AI tasks to their implementations |
| `timeoutMs` | `Long` | Request timeout in milliseconds (0 means no timeout) |
| `maxTokens` | `Int` | Maximum tokens to generate in responses |
| `temperature` | `Float` | Controls randomness in generation (0.0-1.0) |
| `languagePreference` | `String` | Preferred language for responses |

### Request Types

The `AIRequest.RequestType` class defines common request types:

| Constant | Description |
|----------|-------------|
| `TEXT_CHAT` | Text-based conversation |
| `IMAGE_ANALYSIS` | Image analysis/vision tasks |
| `AUDIO_TRANSCRIPTION` | Speech-to-text transcription |
| `MULTIMODAL` | Combined text and image processing |

## Sample Integration Project

> **Note**: A standalone sample integration project is planned for future development. For now, this client app serves as the reference implementation.

To create a minimal integration in your own app:

1. **Create a Service Connection Manager**:
   ```kotlin
   class AIRouterManager(private val context: Context) {
       private var routerService: IAIRouterService? = null
       private var isConnected = false
       private var isInitialized = false
       
       // Connection management methods
       fun connect() { /* ... */ }
       fun disconnect() { /* ... */ }
       fun initialize() { /* ... */ }
       
       // Request methods
       fun sendTextRequest(prompt: String) { /* ... */ }
       fun analyzeImage(prompt: String, imageUri: Uri) { /* ... */ }
   }
   ```

2. **Implement a Basic UI**:
   ```kotlin
   class MinimalAIActivity : AppCompatActivity() {
       private val viewModel: MinimalAIViewModel by viewModels()
       
       override fun onCreate(savedInstanceState: Bundle?) {
           super.onCreate(savedInstanceState)
           setContentView(R.layout.activity_minimal_ai)
           
           // Connect to service
           findViewById<Button>(R.id.connectButton).setOnClickListener {
               viewModel.connectToService()
           }
           
           // Send a request
           findViewById<Button>(R.id.sendRequestButton).setOnClickListener {
               val prompt = findViewById<EditText>(R.id.promptInput).text.toString()
               viewModel.sendTextRequest(prompt)
           }
       }
   }
   ```

## Integration Testing Tools

To help debug and test your integration with the AI Router Service, we've included several utilities:

### 1. Connection Diagnostic Tool

The `test_connection.sh` script helps verify proper service connection:

```bash
#!/bin/bash
# Save as test_connection.sh in your project root

echo "🔧 Testing BreezeApp Router Client Connection"
echo "============================================="

# Verify installation
echo "📋 Verifying installation..."
adb shell pm list packages | grep "com.mtkresearch.breezeapp.router"

# Check permissions
echo "🔒 Checking permissions..."
adb shell dumpsys package com.mtkresearch.breezeapp.router | grep -A 10 "declared permissions"
echo ""
echo "Client permissions:"
adb shell dumpsys package YOUR_PACKAGE_NAME | grep -A 10 "requested permissions"

# Monitor connection logs
echo "📝 Monitoring connection logs..."
adb logcat | grep -E "(AIRouter|ServiceConnection|binder)"
```

Replace `YOUR_PACKAGE_NAME` with your application's package name and run the script to diagnose connection issues.

### 2. AIRouterTester Class

Add this utility class to your project for quick testing of all router capabilities:

```kotlin
/**
 * Utility class for testing AI Router Service integration.
 * Add this to your project for quick diagnostics.
 */
class AIRouterTester(private val context: Context) {
    private var routerService: IAIRouterService? = null
    private val serviceConnection = /* ... */
    
    // Test all capabilities
    fun runFullTest() {
        connectToService()
        testApiVersion()
        testTextGeneration()
        testImageAnalysis()
        // ...
    }
    
    // Individual test methods
    fun testApiVersion() { /* ... */ }
    fun testTextGeneration() { /* ... */ }
    fun testImageAnalysis() { /* ... */ }
    
    // Log results
    private fun logResult(test: String, success: Boolean, message: String) {
        Log.d("AIRouterTest", "[$test] ${if(success) "✅" else "❌"} $message")
    }
}
```

### 3. Router Service Health Check

Before sending important requests, verify the service is healthy:

```kotlin
fun checkRouterHealth(): Boolean {
    if (routerService == null) return false
    
    try {
        // Basic API version check should always work if service is healthy
        val apiVersion = routerService?.apiVersion ?: -1
        if (apiVersion <= 0) return false
        
        // Check essential capabilities
        val hasTextCapability = routerService?.hasCapability("text_generation") ?: false
        
        return hasTextCapability
    } catch (e: Exception) {
        Log.e("RouterHealth", "Health check failed", e)
        return false
    }
}
```

### 4. Performance Monitoring

Monitor the performance of your AI requests:

```kotlin
fun sendRequestWithTiming(request: AIRequest) {
    val startTime = System.currentTimeMillis()
    
    // Add timing metadata
    val requestWithTiming = request.copy(
        options = request.options + ("client_request_time" to startTime.toString())
    )
    
    // Send the request
    routerService?.sendMessage(requestWithTiming)
    
    // In your response callback:
    // val processingTime = System.currentTimeMillis() - startTime
    // Log.d("AIPerformance", "Request ${request.id} took ${processingTime}ms")
}
```

## Building and Running

1. Ensure you have the `breeze-app-router` (debug or release) installed on your target device/emulator.
2. Build and run this client application:
   ```bash
   ./gradlew :breeze-app-router-client:installDebug
   ```
3. Use the UI to connect, initialize, and test AI functions.

## Troubleshooting

### Common Issues

1. **Service Connection Fails**:
   - Verify the router service app is installed (`adb shell pm list packages | grep breezeapp.router`)
   - Check that your app has the correct permission in AndroidManifest.xml
   - Ensure both apps are signed with the same key (for signature-level permissions)

2. **Initialization Fails**:
   - Verify your Configuration object has valid settings
   - Check logcat for detailed error messages from the service

3. **AIDL Errors**:
   - Ensure your AIDL files exactly match those expected by the service
   - Verify that all Parcelable implementations are correct

4. **Data Transfer Issues**:
   - Large binary data should be properly chunked or use content URIs
   - Base64 encoding adds ~33% overhead; consider this for memory constraints

### Debugging Tips

- Use `adb logcat | grep AIRouter` to monitor service logs
- Add detailed logging in your ServiceConnection callbacks
- Test with the mock runners first before moving to production models

## Advanced Integration

For production applications, consider these best practices:

1. **Error Handling**: Implement robust error handling and retry mechanisms.
2. **Service Lifecycle**: Handle service disconnections gracefully.
3. **Configuration Management**: Store and reuse configurations to avoid reinitializing.
4. **Memory Management**: Be mindful of large data transfers, especially with images and audio.
5. **Background Processing**: Consider using a foreground service for long-running AI tasks.

This client provides a clear, working example of all the core concepts. By examining `MainViewModel.kt`, you can quickly learn how to integrate our powerful AI router into your own projects. Welcome to the community! 