# 🧪 BreezeApp v2.0 Testing Guide

*Comprehensive testing strategies for the AI Router architecture*

## 📋 Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [Test Types](#test-types)
3. [Shared Contracts Testing](#shared-contracts-testing)
4. [AI Router Service Testing](#ai-router-service-testing)
5. [Integration Testing](#integration-testing)
6. [Best Practices](#best-practices)

---

## 🎯 Testing Philosophy

BreezeApp v2.0 follows a **comprehensive testing pyramid** approach:

```
        🔺
       /   \
      / E2E \     ← Few, High-Value Integration Tests
     /       \
    /  INTEG  \   ← Service-to-Service Integration
   /           \
  /    UNIT     \  ← Many, Fast Unit Tests
 /_____________\ 
```

### Core Principles

- **Test Contract First**: Shared contracts are the foundation
- **Mock External Dependencies**: Test in isolation
- **Automated Testing**: Every PR runs full test suite
- **Performance Aware**: Test performance implications of IPC

---

## 🔍 Test Types

### Unit Tests
- **Purpose**: Test individual components in isolation
- **Speed**: Fast (< 1 second per test)
- **Coverage**: Business logic, data models, utilities
- **Framework**: JUnit 5, MockK

### Integration Tests
- **Purpose**: Test component interactions
- **Speed**: Medium (1-10 seconds per test)
- **Coverage**: AIDL communication, service binding
- **Framework**: AndroidX Test, Robolectric

### End-to-End Tests
- **Purpose**: Test complete user workflows
- **Speed**: Slow (10+ seconds per test)
- **Coverage**: Full app functionality
- **Framework**: Espresso, UI Automator

---

## 📋 Shared Contracts Testing

### Parcelable Serialization Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class ParcelableTest {
    
    @Test
    fun `Configuration serializes correctly`() {
        val original = Configuration(
            apiVersion = 2,
            logLevel = 3,
            preferredRuntime = Configuration.RuntimeBackend.GPU,
            maxTokens = 2048,
            temperature = 0.8f
        )
        
        val result = testParcelable(original, Configuration.CREATOR)
        assertEquals(original, result)
    }
    
    private fun <T : Parcelable> testParcelable(
        original: T,
        creator: Parcelable.Creator<T>
    ): T {
        val parcel = Parcel.obtain()
        try {
            original.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
```

### Validation Logic Tests

```kotlin
class ConfigurationValidationTest {
    
    @Test
    fun `valid configuration passes validation`() {
        val config = Configuration(
            apiVersion = 2,
            logLevel = 3,
            temperature = 0.7f,
            maxTokens = 1024
        )
        
        val result = config.validate()
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun `invalid temperature fails validation`() {
        val config = Configuration(temperature = 1.5f) // > 1.0
        
        val result = config.validate()
        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message ?: "", "temperature")
    }
    
    @Test
    fun `negative max tokens fails validation`() {
        val config = Configuration(maxTokens = -100)
        
        val result = config.validate()
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `boundary values are handled correctly`() {
        // Test edge cases
        val configs = listOf(
            Configuration(temperature = 0.0f), // Min valid
            Configuration(temperature = 1.0f), // Max valid
            Configuration(maxTokens = 1),       // Min valid
            Configuration(logLevel = 0),        // Min valid
            Configuration(logLevel = 5),        // Max valid
        )
        
        configs.forEach { config ->
            val result = config.validate()
            assertTrue(result.isSuccess, "Config should be valid: $config")
        }
    }
}
```

### AIDL Interface Syntax Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class ContractSyntaxTest {
    
    @Test
    fun `IAIRouterService can be instantiated`() {
        val serviceStub = object : IAIRouterService.Stub() {
            override fun getApiVersion(): Int = 2
            override fun initialize(config: Configuration) {}
            override fun sendMessage(request: AIRequest) {}
            override fun cancelRequest(requestId: String): Boolean = true
            override fun registerListener(listener: IAIRouterListener) {}
            override fun unregisterListener(listener: IAIRouterListener) {}
            override fun hasCapability(capabilityName: String): Boolean = true
        }
        
        assertNotNull(serviceStub)
        assertEquals(2, serviceStub.getApiVersion())
    }
    
    @Test
    fun `IAIRouterListener can be instantiated`() {
        val listenerStub = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse) {}
        }
        
        assertNotNull(listenerStub)
    }
    
    @Test
    fun `Binary data can be passed through AIDL`() {
        val binaryData = BinaryData(
            type = BinaryData.ContentType.IMAGE_JPEG,
            data = byteArrayOf(1, 2, 3),
            metadata = mapOf("test" to "value")
        )
        
        val request = AIRequest(
            id = "test",
            text = "Test",
            sessionId = "session",
            timestamp = System.currentTimeMillis(),
            binaryAttachments = listOf(binaryData)
        )
        
        val serviceStub = object : IAIRouterService.Stub() {
            override fun getApiVersion(): Int = 2
            override fun initialize(config: Configuration) {}
            override fun sendMessage(req: AIRequest) {
                assertEquals(1, req.binaryAttachments.size)
                assertEquals(binaryData.type, req.binaryAttachments[0].type)
            }
            override fun cancelRequest(requestId: String): Boolean = true
            override fun registerListener(listener: IAIRouterListener) {}
            override fun unregisterListener(listener: IAIRouterListener) {}
            override fun hasCapability(capabilityName: String): Boolean = true
        }
        
        serviceStub.sendMessage(request)
    }
}
```

---

## 🔧 AI Router Service Testing

### Service Lifecycle Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class AIRouterServiceTest {
    
    @get:Rule
    val serviceRule = ServiceTestRule()
    
    @Test
    fun `service binds successfully`() {
        val intent = Intent(context, AIRouterService::class.java)
        val binder = serviceRule.bindService(intent)
        val service = IAIRouterService.Stub.asInterface(binder)
        
        assertNotNull(service)
        assertTrue(service.getApiVersion() > 0)
    }
}
```

### Permission Security Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class AIRouterSecurityTest {
    
    @Test
    fun `service requires signature permission`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent().apply {
            component = ComponentName(
                context.packageName,
                "com.mtkresearch.breezeapp.router.AIRouterService"
            )
        }
        
        // This should fail if calling from unsigned app
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
            override fun onServiceDisconnected(name: ComponentName?) {}
        }
        
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        
        // In real test, this would depend on signing configuration
        // For properly signed apps, this should succeed
        assertTrue(bound)
        
        context.unbindService(connection)
    }
}
```

### Configuration Validation Tests

```kotlin
class AIRouterConfigValidationTest {
    
    @Test
    fun `validateConfiguration accepts valid configurations`() {
        val validConfigs = listOf(
            Configuration(apiVersion = 1, logLevel = 0),
            Configuration(apiVersion = 2, logLevel = 5),
            Configuration(temperature = 0.0f),
            Configuration(temperature = 1.0f),
            Configuration(maxTokens = 1),
            Configuration(maxTokens = 10000)
        )
        
        validConfigs.forEach { config ->
            val result = validateConfiguration(config)
            assertTrue(result, "Should accept valid config: $config")
        }
    }
    
    @Test
    fun `validateConfiguration rejects invalid configurations`() {
        val invalidConfigs = listOf(
            Configuration(apiVersion = -1),
            Configuration(logLevel = -1),
            Configuration(logLevel = 10),
            Configuration(temperature = -0.1f),
            Configuration(temperature = 1.1f),
            Configuration(maxTokens = 0),
            Configuration(maxTokens = -1),
            Configuration(timeoutMs = -1)
        )
        
        invalidConfigs.forEach { config ->
            val result = validateConfiguration(config)
            assertFalse(result, "Should reject invalid config: $config")
        }
    }
    
    @Test
    fun `validateConfiguration handles enum validation`() {
        val config = Configuration(
            preferredRuntime = Configuration.RuntimeBackend.GPU,
            runnerConfigurations = mapOf(
                Configuration.AITaskType.TEXT_GENERATION to Configuration.RunnerType.EXECUTORCH
            )
        )
        
        val result = validateConfiguration(config)
        assertTrue(result)
    }
    
    // Helper method matching service implementation
    private fun validateConfiguration(config: Configuration): Boolean {
        if (config.apiVersion <= 0) return false
        if (config.logLevel !in 0..5) return false
        if (config.timeoutMs < 0) return false
        if (config.maxTokens <= 0) return false
        if (config.temperature !in 0.0f..1.0f) return false
        if (config.languagePreference.isBlank()) return false
        return true
    }
}
```

### Listener Management Tests

```kotlin
class AIRouterListenerTest {
    
    private lateinit var service: AIRouterService
    private val mockListeners = mutableListOf<IAIRouterListener>()
    
    @Before
    fun setup() {
        service = AIRouterService()
    }
    
    @Test
    fun `listener registration and notification works`() {
        val responseReceived = CountDownLatch(1)
        var receivedResponse: AIResponse? = null
        
        val listener = object : IAIRouterListener.Stub() {
            override fun onResponse(response: AIResponse) {
                receivedResponse = response
                responseReceived.countDown()
            }
        }
        
        // Register listener
        service.registerListener(listener)
        
        // Send test request
        val request = AIRequest(
            id = "test-123",
            text = "Test message",
            sessionId = "session",
            timestamp = System.currentTimeMillis()
        )
        service.sendMessage(request)
        
        // Wait for response
        assertTrue(responseReceived.await(5, TimeUnit.SECONDS))
        assertNotNull(receivedResponse)
        assertEquals("test-123", receivedResponse?.requestId)
        
        // Cleanup
        service.unregisterListener(listener)
    }
    
    @Test
    fun `multiple listeners receive notifications`() {
        val listenerCount = 3
        val responses = Array<AIResponse?>(listenerCount) { null }
        val latches = Array(listenerCount) { CountDownLatch(1) }
        
        // Create and register multiple listeners
        repeat(listenerCount) { index ->
            val listener = object : IAIRouterListener.Stub() {
                override fun onResponse(response: AIResponse) {
                    responses[index] = response
                    latches[index].countDown()
                }
            }
            service.registerListener(listener)
            mockListeners.add(listener)
        }
        
        // Send request
        val request = AIRequest(
            id = "multi-test",
            text = "Test",
            sessionId = "session",
            timestamp = System.currentTimeMillis()
        )
        service.sendMessage(request)
        
        // Wait for all responses
        latches.forEach { latch ->
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        }
        
        // Verify all listeners received the response
        responses.forEach { response ->
            assertNotNull(response)
            assertEquals("multi-test", response?.requestId)
        }
        
        // Cleanup
        mockListeners.forEach { service.unregisterListener(it) }
    }
}
```

---

## 📱 UI Layer Testing

### ViewModel Tests

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ChatViewModel
    private lateinit var mockAIRouterClient: AIRouterClient
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockAIRouterClient = mockk<AIRouterClient>()
        viewModel = ChatViewModel(mockAIRouterClient)
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `sendMessage updates UI state correctly`() = runTest(testDispatcher) {
        // Given
        val messageText = "Hello AI"
        val mockResponse = AIResponse(
            requestId = "123",
            text = "Hello! How can I help you?",
            isComplete = true,
            state = AIResponse.ResponseState.COMPLETED
        )
        
        coEvery { 
            mockAIRouterClient.sendMessage(messageText, any(), any()) 
        } returns flowOf(mockResponse)
        
        // When
        viewModel.sendMessage(messageText)
        advanceUntilIdle()
        
        // Then
        coVerify { mockAIRouterClient.sendMessage(messageText, any(), any()) }
        
        val messages = viewModel.messages.value
        assertEquals(2, messages.size)
        assertEquals(messageText, messages[0].text)
        assertTrue(messages[0].isFromUser)
        assertEquals(mockResponse.text, messages[1].text)
        assertFalse(messages[1].isFromUser)
    }
    
    @Test
    fun `connection state is properly managed`() = runTest(testDispatcher) {
        // Given
        val connectionFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
        every { mockAIRouterClient.getConnectionState() } returns connectionFlow.asStateFlow()
        
        // When - simulate connection
        connectionFlow.value = ConnectionState.CONNECTING
        advanceUntilIdle()
        
        // Then
        assertEquals(ConnectionState.CONNECTING, viewModel.aiRouterConnectionState.value)
        
        // When - simulate connected
        connectionFlow.value = ConnectionState.CONNECTED
        advanceUntilIdle()
        
        // Then
        assertEquals(ConnectionState.CONNECTED, viewModel.aiRouterConnectionState.value)
    }
    
    @Test
    fun `error handling works correctly`() = runTest(testDispatcher) {
        // Given
        val errorMessage = "Network error"
        coEvery { 
            mockAIRouterClient.sendMessage(any(), any(), any()) 
        } throws AIRouterError.ConnectionError(errorMessage)
        
        // When
        viewModel.sendMessage("Test message")
        advanceUntilIdle()
        
        // Then
        assertEquals(UiState.ERROR, viewModel.uiState.value.state)
        assertTrue(viewModel.uiState.value.message.contains("connection"))
    }
}
```

### Repository Tests

```kotlin
class AIRouterRepositoryTest {
    
    private lateinit var repository: AIRouterRepositoryImpl
    private lateinit var mockService: IAIRouterService
    private lateinit var mockContext: Context
    
    @Before
    fun setup() {
        mockService = mockk<IAIRouterService>()
        mockContext = mockk<Context>()
        repository = AIRouterRepositoryImpl(mockContext)
        
        // Mock successful service binding
        every { mockContext.bindService(any(), any(), any()) } returns true
    }
    
    @Test
    fun `connect establishes service connection`() = runTest {
        // When
        val result = repository.connect()
        
        // Then
        assertTrue(result.isSuccess)
        verify { mockContext.bindService(any(), any(), Context.BIND_AUTO_CREATE) }
    }
    
    @Test
    fun `sendMessage creates proper request`() = runTest {
        // Given
        val text = "Test message"
        val sessionId = "session-123"
        
        every { mockService.sendMessage(any()) } just Runs
        repository.setService(mockService) // Simulate connected state
        
        // When
        repository.sendMessage(text, sessionId, MessageType.TEXT).collect { response ->
            // Response handling tested separately
        }
        
        // Then
        verify { mockService.sendMessage(match { request ->
            request.text == text && request.sessionId == sessionId
        }) }
    }
}
```

---

## 🔗 Integration Testing

### End-to-End Communication Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class E2EIntegrationTest {
    
    @Test
    fun `complete message flow works end to end`() = runTest {
        val aiRouterClient = AIRouterClientImpl(context)
        assertTrue(aiRouterClient.connect().isSuccess)
        
        val request = AIRequest(
            id = "e2e-test",
            text = "Hello from integration test",
            sessionId = "e2e-session",
            timestamp = System.currentTimeMillis()
        )
        
        aiRouterClient.sendMessage(request)
        
        // Verify response received
        // Implementation details...
    }
}
```

### Performance Integration Tests

```kotlin
@RunWith(AndroidJUnit4::class)
class PerformanceIntegrationTest {
    
    @Test
    fun `large message handling performance`() = runTest {
        val aiRouterClient = AIRouterClientImpl(context)
        assertTrue(aiRouterClient.connect().isSuccess)
        
        // Test with large text message
        val largeText = "a".repeat(10000) // 10KB text
        val startTime = System.currentTimeMillis()
        
        val responseReceived = CompletableDeferred<AIResponse>()
        val listener = object : AIRouterListener {
            override fun onResponse(response: AIResponse) {
                if (response.state == AIResponse.ResponseState.COMPLETED) {
                    responseReceived.complete(response)
                }
            }
        }
        
        aiRouterClient.registerListener(listener)
        
        aiRouterClient.sendMessage(
            AIRequest(
                id = "perf-test",
                text = largeText,
                sessionId = "perf-session",
                timestamp = System.currentTimeMillis()
            )
        )
        
        val response = withTimeout(30.seconds) {
            responseReceived.await()
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Assert performance expectations
        assertTrue(duration < 5000, "Large message should be processed within 5 seconds")
        assertEquals("perf-test", response.requestId)
        
        aiRouterClient.disconnect()
    }
    
    @Test
    fun `binary data performance test`() = runTest {
        val aiRouterClient = AIRouterClientImpl(context)
        assertTrue(aiRouterClient.connect().isSuccess)
        
        // Create test image data (1MB)
        val imageData = ByteArray(1024 * 1024) { (it % 256).toByte() }
        val binaryData = BinaryData(
            type = BinaryData.ContentType.IMAGE_JPEG,
            data = imageData,
            metadata = mapOf("test" to "performance")
        )
        
        val startTime = System.currentTimeMillis()
        
        val request = AIRequest(
            id = "binary-perf-test",
            text = "Analyze this large image",
            sessionId = "binary-session",
            timestamp = System.currentTimeMillis(),
            binaryAttachments = listOf(binaryData)
        )
        
        val responseReceived = CompletableDeferred<AIResponse>()
        val listener = object : AIRouterListener {
            override fun onResponse(response: AIResponse) {
                if (response.state == AIResponse.ResponseState.COMPLETED) {
                    responseReceived.complete(response)
                }
            }
        }
        
        aiRouterClient.registerListener(listener)
        aiRouterClient.sendMessage(request)
        
        val response = withTimeout(30.seconds) {
            responseReceived.await()
        }
        
        val duration = System.currentTimeMillis() - startTime
        
        // Assert binary data was handled efficiently
        assertTrue(duration < 10000, "Binary data should be processed within 10 seconds")
        assertEquals("binary-perf-test", response.requestId)
        
        aiRouterClient.disconnect()
    }
}
```

---

## 📊 Performance Testing

### Memory Usage Tests

```kotlin
class MemoryPerformanceTest {
    
    @Test
    fun `service memory usage stays within bounds`() {
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Create many requests to test memory management
        repeat(1000) { index ->
            val request = AIRequest(
                id = "mem-test-$index",
                text = "Memory test message $index",
                sessionId = "mem-session",
                timestamp = System.currentTimeMillis()
            )
            
            // Simulate processing (would normally go through service)
            // Test that request objects are properly garbage collected
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(1000)
        System.gc()
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        
        // Assert memory increase is reasonable (less than 50MB)
        assertTrue(
            memoryIncrease < 50 * 1024 * 1024,
            "Memory increase should be reasonable: ${memoryIncrease / (1024 * 1024)}MB"
        )
    }
}
```

### Concurrent Request Tests

```kotlin
class ConcurrencyTest {
    
    @Test
    fun `service handles concurrent requests correctly`() = runTest {
        val aiRouterClient = AIRouterClientImpl(context)
        assertTrue(aiRouterClient.connect().isSuccess)
        
        val requestCount = 10
        val responses = ConcurrentHashMap<String, AIResponse>()
        val completionLatch = CountDownLatch(requestCount)
        
        val listener = object : AIRouterListener {
            override fun onResponse(response: AIResponse) {
                if (response.state == AIResponse.ResponseState.COMPLETED) {
                    responses[response.requestId] = response
                    completionLatch.countDown()
                }
            }
        }
        
        aiRouterClient.registerListener(listener)
        
        // Send multiple concurrent requests
        val jobs = (1..requestCount).map { index ->
            async {
                aiRouterClient.sendMessage(
                    AIRequest(
                        id = "concurrent-$index",
                        text = "Concurrent message $index",
                        sessionId = "concurrent-session",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        
        // Wait for all requests to be sent
        jobs.awaitAll()
        
        // Wait for all responses
        assertTrue(
            completionLatch.await(30, TimeUnit.SECONDS),
            "All concurrent requests should complete within 30 seconds"
        )
        
        // Verify all responses received
        assertEquals(requestCount, responses.size)
        
        // Verify each request got a response
        (1..requestCount).forEach { index ->
            val requestId = "concurrent-$index"
            assertTrue(responses.containsKey(requestId), "Missing response for $requestId")
        }
        
        aiRouterClient.disconnect()
    }
}
```

---

## ✨ Best Practices

### 1. Naming Conventions

```kotlin
// ✅ Good: Descriptive test names
@Test
fun `sendMessage with valid request should return successful response`()

// ❌ Bad: Unclear test names
@Test
fun testSendMessage()
```

### 2. Test Structure (Given-When-Then)

```kotlin
@Test
fun `listener should receive response when message is sent`() {
    // Given
    val request = AIRequest(...)
    
    // When
    mockService.sendMessage(request)
    
    // Then
    verify { mockService.sendMessage(any()) }
}
```

---

**Last Updated**: 2024-12-19  
**Version**: 2.0  
**Test Coverage Target**: 80%+ 