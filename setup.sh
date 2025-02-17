#!/bin/bash

# Exit on any error
set -e

echo "🚀 Starting Android AI Assistant Setup..."

# Check prerequisites
check_prerequisites() {
    echo "📋 Checking prerequisites..."
    
    # Check Android Studio
    # if ! command -v studio &> /dev/null; then
    #     echo "❌ Android Studio not found. Please install Android Studio Ladybug or newer"
    #     exit 1
    # fi
    
    # Check Android SDK
    if [ -z "$ANDROID_HOME" ]; then
        echo "❌ Android SDK not found. Please set ANDROID_HOME environment variable"
        exit 1
    fi
    
    # Check NDK
    if [ ! -d "$ANDROID_HOME/ndk" ]; then
        echo "❌ Android NDK not found. Please install NDK 26.1.10909125 or higher"
        exit 1
    fi
    
    # Check CMake
    if ! command -v cmake &> /dev/null; then
        echo "❌ CMake not found. Please install CMake 3.10.0 or higher"
        exit 1
    fi
}

# Create directories on the connected Android device
setup_directories() {
    echo "📁 Creating directories on Android device..."
    
    # Check if a device is connected
    if ! adb get-state &> /dev/null; then
        echo "❌ No Android device connected. Please connect a device and enable USB debugging."
        exit 1
    fi
    
    # Create directories on the Android device
    echo "Creating directories on device..."
    adb shell "mkdir -p /data/local/tmp/llama"
    adb shell "mkdir -p /data/local/tmp/llava"
    
    # Create local assets directory
    echo "Creating local assets directory..."
    mkdir -p app/src/main/assets
    
    # Set proper permissions on device directories
    adb shell "chmod 777 /data/local/tmp/llama"
    adb shell "chmod 777 /data/local/tmp/llava"
    
    echo "✅ Directories created successfully"
}

# Download and setup models
setup_models() {
    echo "📥 Downloading required models..."
    
    # Create only local directories
    mkdir -p app/src/main/assets
    
    # Download LLM models
    # echo "Downloading Llama3.2-3B-Instruct..."
    # git lfs install
    # git clone https://huggingface.co/MediaTek-Research/Llama3.2-3B-Instruct-mobile
    # adb push Llama3.2-3B-Instruct-mobile/llama3_2.pte /data/local/tmp/llama/
    # adb push Llama3.2-3B-Instruct-mobile/tokenizer.bin /data/local/tmp/llama/
    
    # echo "Downloading BreezeTiny..."
    # git clone https://huggingface.co/MediaTek-Research/Breeze-Tiny-Instruct-v0_1-mobile
    # adb push Breeze-Tiny-Instruct-v0_1-mobile/Breeze-Tiny-Instruct-v0_1.pte /data/local/tmp/llama/
    # adb push Breeze-Tiny-Instruct-v0_1-mobile/tokenizer.bin /data/local/tmp/llama/
    
    # Download ASR models
    echo "Downloading ASR models..."
    wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
    tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2 -C app/src/main/assets/
    rm sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
    
    # Download TTS models
    echo "Downloading TTS models..."
    wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-melo-tts-zh_en.tar.bz2
    tar xvf vits-melo-tts-zh_en.tar.bz2 -C app/src/main/assets/
    rm vits-melo-tts-zh_en.tar.bz2
}

# Download prebuilt libraries
setup_libraries() {
    echo "📚 Setting up libraries..."
    ./download_prebuilt_lib.sh
}

# Build the project
build_project() {
    echo "🔨 Building project..."
    # Choose build variant (llm, vlm, full, or open_source)
    ./gradlew installFullDebug
}

# Main execution
main() {
    check_prerequisites
    setup_directories
    setup_models
    setup_libraries
    build_project
    echo "✅ Setup completed successfully!"
}

# Run the script
main 