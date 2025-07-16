package com.mtkresearch.breezeapp.router.error

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.model.InferenceRequest
import com.mtkresearch.breezeapp.router.domain.model.InferenceResult
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import com.mtkresearch.breezeapp.router.domain.usecase.AIEngineManager
import com.mtkresearch.breezeapp.router.core.RouterStatusManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Helper class to centralize request processing logic with robust error handling.
 * Reduces code duplication and ensures consistent behavior across all request types.
 */
class RequestProcessingHelper(
    private val engineManager: AIEngineManager,
    private val statusManager: RouterStatusManager,
    private val activeRequestCount: AtomicInteger,
    private val requestTracker: ConcurrentHashMap<String, Long>,
    private val updateStatusAfterRequestCompletion: (Int) -> Unit,
    private val notifyError: (String, String) -> Unit
) {
    
    companion object {
        private const val TAG = "RequestProcessingHelper"
    }
    
    /**
     * Processes a non-streaming request with unified error handling and cleanup.
     */
    suspend fun processNonStreamingRequest(
        requestId: String,
        inferenceRequest: InferenceRequest,
        capability: CapabilityType,
        requestType: String
    ): InferenceResult? {
        val startTime = System.currentTimeMillis()
        requestTracker[requestId] = startTime
        val currentActiveRequests = activeRequestCount.incrementAndGet()
        statusManager.setProcessing(currentActiveRequests)
        
        Log.d(TAG, "Started processing $requestType request $requestId (active: $currentActiveRequests)")
        
        return try {
            val result = engineManager.process(inferenceRequest, capability)
            Log.d(TAG, "$requestType request $requestId processed successfully")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error processing $requestType request $requestId", e)
            statusManager.setError("$requestType processing failed: ${e.message}")
            notifyError(requestId, e.message ?: "Unknown $requestType processing error")
            null
        } finally {
            // Always clean up, regardless of success or failure
            requestTracker.remove(requestId)
            val remainingRequests = activeRequestCount.decrementAndGet()
            val processingTime = System.currentTimeMillis() - startTime
            
            Log.d(TAG, "Completed $requestType request $requestId in ${processingTime}ms (remaining: $remainingRequests)")
            updateStatusAfterRequestCompletion(remainingRequests)
        }
    }
    
    /**
     * Processes a streaming request with proper completion detection and cleanup.
     */
    suspend fun processStreamingRequest(
        requestId: String,
        inferenceRequest: InferenceRequest,
        capability: CapabilityType,
        requestType: String,
        onResult: (InferenceResult) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        requestTracker[requestId] = startTime
        val currentActiveRequests = activeRequestCount.incrementAndGet()
        statusManager.setProcessing(currentActiveRequests)
        
        Log.d(TAG, "Started processing streaming $requestType request $requestId (active: $currentActiveRequests)")
        
        var streamCompleted = false
        
        try {
            engineManager.processStream(inferenceRequest, capability)
                .catch { error ->
                    Log.e(TAG, "$requestType stream processing error", error)
                    notifyError(requestId, error.message ?: "$requestType stream processing failed")
                    streamCompleted = true
                    // Clean up on error
                    cleanupStreamingRequest(requestId, startTime, requestType)
                }
                .collect { result ->
                    onResult(result)
                    
                    // Check if this is the final response in the stream
                    if (!result.partial) {
                        streamCompleted = true
                        Log.d(TAG, "Stream completed for $requestType request $requestId")
                        cleanupStreamingRequest(requestId, startTime, requestType)
                    }
                }
            
            // Safety check: if stream ended without completion signal
            if (!streamCompleted) {
                Log.w(TAG, "Stream ended without completion signal for $requestType request $requestId")
                cleanupStreamingRequest(requestId, startTime, requestType)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing streaming $requestType request $requestId", e)
            statusManager.setError("$requestType streaming failed: ${e.message}")
            notifyError(requestId, e.message ?: "Unknown $requestType streaming error")
            
            // Ensure cleanup on exception
            if (!streamCompleted) {
                cleanupStreamingRequest(requestId, startTime, requestType)
            }
        }
    }
    
    /**
     * Centralized cleanup for streaming requests to avoid code duplication.
     */
    private fun cleanupStreamingRequest(requestId: String, startTime: Long, requestType: String) {
        requestTracker.remove(requestId)
        val remainingRequests = activeRequestCount.decrementAndGet()
        val processingTime = System.currentTimeMillis() - startTime
        
        Log.d(TAG, "Completed streaming $requestType request $requestId in ${processingTime}ms (remaining: $remainingRequests)")
        updateStatusAfterRequestCompletion(remainingRequests)
    }
}