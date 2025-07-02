# 🧪 AI Router 測試情境矩陣

## 🎯 測試策略概覽

本文件定義 BreezeApp AI Router 模組的完整測試情境，涵蓋功能性測試、效能測試、壓力測試與相容性測試。確保系統在各種設備配置、網路狀況與錯誤情境下都能正常運作。

### 測試目標

- **✅ 功能完整性**: 驗證所有 AI 能力的正確性與可靠性
- **⚡ 效能基準**: 確保推論時間與資源使用符合預期
- **🔧 錯誤處理**: 驗證 Fallback 機制與錯誤恢復能力
- **📱 設備相容**: 確保在不同硬體配置下的穩定性
- **🔄 併發安全**: 驗證多執行緒與多會話的安全性

## 📊 測試情境分類

### 功能性測試 (Functional Testing)

#### A. 基本推論功能測試

| 測試案例 | 輸入條件 | 預期結果 | 測試重點 |
|----------|----------|----------|----------|
| **LLM-F001** | 簡單文字問答 | 合理回應 | 基本 LLM 推論 |
| **LLM-F002** | 多輪對話 | 上下文連貫 | 對話狀態管理 |
| **LLM-F003** | 長文本摘要 | 正確摘要 | 長序列處理 |
| **ASR-F001** | 清晰語音檔案 | 正確文字轉錄 | 基本語音識別 |
| **ASR-F002** | 噪音環境語音 | 可接受轉錄 | 噪音抑制能力 |
| **ASR-F003** | 即時語音流 | 實時轉錄 | 串流處理 |
| **TTS-F001** | 中文文字 | 自然語音 | 基本語音合成 |
| **TTS-F002** | 英文文字 | 正確發音 | 多語言支援 |
| **TTS-F003** | 特殊符號文字 | 適當處理 | 符號處理 |
| **VLM-F001** | 圖像問答 | 相關回應 | 視覺理解 |
| **VLM-F002** | 圖像描述 | 準確描述 | 圖像分析 |

#### B. 會話管理測試

| 測試案例 | 操作步驟 | 驗證點 | 備註 |
|----------|----------|--------|------|
| **SESSION-F001** | 建立新會話 | 會話 ID 生成 | 基本會話建立 |
| **SESSION-F002** | 多個並行會話 | 會話隔離性 | 資料不混淆 |
| **SESSION-F003** | 會話超時清理 | 自動釋放資源 | 記憶體管理 |
| **SESSION-F004** | 會話手動銷毀 | 即時資源釋放 | 手動清理 |

#### C. 模型管理測試

| 測試案例 | 測試條件 | 預期行為 | 驗證重點 |
|----------|----------|----------|----------|
| **MODEL-F001** | 載入有效模型 | 成功載入 | 正常載入流程 |
| **MODEL-F002** | 載入無效模型 | 錯誤回報 | 錯誤檢測 |
| **MODEL-F003** | 模型記憶體不足 | Fallback 觸發 | 資源檢查 |
| **MODEL-F004** | 重複載入同一模型 | 複用已載入模型 | 快取機制 |
| **MODEL-F005** | 模型卸載 | 記憶體釋放 | 資源清理 |

### 效能測試 (Performance Testing)

#### A. 推論速度測試

| 能力類型 | 測試案例 | 輸入規模 | 目標延遲 | 測試設備 |
|----------|----------|----------|----------|----------|
| **LLM** | 短文本生成 | < 50 tokens | < 2s | 中階設備 |
| **LLM** | 長文本生成 | 200-500 tokens | < 10s | 中階設備 |
| **LLM** | 批次處理 | 10 個短請求 | < 15s | 中階設備 |
| **ASR** | 短音頻 | 5-10 秒 | < 1s | 中階設備 |
| **ASR** | 長音頻 | 60 秒 | < 5s | 中階設備 |
| **ASR** | 實時轉錄 | 連續音頻流 | < 200ms 延遲 | 中階設備 |
| **TTS** | 短文本 | < 100 字元 | < 2s | 中階設備 |
| **TTS** | 長文本 | 500+ 字元 | < 8s | 中階設備 |
| **VLM** | 單張圖片 | 1080p 圖片 | < 3s | 高階設備 |

#### B. 記憶體使用測試

| 測試情境 | 預期記憶體使用 | 監控指標 | 通過條件 |
|----------|---------------|----------|----------|
| **空載狀態** | < 200MB | 基礎記憶體 | 系統穩定 |
| **載入單一 LLM** | < 1.5GB | 模型記憶體 | 無 OOM |
| **載入多個小模型** | < 2GB | 總記憶體 | 無記憶體洩漏 |
| **高頻推論** | 穩定在載入後水準 | 記憶體增長 | 無持續增長 |
| **長時間運行** | 24 小時穩定 | 記憶體穩定性 | 無洩漏 |

#### C. 並發效能測試

| 並發等級 | 測試案例 | 測試持續時間 | 成功率要求 | 效能要求 |
|----------|----------|--------------|------------|----------|
| **低並發** | 2 個同時請求 | 10 分鐘 | > 95% | 延遲增加 < 50% |
| **中並發** | 5 個同時請求 | 10 分鐘 | > 90% | 延遲增加 < 100% |
| **高並發** | 10 個同時請求 | 5 分鐘 | > 80% | 部分請求排隊 |

### 壓力測試 (Stress Testing)

#### A. 資源限制測試

| 測試場景 | 限制條件 | 測試目標 | 預期結果 |
|----------|----------|----------|----------|
| **STRESS-001** | 低記憶體 (< 1GB 可用) | 模型載入與推論 | 降級到小模型 |
| **STRESS-002** | 低儲存空間 (< 500MB) | 下載與快取 | 清理舊快取 |
| **STRESS-003** | 低電量 (< 20%) | 推論執行 | 優先使用省電策略 |
| **STRESS-004** | 網路不穩定 | 雲端 Fallback | 本地優先處理 |
| **STRESS-005** | CPU 高負載 | 推論效能 | 適度延遲可接受 |

#### B. 極限容量測試

| 測試項目 | 極限條件 | 測試方法 | 通過標準 |
|----------|----------|----------|----------|
| **最大會話數** | 同時 50 個會話 | 逐步增加會話 | 系統不崩潰 |
| **最大請求頻率** | 每秒 20 個請求 | 持續高頻請求 | 排隊處理 |
| **最大模型數** | 載入 10 個模型 | 依序載入模型 | 記憶體管理正常 |
| **長時間運行** | 連續運行 72 小時 | 模擬真實使用 | 無記憶體洩漏 |

### 錯誤處理測試 (Error Handling Testing)

#### A. Fallback 機制測試

| 錯誤情境 | 觸發條件 | Fallback 策略 | 驗證重點 |
|----------|----------|---------------|----------|
| **FALLBACK-001** | 主要模型載入失敗 | 切換到備用模型 | 自動切換 |
| **FALLBACK-002** | GPU 推論失敗 | 降級到 CPU | 效能降級可接受 |
| **FALLBACK-003** | 本地推論超時 | 使用雲端 API | 網路可用時切換 |
| **FALLBACK-004** | 記憶體不足 | 卸載其他模型 | 資源管理 |
| **FALLBACK-005** | 多次錯誤累積 | 系統重置 | 錯誤計數與重置 |

#### B. 例外狀況測試

| 例外類型 | 模擬方法 | 預期處理 | 恢復機制 |
|----------|----------|----------|----------|
| **網路中斷** | 模擬離線狀態 | 本地優先處理 | 網路恢復後同步 |
| **檔案損毀** | 破壞模型檔案 | 重新下載 | 自動修復 |
| **權限拒絕** | 移除檔案權限 | 錯誤提示 | 引導修復 |
| **系統重啟** | 模擬 App 重啟 | 狀態恢復 | 會話重建 |

### 相容性測試 (Compatibility Testing)

#### A. 設備硬體相容性

| 設備類型 | CPU 架構 | 記憶體 | GPU 支援 | 測試重點 |
|----------|----------|--------|----------|----------|
| **入門設備** | ARM64 | 4GB | 無 | 基本功能可用 |
| **中階設備** | ARM64 | 6-8GB | Mali/Adreno | 完整功能 |
| **高階設備** | ARM64 | 8GB+ | 高階 GPU | 最佳效能 |
| **特殊晶片** | 含 NPU | 8GB+ | NPU 支援 | NPU 加速 |

#### B. Android 版本相容性

| Android 版本 | API Level | 測試範圍 | 預期支援度 |
|--------------|-----------|----------|------------|
| **Android 8.0** | API 26 | 基本功能 | 有限支援 |
| **Android 9.0** | API 28 | 大部分功能 | 良好支援 |
| **Android 10** | API 29 | 完整功能 | 完全支援 |
| **Android 11+** | API 30+ | 所有功能 | 最佳支援 |

## 🔧 測試工具與框架

### 自動化測試框架

#### Unit Testing
```kotlin
// 使用 JUnit 5 與 MockK
@ExtendWith(MockKExtension::class)
class AIRouterTest {
    @MockK
    private lateinit var mockDispatcher: RequestDispatcher
    
    @InjectMockKs
    private lateinit var aiRouter: AIRouterImpl
    
    @Test
    fun `should execute inference successfully`() = runTest {
        // Given
        val request = InferenceRequest(/* ... */)
        every { mockDispatcher.dispatch(any()) } returns mockResult
        
        // When
        val result = aiRouter.executeInference(request)
        
        // Then
        assertThat(result).isEqualTo(mockResult)
        verify { mockDispatcher.dispatch(request) }
    }
}
```

#### Integration Testing
```kotlin
@RunWith(AndroidJUnit4::class)
class AIRouterIntegrationTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private lateinit var aiRouter: AIRouter
    
    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        aiRouter = AIRouterInitializer().initialize(context)
    }
    
    @Test
    fun `should handle real LLM inference`() = runTest {
        val request = InferenceRequest(
            capability = CapabilityType.LLM,
            input = TextInput("Hello, world!"),
            config = InferenceConfig.default()
        )
        
        val result = aiRouter.executeInference(request)
        
        assertThat(result).isInstanceOf(TextResult::class.java)
        assertThat((result as TextResult).text).isNotEmpty()
    }
}
```

### 效能測試工具

#### 基準測試
```kotlin
class PerformanceBenchmark {
    @Test
    fun benchmarkLLMInference() {
        val benchmark = AndroidBenchmarkRule()
        
        benchmark.measureRepeated {
            val result = aiRouter.executeInference(llmRequest)
            // 驗證結果
        }
        
        // 分析效能指標
        val metrics = benchmark.getMetrics()
        assertThat(metrics.medianTimeNs).isLessThan(TimeUnit.SECONDS.toNanos(2))
    }
}
```

#### 記憶體監控
```kotlin
class MemoryMonitor {
    fun startMonitoring() {
        val runtime = Runtime.getRuntime()
        
        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                val memoryUsageMB = usedMemory / (1024 * 1024)
                
                Timber.d("Memory usage: ${memoryUsageMB}MB")
                
                if (memoryUsageMB > MEMORY_THRESHOLD) {
                    Timber.w("Memory usage exceeds threshold!")
                }
                
                delay(5000) // 每 5 秒檢查一次
            }
        }
    }
}
```

## 📈 測試指標與通過標準

### 功能性指標

| 指標類型 | 測試項目 | 通過標準 | 測量方法 |
|----------|----------|----------|----------|
| **正確性** | 推論結果準確度 | > 90% | 人工評估 + 自動檢查 |
| **穩定性** | 連續運行無錯誤 | > 95% | 自動化測試 |
| **完整性** | 功能覆蓋率 | 100% | 測試案例執行 |

### 效能指標

| 能力類型 | 延遲要求 | 吞吐量要求 | 記憶體限制 |
|----------|----------|------------|------------|
| **LLM** | < 3s (50 tokens) | 10 req/min | < 1.5GB |
| **ASR** | < 1s (10s 音頻) | 30 req/min | < 500MB |
| **TTS** | < 2s (100 字元) | 20 req/min | < 300MB |
| **VLM** | < 5s (1080p) | 5 req/min | < 2GB |

### 可靠性指標

| 測試場景 | 成功率要求 | 測試持續時間 | 錯誤恢復時間 |
|----------|------------|--------------|--------------|
| **正常操作** | > 99% | 24 小時 | N/A |
| **壓力測試** | > 90% | 4 小時 | < 30s |
| **錯誤恢復** | > 95% | 2 小時 | < 10s |

## 🎯 測試執行策略

### 測試階段劃分

#### Phase 1: 單元測試 (Unit Testing)
- **目標**: 驗證個別模組功能
- **覆蓋率**: > 85%
- **執行時機**: 每次 commit
- **持續時間**: < 10 分鐘

#### Phase 2: 整合測試 (Integration Testing)
- **目標**: 驗證模組間互動
- **覆蓋率**: 主要功能流程
- **執行時機**: 每日構建
- **持續時間**: < 30 分鐘

#### Phase 3: 系統測試 (System Testing)
- **目標**: 驗證完整系統功能
- **覆蓋率**: 所有使用案例
- **執行時機**: 版本發佈前
- **持續時間**: 2-4 小時

#### Phase 4: 驗收測試 (Acceptance Testing)
- **目標**: 驗證商業需求符合度
- **覆蓋率**: 核心業務流程
- **執行時機**: 發佈候選版本
- **持續時間**: 1-2 天

### 持續整合管道

```yaml
# CI Pipeline 配置
test_pipeline:
  stages:
    - unit_tests:
        script: "./gradlew testDebugUnitTest"
        artifacts: "build/test-results/"
        
    - integration_tests:
        script: "./gradlew connectedAndroidTest"
        devices: ["emulator-api-29", "physical-device"]
        
    - performance_tests:
        script: "./gradlew benchmarkDebug"
        metrics: ["latency", "memory", "cpu"]
        
    - compatibility_tests:
        script: "./scripts/compatibility_test.sh"
        matrix:
          - android_version: [26, 28, 29, 30, 31]
          - device_type: ["low", "mid", "high"]
```

## 🔗 相關章節

- **錯誤處理**: [Fallback 策略](../05-Error-Handling/fallback-strategies.md) - 錯誤測試案例設計
- **效能最佳化**: [效能調優指南](../04-Runtime/performance-optimization.md) - 效能基準設定
- **Runner 規格**: [Runner 詳細規格](../02-Interfaces/runner-specifications.md) - 功能測試依據
- **實作指南**: [Android 專案結構](../01-Architecture/android-structure.md) - 測試環境設定

## 💡 測試最佳實務

### ✅ 推薦做法

- **測試金字塔**: 多寫單元測試，適量整合測試，少量 E2E 測試
- **測試隔離**: 每個測試案例獨立，不依賴其他測試
- **真實環境**: 在真實設備上執行關鍵測試
- **持續監控**: 建立效能基準與退化檢測
- **自動化優先**: 自動化所有可重複的測試

### 🚫 避免的陷阱

- **過度測試**: 避免測試實作細節而非行為
- **脆弱測試**: 避免頻繁因小變更而失敗的測試
- **忽略邊界**: 確保測試涵蓋邊界條件與錯誤情況
- **手動依賴**: 減少需要手動干預的測試步驟
- **測試債務**: 定期維護與更新測試案例

---

📍 **返回**: [Testing 首頁](./README.md) | **相關**: [實作指南](../07-Implementation/README.md) 