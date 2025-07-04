package com.mtkresearch.breezeapp.shared.contracts.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * Represents a response from the AI Router Service.
 * This modern version uses a strongly-typed [metadata] object to provide
 * detailed, type-safe information about the response.
 *
 * @param requestId The ID of the original request this response corresponds to.
 * @param text The text content of the response.
 * @param isComplete True if this is the final response for the request, false otherwise.
 * @param state The current state of the response processing.
 * @param metadata Optional: Type-safe metadata about the response.
 * @param error An optional error message if the processing failed.
 * @param apiVersion The version of the API used by the service (default: 1).
 */
@Parcelize
@TypeParceler<AIResponse.ResponseState, ResponseStateParceler>()
data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean,
    val state: ResponseState,
    val metadata: ResponseMetadata? = null,
    val error: String? = null,
    val apiVersion: Int = 1
) : Parcelable {

    /**
     * Defines the state of the response from the service.
     */
    enum class ResponseState {
        /** The service is processing the request. */
        PROCESSING,

        /** The service is streaming back a response. */
        STREAMING,

        /** The service has completed the request successfully. */
        COMPLETED,

        /** An error occurred while processing the request. */
        ERROR
    }
}

/**
 * Custom parceler for ResponseState enum that writes the enum as a string name
 * instead of an ordinal value for better stability across versions.
 */
object ResponseStateParceler : Parceler<AIResponse.ResponseState> {
    override fun create(parcel: Parcel): AIResponse.ResponseState {
        val name = parcel.readString() ?: return AIResponse.ResponseState.ERROR
        return try {
            AIResponse.ResponseState.valueOf(name)
        } catch (e: IllegalArgumentException) {
            AIResponse.ResponseState.ERROR
        }
    }

    override fun AIResponse.ResponseState.write(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
    }
} 