# 🔌 介面規範 (Interfaces)

## 📋 目標與範圍

本章節定義 AI Router 中所有模組間的介面契約，包含 Runner 統一介面、能力對應關係以及各種 Runner 的詳細規格。這些規範確保系統的一致性和可擴展性。

## 📚 文件清單

### 🔧 [runner-interface.md](./runner-interface.md)
**Runner 統一介面定義**
- BaseRunner 核心介面規範
- 資料傳遞格式定義 (InferenceRequest/Result)
- Streaming 推論介面擴展
- 錯誤處理與狀態管理契約

### 🗺️ [capability-mapping.md](./capability-mapping.md)
**能力與 Runner 對應表**
- Capability 到 Runner 的映射關係
- 支援的模型格式與推論後端
- Fallback 策略配置
- 優先順序與選擇邏輯

### 📋 [runner-specifications.md](./runner-specifications.md)
**各 Runner 詳細規格**
- 每個 Runner 的功能矩陣
- 平台支援與限制說明
- Thread Safety 與並發特性
- 與 AI Router 模組的整合方式

## 🎯 閱讀建議

### 👨‍💻 開發者路徑
1. **runner-interface.md** - 理解統一介面設計
2. **capability-mapping.md** - 了解能力分派邏輯
3. **runner-specifications.md** - 查詢特定 Runner 規格

### 🏗️ 架構師關注點
- 介面設計的擴展性與向後相容性
- 不同 Runner 間的抽象層設計
- 錯誤處理與狀態管理的統一性

### ⏱️ 預估閱讀時間
- **快速查閱**: 10-15 分鐘
- **詳細研讀**: 25-35 分鐘

## 💡 設計原則

### 🎯 統一性
- 所有 Runner 遵循相同的介面契約
- 一致的錯誤處理與狀態回報機制
- 標準化的資料格式與協議

### 🔧 可擴展性
- 支援新 AI 能力的快速整合
- 向後相容的介面演進策略
- 插件式的 Runner 註冊機制

### ⚡ 效能考量
- 最小化介面調用開銷
- 支援 Streaming 與批次處理
- 智能的資源管理與共用策略

## 🔗 相關章節

- **前置閱讀**: [專案總覽](../00-Overview/project-overview.md) - 了解整體設計
- **深入實作**: [模型管理](../03-Models/) - 模型配置與生命週期
- **執行流程**: [Runtime 設計](../04-Runtime/) - 實際執行機制
- **實作指南**: [Implementation](../07-Implementation/) - 開發規範

---

📍 **返回**: [主文件](../README.md) | **上一章**: [架構總覽](../00-Overview/) | **下一章**: [模型管理](../03-Models/) 