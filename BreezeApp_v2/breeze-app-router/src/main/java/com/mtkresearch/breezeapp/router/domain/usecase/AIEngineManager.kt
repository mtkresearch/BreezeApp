package com.mtkresearch.breezeapp.router.domain.usecase

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.FlowStreamingRunner
import com.mtkresearch.breezeapp.router.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * AIEngineManager
 * 
 * Use Case 層的核心業務邏輯，負責：
 * 1. Runner 的註冊、選擇和管理
 * 2. 推論請求的分發和處理
 * 3. Fallback 機制的實現
 * 4. 並發請求的處理
 * 
 * 遵循 Clean Architecture 和 MVVM + Use Case 模式
 */
class AIEngineManager {
    
    companion object {
        private const val TAG = "AIEngineManager"
    }
    
    // 執行緒安全的 Runner 儲存
    private val runnerRegistry = ConcurrentHashMap<String, () -> BaseRunner>()
    private val activeRunners = ConcurrentHashMap<String, BaseRunner>()
    private val defaultRunners = ConcurrentHashMap<CapabilityType, String>()
    
    // 讀寫鎖保護配置變更
    private val configLock = ReentrantReadWriteLock()
    
    /**
     * 註冊 Runner 工廠
     * @param name Runner 名稱
     * @param factory Runner 工廠函數
     */
    fun registerRunner(name: String, factory: () -> BaseRunner) {
        configLock.write {
            runnerRegistry[name] = factory
            Log.d(TAG, "Registered runner: $name")
        }
    }
    
    /**
     * 註銷 Runner
     * @param name Runner 名稱
     */
    fun unregisterRunner(name: String) {
        configLock.write {
            runnerRegistry.remove(name)
            // 如果有活躍的實例，也要清理
            activeRunners[name]?.let { runner ->
                runner.unload()
                activeRunners.remove(name)
            }
            Log.d(TAG, "Unregistered runner: $name")
        }
    }
    
    /**
     * 設定預設 Runner 映射
     * @param mappings 能力類型到 Runner 名稱的映射
     */
    fun setDefaultRunners(mappings: Map<CapabilityType, String>) {
        configLock.write {
            defaultRunners.clear()
            defaultRunners.putAll(mappings)
            Log.d(TAG, "Updated default runners: $mappings")
        }
    }
    
    /**
     * 處理推論請求
     * @param request 推論請求
     * @param capability 所需能力
     * @param preferredRunner 偏好的 Runner (可選)
     * @return 推論結果
     */
    fun process(
        request: InferenceRequest, 
        capability: CapabilityType,
        preferredRunner: String? = null
    ): InferenceResult {
        return try {
            val runner = selectRunner(capability, preferredRunner)
                ?: return InferenceResult.error(
                    RunnerError.runtimeError("No suitable runner found for capability: $capability")
                )
            
            Log.d(TAG, "Processing request ${request.sessionId} with runner: ${runner.getRunnerInfo().name}")
            
            if (!runner.isLoaded()) {
                Log.w(TAG, "Runner not loaded, attempting to load default config")
                val loaded = runner.load(createDefaultConfig(runner.getRunnerInfo().name))
                if (!loaded) {
                    return InferenceResult.error(RunnerError.modelNotLoaded())
                }
            }
            
            runner.run(request, stream = false)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing request", e)
            InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e))
        }
    }
    
    /**
     * 處理串流推論請求
     * @param request 推論請求
     * @param capability 所需能力
     * @param preferredRunner 偏好的 Runner (可選)
     * @return 推論結果的 Flow
     */
    fun processStream(
        request: InferenceRequest,
        capability: CapabilityType,
        preferredRunner: String? = null
    ): Flow<InferenceResult> = flow {
        try {
            val runner = selectRunner(capability, preferredRunner)
            if (runner == null) {
                emit(InferenceResult.error(
                    RunnerError.runtimeError("No suitable runner found for capability: $capability")
                ))
                return@flow
            }
            
            Log.d(TAG, "Processing stream request ${request.sessionId} with runner: ${runner.getRunnerInfo().name}")
            
            if (!runner.isLoaded()) {
                val loaded = runner.load(createDefaultConfig(runner.getRunnerInfo().name))
                if (!loaded) {
                    emit(InferenceResult.error(RunnerError.modelNotLoaded()))
                    return@flow
                }
            }
            
            // 檢查是否支援串流
            if (runner is FlowStreamingRunner) {
                runner.runAsFlow(request).collect { result ->
                    emit(result)
                }
            } else {
                // Fallback 到一般推論
                val result = runner.run(request, stream = false)
                emit(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing stream request", e)
            emit(InferenceResult.error(RunnerError.runtimeError(e.message ?: "Unknown error", e)))
        }
    }
    
    /**
     * 取得可用的 Runner 清單
     * @return Runner 名稱清單
     */
    fun getAvailableRunners(): List<String> {
        return configLock.read {
            runnerRegistry.keys.toList()
        }
    }
    
    /**
     * 取得指定能力的預設 Runner
     * @param capability 能力類型
     * @return Runner 名稱，如果未設定則為 null
     */
    fun getDefaultRunner(capability: CapabilityType): String? {
        return configLock.read {
            defaultRunners[capability]
        }
    }
    
    /**
     * 清理所有活躍的 Runner
     */
    fun cleanup() {
        configLock.write {
            activeRunners.values.forEach { runner ->
                try {
                    runner.unload()
                } catch (e: Exception) {
                    Log.e(TAG, "Error unloading runner: ${runner.getRunnerInfo().name}", e)
                }
            }
            activeRunners.clear()
            Log.d(TAG, "Cleaned up all active runners")
        }
    }
    
    /**
     * 選擇合適的 Runner
     * 實現 Fallback 策略
     */
    private fun selectRunner(capability: CapabilityType, preferredRunner: String?): BaseRunner? {
        return configLock.read {
            // 1. 嘗試使用偏好的 Runner
            preferredRunner?.let { name ->
                getOrCreateRunner(name)?.takeIf { 
                    it.getCapabilities().contains(capability) 
                }
            } ?: run {
                // 2. 使用預設 Runner
                defaultRunners[capability]?.let { defaultName ->
                    getOrCreateRunner(defaultName)
                } ?: run {
                    // 3. Fallback: 尋找任何支援該能力的 Runner
                    runnerRegistry.keys.firstNotNullOfOrNull { runnerName ->
                        getOrCreateRunner(runnerName)?.takeIf { runner ->
                            runner.getCapabilities().contains(capability)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 取得或建立 Runner 實例
     */
    private fun getOrCreateRunner(name: String): BaseRunner? {
        // 先檢查是否有活躍實例
        activeRunners[name]?.let { return it }
        
        // 建立新實例
        return runnerRegistry[name]?.let { factory ->
            try {
                val runner = factory()
                activeRunners[name] = runner
                Log.d(TAG, "Created new runner instance: $name")
                runner
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create runner: $name", e)
                null
            }
        }
    }
    
    /**
     * 建立預設配置
     */
    private fun createDefaultConfig(runnerName: String): ModelConfig {
        return if (runnerName.startsWith("Mock")) {
            ModelConfig.createMockConfig(runnerName)
        } else {
            ModelConfig(
                modelName = "default-$runnerName",
                parameters = mapOf("default" to true)
            )
        }
    }
} 