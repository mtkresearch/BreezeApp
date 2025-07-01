package com.mtkresearch.breezeapp.router.domain.interfaces

import com.mtkresearch.breezeapp.router.domain.model.*

/**
 * BaseRunner 核心介面
 * 所有 AI Runner 實作的統一基礎介面
 * 
 * 遵循 Clean Architecture 原則，此介面定義於 Domain 層
 * 具體實作位於 Data 層或 Infrastructure 層
 */
interface BaseRunner {
    
    /**
     * 初始化模型與資源
     * @param config 模型配置資訊
     * @return 是否載入成功
     */
    fun load(config: ModelConfig): Boolean
    
    /**
     * 執行推論
     * @param input 推論請求
     * @param stream 是否為串流模式
     * @return 推論結果
     */
    fun run(input: InferenceRequest, stream: Boolean = false): InferenceResult
    
    /**
     * 卸載資源
     * 釋放所有已載入的模型和佔用的資源
     */
    fun unload()
    
    /**
     * 回傳支援的能力清單
     * @return 支援的能力類型列表
     */
    fun getCapabilities(): List<CapabilityType>
    
    /**
     * 檢查是否已載入模型
     * @return 模型是否已載入
     */
    fun isLoaded(): Boolean
    
    /**
     * 取得 Runner 資訊
     * @return Runner 的基本資訊 (名稱、版本等)
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