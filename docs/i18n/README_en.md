# BreezeApp - AI on phone

[![License: Pending](https://img.shields.io/badge/License-Pending-yellow.svg)](LICENSE)
[![GitHub issues](https://img.shields.io/github/issues/mtkresearch/BreezeApp)](https://github.com/mtkresearch/BreezeApp/issues)
[![Google Play](https://img.shields.io/badge/Google_Play-Coming_Soon-green.svg?style=flat&logo=google-play)](https://play.google.com)
[![App Store](https://img.shields.io/badge/App_Store-Coming_Soon-blue.svg?style=flat&logo=app-store&logoColor=white)](https://apps.apple.com)

[繁體中文](../../README.md) | [English](README_en.md)

## Overview

This project aims to create a community-driven platform for running AI capabilities locally on Android devices. Our goal is to provide a privacy-focused solution where all AI features work completely offline (airplane mode supported), ensuring your data never leaves your device.

<p align="center">
  <img src="../..//assets/BreezeApp_npu.gif" width="300" alt="NPU Backend Demo"/>&nbsp;&nbsp;&nbsp;&nbsp;
  <img src="../..//assets/BreezeApp_cpu.gif" width="300" alt="CPU Backend Demo"/>
</p>
<p align="center">
  <em>Left: NPU Backend &nbsp;&nbsp;&nbsp;&nbsp; Right: CPU Backend</em>
</p>

> [!NOTE]
> Previous issues with unreasonable responses from the <b>CPU</b> backend have been resolved in the latest version. For details about the fix, see the closed issue <a href="https://github.com/mtkresearch/BreezeApp/issues/5">here</a>.

## 🚀 Quick Start 

### Download & Try
- [Download the latest APK](https://huggingface.co/MediaTek-Research/BreezeApp/resolve/main/BreezeApp.apk)
- The app includes an in-app model download feature that will automatically prompt you to download required models on first launch.

### Developer Setup
Looking to build and contribute? Check our [Setup Guide](/docs/setup/installation.md) for detailed instructions.

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

- `/app` - Android application source code
- `/docs` - Documentation and guides
  - `/setup` - Installation and configuration guides
  - `/architecture` - System architecture and design
  - `/contributing` - Guidelines for contributors
  - `/i18n` - Internationalized documentation
- `/assets` - Images, videos, and other static resources

## 🆘 Help Wanted

We're looking for contributors to help with the following tasks:

1. **Performance Optimization**
   - Profile the app and enhance memory management
   - Optimize inference speed on various devices
   - Reduce app size and resource consumption

2. **Code Quality**
   - Refactor code to improve maintainability
   - Add unit and integration tests
   - Implement better error handling and logging

3. **Device Compatibility**
   - Test on more Android devices (currently only tested on Pixel 7a (8GB) and Samsung Flip 4)
   - Identify and fix device-specific issues
   - Support for different screen sizes and aspect ratios

4. **User Experience**
   - Improve UI responsiveness during model inference
   - Enhance accessibility features
   - Create more intuitive onboarding process

5. **Documentation**
   - Improve code documentation
   - Create developer tutorials
   - Add screenshots and demos to user guides

If you're interested in working on any of these tasks, please check out our issue tracker or open a new issue to discuss your approach before submitting a PR.

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