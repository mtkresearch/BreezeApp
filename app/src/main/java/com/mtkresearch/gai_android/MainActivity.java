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
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Toast;

import com.mtkresearch.gai_android.ChatActivity;
import com.mtkresearch.gai_android.R;
import com.mtkresearch.gai_android.databinding.ActivityMainBinding;
import com.mtkresearch.gai_android.service.ASREngineService;
import com.mtkresearch.gai_android.service.BaseEngineService;
import com.mtkresearch.gai_android.service.LLMEngineService;
import com.mtkresearch.gai_android.service.TTSEngineService;
import com.mtkresearch.gai_android.service.VLMEngineService;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int PERMISSION_REQUEST_CODE = 1;
    private ActivityMainBinding binding;
    
    // Services
    private LLMEngineService llmService;
    private VLMEngineService vlmService;
    private ASREngineService asrService;
    private TTSEngineService ttsService;
    
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupUI();
        displayDeviceInfo();
        checkAndRequestPermissions();
    }

    private void setupUI() {
        setupChatButton();
        initializeStatusIndicators();
    }

    private void setupChatButton() {
        binding.startChatButton.setEnabled(false);
        binding.startChatButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatActivity.class);
            startActivity(intent);
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

    private void bindServices() {
        // Bind services sequentially to avoid memory pressure
        startAndBindService(LLMEngineService.class, llmConnection, () -> {
            startAndBindService(VLMEngineService.class, vlmConnection, () -> {
                startAndBindService(ASREngineService.class, asrConnection, () -> {
                    startAndBindService(TTSEngineService.class, ttsConnection, null);
                });
            });
        });
    }

    private void startAndBindService(
            Class<? extends BaseEngineService> serviceClass, 
            ServiceConnection connection,
            Runnable onComplete) {
        Intent intent = new Intent(this, serviceClass);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        
        if (onComplete != null) {
            new Handler().postDelayed(onComplete, 500); // Give time for binding
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
        boolean allReady = llmService != null && llmService.isReady() &&
                          vlmService != null && vlmService.isReady() &&
                          asrService != null && asrService.isReady() &&
                          ttsService != null && ttsService.isReady();
        
        runOnUiThread(() -> {
            binding.startChatButton.setEnabled(allReady);
            
            if (allReady) {
                // When all services are ready, use primary colors
                binding.startChatButton.setBackgroundTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.primary, getTheme())));
                binding.startChatButton.setTextColor(
                    getResources().getColor(R.color.text_secondary, getTheme()));
            } else {
                // When services are not ready, use surface and secondary text colors
                binding.startChatButton.setBackgroundTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.surface, getTheme())));
                binding.startChatButton.setTextColor(
                    getResources().getColor(R.color.text_secondary, getTheme()));
            }
        });
    }

    private enum EngineStatus {
        INITIALIZING,
        READY,
        ERROR
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

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting RECORD_AUDIO permission");
            ActivityCompat.requestPermissions(this,
                new String[]{android.Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST_CODE);
        } else {
            Log.d(TAG, "RECORD_AUDIO permission already granted");
            bindServices();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "RECORD_AUDIO permission granted");
                bindServices();
            } else {
                Log.e(TAG, "RECORD_AUDIO permission denied");
                Toast.makeText(this, "Speech recognition requires audio permission", Toast.LENGTH_LONG).show();
                updateEngineStatus(binding.asrStatusIndicator, EngineStatus.ERROR);
            }
        }
    }
} 