# 🔢 統一錯誤碼定義

## 🎯 目標與範圍

本文件定義 AI Router 所有錯誤碼與錯誤類型，提供一致性錯誤處理、日誌上報與 fallback 機制支援。錯誤碼編碼格式與分類為系統穩定性與可維護性的重要基礎。

## 📌 錯誤碼編碼格式

### 編碼規則

```
格式：E[錯誤類型位碼][具體錯誤碼]
範例：E101 代表推論錯誤中的 "Runtime 執行錯誤"
```

### 錯誤類型分類

| 錯誤類型代碼 | 分類說明              | 預期發生頻率 |
| ------ | ----------------- | -------- |
| E1xx   | Inference 推論流程錯誤  | ~40%     |
| E2xx   | Model 模型載入與管理錯誤   | ~25%     |
| E3xx   | I/O 錯誤（下載、檔案）     | ~20%     |
| E4xx   | Capability 配置錯誤   | ~10%     |
| E5xx   | Dispatcher 執行排程錯誤 | ~5%      |
| E9xx   | 內部錯誤 / 未分類錯誤      | <1%      |

每個錯誤項目包含：**錯誤碼**、**錯誤名稱**、**錯誤描述**、**是否可重試**、**錯誤等級**、**建議行為**、**可能原因**。

## ❗ 完整錯誤碼列表

### E1xx - 推論執行錯誤

| Code | Name | Description | Retryable | Severity | Action | Cause |
|------|------|-------------|-----------|----------|--------|-------|
| E101 | RuntimeExecutionError | 執行 Runtime 時 JNI 回傳失敗 | ❌ | critical | 終止並記錄 log | JNI native crash / invalid output |
| E102 | InferenceTimeout | 推論執行逾時（如 blocking ANR） | ✅ | error | 中止執行並釋放資源 | device busy / blocking call |
| E103 | ModelNotLoaded | 模型未載入就嘗試執行推論 | ✅ | error | 自動載入模型後重試 | model lifecycle management |
| E104 | InsufficientMemory | 推論過程記憶體不足 | ❌ | error | 釋放資源或使用小模型 | device memory pressure |
| E105 | InputValidationFailed | 輸入資料格式驗證失敗 | ❌ | warning | 回傳輸入錯誤提示 | malformed input data |

### E2xx - 模型管理錯誤

| Code | Name | Description | Retryable | Severity | Action | Cause |
|------|------|-------------|-----------|----------|--------|-------|
| E201 | ModelMissing | 所需模型未註冊於 registry | ❌ | error | 回傳錯誤並提示缺模型 | registry 查無此 model ID |
| E202 | ModelLoadFailed | 模型載入失敗 | ✅ | error | 觸發 retry 次數後中止 | memory OOM / file format mismatch |
| E203 | ModelConfigInvalid | config 欄位缺失或錯誤格式 | ❌ | error | log 並略過該模型 | schema validation failed |
| E204 | ModelVersionMismatch | 模型版本與 Runner 不相容 | ❌ | error | 提示升級或換模型 | version compatibility issue |
| E205 | ModelCorrupted | 模型檔案損毀或不完整 | ✅ | error | 刪除並重新下載 | file corruption / incomplete download |

### E3xx - I/O 與下載錯誤

| Code | Name | Description | Retryable | Severity | Action | Cause |
|------|------|-------------|-----------|----------|--------|-------|
| E301 | DownloadInterrupted | 模型下載過程中斷 | ✅ | warning | 可嘗試 retry 或暫停等待網路恢復 | network unavailable |
| E302 | CorruptedModelCache | 下載模型快取檔案毀損 | ✅ | error | 自動刪除並重試下載 | partially written file |
| E303 | InsufficientStorage | 設備儲存空間不足 | ❌ | error | 提示清理空間或使用雲端模型 | device storage full |
| E304 | NetworkTimeout | 網路請求超時 | ✅ | warning | 重試或改用離線模型 | slow network / server overload |
| E305 | FilePermissionDenied | 檔案讀寫權限不足 | ❌ | error | 檢查應用權限設定 | Android permission issue |

### E4xx - 配置與能力錯誤

| Code | Name | Description | Retryable | Severity | Action | Cause |
|------|------|-------------|-----------|----------|--------|-------|
| E401 | CapabilityNotSupported | 呼叫未支援的能力類型 | ❌ | warning | 回傳 Capability 未支援 | mapping 不存在 |
| E402 | RunnerNotFound | 找不到對應的 Runner 實作 | ❌ | error | 檢查 Runner 註冊狀態 | registry configuration |
| E403 | ConfigurationInvalid | 系統配置檔案格式錯誤 | ❌ | critical | 使用預設配置或報錯 | config file corruption |
| E404 | CapabilityMappingError | Capability 與 Runner 對應關係錯誤 | ❌ | error | 檢查映射配置 | mapping configuration issue |

### E5xx - 調度與並發錯誤

| Code | Name | Description | Retryable | Severity | Action | Cause |
|------|------|-------------|-----------|----------|--------|-------|
| E501 | DispatchQueueFull | 排程佇列爆滿無法加入請求 | ✅ | error | 回傳繁忙 / 稍後重試 | queue.size 超標 |
| E502 | ConcurrentRequestConflict | 同一模型重入衝突 | ✅ | warning | 佇列合併或退件 | 同步控制未就緒 |
| E503 | ThreadPoolExhausted | 執行緒池資源耗盡 | ✅ | error | 等待資源釋放後重試 | high concurrency load |
| E504 | ResourceLockTimeout | 資源鎖定超時 | ✅ | warning | 重試或改用其他資源 | resource contention |

### E9xx - 系統內部錯誤

| Code | Name | Description | Retryable | Severity | Action | Cause |
|------|------|-------------|-----------|----------|--------|-------|
| E999 | UnknownInternalError | 未知內部錯誤 | ❌ | critical | 記錄並中止執行 | throwable 未捕捉例外 |
| E901 | SystemInitializationFailed | 系統初始化失敗 | ❌ | critical | 應用重啟或降級模式 | core component failure |
| E902 | ConfigurationCorrupted | 核心配置損毀 | ❌ | critical | 重置為預設配置 | config file corruption |

## 🧱 錯誤資料傳遞格式

### Kotlin 端錯誤封裝

```kotlin
sealed class AIRouterError(
    val code: String,
    val message: String,
    val retryable: Boolean,
    val severity: Severity,
    val cause: Throwable? = null
) {
    enum class Severity { WARNING, ERROR, CRITICAL }
    
    data class InferenceError(
        override val code: String,
        override val message: String,
        override val retryable: Boolean = false,
        override val cause: Throwable? = null
    ) : AIRouterError(code, message, retryable, Severity.ERROR, cause)
    
    data class ModelError(
        override val code: String,
        override val message: String,
        override val retryable: Boolean = true,
        override val cause: Throwable? = null
    ) : AIRouterError(code, message, retryable, Severity.ERROR, cause)
    
    data class SystemError(
        override val code: String,
        override val message: String,
        override val cause: Throwable? = null
    ) : AIRouterError(code, message, false, Severity.CRITICAL, cause)
}
```

### 錯誤建立輔助方法

```kotlin
object ErrorFactory {
    fun runtimeError(message: String, cause: Throwable? = null) = 
        AIRouterError.InferenceError("E101", message, false, cause)
        
    fun modelNotFound(modelId: String) = 
        AIRouterError.ModelError("E201", "Model not found: $modelId", false)
        
    fun networkTimeout(timeout: Long) = 
        AIRouterError.ModelError("E304", "Network timeout after ${timeout}ms", true)
}
```

## 🔌 系統整合建議

### 📊 日誌系統整合

**統一日誌格式**：
```
[AIRouterError][E101] Runtime execution failed: JNI bridge returned null
[AIRouterError][E201] Model not found in registry: llama-7b-mobile
[AIRouterError][E301] Download interrupted: network unavailable
```

**日誌級別對應**：
- **WARNING**: Logcat.w + 選擇性上報
- **ERROR**: Logcat.e + 錯誤統計
- **CRITICAL**: Logcat.e + 立即上報 + Crash 報告

### 🎯 UI 顯示策略

AI Router 僅拋出錯誤物件，不介入顯示，交由 UI 或 domain 層決定呈現方式：

```kotlin
// 在 ViewModel 中處理錯誤
fun handleAIRouterError(error: AIRouterError) {
    when (error.severity) {
        Severity.WARNING -> showToast(error.message)
        Severity.ERROR -> showErrorDialog(error.message, error.retryable)
        Severity.CRITICAL -> navigateToErrorScreen(error.code)
    }
}
```

### 🔄 自動重試與 Fallback

根據 `retryable` 欄位由 Dispatcher 判斷是否自動重試或改用其他 runner：

```kotlin
suspend fun handleErrorWithRetry(
    error: AIRouterError,
    retryAction: suspend () -> Result<Any>
): Result<Any> {
    return if (error.retryable && retryCount < maxRetries) {
        delay(retryDelayMs)
        retryAction()
    } else {
        Result.failure(error)
    }
}
```

## 🌐 國際化與插件擴展

### 🔠 錯誤訊息國際化

雖 AI Router 本身不負責 UI 呈現，但為支援多語系開發，上層可根據錯誤碼查詢對應語系對照表：

```json
{
  "E101": { 
    "en": "Runtime execution failed", 
    "zh": "推論執行失敗",
    "ja": "実行エラーが発生しました"
  },
  "E201": { 
    "en": "Model not found", 
    "zh": "找不到模型",
    "ja": "モデルが見つかりません"
  }
}
```

### 🧩 Fallback Handler Plugin

AI Router 支援註冊式 Plugin 機制，允許各能力自定 fallback 行為：

```kotlin
interface AIRouterFallbackHandler {
    fun onError(error: AIRouterError): Boolean
    // 回傳 true 表示已處理錯誤 (例如改用 CPU fallback)
}

// 在 Dispatcher 中註冊對應 Capability 的 fallback plugin
object FallbackRegistry {
    fun registerFallback(capability: String, handler: AIRouterFallbackHandler) {
        fallbackHandlers[capability] = handler
    }
}

// 使用範例
FallbackRegistry.registerFallback("LLM", CpuFallbackHandler())
FallbackRegistry.registerFallback("TTS", RemoteFallbackHandler())
```

## 📊 錯誤統計與監控

### 錯誤頻率統計

建議統計錯誤碼發生頻率以優化常見異常情境：

```kotlin
object ErrorAnalytics {
    private val errorCounts = mutableMapOf<String, Int>()
    
    fun reportError(error: AIRouterError) {
        errorCounts[error.code] = errorCounts.getOrDefault(error.code, 0) + 1
        
        // 上報到分析系統
        when (error.severity) {
            Severity.CRITICAL -> crashlytics.recordException(error.cause)
            Severity.ERROR -> analytics.logEvent("ai_router_error", mapOf("code" to error.code))
            Severity.WARNING -> analytics.logEvent("ai_router_warning", mapOf("code" to error.code))
        }
    }
    
    fun getTopErrors(limit: Int = 10): List<Pair<String, Int>> {
        return errorCounts.toList().sortedByDescending { it.second }.take(limit)
    }
}
```

## 🔗 相關章節

- **降級策略**: [Fallback 策略](./fallback-strategies.md) - 錯誤處理流程與自動恢復
- **恢復機制**: [恢復機制](./recovery-mechanisms.md) - 系統韌性與自我修復
- **介面規範**: [Runner 介面](../02-Interfaces/runner-interface.md) - 錯誤處理介面定義
- **測試策略**: [測試矩陣](../06-Testing/test-matrix.md) - 錯誤場景測試規劃

---

📍 **返回**: [Error Handling 首頁](./README.md) | **下一篇**: [Fallback 策略](./fallback-strategies.md) 