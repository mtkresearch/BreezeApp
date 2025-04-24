package com.mtkresearch.breeze_app.tts;

/**
 * Utility class for working with PCM audio data
 */
public class PCMUtils {
    
    /**
     * Convert float array audio samples to 16-bit PCM format
     * @param samples Float array of audio samples in the range [-1.0, 1.0]
     * @return Byte array containing 16-bit PCM data
     */
    public static byte[] floatArrayToPCM16(float[] samples) {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            // Convert float in [-1.0,1.0] to 16-bit PCM
            short val = (short) (samples[i] * 32767.0f);
            // Little endian
            pcm[i * 2] = (byte) (val & 0xFF);
            pcm[i * 2 + 1] = (byte) ((val >> 8) & 0xFF);
        }
        return pcm;
    }
    
    /**
     * Convert 16-bit PCM data to float array audio samples
     * @param pcm Byte array containing 16-bit PCM data
     * @return Float array of audio samples in the range [-1.0, 1.0]
     */
    public static float[] pcm16ToFloatArray(byte[] pcm) {
        float[] samples = new float[pcm.length / 2];
        for (int i = 0; i < samples.length; i++) {
            // Little endian
            short val = (short) (pcm[i * 2] & 0xFF | (pcm[i * 2 + 1] & 0xFF) << 8);
            // Convert 16-bit PCM to float in [-1.0,1.0]
            samples[i] = val / 32767.0f;
        }
        return samples;
    }
} 