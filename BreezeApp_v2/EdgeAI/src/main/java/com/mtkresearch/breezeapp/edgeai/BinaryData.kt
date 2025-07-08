package com.mtkresearch.breezeapp.edgeai.model

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize

/**
 * Represents binary data that can be transferred between processes.
 * This class supports various types of binary content such as images and audio.
 *
 * @param type The type of binary data (e.g., "image/jpeg", "audio/wav")
 * @param data The actual binary content as a byte array
 * @param metadata Additional information about the binary data (optional)
 */
@Parcelize
data class BinaryData(
    val type: String,
    val data: ByteArray,
    val metadata: Map<String, String> = emptyMap()
) : Parcelable {
    /**
     * Predefined content types for common binary data formats.
     */
    object ContentType {
        const val IMAGE_JPEG = "image/jpeg"
        const val IMAGE_PNG = "image/png"
        const val AUDIO_WAV = "audio/wav"
        const val AUDIO_MP3 = "audio/mp3"
        const val GENERIC_BINARY = "application/octet-stream"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BinaryData) return false

        if (type != other.type) return false
        if (!data.contentEquals(other.data)) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Custom Parceler implementation for BinaryData.
 */
object BinaryDataParceler : Parceler<BinaryData> {
    override fun create(parcel: Parcel): BinaryData {
        val type = parcel.readString() ?: BinaryData.ContentType.GENERIC_BINARY
        
        // Read byte array
        val dataSize = parcel.readInt()
        val data = ByteArray(dataSize)
        parcel.readByteArray(data)
        
        // Read metadata map
        val metadataCount = parcel.readInt()
        val metadata = mutableMapOf<String, String>()
        for (i in 0 until metadataCount) {
            val key = parcel.readString() ?: ""
            val value = parcel.readString() ?: ""
            metadata[key] = value
        }
        
        return BinaryData(type, data, metadata)
    }

    override fun BinaryData.write(parcel: Parcel, flags: Int) {
        parcel.writeString(type)
        
        // Write byte array
        parcel.writeInt(data.size)
        parcel.writeByteArray(data)
        
        // Write metadata map
        parcel.writeInt(metadata.size)
        metadata.forEach { (key, value) ->
            parcel.writeString(key)
            parcel.writeString(value)
        }
    }
} 