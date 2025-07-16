package com.mtkresearch.breezeapp.router.domain.usecase

import com.mtkresearch.breezeapp.router.domain.usecase.Logger
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.interfaces.FlowStreamingRunner
import com.mtkresearch.breezeapp.router.domain.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

// Private exception to wrap our custom RunnerError for use with Kotlin's Result type.
private class RunnerSelectionException(val runnerError: RunnerError) : Exception()

class AIEngineManager(
    private val runnerRegistry: RunnerRegistry,
    private val logger: Logger
) {
    
    companion object {
        private const val TAG = "AIEngineManager"
    }
    
    // 執行緒安全的 Runner 儲存
    private val activeRunners = ConcurrentHashMap<String, BaseRunner>()
    private val defaultRunners = ConcurrentHashMap<CapabilityType, String>()
    
    // 讀寫鎖保護配置變更
    private val configLock = ReentrantReadWriteLock()
    
    // 請求取消支援
    private val activeRequests = ConcurrentHashMap<String, Job>()
    
    /**
     * 設定預設 Runner 映射
     * @param mappings 能力類型到 Runner 名稱的映射
     */
    fun setDefaultRunners(mappings: Map<CapabilityType, String>) {
        configLock.write {
            defaultRunners.clear()
            defaultRunners.putAll(mappings)
            logger.d(TAG, "Updated default runners: $mappings")
        }
    }
    
    /**
     * 處理推論請求
     * @param request 推論請求
     * @param capability 所需能力
     * @param preferredRunner 偏好的 Runner (可選)
     * @return 推論結果
     */
    suspend fun process(
        request: InferenceRequest, 
        capability: CapabilityType,
        preferredRunner: String? = null
    ): InferenceResult {
        val requestId = request.sessionId ?: "request-${System.currentTimeMillis()}"
        return try {
            selectAndLoadRunner(capability, preferredRunner).fold(
                onSuccess = { runner ->
                    logger.d(TAG, "Processing request $requestId with runner: ${runner.getRunnerInfo().name}")
                    runner.run(request)
                },
                onFailure = { error ->
                    val runnerError = if (error is RunnerSelectionException) error.runnerError else RunnerError("E101", error.message ?: "Unknown runtime error")
                    InferenceResult.error(runnerError)
                }
            )
        } catch (e: Exception) {
            logger.e(TAG, "Error processing request", e)
            InferenceResult.error(RunnerError("E101", e.message ?: "Unknown error", true, e))
        } finally {
            // Clean up completed request from tracking
            activeRequests.remove(requestId)
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
        val requestId = request.sessionId ?: "stream-${System.currentTimeMillis()}"
        try {
            selectAndLoadRunner(capability, preferredRunner).fold(
                onSuccess = { runner ->
                    logger.d(TAG, "Processing stream request $requestId with runner: ${runner.getRunnerInfo().name}")
                    if (runner is FlowStreamingRunner) {
                        runner.runAsFlow(request).collect { emit(it) }
                    } else {
                        emit(InferenceResult.error(RunnerError("E406", "Runner does not support streaming.")))
                    }
                },
                onFailure = { error ->
                    val runnerError = if (error is RunnerSelectionException) error.runnerError else RunnerError("E101", error.message ?: "Unknown runtime error")
                    emit(InferenceResult.error(runnerError))
                }
            )
        } catch (e: CancellationException) {
            logger.d(TAG, "Stream request $requestId was cancelled")
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Error processing stream request", e)
            emit(InferenceResult.error(RunnerError("E101", e.message ?: "Unknown error", true, e)))
        } finally {
            // Clean up completed stream request from tracking
            activeRequests.remove(requestId)
        }
    }
    
    /**
     * 取消正在進行的請求
     * @param requestId 請求ID
     * @return 是否成功取消
     */
    fun cancelRequest(requestId: String): Boolean {
        return try {
            val job = activeRequests.remove(requestId)
            if (job != null) {
                job.cancel()
                logger.d(TAG, "Cancelled request: $requestId")
                true
            } else {
                logger.w(TAG, "Request not found for cancellation: $requestId")
                false
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error cancelling request: $requestId", e)
            false
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
                    logger.e(TAG, "Error unloading runner: ${runner.getRunnerInfo().name}", e)
                }
            }
            activeRunners.clear()
            logger.d(TAG, "Cleaned up all active runners")
        }
    }
    
    /**
     * 選擇合適的 Runner
     * 實現 Fallback 策略
     */
    private suspend fun selectAndLoadRunner(
        capability: CapabilityType,
        preferredRunner: String?
    ): Result<BaseRunner> {
        val runnerName: String?
        
        // 1. Select runner name
        if (preferredRunner != null) {
            if (!runnerRegistry.isRegistered(preferredRunner)) {
                return Result.failure(RunnerSelectionException(RunnerError("E404", "Runner not found: $preferredRunner")))
            }
            runnerName = preferredRunner
        } else {
            runnerName = configLock.read {
                defaultRunners[capability] ?: runnerRegistry.getRunnersForCapability(capability).firstOrNull()?.name
            }
        }

        if (runnerName == null) {
            return Result.failure(RunnerSelectionException(RunnerError("E404", "No runner found for capability: ${capability.name}")))
        }

        // 2. Get or create runner instance
        val runner = getOrCreateRunner(runnerName) 
            ?: return Result.failure(RunnerSelectionException(RunnerError("E404", "Failed to create runner: $runnerName")))

        // 3. Check capability
        if (!runner.getCapabilities().contains(capability)) {
            return Result.failure(RunnerSelectionException(RunnerError("E405", "Runner $runnerName does not support capability: ${capability.name}")))
        }
        
        // 4. Load runner if needed
        if (!runner.isLoaded()) {
            logger.d(TAG, "Runner $runnerName not loaded, attempting to load...")
            val loaded = runner.load(createDefaultConfig(runnerName))
            if (!loaded) {
                return Result.failure(RunnerSelectionException(RunnerError("E501", "Failed to load model for runner: $runnerName")))
            }
        }
        
        return Result.success(runner)
    }
    
    /**
     * 取得或建立 Runner 實例
     */
    private fun getOrCreateRunner(name: String): BaseRunner? {
        configLock.read {
            activeRunners[name]?.let { return it }
        }
        
        return configLock.write {
            // Double-check lock
            activeRunners[name]?.let { return@write it }

            try {
                runnerRegistry.createRunner(name)?.also { runner ->
                    activeRunners[name] = runner
                    logger.d(TAG, "Created new runner instance: $name")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to create runner: $name", e)
                null
            }
        }
    }
    
    /**
     * 建立預設配置
     */
    private fun createDefaultConfig(runnerName: String): ModelConfig {
        return ModelConfig(
            modelName = runnerName,
            modelPath = "/data/local/tmp/models/$runnerName"
        )
    }
} 