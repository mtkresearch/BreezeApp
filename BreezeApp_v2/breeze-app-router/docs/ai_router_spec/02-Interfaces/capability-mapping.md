# 🗺️ Capability 與 Runner 對應表

## 🎯 目標與範圍

本文件列出 AI Router 中每個 Capability 所對應的 Runner、支援的推論後端、模型格式與是否支援 fallback，作為實作與測試的核心依據。定義了能力與實作之間的映射關係，確保系統的可擴展性和一致性。

## 📋 對應表說明

### 欄位定義

| 欄位 | 說明 | 範例值 |
|------|------|--------|
| **Capability** | 功能類型，如 LLM、ASR、TTS 等 | `LLM`, `ASR`, `TTS` |
| **Runner** | 實際處理該能力的類別名稱 | `GpuOnnxLLMRunner` |
| **Model Format** | 支援的模型格式 | `ONNX`, `PTE`, `TFLite` |
| **Backend** | 支援的推論後端 | `CPU`, `GPU`, `NPU`, `Cloud` |
| **Fallback 支援** | 是否提供替代方案 | ✅ / ❌ |
| **Thread Safe** | 是否支援多執行緒安全 | ✅ / ⚠️ / ❌ |
| **Streaming** | 是否支援串流處理 | ✅ / ❌ |
| **Notes** | 補充說明與限制 | 特殊需求或初始化條件 |

## 🔗 完整對應矩陣

### LLM (大語言模型) 能力

| Runner Class | Model Format | Backend | Thread Safe | Streaming | Fallback | Priority | Notes |
|--------------|--------------|---------|-------------|-----------|----------|----------|-------|
| `GpuOnnxLLMRunner` | ONNX | GPU / CPU | ✅ | ✅ | ✅ | 🥇 High | 若 GPU 不支援會降階至 CPU 模型 |
| `CpuExecutorchLLMRunner` | PTE | CPU | ✅ | ✅ | ✅ | 🥈 Medium | 支援 PyTorch Executorch 格式 |
| `CpuLlamaRunner` | PTE | CPU | ✅ | ✅ | ❌ | 🥉 Low | 預設最低支援機制，無 fallback |
| `QNNLLMRunner` | QNN | Qualcomm NPU | ✅ | ❌ | ❌ | 🥇 High | 限 Qualcomm 平台，高效能 |
| `ApiLLMRunner` | API | Cloud | ✅ | ✅ | ✅ | 🔄 Fallback | 需要網路連線，作為 fallback |

### ASR (語音識別) 能力

| Runner Class | Model Format | Backend | Thread Safe | Streaming | Fallback | Priority | Notes |
|--------------|--------------|---------|-------------|-----------|----------|----------|-------|
| `SherpaASRRunner` | ONNX | CPU / NPU | ⚠️ | ✅ | ✅ | 🥇 High | 需限制 session 共用，支援即時識別 |
| `WhisperOnnxRunner` | ONNX | CPU / GPU | ✅ | ❌ | ✅ | 🥈 Medium | OpenAI Whisper ONNX 版本 |
| `RemoteWhisperRunner` | API | Cloud | ✅ | ✅ | ✅ | 🔄 Fallback | 若 Sherpa crash 則切至 Whisper API |
| `AndroidSpeechRunner` | Built-in | Android 系統 | ✅ | ✅ | ❌ | 🥉 Low | 系統內建識別，品質較低 |

### TTS (語音合成) 能力

| Runner Class | Model Format | Backend | Thread Safe | Streaming | Fallback | Priority | Notes |
|--------------|--------------|---------|-------------|-----------|----------|----------|-------|
| `SherpaTTSRunner` | ONNX | CPU / NPU | ✅ | ❌ | ✅ | 🥇 High | 模型初始化耗時較長 |
| `AndroidTTSRunner` | Built-in API | Android 系統 | ✅ | ✅ | ❌ | 🔄 Fallback | 僅作為 fallback 使用 |
| `MtkTTSRunner` | DLA | MTK NPU | ✅ | ❌ | ✅ | 🥈 Medium | 限 MTK 平台專用 |
| `RemoteTTSRunner` | API | Cloud | ✅ | ✅ | ✅ | 🔄 Fallback | 雲端合成，需網路 |

### VLM (視覺語言模型) 能力

| Runner Class | Model Format | Backend | Thread Safe | Streaming | Fallback | Priority | Notes |
|--------------|--------------|---------|-------------|-----------|----------|----------|-------|
| `GpuVLMRunner` | ONNX | GPU | ✅ | ✅ | ✅ | 🥇 High | 支援圖片與文字輸入 |
| `CpuVLMRunner` | PTE | CPU | ✅ | ❌ | ✅ | 🥈 Medium | CPU 版本，處理較慢 |
| `ApiVisionRunner` | API | Cloud | ✅ | ✅ | ✅ | 🔄 Fallback | GPT-4V 或類似服務 |

### Guardian (內容安全檢測) 能力

| Runner Class | Model Format | Backend | Thread Safe | Streaming | Fallback | Priority | Notes |
|--------------|--------------|---------|-------------|-----------|----------|----------|-------|
| `LocalGuardianRunner` | ONNX | CPU | ✅ | ❌ | ✅ | 🥇 High | 本地內容安全檢測 |
| `CloudGuardianRunner` | API | Cloud | ✅ | ❌ | ❌ | 🔄 Fallback | 雲端檢測服務 |

## 🎯 Fallback 策略配置

### 推薦的 Fallback 鏈

```kotlin
// LLM Fallback 策略
val llmFallbackChain = listOf(
    "GpuOnnxLLMRunner",      // 首選：GPU 加速
    "CpuExecutorchLLMRunner", // 備選：CPU Executorch
    "ApiLLMRunner"           // 最終：雲端 API
)

// ASR Fallback 策略  
val asrFallbackChain = listOf(
    "SherpaASRRunner",       // 首選：本地 Sherpa
    "WhisperOnnxRunner",     // 備選：本地 Whisper
    "RemoteWhisperRunner"    // 最終：雲端 Whisper
)

// TTS Fallback 策略
val ttsFallbackChain = listOf(
    "SherpaTTSRunner",       // 首選：高品質合成
    "AndroidTTSRunner"       // 備選：系統內建
)
```

### Fallback 觸發條件

| 條件 | 說明 | 範例 |
|------|------|------|
| **模型載入失敗** | 模型檔案損毀或記憶體不足 | E202, E204 |
| **Runtime 錯誤** | JNI 層崩潰或執行異常 | E101, E103 |
| **資源不足** | 設備記憶體或計算能力不足 | E104, E501 |
| **網路問題** | 雲端服務無法連接 | E301, E304 |
| **相容性問題** | 硬體不支援特定後端 | E402, E404 |

## 🔧 實作整合指南

### CapabilityRouter 配置

```kotlin
class CapabilityRouter {
    private val capabilityMappings = mapOf(
        CapabilityType.LLM to listOf(
            RunnerSpec("GpuOnnxLLMRunner", priority = 10, fallback = true),
            RunnerSpec("CpuExecutorchLLMRunner", priority = 8, fallback = true),
            RunnerSpec("ApiLLMRunner", priority = 5, fallback = true)
        ),
        CapabilityType.ASR to listOf(
            RunnerSpec("SherpaASRRunner", priority = 10, fallback = true),
            RunnerSpec("RemoteWhisperRunner", priority = 7, fallback = true)
        ),
        CapabilityType.TTS to listOf(
            RunnerSpec("SherpaTTSRunner", priority = 10, fallback = true),
            RunnerSpec("AndroidTTSRunner", priority = 3, fallback = false)
        )
    )
    
    fun getRunnerFor(capability: CapabilityType): List<RunnerSpec> {
        return capabilityMappings[capability] ?: emptyList()
    }
}
```

### Runner 註冊機制

```kotlin
object RunnerRegistry {
    fun registerAll() {
        // LLM Runners
        register("GpuOnnxLLMRunner") { GpuOnnxLLMRunner() }
        register("CpuExecutorchLLMRunner") { CpuExecutorchLLMRunner() }
        register("ApiLLMRunner") { ApiLLMRunner() }
        
        // ASR Runners
        register("SherpaASRRunner") { SherpaASRRunner() }
        register("RemoteWhisperRunner") { RemoteWhisperRunner() }
        
        // TTS Runners
        register("SherpaTTSRunner") { SherpaTTSRunner() }
        register("AndroidTTSRunner") { AndroidTTSRunner() }
    }
    
    private fun register(name: String, factory: () -> BaseRunner) {
        runnerFactories[name] = factory
    }
}
```

## 🔍 Runner 選擇邏輯

### 自動選擇演算法

```kotlin
class RunnerSelector {
    fun selectOptimalRunner(
        capability: CapabilityType,
        deviceInfo: DeviceInfo,
        requirements: InferenceRequirements
    ): String? {
        val candidates = capabilityRouter.getRunnerFor(capability)
            .filter { it.isCompatible(deviceInfo) }
            .filter { it.meetsRequirements(requirements) }
            .sortedByDescending { it.priority }
            
        return candidates.firstOrNull()?.name
    }
}

data class InferenceRequirements(
    val needsStreaming: Boolean = false,
    val maxLatencyMs: Long = 5000,
    val requiresOffline: Boolean = false,
    val maxMemoryMB: Int = Int.MAX_VALUE
)
```

### 相容性檢查

```kotlin
fun RunnerSpec.isCompatible(deviceInfo: DeviceInfo): Boolean {
    return when (this.name) {
        "GpuOnnxLLMRunner" -> deviceInfo.hasGPU && deviceInfo.gpuMemoryMB >= 2048
        "QNNLLMRunner" -> deviceInfo.isQualcommDevice && deviceInfo.hasNPU
        "MtkTTSRunner" -> deviceInfo.isMtkDevice && deviceInfo.hasNPU
        "ApiLLMRunner" -> deviceInfo.hasNetworkConnection
        else -> true // CPU-based runners generally compatible
    }
}
```

## 📊 效能基準與建議

### 延遲效能參考

| Runner | 平均延遲 | 記憶體使用 | 功耗 | 適用場景 |
|--------|----------|------------|------|----------|
| `GpuOnnxLLMRunner` | 50-200ms | 3-6GB | 高 | 對話應用、即時回應 |
| `CpuExecutorchLLMRunner` | 200-800ms | 2-4GB | 中 | 一般文字處理 |
| `SherpaASRRunner` | 100-300ms | 500MB-1GB | 低 | 即時語音識別 |
| `SherpaTTSRunner` | 500-1500ms | 200-500MB | 低 | 高品質語音合成 |
| `ApiLLMRunner` | 1000-3000ms | <100MB | 極低 | 網路穩定環境 |

### 使用建議

#### 🎯 效能優先
- 優先使用 GPU/NPU 加速的 Runner
- 預載常用模型以減少初始化時間
- 使用 `singleton` 或 `per_capability` scope

#### 🔋 省電優先  
- 優先使用 CPU Runner
- 設定較長的模型卸載 timeout
- 避免頻繁的 Runner 切換

#### 📱 記憶體受限
- 使用較小的模型變體
- 設定 `per_session` scope 避免同時載入多個模型
- 啟用積極的記憶體回收策略

## 🔗 相關章節

- **Runner 介面**: [Runner 統一介面](./runner-interface.md) - 實作規範與介面定義
- **Runner 規格**: [Runner 詳細規格](./runner-specifications.md) - 各 Runner 的詳細說明
- **錯誤處理**: [錯誤碼定義](../05-Error-Handling/error-codes.md) - Fallback 相關錯誤碼
- **模型配置**: [模型配置規範](../03-Models/model-config-specification.md) - 模型與 Runner 的配置關係

## 💡 最佳實務建議

### 🎯 開發階段
- **測試覆蓋**: 為每個 Runner 建立單元測試
- **效能測試**: 在目標設備上進行實際效能測試
- **相容性驗證**: 在不同硬體配置上驗證 Runner 行為

### 🔧 生產部署
- **監控設置**: 追蹤各 Runner 的使用頻率與錯誤率
- **動態調整**: 根據使用者回饋調整 Fallback 策略
- **版本管理**: 保持 Runner 版本與模型版本的同步

### 📈 擴展策略
- **新 Runner 整合**: 遵循統一的註冊與配置機制
- **能力擴展**: 為新的 AI 能力定義清晰的介面契約
- **向後相容**: 確保新版本不破壞現有的 Runner 配置

---

📍 **返回**: [Interfaces 首頁](./README.md) | **下一篇**: [Runner 詳細規格](./runner-specifications.md) 