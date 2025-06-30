# 📋 模型配置規範

## 🎯 目標與範圍

本文件為 AI Router 所使用的模型配置格式說明，涵蓋模型所需檔案、載入條件、推論選項與多平台支援等欄位。適用於 LLM / VLM / TTS / ASR 等異質模型，支援本地與 API 模式，為 ModelManager 提供統一的配置格式標準。

## 🏗️ 最小可用配置範例

### 基本模型配置 (JSON)

```json
{
  "name": "breeze2-3b-npu",
  "format": "pte",
  "runner": "llm_runner",
  "files": {
    "weights": "llama.pte",
    "tokenizer": "tokenizer.bin"
  },
  "scope": "per_capability",
  "min_ram_mb": 6000
}
```

### 進階配置範例

```json
{
  "name": "sherpa-asr-zh",
  "format": "onnx",
  "runner": "sherpa_asr_runner",
  "files": {
    "encoder": "encoder.onnx",
    "decoder": "decoder.onnx",
    "joiner": "joiner.onnx",
    "tokens": "tokens.txt"
  },
  "scope": "per_session",
  "min_ram_mb": 4000,
  "priority": 10,
  "sha256": {
    "encoder": "a1b2c3d4e5f6...",
    "decoder": "f6e5d4c3b2a1..."
  },
  "path_type": "remote",
  "fallback_to": "whisper-api",
  "tokenizer_format": "sentencepiece",
  "inference_mode": ["streaming", "chunked"],
  "metadata": {
    "version": "1.2.0",
    "language": "zh-TW",
    "license": "MIT"
  }
}
```

## 📑 完整欄位說明

### 🔧 核心必填欄位

| 欄位名 | 型別 | 必填 | 說明 | 範例 |
|-------|------|------|------|------|
| `name` | string | ✅ | 模型唯一識別名稱，作為載入與記憶體快取鍵值 | `"breeze2-3b-npu"` |
| `format` | string | ✅ | 模型格式 | `"pte"`, `"onnx"`, `"tflite"`, `"api"` |
| `runner` | string | ✅ | 指定使用的 Runner 名稱（必須在 Registry 中註冊） | `"llm_runner"`, `"sherpa_asr_runner"` |
| `files` | object | ✅ | 檔案清單，不限制欄位名稱，由對應 Runner 解讀 | `{"weights": "model.pte"}` |

### 🎚️ 資源與性能配置

| 欄位名 | 型別 | 預設值 | 說明 | 範例 |
|-------|------|--------|------|------|
| `min_ram_mb` | number | `null` | 執行此模型所需最小 RAM（用於候選過濾） | `6000` |
| `max_ram_mb` | number | `null` | 建議最大 RAM 使用量 | `8000` |
| `priority` | number | `5` | 預設模型排序優先級（越高越優先） | `10` |
| `timeout_ms` | number | `30000` | 模型載入超時時間（毫秒） | `60000` |

### 🔒 安全與驗證

| 欄位名 | 型別 | 預設值 | 說明 | 範例 |
|-------|------|--------|------|------|
| `sha256` | string \| object | `null` | 檔案 hash 值，用於下載驗證 | `"a1b2c3..."` 或 `{"weights": "a1b2..."}` |
| `signature` | string | `null` | 模型數位簽章（如果需要） | `"signature_string"` |
| `checksum_url` | string | `null` | 檢查碼檔案下載位置 | `"https://example.com/model.sha256"` |

### 📁 檔案來源配置

| 欄位名 | 型別 | 預設值 | 說明 | 範例 |
|-------|------|--------|------|------|
| `path_type` | string | `"local"` | 檔案位置類型 | `"local"`, `"remote"`, `"asset"` |
| `base_url` | string | `null` | 遠端檔案的基礎 URL | `"https://models.example.com/"` |
| `download_mirrors` | array | `[]` | 備用下載鏡像站列表 | `["https://mirror1.com/", "https://mirror2.com/"]` |

### 🔄 模型共用策略

| 欄位名 | 型別 | 預設值 | 說明 | 可選值 |
|-------|------|--------|------|--------|
| `scope` | string | `"per_capability"` | 模型實例範圍，共用策略 | `"singleton"`, `"per_capability"`, `"per_session"` |

**Scope 策略詳細說明**：

| `scope` 值 | 行為描述 | 使用場景 |
|-----------|----------|----------|
| `singleton` | 所有能力與 session 共用同一個模型實例 | Thread-safe 且資源共用的大模型 |
| `per_capability` | 每個能力（LLM / ASR / TTS）保有獨立模型實例 | 一般情況，平衡性能與隔離性 |
| `per_session` | 每次推論 session 獨立建構模型 | 非 thread-safe 或需要狀態隔離的模型 |

### 🔤 Tokenizer 配置

| 欄位名 | 型別 | 預設值 | 說明 | 範例 |
|-------|------|--------|------|------|
| `tokenizer_format` | string | `null` | 指出 tokenizer 的格式 | `"bin"`, `"json"`, `"spm"`, `"tiktoken"` |
| `vocab_size` | number | `null` | 詞彙表大小 | `32000` |
| `special_tokens` | object | `{}` | 特殊 token 定義 | `{"bos": "<s>", "eos": "</s>"}` |

### 🚀 推論配置

| 欄位名 | 型別 | 預設值 | 說明 | 範例 |
|-------|------|--------|------|------|
| `inference_mode` | array | `["full"]` | 支援的推論模式 | `["streaming", "full", "chunked"]` |
| `batch_size` | number | `1` | 預設 batch 大小 | `4` |
| `max_sequence_length` | number | `null` | 最大序列長度 | `2048` |

### 🌐 API 模型配置

| 欄位名 | 型別 | 預設值 | 說明 | 範例 |
|-------|------|--------|------|------|
| `endpoint` | string | `null` | 若為 API 模型，指定其推論端點 URL | `"https://api.openai.com/v1/completions"` |
| `headers` | object | `{}` | API 模型的 HTTP headers 設定 | `{"Authorization": "Bearer ${API_KEY}"}` |
| `api_key_env` | string | `null` | 環境變數中的 API Key 名稱 | `"OPENAI_API_KEY"` |
| `rate_limit` | object | `null` | API 速率限制配置 | `{"requests_per_minute": 60}` |

### 🔄 容錯配置

| 欄位名 | 型別 | 預設值 | 說明 | 範例 |
|-------|------|--------|------|------|
| `fallback_to` | string | `null` | 若此模型無法載入時可切換的備援模型名稱 | `"whisper-api"` |
| `retry_count` | number | `3` | 載入失敗時的重試次數 | `5` |
| `retry_delay_ms` | number | `1000` | 重試間隔時間（毫秒） | `2000` |

### 📊 中繼資料

| 欄位名 | 型別 | 預設值 | 說明 | 範例 |
|-------|------|--------|------|------|
| `metadata` | object | `{}` | 其他中繼資料 | `{"version": "1.0", "language": "zh"}` |
| `description` | string | `null` | 模型描述 | `"Optimized Llama 2 for mobile devices"` |
| `tags` | array | `[]` | 模型標籤 | `["mobile", "chinese", "fast"]` |

## 🧩 與實作模組對應

| 模組 | 職責 | 使用的配置欄位 |
|------|------|---------------|
| `ModelManager` | 解析 config 並管理模型生命週期與記憶體共用策略 | `scope`, `priority`, `fallback_to` |
| `ModelLoader` | 根據檔案資訊與格式載入模型檔案與 tokenizer | `files`, `format`, `tokenizer_format` |
| `ModelDownloader` | 負責下載檔案與驗證 integrity | `sha256`, `base_url`, `download_mirrors` |
| `ModelSelector` | 根據條件選擇最適合當前環境與任務的模型 | `min_ram_mb`, `priority`, `tags` |
| `RunnerRegistry` | 提供 runner 規格、thread safe 資訊等元資料 | `runner`, `inference_mode` |
| `UsageTracker` | 記錄模型使用引用次數，支援引用計數與自動釋放 | `name`, `metadata` |

## 🔧 Kotlin 資料類別定義

### 主要配置類別

```kotlin
data class ModelConfig(
    // 核心必填欄位
    val name: String,
    val format: String,
    val runner: String,
    val files: Map<String, String>,
    
    // 資源與性能
    val scope: String = "per_capability",
    val min_ram_mb: Int? = null,
    val max_ram_mb: Int? = null,
    val priority: Int = 5,
    val timeout_ms: Long = 30000,
    
    // 安全與驗證
    val sha256: ModelChecksums? = null,
    val signature: String? = null,
    val checksum_url: String? = null,
    
    // 檔案來源
    val path_type: String = "local",
    val base_url: String? = null,
    val download_mirrors: List<String> = emptyList(),
    
    // Tokenizer 配置
    val tokenizer_format: String? = null,
    val vocab_size: Int? = null,
    val special_tokens: Map<String, String> = emptyMap(),
    
    // 推論配置
    val inference_mode: List<String> = listOf("full"),
    val batch_size: Int = 1,
    val max_sequence_length: Int? = null,
    
    // API 配置
    val endpoint: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val api_key_env: String? = null,
    val rate_limit: RateLimitConfig? = null,
    
    // 容錯配置
    val fallback_to: String? = null,
    val retry_count: Int = 3,
    val retry_delay_ms: Long = 1000,
    
    // 中繼資料
    val metadata: Map<String, Any> = emptyMap(),
    val description: String? = null,
    val tags: List<String> = emptyList()
)
```

### 輔助資料類別

```kotlin
sealed class ModelChecksums {
    data class SingleChecksum(val value: String) : ModelChecksums()
    data class FileChecksums(val checksums: Map<String, String>) : ModelChecksums()
}

data class RateLimitConfig(
    val requests_per_minute: Int = 60,
    val requests_per_day: Int? = null,
    val burst_size: Int = 10
)

data class ModelScopeConfig(
    val scope: ModelScope,
    val max_instances: Int = 1,
    val idle_timeout_ms: Long = 300000 // 5 minutes
) {
    enum class ModelScope {
        SINGLETON, PER_CAPABILITY, PER_SESSION
    }
}
```

## 📚 JSON Schema 定義

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "AI Router Model Configuration",
  "type": "object",
  "required": ["name", "format", "runner", "files"],
  "properties": {
    "name": {
      "type": "string",
      "pattern": "^[a-zA-Z0-9_-]+$",
      "description": "Model unique identifier"
    },
    "format": {
      "type": "string",
      "enum": ["pte", "onnx", "tflite", "api", "custom"]
    },
    "runner": {
      "type": "string",
      "description": "Runner class name registered in RunnerRegistry"
    },
    "files": {
      "type": "object",
      "additionalProperties": {"type": "string"},
      "minProperties": 1
    },
    "scope": {
      "type": "string",
      "enum": ["singleton", "per_capability", "per_session"],
      "default": "per_capability"
    },
    "min_ram_mb": {
      "type": "integer",
      "minimum": 0
    },
    "priority": {
      "type": "integer",
      "minimum": 0,
      "maximum": 100,
      "default": 5
    },
    "sha256": {
      "oneOf": [
        {"type": "string"},
        {
          "type": "object",
          "additionalProperties": {"type": "string"}
        }
      ]
    },
    "path_type": {
      "type": "string",
      "enum": ["local", "remote", "asset"],
      "default": "local"
    },
    "inference_mode": {
      "type": "array",
      "items": {
        "type": "string",
        "enum": ["streaming", "full", "chunked", "batch"]
      },
      "default": ["full"]
    },
    "endpoint": {
      "type": "string",
      "format": "uri"
    },
    "headers": {
      "type": "object",
      "additionalProperties": {"type": "string"}
    }
  }
}
```

## ✅ 配置驗證工具

### 配置驗證類別

```kotlin
object ModelConfigValidator {
    fun validate(config: ModelConfig): ValidationResult {
        val errors = mutableListOf<String>()
        
        // 基本驗證
        if (config.name.isBlank()) {
            errors.add("Model name cannot be blank")
        }
        
        if (!config.name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            errors.add("Model name contains invalid characters")
        }
        
        // 資源驗證
        if (config.min_ram_mb != null && config.min_ram_mb <= 0) {
            errors.add("min_ram_mb must be positive")
        }
        
        // 檔案驗證
        if (config.files.isEmpty()) {
            errors.add("At least one file must be specified")
        }
        
        // API 配置驗證
        if (config.format == "api" && config.endpoint.isNullOrBlank()) {
            errors.add("endpoint is required for API models")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(errors)
        }
    }
}

sealed class ValidationResult {
    object Success : ValidationResult()
    data class Failure(val errors: List<String>) : ValidationResult()
}
```

## 🔗 相關章節

- **模型管理**: [模型生命週期](./model-lifecycle.md) - 模型載入與卸載流程
- **共用策略**: [模型共用策略](./model-sharing.md) - Scope 詳細說明與最佳實務
- **下載機制**: [模型下載](./model-download.md) - 遠端模型下載與快取策略
- **選擇邏輯**: [模型選擇器](./model-selector.md) - 自動選擇最佳模型的邏輯
- **Runner 介面**: [Runner 規範](../02-Interfaces/runner-interface.md) - Runner 與配置的整合

## 💡 最佳實務建議

### 🎯 性能優化
- **合理設置記憶體需求**: 根據實際測試設定 `min_ram_mb`
- **優先級管理**: 為常用模型設置較高的 `priority`
- **適當的 scope**: 根據模型特性選擇合適的共用策略

### 🛡️ 安全性
- **檔案完整性**: 為重要模型設置 `sha256` 驗證
- **API 安全**: 使用環境變數管理 API Key
- **存取控制**: 確保模型檔案的適當權限設定

### 🔧 可維護性
- **清晰的命名**: 使用有意義的模型名稱
- **完整的中繼資料**: 提供足夠的 `description` 和 `tags`
- **版本管理**: 在 `metadata` 中記錄版本資訊

---

📍 **返回**: [Models 首頁](./README.md) | **下一篇**: [模型生命週期](./model-lifecycle.md) 