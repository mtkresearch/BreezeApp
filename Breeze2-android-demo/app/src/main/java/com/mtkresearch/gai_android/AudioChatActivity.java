package com.mtkresearch.gai_android;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.mtkresearch.gai_android.databinding.ActivityAudioChatBinding;
import android.widget.Toast;
import android.app.Dialog;
import android.widget.ImageButton;
import android.widget.Button;
import android.content.Intent;

public class AudioChatActivity extends AppCompatActivity {
    private ActivityAudioChatBinding binding;
    private boolean isMicMuted = false;
    private boolean isFrontCamera = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAudioChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        setupButtons();
    }

    private void setupButtons() {
        binding.cameraButton.setOnClickListener(v -> {
            isFrontCamera = !isFrontCamera;
            // TODO: Implement camera switch logic
            Toast.makeText(this, 
                "Switched to " + (isFrontCamera ? "front" : "back") + " camera", 
                Toast.LENGTH_SHORT).show();
        });

        binding.micButton.setOnClickListener(v -> {
            isMicMuted = !isMicMuted;
            binding.micButton.setImageResource(
                isMicMuted ? R.drawable.ic_mic_off : R.drawable.ic_mic);
            // TODO: Implement mic mute logic
        });

        binding.menuButton.setOnClickListener(v -> {
            // TODO: Implement menu options
            Toast.makeText(this, "Menu options", Toast.LENGTH_SHORT).show();
        });

        binding.closeButton.setOnClickListener(v -> {
            finish(); // Return to ChatActivity
        });

        binding.voiceSelectionButton.setOnClickListener(v -> {
            showVoiceSelectionDialog();
        });

    }

    private void showVoiceSelectionDialog() {
        Dialog dialog = new Dialog(this, R.style.FullScreenDialog);
        dialog.setContentView(R.layout.dialog_voice_selection);
        
        // Setup dialog views and functionality
        ImageButton cancelButton = dialog.findViewById(R.id.cancelButton);
        Button startChatButton = dialog.findViewById(R.id.startChatButton);
        
        cancelButton.setOnClickListener(v -> dialog.dismiss());
        startChatButton.setOnClickListener(v -> {
            // TODO: Handle voice selection
            dialog.dismiss();
        });
        
        dialog.show();
    }
} 