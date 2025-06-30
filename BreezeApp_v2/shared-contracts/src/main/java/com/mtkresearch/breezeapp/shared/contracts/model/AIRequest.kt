package com.mtkresearch.breezeapp.shared.contracts.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * Represents a request sent from the client to the AI Router Service.
 *
 * @param id Unique identifier for this request.
 * @param text The text content of the request.
 * @param sessionId Identifier for the conversation session this request belongs to.
 * @param timestamp When this request was created (Unix timestamp).
 * @param apiVersion The version of the API being used by the client (default: 1).
 * @param binaryAttachments Optional binary data attachments (images, audio, etc.).
 * @param options Additional request-specific options.
 */
@Parcelize
@TypeParceler<BinaryData, BinaryDataParceler>()
data class AIRequest(
    val id: String,
    val text: String,
    val sessionId: String,
    val timestamp: Long,
    val apiVersion: Int = 1,
    val binaryAttachments: List<BinaryData> = emptyList(),
    val options: Map<String, String> = emptyMap()
) : Parcelable {

    /**
     * Request types that can be used in the options map to specify the kind of request.
     */
    object RequestType {
        const val TEXT_CHAT = "text_chat"
        const val IMAGE_ANALYSIS = "image_analysis"
        const val AUDIO_TRANSCRIPTION = "audio_transcription"
        const val MULTIMODAL = "multimodal"
    }
    
    /**
     * Common option keys that can be used in the options map.
     */
    object OptionKeys {
        const val REQUEST_TYPE = "request_type"
        const val MODEL_NAME = "model_name"
        const val MAX_TOKENS = "max_tokens"
        const val TEMPERATURE = "temperature"
        const val LANGUAGE = "language"
    }

    companion object {
        @JvmStatic
        fun createFromParcel(parcel: Parcel): AIRequest {
            val id = parcel.readString() ?: ""
            val text = parcel.readString() ?: ""
            val sessionId = parcel.readString() ?: ""
            val timestamp = parcel.readLong()
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
            
            // Read options map
            val optionCount = parcel.readInt()
            val options = mutableMapOf<String, String>()
            for (i in 0 until optionCount) {
                val key = parcel.readString() ?: ""
                val value = parcel.readString() ?: ""
                options[key] = value
            }
            
            return AIRequest(
                id = id,
                text = text,
                sessionId = sessionId,
                timestamp = timestamp,
                apiVersion = apiVersion,
                binaryAttachments = attachments,
                options = options
            )
        }
    }
} 