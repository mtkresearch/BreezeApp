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

    public static final String DEFAULT_LLM_MODEL = "Llama3_2-3b-4096-spin-250605-cpu";

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
    public static final int MAX_MTK_INIT_ATTEMPTS = 5;       // Maximum attempts to initialize MTK backend
    public static final long MTK_CLEANUP_TIMEOUT_MS = 5000;   // 5 seconds timeout for cleanup

    
    // LLM Service Constants
    public static final long LLM_INIT_TIMEOUT_MS = 300000;  // 5 minutes for initialization

    public static final String DEFAULT_SYSTEM_PROMPT = "You are a language model with knowledge of Taiwan. Please answer the following questions in Traditional Chinese or English.";

    public static final int LLM_LOAD_TIMEOUT_MS = 300000;
    // Model Files and Paths
    public static final String BREEZE_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048.pte";
    
    // LLM Model Size Options
    public static final String LARGE_LLM_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048.pte";
    public static final String SMALL_LLM_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1-2048-spin.pte";
    public static final String LARGE_LLM_MODEL_DISPLAY_NAME = "Breeze2";
    public static final String SMALL_LLM_MODEL_DISPLAY_NAME = "Breeze2-spinQuant";
    
    // RAM Requirements
    public static final long MIN_RAM_REQUIRED_GB = 5; // Minimum RAM for the app to run
    public static final long LARGE_MODEL_MIN_RAM_GB =7; // Minimum RAM for large model
    
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
    
    // Model Download Constants
    public static final String MODEL_BASE_URL = "https://huggingface.co/MediaTek-Research/Breeze-Tiny-Instruct-v0_1-mobile/resolve/main/";
    
    // Model Download URLs - defined before usage in LLM_DOWNLOAD_FILES
    public static final String[] MODEL_DOWNLOAD_URLS = {
        // Tokenizer - small file, use regular URL
        MODEL_BASE_URL + "tokenizer.bin?download=true",
        // Model file - try multiple reliable sources
        MODEL_BASE_URL + BREEZE_MODEL_FILE + "?download=true"
    };
    
    // Download status constants
    public static final int DOWNLOAD_STATUS_PENDING = 0;
    public static final int DOWNLOAD_STATUS_IN_PROGRESS = 1;
    public static final int DOWNLOAD_STATUS_PAUSED = 2;
    public static final int DOWNLOAD_STATUS_COMPLETED = 3;
    public static final int DOWNLOAD_STATUS_FAILED = 4;
    
    // File type constants
    public static final String FILE_TYPE_LLM = "LLM Model";
    
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


    // Check if TTS models need to be downloaded
    public static boolean needsTTSModelDownload(Context context) {
        return !hasTTSModels(context);
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

    // LLM Sequence Length Constants - these should be calculated based on the current model path. It stands for token length not bytes
    public static int getLLMMaxSeqLength(Context context) {
        return 4096 ; // getCurrentModelPath(context).contains("2048") ? 2048 : 128;
    }

    public static int getLLMMinOutputLength(Context context) {
        return 1024 ; // getCurrentModelPath(context).contains("2048") ? 512 : 32;
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
    public static final int MAX_TOKEN_MAX = 4096;
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

    // Conversation History Constants
    public static final int CONVERSATION_HISTORY_LOOKBACK = 500; // should be ignore and replace by history window length //BREEZE_MODEL_FILE.contains("2048") ? 1 : 1;

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
    
    // Increase timeout for large files (30 minutes)
    public static final long MODEL_DOWNLOAD_TIMEOUT_MS = 1800000;
    
    // Temporary extension for partial downloads
    public static final String MODEL_DOWNLOAD_TEMP_EXTENSION = ".part";

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