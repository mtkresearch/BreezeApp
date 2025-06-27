package com.mtkresearch.breezeapp_ui.domain.model

/**
 * Represents the author of a message.
 */
enum class MessageAuthor {
    /** The user of the app. */
    USER,

    /** The AI model. */
    AI,

    /** A system information message (e.g., "Connected to AI Router"). */
    SYSTEM_INFO,

    /** A system error message (e.g., "Connection failed"). */
    SYSTEM_ERROR
} 