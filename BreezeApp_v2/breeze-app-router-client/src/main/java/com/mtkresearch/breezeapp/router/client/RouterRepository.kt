package com.mtkresearch.breezeapp.router.client

import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterService
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.RequestPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Repository for interacting with the AI Router Service.
 *
 * This class abstracts the data source (the AIDL service) and provides a clean API
 * for the ViewModel to use. It handles all direct communication with the IAIRouterService,
 * including request creation and listener management. It transforms callback-based AIDL
 * responses into a reactive Flow.
 *
 * @param airouterClient The client responsible for managing the service connection lifecycle.
 * @param externalScope A CoroutineScope to launch listening and response handling jobs.
 */
class RouterRepository(
    private val airouterClient: AIRouterClient,
    private val externalScope: CoroutineScope
) {
    private var routerService: IAIRouterService? = null

    // A hot flow to broadcast all incoming responses from the service.
    private val _responses = MutableSharedFlow<AIResponse>()
    val responses: SharedFlow<AIResponse> = _responses.asSharedFlow()

    private val listener = object : IAIRouterListener.Stub() {
        override fun onResponse(response: AIResponse) {
            // Emit the response into the shared flow from a coroutine.
            externalScope.launch {
                _responses.emit(response)
            }
        }
    }

    init {
        externalScope.launch {
            airouterClient.routerService.collect { service ->
                // When the service reference changes, update the listener registration.
                routerService?.unregisterListener(listener)
                routerService = service
                routerService?.registerListener(listener)
            }
        }
    }

    /**
     * Sends a request to the AI Router Service with the given payload.
     *
     * @param payload The type-safe data payload for the request.
     * @return The unique ID of the request that was sent. This can be used to
     *         filter the `responses` flow for a specific response.
     */
    fun sendRequest(payload: RequestPayload): String {
        val requestId = UUID.randomUUID().toString()
        val service = routerService
        if (service == null) {
            // Optionally, we could emit an error state here.
            // For now, we just return an empty ID and the call will do nothing.
            return ""
        }

        val request = AIRequest(
            id = requestId,
            sessionId = "session-${payload::class.java.simpleName}",
            timestamp = System.currentTimeMillis(),
            payload = payload
        )

        service.sendMessage(request)
        return requestId
    }
} 