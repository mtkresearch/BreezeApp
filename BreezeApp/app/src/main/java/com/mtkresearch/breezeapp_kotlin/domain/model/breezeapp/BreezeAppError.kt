package com.mtkresearch.breezeapp_kotlin.domain.model.breezeapp

/**
 * BreezeApp Engine error types
 * 
 * This sealed class defines all possible error types that can occur
 * when interacting with BreezeApp Engine. It follows Clean Architecture
 * principles by being independent of external frameworks.
 */
sealed class BreezeAppError(message: String) : Exception(message) {
    
    /**
     * Connection-related errors
     */
    sealed class ConnectionError(message: String) : BreezeAppError(message) {
        data class ServiceNotFound(override val message: String) : ConnectionError(message)
        data class ServiceDisconnected(override val message: String) : ConnectionError(message)
        data class Timeout(override val message: String) : ConnectionError(message)
        data class InitializationFailed(override val message: String) : ConnectionError(message)
    }
    
    /**
     * Chat-related errors
     */
    sealed class ChatError(message: String) : BreezeAppError(message) {
        data class InvalidInput(override val message: String) : ChatError(message)
        data class ModelNotFound(override val message: String) : ChatError(message)
        data class GenerationFailed(override val message: String) : ChatError(message)
        data class StreamingError(override val message: String) : ChatError(message)
    }
    
    /**
     * TTS-related errors
     */
    sealed class TtsError(message: String) : BreezeAppError(message) {
        data class InvalidText(override val message: String) : TtsError(message)
        data class AudioGenerationFailed(override val message: String) : TtsError(message)
        data class UnsupportedLanguage(override val message: String) : TtsError(message)
    }
    
    /**
     * ASR-related errors
     */
    sealed class AsrError(message: String) : BreezeAppError(message) {
        data class AudioFileNotFound(override val message: String) : AsrError(message)
        data class AudioFormatNotSupported(override val message: String) : AsrError(message)
        data class RecognitionFailed(override val message: String) : AsrError(message)
        data class MicrophonePermissionDenied(override val message: String) : AsrError(message)
    }
    
    /**
     * Image analysis errors
     */
    sealed class ImageAnalysisError(message: String) : BreezeAppError(message) {
        data class ImageNotFound(override val message: String) : ImageAnalysisError(message)
        data class ImageFormatNotSupported(override val message: String) : ImageAnalysisError(message)
        data class AnalysisFailed(override val message: String) : ImageAnalysisError(message)
    }
    
    /**
     * Generic errors
     */
    data class UnknownError(override val message: String) : BreezeAppError(message)
}