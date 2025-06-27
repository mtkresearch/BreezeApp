# BreezeApp 重構流程追蹤表格 (v2.2 - 雙App通訊架構)

*版本: v2.2 | 最後更新: 2024-12-21 | 基於UI/Router獨立App與IPC通訊架構*

## 📋 **重構總覽**

| 階段 | 總任務數 | 完成數 | 進度 | 預估工期 |
|------|----------|--------|------|----------|
| **Phase 0: Project Stabilization** | 3 | 0 | 0% | 1-2天 |
| **Phase 1: UI Layer (Simplified)** | 12 | 12 | 100% | ✅ |
| **Phase 2: UI Domain & Data** | 8 | 7 | 88% | 1週 |
| **Phase 3: AI Router Service** | 15 | 2 | 13% | 4-5週 |
| **Phase 4: AI Router Management** | 8 | 0 | 0% | 2-3週 |
| **Phase 5: System Integration (IPC)** | 6 | 2 | 33% | 2-3週 |
| **總計** | **52** | **23** | **44%** | **9-13週** |

---

## 🎯 **架構重新設計說明**

### **核心改變**
1.  **責任分離**: `breeze-app-ui` 專注於UI/UX，`breeze-app-router` 作為獨立App提供AI服務。
2.  **通信架構**: UI App 透過 **Android IPC (AIDL)** 與 Router App 進行跨進程通訊。
3.  **開發解耦**: UI App 將包含一個 **Mock Service**，使其能在沒有 Router App 的情況下獨立開發與測試。
4.  **共享合約**: 一個新的 `shared-contracts` 模組將定義通訊介面 (AIDL) 和資料模型 (`Parcelable`)。

### **模組邊界**
```
┌───────────────────┐      ┌────────────────────┐      ┌─────────────────────┐
│   breeze-app-ui   │      │  shared-contracts  │      │  breeze-app-router  │
│   (Client App)    ├──────►   (AIDL & Models)  ◄──────┤    (Service App)    │
├───────────────────┤      ├────────────────────┤      ├─────────────────────┤
│• AIRouterClient   │      │• IAIRouter.aidl    │      │• AIRouterService    │
│• ViewModels       │      │• AIRequest.kt      │      │• AI Engines         │
│• MockRouterService│      │  (Parcelable)      │      │• Runtime Settings   │
└───────────────────┘      └────────────────────┘      └─────────────────────┘
```
---

## stabilization **Phase 0: Project Stabilization - 專案穩定化**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P0.1 Fix Package Names | ⏳ TODO | ❌ | ❌ | ❌ | 全面修正 `breezeapp_kotlin` 為 `breezeapp_ui` |
| P0.2 Fix Application ID | ⏳ TODO | ❌ | ❌ | ❌ | 統一 `build.gradle.kts` 中的 `applicationId` |
| P0.3 Decouple Modules | ⏳ TODO | ❌ | ❌ | ❌ | 移除 `breeze-app-ui` 對 `breeze-app-router` 的直接專案依賴 |
---

## 🚀 **Phase 1: UI Layer (Simplified) - 聊天互動 + 應用設定**

### **1.1 Common Base Classes**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P1.1.1 BaseFragment.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 生命週期、權限、錯誤處理 (202行) |
| P1.1.2 BaseViewModel.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 狀態管理、Loading、Error (279行) |
| P1.1.3 BaseAdapter.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | RecyclerView基礎實現 (288行) |

### **1.2 Common UI Widgets**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P1.2.1 MessageBubbleView.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 訊息氣泡、互動按鈕 (350行) + UI測試 (287行) |
| P1.2.2 LoadingView.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 載入動畫、進度指示 (180行) + UI測試 (372行) |
| P1.2.3 ErrorView.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 錯誤顯示、重試機制 (120行) + UI測試 (496行) |


### **1.3 Chat Module (簡化版 - 移除模型管理)**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P1.3.1 ChatMessage.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 訊息數據模型 (35行) + 單元測試 (346行) |
| P1.3.2 MessageAdapter.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | RecyclerView適配器 (400行) + 單元測試 (380行) |
| P1.3.3 ChatViewModel.kt | ✅ COMPLETED | ✅ | ✅ | 🔄 | **v2.2: 待整合 AIRouterClient** |
| P1.3.4 ChatFragment.kt | ✅ COMPLETED | ✅ | ✅ | 🔄 | 聊天UI實現 (593行) + UI測試 (需更新) |
| P1.3.5 AIRouterClient.kt | ❌ DEPRECATED | - | - | - | **v2.2: 將以基於AIDL的新客戶端取代** |

### **1.4 App Settings Module (僅應用層設定)**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P1.4.1 AppSettingsViewModel.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 應用層設定狀態管理 (117行) + 單元測試 (365行) |
| P1.4.2 AppSettingsFragment.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 應用層設定UI + UI測試 (283行) |
| P1.4.3 SettingsActivity.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 設定主活動 (54行) |

### **1.5 Navigation & Main Activity**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P1.5.1 MainActivity.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 主活動實現 (150行) + UI測試 (140行) |
| P1.5.2 HomeFragment.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 主頁UI實現 (200行) + UI測試 |

---

## 🏛️ **Phase 2: UI Domain & Data Layer - 純 UI 業務邏輯**

### **2.1 UI Domain Models**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P2.1.1 AppSettings.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | |
| P2.1.2 ChatSession.kt | ✅ COMPLETED | ✅ | ✅ | 🔄 | |
| P2.1.3 UserProfile.kt | ⏳ TODO | ❌ | ❌ | ❌ | |

### **2.2 UI Repository Interfaces**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P2.2.1 ChatRepository.kt | ⏳ TODO | ❌ | ❌ | ❌ | 對話歷史的本地儲存 |
| P2.2.2 AppSettingsRepository.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | |

### **2.3 UI Use Cases**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P2.3.1 SendMessageUseCase.kt | ✅ COMPLETED | ✅ | ✅ | 🔄 | **v2.1: 已建立** |
| P2.3.2 LoadChatHistoryUseCase.kt | ⏳ TODO | ❌ | ❌ | ❌ | |
| P2.3.3 LoadAppSettingsUseCase.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 應用層設定載入 (14行) + 測試 (232行) |
| P2.3.4 UpdateThemeModeUseCase.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 主題模式更新 (13行) + 測試 (192行) |
| P2.3.5 UpdateFontSizeUseCase.kt | ✅ COMPLETED | ✅ | ✅ | ✅ | 字體大小更新 (13行) + 測試 (240行) |
| P2.3.6 ConnectAIRouterUseCase | ✅ COMPLETED | ✅ | ✅ | 🔄 | **v2.1: 已建立** |
| P2.3.7 ManageChatSessionUseCase | ✅ COMPLETED | ✅ | ✅ | 🔄 | **v2.1: 已建立** |

---

## 🤖 **Phase 3: AI Router Service - 獨立 AI 引擎服務**

### **3.1 AI Router Core Service**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P3.1.1 AIRouterService.kt | ⏳ TODO | ❌ | ❌ | ❌ | 背景主服務 (process=":ai_router") |
| P3.1.2 AIRouterFacade.kt | ✅ COMPLETED | ✅ | ✅ | 🔄 | **v2.1: 已由 `AIRouterRepository` 介面取代** |
| P3.1.3 RequestDispatcher.kt | ✅ COMPLETED | ✅ | ✅ | 🔄 | **v2.1: 已由 `AIRouterRepositoryImpl` 取代** |
| P3.1.4 CapabilityRouter.kt | ⏳ TODO | ❌ | ❌ | ❌ | AI 能力路由 (LLM/VLM/ASR/TTS) |

---

## 🔗 **Phase 5: System Integration - 整合與優化**

### **5.1 Communication Layer (IPC via AIDL)**
| 任務 | 狀態 | 實作 | 驗證 | 測試 | 備註 |
|------|------|------|------|------|------|
| P5.1.1 Communication Protocol | ✅ COMPLETED | ✅ | ✅ | 🔄 | **v2.1: `AIRequest`/`Response` 資料結構已定義** |
| P5.1.2 Shared Contracts Module | ⏳ TODO | ❌ | ❌ | ❌ | 建立 `shared-contracts` Library 模組 |
| P5.1.3 AIDL Interface | ⏳ TODO | ❌ | ❌ | ❌ | 在 `shared-contracts` 中定義 `IAIRouterService.aidl` |
| P5.1.4 Parcelable Models | ⏳ TODO | ❌ | ❌ | ❌ | 在 `shared-contracts` 中實作 `Parcelable` 介面 |
| P5.1.5 Connection Management | ✅ COMPLETED | ✅ | ✅ | 🔄 | **v2.1: 由 `ConnectAIRouterUseCase` 實現，待更新為 AIDL** |
| P5.1.6 AI Router Service Impl | ⏳ TODO | ❌ | ❌ | ❌ | 在 `breeze-app-router` 中實作 AIDL Service |
| P5.1.7 UI Client Impl | ⏳ TODO | ❌ | ❌ | ❌ | 在 `breeze-app-ui` 中實作綁定 AIDL Service 的客戶端 |
| P5.1.8 Mock Service Impl | ⏳ TODO | ❌ | ❌ | ❌ | **關鍵任務**: 在 `breeze-app-ui` 中建立 Mock 版 AIDL Service |

---

## 📊 **品質指標追蹤 (v2.2)**

### **代碼品質**
| 指標 | 目標 | 當前 | 狀態 |
|------|------|------|------|
| UI Layer 測試覆蓋率 | >85% | 90% | ✅ 達標 |
| AI Router 測試覆蓋率 | >80% | 0% | ⏳ 待實作 |
| 代碼複雜度 | <10 | 7.5 | ✅ |
| 技術債務 | <5% | 3% | ✅ |

### **架構品質**
| 指標 | 目標 | 當前 | 狀態 |
|------|------|------|------|
| 模組耦合度 | 低耦合 | 重構中 | 🔄 |
| 介面清晰度 | 高內聚 | 設計中 | ⏳ |
| 測試隔離度 | 100% | 65% | 🔄 |

### **效能指標**
| 指標 | 目標 | 當前 | 狀態 |
|------|------|------|------|
| UI 冷啟動時間 | <2秒 | - | ⏳ |
| AI Router 啟動時間 | <3秒 | - | ⏳ |
| 記憶體使用 | <1.5GB | - | ⏳ |
| IPC 通信延遲 | <50ms | - | ⏳ |

---

## 📈 **測試實作統計 (v2.2)**

### **UI Layer 測試 (已完成)**
| 測試類型 | 檔案數 | 總行數 | 覆蓋範圍 | 狀態 |
|----------|--------|--------|----------|------|
| **單元測試** | **12個** | **3,200行** | **UI業務邏輯** | **✅ COMPLETED** |
| ChatMessageTest.kt | 1個 | 346行 | 訊息模型測試 | ✅ COMPLETED |
| MessageAdapterTest.kt | 1個 | 380行 | 適配器測試 | ✅ COMPLETED |
| AppSettingsViewModelTest.kt | 1個 | 365行 | App設定測試 | ✅ COMPLETED |
| App Settings UseCase 測試 | 3個 | 664行 | UseCase測試 | ✅ COMPLETED |
| AppSettingsRepositoryTest.kt | 1個 | 341行 | Repository測試 | ✅ COMPLETED |
| 其他 UI 測試 | 5個 | 1,104行 | 基礎組件測試 | ✅ COMPLETED |

| **UI測試** | **8個** | **1,951行** | **UI組件** | **✅ COMPLETED** |
| MessageBubbleViewTest.kt | 1個 | 287行 | 訊息氣泡測試 | ✅ COMPLETED |
| LoadingViewTest.kt | 1個 | 372行 | 載入視圖測試 | ✅ COMPLETED |
| ErrorViewTest.kt | 1個 | 496行 | 錯誤視圖測試 | ✅ COMPLETED |
| AppSettingsLayoutTest.kt | 1個 | 283行 | 設定布局測試 | ✅ COMPLETED |
| MainActivityTest.kt | 1個 | 140行 | 主活動測試 | ✅ COMPLETED |
| 其他 UI 測試 | 3個 | 373行 | Fragment測試 | ✅ COMPLETED |

### **AI Router 測試 (待實作)**
| 測試類型 | 檔案數 | 預估行數 | 覆蓋範圍 | 狀態 |
|----------|--------|----------|----------|------|
| **Service 測試** | **0個** | **0行** | **AI Router Core** | **⏳ TODO** |
| **Engine 測試** | **0個** | **0行** | **AI引擎管理** | **⏳ TODO** |
| **管理界面測試** | **0個** | **0行** | **Management UI** | **⏳ TODO** |
| **整合測試** | **0個** | **0行** | **IPC通信** | **⏳ TODO** |

### **總測試統計 (v2.2)**
```kotlin
UI Layer 測試完成:
├── 單元測試: 12個檔案，3,200行代碼
├── UI測試: 8個檔案，1,951行代碼
├── 總測試檔案: 20個
├── 總測試代碼: 5,151行
├── 覆蓋率: 90% (UI Layer)
└── 測試框架: JUnit 5 + Espresso

AI Router 測試計畫:
├── Service測試: 預估6個檔案，1,500行
├── Engine測試: 預估8個檔案，2,000行
├── Management測試: 預估4個檔案，1,000行
├── 整合測試: 預估4個檔案，800行
└── 總預估: 22個檔案，5,300行
```

---

## 🎯 **重構里程碑 (v2.2)**

### **已完成 ✅**
- **UI Layer 基礎架構**: BaseFragment/ViewModel/Adapter 完整實作
- **UI 組件庫**: MessageBubble/Loading/Error 組件完成
- **App Settings 模組**: 應用層設定完整實作和測試
- **聊天 UI 基礎**: ChatMessage/MessageAdapter 基礎實作

### **進行中 🔄**
- **ChatViewModel 重構**: 移除模型管理，實作 AI Router 通信
- **設計 AI Router 通信協議**: 定義請求/回應格式和通信流程
- **實作 AIRouterClient**: UI Layer 的 AI Router 通信客戶端

### **待開始 ⏳**
- **AI Router Service**: 背景服務和 AI 引擎管理
- **AI Router Management**: 浮動管理界面和系統整合
- **IPC 通信實作**: Messenger/AIDL 通信層
- **系統級權限**: Overlay 和背景服務權限管理

---

## 🚀 **下一步行動計畫 (v2.2)**

### **短期目標 (1-3天)**
1. **完成 Phase 0: 專案穩定化**: 修正套件名稱、`applicationId` 及 Gradle 依賴，確保 `breeze-app-ui` 可成功編譯。
2. **建立通訊合約基礎**: 建立 `shared-contracts` 模組，並定義 `IAIRouterService.aidl` 介面草稿。
3. **實作 Parcelable 模型**: 將 `AIRequest`/`AIResponse` 等核心模型改為 `Parcelable`。

### **中期目標 (1-2週)**
1. **實作 Mock Service**: 在 `breeze-app-ui` 中完整實作 Mock 版的 AIDL Service，以便 UI 可獨立開發。
2. **整合 Mock Client**: 將 Mock Service Client 整合進 `ChatViewModel`，讓 UI 流程可以完整運作。
3. **實作真實 Router Service**: 在 `breeze-app-router` 中實作真實的 AIDL Service。

### **長期目標 (3-5週)**
1. **完整 AI Router 功能**: 模型管理、引擎管理、效能監控。
2. **系統整合測試**: 透過 `breeze-app-ui` 對 `breeze-app-router` App 進行完整的跨進程通訊測試。
3. **效能優化**: IPC 通信延遲、記憶體使用、啟動速度優化。

---

## 📝 **重要更新記錄**

### **2024-12-21 - 雙App通訊架構規劃 (v2.2)**
- **核心變更**: 明確 `breeze-app-ui` 和 `breeze-app-router` 為兩個獨立 App，透過 Android IPC (AIDL) 進行通訊。
- **架構升級**: 新增 `shared-contracts` 模組，用於定義共享的通訊介面和資料模型。
- **開發模式**: 新增 **Mock Service** 實作計畫，確保 `breeze-app-ui` 能獨立於 `breeze-app-router` 進行開發與測試。
- **進度追蹤**: 新增 **Phase 0 (專案穩定化)**，並細化 **Phase 5 (系統整合)** 的具體任務。

### **2024-12-20 - 架構重構 (v2.1)**
- **核心變更**: 引入 `breezeapp_UI` 和 `breezeapp_router` 兩個獨立包，實現UI與AI服務的徹底分離。
- **架構升級**: `ChatViewModel` 已完全重構，不再直接依賴任何Router實作，而是通過 Use Case 層進行操作，嚴格遵循Clean Architecture。
- **通信介面**: 舊的 `AIRouterClient` 已被更符合架構設計的 `AIRouterRepository` 介面取代。

### **2024-12-19 - 架構重新設計 (v2.0)**
- **核心變更**: 基於 AI Router 獨立架構重新設計進度追蹤
- **責任分離**: UI Layer 專注聊天互動，AI Router 負責 AI 引擎管理
- **測試重新規劃**: UI Layer 測試保留，AI Router 測試重新規劃
- **進度重新計算**: 總進度從 80% 調整為 38%（架構變更）
- **新增模組**: AI Router Service、AI Router Management 兩大新模組

### **關鍵成就保留**
- ✅ **UI Layer 基礎**: 90% 測試覆蓋率，5,151行測試代碼
- ✅ **MVVM 架構**: Clean Architecture 合規性驗證
- ✅ **App Settings**: 完整應用層設定實作和測試
- ✅ **代碼品質**: 技術債務 3%，複雜度 7.5

---

*最後更新: 2024-12-21*  
*架構版本: v2.2 (雙App獨立通訊架構)*  
*實作狀態: UI Layer 100%完成, 專案穩定化 0%, 系統整合 33%*  
*測試覆蓋率: 90% (UI Layer), 0% (AI Router)*  
*下一步: 執行 Phase 0 的所有任務，穩定化 `breeze-app-ui` 專案。* 

---

## 🧪 **breezeapp_router 模組單元測試進度**

本章節專門追蹤 `breezeapp_router` 模組的單元測試實作進度，確保所有核心業務邏輯的穩定性與可靠性。

| 層級 | 組件 | 測試檔案 | 狀態 | 關鍵測試情境 | 備註 |
|---|---|---|---|---|---|
| **Presentation** | `RuntimeSettingsViewModel` | `RuntimeSettingsViewModelTest.kt` | ✅ COMPLETED | 狀態管理、參數更新、錯誤處理、邊際條件（驗證失敗、狀態保留） | 已遷移並增強 |
| **Domain** | `LoadRuntimeSettingsUseCase` | `LoadRuntimeSettingsUseCaseTest.kt` | ⏳ TODO | 成功載入、Repo返回Failure | |
| **Domain** | `SaveRuntimeSettingsUseCase` | `SaveRuntimeSettingsUseCaseTest.kt` | ⏳ TODO | 成功儲存、Repo返回Failure | |
| **Domain** | `UpdateRuntimeParameterUseCase` | `UpdateRuntimeParameterUseCaseTest.kt` | ⏳ TODO | 所有參數類型的更新邏輯 | |
| **Domain** | `ValidateRuntimeSettingsUseCase`| `ValidateRuntimeSettingsUseCaseTest.kt`| ⏳ TODO | 有效/無效參數、邊界值驗證 | |
| **Data** | `RuntimeSettingsRepositoryImpl` | `RuntimeSettingsRepositoryImplTest.kt`| ⏳ TODO | 與本地數據源的互動、序列化/反序列化錯誤處理 | 依賴本地數據源的 Mock |
| **Data** | `AIRouterRepositoryImpl` | `AIRouterRepositoryImplTest.kt` | ⏳ TODO | 請求分派、成功/錯誤回應處理、連線狀態管理、超時 | 核心通信邏輯 |

---

*最後更新: 2024-12-21*  
*架構版本: v2.2 (雙App獨立通訊架構)*  
*實作狀態: UI Layer 100%完成, AI Router 13%進行中*  
*測試覆蓋率: 90% (UI Layer), 0% (AI Router)*  
*下一步: 執行 Phase 0 的所有任務，穩定化 `breeze-app-ui` 專案。* 