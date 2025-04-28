# TTS Engine Service

This directory contains Android service implementations for text-to-speech functionality. The services provide system integration for the core TTS components located in the `com.mtkresearch.breeze_app.tts` package.

## Component Overview

```
[Your App/Activity] ──binds to──> [TTSEngineService] ──contains──> [TTSService] ──uses──> [TTSRunner Implementation]
   (UI Layer)                   (Android Service Layer)          (Core Logic)           (Backend Implementation)
```

### TTSEngineService

The `TTSEngineService` class is an Android service that integrates the core TTS functionality with the Android system:

- Extends Android's `Service` class for background operation
- Handles service lifecycle (binding, creation, destruction)
- Creates and manages the `TTSService` instance
- Provides IPC interface for Activities to interact with
- Manages audio playback through Android system APIs

## Service Structure

```
com.mtkresearch.breeze_app.service/         - Android service implementations
├── BaseEngineService.java                  - Base class for all engine services 
└── TTSEngineService.java                   - TTS service implementation
```

## Integration Guide

### 1. Add Service to AndroidManifest.xml

```xml
<service android:name="com.mtkresearch.breeze_app.service.TTSEngineService" />
```

### 2. Bind to the Service from Your Activity

```java
private TTSEngineService ttsService;
private boolean ttsServiceBound = false;

private final ServiceConnection ttsConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        TTSEngineService.LocalBinder binder = (TTSEngineService.LocalBinder) service;
        ttsService = binder.getService();
        ttsServiceBound = true;
        
        // Initialize TTS (returns CompletableFuture<Boolean>)
        ttsService.initialize().thenAccept(success -> {
            if (success) {
                Log.d(TAG, "TTS service ready");
            } else {
                Log.e(TAG, "TTS service initialization failed");
            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        ttsService = null;
        ttsServiceBound = false;
    }
};

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // ...other setup code
    
    // Bind to TTS service
    Intent intent = new Intent(this, TTSEngineService.class);
    bindService(intent, ttsConnection, Context.BIND_AUTO_CREATE);
}

@Override
protected void onDestroy() {
    // Unbind from service
    if (ttsServiceBound) {
        unbindService(ttsConnection);
        ttsServiceBound = false;
    }
    super.onDestroy();
}
```

### 3. Use the Service to Synthesize Speech

```java
private void speakText(String text) {
    if (ttsService != null && ttsServiceBound) {
        ttsService.speak(text).thenRun(() -> {
            Log.d(TAG, "Finished speaking");
        }).exceptionally(ex -> {
            Log.e(TAG, "Error speaking text", ex);
            return null;
        });
    } else {
        Log.w(TAG, "TTS service not available");
    }
}
```

## Extending with New TTS Backends

To use a new TTS backend with the service, modify the `initialize()` method in `TTSEngineService`:

```java
@Override
public CompletableFuture<Boolean> initialize() {
    CompletableFuture<Boolean> future = new CompletableFuture<>();
    
    try {
        // Create your new runner
        NewBackendRunner runner = new NewBackendRunner(getApplicationContext());
        
        // Create the TTSService with your runner
        ttsService = new TTSService(runner);
        
        // Create a configuration for your backend
        TTSConfig config = new TTSConfig(
            "new_backend",          // backend identifier
            "/path/to/model",       // model path
            null,                   // vocoder path (if needed)
            0,                      // speaker ID
            1.0f,                   // speed
            null,                   // voice name
            null,                   // API key
            null                    // extra parameters
        );
        
        // Update the model
        ttsService.updateModel(config);
        
        // Rest of initialization...
    }
    catch (Exception e) {
        Log.e(TAG, "Failed to initialize TTS service", e);
        future.complete(false);
    }
    
    return future;
}
```

## Best Practices

1. **Service Management**
   - Always unbind from TTSEngineService in your Activity's onDestroy()
   - Check service connection before attempting to use it

2. **Resource Cleanup**
   - The service handles releasing TTSService resources in onDestroy()
   - Make sure to unbind from the service to prevent memory leaks

3. **Error Handling**
   - Handle exceptions from TTS operations using the CompletableFuture API
   - Provide fallback behavior when TTS is unavailable

4. **Performance Considerations**
   - TTS synthesis can be computationally intensive
   - Use the service for background processing
   - Consider implementing caching for frequently used phrases 