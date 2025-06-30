> **Note:** This document is the detailed technical specification for the internal architecture of the **AI Router module**. For the overall project roadmap, cross-app architecture, and milestone definitions, please refer to the primary [**BreezeApp Refactoring Plan**](./refactoring_plan.md).

# 🚀 AI Router 設計文件

## 📋 專案概覽

AI Router 是 BreezeApp 的核心模組，負責橋接 UI 層與多種 AI 能力（LLM、VLM、ASR、TTS、Guardian 等），提供統一、可擴展的推論服務架構。

### 🎯 核心目標
- **強健封裝**：提供穩定可靠的 AI 服務抽象層
- **可插拔架構**：支援動態 Runner 和模型擴展
- **智能管理**：自動化模型生命週期與資源管理
- **Clean Architecture**：遵循 SOLID 原則的分層設計
- **高可用性**：內建錯誤處理與降級機制

## 📚 文件導航

### 🔍 快速開始
新手建議按以下順序閱讀：

1. [專案總覽](./00-Overview/project-overview.md) - 了解整體概念
2. [架構圖表](./00-Overview/architecture-diagram.md) - 視覺化系統設計
3. [模組依賴](./00-Overview/module-dependencies.md) - 理解組件關係
4. [Android結構](./01-Architecture/android-structure.md) - 實際專案組織

### 📂 文件分類

#### 🔍 [00-Overview](./00-Overview/) - 架構總覽
- 專案概述與核心概念
- 系統架構視覺化
- 模組間依賴關係

#### 🏛️ [01-Architecture](./01-Architecture/) - 架構設計
- 核心組件設計原則
- 資料流程與狀態管理
- 執行緒模型與並發策略
- Android 專案結構規劃

#### 🔌 [02-Interfaces](./02-Interfaces/) - 介面規範
- Runner 統一介面定義
- 能力與 Runner 對應表
- 各 Runner 詳細規格

#### 📦 [03-Models](./03-Models/) - 模型管理
- 模型配置規範與格式
- 模型共用策略設計
- 模型生命週期管理

#### 🚀 [04-Runtime](./04-Runtime/) - 執行時設計
- 派發器工作流程
- 推論執行流程
- 系統資源管理策略

#### 🛡️ [05-Error-Handling](./05-Error-Handling/) - 錯誤處理
- 統一錯誤碼定義
- 降級策略與 Fallback
- 錯誤恢復機制

#### 🧪 [06-Testing](./06-Testing/) - 測試策略
- 完整測試矩陣
- 單元測試指南
- 整合測試計畫

#### 📐 [07-Implementation](./07-Implementation/) - 實作指南
- 開發規範與最佳實務
- 程式碼風格標準
- 部署與維護指南

#### 📊 [08-Diagrams](./08-Diagrams/) - 圖表資源
- Mermaid 原始碼管理
- 匯出圖片資源

## �� 不同角色的閱讀建議

### 👨‍💻 **開發工程師**
```
README.md → 00-Overview → 02-Interfaces → 03-Models → 07-Implementation
```

### 🏗️ **系統架構師**
```
README.md → 00-Overview → 01-Architecture → 04-Runtime → 05-Error-Handling
```

### 🧪 **測試工程師**
```
README.md → 00-Overview → 06-Testing → 05-Error-Handling → 02-Interfaces
```

### 📱 **產品經理**
```
README.md → 00-Overview → 簡要瀏覽各章節的目標與範圍部分
```

## 🛠️ 技術堆疊

- **語言**: Kotlin, JNI/C++
- **平台**: Android (API 24+)
- **架構**: Clean Architecture, MVVM
- **並發**: Kotlin Coroutines
- **推論引擎**: ONNX Runtime, PyTorch Mobile, MediaPipe
- **圖表**: Mermaid

## 📈 專案狀態

| 組件 | 狀態 | 說明 |
|------|------|------|
| 🏗️ 架構設計 | ✅ 完成 | 核心架構已定義 |
| 🔌 介面規範 | ✅ 完成 | Runner介面已標準化 |
| 📦 模型管理 | ✅ 完成 | 配置規範已建立 |
| 🛡️ 錯誤處理 | ✅ 完成 | 錯誤碼與策略已定義 |
| 🧪 測試策略 | ✅ 完成 | 測試矩陣已建立 |
| 💻 程式碼實作 | 🔄 進行中 | 依據設計文件實作中 |

## 🤝 貢獻指南

1. 閱讀相關設計文件
2. 遵循 [開發規範](./07-Implementation/development-guidelines.md)
3. 執行相應測試
4. 更新相關文件

## 📞 聯絡資訊

如有疑問或建議，請：
- 提交 Issue 討論設計相關問題
- 參考 [Implementation Guide](./07-Implementation/) 了解實作詳情
- 查看 [Testing Matrix](./06-Testing/test-matrix.md) 了解驗證標準

---

**🎯 最後更新**: 2025-01
**📄 文件版本**: v2.0
**👥 維護團隊**: BreezeApp AI Router Team 