package com.mtkresearch.breeze_app.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.FileProvider;

import com.mtkresearch.breeze_app.R;

import java.io.File;
import java.io.IOException;

public class ChatMediaHandler {
    private static final String TAG = "ChatMediaHandler";
    
    private final Context context;
    private final AudioRecorder audioRecorder;
    private String currentPhotoPath;
    private boolean isRecording = false;

    public ChatMediaHandler(Context context) {
        this.context = context;
        this.audioRecorder = new AudioRecorder(context);
    }

    public void startRecording() {
        if (isRecording) {
            stopRecording(true);
            return;
        }

        try {
            audioRecorder.startRecording();
            isRecording = true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording", e);
            Toast.makeText(context, context.getString(R.string.failed_to_start_recording), Toast.LENGTH_SHORT).show();
        }
    }

    public void stopRecording(boolean shouldSave) {
        if (!isRecording) return;
        
        if (shouldSave) {
            audioRecorder.stopRecording();
        } else {
            audioRecorder.cancelRecording();
        }
        isRecording = false;
    }

    public Intent createImageSelectionIntent() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        return Intent.createChooser(intent, "Select Picture");
    }

    public Intent createCameraCaptureIntent() throws IOException {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        File photoFile = FileUtils.createImageFile(context);
        if (photoFile != null) {
            currentPhotoPath = photoFile.getAbsolutePath();
            Uri photoURI = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".fileprovider",
                    photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            takePictureIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            return takePictureIntent;
        }
        return null;
    }

    public Intent createFileSelectionIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        return Intent.createChooser(intent, "Select File");
    }

    public String getCurrentPhotoPath() {
        return currentPhotoPath;
    }

    public boolean isRecording() {
        return isRecording;
    }

    public ChatMessage handleSelectedFile(Uri fileUri) {
        try {
            String fileName = FileUtils.getFileName(context, fileUri);
            return new ChatMessage("Attached file: " + fileName, true);
        } catch (Exception e) {
            Log.e(TAG, "Failed to process file", e);
            Toast.makeText(context, context.getString(R.string.failed_to_process_file), Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    public void release() {
        if (audioRecorder != null) {
            audioRecorder.stopRecording();
        }
    }
} 