# BreezeApp - 手機上的 AI 助手

[![License: Pending](https://img.shields.io/badge/License-Pending-yellow.svg)](LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/mtkresearch/BreezeApp)](https://github.com/mtkresearch/BreezeApp/issues)
[![Google Play](https://img.shields.io/badge/Google_Play-即將推出-green.svg?style=flat&logo=google-play)](https://play.google.com)
[![App Store](https://img.shields.io/badge/App_Store-即將推出-blue.svg?style=flat&logo=app-store&logoColor=white)](https://apps.apple.com)

[English](../../README.md) | [繁體中文](README_zh.md)

聊天機器人
<p align="center">
  <img src="../../assets/BreezeApp_npu.gif" width="300" alt="NPU 後端展示"/>&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="../../assets/BreezeApp_cpu.gif" width="300" alt="CPU 後端展示"/>
</p>
<p align="center">
  <em>左：NPU 後端 &nbsp;&nbsp;&nbsp;&nbsp; 右：CPU 後端</em>
</p>

語音轉文字

(準備中)

文字轉語音
<table align="center" border="0" cellspacing="0" cellpadding="0" style="border-collapse: collapse;">
<tr style="border: none;">
<td width="300" valign="middle" style="border: none;">
<img src="../../assets/tts_zh.png" width="300" alt="中文語音合成展示"/>
</td>
<td width="350" valign="middle" style="border: none;">
<p>
<strong>範例一</strong><br/>
『你可以簡短的介紹台灣夜市特色嗎？』<br/>
🎧 <a href="../../assets/tts_zh_1.mp3">直接下載</a>
</p>
<p>
<strong>範例二</strong><br/>
『台灣夜市特色包括多樣的小吃、．．．』<br/>
🎧 <a href="../../assets/tts_zh_2.mp3">直接下載</a>
</p>
</td>
</tr>
</table>

對圖詢問

(準備中)


BreezeAPP 是一款專為 Android 和 iOS 平台開發的純手機 AI 應用程式。用戶只需從 App Store 直接下載，即可在不須連網的離線狀態下享受多項功能，包含了語音轉文字、文字轉語音、文字聊天機器人，以及對圖像進行問答。目前，BreezeApp 支援聯發科技創新基地開發的 Breeze 2 系列模型，未來還會持續更新支援更新、更好的模型。

## 📧 開源的用意

我們是[聯發創新基地](https://i.mediatek.com/mediatekresearch) ([MediaTek Research](https://i.mediatek.com/mediatekresearch))。聯發創新基地是[AI Alliance](https://thealliance.ai/) 的成員。

我們開發這個應用的主要目的，是為了改變大眾對運行大型語言模型(LLM) 的普遍認知。目前，許多人認為LLM的功能只能在昂貴的設備上使用，而且受限於設備提供商的選擇。我們希望提升大眾意識並推廣這樣一個概念：任何人都可以在自己的手機上自由選擇並運行不同的LLM。

我們開發這個應用的另一個主要目的，是通過開源我們的 Kotlin 源代碼，來消除 app 開發者在創建手機 AI 應用時可能遇到的障礙，藉此激發app開發者做出更多有創意的手機應用。我們期待未來能與 app 開發者展開更多合作。

如果您對 BreezeApp 有興趣，歡迎通過以下郵箱與我們聯繫：[info@mtkresearch.com](info@mtkresearch.com)

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