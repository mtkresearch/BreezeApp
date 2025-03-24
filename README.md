# BreezeApp - 手機上的 AI 助手

[![License: Pending](https://img.shields.io/badge/License-Pending-yellow.svg)](LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/mtkresearch/BreezeApp)](https://github.com/mtkresearch/BreezeApp/issues)
[![Google Play](https://img.shields.io/badge/Google_Play-即將推出-green.svg?style=flat&logo=google-play)](https://play.google.com)
[![App Store](https://img.shields.io/badge/App_Store-即將推出-blue.svg?style=flat&logo=app-store&logoColor=white)](https://apps.apple.com)

[繁體中文](README.md) | [English](docs/i18n/README_en.md)

## 概述

本專案旨在創建一個社區驅動的平台，用於在 Android 裝置上本地運行 AI 功能。我們的目標是提供一個注重隱私的解決方案，所有 AI 功能完全離線工作（支援飛行模式），確保您的資料永遠不會離開您的裝置。

<p align="center">
  <img src="assets/BreezeApp_npu.gif" width="300" alt="NPU 後端展示"/>&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="assets/BreezeApp_cpu.gif" width="300" alt="CPU 後端展示"/>
</p>
<p align="center">
  <em>左：NPU 後端 &nbsp;&nbsp;&nbsp;&nbsp; 右：CPU 後端</em>
</p>

> [!NOTE]
> 最新版本中已解決了 <b>CPU</b> 後端的不合理回應問題。有關修復的詳細資訊，請參見<a href="https://github.com/mtkresearch/BreezeApp/issues/5">此處</a>的已關閉問題。

## 🚀 快速開始

### 下載與使用
- [下載最新版 APK](https://huggingface.co/MediaTek-Research/BreezeApp/resolve/main/BreezeApp.apk)
- 應用程式包含應用內模型下載功能，首次啟動時會自動提示您下載所需模型。

### 開發者設定
想要建構和貢獻程式碼？查看我們的[安裝指南](docs/setup/installation_zh.md)獲取詳細說明。

## ✨ 功能特點

- 💬 基於文字的聊天介面
- 🗣️ 語音輸入/輸出支援
- 📸 圖像理解能力
- 🔄 多後端支援：

    | 模型類型 | 本地 CPU | 聯發科 NPU | 預設 |
    |:---------:|:---------:|:-------:|:--------:|
    | LLM       |     ✅     |    ✅    |    -    |
    | VLM       |     🚧     |    ❌    |    -    |
    | ASR       |     🚧     |    ❌    |    -    |
    | TTS       |     ✅     |    ❌    |    -    |

## 🔍 專案結構

- `/app` - Android 應用程式原始碼
- `/docs` - 文件和指南
  - `/setup` - 安裝和配置指南
  - `/architecture` - 系統架構和設計
  - `/contributing` - 貢獻者指南
  - `/i18n` - 國際化文件
- `/assets` - 圖像、影片和其他靜態資源

## 🆘 尋求協助

我們正在尋找貢獻者協助以下任務：

1. **效能優化**
   - 分析應用程式並加強記憶體管理
   - 優化各種裝置上的推理速度
   - 減少應用程式大小和資源消耗

2. **程式碼品質**
   - 重構程式碼以提高可維護性
   - 新增單元和整合測試
   - 實現更好的錯誤處理和日誌記錄

3. **裝置相容性**
   - 在更多 Android 裝置上測試（目前僅在 Pixel 7a (8GB) 和 Samsung Flip 4 上測試過）
   - 識別並修復裝置特定問題
   - 支援不同的螢幕尺寸和長寬比

4. **使用者體驗**
   - 在模型推理期間提高 UI 回應性
   - 增強無障礙功能
   - 創建更直觀的入門流程

5. **文件**
   - 改進程式碼文件
   - 創建開發者教程
   - 在用戶指南中新增截圖和演示

如果您有興趣處理這些任務中的任何一項，請查看我們的問題追蹤器或開啟一個新問題，在提交 PR 之前討論您的方法。

## 🤝 參與貢獻

歡迎貢獻！請查看我們的[貢獻指南](docs/contributing/guidelines.md)開始。

## 📄 授權條款

本專案的授權條款尚未確定 - 詳情請查看 [LICENSE](LICENSE) 檔案。

## 🙏 致謝

- [Executorch](https://github.com/pytorch/executorch) 提供 LLM/VLM 框架
- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 提供 ASR/TTS 功能
- MediaTek Research 提供核心 AI 引擎

## 🔗 相關連結

- [已知問題](https://github.com/mtkresearch/BreezeApp/issues)