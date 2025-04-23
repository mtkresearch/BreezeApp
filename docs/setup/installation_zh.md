# 設定

## 前置需求

- Android Studio Ladybug (2024.2.1 Patch 3) 或更新版本
- Android SDK 31 或更高版本
- NDK 26.1.10909125 或更高版本
- CMake 3.10.0 或更高版本

## 設定步驟

1. 複製程式碼庫：
    ```bash
    git clone https://github.com/mtkresearch/BreezeApp.git
    ```

2. 在 Android Studio 中開啟專案：
    - 啟動 Android Studio
    - 在歡迎畫面中選擇 "Open"
    - 導航至並選擇 `breeze-app` 資料夾
    - 點擊 "OK" 開啟專案
    - 等待專案同步和索引完成

3. 連接您的 Android 裝置：
    - 使用 USB 傳輸線將您的手機連接到電腦
    - 在手機上，當提示時允許檔案傳輸/Android Auto
    - 當提示 "允許 USB 偵錯？" 時，勾選 "一律允許使用這台電腦" 並點擊 "允許"
    - 在 Android Studio 中，從工具列的裝置下拉選單中選擇您的裝置
    - 如果您的裝置未列出，請確保您的 USB 傳輸線支援數據傳輸

4. 下載所需模型檔案：
    - LLM 模型：\
        a. Breeze2-3B-Instruct
        ```bash
        # 從 Hugging Face 下載
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/Breeze-Tiny-Instruct-v0_1-mobile
        
        # 推送到 Android 裝置
        adb push Breeze-Tiny-Instruct-v0_1-mobile/Breeze-Tiny-Instruct-v0_1.pte /data/local/tmp/llama/
        adb push Breeze-Tiny-Instruct-v0_1-mobile/tokenizer.bin /data/local/tmp/llama/
        ```

    - VLM 模型：\
        即將推出...
        <!-- a. LLaVA-1.5-7B
        ```bash
        # 從 Hugging Face 下載
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/llava-1.5-7b-hf-mobile
        
        # 推送到 Android 裝置
        adb push llava-1.5-7b-hf-mobile/llava.pte /data/local/tmp/llava/
        adb push llava-1.5-7b-hf-mobile/tokenizer.bin /data/local/tmp/llava/
        ``` -->
    - ASR 模型（放在 `app/src/main/assets/` 中）：
        ```bash
        wget https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        
        tar xvf sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        rm sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20.tar.bz2
        ```
    - TTS 模型（放在 `app/src/main/assets/` 中）：
        ```bash
        # 從 Hugging Face 下載
        git lfs install
        git clone https://huggingface.co/MediaTek-Research/Breeze2-VITS-onnx
        ```

5. 下載 aar 檔案
    - 在 Android Studio 左側面板中開啟 "Project" 選項卡
    - 點擊下拉選單並選擇 "Project" 而不是 Android 視圖
    - 在 breeze-app 中找到 "download_prebuilt_lib.sh"
    - 在左側面板中開啟 "Terminal"，並執行 bash 檔案以獲取 aar 檔案
    ```bash
    sh {YOURPATH}/BreezeApp-main/breeze-app/download_prebuilt_lib.sh
    ```

6. 在 Android Studio 中建構專案

## 變更預設後端

要變更預設後端（從 CPU 到 MTK），請依照以下步驟：

1. 開啟 "AppConstants.java" 檔案，位於：
   ```bash
   cd {YOURPATH}/BreezeApp-main/breeze-app/app/src/main/java/com/mtkresearch/breeze_app/utils/AppConstants.java
   ```
2. 使用您偏好的程式設計工具修改以下常數：
   ```java
   // 後端常數
   public static final String BACKEND_CPU = "cpu" ;
   public static final String BACKEND_MTK = "mtk" ;
   public static final String BACKEND_DEFAULT = BACKEND_MTK ; // 變更為所需後端
   ```
   
   預設情況下，後端設定為 "CPU"。如果您想使用 "MTK" 作為應用程式後端，請修改以下行：
   ```java
   // 後端常數
   ...
   public static final String BACKEND_DEFAULT = BACKEND_CPU ; // 變更為所需後端
   ```

3. 修改後端後，在 Android Studio 中 "重新建構" 專案以套用變更。 