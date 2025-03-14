# Breeze AI Chat - Android

A mobile chat application that demonstrates the integration of various AI capabilities:

- **LLM (Language Model)**: Text generation with context-aware responses
- **VLM (Vision Language Model)**: Image understanding and analysis
- **ASR (Automatic Speech Recognition)**: Speech-to-text for voice input
- **TTS (Text-to-Speech)**: Voice output for accessibility

## Project Structure

The project follows a clean, modular architecture to make it easy to understand and integrate specific AI features into your own applications:

```
com.mtkresearch.breezeapp/
├── core/               - Core utilities and base classes
│   ├── utils/          - Common utilities
│   └── extensions/     - Kotlin extensions
├── data/               - Data management
│   ├── models/         - Data models
│   └── repository/     - Data sources and repositories
├── features/           - Modular AI features
│   ├── llm/            - Language Model feature
│   ├── vlm/            - Vision Language Model feature
│   ├── asr/            - Automatic Speech Recognition
│   └── tts/            - Text-to-Speech
└── ui/                 - User interface components
    ├── chat/           - Chat interface
    └── common/         - Shared UI components
```

## How to Integrate AI Features

### 1. Language Model (LLM)

To integrate the Language Model feature in your app:

```kotlin
// 1. Bind to the LLM service
val serviceIntent = Intent(context, LLMService::class.java)
context.bindService(serviceIntent, llmConnection, Context.BIND_AUTO_CREATE)

// 2. Create a service connection
private val llmConnection = object : ServiceConnection {
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val llmService = (service as LLMService.LocalBinder).getService()
        // Initialize the service
        lifecycleScope.launch {
            llmService.initialize()
        }
    }
    
    override fun onServiceDisconnected(name: ComponentName?) {
        // Handle disconnection
    }
}

// 3. Generate text with the service
llmService.generateText(
    prompt = "Your prompt here",
    callback = object : LLMService.StreamingResponseCallback {
        override fun onToken(token: String) {
            // Handle streaming tokens
        }
        
        override fun onComplete(fullResponse: String) {
            // Handle complete response
        }
        
        override fun onError(error: String) {
            // Handle errors
        }
    }
)
```

### 2. Vision Language Model (VLM)

To integrate image understanding:

```kotlin
// 1. Bind to the VLM service similar to LLM service

// 2. Process an image
lifecycleScope.launch {
    val description = vlmService.processImage(imageUri)
    // Use the description
}
```

### 3. Automatic Speech Recognition (ASR)

To integrate voice input:

```kotlin
// 1. Bind to the ASR service similar to LLM service

// 2. Collect transcription results
lifecycleScope.launch {
    asrService.transcription.collect { state ->
        when (state) {
            is ASRService.TranscriptionState.Listening -> {
                // Show listening indicator
            }
            is ASRService.TranscriptionState.PartialResult -> {
                // Update UI with partial result
                textView.text = state.text
            }
            is ASRService.TranscriptionState.FinalResult -> {
                // Use final transcription
                submitMessage(state.text)
            }
            is ASRService.TranscriptionState.Error -> {
                // Handle error
                showError(state.message)
            }
        }
    }
}

// 3. Start and stop recognition
asrService.startRecognition()
// Later
asrService.stopRecognition()
```

### 4. Text-to-Speech (TTS)

To integrate voice output:

```kotlin
// 1. Bind to the TTS service similar to LLM service

// 2. Speak text
ttsService.speak("Text to be spoken")

// 3. Track speaking state
lifecycleScope.launch {
    ttsService.ttsState.collect { state ->
        when (state) {
            is TTSService.TTSState.Speaking -> {
                // Show speaking indicator
            }
            is TTSService.TTSState.Completed -> {
                // Handle completion
            }
            is TTSService.TTSState.Error -> {
                // Handle error
            }
        }
    }
}
```

## Model Management

The app includes a `ModelManager` utility class to handle downloading and managing AI models:

```kotlin
// Initialize the manager
val modelManager = ModelManager(context)

// Get available models
val llmModels = modelManager.getAvailableModels(ModelType.LLM)

// Download a model
lifecycleScope.launch {
    val success = modelManager.downloadModel(modelUrl, fileName)
    if (success) {
        // Model downloaded successfully
    }
}

// Get path to a model file
val modelPath = modelManager.getModelPath(fileName)
```

## Extending the App

To add new AI capabilities:

1. Create a new feature module in the `features` package
2. Implement your AI engine as a subclass of `BaseEngineService`
3. Add your service to the AndroidManifest.xml
4. Create a new UI component that binds to your service

## Requirements

- Android 7.0 (API level 26) or above
- Gradle 8.0+
- Android Studio Arctic Fox or newer

## License

This project is open-source and available under the MIT License. 