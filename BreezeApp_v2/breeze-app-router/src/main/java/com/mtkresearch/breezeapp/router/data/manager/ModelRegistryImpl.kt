package com.mtkresearch.breezeapp.router.data.manager

import android.content.Context
import com.mtkresearch.breezeapp.router.domain.model.ModelFile
import com.mtkresearch.breezeapp.router.domain.model.ModelInfo
import com.mtkresearch.breezeapp.router.data.manager.ModelRegistry
import org.json.JSONArray
import org.json.JSONObject

class ModelRegistryImpl(private val context: Context) : ModelRegistry {
    private val assetFile = "fullModelList.json"

    override fun listAllModels(): List<ModelInfo> {
        val result = mutableListOf<ModelInfo>()
        try {
            val json = context.assets.open(assetFile).bufferedReader().use { it.readText() }
            val root = org.json.JSONObject(json)
            val models = root.optJSONArray("models") ?: org.json.JSONArray()
            for (i in 0 until models.length()) {
                val m = models.getJSONObject(i)
                val filesArr = m.optJSONArray("files") ?: org.json.JSONArray()
                val files = mutableListOf<ModelFile>()
                for (j in 0 until filesArr.length()) {
                    val f = filesArr.getJSONObject(j)
                    files.add(
                        ModelFile(
                            fileName = f.optString("fileName", null),
                            group = f.optString("group", null),
                            pattern = f.optString("pattern", null),
                            type = f.optString("type", "model"),
                            urls = f.optJSONArray("urls")?.let { arr ->
                                List(arr.length()) { arr.getString(it) }
                            } ?: emptyList()
                        )
                    )
                }
                val entryPoint = m.optJSONObject("entry_point")
                result.add(
                    ModelInfo(
                        id = m.optString("id"),
                        name = m.optString("name", m.optString("id")),
                        version = m.optString("version", ""),
                        runner = m.optString("runner"),
                        files = files,
                        backend = m.optString("backend"),
                        ramGB = m.optInt("ramGB", 0),
                        entryPointType = entryPoint?.optString("type"),
                        entryPointValue = entryPoint?.optString("value")
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    override fun getModelInfo(modelId: String): ModelInfo? = listAllModels().find { it.id == modelId }
    override fun filterByHardware(hw: String): List<ModelInfo> = listAllModels().filter { it.backend == hw }
} 