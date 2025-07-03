package com.mtkresearch.breezeapp.router.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Defines the structure for the entire runner configuration file (e.g., runner_config.json).
 */
@Serializable
data class RunnerConfigFile(
    val runners: List<RunnerDefinition>
)

/**
 * Defines the structure for a single runner's configuration in the JSON file.
 */
@Serializable
data class RunnerDefinition(
    /** The unique name/identifier for the runner. */
    val name: String,

    /** The fully qualified class name of the runner implementation. */
    @SerialName("class")
    val className: String,

    /** A list of capability names (e.g., "LLM", "ASR") that this runner supports. */
    val capabilities: List<String>,

    /** The priority of the runner (lower is higher). */
    val priority: Int,

    /** A flag to indicate if this is a "real" runner that requires a support check. */
    @SerialName("is_real")
    val isReal: Boolean = false
) 