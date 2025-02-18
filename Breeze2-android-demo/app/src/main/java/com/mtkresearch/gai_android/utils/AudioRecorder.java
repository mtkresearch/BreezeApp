package com.mtkresearch.gai_android.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AudioRecorder {
    private static final String TAG = "AudioRecorder";
    private MediaRecorder mediaRecorder;
    private final Context context;
    private String currentRecordingPath;
    private File recordingsDir;
    private boolean isRecording = false;

    public AudioRecorder(Context context) {
        this.context = context;
        recordingsDir = new File(context.getFilesDir(), "recordings");
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs();
        }
    }
    
    public void startRecording() throws IOException {
        if (isRecording) {
            stopRecording();
            return;
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String fileName = "AUDIO_" + timeStamp + ".m4a";
        File outputFile = new File(recordingsDir, fileName);
        currentRecordingPath = outputFile.getAbsolutePath();

        mediaRecorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S 
            ? new MediaRecorder(context) 
            : new MediaRecorder();
            
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(currentRecordingPath);
        
        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start recording", e);
            releaseRecorder();
            throw e;
        }
    }
    
    public void stopRecording() {
        if (!isRecording) return;
        
        try {
            mediaRecorder.stop();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error stopping recorder", e);
        } finally {
            releaseRecorder();
            isRecording = false;
        }
    }
    
    public void cancelRecording() {
        if (!isRecording) return;
        
        try {
            mediaRecorder.stop();
        } catch (IllegalStateException e) {
            Log.e(TAG, "Error stopping recorder", e);
        } finally {
            releaseRecorder();
            isRecording = false;
            // Delete the current recording file
            if (currentRecordingPath != null) {
                File recordingFile = new File(currentRecordingPath);
                if (recordingFile.exists()) {
                    recordingFile.delete();
                }
            }
        }
    }
    
    public int getMaxAmplitude() {
        if (mediaRecorder != null && isRecording) {
            try {
                return mediaRecorder.getMaxAmplitude();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error getting max amplitude", e);
            }
        }
        return 0;
    }

    public boolean isRecording() {
        return isRecording;
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing recorder", e);
            }
            mediaRecorder = null;
        }
    }
} 