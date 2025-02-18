package com.mtkresearch.gai_android;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.View;

import com.mtkresearch.gai_android.ChatActivity;
import com.mtkresearch.gai_android.R;
import com.mtkresearch.gai_android.databinding.ActivityMainBinding;
import com.mtkresearch.gai_android.service.ASREngineService;
import com.mtkresearch.gai_android.service.BaseEngineService;
import com.mtkresearch.gai_android.service.LLMEngineService;
import com.mtkresearch.gai_android.service.TTSEngineService;
import com.mtkresearch.gai_android.service.VLMEngineService;
import com.mtkresearch.gai_android.utils.IntroDialog;
import com.mtkresearch.gai_android.utils.NativeLibraryLoader;
import com.mtkresearch.gai_android.utils.PromptManager;
import com.mtkresearch.gai_android.utils.AppConstants;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import android.content.SharedPreferences;
import android.os.Looper;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = AppConstants.MAIN_ACTIVITY_TAG;
    private static final int PERMISSION_REQUEST_CODE = AppConstants.PERMISSION_REQUEST_CODE;
    private ActivityMainBinding binding;
    
    // Services
    private LLMEngineService llmService;
    private VLMEngineService vlmService;
    private ASREngineService asrService;
    private TTSEngineService ttsService;
    
    // Service enable flags
    private boolean llmEnabled = false;
    private boolean vlmEnabled = false;
    private boolean asrEnabled = false;
    private boolean ttsEnabled = false;

    private static final String LLAMA_MODEL_PATH = AppConstants.LLAMA_MODEL_DIR;
    
    // Model lists for each service
    private String[] llmModels;
    private String[] vlmModels;
    
    // Selected model for each service
    private String selectedLlmModel = null;
    private String selectedVlmModel = null;

    private final ServiceConnection llmConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            LLMEngineService.LocalBinder binder = (LLMEngineService.LocalBinder) service;
            llmService = binder.getService();
            initializeService(llmService, binding.llmStatusIndicator);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            llmService = null;
            updateEngineStatus(binding.llmStatusIndicator, EngineStatus.ERROR);
        }
    };

    private final ServiceConnection vlmConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            VLMEngineService.LocalBinder binder = (VLMEngineService.LocalBinder) service;
            vlmService = binder.getService();
            initializeService(vlmService, binding.vlmStatusIndicator);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            vlmService = null;
            updateEngineStatus(binding.vlmStatusIndicator, EngineStatus.ERROR);
        }
    };

    private final ServiceConnection asrConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ASREngineService.LocalBinder binder = (ASREngineService.LocalBinder) service;
            asrService = binder.getService();
            initializeService(asrService, binding.asrStatusIndicator);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            asrService = null;
            updateEngineStatus(binding.asrStatusIndicator, EngineStatus.ERROR);
        }
    };

    private final ServiceConnection ttsConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TTSEngineService.LocalBinder binder = (TTSEngineService.LocalBinder) service;
            ttsService = binder.getService();
            initializeService(ttsService, binding.ttsStatusIndicator);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ttsService = null;
            updateEngineStatus(binding.ttsStatusIndicator, EngineStatus.ERROR);
        }
    };

    private EditText historyLookbackInput;
    private EditText sequenceLengthInput;
    private EditText temperatureInput;
    private Spinner backendSpinner;
    private ArrayAdapter<String> backendAdapter;
    private SharedPreferences settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Load native libraries first
        try {
            NativeLibraryLoader.loadLibraries();
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "Failed to load native libraries", e);
            Toast.makeText(this, "Failed to initialize: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Show intro dialog immediately, don't wait for services
        new Handler(getMainLooper()).post(this::showIntroDialog);

        // Continue with normal initialization in background
        new Handler(getMainLooper()).post(() -> {
            scanForModels();
            setupUI();
            setupModelSpinners();
            setupServiceSwitches();
            displayDeviceInfo();
            initializeStatusIndicators();

            settings = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);
            PromptManager.initialize(this);
            setupSettings();
            setupBackendSpinner();
        });
    }

    private void scanForModels() {
        File modelDir = new File(AppConstants.LLAMA_MODEL_DIR);
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            Log.e(TAG, "Model directory not found: " + AppConstants.LLAMA_MODEL_DIR);
            Toast.makeText(this, "Model directory not found", Toast.LENGTH_LONG).show();
            return;
        }

        // Create list to store LLM models
        List<String> llmList = new ArrayList<>();

        // Check for Llama3.2 model
        File llamaModel = new File(modelDir, AppConstants.LLAMA_MODEL_FILE);
        if (llamaModel.exists() && llamaModel.isFile()) {
            llmList.add(AppConstants.LLAMA_MODEL_FILE);
        }

        // Check for Breeze2 model
        File breezeModel = new File(modelDir, AppConstants.BREEZE_MODEL_FILE);
        if (breezeModel.exists() && breezeModel.isFile()) {
            llmList.add(AppConstants.BREEZE_MODEL_FILE);
        }

        // Convert list to array
        llmModels = llmList.toArray(new String[0]);

        // Log found models
        Log.d(TAG, "Found LLM models: " + Arrays.toString(llmModels));
        
        // Update UI based on available models
        runOnUiThread(() -> {
            if (llmModels.length == 0) {
                Toast.makeText(this, "No LLM models found. Please ensure either " + 
                    AppConstants.LLAMA_MODEL_FILE + " or " + 
                    AppConstants.BREEZE_MODEL_FILE + " model is available.", Toast.LENGTH_LONG).show();
            }
            setupModelSpinners();
        });
    }

    private void setupUI() {
        setupChatButton();
        initializeStatusIndicators();
    }

    private void setupChatButton() {
        binding.startChatButton.setEnabled(false);
        binding.startChatButton.setBackgroundTintList(ColorStateList.valueOf(
            getResources().getColor(R.color.surface, getTheme())));
        binding.startChatButton.setTextColor(
            getResources().getColor(R.color.text_secondary, getTheme()));
            
        binding.startChatButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            startActivity(intent);
        });
    }

    private void setupModelSpinners() {
        // Setup LLM model spinner if models are available
        if (llmModels != null && llmModels.length > 0) {
            ArrayAdapter<String> llmAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_dropdown_item, llmModels);
            binding.llmModelSpinner.setAdapter(llmAdapter);
            binding.llmModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedLlmModel = llmModels[position];
                    // If service is already running, restart it with new model
                    if (llmEnabled && llmService != null) {
                        restartService(LLMEngineService.class, llmConnection, binding.llmStatusIndicator);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedLlmModel = null;
                }
            });
        } else {
            binding.llmModelSpinner.setEnabled(false);
            binding.llmSwitch.setEnabled(false);
        }

        // Setup VLM model spinner if models are available
        if (vlmModels != null && vlmModels.length > 0) {
            ArrayAdapter<String> vlmAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_dropdown_item, vlmModels);
            binding.vlmModelSpinner.setAdapter(vlmAdapter);
            binding.vlmModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedVlmModel = vlmModels[position];
                    if (vlmEnabled && vlmService != null) {
                        restartService(VLMEngineService.class, vlmConnection, binding.vlmStatusIndicator);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedVlmModel = null;
                }
            });
        } else {
            binding.vlmModelSpinner.setEnabled(false);
            binding.vlmSwitch.setEnabled(false);
        }

        // Disable model spinners for ASR and TTS as they don't use model selection
        binding.asrModelSpinner.setEnabled(false);
        binding.ttsModelSpinner.setEnabled(false);
    }

    private void setupServiceSwitches() {
        binding.llmSwitch.setChecked(llmEnabled);
        binding.vlmSwitch.setChecked(vlmEnabled);
        binding.asrSwitch.setChecked(asrEnabled);
        binding.ttsSwitch.setChecked(ttsEnabled);

        binding.llmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && selectedLlmModel == null) {
                Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
                buttonView.setChecked(false);
                return;
            }
            
            llmEnabled = isChecked;
            if (isChecked) {
                startAndBindService(LLMEngineService.class, llmConnection, null);
                updateEngineStatus(binding.llmStatusIndicator, EngineStatus.INITIALIZING);
            } else if (llmService != null) {
                safeUnbindService(llmConnection);
                stopService(new Intent(this, LLMEngineService.class));
                llmService = null;
                updateEngineStatus(binding.llmStatusIndicator, EngineStatus.ERROR);
            }
            checkAllServicesReady();
        });

        binding.vlmSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && selectedVlmModel == null) {
                Toast.makeText(this, "Please select a model first", Toast.LENGTH_SHORT).show();
                buttonView.setChecked(false);
                return;
            }
            
            vlmEnabled = isChecked;
            if (isChecked) {
                startAndBindService(VLMEngineService.class, vlmConnection, null);
                updateEngineStatus(binding.vlmStatusIndicator, EngineStatus.INITIALIZING);
            } else if (vlmService != null) {
                safeUnbindService(vlmConnection);
                stopService(new Intent(this, VLMEngineService.class));
                vlmService = null;
                updateEngineStatus(binding.vlmStatusIndicator, EngineStatus.ERROR);
            }
            checkAllServicesReady();
        });

        binding.asrSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            asrEnabled = isChecked;
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.RECORD_AUDIO},
                        PERMISSION_REQUEST_CODE);
                    binding.asrSwitch.setChecked(false);
                    return;
                }
                startAndBindService(ASREngineService.class, asrConnection, null);
                updateEngineStatus(binding.asrStatusIndicator, EngineStatus.INITIALIZING);
            } else if (asrService != null) {
                safeUnbindService(asrConnection);
                stopService(new Intent(this, ASREngineService.class));
                asrService = null;
                updateEngineStatus(binding.asrStatusIndicator, EngineStatus.ERROR);
            }
            checkAllServicesReady();
        });

        binding.ttsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ttsEnabled = isChecked;
            if (isChecked) {
                startAndBindService(TTSEngineService.class, ttsConnection, null);
                updateEngineStatus(binding.ttsStatusIndicator, EngineStatus.INITIALIZING);
            } else if (ttsService != null) {
                safeUnbindService(ttsConnection);
                stopService(new Intent(this, TTSEngineService.class));
                ttsService = null;
                updateEngineStatus(binding.ttsStatusIndicator, EngineStatus.ERROR);
            }
            checkAllServicesReady();
        });
    }

    private void initializeStatusIndicators() {
        updateEngineStatus(binding.llmStatusIndicator, EngineStatus.ERROR);
        updateEngineStatus(binding.vlmStatusIndicator, EngineStatus.ERROR);
        updateEngineStatus(binding.asrStatusIndicator, EngineStatus.ERROR);
        updateEngineStatus(binding.ttsStatusIndicator, EngineStatus.ERROR);
    }

    private void displayDeviceInfo() {
        binding.deviceBrand.setText("Brand: " + Build.MANUFACTURER + " " + Build.MODEL);
        binding.deviceFirmware.setText("Firmware: " + Build.VERSION.RELEASE);
        binding.deviceChip.setText("Chipset: " + Build.HARDWARE);
        binding.deviceRam.setText(getMemoryInfo());
    }

    private String getMemoryInfo() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        activityManager.getMemoryInfo(memoryInfo);
        
        long totalMemory = memoryInfo.totalMem / (1024 * 1024); // Convert to MB
        long availableMemory = memoryInfo.availMem / (1024 * 1024);
        
        return String.format(Locale.getDefault(), "%d MB / %d MB", availableMemory, totalMemory);
    }

    private void startAndBindService(
            Class<? extends BaseEngineService> serviceClass, 
            ServiceConnection connection,
            Runnable onComplete) {
        Intent intent = new Intent(this, serviceClass);
        
        // Add model path to intent only for LLM and VLM services
        if (serviceClass == LLMEngineService.class && selectedLlmModel != null) {
            intent.putExtra("model_path", LLAMA_MODEL_PATH + selectedLlmModel);
            // Add backend preference and temperature for LLM service
            String savedBackend = settings.getString(AppConstants.KEY_PREFERRED_BACKEND, "cpu");
            float savedTemp = settings.getFloat(AppConstants.KEY_TEMPERATURE, 0.0f);
            intent.putExtra("preferred_backend", savedBackend);
            intent.putExtra("temperature", savedTemp);
            Log.d(TAG, "Starting LLM service with backend: " + savedBackend + ", temperature: " + savedTemp);
        } else if (serviceClass == VLMEngineService.class && selectedVlmModel != null) {
            intent.putExtra("model_path", LLAMA_MODEL_PATH + selectedVlmModel);
        }
        
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        
        if (onComplete != null) {
            new Handler().postDelayed(onComplete, 500);
        }
    }

    private void initializeService(BaseEngineService service, ImageView statusIndicator) {
        if (service == null) {
            updateEngineStatus(statusIndicator, EngineStatus.ERROR);
            return;
        }

        updateEngineStatus(statusIndicator, EngineStatus.INITIALIZING);
        service.initialize()
            .thenAccept(success -> {
                runOnUiThread(() -> {
                    EngineStatus status = success ? EngineStatus.READY : EngineStatus.ERROR;
                    updateEngineStatus(statusIndicator, status);
                    checkAllServicesReady();
                });
            })
            .exceptionally(throwable -> {
                Log.e(TAG, "Service initialization failed", throwable);
                runOnUiThread(() -> {
                    updateEngineStatus(statusIndicator, EngineStatus.ERROR);
                    checkAllServicesReady();
                });
                return null;
            });
    }
    
    private void updateEngineStatus(ImageView indicator, EngineStatus status) {
        runOnUiThread(() -> {
            int colorRes;
            switch (status) {
                case INITIALIZING:
                    colorRes = R.drawable.status_indicator_yellow;
                    break;
                case READY:
                    colorRes = R.drawable.status_indicator_green;
                    break;
                case ERROR:
                    colorRes = R.drawable.status_indicator_red;
                    break;
                default:
                    colorRes = R.drawable.status_indicator_red;
                    break;
            }
            indicator.setImageResource(colorRes);
        });
    }

    private void checkAllServicesReady() {
        // Check if at least one service is enabled and ready
        boolean atLeastOneServiceReady = 
            (llmEnabled && llmService != null && llmService.isReady()) ||
            (vlmEnabled && vlmService != null && vlmService.isReady()) ||
            (asrEnabled && asrService != null && asrService.isReady()) ||
            (ttsEnabled && ttsService != null && ttsService.isReady());

        // Check if any service is enabled but not ready
        boolean anyServiceInitializing = 
            (llmEnabled && (llmService == null || !llmService.isReady())) ||
            (vlmEnabled && (vlmService == null || !vlmService.isReady())) ||
            (asrEnabled && (asrService == null || !asrService.isReady())) ||
            (ttsEnabled && (ttsService == null || !ttsService.isReady()));

        runOnUiThread(() -> {
            binding.startChatButton.setEnabled(atLeastOneServiceReady);

            if (atLeastOneServiceReady) {
                binding.startChatButton.setBackgroundTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.primary, getTheme())));
                binding.startChatButton.setTextColor(
                    getResources().getColor(R.color.text_primary, getTheme()));
            } else {
                binding.startChatButton.setBackgroundTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.surface, getTheme())));
                binding.startChatButton.setTextColor(
                    getResources().getColor(R.color.text_secondary, getTheme()));
                
                // Show initialization status only if any service is enabled and initializing
                if (anyServiceInitializing) {
                    Toast.makeText(this, "Waiting for services to initialize", 
                        Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private enum EngineStatus {
        INITIALIZING,
        READY,
        ERROR
    }

    private void restartService(Class<? extends BaseEngineService> serviceClass, 
                              ServiceConnection connection,
                              ImageView statusIndicator) {
        // Unbind and stop current service
        safeUnbindService(connection);
        stopService(new Intent(this, serviceClass));
        
        // Update status to initializing
        updateEngineStatus(statusIndicator, EngineStatus.INITIALIZING);
        
        // Start new service with delay
        new Handler().postDelayed(() -> {
            startAndBindService(serviceClass, connection, null);
        }, 500);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing()) {
            // Only stop services if activity is actually finishing
            stopService(new Intent(this, LLMEngineService.class));
            stopService(new Intent(this, VLMEngineService.class));
            stopService(new Intent(this, ASREngineService.class));
            stopService(new Intent(this, TTSEngineService.class));
        }
        // Always unbind
        safeUnbindService(llmConnection);
        safeUnbindService(vlmConnection);
        safeUnbindService(asrConnection);
        safeUnbindService(ttsConnection);
    }

    private void safeUnbindService(ServiceConnection connection) {
        try {
            unbindService(connection);
        } catch (IllegalArgumentException e) {
            // Service might not be bound
            Log.w(TAG, "Service was not bound", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "RECORD_AUDIO permission granted");
                // Re-enable ASR switch if permission was granted
                binding.asrSwitch.setChecked(true);
                startAndBindService(ASREngineService.class, asrConnection, null);
            } else {
                Log.e(TAG, "RECORD_AUDIO permission denied");
                Toast.makeText(this, "Speech recognition requires audio permission", Toast.LENGTH_LONG).show();
                binding.asrSwitch.setChecked(false);
            }
        }
    }

    private void showIntroDialog() {
        Log.d(TAG, "Showing intro dialog");
        try {
            // Ensure we're on the main thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                new Handler(Looper.getMainLooper()).post(this::showIntroDialog);
                return;
            }

            // Check if activity is finishing
            if (isFinishing()) {
                Log.w(TAG, "Activity is finishing, skipping dialog");
                return;
            }

            IntroDialog dialog = new IntroDialog(this);
            dialog.setOnDismissListener(dialogInterface -> {
                Log.d(TAG, "Intro dialog dismissed");
            });
            dialog.show();
        } catch (Exception e) {
            Log.e(TAG, "Error showing intro dialog", e);
        }
    }

    private void setupSettings() {
        historyLookbackInput = binding.settingsContainer.findViewById(R.id.historyLookbackInput);
        sequenceLengthInput = binding.settingsContainer.findViewById(R.id.sequenceLengthInput);

        // Load saved values or use defaults
        int savedHistoryLookback = settings.getInt(AppConstants.KEY_HISTORY_LOOKBACK, PromptManager.DEFAULT_HISTORY_LOOKBACK);
        int savedSequenceLength = settings.getInt(AppConstants.KEY_SEQUENCE_LENGTH, PromptManager.MAX_SEQUENCE_LENGTH);

        historyLookbackInput.setText(String.valueOf(savedHistoryLookback));
        sequenceLengthInput.setText(String.valueOf(savedSequenceLength));

        // Add listeners to save changes
        historyLookbackInput.addTextChangedListener(new SettingsTextWatcher(AppConstants.KEY_HISTORY_LOOKBACK));
        sequenceLengthInput.addTextChangedListener(new SettingsTextWatcher(AppConstants.KEY_SEQUENCE_LENGTH));
    }

    private class SettingsTextWatcher implements TextWatcher {
        private final String key;

        SettingsTextWatcher(String key) {
            this.key = key;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            try {
                int value = s.length() > 0 ? Integer.parseInt(s.toString()) : 0;
                
                // Apply constraints based on the setting
                if (key.equals(AppConstants.KEY_HISTORY_LOOKBACK)) {
                    value = Math.max(1, Math.min(100, value)); // Limit between 1 and 100
                } else if (key.equals(AppConstants.KEY_SEQUENCE_LENGTH)) {
                    value = Math.max(128, Math.min(8192, value)); // Limit between 256 and 8192
                }
                
                // Save the value
                settings.edit().putInt(key, value).apply();
                
                // Update the text if the value was constrained
                String newText = String.valueOf(value);
                if (!s.toString().equals(newText)) {
                    if (key.equals(AppConstants.KEY_HISTORY_LOOKBACK)) {
                        historyLookbackInput.setText(newText);
                        historyLookbackInput.setSelection(newText.length());
                    } else {
                        sequenceLengthInput.setText(newText);
                        sequenceLengthInput.setSelection(newText.length());
                    }
                }
            } catch (NumberFormatException e) {
                // Reset to default if invalid input
                int defaultValue = key.equals(AppConstants.KEY_HISTORY_LOOKBACK) ? 
                    PromptManager.DEFAULT_HISTORY_LOOKBACK : 
                    PromptManager.MAX_SEQUENCE_LENGTH;
                settings.edit().putInt(key, defaultValue).apply();
            }
        }
    }

    public static int getHistoryLookback(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(AppConstants.KEY_HISTORY_LOOKBACK, PromptManager.DEFAULT_HISTORY_LOOKBACK);
    }

    public static int getSequenceLength(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(AppConstants.KEY_SEQUENCE_LENGTH, PromptManager.MAX_SEQUENCE_LENGTH);
    }

    private void setupBackendSpinner() {
        // Initialize views
        temperatureInput = binding.settingsContainer.findViewById(R.id.temperatureInput);
        backendSpinner = binding.settingsContainer.findViewById(R.id.backendSpinner);
        
        // Setup backend spinner
        List<String> backendOptions = new ArrayList<>();
        backendOptions.add("cpu");  // CPU backend is always available
        // Disable MTK backend option for now
        // if (LLMEngineService.isMTKBackendAvailable()) {
        //     backendOptions.add("mtk");
        // }
        
        backendAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, backendOptions);
        backendAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        backendSpinner.setAdapter(backendAdapter);

        // Load saved settings
        float savedTemp = settings.getFloat(AppConstants.KEY_TEMPERATURE, 0.0f);
        String savedBackend = settings.getString(AppConstants.KEY_PREFERRED_BACKEND, AppConstants.DEFAULT_BACKEND);
        
        temperatureInput.setText(String.valueOf(savedTemp));
        int backendPosition = backendAdapter.getPosition(savedBackend);
        if (backendPosition >= 0) {
            backendSpinner.setSelection(backendPosition);
        }

        setupSettingsListeners();
    }

    private void setupSettingsListeners() {
        temperatureInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    float temp = s.length() > 0 ? Float.parseFloat(s.toString()) : 0.0f;
                    if (temp >= 0.0f && temp <= 2.0f) {
                        settings.edit().putFloat(AppConstants.KEY_TEMPERATURE, temp).apply();
                    }
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid temperature value");
                }
            }
        });

        backendSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedBackend = backendAdapter.getItem(position);
                settings.edit().putString(AppConstants.KEY_PREFERRED_BACKEND, selectedBackend).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }
} 