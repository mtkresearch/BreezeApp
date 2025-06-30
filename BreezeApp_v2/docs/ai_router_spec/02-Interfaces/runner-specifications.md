# 📋 Runner 詳細規格

## 🎯 目標與範圍

本文件定義 AI Router 中可註冊 Runner 的基本規格、功能支援範圍與平台需求，協助實作者判斷模型適用性與整合限制。每個 Runner 都必須符合本規格要求，以確保系統的一致性和可維護性。

## 📊 Runner 能力矩陣

### 核心 Runner 規格表

| Runner 名稱 | 能力支援 | 支援平台 | 支援格式 | Streaming | Fallback | Thread-Safe | 優先級 |
|-------------|----------|----------|----------|-----------|----------|-------------|--------|
| `GpuOnnxLLMRunner` | LLM | Android GPU | ONNX | ✅ | ✅ | ✅ | 🥇 High |
| `CpuExecutorchLLMRunner` | LLM | Android CPU | PTE | ✅ | ✅ | ✅ | 🥈 Medium |
| `SherpaASRRunner` | ASR | Android CPU/NPU | ONNX | ✅ | ✅ | ⚠️ | 🥇 High |
| `WhisperOnnxRunner` | ASR | Android CPU/GPU | ONNX | ❌ | ✅ | ✅ | 🥈 Medium |
| `SherpaTTSRunner` | TTS | Android CPU/NPU | ONNX | ❌ | ✅ | ✅ | 🥇 High |
| `MtkTTSRunner` | TTS | MTK NPU | DLA/Custom | ❌ | ✅ | ✅ | 🥈 Medium |
| `ApiLLMRunner` | LLM | 全平台 | API/Remote | ✅ | ✅ | ✅ | 🔄 Fallback |
| `QNNRunner` | LLM | Qualcomm NPU | QNN Graph/QDQ | ❌ | ❌ | ✅ | 🥇 High |
| `GpuVLMRunner` | VLM | Android GPU | ONNX | ✅ | ✅ | ✅ | 🥇 High |
| `LocalGuardianRunner` | Guardian | Android CPU | ONNX | ❌ | ✅ | ✅ | 🥇 High |

### 圖例說明

| 符號 | 意義 | 說明 |
|------|------|------|
| ✅ | 完全支援 | 功能完整實作且穩定 |
| ⚠️ | 有限支援 | 支援但有特殊限制或注意事項 |
| ❌ | 不支援 | 此功能未實作或不適用 |
| 🥇 | 高優先級 | 推薦的首選實作 |
| 🥈 | 中優先級 | 備選實作方案 |
| 🥉 | 低優先級 | 最後選擇或相容性方案 |
| 🔄 | Fallback | 主要作為備援或降級方案 |

## 📦 Runner 元資料規範

### 必要元資料欄位

每個 Runner 必須提供以下 metadata 供 AI Router 註冊與管理：

```kotlin
data class RunnerMetadata(
    val name: String,                    // Runner 唯一識別名稱
    val capabilities: List<CapabilityType>, // 支援的能力列表
    val supportedFormats: List<String>,  // 支援的模型格式
    val supportedPlatforms: List<String>, // 支援的平台
    val supportsStreaming: Boolean,      // 是否支援串流
    val supportsFallback: Boolean,       // 是否支援降級
    val isThreadSafe: Boolean,           // 是否為執行緒安全
    val priority: Int,                   // 優先級 (1-10, 越高越優先)
    val minMemoryMB: Int,               // 最小記憶體需求 (MB)
    val maxConcurrentSessions: Int,      // 最大併發 session 數
    val initializationTimeMs: Long,      // 預期初始化時間 (ms)
    val averageLatencyMs: Long,          // 平均推論延遲 (ms)
    val powerConsumption: PowerLevel,    // 功耗等級
    val hardwareRequirements: List<String>, // 硬體需求
    val version: String,                 // Runner 版本
    val description: String              // 描述與使用說明
)

enum class PowerLevel { LOW, MEDIUM, HIGH, VERY_HIGH }
```

### 範例實作

```kotlin
class GpuOnnxLLMRunner : BaseRunner {
    companion object {
        val METADATA = RunnerMetadata(
            name = "GpuOnnxLLMRunner",
            capabilities = listOf(CapabilityType.LLM),
            supportedFormats = listOf("onnx"),
            supportedPlatforms = listOf("android_gpu", "android_cpu"),
            supportsStreaming = true,
            supportsFallback = true,
            isThreadSafe = true,
            priority = 9,
            minMemoryMB = 2048,
            maxConcurrentSessions = 4,
            initializationTimeMs = 3000,
            averageLatencyMs = 150,
            powerConsumption = PowerLevel.HIGH,
            hardwareRequirements = listOf("GPU_MEMORY_2GB", "OPENGL_ES_3_2"),
            version = "1.2.0",
            description = "GPU-accelerated ONNX LLM runner with CPU fallback support"
        )
    }
    
    override fun getMetadata(): RunnerMetadata = METADATA
}
```

## 🔧 系統整合規範

### RunnerRegistry 整合

```kotlin
object RunnerRegistry {
    private val registeredRunners = mutableMapOf<String, RunnerFactory>()
    private val runnerMetadata = mutableMapOf<String, RunnerMetadata>()
    
    fun register(factory: RunnerFactory) {
        val metadata = factory.createRunner().getMetadata()
        registeredRunners[metadata.name] = factory
        runnerMetadata[metadata.name] = metadata
    }
    
    fun getMetadata(runnerName: String): RunnerMetadata? {
        return runnerMetadata[runnerName]
    }
    
    fun getAvailableRunners(capability: CapabilityType): List<RunnerMetadata> {
        return runnerMetadata.values
            .filter { it.capabilities.contains(capability) }
            .sortedByDescending { it.priority }
    }
}
```

### ModelSelector 整合

```kotlin
class ModelSelector {
    fun selectRunner(
        capability: CapabilityType,
        requirements: InferenceRequirements,
        deviceInfo: DeviceInfo
    ): RunnerMetadata? {
        return RunnerRegistry.getAvailableRunners(capability)
            .filter { it.isCompatible(deviceInfo) }
            .filter { it.meetsRequirements(requirements) }
            .firstOrNull()
    }
}

fun RunnerMetadata.isCompatible(deviceInfo: DeviceInfo): Boolean {
    // 檢查平台相容性
    if (!supportedPlatforms.any { platform -> 
        deviceInfo.matchesPlatform(platform) 
    }) return false
    
    // 檢查記憶體需求
    if (deviceInfo.availableMemoryMB < minMemoryMB) return false
    
    // 檢查硬體需求
    return hardwareRequirements.all { requirement ->
        deviceInfo.supportsHardwareRequirement(requirement)
    }
}
```

### Dispatcher 整合

```kotlin
class RequestDispatcher {
    fun dispatch(request: InferenceRequest): InferenceResult {
        val runnerMetadata = selectOptimalRunner(request)
        
        // 根據 thread safety 決定執行策略
        return if (runnerMetadata.isThreadSafe) {
            executeWithSharedRunner(request, runnerMetadata)
        } else {
            executeWithDedicatedRunner(request, runnerMetadata)
        }
    }
    
    private fun selectOptimalRunner(request: InferenceRequest): RunnerMetadata {
        val candidates = RunnerRegistry.getAvailableRunners(request.capability)
        
        return candidates
            .filter { it.isCompatible(deviceInfo) }
            .filter { it.supportsFallback || isFirstChoice }
            .maxByOrNull { calculateScore(it, request) }
            ?: throw NoSuitableRunnerException(request.capability)
    }
}
```

### FallbackPolicy 整合

```kotlin
class FallbackPolicy {
    fun getFallbackChain(
        capability: CapabilityType,
        failedRunner: String
    ): List<RunnerMetadata> {
        return RunnerRegistry.getAvailableRunners(capability)
            .filter { it.name != failedRunner }
            .filter { it.supportsFallback }
            .sortedByDescending { it.priority }
    }
}
```

## 📋 Runner 實作標準

### 必要介面實作

所有 Runner 必須實作以下核心介面（詳見 [runner-interface.md](./runner-interface.md)）：

```kotlin
interface BaseRunner {
    fun getMetadata(): RunnerMetadata
    fun load(config: ModelConfig): Boolean
    fun run(input: InferenceRequest, stream: Boolean = false): InferenceResult
    fun unload(): Unit
    fun getCapabilities(): List<CapabilityType>
}
```

### 串流支援實作

支援串流的 Runner 應額外實作：

```kotlin
interface StreamingRunner : BaseRunner {
    fun runStream(
        input: InferenceRequest,
        onResult: (InferenceResult) -> Unit,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    )
    
    fun cancelStream(sessionId: String): Boolean
}
```

### 錯誤處理實作

```kotlin
interface ErrorAwareRunner : BaseRunner {
    fun getLastError(): RunnerError?
    fun getSupportedErrorCodes(): List<String>
    fun canRecover(error: RunnerError): Boolean
}
```

## 🔍 相容性與限制規範

### 平台相容性檢查

```kotlin
enum class PlatformType {
    ANDROID_CPU,
    ANDROID_GPU,
    ANDROID_NPU_QUALCOMM,
    ANDROID_NPU_MTK,
    ANDROID_NPU_SAMSUNG,
    CLOUD_API
}

fun DeviceInfo.supportsRunner(metadata: RunnerMetadata): Boolean {
    return when {
        metadata.supportedPlatforms.contains("android_gpu") && 
        !this.hasGPU -> false
        
        metadata.supportedPlatforms.contains("qualcomm_npu") && 
        !this.isQualcommDevice -> false
        
        metadata.minMemoryMB > this.availableMemoryMB -> false
        
        else -> true
    }
}
```

### 執行緒安全性規範

| Thread Safety Level | 說明 | 實作要求 |
|---------------------|------|----------|
| **完全安全** (✅) | 可同時被多個執行緒調用 | 內部狀態使用同步機制保護 |
| **有限安全** (⚠️) | 需要額外限制 | 限制同時 session 數或使用佇列 |
| **不安全** (❌) | 必須單執行緒使用 | 每個 session 需要獨立實例 |

### 記憶體管理規範

```kotlin
interface MemoryAwareRunner : BaseRunner {
    fun getMemoryUsage(): MemoryUsage
    fun requestMemoryOptimization(): Boolean
    fun setMemoryLimit(limitMB: Int): Boolean
}

data class MemoryUsage(
    val totalAllocatedMB: Int,
    val peakUsageMB: Int,
    val currentUsageMB: Int,
    val nativeHeapMB: Int
)
```

## 📊 效能規範與測試

### 效能基準要求

| 效能指標 | LLM Runner | ASR Runner | TTS Runner | VLM Runner |
|----------|------------|------------|------------|------------|
| **初始化時間** | < 5秒 | < 2秒 | < 3秒 | < 8秒 |
| **推論延遲** | < 500ms/token | < 200ms/chunk | < 2秒/sentence | < 1秒/image |
| **記憶體使用** | < 4GB | < 1GB | < 512MB | < 6GB |
| **CPU 使用率** | < 80% | < 60% | < 40% | < 90% |

### 效能測試範例

```kotlin
class RunnerPerformanceTest {
    @Test
    fun testInitializationTime() {
        val runner = GpuOnnxLLMRunner()
        val startTime = System.currentTimeMillis()
        
        val success = runner.load(testModelConfig)
        val initTime = System.currentTimeMillis() - startTime
        
        assertTrue(success)
        assertTrue("Initialization too slow: ${initTime}ms", 
                  initTime < runner.getMetadata().initializationTimeMs * 1.5)
    }
    
    @Test
    fun testMemoryUsage() {
        val runner = GpuOnnxLLMRunner()
        runner.load(testModelConfig)
        
        val memoryBefore = getUsedMemory()
        runner.run(testRequest)
        val memoryAfter = getUsedMemory()
        
        val memoryIncrease = memoryAfter - memoryBefore
        assertTrue("Memory usage too high: ${memoryIncrease}MB",
                  memoryIncrease < runner.getMetadata().minMemoryMB * 1.2)
    }
}
```

## 🛠 注意事項與最佳實務

### ✅ Runner 實作準則

#### 模型與能力關係
- **單一責任**: 一個 Runner 專注於單一 Capability，避免過度複雜
- **能力隔離**: 如需支援多種能力，確保內部邏輯清晰分離
- **狀態管理**: 明確管理模型載入狀態與 session 生命週期

#### 資源管理
- **記憶體**: 及時釋放不需要的資源，避免記憶體洩漏
- **執行緒**: 正確處理執行緒同步，避免競態條件
- **檔案**: 妥善處理暫存檔案與模型檔案的生命週期

#### 錯誤處理
- **一致性**: 使用統一的錯誤碼與錯誤格式
- **可復原**: 明確標示哪些錯誤可以重試
- **日誌**: 提供足夠的除錯資訊但避免敏感資料

### 🔁 Fallback 實作準則

#### Fallback 行為
- **被動觸發**: Runner 不應自行切換 fallback，由 AI Router 控制
- **狀態保持**: Fallback 切換時保持必要的 session 狀態
- **透明性**: 使用者不應感知到 fallback 的發生

#### 降級策略
- **效能降級**: 從 GPU → CPU → Cloud 的效能降級路徑
- **品質降級**: 從高精度模型降至較小模型
- **功能降級**: 從完整功能降至基本功能

### 📦 模型載入與管理

#### 載入策略
- **懶載入**: 僅在需要時載入模型，節省記憶體
- **預載入**: 對常用模型提供預載入機制
- **共享載入**: 相同模型在不同 Runner 間共享

#### 生命週期管理
- **引用計數**: 追蹤模型的使用狀況
- **自動釋放**: 在適當時機自動釋放未使用的模型
- **版本控制**: 支援模型版本更新與相容性檢查

## 📂 進階配置與擴展

### 自訂 Runner 開發

#### 開發步驟
1. **實作介面**: 繼承 `BaseRunner` 並實作必要方法
2. **定義元資料**: 提供完整的 `RunnerMetadata`
3. **註冊到系統**: 在 `RunnerRegistry` 中註冊
4. **撰寫測試**: 包含單元測試與整合測試
5. **更新文件**: 更新相關文件與配置

#### 範例自訂 Runner

```kotlin
class CustomVoiceRunner : BaseRunner {
    companion object {
        val METADATA = RunnerMetadata(
            name = "CustomVoiceRunner",
            capabilities = listOf(CapabilityType.TTS),
            supportedFormats = listOf("custom_voice"),
            supportedPlatforms = listOf("android_cpu"),
            supportsStreaming = true,
            supportsFallback = true,
            isThreadSafe = true,
            priority = 7,
            minMemoryMB = 256,
            maxConcurrentSessions = 2,
            initializationTimeMs = 1500,
            averageLatencyMs = 800,
            powerConsumption = PowerLevel.MEDIUM,
            hardwareRequirements = listOf("AUDIO_OUTPUT"),
            version = "1.0.0",
            description = "Custom voice synthesis with emotional expression"
        )
    }
    
    override fun getMetadata(): RunnerMetadata = METADATA
    // ... 其他實作
}
```

### 複雜 Runner 文件結構

對於複雜的 Runner，建議在 `07-Implementation/runners/` 中建立專門文件：

```
07-Implementation/runners/
├── gpu-onnx-llm/
│   ├── README.md                 # 概述與快速開始
│   ├── configuration.md          # 配置選項詳解
│   ├── performance-tuning.md     # 效能調優指南
│   ├── troubleshooting.md        # 常見問題解決
│   └── examples.md              # 使用範例
├── sherpa-asr/
│   └── ...
└── ...
```

## 🔗 相關章節

- **Runner 介面**: [Runner 統一介面](./runner-interface.md) - 基礎介面定義
- **能力對應**: [Capability 對應表](./capability-mapping.md) - Runner 與能力的映射關係  
- **模型配置**: [模型配置規範](../03-Models/model-config-specification.md) - Runner 使用的模型配置
- **錯誤處理**: [錯誤碼定義](../05-Error-Handling/error-codes.md) - Runner 相關錯誤碼
- **測試矩陣**: [測試情境](../06-Testing/test-scenarios.md) - Runner 測試規範

---

📍 **返回**: [Interfaces 首頁](./README.md) | **上一篇**: [Capability 對應表](./capability-mapping.md) 