package com.mtkresearch.gai_android.utils;

public class AppConstants {
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
    
    // Backend Enable Flags
    public static final boolean MTK_BACKEND_ENABLED = false;  // Set to true to enable MTK backend
    public static volatile boolean MTK_BACKEND_AVAILABLE = false;  // Runtime state of MTK backend availability
    
    // Backend Constants
    public static final String BACKEND_CPU = "cpu";
    public static final String BACKEND_MTK = "mtk";
    public static final String BACKEND_DEFAULT = BACKEND_MTK;
    
    // MTK Backend Constants
    public static final int MAX_MTK_INIT_ATTEMPTS = 5;
    public static final long MTK_CLEANUP_TIMEOUT_MS = 5000;  // 5 seconds timeout for cleanup
    public static final long MTK_NATIVE_OP_TIMEOUT_MS = 2000;  // 2 seconds timeout for native operations
    
    // LLM Service Constants
    public static final long LLM_INIT_TIMEOUT_MS = 120000;  // 2 minutes
    public static final long LLM_GENERATION_TIMEOUT_MS = 60000;  // 1 minute
    public static final long LLM_NATIVE_OP_TIMEOUT_MS = 2000;  // 2 seconds
    public static final long LLM_CLEANUP_TIMEOUT_MS = 5000;  // 5 seconds
    public static final int LLM_MAX_MTK_INIT_ATTEMPTS = 3;
    
    // LLM Response Messages
    public static final String LLM_ERROR_RESPONSE = "[!!!] LLM engine backend failed";
    public static final String LLM_DEFAULT_ERROR_RESPONSE = "I apologize, but I encountered an error generating a response. Please try again.";
    public static final String LLM_EMPTY_RESPONSE_ERROR = "I apologize, but I couldn't generate a proper response. Please try rephrasing your question.";
    
    // LLM Configuration
    public static final String LLM_TOKENIZER_PATH = "/data/local/tmp/llama/tokenizer.bin";
    public static final float LLM_TEMPERATURE = 0.8f;
    
    // When false: Send button always shows send icon and only sends messages
    // When true: Send button toggles between send and audio chat mode
    public static final boolean AUDIO_CHAT_ENABLED = false;

    // Model Files and Paths
    public static final String LLAMA_MODEL_FILE = "llama3_2.pte";
    public static final String BREEZE_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1.pte";
    public static final String LLAMA_MODEL_DIR = "/data/local/tmp/llama/";
    public static final String MODEL_PATH = LLAMA_MODEL_DIR + LLAMA_MODEL_FILE; // Default to Breeze model

    // Activity Request Codes
    public static final int PERMISSION_REQUEST_CODE = 123;
    public static final int PICK_IMAGE_REQUEST = 1;
    public static final int CAPTURE_IMAGE_REQUEST = 2;
    public static final int PICK_FILE_REQUEST = 3;

    // UI Constants
    public static final float ENABLED_ALPHA = 1.0f;
    public static final float DISABLED_ALPHA = 0.3f;
    public static final int CONVERSATION_HISTORY_LOOKBACK = 2;
    public static final int TAPS_TO_SHOW_MAIN = 7;
    public static final long TAP_TIMEOUT_MS = 3000;
    public static final int INIT_DELAY_MS = 1000;

    // Activity Tags
    public static final String CHAT_ACTIVITY_TAG = "ChatActivity";
    public static final String MAIN_ACTIVITY_TAG = "MainActivity";
    public static final String AUDIO_CHAT_ACTIVITY_TAG = "AudioChatActivity";
} 