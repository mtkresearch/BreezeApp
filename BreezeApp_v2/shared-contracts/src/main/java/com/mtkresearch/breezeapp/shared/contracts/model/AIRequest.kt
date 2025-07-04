package com.mtkresearch.breezeapp.shared.contracts.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.parcelize.Parceler
import android.os.Parcel
import java.util.UUID

/**
 * Represents a request sent from the client to the AI Router Service.
 * This modern version uses a strongly-typed [payload] to define the request,
 * improving type safety and developer experience.
 *
 * @param id Unique identifier for this request. Defaults to a new random UUID.
 * @param sessionId Identifier for the conversation session. Defaults to a new random UUID.
 * @param timestamp When this request was created (Unix timestamp). Defaults to the current time.
 * @param payload The specific, type-safe payload for the request.
 * @param apiVersion The version of the API being used by the client (default: 1).
 */
@Parcelize
data class AIRequest(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val payload: RequestPayload,
    val apiVersion: Int = 1
) : Parcelable 