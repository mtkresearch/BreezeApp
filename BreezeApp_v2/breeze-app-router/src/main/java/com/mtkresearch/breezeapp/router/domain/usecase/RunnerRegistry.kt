package com.mtkresearch.breezeapp.router.domain.usecase

import com.mtkresearch.breezeapp.router.domain.usecase.Logger
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private const val TAG = "RunnerRegistry"

/**
 * RunnerRegistry
 * 
 * Runner 註冊管理器，實現 Factory Pattern
 * 負責 Runner 的動態註冊、註銷和實例化
 * 
 * 特性：
 * - 線程安全的註冊和查詢
 * - 支援 Factory Pattern 動態創建
 * - 基於能力的優先級選擇
 * - Runner 生命週期管理
 * - 異常處理和日誌記錄
 */
class RunnerRegistry(private val logger: Logger) {
    
    // 以能力為鍵，儲存一個按優先級排序的 RunnerRegistration 列表
    private val capabilitiesMap = ConcurrentHashMap<CapabilityType, MutableList<RunnerRegistration>>()
    private val runnersByName = ConcurrentHashMap<String, RunnerRegistration>()
    private val indexLock = ReentrantReadWriteLock()
    
    /**
     * Runner 工廠介面
     */
    fun interface RunnerFactory {
        fun create(): BaseRunner
    }
    
    /**
     * Runner 註冊資訊
     */
    data class RunnerRegistration(
        val name: String,
        val factory: RunnerFactory,
        val capabilities: List<CapabilityType>,
        val priority: Int, // 0=最高, 數字越大優先級越低
        val description: String = "",
        val version: String = "1.0.0"
    )
    
    /**
     * 註冊 Runner
     * @param registration Runner 註冊資訊
     */
    fun register(registration: RunnerRegistration) {
        indexLock.write {
            try {
                // 註冊到名稱映射中，方便按名稱查找
                runnersByName[registration.name] = registration
                
                // 更新能力索引
                registration.capabilities.forEach { capability ->
                    capabilitiesMap.getOrPut(capability) { mutableListOf() }
                        .apply {
                            // 移除舊的同名註冊，避免重複
                            removeAll { it.name == registration.name }
                            add(registration)
                            // 根據優先級排序，數字越小越靠前
                            sortBy { it.priority }
                        }
                }
                
                logger.d(TAG, "Registered runner: ${registration.name} with priority: ${registration.priority}")
            } catch (e: Exception) {
                logger.e(TAG, "Failed to register runner: ${registration.name}", e)
                throw e
            }
        }
    }
    
    /**
     * 註冊 Runner (簡化版本)
     * @param name Runner 名稱
     * @param factory Runner 工廠函數
     * @param priority 優先級，數字越小越高
     */
    fun register(name: String, factory: RunnerFactory, priority: Int = 10) {
        try {
            // 嘗試創建實例以獲取能力資訊
            val tempInstance = factory.create()
            val capabilities = tempInstance.getCapabilities()
            val info = tempInstance.getRunnerInfo()
            
            // 清理臨時實例
            tempInstance.unload()
            
            register(RunnerRegistration(
                name = name,
                factory = factory,
                capabilities = capabilities,
                priority = priority, // Use provided priority
                description = info.description,
                version = info.version
            ))
        } catch (e: Exception) {
            logger.e(TAG, "Failed to register runner: $name", e)
            throw RunnerRegistrationException("Failed to register runner: $name", e)
        }
    }
    
    /**
     * 註銷 Runner
     * @param name Runner 名稱
     */
    fun unregister(name: String) {
        indexLock.write {
            try {
                runnersByName.remove(name)
                
                // 從能力索引中移除
                capabilitiesMap.values.forEach { runnerList ->
                    runnerList.removeAll { it.name == name }
                }
                
                logger.d(TAG, "Unregistered runner: $name")
            } catch (e: Exception) {
                logger.e(TAG, "Failed to unregister runner: $name", e)
            }
        }
    }
    
    /**
     * 創建 Runner 實例 (按名稱)
     * @param name Runner 名稱
     * @return Runner 實例，如果未找到則返回 null
     */
    fun createRunner(name: String): BaseRunner? {
        return indexLock.read {
            try {
                runnersByName[name]?.factory?.create()?.also {
                    logger.d(TAG, "Created runner instance by name: $name")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to create runner by name: $name", e)
                null
            }
        }
    }
    
    /**
     * 根據能力獲取最佳的 Runner 實例
     * @param capability 能力類型
     * @return 優先級最高的 Runner 實例，如果未找到則返回 null
     */
    fun getRunnerForCapability(capability: CapabilityType): BaseRunner? {
        return indexLock.read {
            try {
                // 因為列表已經排序，直接取第一個
                capabilitiesMap[capability]?.firstOrNull()?.factory?.create()?.also {
                    logger.d(TAG, "Created best runner for capability $capability: ${it.getRunnerInfo().name}")
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to create runner for capability: $capability", e)
                null
            }
        }
    }
    
    /**
     * 檢查 Runner 是否已註冊
     * @param name Runner 名稱
     * @return 是否已註冊
     */
    fun isRegistered(name: String): Boolean {
        return runnersByName.containsKey(name)
    }
    
    /**
     * 取得所有已註冊的 Runner 名稱
     * @return Runner 名稱列表
     */
    fun getRegisteredRunners(): List<String> {
        return runnersByName.keys.toList()
    }
    
    /**
     * 根據能力類型查詢支援的 Runner (按優先級排序)
     * @param capability 能力類型
     * @return 支援該能力的 RunnerRegistration 列表
     */
    fun getRunnersForCapability(capability: CapabilityType): List<RunnerRegistration> {
        return indexLock.read {
            capabilitiesMap[capability]?.toList() ?: emptyList()
        }
    }
    
    /**
     * 取得所有支援的能力類型
     * @return 能力類型列表
     */
    fun getSupportedCapabilities(): List<CapabilityType> {
        return capabilitiesMap.keys.toList()
    }
    
    /**
     * 清空所有註冊的 Runner
     */
    fun clear() {
        indexLock.write {
            runnersByName.clear()
            capabilitiesMap.clear()
            logger.d(TAG, "Cleared all registered runners")
        }
    }
    
    /**
     * 取得註冊統計資訊
     */
    fun getRegistryStats(): RegistryStats {
        return indexLock.read {
            RegistryStats(
                totalRunners = runnersByName.size,
                capabilityCount = capabilitiesMap.size,
                runnersPerCapability = capabilitiesMap.mapValues { it.value.size }
            )
        }
    }
    
    /**
     * 驗證 Runner 是否正常
     * @param name Runner 名稱
     * @return 驗證結果
     */
    fun validateRunner(name: String): ValidationResult {
        return try {
            val runner = createRunner(name)
                ?: return ValidationResult.failure("Runner not found: $name")
            
            // 基本驗證
            val info = runner.getRunnerInfo()
            val capabilities = runner.getCapabilities()
            
            runner.unload() // 清理測試實例
            
            ValidationResult.success("Runner validation passed", mapOf(
                "name" to info.name,
                "version" to info.version,
                "capabilities" to capabilities.map { it.name }
            ))
        } catch (e: Exception) {
            ValidationResult.failure("Runner validation failed: ${e.message}")
        }
    }
}

/**
 * 註冊統計資訊
 */
data class RegistryStats(
    val totalRunners: Int,
    val capabilityCount: Int,
    val runnersPerCapability: Map<CapabilityType, Int>
)

/**
 * 驗證結果
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String,
    val metadata: Map<String, Any> = emptyMap()
) {
    companion object {
        fun success(message: String, metadata: Map<String, Any> = emptyMap()) = 
            ValidationResult(true, message, metadata)
        
        fun failure(message: String) = ValidationResult(false, message)
    }
}

/**
 * Runner 註冊異常
 */
class RunnerRegistrationException(message: String, cause: Throwable? = null) : 
    Exception(message, cause)