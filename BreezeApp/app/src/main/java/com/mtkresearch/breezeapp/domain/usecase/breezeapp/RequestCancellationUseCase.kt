package com.mtkresearch.breezeapp.domain.usecase.breezeapp

import android.util.Log
import com.mtkresearch.breezeapp.edgeai.EdgeAI
import javax.inject.Inject

/**
 * UseCase for handling request cancellation
 * 
 * Responsibilities:
 * - Cancel active requests
 * - Handle cancellation-specific scenarios
 * - Provide unified cancellation handling
 * 
 * This UseCase follows Clean Architecture principles by:
 * - Being independent of external frameworks
 * - Having a single responsibility
 * - Being easily testable
 */
class RequestCancellationUseCase @Inject constructor() {
    
    companion object {
        private const val TAG = "RequestCancellationUseCase"
    }
    
    /**
     * Cancel an active request by its ID
     * 
     * @param requestId The ID of the request to cancel
     * @return true if the request was successfully cancelled, false otherwise
     */
    fun cancelRequest(requestId: String): Boolean {
        return try {
            Log.d(TAG, "Cancelling request: $requestId")
            val cancelled = EdgeAI.cancelRequest(requestId)
            
            if (cancelled) {
                Log.d(TAG, "Successfully cancelled request: $requestId")
            } else {
                Log.w(TAG, "Failed to cancel request: $requestId")
            }
            
            cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling request: ${e.message}")
            false
        }
    }
    
    /**
     * Cancel the last active request
     * 
     * @return true if the request was successfully cancelled, false otherwise
     */
    fun cancelLastRequest(): Boolean {
        return try {
            Log.d(TAG, "Cancelling last request")
            val cancelled = EdgeAI.cancelLastRequest()
            
            if (cancelled) {
                Log.d(TAG, "Successfully cancelled last request")
            } else {
                Log.w(TAG, "Failed to cancel last request")
            }
            
            cancelled
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling last request: ${e.message}")
            false
        }
    }
}