package com.mtkresearch.breezeapp.edgeai

/**
 * Base exception class for all EdgeAI SDK errors.
 * 
 * This is the parent class for all exceptions that can be thrown by the EdgeAI SDK.
 * You can catch this type to handle all EdgeAI-related errors, or catch specific 
 * subclasses for more granular error handling.
 */
open class EdgeAIException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when input parameters are invalid, missing required fields, or exceed allowed limits.
 * 
 * Common scenarios:
 * - Text input exceeds maximum length
 * - Required parameters are null or empty
 * - Parameter values are out of valid range
 * 
 * Example:
 * ```
 * throw InvalidInputException("Input text exceeds maximum length (4096 characters)")
 * ```
 */
class InvalidInputException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Thrown when the specified model does not exist or is not loaded.
 * 
 * Common scenarios:
 * - Model name is incorrect or not available
 * - Model failed to load during service initialization
 * - Model was unloaded due to resource constraints
 * 
 * Example:
 * ```
 * throw ModelNotFoundException("Model 'gpt-4o-mini-tts' not found")
 * ```
 */
class ModelNotFoundException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Thrown when an error occurs during model inference.
 * 
 * Common scenarios:
 * - Model encounters an internal error during processing
 * - Input data causes model to fail
 * - Memory issues during inference
 * 
 * Example:
 * ```
 * throw ModelInferenceException("Model inference failed due to out-of-memory")
 * ```
 */
class ModelInferenceException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Thrown when audio processing fails due to format or data issues.
 * 
 * Common scenarios:
 * - Audio file is corrupted
 * - Unsupported audio format
 * - Audio data cannot be decoded
 * 
 * Example:
 * ```
 * throw AudioProcessingException("Unsupported audio format: .xyz")
 * ```
 */
class AudioProcessingException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Thrown when the system does not have enough resources to complete the request.
 * 
 * Common scenarios:
 * - Insufficient GPU memory for inference
 * - CPU resources exhausted
 * - System RAM limit reached
 * 
 * Example:
 * ```
 * throw ResourceLimitException("Insufficient GPU memory for inference")
 * ```
 */
class ResourceLimitException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Thrown when the inference process takes too long and is aborted.
 * 
 * Common scenarios:
 * - Model inference exceeds configured timeout
 * - Network connection timeout (if applicable)
 * - Service becomes unresponsive
 * 
 * Example:
 * ```
 * throw TimeoutException("Inference timed out after 30 seconds")
 * ```
 */
class TimeoutException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Thrown when a requested feature, parameter, or format is not supported.
 * 
 * Common scenarios:
 * - Model doesn't support specific parameters
 * - Feature not available on current hardware
 * - API version mismatch
 * 
 * Example:
 * ```
 * throw NotSupportedException("Parameter 'speed' is not supported by this model")
 * ```
 */
class NotSupportedException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Thrown when an unexpected internal error occurs.
 * 
 * This is a catch-all exception for errors that don't fit into other categories.
 * If you encounter this frequently, it may indicate a bug in the SDK or service.
 * 
 * Example:
 * ```
 * throw InternalErrorException("Unexpected internal error occurred")
 * ```
 */
class InternalErrorException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause)

/**
 * Thrown when the EdgeAI SDK is not properly initialized or service connection fails.
 * 
 * Common scenarios:
 * - EdgeAI.initialize() was not called
 * - Service binding failed
 * - AI Router service is not installed
 * 
 * Example:
 * ```
 * throw ServiceConnectionException("EdgeAI SDK not initialized. Call EdgeAI.initialize() first.")
 * ```
 */
class ServiceConnectionException(message: String, cause: Throwable? = null) : EdgeAIException(message, cause) 