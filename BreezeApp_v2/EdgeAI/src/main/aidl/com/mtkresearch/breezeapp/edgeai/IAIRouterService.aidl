// IAIRouterService.aidl
package com.mtkresearch.breezeapp.edgeai;

import com.mtkresearch.breezeapp.edgeai.model.Configuration;
import com.mtkresearch.breezeapp.edgeai.model.AIRequest;
import com.mtkresearch.breezeapp.edgeai.model.BinaryData;
import com.mtkresearch.breezeapp.edgeai.IAIRouterListener;

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

    /**
     * Sends an AI request to the service for processing.
     * The service will process the request asynchronously and send the response
     * via a registered IAIRouterListener.
     *
     * @param request The AIRequest object containing all necessary data.
     */
    void sendMessage(in AIRequest request);
    
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