# 🧩 Runner Development Guide

This guide walks you through creating custom AI runners for the BreezeApp AI Router.

## 📋 What is a Runner?

A **Runner** is a self-contained AI implementation that:
- ✅ Implements the `BaseRunner` interface
- ✅ Handles a specific AI capability (LLM, VLM, ASR, TTS, Guardian)
- ✅ Manages its own model loading and lifecycle
- ✅ Provides consistent error handling and responses

## 🚀 Quick Start: Your First Runner

### 1. Implement BaseRunner

```kotlin
class MyCustomLLMRunner : BaseRunner {
    private var isModelLoaded = false
    private var modelConfig: ModelConfig? = null
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            // Load your AI model here
            modelConfig = config
            // Initialize model files, weights, etc.
            isModelLoaded = true
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            false
        }
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        if (!isModelLoaded) {
            return InferenceResult.error(RunnerError.modelNotLoaded())
        }
        
        return try {
            val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as? String 
                ?: return InferenceResult.error(RunnerError.invalidInput("Missing text input"))
            
            // Run your AI inference here
            val output = performInference(inputText)
            
            InferenceResult.success(
                outputs = mapOf(InferenceResult.OUTPUT_TEXT to output),
                metadata = mapOf(
                    InferenceResult.META_PROCESSING_TIME_MS to 150,
                    InferenceResult.META_MODEL_NAME to "my-custom-model"
                )
            )
        } catch (e: Exception) {
            InferenceResult.error(RunnerError.runtimeError("Inference failed", e))
        }
    }
    
    override fun unload() {
        // Clean up resources
        isModelLoaded = false
        modelConfig = null
    }
    
    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.LLM)
    
    override fun isLoaded(): Boolean = isModelLoaded
    
    override fun getRunnerInfo(): RunnerInfo = RunnerInfo(
        name = "MyCustomLLMRunner",
        version = "1.0.0",
        capabilities = getCapabilities(),
        description = "Custom LLM implementation",
        isMock = false
    )
    
    private fun performInference(input: String): String {
        // Your AI inference logic here
        return "Generated response for: $input"
    }
}
```

### 2. Register Your Runner

Add to `assets/runner_config.json`:

```json
{
  "runners": [
    {
      "name": "my_custom_llm",
      "class": "com.yourpackage.MyCustomLLMRunner",
      "capabilities": ["LLM"],
      "priority": 10,
      "is_real": true
    }
  ]
}
```

### 3. Test Your Runner

```kotlin
@Test
fun testCustomRunner() {
    val runner = MyCustomLLMRunner()
    
    // Test loading
    val config = ModelConfig("test-model")
    assertTrue(runner.load(config))
    assertTrue(runner.isLoaded())
    
    // Test inference
    val request = InferenceRequest(
        sessionId = "test",
        inputs = mapOf(InferenceRequest.INPUT_TEXT to "Hello")
    )
    
    val result = runner.run(request)
    assertNull(result.error)
    assertNotNull(result.outputs[InferenceResult.OUTPUT_TEXT])
    
    // Test cleanup
    runner.unload()
    assertFalse(runner.isLoaded())
}
```

## 🌊 Adding Streaming Support

For real-time responses (LLM text generation, ASR transcription):

```kotlin
class StreamingLLMRunner : BaseRunner, FlowStreamingRunner {
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as String
        
        // Generate response incrementally
        val fullResponse = generateFullResponse(inputText)
        val tokens = tokenize(fullResponse)
        
        var partialText = ""
        tokens.forEachIndexed { index, token ->
            partialText += token
            
            emit(InferenceResult.textOutput(
                text = partialText,
                metadata = mapOf(
                    InferenceResult.META_PARTIAL_TOKENS to index + 1,
                    InferenceResult.META_SESSION_ID to input.sessionId
                ),
                partial = index < tokens.size - 1
            ))
            
            // Add realistic delay
            delay(50)
        }
    }
    
    // Also implement standard run() method for non-streaming
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        return if (stream) {
            // For streaming, collect all results and return the final one
            runBlocking {
                runAsFlow(input).last()
            }
        } else {
            // Standard non-streaming inference
            val inputText = input.inputs[InferenceRequest.INPUT_TEXT] as String
            val output = generateFullResponse(inputText)
            InferenceResult.textOutput(output)
        }
    }
}
```

## 🎯 Capability-Specific Examples

### 🧠 LLM Runner (Text Generation)

```kotlin
class ExecutorchLLMRunner : BaseRunner, FlowStreamingRunner {
    private var model: ExecutorchModel? = null
    private var tokenizer: Tokenizer? = null
    
    override fun load(config: ModelConfig): Boolean {
        return try {
            val modelPath = config.files[ModelConfig.FILE_MODEL] 
                ?: throw IllegalArgumentException("Model file path required")
            
            model = ExecutorchModel.load(modelPath)
            tokenizer = Tokenizer.load(config.files[ModelConfig.FILE_TOKENIZER])
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load LLM model", e)
            false
        }
    }
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        val prompt = input.inputs[InferenceRequest.INPUT_TEXT] as String
        val maxTokens = input.params[InferenceRequest.PARAM_MAX_TOKENS] as? Int ?: 100
        val temperature = input.params[InferenceRequest.PARAM_TEMPERATURE] as? Float ?: 0.7f
        
        val tokenIds = tokenizer?.encode(prompt) ?: throw IllegalStateException("Tokenizer not loaded")
        var generatedText = prompt
        
        repeat(maxTokens) { step ->
            val nextTokenId = model?.generateNextToken(tokenIds, temperature) 
                ?: throw IllegalStateException("Model not loaded")
            
            val nextToken = tokenizer?.decode(listOf(nextTokenId)) ?: ""
            generatedText += nextToken
            
            emit(InferenceResult.textOutput(
                text = generatedText,
                metadata = mapOf(
                    InferenceResult.META_TOKEN_COUNT to step + 1,
                    InferenceResult.META_MODEL_NAME to "executorch-llm"
                ),
                partial = step < maxTokens - 1
            ))
            
            // Stop if end token
            if (nextTokenId == tokenizer?.eosTokenId()) break
        }
    }
}
```

### 👁️ VLM Runner (Vision-Language)

```kotlin
class MediaPipeVLMRunner : BaseRunner {
    private var visionModel: MediaPipeVision? = null
    private var languageModel: MediaPipeLanguage? = null
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        val imageBytes = input.inputs[InferenceRequest.INPUT_IMAGE] as? ByteArray
            ?: return InferenceResult.error(RunnerError.invalidInput("Image data required"))
        
        val question = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            ?: return InferenceResult.error(RunnerError.invalidInput("Question text required"))
        
        return try {
            // Process image
            val imageFeatures = visionModel?.extractFeatures(imageBytes)
                ?: throw IllegalStateException("Vision model not loaded")
            
            // Generate response
            val response = languageModel?.generateResponse(imageFeatures, question)
                ?: throw IllegalStateException("Language model not loaded")
            
            InferenceResult.success(
                outputs = mapOf(
                    InferenceResult.OUTPUT_TEXT to response,
                    "confidence" to 0.85,
                    "image_size" to "${imageBytes.size} bytes"
                ),
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to "mediapipe-vlm",
                    InferenceResult.META_PROCESSING_TIME_MS to 200
                )
            )
        } catch (e: Exception) {
            InferenceResult.error(RunnerError.runtimeError("VLM inference failed", e))
        }
    }
}
```

### 🎤 ASR Runner (Speech Recognition)

```kotlin
class SherpaASRRunner : BaseRunner, FlowStreamingRunner {
    private var asrModel: SherpaOnnxModel? = null
    
    override fun runAsFlow(input: InferenceRequest): Flow<InferenceResult> = flow {
        val audioBytes = input.inputs[InferenceRequest.INPUT_AUDIO] as? ByteArray
            ?: throw IllegalArgumentException("Audio data required")
        
        val language = input.params[InferenceRequest.PARAM_LANGUAGE] as? String ?: "en"
        
        // Process audio in chunks for streaming recognition
        val chunkSize = 1600 // 100ms at 16kHz
        val audioChunks = audioBytes.toList().chunked(chunkSize)
        
        var transcription = ""
        
        audioChunks.forEachIndexed { index, chunk ->
            val partialResult = asrModel?.processAudioChunk(chunk.toByteArray(), language)
            
            if (!partialResult.isNullOrEmpty()) {
                transcription = partialResult
                
                emit(InferenceResult.textOutput(
                    text = transcription,
                    metadata = mapOf(
                        InferenceResult.META_SEGMENT_INDEX to index,
                        "confidence" to 0.9,
                        "language" to language
                    ),
                    partial = index < audioChunks.size - 1
                ))
            }
        }
    }
}
```

### 🔊 TTS Runner (Text-to-Speech)

```kotlin
class SherpaTTSRunner : BaseRunner {
    private var ttsModel: SherpaTTSModel? = null
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            ?: return InferenceResult.error(RunnerError.invalidInput("Text required"))
        
        val voice = input.params["voice"] as? String ?: "default"
        val speed = input.params["speed"] as? Float ?: 1.0f
        
        return try {
            val audioBytes = ttsModel?.synthesize(text, voice, speed)
                ?: throw IllegalStateException("TTS model not loaded")
            
            InferenceResult.success(
                outputs = mapOf(
                    InferenceResult.OUTPUT_AUDIO to audioBytes,
                    "duration_ms" to calculateDuration(audioBytes),
                    "sample_rate" to 22050
                ),
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to "sherpa-tts",
                    "voice" to voice,
                    "speed" to speed
                )
            )
        } catch (e: Exception) {
            InferenceResult.error(RunnerError.runtimeError("TTS synthesis failed", e))
        }
    }
}
```

### 🛡️ Guardian Runner (Content Safety)

```kotlin
class TensorFlowGuardianRunner : BaseRunner {
    private var safetyModel: TensorFlowLiteModel? = null
    private var toxicityThreshold = 0.7f
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        val text = input.inputs[InferenceRequest.INPUT_TEXT] as? String
            ?: return InferenceResult.error(RunnerError.invalidInput("Text required"))
        
        return try {
            val features = extractTextFeatures(text)
            val scores = safetyModel?.runInference(features)
                ?: throw IllegalStateException("Safety model not loaded")
            
            val isSafe = scores.maxOrNull() ?: 0f < toxicityThreshold
            val riskCategories = identifyRiskCategories(scores)
            
            InferenceResult.success(
                outputs = mapOf(
                    "is_safe" to isSafe,
                    "risk_score" to (scores.maxOrNull() ?: 0f),
                    "risk_categories" to riskCategories,
                    "action" to if (isSafe) "allow" else "block"
                ),
                metadata = mapOf(
                    InferenceResult.META_MODEL_NAME to "tensorflow-guardian",
                    "threshold" to toxicityThreshold,
                    InferenceResult.META_PROCESSING_TIME_MS to 25
                )
            )
        } catch (e: Exception) {
            InferenceResult.error(RunnerError.runtimeError("Safety check failed", e))
        }
    }
}
```

## 🔧 Advanced Features

### Device Support Detection

For hardware-specific runners:

```kotlin
class GPULLMRunner : BaseRunner {
    companion object {
        @JvmStatic
        fun isSupported(): Boolean {
            return try {
                // Check for GPU support
                val gpuDelegate = GpuDelegate()
                gpuDelegate.close()
                true
            } catch (e: Exception) {
                false
            }
        }
    }
    
    override fun load(config: ModelConfig): Boolean {
        if (!isSupported()) {
            Log.w(TAG, "GPU not supported on this device")
            return false
        }
        // Load with GPU acceleration
        return loadWithGPU(config)
    }
}
```

### Configuration Parameters

```kotlin
override fun load(config: ModelConfig): Boolean {
    // Read custom parameters
    val batchSize = config.parameters[PARAM_BATCH_SIZE] as? Int ?: 1
    val precision = config.parameters[PARAM_PRECISION] as? String ?: "fp16"
    val useQuantization = config.parameters[PARAM_QUANTIZATION] as? Boolean ?: false
    
    return loadModel(config.modelPath, batchSize, precision, useQuantization)
}
```

### Memory Management

```kotlin
class MemoryEfficientRunner : BaseRunner {
    private var modelReference: WeakReference<Model>? = null
    
    override fun load(config: ModelConfig): Boolean {
        val model = loadLargeModel(config)
        modelReference = WeakReference(model)
        return true
    }
    
    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        val model = modelReference?.get() 
            ?: return InferenceResult.error(RunnerError.modelNotLoaded("Model was garbage collected"))
        
        // Use model for inference
        return performInference(model, input)
    }
    
    override fun unload() {
        modelReference?.get()?.dispose()
        modelReference = null
        System.gc() // Suggest garbage collection
    }
}
```

## 🧪 Testing Your Runner

### Unit Tests

```kotlin
class MyRunnerTest {
    
    @Test
    fun `load returns true with valid config`() {
        val runner = MyCustomLLMRunner()
        val config = ModelConfig(
            modelName = "test-model",
            modelPath = getTestModelPath()
        )
        
        assertTrue(runner.load(config))
        assertTrue(runner.isLoaded())
        
        runner.unload()
    }
    
    @Test
    fun `run returns error when not loaded`() {
        val runner = MyCustomLLMRunner()
        val request = InferenceRequest("test", mapOf("text" to "hello"))
        
        val result = runner.run(request)
        
        assertNotNull(result.error)
        assertEquals("E001", result.error?.code) // MODEL_NOT_LOADED
    }
    
    @Test
    fun `streaming produces partial results`() = runTest {
        val runner = MyStreamingRunner()
        runner.load(testConfig)
        
        val request = InferenceRequest("test", mapOf("text" to "hello"))
        val results = runner.runAsFlow(request).toList()
        
        assertTrue(results.size > 1)
        assertTrue(results.dropLast(1).all { it.partial })
        assertFalse(results.last().partial)
    }
}
```

### Integration Tests

```kotlin
@Test
fun `runner integrates with registry`() {
    val registry = RunnerRegistry(mockLogger)
    registry.register("my_runner", ::MyCustomLLMRunner)
    
    val runner = registry.getRunnerForCapability(CapabilityType.LLM)
    assertNotNull(runner)
    assertTrue(runner is MyCustomLLMRunner)
}
```

## 🚀 Performance Best Practices

### ✅ Do's

- **Lazy Load Models**: Only load when needed
- **Reuse Instances**: Same runner serves multiple requests
- **Use Appropriate Data Types**: Float16 for mobile, quantization
- **Implement Proper Cleanup**: Release native resources
- **Handle Interruptions**: Support request cancellation
- **Cache Tokenizers**: Reuse tokenization across requests

### ❌ Don'ts

- **Block UI Thread**: Always use background threads
- **Load Multiple Models**: One model per runner instance
- **Ignore Memory Limits**: Monitor and respect device constraints
- **Skip Error Handling**: Always return structured errors
- **Forget Lifecycle**: Implement proper load/unload

## 📦 Packaging & Distribution

### Native Libraries

```kotlin
// In your runner's companion object
companion object {
    init {
        try {
            System.loadLibrary("your_native_lib")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }
}
```

### Model Assets

```
src/main/assets/models/
├── your_model.onnx
├── tokenizer.json
└── config.json
```

### Gradle Dependencies

```kotlin
dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.13.0")
    implementation("ai.onnxruntime:onnxruntime-android:1.15.1")
    implementation("com.google.mediapipe:solution-core:0.10.5")
}
```

## 🔗 Integration Examples

Check out real examples in the codebase:
- `MockLLMRunner` - Complete streaming implementation
- `MockVLMRunner` - Image analysis patterns
- `MockASRRunner` - Audio processing flow
- `MockTTSRunner` - Audio generation patterns
- `MockGuardrailRunner` - Safety check implementation

## 🎯 Next Steps

1. **Start with a Mock**: Implement your interface first with mock data
2. **Add Real Implementation**: Replace mock logic with actual AI
3. **Test Thoroughly**: Unit tests, integration tests, device testing
4. **Optimize Performance**: Profile and optimize for mobile constraints
5. **Document Your Runner**: Help others understand and use it

---

🎉 **Ready to build?** Start with the examples above and customize for your specific AI model and requirements!

## Appendix C: Headless Testing with ADB

For advanced development and debugging, you can test the `breeze-app-router` service directly from the command line using `adb`, without needing a separate client UI application. This is especially useful when developing and testing new runners.

### 1. Install and Start the Service

First, ensure the router APK is installed and the service is running.

```sh
# Define your package name
PACKAGE_NAME="com.mtkresearch.breezeapp.router"

# Install the APK
adb install -r -d /path/to/your/breeze-app-router-debug.apk

# Start the dummy activity to ensure the app process is active
adb shell am start -n "$PACKAGE_NAME/com.mtkresearch.breezeapp.router.ui.DummyLauncherActivity"

# Start the service
adb shell am start-service -n "$PACKAGE_NAME/com.mtkresearch.breezeapp.router.AIRouterService"
```

### 2. Send a Test Request via Broadcast

You can send a test request using a broadcast intent. This is the simplest way to trigger a runner.

#### Example: Test an LLM Runner

```sh
adb shell am broadcast -a "com.mtkresearch.breezeapp.router.DEBUG_COMMAND" \
  -p $PACKAGE_NAME \
  --es "command" "generate_text" \
  --es "prompt" "Hello, runner! This is a test from ADB."
```

#### Example: Test an ASR Runner

Make sure you have an audio file on your device first (`adb push my_audio.wav /sdcard/Download/test_audio.wav`).

```sh
adb shell am broadcast -a "com.mtkresearch.breezeapp.router.DEBUG_COMMAND" \
  -p $PACKAGE_NAME \
  --es "command" "recognize_speech" \
  --es "audio_path" "/sdcard/Download/test_audio.wav"
```

### 3. Monitor the Output

To see the results and any debug logs from your runner, monitor logcat:

```sh
# Filter logs by the service tag
adb logcat | grep "AIRouterService"

# Or, if you added custom tags in your runner:
adb logcat | grep "MyNewRunnerTag"
```

This headless approach allows for rapid testing cycles, helping you quickly validate your runner's logic, performance, and integration with the router's core system. 