package com.mtkresearch.breezeapp.utils;

import android.content.Context;
import java.io.File;
import android.util.Log;
import java.io.IOException;
import android.app.ActivityManager;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.preference.PreferenceManager;

public class AppConstants {
    private static final String TAG = "AppConstants";
    // Preference Keys
    public static final String KEY_HISTORY_LOOKBACK = "history_lookback";
    public static final String KEY_SEQUENCE_LENGTH = "sequence_length";
    public static final String KEY_DEFAULT_MODEL = "default_model";
    public static final String KEY_FIRST_LAUNCH = "first_launch";
    public static final String KEY_PREFERRED_BACKEND = "preferred_backend";

    // Preference keys from SettingsFragment
    // Keys without suffix store integer values for UI controls (0-100)
    // Keys with "_value" suffix store actual float values (0.0-1.0) used by the model
    public static final String KEY_TEMPERATURE = "temperature";
    public static final String KEY_TEMPERATURE_VALUE = "temperature_value"; // Actual float value used by the model
    public static final String KEY_MAX_TOKEN_VALUE = "max_token_value";
    public static final String KEY_REPETITION_PENALTY = "repetition_penalty";
    public static final String KEY_REPETITION_PENALTY_VALUE = "repetition_penalty_value"; // Actual float value used by the model
    public static final String KEY_FREQUENCY_PENALTY = "frequency_penalty";
    public static final String KEY_FREQUENCY_PENALTY_VALUE = "frequency_penalty_value"; // Actual float value used by the model
    public static final String KEY_TOP_K_VALUE = "top_k_value";
    public static final String KEY_TOP_P = "top_p";
    public static final String KEY_TOP_P_VALUE = "top_p_value"; // Actual float value used by the model


    public static final String DEFAULT_BACKEND = "cpu";  // Default to CPU backend
    public static final String DEFAULT_LLM_MODEL = "breeze2-3b-spin-250501-cpu";

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

    public static final int LLM_LOAD_TIMEOUT_MS = 300000;
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
    public static final long MIN_RAM_REQUIRED_GB = 5; // Minimum RAM for the app to run
    public static final long LARGE_MODEL_MIN_RAM_GB =12; // Minimum RAM for large model
    
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
        public final String modelId;  // Add modelId field
        
        public DownloadFileInfo(String url, String fileName, String displayName, String fileType, long fileSize, String modelId) {
            this.url = url;
            this.fileName = fileName;
            this.displayName = displayName;
            this.fileType = fileType;
            this.fileSize = fileSize;
            this.modelId = modelId;
        }
        
        public DownloadFileInfo(String url, String fileName, String displayName, String fileType, long fileSize) {
            this(url, fileName, displayName, fileType, fileSize, null);
        }
    }

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
        // Check if downloadedModelList.json exists and has content
        File downloadedListFile = new File(context.getFilesDir(), "downloadedModelList.json");
        if (!downloadedListFile.exists() || downloadedListFile.length() == 0) {
            return true;
        }
        
        try {
            // Read and parse the file
            JSONObject json = new JSONObject(new String(java.nio.file.Files.readAllBytes(downloadedListFile.toPath())));
            JSONArray models = json.getJSONArray("models");
            return models.length() == 0;  // Need download if no models in list
        } catch (Exception e) {
            Log.e("AppConstants", "Error reading downloadedModelList.json", e);
            return true;  // Need download if can't read file
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
        return memoryInfo.availMem / (1024 * 1024 * 1024);
    }
    
    // Check if device has enough RAM for large model
    public static boolean canUseLargeModel(Context context) {
        return getAvailableRamGB(context) >= LARGE_MODEL_MIN_RAM_GB;
    }
    
    // Get the appropriate model file based on user preference and RAM constraints
    public static String getAppropriateModelFile(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
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
    
    // LLM Configuration
    public static final float DEFAULT_LLM_TEMPERATURE = 0.2f;
    public static final int DEFAULT_LLM_MAX_TOKEN = 256;
    public static final float DEFAULT_LLM_REPETITION_PENALTY = 1.2f;
    public static final float DEFAULT_LLM_FREQUENCY_PENALTY = 1.2f;
    public static final int DEFAULT_LLM_TOP_K = 5;
    public static final float DEFAULT_LLM_TOP_P = 0.9f;

    // LLM UI/Preference Min/Max/Step
    public static final int TEMPERATURE_MIN = 0;
    public static final int TEMPERATURE_MAX = 100;
    public static final int TEMPERATURE_SCALE = 100;
    public static final int TEMPERATURE_STEPS = 10;
    public static final int TEMPERATURE_STEP_SIZE = TEMPERATURE_SCALE / TEMPERATURE_STEPS;

    public static final int MAX_TOKEN_MIN = 128;
    public static final int MAX_TOKEN_MAX = 2048;
    public static final int MAX_TOKEN_STEP_SIZE = 128;

    
    public static final int REPETITION_PENALTY_MIN = 100;
    public static final int REPETITION_PENALTY_MAX = 200;
    public static final int REPETITION_PENALTY_SCALE = 100;
    public static final int REPETITION_PENALTY_STEPS = 10;
    public static final int REPETITION_PENALTY_STEP_SIZE = REPETITION_PENALTY_SCALE / REPETITION_PENALTY_STEPS;

    public static final int FREQUENCY_PENALTY_MIN = 100;
    public static final int FREQUENCY_PENALTY_MAX = 200;
    public static final int FREQUENCY_PENALTY_SCALE = 100;
    public static final int FREQUENCY_PENALTY_STEPS = 10;
    public static final int FREQUENCY_PENALTY_STEP_SIZE = FREQUENCY_PENALTY_SCALE / FREQUENCY_PENALTY_STEPS;

    public static final int TOP_P_MIN = 0;
    public static final int TOP_P_MAX = 100;
    public static final int TOP_P_SCALE = 100;
    public static final int TOP_P_STEPS = 10;
    public static final int TOP_P_STEP_SIZE = TOP_P_SCALE / TOP_P_STEPS;

    // Default values as integers (for SeekBar)
    public static final int DEFAULT_TEMPERATURE_INT = (int)(DEFAULT_LLM_TEMPERATURE * TEMPERATURE_SCALE);
    public static final int DEFAULT_REPETITION_PENALTY_INT = (int)(DEFAULT_LLM_REPETITION_PENALTY * REPETITION_PENALTY_SCALE);
    public static final int DEFAULT_FREQUENCY_PENALTY_INT = (int)(DEFAULT_LLM_FREQUENCY_PENALTY * FREQUENCY_PENALTY_SCALE);
    public static final int DEFAULT_TOP_P_INT = (int)(DEFAULT_LLM_TOP_P * TOP_P_SCALE);
    
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
               com.mtkresearch.breezeapp.service.LLMEngineService.isMTKBackendAvailable();
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

    // LLM 參數封裝類別
    public static class LLMPreferenceField {
        public final String key;
        public final int min;
        public final int max;
        public final int defaultValue;
        public LLMPreferenceField(String key, int min, int max, int defaultValue) {
            this.key = key;
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }
    }

    // LLM 參數欄位定義
    public static final LLMPreferenceField FIELD_TEMPERATURE = new LLMPreferenceField(
            KEY_TEMPERATURE, TEMPERATURE_MIN, TEMPERATURE_MAX, DEFAULT_TEMPERATURE_INT);
    public static final LLMPreferenceField FIELD_MAX_TOKEN = new LLMPreferenceField(
            KEY_MAX_TOKEN_VALUE, MAX_TOKEN_MIN, MAX_TOKEN_MAX, DEFAULT_LLM_MAX_TOKEN);
    public static final LLMPreferenceField FIELD_REPETITION_PENALTY = new LLMPreferenceField(
            KEY_REPETITION_PENALTY, REPETITION_PENALTY_MIN, REPETITION_PENALTY_MAX, DEFAULT_REPETITION_PENALTY_INT);
    public static final LLMPreferenceField FIELD_FREQUENCY_PENALTY = new LLMPreferenceField(
            KEY_FREQUENCY_PENALTY, FREQUENCY_PENALTY_MIN, FREQUENCY_PENALTY_MAX, DEFAULT_FREQUENCY_PENALTY_INT);
    public static final LLMPreferenceField FIELD_TOP_P = new LLMPreferenceField(
            KEY_TOP_P, TOP_P_MIN, TOP_P_MAX, DEFAULT_TOP_P_INT);
    // top_k 是 string 輸入，這裡不包
} 