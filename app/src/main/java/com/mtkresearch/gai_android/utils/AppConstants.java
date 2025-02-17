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
    
    // Service Enable Flags
    public static final boolean LLM_ENABLED = true;  // LLM is essential
    public static final boolean VLM_ENABLED = false; // VLM is experimental
    public static final boolean ASR_ENABLED = false; // ASR requires permission
    public static final boolean TTS_ENABLED = true;  // TTS is stable
    
    // When false: Send button always shows send icon and only sends messages
    // When true: Send button toggles between send and audio chat mode
    public static final boolean AUDIO_CHAT_ENABLED = false;

    // Model Files and Paths
    public static final String LLAMA_MODEL_FILE = "llama3_2.pte";
    public static final String BREEZE_MODEL_FILE = "Breeze-Tiny-Instruct-v0_1.pte";
    public static final String LLAMA_MODEL_DIR = "/data/local/tmp/llama/";
    public static final String MODEL_PATH = LLAMA_MODEL_DIR + BREEZE_MODEL_FILE; // Default to Breeze model

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