# Android AI Assistant

An open-source Android chatbot that integrates multiple AI capabilities including:
- Large Language Models (LLM)
- Vision Language Models (VLM) 
- Automatic Speech Recognition (ASR)
- Text-to-Speech Synthesis (TTS)

## Features

- 💬 Text-based chat interface
- 🗣️ Voice input/output support
- 📸 Image understanding capabilities
- 🔄 Multiple backend support for each component:
  - LLM/VLM: MTK backend (primary), Executorch framework
  - ASR: MTK backend, Sherpa-ONNX, Android default
  - TTS: MTK backend, Sherpa-TTS, Android default

    | Model Type | Local CPU | MTK NPU | Default |
    |:---------:|:---------:|:-------:|:--------:|
    | LLM       |     ✅     |    ✅    |    ❌    |
    | VLM       |     🚧     |    ❌    |    ❌    |
    | ASR       |     ✅     |    ❌    |    ✅    |
    | TTS       |     ✅     |    ❌    |    ✅    |


## Prerequisites

- Android Studio Arctic Fox or newer
- Android SDK 31 or higher
- NDK 21.0 or higher
- CMake 3.10.2 or higher

## Setup

1. Clone the repository:

```bash
git clone https://github.com/muxi1998/GAI-android.git
```

2. Download required model files:
   - Due to size limitations, model files are not included in the repository
   - Download the following files from [MODEL_DOWNLOAD_LINK]:
     - LLM models (use adb to push into android phone and place under `/data/local/tmp/llama`)
     - VLM models (use adb to push into android phone and place under `/data/local/tmp/llama`)
     - [ASR models](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny/tree/main/test_wavs) (place in `app/src/main/assets/`):
       ```bash
       wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
       
       tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
       rm sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
       ```
     - TTS models (place in `app/src/main/assets/`):
        ```bash
        wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
        tar xvf vits-melo-tts-zh_en.tar.bz2
        rm vits-melo-tts-zh_en.tar.bz2
        ```

3. Build the project in Android Studio

## Project Structure

```
app/src/main/
├── java/com/
│   ├── executorch/        # Executorch framework integration for LLM/VLM
│   ├── k2fsa/            # Sherpa integration for ASR/TTS
│   └── mtkresearch/      # Main application code
│       ├── adapters/     # RecyclerView adapters
│       ├── models/       # Data models
│       └── service/      # AI engine services
├── cpp/                  # Native code for MTK backend
├── assets/              # Model files (to be downloaded separately)
└── res/                 # Android resources
```

## Architecture

The application follows a service-based architecture where each AI capability (LLM, VLM, ASR, TTS) is implemented as an Android service. Each service supports multiple backends with graceful fallback:

1. Primary MTK backend (if available)
2. Open-source alternatives (Executorch/Sherpa)
3. Android system defaults

Key components:
- `ChatActivity`: Main UI for text/voice interaction
- `AudioChatActivity`: Dedicated voice interface
- `*EngineService`: Service implementations for each AI capability

## Contributing

Contributions are welcome! Please read our [Contributing Guidelines](CONTRIBUTING.md) before submitting pull requests.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Executorch](https://github.com/pytorch/executorch) for LLM/VLM framework
- [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) for ASR/TTS capabilities
- MediaTek Research for core AI engines

## Note

This is a research project and some features may require specific hardware support or proprietary components. The open-source version provides alternative implementations where possible.