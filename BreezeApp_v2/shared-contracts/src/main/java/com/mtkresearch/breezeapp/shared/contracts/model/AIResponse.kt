package com.mtkresearch.breezeapp.shared.contracts.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * Represents a response from the AI Router Service.
 *
 * @param requestId The ID of the original request this response corresponds to.
 * @param text The text content of the response.
 * @param isComplete True if this is the final response for the request, false otherwise.
 * @param state The current state of the response processing.
 * @param apiVersion The version of the API used by the service (default: 1).
 * @param binaryAttachments Optional binary data attachments (images, audio, etc.).
 * @param metadata Additional response metadata.
 * @param error An optional error message if the processing failed.
 */
@Parcelize
@TypeParceler<AIResponse.ResponseState, ResponseStateParceler>()
@TypeParceler<BinaryData, BinaryDataParceler>()
data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean,
    val state: ResponseState,
    val apiVersion: Int = 1,
    val binaryAttachments: List<BinaryData> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val error: String? = null
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
    
    /**
     * Common metadata keys that can be included in the metadata map.
     */
    object MetadataKeys {
        const val MODEL_NAME = "model_name"
        const val PROCESSING_TIME_MS = "processing_time_ms"
        const val TOKEN_COUNT = "token_count"
        const val RUNTIME_BACKEND = "runtime_backend" // CPU, GPU, NPU
    }
    
    companion object {
        @JvmStatic
        fun createFromParcel(parcel: Parcel): AIResponse {
            val requestId = parcel.readString() ?: ""
            val text = parcel.readString() ?: ""
            val isComplete = parcel.readInt() != 0
            
            // Read the ordinal value as a name string for more reliability
            val stateName = parcel.readString() ?: ResponseState.ERROR.name
            val state = try {
                ResponseState.valueOf(stateName)
            } catch (e: IllegalArgumentException) {
                ResponseState.ERROR
            }
            
            val apiVersion = parcel.readInt()
            
            // Read binary attachments
            val attachmentCount = parcel.readInt()
            val attachments = mutableListOf<BinaryData>()
            if (attachmentCount > 0) {
                // Use the BinaryDataParceler to read each attachment
                for (i in 0 until attachmentCount) {
                    attachments.add(BinaryDataParceler.create(parcel))
                }
            }
            
            // Read metadata map
            val metadataCount = parcel.readInt()
            val metadata = mutableMapOf<String, String>()
            for (i in 0 until metadataCount) {
                val key = parcel.readString() ?: ""
                val value = parcel.readString() ?: ""
                metadata[key] = value
            }
            
            val error = parcel.readString()
            
            return AIResponse(
                requestId = requestId,
                text = text,
                isComplete = isComplete,
                state = state,
                apiVersion = apiVersion,
                binaryAttachments = attachments,
                metadata = metadata,
                error = error
            )
        }
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