# Breeze App Router Client - A Developer's Guide & Example

This application serves as a reference implementation and a testing tool for the `breeze-app-router` service. It's designed to guide developers on how to connect their own Android applications with our AI routing service and invoke its various AI capabilities.

This client is built with modern Android practices, featuring a clean architecture that separates the UI (View), UI logic (ViewModel), and service connection logic (`AIRouterClient`).

## Architecture Overview

The client's architecture is simple and robust, promoting separation of concerns:

- **`MainActivity.kt` (View)**: The UI layer. It observes the `MainViewModel` for state changes and forwards user actions.
- **`MainViewModel.kt` (ViewModel)**: The logic hub. It prepares `RequestPayload` objects, sends them via the router service, and manages the UI state. It delegates connection management to the `AIRouterClient`.
- **`AIRouterClient.kt` (Service Connector)**: A dedicated class that handles all the complexities of binding to the AIDL service, managing the connection lifecycle, and exposing the service state reactively.
- **`breeze-app-router` (Service)**: The external AIDL service this client binds to.

This separation makes it easy to understand the integration by looking at `AIRouterClient` for connection logic and `MainViewModel` for request/response logic.

## Key Integration Steps for Your App

Here's a breakdown of the essential code you'll need to integrate the `breeze-app-router` into your own application.

### 1. Add the `shared-contracts` Module

Your project must include the `shared-contracts` module, which contains the AIDL interfaces and `Parcelable` data models for communication.

Add the module to your `settings.gradle.kts`:
```kotlin
include(":shared-contracts")
```

Add the dependency to your app's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(project(":shared-contracts"))
}
```

### 2. Configure Your `AndroidManifest.xml`

Your app needs permissions and queries to discover and bind to the service:

```xml
<!-- Add to your AndroidManifest.xml -->
<uses-permission android:name="com.mtkresearch.breezeapp.permission.BIND_AI_ROUTER_SERVICE" />

<queries>
    <package android:name="com.mtkresearch.breezeapp.router" />
</queries>
```

### 3. Connect to the Service using `AIRouterClient`

Use `AIRouterClient` to manage the service connection. In your ViewModel or another lifecycle-aware component, create an instance and collect its state.

**Key Code (`MainViewModel.kt`)**:
```kotlin
// In your ViewModel
private val airouterClient = AIRouterClient(application)
private var routerService: IAIRouterService? = null

init {
    // Collect the connection state to update UI
    viewModelScope.launch {
        airouterClient.connectionState.collect { state ->
            // Update your UI state based on connection status (CONNECTED, DISCONNECTED, etc.)
        }
    }
    
    // Collect the service binder to get access to the service interface
    viewModelScope.launch {
        airouterClient.routerService.collect { service ->
            this.routerService = service
            // Register your listener once the service is available
            service?.registerListener(your_listener_callback)
        }
    }
}

// Call connect() when you're ready to bind
fun connect() {
    airouterClient.connect()
}

// Call disconnect() when you're done
fun disconnect() {
    airouterClient.disconnect()
}
```

### 4. Send a Type-Safe AI Request

Create a specific `RequestPayload` object and send it using the `sendMessage` method.

**Key Code (`MainViewModel.kt`)**:
```kotlin
fun sendLLMRequest(prompt: String) {
    if (routerService == null) return

    // 1. Create a strongly-typed payload object
    val payload = RequestPayload.TextChat(
        prompt = prompt,
        modelName = "mock-llm"
    )

    // 2. Create the AIRequest wrapper
    val request = AIRequest(
        id = UUID.randomUUID().toString(),
        sessionId = "your-session-id",
        timestamp = System.currentTimeMillis(),
        payload = payload
    )
    
    // 3. Send the request
    routerService?.sendMessage(request)
}
```

### 5. Handle Responses

Implement the `IAIRouterListener` to receive responses. The callback methods are executed on a background thread.

**Key Code (`MainViewModel.kt`)**:
```kotlin
private val callback = object : IAIRouterListener.Stub() {
    override fun onResponse(response: AIResponse) {
        // This is called by the service from a background thread
        val state = if(response.isComplete) "Completed" else "Streaming"
        
        // Example: Log the response text and metadata
        Log.d(TAG, "Response [${state}]: ${response.text}")
        response.metadata?.let { Log.d(TAG, "Metadata: $it") }
        
        // In a real app, post to the Main thread to update UI
        // viewModelScope.launch(Dispatchers.Main) { /* Update UI */ }
    }
}
```

## API Documentation

For complete details on all data models (`Configuration`, `AIRequest`, `AIResponse`, `RequestPayload`, `ResponseMetadata`), please refer to the documentation in the `shared-contracts` module.

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