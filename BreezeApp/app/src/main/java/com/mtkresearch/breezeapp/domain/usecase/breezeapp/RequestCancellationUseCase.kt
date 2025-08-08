package com.mtkresearch.breezeapp.domain.usecase.breezeapp

import android.util.Log
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import com.mtkresearch.breezeapp.edgeai.EdgeAIException
import com.mtkresearch.breezeapp.edgeai.ServiceConnectionException
import javax.inject.Inject

/**
 * UseCase for handling request cancellation
 * 
 * Responsibilities:
 * - Cancel active requests
 * - Handle cancellation-specific error scenarios
 * - Provide cancellation status
 * - Manage cancellation state
 */
class RequestCancellationUseCase @Inject constructor() {
    
    companion object {
        private const val TAG = "RequestCancellationUseCase"
    }
    
    /**
     * Cancel the last active request
     * 
     * @return true if cancellation was successful, false otherwise
     */
    suspend fun cancelLastRequest(): Boolean {
        
        Log.d(TAG, "Cancelling last active request")
        
        return try {
            val cancelled = EdgeAI.cancelLastRequest()
            
            if (cancelled) {
                Log.d(TAG, "Request cancelled successfully")
            } else {
                Log.d(TAG, "No active request found to cancel")
            }
            
            cancelled
        } catch (e: ServiceConnectionException) {
            Log.e(TAG, "Connection error during cancellation: ${e.message}")
            throw CancellationError.ConnectionError(e.message ?: "Connection error")
        } catch (e: EdgeAIException) {
            Log.e(TAG, "EdgeAI error during cancellation: ${e.message}")
            throw CancellationError.UnknownError(e.message ?: "Unknown error")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during cancellation: ${e.message}")
            throw CancellationError.UnknownError(e.message ?: "Unexpected error")
        }
    }
    
    /**
     * Cancel a specific request by ID
     * 
     * @param requestId The ID of the request to cancel
     * @return true if cancellation was successful, false otherwise
     */
    suspend fun cancelRequest(requestId: String): Boolean {
        
        Log.d(TAG, "Cancelling request: $requestId")
        
        return try {
            // Note: EdgeAI SDK currently only supports cancelling the last request
            // This method is prepared for future implementation
            Log.d(TAG, "Specific request cancellation not yet supported, cancelling last request")
            cancelLastRequest()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling request $requestId: ${e.message}")
            throw CancellationError.UnknownError(e.message ?: "Unknown error")
        }
    }
}

/**
 * Cancellation-specific error types
 */
sealed class CancellationError(message: String) : Exception(message) {
    data class ConnectionError(override val message: String) : CancellationError(message)
    data class UnknownError(override val message: String) : CancellationError(message)
}