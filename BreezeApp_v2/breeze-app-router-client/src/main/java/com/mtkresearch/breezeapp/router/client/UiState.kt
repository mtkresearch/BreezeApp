package com.mtkresearch.breezeapp.router.client

import android.net.Uri

/**
 * Represents the UI state for the Breeze App Router Client.
 * 
 * This immutable data class encapsulates all state needed by the UI layer, following
 * the unidirectional data flow pattern. The ViewModel exposes this state as a StateFlow
 * that the UI observes to render the current state.
 *
 * @property logMessages List of log messages to display in the UI, with the most recent messages at the end.
 * @property connectionStatus Human-readable status of the connection to the AI Router Service.
 * @property isConnected Whether the client is currently connected to the AI Router Service.
 * @property isRecording Whether audio recording is currently active.
 * @property selectedImageUri The URI of an image selected by the user for image-based requests, if any.
 * @property hasRecordedAudio Whether audio has been recorded and is ready to be sent.
 *
 * @see com.mtkresearch.breezeapp.router.client.MainViewModel
 */
data class UiState(
    val logMessages: List<String> = emptyList(),
    val connectionStatus: String = "Disconnected",
    val isConnected: Boolean = false,
    val isRecording: Boolean = false,
    val selectedImageUri: Uri? = null,
    val hasRecordedAudio: Boolean = false
) {
    /**
     * Adds a new log message to the state.
     *
     * @param message The log message to add.
     * @return A new UiState with the updated log messages.
     */
    fun addLogMessage(message: String): UiState {
        // Keep only the last 100 messages to prevent memory issues
        val updatedLogs = logMessages.toMutableList().apply {
            add(message)
            if (size > 100) removeAt(0)
        }
        return copy(logMessages = updatedLogs)
    }

    /**
     * Updates the connection status.
     *
     * @param status The new connection status.
     * @param isConnected Whether the connection is established.
     * @return A new UiState with the updated connection information.
     */
    fun updateConnectionStatus(status: String, isConnected: Boolean): UiState {
        return copy(
            connectionStatus = status,
            isConnected = isConnected
        )
    }

    /**
     * Updates the recording state.
     *
     * @param isRecording Whether recording is active.
     * @return A new UiState with the updated recording state.
     */
    fun setRecording(isRecording: Boolean): UiState {
        return copy(isRecording = isRecording)
    }

    /**
     * Sets the selected image URI.
     *
     * @param uri The URI of the selected image, or null to clear.
     * @return A new UiState with the updated image URI.
     */
    fun setSelectedImageUri(uri: Uri?): UiState {
        return copy(selectedImageUri = uri)
    }

    /**
     * Sets whether audio has been recorded.
     *
     * @param hasAudio Whether audio has been recorded.
     * @return A new UiState with the updated audio recording state.
     */
    fun setHasRecordedAudio(hasAudio: Boolean): UiState {
        return copy(hasRecordedAudio = hasAudio)
    }
} 