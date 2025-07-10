package com.mtkresearch.breezeapp.edgeai

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/**
 * 簡化版 AIResponse - 內部服務回應格式
 * 
 * 這是 Router Service 返回的標準化回應格式，
 * 在簡化架構中直接轉換為標準化 API 格式。
 */
@Parcelize
@TypeParceler<AIResponse.ResponseState, ResponseStateParceler>()
data class AIResponse(
    val requestId: String,
    val text: String,
    val isComplete: Boolean,
    val state: ResponseState,
    val error: String? = null,
    val audioData: ByteArray? = null  // For TTS responses
) : Parcelable {

    /**
     * 回應狀態
     */
    enum class ResponseState {
        PROCESSING,
        STREAMING,
        COMPLETED,
        ERROR
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AIResponse

        if (requestId != other.requestId) return false
        if (text != other.text) return false
        if (isComplete != other.isComplete) return false
        if (state != other.state) return false
        if (error != other.error) return false
        if (audioData != null) {
            if (other.audioData == null) return false
            if (!audioData.contentEquals(other.audioData)) return false
        } else if (other.audioData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = requestId.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + isComplete.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + (error?.hashCode() ?: 0)
        result = 31 * result + (audioData?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * ResponseState 的 Parceler
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