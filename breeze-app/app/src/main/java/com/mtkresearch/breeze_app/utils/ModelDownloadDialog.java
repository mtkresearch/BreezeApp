package com.mtkresearch.breeze_app.utils;

import android.app.Dialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.viewpager2.widget.ViewPager2;

import com.mtkresearch.breeze_app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class ModelDownloadDialog extends Dialog {
    private static final String TAG = "ModelDownloadDialog";

    public enum DownloadMode {
        LLM,
        TTS
    }

    private final IntroDialog parentDialog;
    private final DownloadMode downloadMode;
    private ProgressBar progressBar;
    private TextView statusText;
    private Button downloadButton;
    private Button cancelButton;
    private DownloadTask downloadTask;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ModelDownloadDialog(Context context, IntroDialog parentDialog, DownloadMode mode) {
        super(context);
        this.parentDialog = parentDialog;
        this.downloadMode = mode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Set dialog window properties
        if (getWindow() != null) {
            // Set a semi-transparent dim background
            getWindow().setDimAmount(0.5f);
            // Set the background to use the dialog background drawable
            getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            // Set the layout size
            getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        setContentView(R.layout.model_download_dialog);

        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        downloadButton = findViewById(R.id.downloadButton);
        cancelButton = findViewById(R.id.cancelButton);
        TextView messageText = findViewById(R.id.messageText);

        // Set appropriate message based on download mode
        messageText.setText(downloadMode == DownloadMode.TTS ? 
            R.string.model_missing_message_tts : 
            R.string.model_missing_message);

        downloadButton.setOnClickListener(v -> startDownload());
        cancelButton.setOnClickListener(v -> {
            if (downloadTask != null && downloadTask.getStatus() == AsyncTask.Status.RUNNING) {
                downloadTask.cancel(true);
                Toast.makeText(getContext(), R.string.download_cancelled, Toast.LENGTH_SHORT).show();
            }
            dismiss();
        });

        setCancelable(false);
    }

    private File getModelDir() {
        File appDir = getContext().getFilesDir();
        File modelDir;
        
        if (downloadMode == DownloadMode.TTS) {
            File baseModelDir = new File(appDir, AppConstants.APP_MODEL_DIR);
            if (!baseModelDir.exists()) {
                if (!baseModelDir.mkdirs()) {
                    Log.e(TAG, "Failed to create base model directory");
                    return null;
                }
            }
            modelDir = new File(baseModelDir, AppConstants.TTS_MODEL_DIR);
            if (!modelDir.exists()) {
                if (!modelDir.mkdirs()) {
                    Log.e(TAG, "Failed to create TTS model directory");
                    return null;
                }
            }
        } else {
            modelDir = new File(appDir, AppConstants.APP_MODEL_DIR);
            if (!modelDir.exists() && !modelDir.mkdirs()) {
                Log.e(TAG, "Failed to create model directory");
                return null;
            }
        }
        
        return modelDir;
    }

    private void startDownload() {
        File modelDir = getModelDir();
        if (modelDir == null) {
            Toast.makeText(getContext(), R.string.error_creating_model_directory, Toast.LENGTH_SHORT).show();
            return;
        }

        // Get available space from the app's private storage
        long availableSpaceMB = getContext().getFilesDir().getFreeSpace() / (1024 * 1024);
        long requiredSpace = downloadMode == DownloadMode.TTS ? 125 : 8 * 1024; // 125MB for TTS, 8GB for LLM

        Log.d(TAG, String.format("Storage check - Available: %dMB, Required: %dMB", availableSpaceMB, requiredSpace));

        if (availableSpaceMB < requiredSpace) {
            if (downloadMode == DownloadMode.TTS) {
                Toast.makeText(getContext(), getContext().getString(R.string.insufficient_storage_for_download_mb, requiredSpace),
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), getContext().getString(R.string.insufficient_storage_for_download, requiredSpace / 1024),
                        Toast.LENGTH_LONG).show();
            }
            dismiss();
            return;
        }

        // Disable UI elements
        downloadButton.setEnabled(false);
        downloadButton.setAlpha(AppConstants.DISABLED_ALPHA);  // Visual feedback that button is disabled
        progressBar.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(downloadMode == DownloadMode.TTS ? R.string.download_progress_tts : R.string.downloading);

        downloadTask = new DownloadTask(modelDir);
        String[] urls = downloadMode == DownloadMode.TTS ? 
            AppConstants.TTS_MODEL_DOWNLOAD_URLS : 
            AppConstants.MODEL_DOWNLOAD_URLS;
        downloadTask.execute(urls);
    }

    private class DownloadTask extends AsyncTask<String[], Integer, Boolean> {
        private Exception error;
        private final File modelDir;
        private int totalFiles = 0;
        private int currentFileIndex = 0;
        private volatile boolean isCancelled = false;

        public DownloadTask(File modelDir) {
            this.modelDir = modelDir;
        }

        @Override
        protected Boolean doInBackground(String[]... params) {
            String[] urls = params[0];
            totalFiles = urls.length;
            
            if (downloadMode == DownloadMode.TTS) {
                // Download all TTS model files
                for (int i = 0; i < urls.length; i += 2) {  // Process in pairs (main URL + mirror)
                    String fileName = getFileNameFromUrl(urls[i]);
                    if (!tryDownloadFromUrl(urls[i], fileName) && !tryDownloadFromUrl(urls[i + 1], fileName)) {
                        return false;
                    }
                    if (isCancelled()) {
                        return false;
                    }
                    currentFileIndex += 2;
                }
                return true;
            } else {
                // Download LLM files
                // Download tokenizer first (small file)
                if (!tryDownloadFromUrl(urls[0], "tokenizer.bin")) {
                    return false;
                }
                
                if (isCancelled()) {
                    return false;
                }

                // For the model file, try multiple URLs
                for (int i = 1; i < urls.length; i++) {
                    if (tryDownloadFromUrl(urls[i], AppConstants.BREEZE_MODEL_FILE)) {
                        return true;
                    }
                    if (isCancelled()) {
                        return false;
                    }
                }
            }
            
            return false;
        }

        private String getFileNameFromUrl(String url) {
            String[] parts = url.split("/");
            String lastPart = parts[parts.length - 1];
            int queryIndex = lastPart.indexOf('?');
            return queryIndex > 0 ? lastPart.substring(0, queryIndex) : lastPart;
        }

        private boolean tryDownloadFromUrl(String urlString, String fileName) {
            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;

            try {
                // Check available storage space first
                long availableSpace = getContext().getFilesDir().getFreeSpace() / (1024 * 1024); // Convert to MB
                long requiredSpace = downloadMode == DownloadMode.TTS ? 125 : AppConstants.MODEL_DOWNLOAD_MIN_SPACE_MB;
                
                Log.d(TAG, String.format("Download attempt - URL: %s, File: %s, Available: %dMB, Required: %dMB",
                    urlString, fileName, availableSpace, requiredSpace));

                if (availableSpace < requiredSpace) {
                    if (downloadMode == DownloadMode.TTS) {
                        throw new IOException("Insufficient storage space. Need " + requiredSpace + "MB free.");
                    } else {
                        int requiredGB = (int) Math.ceil(requiredSpace / 1024.0);
                        throw new IOException("Insufficient storage space. Need " + requiredGB + "GB free.");
                    }
                }

                // Setup files
                File outputFile = new File(modelDir, fileName);
                File tempFile = new File(modelDir, fileName + AppConstants.MODEL_DOWNLOAD_TEMP_EXTENSION);
                long existingLength = 0;

                // Check for existing temporary file
                if (tempFile.exists()) {
                    existingLength = tempFile.length();
                    Log.i(TAG, "Found existing partial download: " + existingLength + " bytes");
                }

                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                
                // Set all required headers
                for (String[] header : AppConstants.DOWNLOAD_HEADERS) {
                    connection.setRequestProperty(header[0], header[1]);
                }

                // Add Range header if we have partial file
                if (existingLength > 0) {
                    connection.setRequestProperty("Range", "bytes=" + existingLength + "-");
                }
                
                // Set timeouts
                connection.setConnectTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                connection.setReadTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                
                connection.connect();
                
                // Handle redirects
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    
                    String redirectUrl = connection.getHeaderField("Location");
                    if (redirectUrl == null) {
                        Log.w(TAG, "Redirect URL is null for " + urlString);
                        return false;
                    }
                    
                    connection.disconnect();
                    url = new URL(redirectUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    
                    // Set headers again for redirected URL
                    for (String[] header : AppConstants.DOWNLOAD_HEADERS) {
                        connection.setRequestProperty(header[0], header[1]);
                    }
                    if (existingLength > 0) {
                        connection.setRequestProperty("Range", "bytes=" + existingLength + "-");
                    }
                    connection.setConnectTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                    connection.setReadTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                    connection.connect();
                    
                    responseCode = connection.getResponseCode();
                }

                // Check if range request was accepted
                boolean isResuming = (responseCode == HttpURLConnection.HTTP_PARTIAL);
                if (!isResuming && responseCode != HttpURLConnection.HTTP_OK) {
                    String errorMessage = "";
                    try {
                        errorMessage = connection.getResponseMessage();
                        try (InputStream errorStream = connection.getErrorStream()) {
                            if (errorStream != null) {
                                byte[] errorBytes = new byte[1024];
                                int bytesRead = errorStream.read(errorBytes);
                                if (bytesRead > 0) {
                                    errorMessage += " - " + new String(errorBytes, 0, bytesRead);
                                }
                            }
                        }
                    } catch (IOException e) {
                        errorMessage = e.getMessage();
                    }
                    Log.w(TAG, String.format("Failed to download from %s: HTTP %d %s", 
                        urlString, responseCode, errorMessage));
                    return false;
                }

                // Get the file length
                long fileLength = connection.getContentLengthLong();
                if (isResuming) {
                    String contentRange = connection.getHeaderField("Content-Range");
                    if (contentRange != null) {
                        String[] parts = contentRange.split("/");
                        if (parts.length == 2) {
                            fileLength = Long.parseLong(parts[1]);
                        }
                    }
                }
                
                input = connection.getInputStream();
                
                // Open output in append mode if resuming
                output = new FileOutputStream(tempFile, isResuming);

                byte[] data = new byte[AppConstants.MODEL_DOWNLOAD_BUFFER_SIZE];
                long total = existingLength;
                int count;
                int lastProgress = 0;

                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        input.close();
                        output.close();
                        // Don't delete temp file to allow resume
                        return false;
                    }
                    
                    total += count;
                    output.write(data, 0, count);

                    if (fileLength > 0) {
                        // Calculate combined progress across all files
                        int fileProgress = (int) (total * 100 / fileLength);
                        int overallProgress = (currentFileIndex * 100 + fileProgress) / totalFiles;
                        if (overallProgress > lastProgress + AppConstants.MODEL_DOWNLOAD_PROGRESS_UPDATE_INTERVAL) {
                            publishProgress(overallProgress);
                            lastProgress = overallProgress;
                        }
                    }
                }

                // Close streams before moving file
                output.close();
                output = null;
                input.close();
                input = null;

                // Verify the downloaded file
                if (!tempFile.exists() || tempFile.length() != fileLength) {
                    throw new IOException("Download incomplete or file size mismatch");
                }

                // Move temp file to final location
                if (!tempFile.renameTo(outputFile)) {
                    throw new IOException("Failed to move temporary file to final location");
                }

                // Log successful download
                Log.i(TAG, "Successfully downloaded " + fileName + " from " + urlString);
                return true;

            } catch (Exception e) {
                error = e;
                Log.e(TAG, "Error downloading from " + urlString + ": " + e.getMessage(), e);
                return false;
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                } catch (IOException ignored) {
                }
                if (connection != null) connection.disconnect();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
            String progressText = downloadMode == DownloadMode.TTS ?
                getContext().getString(R.string.download_progress_tts, values[0]) :
                getContext().getString(R.string.download_progress, values[0]);
            statusText.setText(progressText);
            // Ensure button stays disabled during download
            downloadButton.setEnabled(false);
            downloadButton.setAlpha(AppConstants.DISABLED_ALPHA);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                statusText.setText(downloadMode == DownloadMode.TTS ? 
                    R.string.download_complete_tts : 
                    R.string.download_complete);
                // Keep button disabled on success since download is complete
                downloadButton.setEnabled(false);
                downloadButton.setAlpha(AppConstants.DISABLED_ALPHA);
                Toast.makeText(getContext(), 
                    downloadMode == DownloadMode.TTS ? 
                        R.string.download_complete_tts : 
                        R.string.download_complete, 
                    Toast.LENGTH_SHORT).show();
                
                // Ensure we recheck system requirements before dismissing
                if (getContext() instanceof android.app.Activity) {
                    android.app.Activity activity = (android.app.Activity) getContext();
                    activity.runOnUiThread(() -> {
                        // Recheck requirements to update status
                        if (getOwnerActivity() instanceof android.app.Activity) {
                            android.app.Activity ownerActivity = getOwnerActivity();
                            android.view.View currentFocus = ownerActivity.getCurrentFocus();
                            if (currentFocus instanceof ViewPager2) {
                                ViewPager2 viewPager = (ViewPager2) currentFocus;
                                if (viewPager.getAdapter() instanceof IntroDialog.IntroPagerAdapter) {
                                    IntroDialog.IntroPagerAdapter adapter = 
                                        (IntroDialog.IntroPagerAdapter) viewPager.getAdapter();
                                    adapter.updateRequirementsPage(
                                        parentDialog.buildRequirementsDescription(getContext()));
                                }
                            }
                        }
                    });
                }
                
                // Dismiss after a short delay to show completion
                mainHandler.postDelayed(() -> dismiss(), 1000);
            } else {
                // Only re-enable button if download failed
                downloadButton.setEnabled(true);
                downloadButton.setAlpha(AppConstants.ENABLED_ALPHA);
                String errorMessage = error != null ? 
                    error.getMessage() : 
                    getContext().getString(
                        downloadMode == DownloadMode.TTS ? 
                            R.string.error_all_download_attempts_failed_tts : 
                            R.string.error_all_download_attempts_failed
                    );
                statusText.setText(getContext().getString(
                    downloadMode == DownloadMode.TTS ? 
                        R.string.download_failed_tts : 
                        R.string.download_failed, 
                    errorMessage));
                Toast.makeText(getContext(), 
                    getContext().getString(
                        downloadMode == DownloadMode.TTS ? 
                            R.string.error_downloading_tts_model : 
                            R.string.error_downloading_model, 
                        errorMessage), 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
} 