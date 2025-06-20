package com.mtkresearch.breezeapp

import com.google.gson.annotations.SerializedName

data class LLMModelConfig(
    @SerializedName("models")
    val models: List<Model>
) {
    data class Model(
        @SerializedName("id") val id: String,
        @SerializedName("runner") val runner: String,
        @SerializedName("backend") val backend: String,
        @SerializedName("ramGB") val ramGB: Int,
        @SerializedName("model_entry_path") val modelEntryPath: String,
        @SerializedName("urls") val urls: List<String>
    )
}