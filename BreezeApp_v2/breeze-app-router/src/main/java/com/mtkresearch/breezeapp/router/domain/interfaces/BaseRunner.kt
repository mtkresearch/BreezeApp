package com.mtkresearch.breezeapp.router.domain.interfaces

import com.mtkresearch.breezeapp.router.domain.model.*

/**
 * Core interface for all AI Runner implementations.
 * 
 * This interface defines the contract that all AI runners must implement to integrate
 * with the BreezeApp Router system. It follows Clean Architecture principles by being
 * defined in the Domain layer while implementations reside in the Data layer.
 * 
 * ## Implementation Guidelines
 * - Runners should be stateless where possible
 * - Load/unload operations should be idempotent
 * - All methods should be thread-safe
 * - Errors should be communicated via InferenceResult.error()
 * 
 * ## Lifecycle
 * 1. **Creation**: Runner is instantiated via reflection
 * 2. **Loading**: [load] is called with model configuration
 * 3. **Processing**: [run] is called for each inference request
 * 4. **Cleanup**: [unload] is called when runner is no longer needed
 * 
 * ## Example Implementation
 * ```kotlin
 * class MyCustomRunner : BaseRunner {
 *     private var isModelLoaded = false
 *     
 *     override fun load(config: ModelConfig): Boolean {
 *         // Load your AI model here
 *         isModelLoaded = loadModel(config.modelPath)
 *         return isModelLoaded
 *     }
 *     
 *     override fun run(input: InferenceRequest): InferenceResult {
 *         if (!isModelLoaded) return InferenceResult.error("Model not loaded")
 *         // Process the request and return results
 *         return InferenceResult.success(mapOf("text" to processText(input)))
 *     }
 *     
 *     override fun getCapabilities() = listOf(CapabilityType.LLM)
 *     // ... implement other methods
 * }
 * ```
 * 
 * @see InferenceRequest for input format
 * @see InferenceResult for output format
 * @see CapabilityType for supported capabilities
 * @see FlowStreamingRunner for streaming support
 */
interface BaseRunner {
    
    /**
     * Initializes the AI model and allocates necessary resources.
     * 
     * This method should load the AI model from the specified configuration
     * and prepare it for inference. The implementation should be idempotent -
     * calling load multiple times should not cause issues.
     * 
     * @param config Model configuration containing paths, parameters, and metadata
     * @return true if the model was successfully loaded, false otherwise
     * 
     * @see ModelConfig for configuration format
     * @see unload to release resources
     */
    fun load(config: ModelConfig): Boolean
    
    /**
     * Executes AI inference on the provided input.
     * 
     * This is the core method that processes inference requests. The implementation
     * should handle the input data according to its capabilities and return
     * appropriate results or errors.
     * 
     * @param input The inference request containing input data and parameters
     * @param stream Whether to process in streaming mode (deprecated - use FlowStreamingRunner)
     * @return Inference result containing output data or error information
     * 
     * @see InferenceRequest for input format
     * @see InferenceResult for output format
     * @see FlowStreamingRunner for proper streaming support
     */
    fun run(input: InferenceRequest, stream: Boolean = false): InferenceResult
    
    /**
     * Releases all loaded models and allocated resources.
     * 
     * This method should clean up all resources used by the runner, including
     * model memory, GPU resources, and any temporary files. The implementation
     * should be idempotent and safe to call multiple times.
     * 
     * After calling unload(), the runner should not be used for inference
     * until load() is called again successfully.
     */
    fun unload()
    
    /**
     * Returns the list of AI capabilities supported by this runner.
     * 
     * This method defines what types of AI operations this runner can perform.
     * The router uses this information for request routing and capability discovery.
     * 
     * @return List of supported capability types (LLM, TTS, ASR, VLM, etc.)
     * @see CapabilityType for available capability types
     */
    fun getCapabilities(): List<CapabilityType>
    
    /**
     * Checks whether the AI model is currently loaded and ready for inference.
     * 
     * This method should return true only if the model is fully loaded and
     * ready to process requests. It's used by the router to determine if
     * load() needs to be called before processing requests.
     * 
     * @return true if the model is loaded and ready, false otherwise
     */
    fun isLoaded(): Boolean
    
    /**
     * Returns metadata information about this runner.
     * 
     * Provides descriptive information about the runner including its name,
     * version, capabilities, and other metadata useful for debugging and
     * system introspection.
     * 
     * @return Runner information containing name, version, and capabilities
     * @see RunnerInfo for metadata format
     */
    fun getRunnerInfo(): RunnerInfo
}

/**
 * Runner 資訊資料類別
 */
data class RunnerInfo(
    val name: String,
    val version: String,
    val capabilities: List<CapabilityType>,
    val description: String = "",
    val isMock: Boolean = false
) 