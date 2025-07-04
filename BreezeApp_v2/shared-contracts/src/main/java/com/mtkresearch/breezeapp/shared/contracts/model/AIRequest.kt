package com.mtkresearch.breezeapp.shared.contracts.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlinx.parcelize.Parceler
import android.os.Parcel

/**
 * Represents a request sent from the client to the AI Router Service.
 * This modern version uses a strongly-typed [payload] to define the request,
 * improving type safety and developer experience.
 *
 * @param id Unique identifier for this request.
 * @param sessionId Identifier for the conversation session this request belongs to.
 * @param timestamp When this request was created (Unix timestamp).
 * @param payload The specific, type-safe payload for the request.
 * @param apiVersion The version of the API being used by the client (default: 1).
 */
@Parcelize
data class AIRequest(
    val id: String,
    val sessionId: String,
    val timestamp: Long,
    val payload: RequestPayload,
    val apiVersion: Int = 1
) : Parcelable 