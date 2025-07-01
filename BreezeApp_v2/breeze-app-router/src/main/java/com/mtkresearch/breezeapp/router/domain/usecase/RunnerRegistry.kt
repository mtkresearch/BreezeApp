 package com.mtkresearch.breezeapp.router.domain.usecase

import android.util.Log
import com.mtkresearch.breezeapp.router.domain.interfaces.BaseRunner
import com.mtkresearch.breezeapp.router.domain.model.CapabilityType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * RunnerRegistry
 * 
 * Runner 註冊管理器，實現 Factory Pattern
 * 負責 Runner 的動態註冊、註銷和實例化
 * 
 * 特性：
 * - 線程安全的註冊和查詢
 * - 支援 Factory Pattern 動態創建
 * - 能力類型索引
 * - Runner 生命週期管理
 * - 異常處理和日誌記錄
 */
class RunnerRegistry {
    
    companion object {
        private const val TAG = "RunnerRegistry"
        
        @Volatile
        private var INSTANCE: RunnerRegistry? = null
        
        fun getInstance(): RunnerRegistry {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RunnerRegistry().also { INSTANCE = it }
            }
        }
    }
    
    // Runner 工廠儲存
    private val runnerFactories = ConcurrentHashMap<String, RunnerFactory>()
    
    // 能力類型索引 (快速查詢支援特定能力的 Runner)
    private val capabilityIndex = ConcurrentHashMap<CapabilityType, MutableSet<String>>()
    
    // 讀寫鎖保護索引更新
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
        val description: String = "",
        val version: String = "1.0.0",
        val isMock: Boolean = false
    )
    
    /**
     * 註冊 Runner
     * @param registration Runner 註冊資訊
     */
    fun register(registration: RunnerRegistration) {
        indexLock.write {
            try {
                runnerFactories[registration.name] = registration.factory
                
                // 更新能力索引
                registration.capabilities.forEach { capability ->
                    capabilityIndex.getOrPut(capability) { ConcurrentHashMap.newKeySet() }
                        .add(registration.name)
                }
                
                Log.d(TAG, "Registered runner: ${registration.name} with capabilities: ${registration.capabilities}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register runner: ${registration.name}", e)
                throw e
            }
        }
    }
    
    /**
     * 註冊 Runner (簡化版本)
     * @param name Runner 名稱
     * @param factory Runner 工廠函數
     */
    fun register(name: String, factory: RunnerFactory) {
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
                description = info.description,
                version = info.version,
                isMock = info.isMock
            ))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register runner: $name", e)
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
                runnerFactories.remove(name)
                
                // 從能力索引中移除
                capabilityIndex.values.forEach { runnerSet ->
                    runnerSet.remove(name)
                }
                
                Log.d(TAG, "Unregistered runner: $name")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister runner: $name", e)
            }
        }
    }
    
    /**
     * 創建 Runner 實例
     * @param name Runner 名稱
     * @return Runner 實例，如果未找到則返回 null
     */
    fun createRunner(name: String): BaseRunner? {
        return indexLock.read {
            try {
                runnerFactories[name]?.let { factory ->
                    val runner = factory.create()
                    Log.d(TAG, "Created runner instance: $name")
                    runner
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create runner: $name", e)
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
        return runnerFactories.containsKey(name)
    }
    
    /**
     * 取得所有已註冊的 Runner 名稱
     * @return Runner 名稱列表
     */
    fun getRegisteredRunners(): List<String> {
        return runnerFactories.keys.toList()
    }
    
    /**
     * 根據能力類型查詢支援的 Runner
     * @param capability 能力類型
     * @return 支援該能力的 Runner 名稱列表
     */
    fun getRunnersForCapability(capability: CapabilityType): List<String> {
        return indexLock.read {
            capabilityIndex[capability]?.toList() ?: emptyList()
        }
    }
    
    /**
     * 取得所有支援的能力類型
     * @return 能力類型列表
     */
    fun getSupportedCapabilities(): List<CapabilityType> {
        return capabilityIndex.keys.toList()
    }
    
    /**
     * 清空所有註冊的 Runner
     */
    fun clear() {
        indexLock.write {
            runnerFactories.clear()
            capabilityIndex.clear()
            Log.d(TAG, "Cleared all registered runners")
        }
    }
    
    /**
     * 取得註冊統計資訊
     */
    fun getRegistryStats(): RegistryStats {
        return indexLock.read {
            RegistryStats(
                totalRunners = runnerFactories.size,
                capabilityCount = capabilityIndex.size,
                runnersPerCapability = capabilityIndex.mapValues { it.value.size }
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