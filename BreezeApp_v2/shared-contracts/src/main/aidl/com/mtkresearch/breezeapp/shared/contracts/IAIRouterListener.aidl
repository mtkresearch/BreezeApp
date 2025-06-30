// IAIRouterListener.aidl
package com.mtkresearch.breezeapp.shared.contracts;

import com.mtkresearch.breezeapp.shared.contracts.model.AIResponse;

/**
 * Callback interface for the AIRouterService to send responses back to the client.
 */
oneway interface IAIRouterListener {
    /**
     * Called when the service has a response to a request.
     * This may be called multiple times for a single request if the response is streamed.
     */
    void onResponse(in AIResponse response);
} 