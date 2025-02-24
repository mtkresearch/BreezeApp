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

import com.mtkresearch.breeze_app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ModelDownloadDialog extends Dialog {
    private static final String TAG = "ModelDownloadDialog";

    private ProgressBar progressBar;
    private TextView statusText;
    private Button downloadButton;
    private Button cancelButton;
    private DownloadTask downloadTask;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ModelDownloadDialog(Context context) {
        super(context);
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

    private void startDownload() {
        // Get app's private storage directory
        File appDir = getContext().getFilesDir();
        File modelDir = new File(appDir, AppConstants.APP_MODEL_DIR);
        
        // Check available storage space
        long availableSpace = modelDir.getParentFile().getFreeSpace() / (1024 * 1024); // Convert to MB

        if (availableSpace < AppConstants.MODEL_DOWNLOAD_MIN_SPACE_MB) {
            int requiredGB = (int) Math.ceil(AppConstants.MODEL_DOWNLOAD_MIN_SPACE_MB / 1024.0);
            String message = getContext().getString(R.string.insufficient_storage_for_download, requiredGB);
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            return;
        }

        // Create model directory if it doesn't exist
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            Toast.makeText(getContext(), R.string.error_creating_model_directory, Toast.LENGTH_SHORT).show();
            return;
        }

        downloadButton.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(R.string.downloading);

        downloadTask = new DownloadTask(modelDir);
        downloadTask.execute(AppConstants.MODEL_DOWNLOAD_URLS);
    }

    private class DownloadTask extends AsyncTask<String[], Integer, Boolean> {
        private Exception error;
        private final File modelDir;
        private int totalFiles = 0;
        private int currentFileIndex = 0;

        public DownloadTask(File modelDir) {
            this.modelDir = modelDir;
        }

        @Override
        protected Boolean doInBackground(String[]... params) {
            String[] urls = params[0];
            totalFiles = urls.length;
            
            for (int i = 0; i < urls.length; i++) {
                currentFileIndex = i;
                String url = urls[i];
                String fileName = url.contains("tokenizer.bin") ? "tokenizer.bin" : AppConstants.BREEZE_MODEL_FILE;
                
                if (!tryDownloadFromUrl(url, fileName)) {
                    return false;
                }
                if (isCancelled()) {
                    return false;
                }
            }
            
            return true;
        }

        private boolean tryDownloadFromUrl(String urlString, String fileName) {
            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;

            try {
                // Check available storage space first
                long availableSpace = modelDir.getParentFile().getFreeSpace() / (1024 * 1024); // Convert to MB
                if (availableSpace < AppConstants.MODEL_DOWNLOAD_MIN_SPACE_MB) {
                    int requiredGB = (int) Math.ceil(AppConstants.MODEL_DOWNLOAD_MIN_SPACE_MB / 1024.0);
                    throw new IOException("Insufficient storage space. Need " + requiredGB + "GB free.");
                }

                // Create model directory if needed
                if (!modelDir.exists() && !modelDir.mkdirs()) {
                    throw new IOException("Failed to create model directory");
                }

                URL url = new URL(urlString);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                connection.setReadTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                
                // Set standard headers
                connection.setRequestProperty("User-Agent", "Breeze-Android-App");
                connection.setRequestProperty("Accept", "application/octet-stream");
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
                    connection.setConnectTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                    connection.setReadTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                    connection.setRequestProperty("User-Agent", "Breeze-Android-App");
                    connection.setRequestProperty("Accept", "application/octet-stream");
                    connection.connect();
                    
                    responseCode = connection.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    String errorMessage = "";
                    try {
                        errorMessage = connection.getResponseMessage();
                        // Get more detailed error message if available
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

                // Get the file length if possible
                int fileLength = connection.getContentLength();
                input = connection.getInputStream();
                
                // Create the output file
                File outputFile = new File(modelDir, fileName);
                output = new FileOutputStream(outputFile);

                byte[] data = new byte[AppConstants.MODEL_DOWNLOAD_BUFFER_SIZE];
                long total = 0;
                int count;
                int lastProgress = 0;

                while ((count = input.read(data)) != -1) {
                    if (isCancelled()) {
                        input.close();
                        output.close();
                        outputFile.delete();
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

                // Verify the downloaded file
                if (!outputFile.exists() || outputFile.length() == 0) {
                    throw new IOException("Download completed but file is empty or missing");
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
            statusText.setText(getContext().getString(R.string.download_progress, values[0]));
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                statusText.setText(R.string.download_complete);
                Toast.makeText(getContext(), R.string.download_complete, Toast.LENGTH_SHORT).show();
                mainHandler.postDelayed(() -> dismiss(), 1000);
            } else {
                downloadButton.setEnabled(true);
                String errorMessage = error != null ? 
                    error.getMessage() : 
                    getContext().getString(R.string.error_all_download_attempts_failed);
                statusText.setText(getContext().getString(R.string.download_failed, errorMessage));
                Toast.makeText(getContext(), 
                    getContext().getString(R.string.error_downloading_model, errorMessage), 
                    Toast.LENGTH_LONG).show();
            }
        }
    }
} 