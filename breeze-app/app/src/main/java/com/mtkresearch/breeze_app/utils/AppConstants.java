package com.mtkresearch.breeze_app.utils;

import android.content.Context;
import java.io.File;
import android.util.Log;
import java.io.IOException;
import android.app.ActivityManager;
import android.content.SharedPreferences;
import com.mtkresearch.breeze_app.utils.ModelUtils;

public class AppConstants {
    private static final String TAG = "AppConstants";

    // Shared Preferences
    public static final String PREFS_NAME = "GAISettings";
    
    // Preference Keys
    public static final String KEY_HISTORY_LOOKBACK = "history_lookback";
    public static final String KEY_SEQUENCE_LENGTH = "sequence_length";
    public static final String KEY_DEFAULT_MODEL = "default_model";
    public static final String KEY_FIRST_LAUNCH = "first_launch";
    public static final String KEY_TEMPERATURE = "temperature";
    public static final String KEY_PREFERRED_BACKEND = "preferred_backend";
    public static final String DEFAULT_BACKEND = "cpu";  // Default to CPU backend
    
    // Service Enable Flags
    public static final boolean LLM_ENABLED = true;  // LLM is essential
    public static final boolean VLM_ENABLED = false; // VLM is experimental
    public static final boolean ASR_ENABLED = false; // ASR requires permission
    public static final boolean TTS_ENABLED = true;  // TTS is stable
    
    // Backend Constants
    public static final String BACKEND_NONE = "none";
    public static final String BACKEND_CPU = "cpu";
    public static final String BACKEND_MTK = "mtk";
    public static final String BACKEND_DEFAULT = BACKEND_MTK;  // Default to CPU backend since MTK is experimental
    
    // Backend Enable Flags
    public static final boolean MTK_BACKEND_ENABLED = true;  // Set to true to enable MTK backend
    public static volatile boolean MTK_BACKEND_AVAILABLE = false;  // Runtime state of MTK backend availability
    
    // Backend Initialization Constants
    public static final long BACKEND_INIT_DELAY_MS = 200;    // Delay between backend initialization attempts
    public static final long BACKEND_CLEANUP_DELAY_MS = 100; // Delay for backend cleanup operations
    public static final int MAX_MTK_INIT_ATTEMPTS = 5;       // Maximum attempts to initialize MTK backend
    public static final long MTK_NATIVE_OP_TIMEOUT_MS = 10000;
    public static final long MTK_CLEANUP_TIMEOUT_MS = 5000;   // 5 seconds timeout for cleanup
    
    // MTK Backend Constants
    public static final String MTK_CONFIG_PATH = "Breeze2-3B-Instruct-mobile-npu/";
    public static final String MTK_SERVICE_TAG = "LLMEngineService";
    public static final Object MTK_LOCK = new Object();
    public static final boolean MTK_VALIDATE_UTF8 = false;
    public static final long MTK_STOP_DELAY_MS = 100;  // Delay between stop attempts
    public static final int MTK_TOKEN_SIZE = 1; // Token size for generation
    public static final int MTK_PROMPT_TOKEN_SIZE = 128; // Token size for prompt processing
    public static volatile int mtkInitCount = 0;       // Counter for MTK initialization attempts
    public static volatile boolean isCleaningUp = false; // Flag to track MTK cleanup state
    
    // LLM Stop Tokens
    public static final String LLM_STOP_TOKEN_EOT = "<|eot_id|>";
    public static final String LLM_STOP_TOKEN_EOT_ALT = "<|end_of_text|>";
    
    // LLM Service Constants
    public static final long LLM_INIT_TIMEOUT_MS = 300000;  // 5 minutes for initialization
    public static final long LLM_GENERATION_TIMEOUT_MS = Long.MAX_VALUE;  // No timeout for generation
    public static final long LLM_NATIVE_OP_TIMEOUT_MS = 10000;  // 10 seconds for native ops
    public static final long LLM_CLEANUP_TIMEOUT_MS = 10000;  // 10 seconds for cleanup
    public static final int LLM_MAX_MTK_INIT_ATTEMPTS = 3;
    public static final String DEFAULT_SYSTEM_PROMPT = "你是擁有臺灣知識的語言模型，請用繁體中文或英文回答以下問題";

    // Model Files and Paths
    public static final String LLAMA_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048.pte";
    public static final String BREEZE_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048.pte";
    public static final String BREEZE_MODEL_DISPLAY_NAME = "Breeze Tiny Instruct v0.1 (2048)";
    
    // LLM Model Size Options
    public static final String LARGE_LLM_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048.pte";
    public static final String SMALL_LLM_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048-spin.pte";
    public static final String LARGE_LLM_MODEL_DISPLAY_NAME = "Breeze2";
    public static final String SMALL_LLM_MODEL_DISPLAY_NAME = "Breeze2-spinQuant";
    
    // RAM Requirements
    public static final long MIN_RAM_REQUIRED_GB = 7; // Minimum RAM for the app to run
    public static final long LARGE_MODEL_MIN_RAM_GB = 10; // Minimum RAM for large model
    
    // Model Selection Key
    public static final String KEY_MODEL_SIZE_PREFERENCE = "model_size_preference";
    public static final String MODEL_SIZE_LARGE = "large";
    public static final String MODEL_SIZE_SMALL = "small";
    public static final String MODEL_SIZE_AUTO = "auto"; // Let the app decide based on available RAM
    
    public static final String LLAMA_MODEL_DIR = "/data/local/tmp/llama/";  // Legacy location
    public static final String APP_MODEL_DIR = "models";  // New path relative to app's private storage
    public static final String LLM_TOKENIZER_FILE = "tokenizer.bin";  // Add tokenizer filename constant
    
    // TTS Model Files and Paths
    public static final String TTS_MODEL_DIR = "Breeze2-VITS-onnx";
    public static final String TTS_MODEL_FILE = "breeze2-vits.onnx";
    public static final String TTS_LEXICON_FILE = "lexicon.txt";
    public static final String TTS_TOKENS_FILE = "tokens.txt";
    
    // Model Download Constants
    public static final String MODEL_BASE_URL = "https://huggingface.co/MediaTek-Research/Breeze-Tiny-Instruct-v0_1-mobile/resolve/main/";
    
    // Model Download URLs - defined before usage in LLM_DOWNLOAD_FILES
    public static final String[] MODEL_DOWNLOAD_URLS = {
        // Tokenizer - small file, use regular URL
        MODEL_BASE_URL + "tokenizer.bin?download=true",
        // Model file - try multiple reliable sources
        MODEL_BASE_URL + BREEZE_MODEL_FILE + "?download=true"
    };
    
    // TTS Model Download URLs
    private static final String TTS_MODEL_BASE_URL = "https://huggingface.co/MediaTek-Research/Breeze2-VITS-onnx/resolve/main/";
    private static final String TTS_HF_MIRROR_URL = "https://hf-mirror.com/MediaTek-Research/Breeze2-VITS-onnx/resolve/main/";
    
    // Download status constants
    public static final int DOWNLOAD_STATUS_PENDING = 0;
    public static final int DOWNLOAD_STATUS_IN_PROGRESS = 1;
    public static final int DOWNLOAD_STATUS_PAUSED = 2;
    public static final int DOWNLOAD_STATUS_COMPLETED = 3;
    public static final int DOWNLOAD_STATUS_FAILED = 4;
    
    // File type constants
    public static final String FILE_TYPE_LLM = "LLM Model";
    public static final String FILE_TYPE_TOKENIZER = "Tokenizer";
    public static final String FILE_TYPE_TTS_MODEL = "TTS Model";
    public static final String FILE_TYPE_TTS_LEXICON = "TTS Lexicon";
    public static final String FILE_TYPE_TTS_TOKENS = "TTS Tokens";
    
    
    // Download file information
    public static final class DownloadFileInfo {
        public final String url;
        public final String fileName;
        public final String displayName;
        public final String fileType;
        public final long fileSize;
        
        public DownloadFileInfo(String url, String fileName, String displayName, String fileType, long fileSize) {
            this.url = url;
            this.fileName = fileName;
            this.displayName = displayName;
            this.fileType = fileType;
            this.fileSize = fileSize;
        }
        
        /**
         * Convenience constructor that sets fileType to FILE_TYPE_LLM by default
         */
        public DownloadFileInfo(String url, String fileName, String displayName, long fileSize) {
            this(url, fileName, displayName, FILE_TYPE_LLM, fileSize);
        }
    }
    
    // LLM related download files
    public static final DownloadFileInfo[] LLM_DOWNLOAD_FILES = {
        new DownloadFileInfo(
            MODEL_DOWNLOAD_URLS[0], // Using first URL from MODEL_DOWNLOAD_URLS
            LLM_TOKENIZER_FILE,
            "Tokenizer",
            FILE_TYPE_TOKENIZER,
            5 * 1024 * 1024 // ~5MB estimate
        ),
        new DownloadFileInfo(
            MODEL_DOWNLOAD_URLS[1], // Using second URL from MODEL_DOWNLOAD_URLS 
            BREEZE_MODEL_FILE,
            "Language Model",
            FILE_TYPE_LLM,
            6 * 1024 * 1024 * 1024L // 6GB estimate
        )
    };

    public static final DownloadFileInfo[] MTK_NPU_LLM_DOWNLOAD_FILES = {
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/BreezeTinyInstruct_v0.1_sym4W_sym16A_Overall_28layer_128t1024c_0.dla", // Using second URL from MODEL_DOWNLOAD_URLS 
                "BreezeTinyInstruct_v0.1_sym4W_sym16A_Overall_28layer_128t1024c_0.dla",
                "Language Model",
                    AppConstants.FILE_TYPE_LLM,
                2 * 1024 * 1024 * 1024L // 6GB estimate
        ),
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/BreezeTinyInstruct_v0.1_sym4W_sym16A_Overall_28layer_128t1024c_0_extracted.dla", // Using second URL from MODEL_DOWNLOAD_URLS 
                "BreezeTinyInstruct_v0.1_sym4W_sym16A_Overall_28layer_128t1024c_0_extracted.dla",
                "Language Model",
                AppConstants.FILE_TYPE_LLM,
                16  * 1024 * 1024L // 6GB estimate
        ),
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/BreezeTinyInstruct_v0.1_sym4W_sym16A_Overall_28layer_1t1024c_0.dla", // Using second URL from MODEL_DOWNLOAD_URLS 
                "BreezeTinyInstruct_v0.1_sym4W_sym16A_Overall_28layer_1t1024c_0.dla",
                "Language Model",
                    AppConstants.FILE_TYPE_LLM,
                2 * 1024 * 1024 * 1024L // 6GB estimate
        ),
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/BreezeTinyInstruct_v0.1_sym4W_sym16A_Overall_28layer_1t1024c_0_extracted.dla", // Using second URL from MODEL_DOWNLOAD_URLS 
                "BreezeTinyInstruct_v0.1_sym4W_sym16A_Overall_28layer_1t1024c_0_extracted.dla",
                "Language Model",
                    AppConstants.FILE_TYPE_LLM,
                5  * 1024 * 1024L // 6GB estimate
        ),
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/added_tokens.yaml", // Using second URL from MODEL_DOWNLOAD_URLS 
                "added_tokens.yaml",
                "YAML",
                    AppConstants.FILE_TYPE_LLM,
                10  * 1024L // 6GB estimate
        ),
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/config_breezetiny_3b_instruct.yaml", // Using second URL from MODEL_DOWNLOAD_URLS 
                "config_breezetiny_3b_instruct.yaml",
                "YAML",
                    AppConstants.FILE_TYPE_LLM,
                3 *  1024L // 6GB estimate
            ),
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/embedding_int16.bin", // Using second URL from MODEL_DOWNLOAD_URLS 
                "embedding_int16.bin",
                "bin",
                    AppConstants.FILE_TYPE_LLM,
                790 * 1024 * 1024L // 6GB estimate
            ),
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/shared_weights_0.bin", // Using second URL from MODEL_DOWNLOAD_URLS 
                "shared_weights_0.bin",
                "bin",
                    AppConstants.FILE_TYPE_LLM,
                790 * 1024 * 1024L // 6GB estimate
        ),
        new AppConstants.DownloadFileInfo(
                "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/tokenizer.tiktoken", // Using second URL from MODEL_DOWNLOAD_URLS 
                "tokenizer.tiktoken",
                "bin",
                    AppConstants.FILE_TYPE_LLM,
                3 * 1024 * 1024L // 6GB estimate
         )

    };
    // TTS related download files
    public static final DownloadFileInfo[] TTS_DOWNLOAD_FILES = {
        new DownloadFileInfo(
            TTS_MODEL_BASE_URL + TTS_MODEL_FILE + "?download=true",
            TTS_MODEL_FILE,
            "TTS Model", 
            FILE_TYPE_TTS_MODEL,
            100 * 1024 * 1024 // ~100MB estimate
        ),
        new DownloadFileInfo(
            TTS_MODEL_BASE_URL + TTS_LEXICON_FILE + "?download=true",
            TTS_LEXICON_FILE,
            "Lexicon",
            FILE_TYPE_TTS_LEXICON,
            1 * 1024 * 1024 // ~1MB estimate
        ),
        new DownloadFileInfo(
            TTS_MODEL_BASE_URL + "tokens.txt?download=true",
            "tokens.txt",
            "Tokens",
            FILE_TYPE_TTS_TOKENS,
            100 * 1024 // ~100KB estimate
        )
    };
    
    // TTS Model Download URLs (keeping for backward compatibility)
    public static final String[] TTS_MODEL_DOWNLOAD_URLS = {
        // Primary TTS model files
        TTS_MODEL_BASE_URL + TTS_MODEL_FILE + "?download=true",
        TTS_HF_MIRROR_URL + TTS_MODEL_FILE + "?download=true",
        // Lexicon file
        TTS_MODEL_BASE_URL + TTS_LEXICON_FILE + "?download=true",
        TTS_HF_MIRROR_URL + TTS_LEXICON_FILE + "?download=true",
        // Tokens file
        TTS_MODEL_BASE_URL + "tokens.txt?download=true",
        TTS_HF_MIRROR_URL + "tokens.txt?download=true"
    };

    // Check if TTS models exist in assets or app storage
    public static boolean hasTTSModels(Context context) {
        // First check app's private storage
        File ttsDir = new File(new File(context.getFilesDir(), APP_MODEL_DIR), TTS_MODEL_DIR);
        File primaryModel = new File(ttsDir, TTS_MODEL_FILE);
        File primaryLexicon = new File(ttsDir, TTS_LEXICON_FILE);
        File primaryTokens = new File(ttsDir, "tokens.txt");
        
        boolean primaryExists = primaryModel.exists() && primaryModel.isFile() && primaryModel.length() > 0 &&
                              primaryLexicon.exists() && primaryLexicon.isFile() && primaryLexicon.length() > 0 &&
                              primaryTokens.exists() && primaryTokens.isFile() && primaryTokens.length() > 0;
        
        if (primaryExists) {
            return true;
        }
        
        // Then check assets
        try {
            context.getAssets().open(TTS_MODEL_DIR + "/" + TTS_MODEL_FILE).close();
            context.getAssets().open(TTS_MODEL_DIR + "/" + TTS_LEXICON_FILE).close();
            context.getAssets().open(TTS_MODEL_DIR + "/tokens.txt").close();
            return true;
        } catch (IOException e) {
            Log.d(TAG, "TTS models not found in assets", e);
        }
        
        return false;
    }

    // Get TTS model path
    public static String getTTSModelPath(Context context) {
        // First check app's private storage
        File ttsDir = new File(new File(context.getFilesDir(), APP_MODEL_DIR), TTS_MODEL_DIR);
        File primaryModel = new File(ttsDir, TTS_MODEL_FILE);
        
        if (primaryModel.exists() && primaryModel.isFile() && primaryModel.length() > 0) {
            return primaryModel.getAbsolutePath();
        }
        
        // Then check assets
        try {
            context.getAssets().open(TTS_MODEL_DIR + "/" + TTS_MODEL_FILE).close();
            return TTS_MODEL_DIR + "/" + TTS_MODEL_FILE;
        } catch (IOException e) {
            Log.d(TAG, "TTS model not found in assets", e);
        }
        
        return null;
    }

    // Check if TTS models need to be downloaded
    public static boolean needsTTSModelDownload(Context context) {
        return !hasTTSModels(context);
    }
    
    // Get absolute path to the app's TTS model directory
    public static String getAppTTSModelDir(Context context) {
        return new File(new File(context.getFilesDir(), APP_MODEL_DIR), TTS_MODEL_DIR).getAbsolutePath();
    }

    // Get absolute path to the app's model directory
    public static String getAppModelDir(Context context) {
        return new File(context.getFilesDir(), APP_MODEL_DIR).getAbsolutePath();
    }

    // Check if model exists in legacy location
    public static boolean isModelInLegacyLocation() {
        File legacyModelFile = new File(LLAMA_MODEL_DIR, BREEZE_MODEL_FILE);
        return legacyModelFile.exists() && legacyModelFile.length() > 0;
    }

    // Get the model path to use, prioritizing legacy location
    public static String getModelPath(Context context) {
        // Get the appropriate model file based on preferences and RAM
        String modelFileName = getAppropriateModelFile(context);
        
        // First check the legacy location
        File legacyModelFile = new File(LLAMA_MODEL_DIR, modelFileName);
        Log.d("AppConstants", "Checking legacy model path: " + legacyModelFile.getAbsolutePath());
        if (legacyModelFile.exists() && legacyModelFile.length() > 0) {
            Log.d("AppConstants", "Found model in legacy directory: " + legacyModelFile.getAbsolutePath());
            return legacyModelFile.getAbsolutePath();
        }

        // If not in legacy location, use app's private storage path
        File appModelFile = new File(new File(context.getFilesDir(), APP_MODEL_DIR), modelFileName);
        Log.d("AppConstants", "Using app model path: " + appModelFile.getAbsolutePath());
        return appModelFile.getAbsolutePath();
    }

    // Get the tokenizer path to use
    public static String getTokenizerPath(Context context) {
        // First check the legacy location
        File legacyTokenizerFile = new File(LLAMA_MODEL_DIR, LLM_TOKENIZER_FILE);
        if (legacyTokenizerFile.exists() && legacyTokenizerFile.length() > 0) {
            return legacyTokenizerFile.getAbsolutePath();
        }

        // If not in legacy location, use app's private storage path
        File appTokenizerFile = new File(new File(context.getFilesDir(), APP_MODEL_DIR), LLM_TOKENIZER_FILE);
        return appTokenizerFile.getAbsolutePath();
    }

    // Check if model needs to be downloaded
    public static boolean needsModelDownload(Context context) {
        if (ModelUtils.getPreferredBackend() == "mtk") {
            // First check if model exists in legacy location
            boolean needDownload = false;
            // TBD, should add downloading flag here to avoid download accident
            for(int i=0; i< MTK_NPU_LLM_DOWNLOAD_FILES.length; i++){
                File file = new File(getMTKModelPath(context), MTK_NPU_LLM_DOWNLOAD_FILES[i].fileName);
                if(!file.exists()){
                    needDownload = true;
                }
            }            
            return needDownload;
        }
        else {
            String modelFileName = getAppropriateModelFile(context);
            
            // First check if model exists in legacy location
            File legacyModelFile = new File(LLAMA_MODEL_DIR, modelFileName);
            if (legacyModelFile.exists() && legacyModelFile.length() > 0) {
                return false;
            }

            // Then check app's private storage
            File appModelFile = new File(new File(context.getFilesDir(), APP_MODEL_DIR), modelFileName);
            return !appModelFile.exists() || appModelFile.length() == 0;
        }
    }

    // Get the current effective model path (used for sequence length calculations)
    private static String getCurrentModelPath(Context context) {
        return isModelInLegacyLocation() ? 
            new File(LLAMA_MODEL_DIR, BREEZE_MODEL_FILE).getAbsolutePath() :
            new File(new File(context.getFilesDir(), APP_MODEL_DIR), BREEZE_MODEL_FILE).getAbsolutePath();
    }

    // LLM Sequence Length Constants - these should be calculated based on the current model path
    public static int getLLMMaxSeqLength(Context context) {
        return getCurrentModelPath(context).contains("2048") ? 512 : 128;
    }

    public static int getLLMMinOutputLength(Context context) {
        return getCurrentModelPath(context).contains("2048") ? 512 : 32;
    }

    public static int getLLMMaxInputLength(Context context) {
        return getLLMMaxSeqLength(context) - getLLMMinOutputLength(context);
    }
    
    // Get the available RAM in GB
    public static long getAvailableRamGB(Context context) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        
        // Convert total memory from bytes to GB
        return memoryInfo.totalMem / (1024 * 1024 * 1024);
    }
    
    // Check if device has enough RAM for large model
    public static boolean canUseLargeModel(Context context) {
        return getAvailableRamGB(context) >= LARGE_MODEL_MIN_RAM_GB;
    }
    
    // Get the appropriate model file based on user preference and RAM constraints
    public static String getAppropriateModelFile(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String modelSizePreference = prefs.getString(KEY_MODEL_SIZE_PREFERENCE, MODEL_SIZE_AUTO);
        
        // For auto preference, choose based on available RAM
        if (modelSizePreference.equals(MODEL_SIZE_AUTO)) {
            return canUseLargeModel(context) ? LARGE_LLM_MODEL_FILE : SMALL_LLM_MODEL_FILE;
        }
        
        // For explicit preferences, respect the user's choice between Breeze variants
        if (modelSizePreference.equals(MODEL_SIZE_LARGE)) {
            return LARGE_LLM_MODEL_FILE; // Breeze high performance variant
        } else {
            return SMALL_LLM_MODEL_FILE; // Breeze small variant
        }
    }
    
    // LLM Response Messages
    public static final String LLM_ERROR_RESPONSE = "[!!!] LLM engine backend failed";
    public static final String LLM_DEFAULT_ERROR_RESPONSE = "I apologize, but I encountered an error generating a response. Please try again.";
    public static final String LLM_EMPTY_RESPONSE_ERROR = "I apologize, but I couldn't generate a proper response. Please try rephrasing your question.";
    public static final String LLM_INPUT_TOO_LONG_ERROR = "I apologize, but your input is too long. Please try breaking it into smaller parts.";
    public static final String LLM_INVALID_TOKEN_ERROR = "I apologize, but I was unable to generate a valid response. This might be due to the complexity of the question or current model limitations. Please try rephrasing your question.";
    
    // LLM Configuration
    public static final float LLM_TEMPERATURE = 0.2f;
    
    // When false: Send button always shows send icon and only sends messages
    // When true: Send button toggles between send and audio chat mode
    public static final boolean AUDIO_CHAT_ENABLED = false;

    // Conversation History Constants
    public static final int CONVERSATION_HISTORY_LOOKBACK = BREEZE_MODEL_FILE.contains("2048") ? 1 : 1;

    // Activity Request Codes
    public static final int PERMISSION_REQUEST_CODE = 123;
    public static final int PICK_IMAGE_REQUEST = 1;
    public static final int CAPTURE_IMAGE_REQUEST = 2;
    public static final int PICK_FILE_REQUEST = 3;
    public static final int REQUEST_CODE_DOWNLOAD_ACTIVITY = 4;

    // UI Constants
    public static final float ENABLED_ALPHA = 1.0f;
    public static final float DISABLED_ALPHA = 0.3f;

    public static final int TAPS_TO_SHOW_MAIN = 7;
    public static final long TAP_TIMEOUT_MS = 3000;
    public static final int INIT_DELAY_MS = 1000;

    // Activity Tags
    public static final String CHAT_ACTIVITY_TAG = "ChatActivity";
    public static final String MAIN_ACTIVITY_TAG = "MainActivity";
    public static final String AUDIO_CHAT_ACTIVITY_TAG = "AudioChatActivity";



    // HTTP Headers
    public static final String[][] DOWNLOAD_HEADERS = {
        {"User-Agent", "Mozilla/5.0 (Android) BreezeApp"},
        {"Accept", "*/*"},
        {"Connection", "keep-alive"}
    };

    // Logging control for downloads
    public static final boolean ENABLE_DOWNLOAD_VERBOSE_LOGGING = false; // Set to true for debug builds, false for release
    
    // File size units
    public static final String[] FILE_SIZE_UNITS = { "B", "KB", "MB", "GB", "TB" };
    
    // Optimize buffer size for large files (8MB buffer)
    public static final int MODEL_DOWNLOAD_BUFFER_SIZE = 8 * 1024 * 1024;
    
    // More frequent progress updates for better UX
    public static final int MODEL_DOWNLOAD_PROGRESS_UPDATE_INTERVAL = 1;
    
    // Increase timeout for large files (30 minutes)
    public static final long MODEL_DOWNLOAD_TIMEOUT_MS = 1800000;
    
    // Required free space (8GB)
    public static final long MODEL_DOWNLOAD_MIN_SPACE_MB = 8192;
    
    // Disable parallel downloads since servers don't support it well
    public static final boolean MODEL_DOWNLOAD_PARALLEL = false;
    
    // Temporary extension for partial downloads
    public static final String MODEL_DOWNLOAD_TEMP_EXTENSION = ".part";

    // ## Feature Flags
    // Show the image selection button
    public static final boolean IMAGE_ENABLED = true;
    // Show the speech-to-text and text-to-speech buttons
    public static final boolean SPEECH_ENABLED = true;
    // Enable expanding the send button when typing
    public static final boolean EXPANDED_INPUT_ENABLED = true;
    // Enable downloading models
    public static final boolean DOWNLOAD_ENABLED = true;

    // Check if MTK backend is truly available
    public static boolean isMTKBackendAvailable() {
        return MTK_BACKEND_ENABLED && MTK_BACKEND_AVAILABLE && 
               com.mtkresearch.breeze_app.service.LLMEngineService.isMTKBackendAvailable();
    }
    
    // Safely get a working executor or create one if needed
    public static java.util.concurrent.ExecutorService ensureExecutor(
            java.util.concurrent.ExecutorService executor, String logTag) {
        if (executor == null || executor.isShutdown()) {
            android.util.Log.w(logTag, "Executor null or shutdown, creating new one");
            return java.util.concurrent.Executors.newSingleThreadExecutor();
        }
        return executor;
    }
    public static String getMTKModelPath(Context context) {
        if (context != null) {
            File mtkNpuDir = new File(new File(context.getFilesDir(), APP_MODEL_DIR), MTK_NPU_MODEL_DIR);
            return mtkNpuDir.getAbsolutePath();
        }
        return "";
    }

    // Get MTK config path - use downloaded config when available
    public static String getMtkConfigPath(Context context) {
        if (context != null) {
            // First check if we have a downloaded config file in the MTK NPU model directory
            File configFile = new File(getMTKModelPath(context), MTK_NPU_MODEL_CONFIG_FILE);
            
            if (configFile.exists() && configFile.length() > 0) {
                Log.d(TAG, "Using downloaded MTK config file: " + configFile.getAbsolutePath());
                return configFile.getAbsolutePath();
            }
        }
        
        // Fall back to default path if no downloaded config
        Log.d(TAG, "Using default MTK config path: " + MTK_CONFIG_PATH);
        return MTK_CONFIG_PATH;
    }

    // MTK NPU Model Directory and files
    public static final String MTK_NPU_MODEL_DIR = "Breeze2-3B-Instruct-mobile-npu";
    public static final String MTK_NPU_MODEL_CONFIG_FILE = "config_breezetiny_3b_instruct.yaml";
    
    // MTK NPU Model Download URLs
    public static final String MTK_NPU_MODEL_BASE_URL = "https://huggingface.co/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/";
    // Mirror URL using HF Mirror
    public static final String MTK_NPU_MODEL_MIRROR_URL = "https://hf-mirror.com/MediaTek-Research/Breeze2-3B-Instruct-mobile-npu/resolve/main/";
    // Direct download URLs for MTK NPU files (fallback)
    public static final String MTK_NPU_CONFIG_DIRECT_URL = "https://raw.githubusercontent.com/MediaTek-Research/briztk/main/config_breezetiny_3b_instruct.yaml";
    
    // Fallback direct URLs for each file type - used when Hugging Face is unreachable
    public static final String[] MTK_NPU_FALLBACK_URLS = {
        "https://raw.githubusercontent.com/MediaTek-Research/briztk/main/samples/config_breezetiny_3b_instruct.yaml",
        "https://fastly.jsdelivr.net/gh/MediaTek-Research/briztk@main/samples/config_breezetiny_3b_instruct.yaml",
        "https://cdn.jsdelivr.net/gh/MediaTek-Research/briztk@main/samples/config_breezetiny_3b_instruct.yaml"
    };
    
    // Helper method to generate multiple download URLs for a file
    private static String getMultipleUrls(String filename) {
        String urls = MTK_NPU_MODEL_BASE_URL + filename + ";" + 
               MTK_NPU_MODEL_MIRROR_URL + filename;
        
        // For config file, add the direct GitHub URLs
        if (filename.equals(MTK_NPU_MODEL_CONFIG_FILE)) {
            for (String fallbackUrl : MTK_NPU_FALLBACK_URLS) {
                urls += ";" + fallbackUrl;
            }
        }
        
        return urls;
    }
} 