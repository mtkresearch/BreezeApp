package com.mtkresearch.breeze_app.utils;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
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

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mtkresearch.breeze_app.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.ActivityManager;
import android.text.format.Formatter;

public class ModelDownloadDialog extends Dialog {
    private static final String TAG = "ModelDownloadDialog";

    public enum DownloadMode {
        LLM,
        TTS,
        MTK_NPU
    }

    private final IntroDialog parentDialog;
    private final DownloadMode downloadMode;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView overallProgressText;
    private TextView fileListTitle;
    private Button downloadButton;
    private Button cancelButton;
    private Button pauseResumeButton;
    private Button retryButton;
    private RecyclerView fileRecyclerView;
    private FileDownloadAdapter fileAdapter;
    private DownloadTask downloadTask;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private List<AppConstants.DownloadFileInfo> downloadFiles = new ArrayList<>();
    private TextView messageText;

    public ModelDownloadDialog(Context context, IntroDialog parentDialog, DownloadMode mode) {
        super(context);
        this.parentDialog = parentDialog;
        this.downloadMode = mode;
    }

    private class DownloadTask extends AsyncTask<Void, Object, Integer> {
        private volatile boolean isCancelled = false;
        private final File modelDir;
        private Exception error = null;
        private volatile boolean isDownloading = true;
        private OnDownloadCompleteListener callback;
        
        public DownloadTask(File modelDir) {
            this.modelDir = modelDir;
        }
        
        public void setCallback(OnDownloadCompleteListener callback) {
            this.callback = callback;
        }
        
        public boolean isDownloadCancelled() {
            return isCancelled;
        }
        
        @Override
        protected Integer doInBackground(Void... params) {
            int successfulDownloads = 0;
            int fileCount = fileAdapter.getCount();
            
            try {
                // Create model directory if needed
                if (!modelDir.exists() && !modelDir.mkdirs()) {
                    error = new IOException("Failed to create model directory: " + modelDir.getAbsolutePath());
                    return 0;
                }
                
                for (int i = 0; i < fileCount; i++) {
                    if (isCancelled) {
                        Log.d(TAG, "Download task cancelled before processing file " + i);
                        return successfulDownloads;
                    }
                    
                    FileDownloadAdapter.FileDownloadStatus fileStatus = fileAdapter.getItem(i);
                    if (fileStatus == null) {
                        Log.e(TAG, "File status is null for index " + i);
                        continue;
                    }
                    
                    AppConstants.DownloadFileInfo fileInfo = fileStatus.getFileInfo();
                    
                    // Skip already completed files
                    if (fileStatus.getStatus() == AppConstants.DOWNLOAD_STATUS_COMPLETED) {
                        Log.d(TAG, "Skipping already completed file: " + fileInfo.fileName);
                        successfulDownloads++;
                        continue;
                    }
                    
                    Log.d(TAG, "Processing file: " + fileInfo.fileName);
                    
                    // Check if the URL contains multiple alternatives (separated by semicolons)
                    String[] urls = fileInfo.url.split(";");
                    boolean downloadSuccessful = false;
                    
                    for (String url : urls) {
                        if (url.trim().isEmpty()) {
                            continue;
                        }
                        
                        // Create a temporary file info with the current URL
                        AppConstants.DownloadFileInfo currentUrlFileInfo = new AppConstants.DownloadFileInfo(
                            url.trim(), fileInfo.fileName, fileInfo.displayName, fileInfo.fileSize);
                        
                        Log.d(TAG, "Attempting download from URL: " + url.trim());
                        
                        // Try to download using this URL
                        if (downloadFile(currentUrlFileInfo, i)) {
                            Log.d(TAG, "Successfully downloaded from URL: " + url.trim());
                            downloadSuccessful = true;
                            break;  // Break out of the URL loop once successful
                        } else if (isCancelled) {
                            Log.d(TAG, "Download task cancelled during file download");
                            return successfulDownloads;
                        }
                        
                        Log.d(TAG, "Failed to download from URL: " + url.trim() + ", trying next URL if available");
                    }
                    
                    if (downloadSuccessful) {
                        successfulDownloads++;
                        final int finalI = i;  // Create a final copy of i
                        mainHandler.post(() -> {
                            fileAdapter.updateFileStatus(finalI, AppConstants.DOWNLOAD_STATUS_COMPLETED);
                            updateOverallProgress();
                        });
                    } else {
                        Log.e(TAG, "Failed to download " + fileInfo.fileName + " from all available URLs");
                        final int finalI = i;  // Create a final copy of i
                        mainHandler.post(() -> {
                            fileAdapter.updateFileStatus(finalI, AppConstants.DOWNLOAD_STATUS_FAILED);
                            updateOverallProgress();
                        });
                    }
                }
                
                // Check if all files were downloaded successfully
                return successfulDownloads;
                
            } catch (Exception e) {
                Log.e(TAG, "Error in download task", e);
                error = e;
                return successfulDownloads;
            }
        }

        @Override
        protected void onProgressUpdate(Object... values) {
            if (values.length >= 4) {
                int fileIndex = (int) values[0];
                int progress = (int) values[1];
                long downloadedBytes = (int) values[2];
                long totalBytes = (int) values[3];
                
                fileAdapter.updateFileProgress(fileIndex, progress, downloadedBytes, totalBytes);
                updateOverallProgress();
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            isDownloading = false;
            downloadTask = null;
            
            // Hide the progress bar and progress text when done
            progressBar.setVisibility(View.GONE);
            overallProgressText.setVisibility(View.GONE);
            
            // Update UI based on download result
            handleDownloadCompletion(result);
        }

        @Override
        protected void onCancelled(Integer result) {
            handleDownloadCompletion(result);
        }

        private void handleDownloadCompletion(Integer result) {
            // Hide pause/resume button when download is complete
            pauseResumeButton.setVisibility(View.GONE);
            
            if (result > 0) {
                if (result == fileAdapter.getCount()) {
                    // All files downloaded successfully
                    statusText.setText(R.string.download_complete);
                    progressBar.setProgress(100);
                    
                    dismiss();
                    if (callback != null) {
                        callback.onDownloadComplete();
                    }
                } else {
                    // Some files downloaded, others failed
                    statusText.setText(getContext().getString(R.string.download_partially_complete, result, fileAdapter.getCount()));
                    
                    // Show retry button for failed files
                    downloadButton.setText(R.string.retry_download);
                    downloadButton.setEnabled(true);
                    downloadButton.setAlpha(1.0f);
                }
            } else {
                // No files downloaded successfully
                if (isCancelled) {
                    statusText.setText(R.string.download_cancelled);
                    
                    // Update file statuses to show cancellation
                    for (int i = 0; i < fileAdapter.getCount(); i++) {
                        final int finalI = i; // Create a final copy of i
                        FileDownloadAdapter.FileDownloadStatus status = fileAdapter.getItem(i);
                        if (status != null && status.getStatus() != AppConstants.DOWNLOAD_STATUS_COMPLETED) {
                            fileAdapter.updateFileStatus(finalI, AppConstants.DOWNLOAD_STATUS_FAILED, "Download cancelled");
                        }
                    }
                } else if (error != null) {
                    String errorMessage = error.getMessage();
                    if (errorMessage == null || errorMessage.isEmpty()) {
                        errorMessage = error.getClass().getSimpleName();
                    }
                    statusText.setText(getContext().getString(R.string.download_failed, errorMessage));
                    Log.e(TAG, "Download failed with error", error);
                } else {
                    statusText.setText(R.string.download_failed_unknown);
                    Log.e(TAG, "Download failed without specific error");
                }
                
                // Enable retry
                downloadButton.setText(R.string.retry_download);
                downloadButton.setEnabled(true);
                downloadButton.setAlpha(1.0f);
            }
        }
        
        /**
         * Updates the overall progress in the UI based on individual file progress
         */
        private void updateOverallProgress() {
            if (fileAdapter == null) return;
            
            List<FileDownloadAdapter.FileDownloadStatus> files = fileAdapter.getFiles();
            if (files == null || files.isEmpty()) return;
            
            int totalProgress = 0;
            long totalDownloaded = 0;
            long totalSize = 0;
            
            for (FileDownloadAdapter.FileDownloadStatus file : files) {
                totalProgress += file.getProgress();
                totalDownloaded += file.getDownloadedBytes();
                totalSize += file.getTotalBytes();
            }
            
            // Calculate average progress
            int overallProgress = totalProgress / files.size();
            progressBar.setProgress(overallProgress);
            
            // Update the overall progress text
            String downloadedSize = Formatter.formatFileSize(getContext(), totalDownloaded);
            String totalSizeStr = Formatter.formatFileSize(getContext(), totalSize);
            overallProgressText.setText(getContext().getString(R.string.download_progress_format, 
                overallProgress, downloadedSize, totalSizeStr));
        }
        
        /**
         * Downloads a file from the given URL
         * 
         * @param fileInfo Information about the file to download
         * @param fileIndex Index of the file in the adapter
         * @return true if download was successful, false otherwise
         */
        private boolean downloadFile(AppConstants.DownloadFileInfo fileInfo, int fileIndex) {
            HttpURLConnection connection = null;
            InputStream input = null;
            FileOutputStream output = null;
            
            try {
                // Update file status in UI
                final int finalFileIndex = fileIndex; // Create a final copy of fileIndex
                mainHandler.post(() -> {
                    fileAdapter.updateFileStatus(finalFileIndex, AppConstants.DOWNLOAD_STATUS_IN_PROGRESS);
                });
                
                URL url = new URL(fileInfo.url);
                connection = (HttpURLConnection) url.openConnection();
                
                // Set appropriate headers
                for (String[] header : AppConstants.DOWNLOAD_HEADERS) {
                    connection.setRequestProperty(header[0], header[1]);
                }
                
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(20000);
                
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Bad response code: " + responseCode + " for URL: " + fileInfo.url);
                    return false;
                }
                
                // Get the actual file size from the server
                long fileSize = connection.getContentLength();
                long actualFileSize = fileSize > 0 ? fileSize : fileInfo.fileSize;
                
                // Create target file
                File outputFile = new File(modelDir, fileInfo.fileName);
                File partFile = new File(modelDir, fileInfo.fileName + AppConstants.MODEL_DOWNLOAD_TEMP_EXTENSION);
                
                // Create parent directories if needed
                if (!outputFile.getParentFile().exists()) {
                    outputFile.getParentFile().mkdirs();
                }
                
                // Setup streams
                input = new BufferedInputStream(connection.getInputStream(), AppConstants.MODEL_DOWNLOAD_BUFFER_SIZE);
                output = new FileOutputStream(partFile);
                
                byte[] buffer = new byte[AppConstants.MODEL_DOWNLOAD_BUFFER_SIZE];
                long totalBytesRead = 0;
                int bytesRead;
                long lastProgressUpdate = 0;
                
                while ((bytesRead = input.read(buffer)) != -1) {
                    // Check if download was cancelled or paused
                    if (isCancelled) {
                        Log.d(TAG, "Download cancelled for " + fileInfo.fileName);
                        return false;
                    }
                    
                    // Handle pause
                    if (isPaused.get()) {
                        try {
                            synchronized (this) {
                                wait(); // Wait for notify from resume button
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return false;
                        }
                    }
                    
                    // Write data to file
                    output.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    
                    // Calculate progress
                    int progress = (int) ((totalBytesRead * 100) / actualFileSize);
                    
                    // Update progress periodically to avoid excessive UI updates
                    long now = System.currentTimeMillis();
                    if (now - lastProgressUpdate > AppConstants.MODEL_DOWNLOAD_PROGRESS_UPDATE_INTERVAL * 1000 || 
                            progress == 100) {
                        publishProgress(fileIndex, progress, totalBytesRead, actualFileSize);
                        lastProgressUpdate = now;
                    }
                }
                
                // Close streams
                output.flush();
                output.close();
                output = null;
                input.close();
                input = null;
                
                // Rename part file to actual file when download is complete
                if (!partFile.renameTo(outputFile)) {
                    Log.e(TAG, "Failed to rename temporary file to target file: " + outputFile.getName());
                    return false;
                }
                
                Log.d(TAG, "Download completed for " + fileInfo.fileName);
                return true;
                
            } catch (Exception e) {
                Log.e(TAG, "Error downloading file: " + fileInfo.fileName, e);
                final int finalFileIndex = fileIndex; // Create a final copy of fileIndex
                mainHandler.post(() -> {
                    fileAdapter.updateFileStatus(finalFileIndex, AppConstants.DOWNLOAD_STATUS_FAILED, e.getMessage());
                });
                return false;
            } finally {
                try {
                    if (output != null) {
                        output.close();
                    }
                    if (input != null) {
                        input.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error closing streams", e);
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
} 