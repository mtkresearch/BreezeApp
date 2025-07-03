// Streaming example: receive partial results
val request = AIRequest(
    id = "req-002",
    sessionId = "sess-002",
    text = "Stream this!",
    timestamp = System.currentTimeMillis(),
    options = mapOf("request_type" to "text_generation", "streaming" to "true")
)
aiRouterService.sendMessage(request)
