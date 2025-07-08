 package com.mtkresearch.breezeapp.router.client

import android.util.Log
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest
import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse
import com.mtkresearch.breezeapp.shared.contracts.model.RequestPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * Repository for the AI Router Service.
 *
 * This class is the single source of truth for all data related to the AI service.
 * It abstracts the underlying data source (the [AIRouterClient]) and provides a clean,
 * reactive API for the ViewModel to consume. It manages the service listener lifecycle
 * and exposes service responses and connection state as Flows.
 */
class RouterRepository(
    private val client: AIRouterClient,
    private val externalScope: CoroutineScope
) {
    private val _responses = MutableSharedFlow<AIResponse>()
    val responses = _responses.asSharedFlow()

    // Expose the client's connection state directly to the ViewModel.
    val connectionState = client.connectionState

    init {
        // When the service connects, register our listener.
        // When it disconnects, the listener is automatically invalid.
        externalScope.launch {
            client.routerService.collect { service ->
                if (service != null) {
                    try {
                        service.registerListener(serviceListener)
                        Log.d(TAG, "Service listener registered.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to register listener", e)
                    }
                }
            }
        }
    }

    /**
     * Sends a request to the AI service. The response will be emitted
     * through the [responses] flow.
     * @return The ID of the sent request.
     */
    fun sendRequest(payload: RequestPayload): String {
        val request = AIRequest(payload = payload)
        externalScope.launch {
            try {
                client.routerService.value?.sendMessage(request)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
            }
        }
        return request.id
    }

    /**
     * The AIDL listener that receives callbacks from the service.
     */
    private val serviceListener = object : IAIRouterListener.Stub() {
        override fun onResponse(response: AIResponse) {
            // Emit the response to our shared flow for the ViewModel to collect.
            externalScope.launch {
                _responses.emit(response)
            }
        }
    }
    
    fun connect() = client.connect()
    fun disconnect() = client.disconnect()

    companion object {
        private const val TAG = "RouterRepository"
    }
}
