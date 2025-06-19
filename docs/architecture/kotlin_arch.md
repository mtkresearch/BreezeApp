# BreezeApp 架構重構概要

## 📋 新架構核心目標

### 主要目標
1. **模組化設計** - 將巨型 Activity 分解為可重用的模組化組件
2. **技術升級** - 從 Java 全面遷移到 Kotlin，採用現代 Android 開發模式
3. **架構清晰** - 實現清晰的分層架構，降低組件間耦合度
4. **測試友好** - 每層組件獨立可測，提升代碼品質
5. **擴展彈性** - 支援多 AI 引擎、多 Backend、多 Runner 的靈活組合

### 達成方式
- **MVVM + UseCase 架構模式** - 分離 UI 邏輯、業務邏輯和數據處理
- **依賴注入** - 使用 Hilt/Dagger 實現組件解耦
- **統一抽象層** - 通過介面定義標準化各層組件互動
- **策略模式** - 支援 Backend 和 Runtime 的動態選擇
- **單一職責原則** - 每個組件專注單一功能，便於維護和測試

## 🏗️ MVVM + UseCase 架構說明

### 架構分層設計

```
📱 Presentation Layer    ← UI 顯示與用戶互動
🎯 Domain Layer         ← 業務邏輯與規則 
🗃️ Data Layer          ← 數據存取與管理
🤖 AI Engine Layer     ← AI 引擎統一管理
⚙️ Runtime Layer       ← 執行環境與策略
🔗 Native Layer        ← 原生庫與模型載入
```

### MVVM + UseCase 組件職責

#### **Presentation Layer**
- **UI Components** (Activity/Fragment): 純 UI 展示，不含業務邏輯
- **ViewModel**: UI 狀態管理，協調 UseCase 呼叫

#### **Domain Layer (核心業務層)**
- **UseCase**: 封裝具體業務邏輯 (如 `SendMessageUseCase`, `ProcessVoiceInputUseCase`)
- **Repository Interface**: 定義數據操作契約，不涉及具體實現
- **Domain Models**: 純業務實體，與 UI 和數據庫無關

#### **Data Layer**
- **Repository Implementation**: 實現 Domain 層定義的數據操作介面
- **數據源協調**: 整合 AI 引擎、本地存儲、網路 API 等多種數據來源

### UseCase 設計理念

**為什麼需要 UseCase？**
- **業務邏輯集中化**: 避免 ViewModel 過於龐大
- **可重用性**: 同一業務邏輯可在不同 UI 中重用
- **測試便利性**: 業務邏輯獨立測試，不依賴 UI 框架
- **清晰的業務邊界**: 每個 UseCase 對應一個明確的用戶操作

**UseCase 範例**:
```kotlin
class SendMessageUseCase(
    private val chatRepository: ChatRepository,
    private val aiEngineManager: AIEngineManager
) {
    suspend operator fun invoke(message: String): Result<AIResponse> {
        // 1. 驗證輸入
        // 2. 呼叫 AI 引擎
        // 3. 處理回應
        // 4. 更新聊天記錄
    }
}
```

## 🔄 架構對應關係

### 目前程式碼 → 新架構對應

| 現有組件 | 新架構位置 | 重構方式 |
|---------|-----------|----------|
| `ChatActivity.java` (2299行) | **分解為多個組件** | Activity → Fragment + ViewModel + UseCase |
| UI 邏輯混合業務邏輯 | **Presentation Layer** | 提取 ViewModel，分離 UI 狀態管理 |
| 硬編碼業務規則 | **Domain Layer UseCase** | 封裝為可測試的業務邏輯類別 |
| 直接調用 Service | **Data Layer Repository** | 通過 Repository 抽象數據存取 |
| `LLMEngineService` 等 | **AI Engine Layer** | 統一管理，支援多 Backend 策略 |
| 原生庫直接調用 | **Native Layer** | 統一 JNI 介面，簡化模型載入 |

### 模組對應結構

```
app/src/main/java/com/mtkresearch/breezeapp/
├── presentation/           ← UI 相關 (Activity, Fragment, ViewModel)
│   ├── chat/              ← 聊天功能 UI
│   ├── settings/          ← 設定功能 UI  
│   └── download/          ← 下載功能 UI
├── domain/                ← 業務邏輯核心
│   ├── usecase/           ← 業務用例 (SendMessage, ProcessVoice...)
│   ├── repository/        ← Repository 介面定義
│   └── model/             ← Domain 實體 (ChatMessage, AIRequest...)
├── data/                  ← 數據存取實現
│   ├── repository/        ← Repository 具體實現
│   └── source/            ← 數據源 (AI Engine, Database, Network)
└── core/                  ← 基礎設施
    ├── di/                ← 依賴注入配置
    ├── ai/                ← AI 引擎管理
    └── native/            ← 原生庫封裝
```

### 重構優先級

#### **Phase 1: 基礎架構建立**
1. 建立 Domain Layer 基礎結構
2. 定義核心 UseCase 和 Repository 介面
3. 設置依賴注入框架

#### **Phase 2: UI 層重構**  
1. 分解 `ChatActivity` 為多個 Fragment
2. 建立對應的 ViewModel
3. 實現 UI 狀態管理

#### **Phase 3: 業務邏輯遷移**
1. 將業務邏輯封裝為 UseCase
2. 實現 Repository 模式
3. 整合 AI Engine 統一管理

#### **Phase 4: 原生層優化**
1. 統一 JNI 介面
2. 簡化模型管理
3. 優化效能和記憶體使用

## 🎯 預期效益

### 開發效益
- **開發效率提升 40%**: 模組化開發，團隊可並行作業
- **維護成本降低 60%**: 清晰架構，問題定位快速
- **測試覆蓋率提升至 80%**: 每層組件獨立可測

### 技術效益  
- **記憶體使用最佳化**: 避免 Activity 記憶體洩漏
- **啟動速度提升 30%**: 延遲載入非核心模組
- **擴展性增強**: 新增 AI 引擎或 Backend 僅需實現介面

### 業務效益
- **功能迭代速度加快**: 新功能開發不影響現有穩定功能
- **品質穩定性提升**: 完善的測試體系保障代碼品質
- **團隊協作效率**: 清晰的模組邊界便於多人協作開發

## 📋 現有功能特性清單

為確保重構後的功能與原版完全一致，以下列出目前應用的所有功能特性：

### 🎨 UI Features (用戶介面功能)
- **主聊天介面**
  - 對話氣泡顯示 (用戶/AI 訊息分離)
  - 即時文字流式顯示 (Streaming response)
  - 訊息長按選擇和複製功能
  - 可選擇文字內容
  - 載入和思考狀態顯示
  - 模型名稱和狀態指示器

- **輸入控制**
  - 文字輸入框 (支援多行)
  - 傳送按鈕 (文字/停止生成切換)
  - 附件按鈕 (相機/圖片選擇/檔案)
  - 語音輸入按鈕 (錄音功能)
  - 新對話按鈕

- **側邊欄抽屜**
  - 聊天歷史列表
  - 歷史對話預覽和日期
  - 歷史對話刪除 (單個/批量)
  - 選擇模式切換

- **設定頁面**
  - LLM 參數調整 (溫度、最大Token、重複懲罰等)
  - 模型選擇下拉選單
  - SeekBar 和數值輸入控制
  - 即時參數驗證和修正

- **對話回饋系統**
  - 讚/踩按鈕
  - 回饋對話框
  - 意見文字輸入

- **權限和導覽**
  - 導覽按鈕 (返回/設定)
  - 權限請求處理 (相機/麥克風)
  - 鍵盤自動隱藏

### 🔧 Utility Features (實用工具功能)
- **檔案管理**
  - 圖片檔案選擇和預覽
  - 相機拍照功能
  - 檔案瀏覽器整合
  - 圖片儲存到相簿
  - 音訊錄製檔案管理

- **音訊處理**
  - 音訊錄製和播放
  - 音訊波形視覺化 (AudioWaveView)
  - 音訊列表管理和刪除
  - 錄製檔案重播功能
  - 音訊振幅檢測

- **對話管理**
  - 對話歷史持久化儲存
  - 對話標題自動生成
  - 對話訊息序列化/反序列化
  - 多對話會話管理
  - 對話狀態維護

- **系統整合**
  - 硬體相容性檢測
  - RAM 使用量監控
  - 儲存空間檢查
  - 權限狀態管理
  - 系統主題適配 (深色/淺色模式)

- **模型管理**
  - 模型下載和安裝
  - 模型檔案驗證
  - 模型版本控制
  - 自動模型選擇 (基於RAM)
  - 模型路徑管理 (Legacy/App 目錄)

### 🤖 LLM Features (大語言模型功能)
- **多Backend支援**
  - CPU Backend (ExecuTorch)
  - MTK NPU Backend (實驗性)
  - 自動Backend選擇和降級

- **推理功能**
  - 串流文字生成 (Streaming inference)
  - 即時Token輸出
  - 生成過程中斷功能
  - 提示格式化 (System + User prompt)
  - 上下文歷史管理

- **模型配置**
  - 溫度控制 (0.0-1.0)
  - 最大Token長度 (128-4096)
  - 重複懲罰係數 (1.0-2.0)
  - 頻率懲罰係數 (1.0-2.0)
  - Top-K 採樣 (整數值)
  - Top-P 採樣 (0.0-1.0)

- **模型支援**
  - Breeze-Tiny-Instruct 模型
  - 多種量化版本支援
  - PTE 格式模型載入
  - Tokenizer 整合

- **效能最佳化**
  - 非同步推理處理
  - 記憶體使用監控
  - 推理超時處理
  - 模型預載和快取

### 👁️ VLM Features (視覺語言模型功能)
- **圖像處理**
  - 圖像選擇和載入 (相簿/相機)
  - 圖像預處理和縮放
  - 多格式圖像支援
  - 圖像數據轉換 (int array)

- **視覺理解**
  - 圖像內容分析
  - 圖像描述生成
  - LLaVA 模型整合
  - 圖像+文字組合提示

- **Backend支援**
  - CPU Backend (LLaVA)
  - MTK NPU Backend (預備)
  - 圖像預填充 (Image prefill)

### 🎤 ASR Features (語音識別功能)
- **多Backend支援**
  - 系統ASR (Android SpeechRecognizer)
  - CPU ASR (Sherpa ONNX)
  - MTK NPU ASR (預備)
  - 雲端ASR (預備)

- **語音處理**
  - 即時語音辨識
  - 部分結果更新
  - 語音轉文字準確度測試
  - 音訊檔案轉錄功能

- **錄音功能**
  - 背景錄音處理
  - 錄音品質控制
  - 錄音檔案管理
  - 錄音權限處理

### 🔊 TTS Features (文字轉語音功能)
- **多Backend支援**
  - 系統TTS (Android TextToSpeech)
  - CPU TTS (Sherpa ONNX)
  - MTK NPU TTS (預備)
  - 雲端TTS (預備)

- **語音合成**
  - 文字轉語音播放
  - 多語言支援 (中文/英文)
  - 語音參數控制 (音調/語速)
  - 音訊串流播放

- **TTS模型**
  - Breeze2-VITS ONNX模型
  - 多說話者支援
  - 自訂詞典 (lexicon.txt)
  - 音素轉換處理

- **播放控制**
  - TTS 播放狀態指示
  - 播放進度動畫
  - 播放錯誤處理
  - 音訊佇列管理

## 🔄 功能對應重構計畫

重構過程中，每個功能都將對應到新架構的特定層級：

| 現有功能類別 | 新架構層級 | 重構方式 |
|-------------|-----------|----------|
| UI 互動邏輯 | **Presentation Layer** | Activity → Fragment + ViewModel |
| 業務流程控制 | **Domain Layer UseCase** | 提取為獨立 UseCase 類別 |
| 資料存取操作 | **Data Layer Repository** | 統一資料存取介面 |
| AI 引擎管理 | **AI Engine Layer** | 統一 AI 引擎抽象和管理 |
| Backend 選擇邏輯 | **Runtime Layer** | 策略模式實現動態切換 |
| 原生庫調用 | **Native Layer** | 統一 JNI 介面封裝 |

這個清單確保了重構後的應用將完全保留所有現有功能，同時獲得更好的架構彈性和擴展性。

## 🎯 重構後功能驗收標準

基於新架構設計，以下是完整的功能驗收標準清單，包含原有功能保留和新架構增強功能：

### 📱 **Presentation Layer 驗收標準**

#### **✅ UI Components (Fragment 化)**
- [ ] **ChatFragment**: 主聊天介面獨立Fragment
  - [ ] 對話氣泡顯示 (用戶/AI訊息分離) 
  - [ ] 即時文字流式顯示 (Streaming response)
  - [ ] 訊息長按選擇和複製功能
  - [ ] 可選擇文字內容
  - [ ] 載入和思考狀態顯示
  - [ ] 錯誤訊息顯示和重試機制

- [ ] **InputFragment**: 輸入控制元件
  - [ ] 文字輸入框 (支援多行自動調整)
  - [ ] 傳送按鈕狀態管理 (文字/停止生成/禁用)
  - [ ] 附件按鈕功能選單
  - [ ] 語音輸入按鈕和錄音狀態
  - [ ] 輸入驗證和字數限制提示

- [ ] **HistoryFragment**: 聊天歷史管理
  - [ ] 歷史對話列表 (日期分組)
  - [ ] 對話預覽和搜尋功能
  - [ ] 批量選擇和刪除操作
  - [ ] 對話重新載入和繼續功能
  - [ ] 歷史清空和匯出功能

- [ ] **SettingsFragment**: 設定管理 (現代化)
  - [ ] 動態設定載入和即時預覽
  - [ ] 參數驗證和錯誤提示
  - [ ] 設定重置和匯入/匯出
  - [ ] 高級設定展開/收合

#### **✅ ViewModel 狀態管理**
- [ ] **ChatViewModel**: 聊天狀態統一管理
  - [ ] UI狀態統一管理 (LiveData/StateFlow)
  - [ ] 配置變更狀態保存 (ViewModel)
  - [ ] 錯誤狀態處理和恢復
  - [ ] 載入狀態指示器管理
  - [ ] 多Fragment間狀態同步

- [ ] **SettingsViewModel**: 設定狀態管理
  - [ ] 設定變更即時驗證
  - [ ] 參數依賴關係管理
  - [ ] 設定變更歷史追蹤

#### **✅ 新增 UI 功能**
- [ ] **主題系統**: 完整的主題切換支援
  - [ ] 深色/淺色模式切換
  - [ ] 系統主題自動跟隨
  - [ ] 自訂主題色彩
  - [ ] 字體大小調整

- [ ] **無障礙功能**: 改善可用性
  - [ ] TalkBack 支援完整
  - [ ] 語音描述和導航
  - [ ] 高對比度支援
  - [ ] 字體放大支援

- [ ] **手勢和快捷鍵**: 提升操作效率
  - [ ] 滑動手勢 (刪除、回覆)
  - [ ] 長按選單擴展
  - [ ] 鍵盤快捷鍵支援
  - [ ] 語音命令整合

### 🎯 **Domain Layer 驗收標準**

#### **✅ UseCase 業務邏輯封裝**
- [ ] **SendMessageUseCase**: 訊息發送流程
  - [ ] 訊息驗證和預處理
  - [ ] AI引擎選擇和調用
  - [ ] 錯誤處理和重試邏輯
  - [ ] 結果後處理和儲存

- [ ] **ProcessVoiceInputUseCase**: 語音輸入處理
  - [ ] 語音錄製管理
  - [ ] ASR 引擎調用
  - [ ] 文字後處理和驗證
  - [ ] 錯誤恢復機制

- [ ] **AnalyzeImageUseCase**: 圖像分析流程
  - [ ] 圖像預處理和驗證
  - [ ] VLM 引擎調用
  - [ ] 結果格式化
  - [ ] 快取管理

- [ ] **SpeakTextUseCase**: 文字轉語音
  - [ ] 文字預處理和分段
  - [ ] TTS 引擎調用
  - [ ] 播放佇列管理
  - [ ] 音量和速度控制

- [ ] **ManageConversationUseCase**: 對話管理
  - [ ] 對話創建和更新
  - [ ] 歷史記錄管理
  - [ ] 上下文窗口控制
  - [ ] 對話匯出和備份

- [ ] **DownloadModelUseCase**: 模型下載管理
  - [ ] 模型版本檢查
  - [ ] 下載進度追蹤
  - [ ] 斷點續傳功能
  - [ ] 下載驗證和安裝

#### **✅ Repository Interface 定義**
- [ ] **ChatRepository**: 聊天數據操作
- [ ] **ModelRepository**: 模型管理操作  
- [ ] **ConfigRepository**: 配置管理操作
- [ ] **MediaRepository**: 媒體檔案操作

#### **✅ Domain Models 業務實體**
- [ ] **ChatMessage**: 增強訊息模型
  - [ ] 多媒體內容支援
  - [ ] 訊息狀態追蹤
  - [ ] 回覆和引用關係
  - [ ] 訊息評分和標記

- [ ] **AIRequest/AIResponse**: 統一AI請求回應
  - [ ] 多模態請求支援
  - [ ] 串流回應處理
  - [ ] 錯誤訊息標準化
  - [ ] 請求追蹤和記錄

### 🗃️ **Data Layer 驗收標準**

#### **✅ Repository Implementation**
- [ ] **本地資料源整合**: Room Database
  - [ ] 對話記錄資料庫設計
  - [ ] 訊息搜尋和索引
  - [ ] 資料遷移和備份
  - [ ] 離線資料同步

- [ ] **快取策略**: 智能快取管理
  - [ ] 模型快取機制
  - [ ] 圖像快取管理
  - [ ] 設定快取同步
  - [ ] LRU 快取策略

- [ ] **檔案系統管理**: 統一檔案操作
  - [ ] 模型檔案管理
  - [ ] 媒體檔案整理
  - [ ] 臨時檔案清理
  - [ ] 儲存空間監控

#### **✅ 新增 Data 功能**
- [ ] **資料同步**: 雲端備份支援 (可選)
  - [ ] 對話歷史雲端備份
  - [ ] 設定同步功能
  - [ ] 跨設備資料同步
  - [ ] 資料匯入/匯出

- [ ] **分析和統計**: 使用行為分析
  - [ ] 使用統計收集
  - [ ] 效能指標追蹤
  - [ ] 當機報告收集
  - [ ] 用戶反饋收集

### 🤖 **AI Engine Layer 驗收標準**

#### **✅ 統一 AI 引擎管理**
- [ ] **AIEngineManager**: 中央協調器
  - [ ] 引擎生命週期管理
  - [ ] 動態引擎切換
  - [ ] 資源使用監控
  - [ ] 引擎健康檢查
  - [ ] 錯誤恢復機制

- [ ] **引擎抽象介面**: 統一 API
  - [ ] 所有引擎共用介面設計
  - [ ] 非同步操作支援
  - [ ] 進度回調機制
  - [ ] 取消操作支援
  - [ ] 狀態查詢介面

#### **✅ 新增 AI 功能**
- [ ] **Guardian API 整合**: 內容安全檢查
  - [ ] 輸入內容檢查
  - [ ] 輸出內容過濾
  - [ ] 多級安全策略
  - [ ] 自訂過濾規則

- [ ] **批次處理**: 提升效率
  - [ ] 批次推理支援
  - [ ] 批次語音轉錄
  - [ ] 批次圖像分析
  - [ ] 佇列管理機制

- [ ] **模型熱切換**: 動態模型管理
  - [ ] 執行時模型切換
  - [ ] 模型預載機制
  - [ ] 記憶體使用最佳化
  - [ ] 模型版本比較

### ⚙️ **Runtime Layer 驗收標準**

#### **✅ Backend Strategy 管理**
- [ ] **智能Backend選擇**: 自動最佳化
  - [ ] 硬體能力檢測
  - [ ] 效能基準測試
  - [ ] 動態Backend切換
  - [ ] 負載平衡機制

- [ ] **Runtime Engines 最佳化**
  - [ ] ExecuTorch 引擎增強
  - [ ] MTK NPU 引擎穩定化
  - [ ] Qualcomm 引擎整合
  - [ ] ONNX Runtime 最佳化
  - [ ] Cloud Runtime 整合

#### **✅ 新增 Runtime 功能**
- [ ] **效能監控**: 即時效能追蹤
  - [ ] 推理速度監控
  - [ ] 記憶體使用追蹤
  - [ ] 電池消耗監控
  - [ ] 溫度控制機制

- [ ] **資源管理**: 智能資源分配
  - [ ] 動態記憶體分配
  - [ ] CPU 核心調度
  - [ ] GPU 資源管理
  - [ ] NPU 排程最佳化

### 🔗 **Native Layer 驗收標準**

#### **✅ 統一 Native 介面**
- [ ] **Unified JNI Bridge**: 簡化原生調用
  - [ ] 統一錯誤處理機制
  - [ ] 自動資源管理
  - [ ] 執行緒安全保證
  - [ ] 記憶體洩漏防護

- [ ] **Universal Model Loader**: 通用模型載入
  - [ ] 多格式模型支援 (PTE/DLA/ONNX/TFLite)
  - [ ] 模型驗證機制
  - [ ] 載入進度追蹤
  - [ ] 錯誤診斷功能

#### **✅ 新增 Native 功能**
- [ ] **原生效能最佳化**: 底層優化
  - [ ] SIMD 指令最佳化
  - [ ] 記憶體對齊優化
  - [ ] 批次處理加速
  - [ ] 快取友好設計

### 🧪 **Testing & Quality 驗收標準**

#### **✅ 測試覆蓋率**
- [ ] **Unit Tests**: 80%+ 測試覆蓋率
  - [ ] UseCase 單元測試
  - [ ] ViewModel 測試
  - [ ] Repository 測試
  - [ ] Utils 類別測試

- [ ] **Integration Tests**: 系統整合測試
  - [ ] AI 引擎整合測試
  - [ ] 資料庫整合測試
  - [ ] UI 整合測試
  - [ ] 端到端測試

- [ ] **UI Tests**: 使用者介面測試
  - [ ] Espresso UI 測試
  - [ ] 可用性測試
  - [ ] 效能測試
  - [ ] 壓力測試

#### **✅ 程式碼品質**
- [ ] **靜態分析**: 程式碼品質保證
  - [ ] Ktlint 程式碼格式檢查
  - [ ] Detekt 程式碼品質分析
  - [ ] SonarQube 安全漏洞掃描
  - [ ] 程式碼覆蓋率報告

### 🚀 **Performance & Reliability 驗收標準**

#### **✅ 效能指標**
- [ ] **啟動時間**: 冷啟動 < 3秒，熱啟動 < 1秒
- [ ] **記憶體使用**: 峰值記憶體 < 2GB
- [ ] **推理速度**: LLM 推理 > 5 tokens/sec
- [ ] **電池消耗**: 正常使用 < 10%/hour

#### **✅ 穩定性指標**
- [ ] **當機率**: ANR + Crash < 0.1%
- [ ] **記憶體洩漏**: 0 記憶體洩漏
- [ ] **資源清理**: 100% 資源正確釋放
- [ ] **錯誤恢復**: 100% 錯誤情況可恢復

## 📊 **驗收檢查表**

| 功能類別 | 原有功能 | 新增功能 | 總功能數 | 完成狀態 |
|---------|---------|---------|---------|----------|
| UI Features | 16 | 12 | 28 | ⏳ 待開發 |
| Domain UseCase | 0 | 18 | 18 | ⏳ 待開發 |
| Data Layer | 17 | 8 | 25 | ⏳ 待開發 |
| AI Engine | 76 | 15 | 91 | ⏳ 待開發 |
| Runtime Layer | 0 | 12 | 12 | ⏳ 待開發 |
| Native Layer | 0 | 8 | 8 | ⏳ 待開發 |
| Testing | 0 | 20 | 20 | ⏳ 待開發 |
| Performance | 0 | 8 | 8 | ⏳ 待開發 |
| **總計** | **109** | **101** | **210** | **0%** |

這個全面的驗收標準確保重構後的應用不僅保留所有原有功能，還具備現代化架構的所有優勢，為未來擴展打下堅實基礎。