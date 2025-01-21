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

- Android Studio Ladybug or newer
- Android SDK 31 or higher
- NDK 26.1.10909125 or higher
- CMake 3.10.0 or higher

## Setup

1. Clone the repository:

```bash
git clone https://github.com/muxi1998/GAI-android.git
```

2. Download required model files:
   - Due to size limitations, model files are not included in the repository
   - Download the following files from [MODEL_DOWNLOAD_LINK]:
     - LLM models (use adb to push into android phone and place under `/data/local/tmp/llama`)
        ```bash
       adb push /path/to/llama3_2.pte /data/local/tmp/llama
       adb push /path/to/tokenizer.bin /data/local/tmp/llama
       ```
     - VLM models (use adb to push into android phone and place under `/data/local/tmp/llava`)
        ```bash
        adb push /path/to/llava.pte /data/local/tmp/llava
        adb push /path/to/tokenizer.bin /data/local/tmp/llava
        ```
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
app/src/main
├── AndroidManifest.xml
├── assets
│   ├── sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20
│   └── vits-melo-tts-zh_en
├── cpp
│   ├── CMakeLists.txt
│   └── mtk_llm_jni.cpp
├── java
│   └── com
│       ├── executorch
│       ├── k2fsa
│       │   └── sherpa
│       │       └── onnx
│       └── mtkresearch
│           └── gai_android
│               ├── AudioChatActivity.java
│               ├── ChatActivity.java
│               ├── MainActivity.java
│               ├── service
│               │   ├── ASREngineService.java
│               │   ├── BaseEngineService.java
│               │   ├── LLMEngineService.java
│               │   ├── TTSEngineService.java
│               │   └── VLMEngineService.java
│               └── utils
│                   ├── AudioListAdapter.java
│                   ├── AudioRecorder.java
│                   ├── AudioWaveView.java
│                   ├── ChatMediaHandler.java
│                   ├── ChatMessage.java
│                   ├── ChatMessageAdapter.java
│                   ├── ChatUIStateHandler.java
│                   ├── FileUtils.java
│                   ├── NativeLibraryLoader.java
│                   └── UiUtils.java
└── res
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