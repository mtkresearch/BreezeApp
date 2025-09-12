# 🏗️ **BreezeApp 架構概覽**

*版本: v2.0 | 最後更新: 2024-12-19*

---

## 📋 **目錄**

1. [**專案概述**](#project-overview)
2. [**架構設計原則**](#architecture-principles)
3. [**核心架構模式**](#core-architecture)
4. [**分層架構說明**](#layer-architecture)
5. [**雙層設定系統**](#dual-settings-system)
6. [**模組化設計**](#modular-design)
7. [**技術選型**](#technology-stack)
8. [**開發流程**](#development-workflow)

---

## 🎯 **專案概述** {#project-overview}

BreezeApp 是一個現代化的 AI 對話應用程式，支援多種 AI 引擎 (LLM、VLM、ASR、TTS) 的整合。本專案採用 **MVVM + Clean Architecture** 設計模式，實現高度模組化、可測試、可擴展的架構。

### **核心特色**
- 🤖 **多 AI 引擎支援**: LLM、VLM、ASR、TTS 統一管理
- 🏗️ **Clean Architecture**: 分層架構，職責清晰
- 🔧 **雙層設定系統**: 應用層與 AI 推論層分離配置
- 📱 **現代化 UI**: Material Design 3 + 響應式設計
- 🧪 **高測試覆蓋**: 單元測試 + 整合測試
- 🔄 **CI/CD 整合**: 自動化建置與部署

---

## 🎨 **架構設計原則** {#architecture-principles}

### **SOLID 原則**
- **S**ingle Responsibility: 每個類別只負責一個功能
- **O**pen/Closed: 對擴展開放，對修改封閉
- **L**iskov Substitution: 子類別可以替換父類別
- **I**nterface Segregation: 介面分離，避免肥大介面
- **D**ependency Inversion: 依賴抽象，不依賴具體實現

### **Clean Architecture 核心概念**
- **依賴方向**: 外層依賴內層，內層不依賴外層
- **抽象隔離**: 通過介面定義層間互動
- **業務邏輯獨立**: Domain Layer 不依賴框架
- **可測試性**: 每層都可獨立測試

---

## 🏗️ **核心架構模式** {#core-architecture}

### **MVVM + UseCase 架構**

```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐      │
│  │  Fragment   │◄──►│  ViewModel  │◄──►│   Adapter   │      │
│  └─────────────┘    └─────────────┘    └─────────────┘      │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                     Domain Layer                            │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐      │
│  │   UseCase   │◄──►│ Repository  │◄──►│    Model    │      │
│  └─────────────┘    └─────────────┘    └─────────────┘      │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                      Data Layer                             │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐      │
│  │ Repository  │◄──►│ DataSource  │◄──►│   Entity    │      │
│  │    Impl     │    │             │    │             │      │
│  └─────────────┘    └─────────────┘    └─────────────┘      │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                   AI Engine Layer                           │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐      │
│  │ AI Manager  │◄──►│   Engine    │◄──►│   Config    │      │
│  └─────────────┘    └─────────────┘    └─────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

---

## 📚 **分層架構說明** {#layer-architecture}

### **🎨 Presentation Layer**
- **職責**: UI 顯示與用戶互動
- **組件**: Fragment, ViewModel, Adapter
- **特點**: 響應式 UI, 狀態管理, 事件處理

### **🎯 Domain Layer**
- **職責**: 業務邏輯與規則
- **組件**: UseCase, Repository Interface, Domain Model
- **特點**: 框架無關, 純業務邏輯, 高度可測試

### **🗃️ Data Layer**
- **職責**: 數據存取與管理
- **組件**: Repository Implementation, DataSource, Entity
- **特點**: 數據源抽象, 緩存策略, 錯誤處理

### **🤖 AI Engine Layer**
- **職責**: AI 引擎統一管理
- **組件**: AI Manager, Engine Wrapper, Configuration
- **特點**: 多引擎支援, 策略模式, 效能優化

---

## ⚙️ **雙層設定系統** {#dual-settings-system}

### **設計理念**
BreezeApp 採用雙層設定系統，將應用程式層面的設定與 AI 推論層面的設定分離，提供更靈活的配置管理。

### **架構設計**

```
┌─────────────────────────────────────────────────────────────┐
│                    雙層設定系統架構                           │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────┐    ┌─────────────────────┐        │
│  │    應用層設定        │    │   AI推論層設定       │        │
│  │  (App Settings)     │    │(Runtime Settings)   │        │
│  ├─────────────────────┤    ├─────────────────────┤        │
│  │ • 主題色彩          │    │ • LLM 參數          │        │
│  │ • 字體大小          │    │   - Temperature     │        │
│  │ • 語言偏好          │    │   - Top-K, Top-P    │        │
│  │ • 深色/淺色模式     │    │   - Max Tokens      │        │
│  │ • 通知設定          │    │ • VLM 參數          │        │
│  │ • 儲存位置          │    │   - 圖像解析度      │        │
│  │ • 動畫效果          │    │   - 視覺溫度        │        │
│  └─────────────────────┘    │ • ASR 參數          │        │
│                             │   - 語言模型        │        │
│  ┌─────────────────────┐    │   - Beam Size       │        │
│  │   儲存策略分離       │    │ • TTS 參數          │        │
│  ├─────────────────────┤    │   - 說話者 ID       │        │
│  │ • SharedPreferences │    │   - 語速調整        │        │
│  │ • DataStore         │    │   - 音調控制        │        │
│  │ • 持久化儲存        │    └─────────────────────┘        │
│  │ • 跨設備同步        │                                   │
│  └─────────────────────┘    ┌─────────────────────┐        │
│                             │   動態配置管理       │        │
│                             ├─────────────────────┤        │
│                             │ • 即時參數更新      │        │
│                             │ • 參數驗證          │        │
│                             │ • 預設值管理        │        │
│                             │ • 引擎同步          │        │
│                             └─────────────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### **設定分層邏輯**

#### **🎨 應用層設定 (App Settings)**
- **生命週期**: 應用程式啟動時載入，變更後立即生效
- **儲存方式**: SharedPreferences + DataStore
- **同步機制**: 跨 Fragment 即時同步
- **配置範圍**: 
  - UI 外觀設定 (主題、字體、語言)
  - 系統行為設定 (通知、動畫、儲存)
  - 使用者偏好設定 (預設值、快捷鍵)

#### **🤖 AI推論層設定 (Runtime Settings)**
- **生命週期**: 進入 Chat 時載入，推論前動態應用
- **儲存方式**: 記憶體快取 + 持久化備份
- **同步機制**: 與 AI 引擎即時同步
- **配置範圍**:
  - LLM 推論參數 (Temperature, Top-K, Top-P, Max Tokens)
  - VLM 視覺參數 (圖像解析度, 視覺溫度)
  - ASR 語音參數 (語言模型, Beam Size, 語言偏好)
  - TTS 合成參數 (說話者 ID, 語速, 音調)

### **設定管理流程**

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   用戶操作       │    │   設定驗證       │    │   設定生效       │
├─────────────────┤    ├─────────────────┤    ├─────────────────┤
│ • 調整參數      │───►│ • 範圍檢查      │───►│ • 即時更新      │
│ • 選擇預設值    │    │ • 依賴驗證      │    │ • 引擎同步      │
│ • 重置設定      │    │ • 格式驗證      │    │ • UI 反映       │
│ • 匯入/匯出     │    │ • 錯誤提示      │    │ • 持久化儲存    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

---

## 🧩 **模組化設計** {#modular-design}

### **模組分離原則**
- **功能模組化**: 每個功能獨立封裝
- **層級模組化**: 不同層級獨立開發
- **介面統一**: 統一的介面定義
- **依賴注入**: 模組間解耦

### **核心模組**

```
┌─────────────────────────────────────────────────────────────┐
│                      模組化架構                              │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │   Chat      │  │  Settings   │  │  Download   │        │
│  │   Module    │  │   Module    │  │   Module    │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │    Core     │  │  AI Engine  │  │   Common    │        │
│  │   Module    │  │   Module    │  │   Module    │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

## 🛠️ **技術選型** {#technology-stack}

### **核心技術**
- **語言**: Kotlin 100%
- **架構**: MVVM + Clean Architecture
- **UI**: Material Design 3 + Jetpack Compose (未來)
- **DI**: Hilt/Dagger
- **異步**: Coroutines + Flow
- **數據**: Room + DataStore
- **測試**: JUnit 5 + Mockk + Espresso

### **AI 引擎**
- **LLM**: ExecuTorch + MTK NPU
- **VLM**: LLaVA + 自定義 Backend
- **ASR**: Sherpa ONNX + 系統 ASR
- **TTS**: Breeze-VITS + 系統 TTS

---

## 🔄 **開發流程** {#development-workflow}

### **開發階段**
1. **Phase 1**: Presentation Layer (UI + ViewModel)
2. **Phase 2**: Domain Layer (UseCase + Repository)
3. **Phase 3**: Data Layer (Repository + DataSource)
4. **Phase 4**: AI Engine Layer (AI Manager + Engine)
5. **Phase 5**: Integration & Testing

### **品質保證**
- **代碼審查**: Pull Request 必須通過審查
- **自動化測試**: 單元測試覆蓋率 > 80%
- **持續整合**: GitHub Actions 自動建置
- **性能監控**: 記憶體、CPU 使用率監控

---

## 📊 **總結**

BreezeApp 採用現代化的架構設計，通過分層架構、雙層設定系統、模組化設計等方式，實現了高度可維護、可擴展、可測試的代碼結構。這個架構不僅支援當前的功能需求，也為未來的功能擴展提供了良好的基礎。

## Known Issues

1. **VLM Support (Executorch)**: VLM features are currently non-functional due to limitations in Executorch's image processing capabilities. See [executorch#6189](https://github.com/pytorch/executorch/issues/6189) for updates.

2. **Audio Chat Interface**: The dedicated voice interface (`AudioChatActivity`) is still under development and may have limited functionality.

3. **MediaTek NPU Backend**: Support for MediaTek NPU acceleration is currently in development. Only CPU inference is fully supported at this time. 