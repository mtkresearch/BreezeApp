# BreezeApp

[![License: Pending](https://img.shields.io/badge/License-Pending-yellow.svg)](LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/mtkresearch/BreezeApp)](https://github.com/mtkresearch/BreezeApp/issues)
[![Google Play](https://img.shields.io/badge/Google_Play-即將推出-green.svg?style=flat&logo=google-play)](https://play.google.com)
[![App Store](https://img.shields.io/badge/App_Store-即將推出-blue.svg?style=flat&logo=app-store&logoColor=white)](https://apps.apple.com)

[繁體中文](README.md) | [English](docs/i18n/README_en.md)

Text chatbot
<p align="center">
  <img src="assets/BreezeApp_npu.gif" width="300" alt="NPU 後端展示"/>&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="assets/BreezeApp_cpu.gif" width="300" alt="CPU 後端展示"/>
</p>
<p align="center">
  <em>左：NPU 後端 &nbsp;&nbsp;&nbsp;&nbsp; 右：CPU 後端</em>
</p>

Speech-to-Text

Text-to-Speech

Image QA

BreezeAPP是一個 Android 以及 iOS 的純手機 AI 應用程式。從 App Store 直接下載後，使用者可以不須連網，就享受語音轉文字，文字轉語音，文字聊天機器人，以及對影像問問題的功能。目前 BreezeApp 支援聯發創新基地的 Breeze 2 系列的模型，未來會支援更新更好的模型。

## 您與我

我們是[聯發創新基地](https://i.mediatek.com/mediatekresearch) ([MediaTek Research](https://i.mediatek.com/mediatekresearch))。聯發創新基地是[AI Alliance](https://thealliance.ai/) 的成員。

我們開發這個應用的主要目的，是鑒於目前一般大眾，普遍認為 LLM的功能，受制於總之是有很貴的設備的人，他給出什麼，才用的到什麼。我們想要提升與散播，任何人都可以決定在他的手機上跑哪款 LLM 的認知。

我們開發這個應用的另一個主要目的，是希望藉由我們將 Kotlin 的源碼開源後，我們能夠移除 app 開發者建立手機 AI APP 所有的門檻，讓 app開發者能夠做出更多有創意的手機應用。我們期待未來能與 app 開發者協作。

您可以聯繫我們於 info 在於 mtkresearch.com.

## 🚀 快速開始

### 下載與使用
- [下載最新版 APK](https://huggingface.co/MediaTek-Research/BreezeApp/resolve/main/BreezeApp.apk)
- 應用程式包含應用內模型下載功能，首次啟動時會自動提示您下載所需模型。

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

## Compatible models

    | 模型類型 | |
    |:---------:|:--------:|
    | LLM       |  Breeze 2   |
    | VLM       |  Breeze 2   |   
    | ASR       |  Breeze 2   |    
    | TTS       |  Breeze 2   |   

## Compatible devices
目前僅在 Pixel 7a (8GB) 和 Samsung Flip 4 上測試通過，希望能得到更多的實測回報

### 開發者設定
想要建構和貢獻程式碼？查看我們的[安裝指南](docs/setup/installation_zh.md)獲取詳細說明。

## 🆘 尋求協助

我們正在尋找貢獻者協助以下任務：

1. **推廣**
   - 能見度
   - 多語言
    
2. **效能優化**
   - 分析應用程式並加強記憶體管理
   - 優化各種裝置上的推理速度
   - 減少應用程式大小和資源消耗

3. **程式碼品質**
   - 重構程式碼以提高可維護性
   - 新增單元和整合測試
   - 實現更好的錯誤處理和日誌記錄

4. **裝置相容性**
   - 在更多 Android 裝置上測試
   - 識別並修復裝置特定問題
   - 支援不同的螢幕尺寸和長寬比

5. **使用者體驗**
   - 在模型推理期間提高 UI 回應性
   - 增強無障礙功能
   - 創建更直觀的入門流程

6. **文件**
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
