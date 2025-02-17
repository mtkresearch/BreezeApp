# Overview

This project aims to create a community-driven platform for running AI capabilities locally on Android devices. Our goal is to provide a privacy-focused solution where all AI features work completely offline (airplane mode supported), ensuring your data never leaves your device.


<p align="center">
  <img src="assets/breeze2_demo_npu.gif" width="300"/>
</p>


## Project Vision
This app serves as an entry point for everyone, especially those not familiar with coding, to experience AI features directly on their phones. As MediaTek Research continues to develop and provide powerful AI models with various capabilities, this app will act as a carrier to showcase these models and make them accessible to users.

## Community Focus
As a kick-off project, we acknowledge that there might be stability issues and areas for improvement. We welcome developers and enthusiasts to join us in enhancing this project. Feel free to:
- Report issues or bugs
- Suggest new features
- Submit pull requests
- Share your experience and feedback

Together, let's build a privacy-focused AI experience that everyone can use!

## Features

- 💬 Text-based chat interface
- 🗣️ Voice input/output support
- 📸 Image understanding capabilities
- 🔄 Multiple backend support for each component:
  - LLM/VLM: Executorch framework, MediaTek backend (Future)
  - ASR: Sherpa-ONNX, MediaTek backend (Future)
  - TTS: Sherpa-TTS, MediaTek backend (Future)

    | Model Type | Local CPU | MediaTek NPU | Default |
    |:---------:|:---------:|:-------:|:--------:|
    | LLM       |     ✅     |    ✅    |    -    |
    | VLM       |     🚧     |    ❌    |    -    |
    | ASR       |     🚧     |    ❌    |    -    |
    | TTS       |     ✅     |    ❌    |    -    |
🚨 Note: VLM is currently not supported due to the lack of support for image processing in Executorch. 

## Prerequisites

- Android Studio Ladybug (2024.2.1 Patch 3) or newer
- Android SDK 31 or higher
- NDK 26.1.10909125 or higher
- CMake 3.10.0 or higher

## Setup

1. Clone the repository:
    ```bash
    git clone https://github.com/mtkresearch/Breeze2-android-demo.git
    ```

2. Open the project in Android Studio:
    - Launch Android Studio
    - Select "Open" from the welcome screen
    - Navigate to and select the `Breeze2-android-demo` folder
    - Click "OK" to open the project
    - Wait for the project sync and indexing to complete

3. Download required model files:
    - LLM models: \
        a. BreezeTiny:
        ```bash
        # Download from Hugging Face
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/Breeze-Tiny-Instruct-v0_1-mobile
        
        # Push to Android device
        adb push Breeze-Tiny-Instruct-v0_1-mobile/Breeze-Tiny-Instruct-v0_1.pte /data/local/tmp/llama/
        adb push Breeze-Tiny-Instruct-v0_1-mobile/tokenizer.bin /data/local/tmp/llama/
        ```
        b. Llama3.2-3B-Instruct:
        ```bash
        # Download from Hugging Face
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/Llama3.2-3B-Instruct-mobile
        
        # Push to Android device
        adb push Llama3.2-3B-Instruct-mobile/llama3_2.pte /data/local/tmp/llama/
        adb push Llama3.2-3B-Instruct-mobile/tokenizer.bin /data/local/tmp/llama/
        ```

    - VLM models:\
        Coming soon...
        <!-- a. LLaVA-1.5-7B
        ```bash
        # Download from Hugging Face
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/llava-1.5-7b-hf-mobile
        
        # Push to Android device
        adb push llava-1.5-7b-hf-mobile/llava.pte /data/local/tmp/llava/
        adb push llava-1.5-7b-hf-mobile/tokenizer.bin /data/local/tmp/llava/
        ``` -->
    - ASR models (place in `app/src/main/assets/`):
        ```bash
        wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/
        sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        
        tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        rm sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        ```
    - TTS models (place in `app/src/main/assets/`):
        ```bash
        # Download from Hugging Face
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/Breeze2-VITS-onnx

4. Build the project in Android Studio



## Architecture

The application follows a service-based architecture where each AI capability (LLM, VLM, ASR, TTS) is implemented as an Android service. Each service supports multiple backends with graceful fallback:

1. Primary MediaTek backend (🚧 Still in development...)
2. ⭐️ Open-source alternatives (Executorch/Sherpa)
3. Android system defaults

Key components:
- `ChatActivity`: Main UI for text/voice interaction
- `AudioChatActivity`: Dedicated voice interface (🚧 Still in development...)
- `*EngineService`: Service implementations for each AI capability

## Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting pull requests.

## License
TBD
<!-- This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details. -->

## Known Issues

1. **VLM Support (Executorch)**: VLM features are currently non-functional due to limitations in Executorch's image processing capabilities. See [executorch#6189](https://github.com/pytorch/executorch/issues/6189) for updates.

2. **Audio Chat Interface**: The dedicated voice interface (`AudioChatActivity`) is still under development and may have limited functionality.

3. **MediaTek NPU Backend**: Support for MediaTek NPU acceleration is currently in development. Only CPU inference is fully supported at this time.

Please check our [Issues](https://github.com/muxi1998/GAI-android/issues) page for the most up-to-date status of these and other known issues.

## Acknowledgments

- [Executorch](https://github.com/pytorch/executorch) for LLM/VLM framework
- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) for ASR/TTS capabilities
- MediaTek Research for core AI engines

## Note

This is a research project and some features may require specific hardware support or proprietary components. The open-source version provides alternative implementations where possible.