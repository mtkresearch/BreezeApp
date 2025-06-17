# Setup

## Prerequisites

- Android Studio Ladybug (2024.2.1 Patch 3) or newer
- Android SDK 31 or higher
- NDK 26.1.10909125 or higher
- CMake 3.10.0 or higher

## Setup Steps

1. Clone the repository:
    ```bash
    git clone https://github.com/mtkresearch/BreezeApp.git
    ```

2. Open the project in Android Studio:
    - Launch Android Studio
    - Select "Open" from the welcome screen
    - Navigate to and select the `breeze-app` folder
    - Click "OK" to open the project
    - Wait for the project sync and indexing to complete

3. Connect your Android device:
    - Connect your phone to your computer using a USB cable
    - On your phone, allow file transfer/Android Auto when prompted
    - When prompted "Allow USB debugging?", check "Always allow from this computer" and tap "Allow"
    - In Android Studio, select your device from the device dropdown menu in the toolbar
    - If your device is not listed, make sure your USB cable supports data transfer

4. Download required model files:
    - LLM models: \
        a. Breeze2-3B-Instruct:
        ```bash
        # Download from Hugging Face
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/Breeze-Tiny-Instruct-v0_1-mobile
        
        # Push to Android device
        adb push Breeze-Tiny-Instruct-v0_1-mobile/Breeze-Tiny-Instruct-v0_1-2048-spin.pte /data/local/tmp/llama/
        adb push Breeze-Tiny-Instruct-v0_1-mobile/tokenizer.bin /data/local/tmp/llama/
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
        wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        
        tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        rm sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        ```
    - TTS models (place in `app/src/main/assets/`):
        ```bash
        # Download from Hugging Face
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/Breeze2-VITS-onnx
        ```

5. Download ExecuTorch aar file
    - Open the "Project tab" in the left panel of Android Studio
    - Click the dropdown and select "Project" instead of Android view
    - Find "download_prebuilt_lib.sh" inside breeze-app
    - Open the "Terminal" in the left panel, and run the bash file to retrieve aar file
    ```bash
    sh {YOURPATH}/BreezeApp-main/breeze-app/download_prebuilt_lib.sh
    ```

6. Build the project in Android Studio