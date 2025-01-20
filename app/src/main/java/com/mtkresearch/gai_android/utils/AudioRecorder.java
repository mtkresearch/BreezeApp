package com.mtkresearch.gai_android.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import java.io.File;
import java.io.IOException;

public class AudioRecorder {
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private final Context context;

    public AudioRecorder(Context context) {
        this.context = context;
    }
    
    public void startRecording(File outputFile) throws IOException {
        this.outputFile = outputFile;
        
        mediaRecorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S 
            ? new MediaRecorder(context) 
            : new MediaRecorder();
            
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
        
        mediaRecorder.prepare();
        mediaRecorder.start();
    }
    
    public void stopRecording() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaRecorder = null;
        }
    }
    
    public int getMaxAmplitude() {
        if (mediaRecorder != null) {
            try {
                return mediaRecorder.getMaxAmplitude();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    public void release() {
        if (mediaRecorder != null) {
            try {
                mediaRecorder.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mediaRecorder = null;
        }
    }
} 