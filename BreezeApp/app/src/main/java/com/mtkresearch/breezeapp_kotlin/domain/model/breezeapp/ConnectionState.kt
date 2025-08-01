package com.mtkresearch.breezeapp_kotlin.domain.model.breezeapp

/**
 * BreezeApp Engine connection state
 * 
 * This sealed class represents the different states of connection
 * to the BreezeApp Engine service.
 */
sealed class ConnectionState {
    object Initializing : ConnectionState()
    object Connected : ConnectionState()
    object Disconnecting : ConnectionState()
    object Disconnected : ConnectionState()
    data class Failed(val message: String) : ConnectionState()
    
    /**
     * Check if the connection is active
     */
    fun isActive(): Boolean = this is Connected
    
    /**
     * Check if the connection is in a terminal state
     */
    fun isTerminal(): Boolean = this is Disconnected || this is Failed
}