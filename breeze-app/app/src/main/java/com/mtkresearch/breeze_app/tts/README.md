# Text-to-Speech (TTS) Module

This package provides a modular, extensible TTS system with support for multiple backends including Sherpa-onnx and Android's Google TTS.

## Package Structure

```
com.mtkresearch.breeze_app.tts
├── TTSService.java          - Main service class
├── TTSRunner.java           - Interface for TTS implementations
├── TTSConfig.java           - Configuration class
├── PCMUtils.java            - Audio data conversion utilities
└── runners/                 - TTS implementation runners
    ├── SherpaTTSRunner.java - Sherpa-onnx implementation
    ├── GoogleTTSRunner.java - Google TTS implementation
    └── MtkTTSRunner.java    - Simple example implementation
```

## Overview

The TTS module consists of the following components:

- `TTSRunner`: Interface that any TTS implementation must implement
- `TTSConfig`: Configuration class for TTS settings
- `TTSService`: Main service for managing TTS functionality
- `runners.SherpaTTSRunner`: Implementation using the sherpa-onnx library
- `runners.GoogleTTSRunner`: Implementation using Android's built-in Google TTS
- `runners.MtkTTSRunner`: Simple example implementation for demonstration
- `PCMUtils`: Utility for audio data conversion

## Sherpa-onnx Integration

This module uses the sherpa-onnx library from the Git submodule at `external/sherpa-onnx`. The JAR file and native libraries need to be included in the project.

## Usage Examples

### Basic Example with Sherpa TTS

```java
import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSService;
import com.mtkresearch.breeze_app.tts.runners.SherpaTTSRunner;
import java.util.HashMap;
import java.util.Map;

// Get application context
Context context = getApplicationContext();

// Create a TTS configuration for Sherpa
Map<String, String> extraParams = new HashMap<>();
extraParams.put("numThreads", "2");
extraParams.put("lexicon", "/path/to/lexicon.txt");
extraParams.put("dictDir", "/path/to/dict");

TTSConfig config = new TTSConfig(
    "sherpa",               // backend
    "/path/to/model.onnx",  // modelPath
    "/path/to/vocoder.onnx", // vocoderPath
    0,                      // speakerId
    1.0f,                   // speed
    null,                   // voiceName (not used for Sherpa)
    null,                   // apiKey (not used for Sherpa)
    extraParams             // extra parameters
);

// Create a Sherpa TTS runner
SherpaTTSRunner runner = new SherpaTTSRunner(context, config);

// Create the TTS service with the runner
TTSService ttsService = new TTSService(runner);

// Synthesize speech
ttsService.speak("Hello, world!", pcmData -> {
    // Use the PCM data, e.g., play it or save it to a file
    AudioTrack audioTrack = /* initialize AudioTrack */;
    audioTrack.write(pcmData, 0, pcmData.length);
});

// Don't forget to release resources when done
ttsService.release();
```

### Using Google TTS

```java
import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSService;
import com.mtkresearch.breeze_app.tts.runners.GoogleTTSRunner;

// Get application context
Context context = getApplicationContext();

// Create a TTS configuration for Google
TTSConfig config = TTSConfig.createGoogleTTS("en-us-x-sfg#female_1-local", 1.0f);

// Create a Google TTS runner
GoogleTTSRunner runner = new GoogleTTSRunner(context, config);

// Create the TTS service
TTSService ttsService = new TTSService(runner);

// Synthesize speech (Note: Google TTS will play audio directly)
ttsService.speak("Hello, world!", pcmData -> {
    // pcmData will be empty for Google TTS
});

// Release resources
ttsService.release();
```

### Using MTK Example Runner

```java
import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSService;
import com.mtkresearch.breeze_app.tts.runners.MtkTTSRunner;

// Get application context
Context context = getApplicationContext();

// Create a simple configuration
TTSConfig config = new TTSConfig(
    "mtk",                  // backend
    "dummy_model_path",     // modelPath (not actually used)
    null,                   // vocoderPath
    0,                      // speakerId
    1.0f,                   // speed
    null,                   // voiceName
    null,                   // apiKey
    null                    // extra parameters
);

// Create the MTK TTS runner
MtkTTSRunner runner = new MtkTTSRunner(context, config);

// Create the TTS service
TTSService ttsService = new TTSService(runner);

// Synthesize speech using the dummy implementation
ttsService.speak("This is a test", pcmData -> {
    // pcmData will contain a UTF-8 byte representation of a fixed string
    String returnedText = new String(pcmData, StandardCharsets.UTF_8);
    Log.d("MTK_TTS_TEST", "Received: " + returnedText);
});

// Release resources
ttsService.release();
```

### Switching Between Backends

```java
import com.mtkresearch.breeze_app.tts.TTSConfig;
import com.mtkresearch.breeze_app.tts.TTSService;
import com.mtkresearch.breeze_app.tts.runners.SherpaTTSRunner;
import com.mtkresearch.breeze_app.tts.runners.GoogleTTSRunner;

// Get application context
Context context = getApplicationContext();

// Create a TTS service with initial Sherpa TTS runner
TTSService ttsService = new TTSService(new SherpaTTSRunner(context, sherpaConfig));

// Later, switch to Google TTS
GoogleTTSRunner googleRunner = new GoogleTTSRunner(context, googleConfig);
ttsService.switchRunner(googleRunner);
```

## Notes on Model Files

Sherpa TTS models can be downloaded from the sherpa-onnx releases page:
https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models

Models should be placed in the app's assets directory or external storage.

## Extending with Your Own Runner

To create your own TTS runner implementation:

1. Implement the TTSRunner interface
2. Handle the configuration conversion within your runner
3. Implement the synthesis logic
4. Follow the context pattern established in existing runners

```java
public class CustomTTSRunner implements TTSRunner {
    private final Context context;
    
    public CustomTTSRunner(Context context) {
        this.context = context;
    }
    
    @Override
    public void setModel(TTSConfig config) {
        // Convert TTSConfig to your TTS engine's configuration format
        // Initialize your TTS engine
    }
    
    @Override
    public void synthesize(String text, Consumer<byte[]> callback) {
        // Convert text to speech using your TTS engine
        // Call the callback with the resulting audio data
    }
    
    @Override
    public void release() {
        // Clean up resources
    }
} 