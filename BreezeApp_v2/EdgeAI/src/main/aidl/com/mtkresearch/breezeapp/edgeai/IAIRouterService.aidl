// IAIRouterService.aidl
package com.mtkresearch.breezeapp.edgeai;

import com.mtkresearch.breezeapp.edgeai.IAIRouterListener;
import com.mtkresearch.breezeapp.edgeai.ChatRequest;
import com.mtkresearch.breezeapp.edgeai.TTSRequest;
import com.mtkresearch.breezeapp.edgeai.ASRRequest;

/**
 * AIDL interface for the AI Router Service.
 * This interface defines the contract for communication between client applications
 * and the AI Router Service. It supports sending AI requests and managing listeners
 * for asynchronous responses.
 */
interface IAIRouterService {
    /**
     * Retrieves the current API version of the service.
     * This allows clients to check for compatibility.
     * @return The integer API version.
     */
    int getApiVersion();
    
    // === NEW SIMPLIFIED API ===
    
    /**
     * [PREFERRED] Direct chat request (標準化 API).
     * Eliminates intermediate model conversion for better performance.
     * @param requestId Client-generated request ID for tracking
     * @param request The chat request to process
     */
    void sendChatRequest(String requestId, in ChatRequest request);
    
    /**
     * [PREFERRED] Direct text-to-speech request (標準化 API).
     * Eliminates intermediate model conversion for better performance.
     * @param requestId Client-generated request ID for tracking
     * @param request The TTS request to process
     */
    void sendTTSRequest(String requestId, in TTSRequest request);
    
    /**
     * [PREFERRED] Direct speech recognition request (標準化 API).
     * Eliminates intermediate model conversion for better performance.
     * @param requestId Client-generated request ID for tracking
     * @param request The ASR request to process
     */
    void sendASRRequest(String requestId, in ASRRequest request);
    
    // === COMMON METHODS ===
    
    /**
     * Cancels an in-progress request.
     * Returns true if the request was successfully canceled, false otherwise.
     */
    boolean cancelRequest(String requestId);

    /**
     * Registers a listener to receive callbacks from the service.
     */
    void registerListener(IAIRouterListener listener);

    /**
     * Unregisters a previously registered listener.
     */
    void unregisterListener(IAIRouterListener listener);
    
    /**
     * Checks if the service supports a specific capability.
     * Returns true if the capability is supported, false otherwise.
     * 
     * Capability strings include:
     * - "binary_data" - Support for binary data transfer
     * - "streaming" - Support for streaming responses
     * - "image_processing" - Support for image analysis
     * - "audio_processing" - Support for audio processing
     */
    boolean hasCapability(String capabilityName);
} 