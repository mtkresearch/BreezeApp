# 🚀 BreezeApp v2.0 Quick Start Guide

*Get up and running with BreezeApp's AI Router architecture in 5 minutes*

## 🎯 What You'll Learn

- How to set up the development environment
- Understanding the dual-process architecture
- Building your first AI-powered chat interface
- Testing your implementation

---

## 📋 Prerequisites

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **Android SDK**: Level 34+
- **Kotlin**: 1.9+
- **Gradle**: 8.4+
- **Java**: 8+

---

## 🏃‍♂️ 5-Minute Setup

### Step 1: Clone and Build

```bash
# Clone the repository
git clone https://github.com/your-org/BreezeApp.git
cd BreezeApp/BreezeApp_v2

# Build the project
./gradlew build

# Run tests to verify setup
./gradlew test
```

### Step 2: Understand the Architecture

BreezeApp v2.0 uses a **dual-process architecture**:

```
┌─────────────────┐    IPC     ┌─────────────────┐
│   UI Process    │◄────────►│  Router Process │
│                 │   (AIDL)   │                 │
│ • Chat UI       │            │ • AI Engine    │
│ • User Input    │            │ • Model Mgmt   │
│ • Display       │            │ • Processing   │
└─────────────────┘            └─────────────────┘
```

**Benefits**:
- 🔒 UI crashes don't affect AI processing
- ⚡ Non-blocking AI operations
- 🧪 Easy testing and debugging

### Step 3: Key Components

| Component | Purpose | Location |
|-----------|---------|----------|
| `shared-contracts` | Communication protocol | `/shared-contracts/` |
| `breeze-app-router` | AI processing service | `/breeze-app-router/` |
| `breeze-app-ui` | User interface (planned) | `/breeze-app-ui/` |

---

## 🛠️ Your First AI Chat Interface

### 1. Add Dependency

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":shared-contracts"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
```

### 2. Create AI Router Client

```kotlin
class SimpleAIClient(private val context: Context) {
    private var service: IAIRouterService? = null
    private var listener: IAIRouterListener? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IAIRouterService.Stub.asInterface(binder)
            
            // Initialize the service
            val config = Configuration(
                apiVersion = 2,
                logLevel = 3,
                maxTokens = 1024,
                temperature = 0.7f
            )
            service?.initialize(config)
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }
    
    fun connect(): Boolean {
        val intent = Intent().apply {
            component = ComponentName(
                "com.mtkresearch.breezeapp.router",
                "com.mtkresearch.breezeapp.router.AIRouterService"
            )
        }
        return context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    fun sendMessage(text: String, callback: (String) -> Unit) {
        // Create response listener
        listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse) {
                if (response.state == AIResponse.ResponseState.COMPLETED) {
                    callback(response.text)
                }
            }
        }
        
        // Register listener
        service?.registerListener(listener)
        
        // Send request
        val request = AIRequest(
            id = UUID.randomUUID().toString(),
            text = text,
            sessionId = "demo-session",
            timestamp = System.currentTimeMillis()
        )
        
        service?.sendMessage(request)
    }
    
    fun disconnect() {
        listener?.let { service?.unregisterListener(it) }
        context.unbindService(serviceConnection)
    }
}
```

### 3. Create Simple Chat Activity

```kotlin
class SimpleChatActivity : AppCompatActivity() {
    private lateinit var aiClient: SimpleAIClient
    private lateinit var messagesAdapter: ArrayAdapter<String>
    private val messages = mutableListOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_simple_chat)
        
        setupUI()
        setupAIClient()
    }
    
    private fun setupUI() {
        val messagesListView = findViewById<ListView>(R.id.messagesListView)
        val inputEditText = findViewById<EditText>(R.id.inputEditText)
        val sendButton = findViewById<Button>(R.id.sendButton)
        
        messagesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messages)
        messagesListView.adapter = messagesAdapter
        
        sendButton.setOnClickListener {
            val userMessage = inputEditText.text.toString()
            if (userMessage.isNotBlank()) {
                addMessage("You: $userMessage")
                aiClient.sendMessage(userMessage) { aiResponse ->
                    runOnUiThread {
                        addMessage("AI: $aiResponse")
                    }
                }
                inputEditText.text.clear()
            }
        }
    }
    
    private fun setupAIClient() {
        aiClient = SimpleAIClient(this)
        if (!aiClient.connect()) {
            Toast.makeText(this, "Failed to connect to AI Router", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun addMessage(message: String) {
        messages.add(message)
        messagesAdapter.notifyDataSetChanged()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        aiClient.disconnect()
    }
}
```

### 4. Layout File

Create `res/layout/activity_simple_chat.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <ListView
        android:id="@+id/messagesListView"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:layout_marginBottom="16dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <EditText
            android:id="@+id/inputEditText"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="Type your message..."
            android:layout_marginEnd="8dp" />

        <Button
            android:id="@+id/sendButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Send" />

    </LinearLayout>

</LinearLayout>
```

---

## 🧪 Testing Your Implementation

### 1. Run the App

```bash
# Install AI Router Service first
./gradlew :breeze-app-router:installDebug

# Install your UI app
./gradlew :your-app:installDebug
```

### 2. Test the Connection

1. Open your chat activity
2. Type "Hello AI" and press Send
3. You should see a mock response from the AI Router

### 3. Check Logs

```bash
# Monitor AI Router Service logs
adb logcat | grep "AIRouterService"

# Monitor your app logs
adb logcat | grep "YourAppTag"
```

---

## 🔍 What's Happening Under the Hood?

1. **Your UI app** creates an `AIRequest` with your message
2. **AIDL IPC** securely sends the request to the AI Router Service
3. **AI Router Service** processes the request (currently with mock responses)
4. **Service sends back** an `AIResponse` via the listener callback
5. **Your UI updates** with the AI response

---

## 🚀 Next Steps

Now that you have a basic chat interface working:

### Immediate Next Steps
1. **Explore the API**: Read [`docs/api.md`](api.md) for comprehensive API documentation
2. **Add Error Handling**: Implement proper error handling for network issues
3. **Enhance UI**: Create a more sophisticated chat interface
4. **Add Features**: Support for images, voice input, etc.

### Advanced Topics
1. **Custom Configuration**: Customize AI model parameters
2. **Binary Data**: Send images and audio to the AI
3. **Streaming Responses**: Handle real-time AI responses
4. **Production Deployment**: Prepare for production use

### Useful Resources
- 📖 **[API Reference](api.md)**: Complete API documentation
- 🏗️ **[Developer Guide](developer-guide.md)**: In-depth development guide
- 🗺️ **[Refactoring Plan](refactoring_plan.md)**: Understanding the project roadmap
- 🎨 **[Client API Spec](client_api_spec.md)**: UI layer architecture details

---

## ❓ Need Help?

### Common Issues

**Q: "Service not binding"**  
A: Check that both apps are signed with the same certificate and permissions are declared correctly.

**Q: "RemoteException when calling service"**  
A: Verify the AI Router Service is running and implement reconnection logic.

**Q: "No response from AI"**  
A: Check logs for errors and ensure the listener is properly registered.

### Getting Support

- 📋 **Issues**: Report bugs on GitHub Issues
- 💬 **Discussions**: Join GitHub Discussions for questions
- 📧 **Contact**: Reach out to the development team

---

**🎉 Congratulations!** You now have a working AI-powered chat interface using BreezeApp v2.0's architecture.

*Last Updated: 2024-12-19* 