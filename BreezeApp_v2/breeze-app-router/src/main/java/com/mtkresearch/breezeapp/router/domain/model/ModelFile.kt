package com.mtkresearch.breezeapp.router.domain.model

/**
 * 單一模型檔案描述
 * - 支援 group/pattern/type/urls
 */
data class ModelFile(
    val fileName: String? = null,
    val group: String? = null,
    val pattern: String? = null,
    val type: String = "model",
    val urls: List<String>
)

/**
 * 模型資訊描述
 * - 支援 entry_point (type/value)
 */
data class ModelInfo(
    val id: String,
    val name: String,
    val version: String = "",
    val runner: String,
    val files: List<ModelFile>,
    val backend: String,
    val ramGB: Int = 0,
    val entryPointType: String? = null,
    val entryPointValue: String? = null
) 