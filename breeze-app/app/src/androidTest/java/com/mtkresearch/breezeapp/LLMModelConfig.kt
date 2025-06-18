package com.mtkresearch.breezeapp

data class LLMModelConfig(
    val models: List<Model>
) {
    data class Model(
        val id: String,
        val runner: String,
        val backend: String,
        val ramGB: Int,
        val model_entry_path: String,
        val urls: List<String>
    )
}