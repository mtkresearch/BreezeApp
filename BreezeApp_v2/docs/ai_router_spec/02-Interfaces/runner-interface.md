# 🔧 Runner 統一介面定義

## 🎯 目標與範圍

本文件定義 AI Router 對所有 Runner 實作統一的核心介面與行為契約，確保能力模組能以一致方式調用各種推論引擎。所有 Runner 必須遵循此介面規範以保證系統的一致性和可擴展性。

## 🔧 核心介面定義

### BaseRunner 介面 (Kotlin)

```kotlin
interface BaseRunner {
    /** 初始化模型與資源 */
    fun load(config: ModelConfig): Boolean

    /** 執行推論，stream 為 true 時可回傳分段結果 */
    fun run(input: InferenceRequest, stream: Boolean = false): InferenceResult

    /** 卸載資源 */
    fun unload(): Unit

    /** 回傳支援的能力清單（通常為單一項） */
    fun getCapabilities(): List<CapabilityType>
}
```

### 能力類型定義

```kotlin
enum class CapabilityType {
    LLM,        // 大語言模型
    VLM,        // 視覺語言模型  
    ASR,        // 語音識別
    TTS,        // 語音合成
    GUARDIAN    // 內容安全檢測
}
```

## 📦 資料類型定義

### 推論請求格式

```kotlin
data class InferenceRequest(
    val sessionId: String,                    // 會話唯一識別碼
    val inputs: Map<String, Any>,            // 輸入資料 (文字、音訊、圖片等)
    val params: Map<String, Any> = emptyMap(), // 推論參數
    val timestamp: Long = System.currentTimeMillis()
)
```

**輸入資料範例**：
- **LLM**: `inputs["text"] = "使用者問題"`
- **ASR**: `inputs["audio"] = FloatArray(...)`  
- **TTS**: `inputs["text"] = "要合成的文字"`
- **VLM**: `inputs["image"] = Bitmap, inputs["text"] = "問題"`

### 推論結果格式

```kotlin
data class InferenceResult(
    val outputs: Map<String, Any>,           // 輸出結果
    val metadata: Map<String, Any> = emptyMap(), // 中繼資料 (如置信度、延遲等)
    val error: RunnerError? = null,          // 錯誤資訊
    val partial: Boolean = false             // 是否為部分結果 (streaming)
)
```

**輸出資料範例**：
- **LLM**: `outputs["text"] = "AI 回應"`
- **ASR**: `outputs["text"] = "識別出的文字"`
- **TTS**: `outputs["audio"] = ByteArray(...)`
- **VLM**: `outputs["text"] = "圖片描述"`

### 錯誤處理格式

```kotlin
data class RunnerError(
    val code: String,                        // 錯誤碼 (如 E101, E201)
    val message: String,                     // 錯誤描述
    val recoverable: Boolean = false,        // 是否可重試
    val cause: Throwable? = null            // 原始異常
)
```

## 🔁 Streaming 推論擴展

對於支援 streaming 的 Runner，可實作以下擴展介面：

```kotlin
interface StreamingRunner : BaseRunner {
    fun runStream(
        input: InferenceRequest,
        onResult: (InferenceResult) -> Unit,    // 部分結果回調
        onComplete: () -> Unit,                 // 完成回調
        onError: (Throwable) -> Unit           // 錯誤回調
    )
}
```

### 或使用 Kotlin Flow

```kotlin
interface FlowStreamingRunner : BaseRunner {
    fun runAsFlow(input: InferenceRequest): Flow<InferenceResult>
}
```

## 🏗️ 實作範例

### SherpaASRRunner 實作示例

```kotlin
class SherpaASRRunner : BaseRunner {

    private var sherpaEngine: SherpaASREngine? = null

    override fun load(config: ModelConfig): Boolean {
        return try {
            sherpaEngine = SherpaASREngine().apply {
                init(config.files["model"] ?: error("Model file not specified"))
            }
            true
        } catch (e: Exception) {
            Log.e("SherpaASRRunner", "Failed to load model", e)
            false
        }
    }

    override fun run(input: InferenceRequest, stream: Boolean): InferenceResult {
        return try {
            val audioData = input.inputs["audio"] as? FloatArray
                ?: return InferenceResult(
                    outputs = emptyMap(),
                    error = RunnerError("E401", "Audio input required", false)
                )

            val result = sherpaEngine?.infer(audioData)
            InferenceResult(
                outputs = mapOf("text" to (result ?: "")),
                metadata = mapOf(
                    "confidence" to 0.95,
                    "processing_time_ms" to 150
                )
            )
        } catch (e: Exception) {
            InferenceResult(
                outputs = emptyMap(),
                error = RunnerError("E101", e.message ?: "Runtime error", true, e)
            )
        }
    }

    override fun unload() {
        sherpaEngine?.release()
        sherpaEngine = null
    }

    override fun getCapabilities(): List<CapabilityType> = listOf(CapabilityType.ASR)
}
```

## 📋 介面實作規範

### ✅ 必須遵循的規則

1. **資源管理**
   - `load()` 成功後必須能執行 `run()`
   - `unload()` 後必須釋放所有資源
   - 支援多次 `load()/unload()` 循環

2. **錯誤處理**
   - 所有異常必須轉換為 `RunnerError`
   - 不得拋出未捕捉的例外到上層
   - 錯誤碼必須遵循 [錯誤碼規範](../05-Error-Handling/error-codes.md)

3. **執行緒安全**
   - 如果不是 thread-safe，必須在 RunnerSpec 中標明
   - 建議使用 `@ThreadSafe` 或 `@NotThreadSafe` 註解

4. **效能要求**
   - `run()` 方法不應阻塞超過配置的 timeout 時間
   - 大型資源載入應在 `load()` 階段完成

### ⚠️ 限制與注意事項

- **禁止直接 JNI 調用**: 所有 native 操作必須經由 `RuntimeEngine`
- **資料格式驗證**: 輸入資料格式錯誤應回傳 E401 錯誤碼  
- **記憶體管理**: 避免記憶體洩漏，特別是 native 資源
- **日誌記錄**: 重要操作應記錄適當的 log 以便除錯

## 🔗 相關章節

- **錯誤處理**: [錯誤碼定義](../05-Error-Handling/error-codes.md) - 統一錯誤碼規範
- **能力對應**: [Capability 對應表](./capability-mapping.md) - Runner 與能力的映射關係
- **詳細規格**: [Runner 規格表](./runner-specifications.md) - 各 Runner 的詳細規格
- **模型配置**: [模型配置規範](../03-Models/model-config-specification.md) - ModelConfig 格式定義

## 💡 最佳實務建議

### 🎯 效能優化
- 預先載入常用資源，延遲載入次要資源
- 使用物件池重用昂貴的物件
- 合理設置 timeout 避免無限等待

### 🛡️ 穩定性保證
- 實作健全的輸入驗證
- 提供有意義的錯誤訊息
- 支援 graceful degradation

### 🔧 可維護性
- 清楚的程式碼註解與文件
- 統一的命名慣例
- 適當的單元測試覆蓋率

---

📍 **返回**: [Interfaces 首頁](./README.md) | **下一篇**: [能力對應表](./capability-mapping.md) 