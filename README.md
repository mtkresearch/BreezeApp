# BreezeApp - AI on phone

[![License: Pending](https://img.shields.io/badge/License-Pending-yellow.svg)](LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/mtkresearch/BreezeApp)](https://github.com/mtkresearch/BreezeApp/issues)
[![Google Play](https://img.shields.io/badge/Google_Play-Coming_Soon-green.svg?style=flat&logo=google-play)](https://play.google.com)
[![App Store](https://img.shields.io/badge/App_Store-Coming_Soon-blue.svg?style=flat&logo=app-store&logoColor=white)](https://apps.apple.com)

[English](README.md) | [繁體中文](./docs/i18n/README_zh.md)

## Overview

BreezeAPP is a mobile AI application developed for both Android and iOS platforms. Users can download it directly from the App Store and enjoy a variety of features offline, including speech-to-text, text-to-speech, text-based chatbot interactions, and image question-answering. Currently, BreezeApp defaults to the Breeze 2 series of models developed by MediaTek Research and we provide simple installation instructions for changing your backend to Llama 3.2 See the [Roadmap](/Roadmap.md) for additional planned integrations. 

This project aims to create a community-driven platform for running AI capabilities locally on Android devices. Our goal is to provide a privacy-focused solution where all AI features work completely offline (airplane mode supported), ensuring your data never leaves your device.

<p align="center">
  <img src="./assets/BreezeApp_npu_en.gif" width="300" alt="NPU Backend Demo"/>&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="./assets/BreezeApp_cpu_en.gif" width="300" alt="CPU Backend Demo"/>
</p>
<p align="center">
  <em>Left: NPU Backend &nbsp;&nbsp;&nbsp;&nbsp; Right: CPU Backend</em>
</p>

Speech-to-Text

(In preparation)

Text-to-Speech
<p align="center">
  <img src="./assets/tts_en.png" width="300" alt="Text-to-Speech Demo"/>
</p>

## 🚀 Quick Start 

### Download & Try
- [Download the latest APK](https://huggingface.co/MediaTek-Research/BreezeApp/resolve/main/BreezeApp.apk)
- The app includes an in-app model download feature that will automatically prompt you to download required models on first launch.

### Developer Setup
Looking to build and contribute? Check our [Setup Guide](/docs/setup/installation.md) for detailed instructions.

### Working with Submodules
This project uses Git Submodules for component management. See [Submodules Guide](/docs/setup/submodules.md) for detailed instructions on:
- Initial setup and cloning
- Updating submodules
- Contributing to components
- Troubleshooting

## ✨ Features

- 💬 Text-based chat interface
- 🗣️ Voice input/output support
- 📸 Image understanding capabilities
- 🔄 Multiple backend support:

    | Model Type | Local CPU | MediaTek NPU | Default |
    |:---------:|:---------:|:-------:|:--------:|
    | LLM       |     ✅     |    ✅    |    -    |
    | VLM       |     🚧     |    ❌    |    -    |
    | ASR       |     🚧     |    ❌    |    -    |
    | TTS       |     ✅     |    ❌    |    -    |

## 🔍 Project Structure

- `/BreezeApp` - **Production App** (Official consumer application)
- `/BreezeApp-client` - **Engineering App** (Demo of using the core AI engine)
- `/BreezeApp-engine` - **Core AI Engine** (Inference framework)
- `/app` - Android application source code
- `/docs` - Documentation and guides
  - `/setup` - Installation and configuration guides
  - `/architecture` - System architecture and design
  - `/contributing` - Guidelines for contributors
  - `/i18n` - Internationalized documentation
- `/assets` - Images, videos, and other static resources

## 🤝 Contributing

Contributions are welcome! See our [Contributing Guide](/docs/contributing/guidelines.md) to get started.

## 📄 License

The license for this project is pending determination - see the [LICENSE](/LICENSE) file for details.

## 🙏 Acknowledgments

- [Executorch](https://github.com/pytorch/executorch) for LLM/VLM framework
- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) for ASR/TTS capabilities
- MediaTek Research for core AI engines

## 🔗 Links

- [Known Issues](https://github.com/mtkresearch/BreezeApp/issues) 

## 📧 We are

[MediaTek Research](https://i.mediatek.com/mediatekresearch), a memeber of the [AI Alliance](https://thealliance.ai/).

Contact us: [info@mtkresearch.com](info@mtkresearch.com)
