# Text-to-Speech (TTS) Module

This package provides a modular, extensible TTS system with support for multiple backends including Sherpa-onnx and Android's Google TTS.

## Architecture Overview

The TTS module follows a layered architecture with clear separation of concerns:

```
[Core TTS Components]
     TTSService ──uses──> TTSRunner Implementation
                            (SherpaTTSRunner, GoogleTTSRunner, etc.)
```

### Component Roles

1. **TTSConfig**: Data container class
   - Encapsulates configuration for TTS systems
   - Provides factory methods for common configurations
   - Allows backend-specific parameters through `extra` map

2. **TTSRunner** (Interface): Implementation contract
   - Defines the operations any TTS implementation must support
   - Abstracts away the specifics of different TTS backends
   - Enforces a consistent API regardless of implementation

3. **TTSService**: Core business logic
   - Manages TTSRunner implementations
   - Provides simplified API for speech synthesis
   - Handles error cases and resource management
   - Independent of Android-specific code for testability

4. **Runner Implementations**: Backend-specific code
   - Implement the TTSRunner interface
   - Handle the details of specific TTS technologies
   - Translate between the common API and backend-specific requirements

## Package Structure

```
com.mtkresearch.breeze_app.tts/             - Core TTS implementation
├── TTSService.java                         - Business logic for TTS functionality
├── TTSRunner.java                          - Interface for TTS implementations
├── TTSConfig.java                          - Configuration data container
└── runners/                                - TTS backend implementations
    ├── SherpaTTSRunner.java                - Sherpa-onnx implementation
    └── CustomTTSRunner.java                - Custom implementation
```

## Using the Core TTS Components

### Basic Example with Sherpa TTS

```java
// Create a Sherpa TTS runner
SherpaTTSRunner runner = new SherpaTTSRunner(context);

// Create the TTS service with the runner
TTSService ttsService = new TTSService(runner);

// Create a TTS configuration
TTSConfig config = TTSConfig.createSherpaTTS(
    "/path/to/model.onnx",  // modelPath
    "/path/to/vocoder.onnx", // vocoderPath
    0,                      // speakerId
    1.0f                    // speed
);

// Update the model configuration
ttsService.updateModel(config);

// Synthesize speech
ttsService.speak("Hello, world!", audioData -> {
    // Process or play audio data
});

// Don't forget to release resources when done
ttsService.release();
```


### Switching Between Backends at Runtime

```java
// Start with one backend
TTSService ttsService = new TTSService(new SherpaTTSRunner(context));
ttsService.updateModel(TTSConfig.createSherpaTTS(...));

// Later, switch to another backend
TTSRunner newRunner = new CustomTTSRunner(context);
ttsService.setRunner(newRunner);
ttsService.updateModel(TTSConfig.createCustomTTS(...));
```

## Extending with New TTS Backends

To add a new TTS backend, implement the TTSRunner interface:

```java
package com.mtkresearch.breeze_app.tts.runners;

import android.content.Context;
import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSRunner;

import java.util.function.Consumer;

public class NewBackendRunner implements TTSRunner {
    private final Context context;
    private YourTTSEngine engine; // Your backend TTS engine
    
    public NewBackendRunner(Context context) {
        this.context = context;
        // Initialize your engine
    }
    
    @Override
    public void setModel(TTSConfig config) {
        // Convert TTSConfig to your engine's configuration
        // Example:
        String modelPath = config.modelPath;
        float speed = config.speed;
        
        // Configure your engine with these parameters
        engine.configure(modelPath, speed);
    }
    
    @Override
    public void synthesize(String text, Consumer<float[]> callback) {
        // Generate speech using your engine
        float[] audioData = engine.generateSpeech(text);
        
        // Return the audio data through the callback
        callback.accept(audioData);
    }
    
    @Override
    public int getSampleRate() {
        // Return your engine's sample rate
        return engine.getSampleRate();
    }
    
    @Override
    public void release() {
        // Clean up resources
        if (engine != null) {
            engine.shutdown();
            engine = null;
        }
    }
}
```

## Notes on Model Files

Sherpa TTS models can be downloaded from the sherpa-onnx releases page:
https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models

Models should be placed in the app's assets directory or external storage.

## Best Practices

1. **Resource Cleanup**
   - Call release() on TTSService when you're done with it
   - Properly manage the lifecycle to prevent memory leaks

2. **Error Handling**
   - Handle exceptions from TTS operations
   - Provide fallback behavior when TTS is unavailable

3. **Performance Considerations**
   - TTS synthesis can be computationally intensive
   - Consider caching frequently used phrases
