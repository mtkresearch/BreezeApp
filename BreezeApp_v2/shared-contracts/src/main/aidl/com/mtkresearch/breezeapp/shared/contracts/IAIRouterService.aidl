// IAIRouterService.aidl
package com.mtkresearch.breezeapp.shared.contracts;

import com.mtkresearch.breezeapp.shared.contracts.model.Configuration;
import com.mtkresearch.breezeapp.shared.contracts.model.AIRequest;
import com.mtkresearch.breezeapp.shared.contracts.model.BinaryData;
import com.mtkresearch.breezeapp.shared.contracts.IAIRouterListener;

/**
 * Main interface for communication with the AIRouterService.
 */
interface IAIRouterService {
    /**
     * Returns the API version supported by this service implementation.
     * Clients should check this before proceeding with other calls to ensure compatibility.
     */
    int getApiVersion();
    
    /**
     * Initializes the service with a given configuration.
     * This should be the first call made by a client after checking the API version.
     */
    void initialize(in Configuration config);

    /**
     * Sends a request to the service for processing.
     * Responses will be sent back via the IAIRouterListener.
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