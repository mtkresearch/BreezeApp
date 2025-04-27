package com.mtkresearch.breeze_app.utils;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Simplified utility class for audio playback functionality.
 * Handles initialization and direct streaming of audio samples.
 */
public class AudioPlayerUtils {
    private static final String TAG = "AudioPlayerUtils";
    
    private final int sampleRate;
    private AudioTrack audioTrack;
    private boolean isPlaying = false;
    
    /**
     * Constructor with sample rate
     * 
     * @param context Application context
     * @param sampleRate Sample rate for audio playback (e.g., 16000, 22050, 44100)
     */
    public AudioPlayerUtils(Context context, int sampleRate) {
        this.sampleRate = sampleRate;
        initAudioTrack();
    }
    
    /**
     * Initialize the AudioTrack for playback
     */
    private void initAudioTrack() {
        try {
            // Get minimum buffer size
            int minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            );
            
            Log.d(TAG, "Sample rate: " + sampleRate + ", min buffer size: " + minBufferSize);
            
            // Configure audio attributes
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build();
    
            // Configure audio format
            AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
    
            // Create AudioTrack with stream mode for continuous playback
            audioTrack = new AudioTrack(
                audioAttributes,
                audioFormat,
                minBufferSize * 2,  // Double the buffer size for stability
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            );
            
            // Start playing immediately - the track will buffer data until it's written
            audioTrack.play();
            isPlaying = true;
            
            Log.d(TAG, "AudioTrack initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing AudioTrack", e);
            release();
        }
    }
    
    /**
     * Play float audio samples directly
     * 
     * @param samples Float array of audio samples in range [-1.0, 1.0]
     * @return true if samples were successfully played, false otherwise
     */
    public boolean playAudioSamples(float[] samples) {
        if (audioTrack == null || samples == null || samples.length == 0) {
            return false;
        }
        
        // Ensure AudioTrack is playing
        if (!isPlaying || audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.play();
            isPlaying = true;
        }
        
        try {
            // Write samples with blocking mode
            int result = audioTrack.write(samples, 0, samples.length, AudioTrack.WRITE_BLOCKING);
            return result > 0;
        } catch (Exception e) {
            Log.e(TAG, "Error playing audio samples", e);
            return false;
        }
    }
    
    /**
     * Stop audio playback and release resources
     */
    public void stopPlayback() {
        if (audioTrack != null) {
            try {
                audioTrack.pause();
                audioTrack.flush();
                isPlaying = false;
            } catch (Exception e) {
                Log.e(TAG, "Error stopping audio playback", e);
            }
        }
    }
    
    /**
     * Release all resources
     */
    public void release() {
        if (audioTrack != null) {
            try {
                audioTrack.stop();
                audioTrack.release();
                Log.d(TAG, "AudioTrack released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing AudioTrack", e);
            }
            audioTrack = null;
            isPlaying = false;
        }
    }
    
    /**
     * Check if audio is currently playing
     */
    public boolean isPlaying() {
        return isPlaying && audioTrack != null;
    }
} 