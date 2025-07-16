# Quick Start Guide

## 🚀 Get Started in 5 Minutes

### For App Developers

#### 1. Install BreezeApp Router
```bash
# Install the router APK
adb install breeze-app-router.apk
```

#### 2. Add to Your Project
```kotlin
// build.gradle (app level)
dependencies {
    implementation project(':breeze-app-router')
}
```

#### 3. Basic Chat Implementation
```kotlin
class MainActivity : AppCompatActivity() {
    private var aiService: IAIRouterService? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Bind to AI service
        bindAIService()
        
        // Send a chat message
        sendButton.setOnClickListener {
            sendChatMessage(inputText.text.toString())
        }
    }
    
    private fun sendChatMessage(message: String) {
        val request = ChatRequest().apply {
            messages = listOf(ChatMessage().apply {
                role = "user"
                content = message
            })
            stream = true
        }
        
        aiService?.sendChatRequest(UUID.randomUUID().toString(), request)
    }
    
    // See API_REFERENCE.md for complete implementation
}
```

### For AI Engineers

#### 1. Create Your Runner
```kotlin
class MyCustomRunner : BaseRunner {
    override fun load(config: ModelConfig): Boolean {
        // Load your AI model
        return true
    }
    
    override fun run(input: InferenceRequest): InferenceResult {
        // Process the request
        return InferenceResult.success(mapOf("text" to "Hello!"))
    }
    
    override fun getCapabilities() = listOf(CapabilityType.LLM)
    // ... implement other methods
}
```

#### 2. Register Your Runner
```json
// assets/runner_config.json
{
  "runners": [
    {
      "name": "MyCustomRunner",
      "class": "com.yourpackage.MyCustomRunner",
      "capabilities": ["LLM"]
    }
  ]
}
```

## 📚 Next Steps

- **App Developers**: Read [API_REFERENCE.md](API_REFERENCE.md)
- **AI Engineers**: Read [RUNNER_DEVELOPMENT.md](RUNNER_DEVELOPMENT.md)
- **Contributors**: Read [CONTRIBUTING.md](CONTRIBUTING.md)
- **Architecture**: Read [ARCHITECTURE_GUIDELINES.md](ARCHITECTURE_GUIDELINES.md)

## 🎯 Examples

Check out the `/examples` folder for:
- Simple chat app
- Voice assistant implementation
- Custom runner examples
- Integration patterns