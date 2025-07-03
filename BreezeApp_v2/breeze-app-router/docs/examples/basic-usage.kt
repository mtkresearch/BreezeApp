// Basic usage: send a text request and receive a response
val request = AIRequest(
    id = "req-001",
    sessionId = "sess-001",
    text = "Hello, AI!",
    timestamp = System.currentTimeMillis(),
    options = mapOf("request_type" to "text_generation")
)
aiRouterService.sendMessage(request)
