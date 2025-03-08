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

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.mtkresearch.breeze_app.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

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

        // Initialize UI components
        progressBar = findViewById(R.id.progressBar);
        statusText = findViewById(R.id.statusText);
        TextView overallProgressLabel = findViewById(R.id.overallProgressLabel);
        overallProgressText = overallProgressLabel; // Use the existing label as the text view
        fileListTitle = findViewById(R.id.filesLabel);
        downloadButton = findViewById(R.id.downloadButton);
        cancelButton = findViewById(R.id.cancelButton);
        pauseResumeButton = findViewById(R.id.pauseResumeButton);
        retryButton = findViewById(R.id.retryButton);
        fileRecyclerView = findViewById(R.id.filesList);
        TextView messageText = findViewById(R.id.messageText);

        // Prepare download file list to calculate total size 
        prepareDownloadFileList();
        
        // Calculate total download size for display
        long totalSize = 0;
        for (AppConstants.DownloadFileInfo fileInfo : downloadFiles) {
            totalSize += fileInfo.fileSize;
            Log.d(TAG, "Adding file to total: " + fileInfo.fileName + ", size: " + formatFileSize(fileInfo.fileSize) + " (" + fileInfo.fileSize + " bytes)");
        }
        String formattedSize = formatFileSize(totalSize);
        Log.d(TAG, "Total download size: " + formattedSize + " (" + totalSize + " bytes) from " + downloadFiles.size() + " files");
        
        // Calculate SI unit GB for logging
        double sizeInGb = totalSize / (1000.0 * 1000.0 * 1000.0);
        Log.d(TAG, String.format("For confirmation - Total size in SI units: %.2f GB", sizeInGb));
        
        // Set appropriate message based on download mode
        if (downloadMode == DownloadMode.TTS) {
            messageText.setText(R.string.model_missing_message_tts);
        } else {
            // For LLM mode, first show a message indicating we're calculating the size
            // The actual size will be updated by fetchFileSizesAsync() once available
            String initialMessage = getContext().getString(R.string.model_missing_message, "...calculating...");
            messageText.setText(initialMessage);
            Log.d(TAG, "Set initial dialog message while fetching file sizes");
        }

        // Initialize RecyclerView
        fileRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        fileAdapter = new FileDownloadAdapter(getContext());
        fileRecyclerView.setAdapter(fileAdapter);
        
        // Hide file list initially
        fileListTitle.setVisibility(View.GONE);
        fileRecyclerView.setVisibility(View.GONE);
        overallProgressText.setVisibility(View.GONE);
        pauseResumeButton.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);

        // Set up button click listeners
        downloadButton.setOnClickListener(v -> startDownload());
        cancelButton.setOnClickListener(v -> {
            Log.d(TAG, "Cancel button clicked - attempting to stop download");
            
            // Properly cancel any running download task
            if (downloadTask != null) {
                // Set the cancellation flag first
                downloadTask.isCancelled = true;
                
                // Then call AsyncTask's cancel method with interrupt flag
                downloadTask.cancel(true);
                
                // Update UI immediately to provide feedback
                statusText.setText(R.string.download_cancelled);
                Toast.makeText(getContext(), R.string.download_cancelled, Toast.LENGTH_SHORT).show();
                
                Log.d(TAG, "Download task cancellation initiated");
            }
            
            // Dismiss the dialog
            dismiss();
        });
        
        retryButton.setOnClickListener(v -> {
            // Hide retry button and show download progress UI
            retryButton.setVisibility(View.GONE);
            progressBar.setProgress(0);
            
            // Reset file statuses
            for (int i = 0; i < fileAdapter.getItemCount(); i++) {
                fileAdapter.updateFileStatus(i, AppConstants.DOWNLOAD_STATUS_PENDING);
            }
            
            // Start download again
            startDownload();
        });
        
        pauseResumeButton.setOnClickListener(v -> {
            if (isPaused.get()) {
                // Resume download
                isPaused.set(false);
                pauseResumeButton.setText(R.string.pause);
                statusText.setText(R.string.download_resuming);
                synchronized (downloadTask) {
                    downloadTask.notifyAll(); // Notify waiting threads to continue
                }
            } else {
                // Pause download
                isPaused.set(true);
                pauseResumeButton.setText(R.string.resume);
                statusText.setText(R.string.download_paused);
            }
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

        // Prepare download file list
        prepareDownloadFileList();
        if (downloadFiles.isEmpty()) {
            Toast.makeText(getContext(), R.string.error_preparing_download, Toast.LENGTH_SHORT).show();
            return;
        }

        // Update UI for download start
        downloadButton.setEnabled(false);
        downloadButton.setAlpha(AppConstants.DISABLED_ALPHA);
        downloadButton.setVisibility(View.GONE); // Hide the download button completely
        retryButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        fileListTitle.setVisibility(View.VISIBLE);
        fileRecyclerView.setVisibility(View.VISIBLE);
        overallProgressText.setVisibility(View.VISIBLE);
        pauseResumeButton.setVisibility(View.VISIBLE);
        overallProgressText.setText(R.string.overall_progress);
        fileListTitle.setText(R.string.model_files);
        pauseResumeButton.setText(R.string.pause);
        
        // Reset pause state
        isPaused.set(false);

        // Setup the files in the adapter
        // Convert DownloadFileInfo objects to FileDownloadStatus objects
        List<FileDownloadAdapter.FileDownloadStatus> fileStatusList = new ArrayList<>();
        for (AppConstants.DownloadFileInfo fileInfo : downloadFiles) {
            fileStatusList.add(new FileDownloadAdapter.FileDownloadStatus(fileInfo));
        }
        fileAdapter.setFiles(fileStatusList);
        
        // Display initial status
        statusText.setText(downloadMode == DownloadMode.TTS ? R.string.download_progress_tts : R.string.downloading);
        
        // Start download task
        downloadTask = new DownloadTask(modelDir);
        downloadTask.execute();
    }
    
    private void prepareDownloadFileList() {
        downloadFiles.clear();
        
        if (downloadMode == DownloadMode.TTS) {
            // Add TTS model files to the list
            for (int i = 0; i < AppConstants.TTS_MODEL_DOWNLOAD_URLS.length; i += 2) {
                String url = AppConstants.TTS_MODEL_DOWNLOAD_URLS[i]; // Use primary URL
                String fileName = getFileNameFromUrl(url);
                
                // Get dynamic file size from server first
                long fileSize = estimateFileSize(url);
                Log.d(TAG, "TTS model file: " + fileName + ", size: " + formatFileSize(fileSize));
                
                downloadFiles.add(new AppConstants.DownloadFileInfo(
                    url,
                    fileName,
                    "TTS Model: " + fileName,
                    AppConstants.FILE_TYPE_TTS_MODEL,
                    fileSize
                ));
            }
        } else {
            // Add LLM model files to the list with temporary file sizes
            // We'll update these sizes asynchronously when we get the actual values
            
            // First, add tokenizer
            String tokenizerUrl = AppConstants.MODEL_DOWNLOAD_URLS[0];
            String tokenizerFileName = AppConstants.LLM_TOKENIZER_FILE;
            
            // Use a temporary size - will be updated asynchronously
            long tempTokenizerSize = 0L; 
            
            AppConstants.DownloadFileInfo tokenizerInfo = new AppConstants.DownloadFileInfo(
                tokenizerUrl,
                tokenizerFileName,
                "Tokenizer",
                AppConstants.FILE_TYPE_TOKENIZER,
                tempTokenizerSize
            );
            downloadFiles.add(tokenizerInfo);
            
            // Then add the main model file 
            String modelUrl = AppConstants.MODEL_DOWNLOAD_URLS[1];
            String modelFileName = AppConstants.BREEZE_MODEL_FILE;
            String modelDisplayName = "Breeze Tiny Instruct v0.1 (2048)"; // More user-friendly name
            
            // Use a temporary size - will be updated asynchronously
            long tempModelSize = 0L;
            
            AppConstants.DownloadFileInfo modelInfo = new AppConstants.DownloadFileInfo(
                modelUrl,
                modelFileName,
                modelDisplayName,
                AppConstants.FILE_TYPE_LLM,
                tempModelSize
            );
            downloadFiles.add(modelInfo);
            
            // Request file sizes asynchronously
            fetchFileSizesAsync();
        }
    }
    
    /**
     * Fetches file sizes asynchronously to avoid blocking the main thread
     */
    private void fetchFileSizesAsync() {
        // Create a background thread for network operations
        new Thread(() -> {
            boolean allSizesUpdated = true;
            long totalSize = 0;
            
            // Process each file in the download list
            for (int i = 0; i < downloadFiles.size(); i++) {
                // Skip detailed logging if verbose logging is disabled
                if (!AppConstants.ENABLE_DOWNLOAD_VERBOSE_LOGGING) {
                    // Just get the file size without logging
                    AppConstants.DownloadFileInfo fileInfo = downloadFiles.get(i);
                    long fileSize = getFileSizeFromHeadRequest(fileInfo.url);
                    
                    // If we couldn't get size from server, use fallback estimates
                    if (fileSize <= 0) {
                        if (fileInfo.url.contains("tokenizer")) {
                            fileSize = 2_500_000L; // ~2.5MB estimate
                        } else if (fileInfo.url.contains("breeze") && fileInfo.url.contains(".pte")) {
                            fileSize = 6_500_000_000L; // ~6.5GB estimate
                        } else {
                            fileSize = 50_000_000L; // 50MB default estimate
                        }
                    }
                    
                    // Create a new DownloadFileInfo with the updated size and replace the old one
                    final int index = i;
                    final long updatedSize = fileSize;
                    
                    // Create a new object with the updated size
                    AppConstants.DownloadFileInfo updatedFileInfo = new AppConstants.DownloadFileInfo(
                        fileInfo.url,
                        fileInfo.fileName,
                        fileInfo.displayName,
                        fileInfo.fileType,
                        updatedSize
                    );
                    
                    // Replace the old object with the new one at the same index
                    downloadFiles.set(index, updatedFileInfo);
                    totalSize += updatedSize;
                    continue;
                }
                
                // Full verbose logging below this point
                AppConstants.DownloadFileInfo fileInfo = downloadFiles.get(i);
                
                // Get size from server via HEAD request
                long fileSize = getFileSizeFromHeadRequest(fileInfo.url);
                
                // If we couldn't get size from server, use fallback estimates
                if (fileSize <= 0) {
                    Log.w(TAG, "Failed to get file size for: " + fileInfo.url);
                    allSizesUpdated = false;
                    
                    // Fallback estimates
                    if (fileInfo.url.contains("tokenizer")) {
                        fileSize = 2_500_000L; // ~2.5MB estimate
                        Log.d(TAG, "Using fallback size for tokenizer: " + formatFileSize(fileSize));
                    } else if (fileInfo.url.contains("breeze") && fileInfo.url.contains(".pte")) {
                        fileSize = 6_500_000_000L; // ~6.5GB estimate
                        Log.d(TAG, "Using fallback size for model: " + formatFileSize(fileSize));
                    } else {
                        fileSize = 50_000_000L; // 50MB default estimate
                    }
                } else {
                    Log.d(TAG, "Got file size from server for " + fileInfo.fileName + ": " + formatFileSize(fileSize));
                }
                
                // Create a new DownloadFileInfo with the updated size and replace the old one
                final int index = i;
                final long updatedSize = fileSize;
                
                // Create a new object with the updated size
                AppConstants.DownloadFileInfo updatedFileInfo = new AppConstants.DownloadFileInfo(
                    fileInfo.url,
                    fileInfo.fileName,
                    fileInfo.displayName,
                    fileInfo.fileType,
                    updatedSize
                );
                
                // Replace the old object with the new one at the same index
                downloadFiles.set(index, updatedFileInfo);
                totalSize += updatedSize;
            }
            
            // Calculate and format the total size
            final long finalTotalSize = totalSize;
            final String formattedTotalSize = formatFileSize(totalSize);
            
            // Update UI on main thread
            mainHandler.post(() -> {
                // Update the dialog message with the new size
                TextView messageText = findViewById(R.id.messageText);
                if (messageText != null && downloadMode == DownloadMode.LLM) {
                    String message = getContext().getString(R.string.model_missing_message, formattedTotalSize);
                    messageText.setText(message);
                    
                    if (AppConstants.ENABLE_DOWNLOAD_VERBOSE_LOGGING) {
                        Log.d(TAG, "Updated dialog message with size from server: " + formattedTotalSize + 
                              " (" + finalTotalSize + " bytes)");
                    }
                }
                
                // Log for debugging
                if (AppConstants.ENABLE_DOWNLOAD_VERBOSE_LOGGING && downloadFiles.size() >= 2) {
                    Log.d(TAG, "Updated file sizes - Tokenizer: " + formatFileSize(downloadFiles.get(0).fileSize) + 
                          ", Model: " + formatFileSize(downloadFiles.get(1).fileSize) + 
                          ", Total: " + formattedTotalSize);
                }
            });
            
        }).start();
    }
    
    /**
     * Format file size to human-readable format
     */
    private String formatFileSize(long size) {
        if (size <= 0) return "Unknown size";
        
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        // Use 1000 instead of 1024 for decimal SI units instead of binary units
        int digitGroups = (int) (Math.log10(size) / Math.log10(1000));
        
        // Keep digitGroups within the units array bounds
        digitGroups = Math.min(digitGroups, units.length - 1);
        
        // Use 1000 for division - SI decimal units
        return String.format("%.2f %s", size / Math.pow(1000, digitGroups), units[digitGroups]);
    }
    
    private String getFileNameFromUrl(String url) {
        String[] parts = url.split("/");
        String lastPart = parts[parts.length - 1];
        int queryIndex = lastPart.indexOf('?');
        return queryIndex > 0 ? lastPart.substring(0, queryIndex) : lastPart;
    }
    
    /**
     * Estimates or retrieves the file size for a given URL.
     * First tries to get the size from the server using a HEAD request.
     * Falls back to approximate sizes if that fails.
     */
    private long estimateFileSize(String url) {
        // Try to get the actual size from server via HEAD request first
        // This is always the preferred method to ensure accurate size display
        long size = getFileSizeFromHeadRequest(url);
        if (size > 0) {
            Log.d(TAG, "Using actual file size from HEAD request: " + formatFileSize(size) + " for " + url);
            return size;
        }

        // If HEAD request failed, use reasonable fallback estimates
        // These are only used if the network request fails
        Log.d(TAG, "HEAD request failed, using fallback file size estimate for " + url);
        
        // Fallback estimates only if server request fails
        if (url.contains("tokenizer")) {
            Log.d(TAG, "Using fallback size for tokenizer");
            return 2_500_000L; // ~2.5MB estimate for tokenizer
        } else if (url.contains("breeze") && url.contains(".pte")) {
            Log.d(TAG, "Using fallback size for LLM model");
            return 6_500_000_000L; // ~6.5GB estimate for main LLM
        } else if (url.contains("breeze2-vits.onnx")) {
            return 41_943_040L; // ~40MB estimate for TTS model
        } else if (url.contains("lexicon.txt")) {
            return 2_097_152L; // ~2MB estimate for lexicon
        } else if (url.contains("tokens.txt")) {
            return 1_048_576L; // ~1MB estimate for tokens
        } else {
            // Default fallback
            return 50_000_000L; // 50MB default conservative estimate
        }
    }
    
    /**
     * Makes a HEAD request to get the file size from Content-Length header.
     * Handles redirects and retries with proper error reporting.
     * 
     * @param urlString URL to check
     * @return file size in bytes, or -1 if unable to determine
     */
    private long getFileSizeFromHeadRequest(String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000); // 5 second timeout for network conditions
            connection.setReadTimeout(5000);
            
            // Set common headers for better compatibility
            for (String[] header : AppConstants.DOWNLOAD_HEADERS) {
                connection.setRequestProperty(header[0], header[1]);
            }
            
            // Add a user agent to avoid server restrictions
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) BreezeApp");
            
            int responseCode = connection.getResponseCode();
            Log.d(TAG, "HEAD request for " + urlString + " responded with code: " + responseCode);
            
            // Handle redirects manually if needed
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                
                String redirectUrl = connection.getHeaderField("Location");
                if (redirectUrl != null) {
                    connection.disconnect();
                    Log.d(TAG, "Following redirect to: " + redirectUrl);
                    return getFileSizeFromHeadRequest(redirectUrl);
                }
            }
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                long contentLength = connection.getContentLengthLong();
                
                // Log all relevant headers for debugging
                Map<String, List<String>> headers = connection.getHeaderFields();
                StringBuilder headerLog = new StringBuilder("Response headers for ").append(urlString).append(":\n");
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if (entry.getKey() != null) {
                        headerLog.append(" - ").append(entry.getKey()).append(": ")
                                 .append(String.join(", ", entry.getValue())).append("\n");
                    }
                }
                Log.d(TAG, headerLog.toString());
                
                if (contentLength > 0) {
                    Log.d(TAG, "Content-Length: " + contentLength + " bytes (" + 
                          formatFileSize(contentLength) + ") for " + urlString);
                    return contentLength;
                } else {
                    Log.w(TAG, "Server didn't provide Content-Length header for " + urlString);
                }
            } else {
                Log.w(TAG, "HEAD request failed with response code: " + responseCode);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get file size from HEAD request: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return -1;
    }

    private class DownloadTask extends AsyncTask<Void, Integer, Boolean> {
        private Exception error;
        private final File modelDir;
        private volatile boolean isCancelled = false;

        public DownloadTask(File modelDir) {
            this.modelDir = modelDir;
        }

        public boolean isDownloadCancelled() {
            return super.isCancelled() || isCancelled;
        }
        
        @Override
        protected Boolean doInBackground(Void... params) {
            boolean allFilesDownloaded = true;
            
            // Process each file in the download list
            for (int i = 0; i < downloadFiles.size(); i++) {
                AppConstants.DownloadFileInfo fileInfo = downloadFiles.get(i);
                final int fileIndex = i;
                
                // Update UI to show which file is being downloaded
                mainHandler.post(() -> {
                    statusText.setText(getContext().getString(R.string.downloading_file, fileInfo.displayName));
                    fileAdapter.updateFileStatus(fileIndex, AppConstants.DOWNLOAD_STATUS_IN_PROGRESS);
                });
                
                if (!downloadFile(fileInfo, fileIndex)) {
                    allFilesDownloaded = false;
                    
                    // Try alternative URL if available (for LLM models)
                    if (downloadMode == DownloadMode.LLM && AppConstants.FILE_TYPE_LLM.equals(fileInfo.fileType)) {
                        // Loop through alternative URLs
                        for (int j = 2; j < AppConstants.MODEL_DOWNLOAD_URLS.length; j++) {
                            AppConstants.DownloadFileInfo alternativeFile = new AppConstants.DownloadFileInfo(
                                AppConstants.MODEL_DOWNLOAD_URLS[j],
                                fileInfo.fileName,
                                fileInfo.displayName + " (Alternative Source)",
                                fileInfo.fileType,
                                fileInfo.fileSize
                            );
                            
                            if (downloadFile(alternativeFile, fileIndex)) {
                                allFilesDownloaded = true;
                                break;
                            }
                            
                            if (isCancelled()) {
                                return false;
                            }
                        }
                    }
                    
                    // If still unsuccessful, mark as failed and continue to next file
                    if (!allFilesDownloaded) {
                        final String errorMsg = error != null ? error.getMessage() : "Unknown error";
                        mainHandler.post(() -> {
                            fileAdapter.updateFileStatus(fileIndex, AppConstants.DOWNLOAD_STATUS_FAILED, errorMsg);
                        });
                    }
                }
                
                if (isCancelled()) {
                    return false;
                }
            }
            
            return allFilesDownloaded;
        }
        
        private boolean downloadFile(AppConstants.DownloadFileInfo fileInfo, int fileIndex) {
            InputStream input = null;
            FileOutputStream output = null;
            HttpURLConnection connection = null;

            try {
                // Check for cancellation at the start
                if (isDownloadCancelled()) {
                    // Only log if verbose logging is enabled
                    if (AppConstants.ENABLE_DOWNLOAD_VERBOSE_LOGGING) {
                        Log.d(TAG, "Download cancelled before starting file: " + fileInfo.fileName);
                    }
                    return false;
                }
                
                // Check available storage space first
                long availableSpace = getContext().getFilesDir().getFreeSpace() / (1024 * 1024); // Convert to MB
                long requiredSpace = Math.max(fileInfo.fileSize / (1024 * 1024), 100); // At least 100MB or file size in MB
                
                // Only log if verbose logging is enabled
                if (AppConstants.ENABLE_DOWNLOAD_VERBOSE_LOGGING) {
                    Log.d(TAG, String.format("Download attempt - URL: %s, File: %s, Available: %dMB, Required: %dMB",
                        fileInfo.url, fileInfo.fileName, availableSpace, requiredSpace));
                }

                if (availableSpace < requiredSpace) {
                    throw new IOException("Insufficient storage space. Need " + requiredSpace + "MB free.");
                }

                // Setup files
                File outputFile = new File(modelDir, fileInfo.fileName);
                File tempFile = new File(modelDir, fileInfo.fileName + AppConstants.MODEL_DOWNLOAD_TEMP_EXTENSION);
                long existingLength = 0;

                // Check for cancellation before network operations
                if (isDownloadCancelled()) {
                    // Only log if verbose logging is enabled
                    if (AppConstants.ENABLE_DOWNLOAD_VERBOSE_LOGGING) {
                        Log.d(TAG, "Download cancelled before establishing connection: " + fileInfo.fileName);
                    }
                    return false;
                }

                // Check for existing temporary file
                if (tempFile.exists()) {
                    existingLength = tempFile.length();
                    Log.i(TAG, "Found existing partial download: " + existingLength + " bytes");
                    
                    // Update UI with existing progress
                    if (fileInfo.fileSize > 0 && existingLength > 0) {
                        final int progress = (int) (existingLength * 100 / fileInfo.fileSize);
                        final long downloadedBytes = existingLength;
                        mainHandler.post(() -> {
                            fileAdapter.updateFileProgress(fileIndex, progress, downloadedBytes);
                        });
                    }
                }

                URL url = new URL(fileInfo.url);
                connection = (HttpURLConnection) url.openConnection();
                
                // Set all required headers
                for (String[] header : AppConstants.DOWNLOAD_HEADERS) {
                    connection.setRequestProperty(header[0], header[1]);
                }

                // Add Range header if we have partial file
                if (existingLength > 0) {
                    connection.setRequestProperty("Range", "bytes=" + existingLength + "-");
                    Log.d(TAG, "Resuming download from byte " + existingLength);
                }
                
                // Set timeouts
                connection.setConnectTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                connection.setReadTimeout((int) AppConstants.MODEL_DOWNLOAD_TIMEOUT_MS);
                
                // Connect to the URL
                connection.connect();
                
                // Handle redirects
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    
                    String redirectUrl = connection.getHeaderField("Location");
                    if (redirectUrl == null) {
                        Log.w(TAG, "Redirect URL is null for " + fileInfo.url);
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
                        fileInfo.url, responseCode, errorMessage));
                    return false;
                }

                // Get the file length
                long fileLength = connection.getContentLengthLong();
                if (isResuming) {
                    String contentRange = connection.getHeaderField("Content-Range");
                    if (contentRange != null) {
                        String[] parts = contentRange.split("/");
                        if (parts.length == 2) {
                            try {
                                fileLength = Long.parseLong(parts[1]);
                                Log.d(TAG, "Content-Range total size: " + ModelDownloadDialog.this.formatFileSize(fileLength));
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Failed to parse Content-Range: " + contentRange, e);
                            }
                        }
                    }
                }
                
                // Log content length information for debugging
                Log.d(TAG, String.format("Content-Length: %d, Estimated Size: %d, Resuming: %b", 
                      fileLength, fileInfo.fileSize, isResuming));
                
                // Always update the file size if content length is available
                if (fileLength > 0) {
                    final long updatedSize = fileLength;
                    final long finalExistingLength = existingLength; // Make existingLength final for lambda
                    Log.d(TAG, "Updating file size for " + fileInfo.fileName + " from " + 
                          ModelDownloadDialog.this.formatFileSize(fileInfo.fileSize) + " to " + ModelDownloadDialog.this.formatFileSize(updatedSize));
                    mainHandler.post(() -> {
                        // Use the actual file length for progress calculation
                        int progressPercent = (int) (finalExistingLength * 100 / updatedSize);
                        fileAdapter.updateFileProgress(fileIndex, progressPercent, finalExistingLength, updatedSize);
                        // Force update overall progress to reflect the new file size
                        updateOverallProgress();
                    });
                } else if (fileLength <= 0) {
                    // If content length is not provided, use the estimated size
                    fileLength = fileInfo.fileSize;
                    Log.w(TAG, "No Content-Length header, using estimated size: " + ModelDownloadDialog.this.formatFileSize(fileLength));
                }
                
                input = connection.getInputStream();
                
                // Open output in append mode if resuming
                output = new FileOutputStream(tempFile, isResuming);

                byte[] data = new byte[AppConstants.MODEL_DOWNLOAD_BUFFER_SIZE];
                long total = existingLength;
                int count;
                int lastProgress = 0;
                long lastUIUpdateTime = System.currentTimeMillis();

                while ((count = input.read(data)) != -1) {
                    // Handle pause/resume
                    while (isPaused.get() && !isDownloadCancelled()) {
                        try {
                            // Update UI to show paused status periodically
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastUIUpdateTime > 500) { // Update UI every 500ms while paused
                                lastUIUpdateTime = currentTime;
                                final int fileProgress = (int) (total * 100 / fileLength);
                                final long downloadedBytes = total;
                                final long actualFileLength = fileLength;
                                mainHandler.post(() -> {
                                    fileAdapter.updateFileStatus(fileIndex, AppConstants.DOWNLOAD_STATUS_PAUSED);
                                    fileAdapter.updateFileProgress(fileIndex, fileProgress, downloadedBytes, actualFileLength);
                                    updateOverallProgress();
                                });
                            }
                            
                            synchronized (downloadTask) {
                                downloadTask.wait(500); // Wait until resumed or every 500ms to check for cancel
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        
                        // High priority cancellation check inside pause loop
                        if (isDownloadCancelled()) {
                            Log.d(TAG, "Download cancelled while paused for file: " + fileInfo.fileName);
                            return false;
                        }
                    }
                    
                    // If just resumed, update status to downloading
                    if (!isPaused.get() && !isDownloadCancelled()) {
                        mainHandler.post(() -> {
                            fileAdapter.updateFileStatus(fileIndex, AppConstants.DOWNLOAD_STATUS_IN_PROGRESS);
                        });
                    }
                    
                    // High priority cancellation check
                    if (isDownloadCancelled()) {
                        Log.d(TAG, "Download cancelled during download loop for: " + fileInfo.fileName);
                        try {
                            input.close();
                            output.close();
                        } catch (IOException e) {
                            Log.w(TAG, "Error closing streams during cancellation: " + e.getMessage());
                        }
                        // Important: Don't delete the temp file so download can be resumed later
                        Log.d(TAG, "Preserving temp file for future resume: " + tempFile.getPath());
                        return false;
                    }
                    
                    total += count;
                    output.write(data, 0, count);

                    if (fileLength > 0) {
                        int fileProgress = (int) (total * 100 / fileLength);
                        long currentTime = System.currentTimeMillis();
                        
                        // Update progress in UI if it has changed significantly or enough time has passed
                        if (fileProgress > lastProgress || currentTime - lastUIUpdateTime > 1000) {
                            final int progress = fileProgress;
                            final long downloadedBytes = total;
                            final long actualFileLength = fileLength; // Use actual file length from server
                            mainHandler.post(() -> {
                                fileAdapter.updateFileProgress(fileIndex, progress, downloadedBytes, actualFileLength);
                                // Also update overall progress
                                updateOverallProgress();
                            });
                            lastProgress = fileProgress;
                            lastUIUpdateTime = currentTime;
                        }
                    }
                }

                // Close streams before moving file
                output.close();
                output = null;
                input.close();
                input = null;

                // Verify the downloaded file
                if (!tempFile.exists()) {
                    throw new IOException("Download failed - temporary file missing");
                }

                // Move temp file to final location
                if (!tempFile.renameTo(outputFile)) {
                    throw new IOException("Failed to move temporary file to final location");
                }

                // Update file status to complete
                mainHandler.post(() -> {
                    fileAdapter.updateFileStatus(fileIndex, AppConstants.DOWNLOAD_STATUS_COMPLETED);
                    updateOverallProgress();
                });

                // Log successful download
                Log.i(TAG, "Successfully downloaded " + fileInfo.fileName + " from " + fileInfo.url);
                return true;

            } catch (Exception e) {
                error = e;
                Log.e(TAG, "Error downloading from " + fileInfo.url + ": " + e.getMessage(), e);
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

        /**
         * Updates the overall progress in the UI based on individual file progress
         */
        private void updateOverallProgress() {
            // Skip detailed logging if verbose logging is disabled
            if (!AppConstants.ENABLE_DOWNLOAD_VERBOSE_LOGGING) {
                // Still calculate progress but skip logging
                long totalSize = 0;
                long totalDownloaded = 0;
                boolean allComplete = true;
                
                List<FileDownloadAdapter.FileDownloadStatus> files = fileAdapter.getFiles();
                for (int i = 0; i < files.size(); i++) {
                    FileDownloadAdapter.FileDownloadStatus file = files.get(i);
                    long fileSize = file.getTotalBytes();
                    long fileDownloaded = file.getDownloadedBytes();
                    
                    totalSize += fileSize;
                    totalDownloaded += fileDownloaded;
                    
                    if (file.getStatus() != AppConstants.DOWNLOAD_STATUS_COMPLETED) {
                        allComplete = false;
                    }
                }
                
                // Calculate overall progress based on total downloaded bytes and total size
                int overallProgress = 0;
                if (totalSize > 0) {
                    overallProgress = (int) (totalDownloaded * 100 / totalSize);
                }
                
                progressBar.setProgress(overallProgress);
                
                // Update status message based on download state
                if (isPaused.get()) {
                    statusText.setText(R.string.download_paused);
                } else if (allComplete) {
                    statusText.setText(R.string.download_complete);
                } else {
                    // Format downloaded/total size
                    String downloadedStr = ModelDownloadDialog.this.formatFileSize(totalDownloaded);
                    String totalStr = ModelDownloadDialog.this.formatFileSize(totalSize);
                    statusText.setText(downloadedStr + " / " + totalStr + " ("+overallProgress+"%)" );
                }
                return;
            }
            
            // Full verbose logging below this point
            long totalSize = 0;
            long totalDownloaded = 0;
            boolean allComplete = true;
            
            // Log individual files details
            Log.d(TAG, "--- Calculating overall progress ---");
            List<FileDownloadAdapter.FileDownloadStatus> files = fileAdapter.getFiles();
            for (int i = 0; i < files.size(); i++) {
                FileDownloadAdapter.FileDownloadStatus file = files.get(i);
                long fileSize = file.getTotalBytes();
                long fileDownloaded = file.getDownloadedBytes();
                
                // Add logging for debugging
                Log.d(TAG, String.format("File %d: %s - Size: %s (%d bytes), Downloaded: %s (%d bytes), Status: %d", 
                      i+1,
                      file.getFileInfo().fileName,
                      ModelDownloadDialog.this.formatFileSize(fileSize),
                      fileSize,
                      ModelDownloadDialog.this.formatFileSize(fileDownloaded),
                      fileDownloaded,
                      file.getStatus()));
                
                totalSize += fileSize;
                totalDownloaded += fileDownloaded;
                
                if (file.getStatus() != AppConstants.DOWNLOAD_STATUS_COMPLETED) {
                    allComplete = false;
                }
            }
            
            // Log total download progress
            Log.d(TAG, String.format("Overall - Total size: %s (%d bytes), Total downloaded: %s (%d bytes), Files: %d", 
                  ModelDownloadDialog.this.formatFileSize(totalSize),
                  totalSize,
                  ModelDownloadDialog.this.formatFileSize(totalDownloaded),
                  totalDownloaded,
                  files.size()));
            
            // Calculate overall progress based on total downloaded bytes and total size
            int overallProgress = 0;
            if (totalSize > 0) {
                overallProgress = (int) (totalDownloaded * 100 / totalSize);
            }
            
            progressBar.setProgress(overallProgress);
            
            // Update status message based on download state
            if (isPaused.get()) {
                statusText.setText(R.string.download_paused);
            } else if (allComplete) {
                statusText.setText(R.string.download_complete);
            } else {
                // Format downloaded/total size
                String downloadedStr = ModelDownloadDialog.this.formatFileSize(totalDownloaded);
                String totalStr = ModelDownloadDialog.this.formatFileSize(totalSize);
                
                // Log the exact values that will be displayed
                Log.d(TAG, String.format("Progress display: %s / %s (%d%%)", downloadedStr, totalStr, overallProgress));
                
                statusText.setText(downloadedStr + " / " + totalStr + " ("+overallProgress+"%)" );
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progressBar.setProgress(values[0]);
            // We don't need to update statusText here as it's handled by updateOverallProgress
            
            // Ensure button stays disabled during download
            downloadButton.setEnabled(false);
            downloadButton.setAlpha(AppConstants.DISABLED_ALPHA);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            // Hide pause/resume button when download is complete
            pauseResumeButton.setVisibility(View.GONE);
            
            if (success) {
                statusText.setText(R.string.download_complete);
                progressBar.setProgress(100);
                
                // Keep button disabled on success since download is complete
                downloadButton.setEnabled(false);
                downloadButton.setAlpha(AppConstants.DISABLED_ALPHA);
                retryButton.setVisibility(View.GONE);
                
                // Update all file statuses to completed (in case any were missed)
                for (int i = 0; i < fileAdapter.getItemCount(); i++) {
                    fileAdapter.updateFileStatus(i, AppConstants.DOWNLOAD_STATUS_COMPLETED);
                }
                
                Toast.makeText(getContext(), R.string.download_complete, Toast.LENGTH_SHORT).show();
                
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
                mainHandler.postDelayed(() -> dismiss(), 2000);
            } else {
                // Only re-enable button if download failed
                downloadButton.setEnabled(true);
                downloadButton.setAlpha(AppConstants.ENABLED_ALPHA);
                
                // Show retry button when download fails
                retryButton.setVisibility(View.VISIBLE);
                
                // Check if any files were successfully downloaded
                boolean anySuccess = false;
                for (FileDownloadAdapter.FileDownloadStatus file : fileAdapter.getFiles()) {
                    if (file.getStatus() == AppConstants.DOWNLOAD_STATUS_COMPLETED) {
                        anySuccess = true;
                        break;
                    }
                }
                
                // Display appropriate error message
                String errorMessage = error != null ? 
                    error.getMessage() : 
                    getContext().getString(R.string.error_all_download_attempts_failed);
                
                if (downloadMode == DownloadMode.TTS) {
                    statusText.setText(getContext().getString(R.string.download_failed_tts, ""));
                } else {
                    statusText.setText(getContext().getString(R.string.download_failed, ""));
                }
                
                // Show toast with more details
                String toastMessage = anySuccess ?
                    getContext().getString(R.string.download_partially_complete) :
                    getContext().getString(R.string.error_downloading_model, errorMessage);
                    
                Toast.makeText(getContext(), toastMessage, Toast.LENGTH_LONG).show();
            }
        }

        @Override
        protected void onCancelled() {
            Log.d(TAG, "Download task onCancelled called - performing cleanup");
            
            // Ensure our cancellation flag is set
            isCancelled = true;
            
            // Update UI to show cancellation (in case dialog isn't dismissed yet)
            mainHandler.post(() -> {
                statusText.setText(R.string.download_cancelled);
                progressBar.setProgress(0);
                
                // Update file statuses to show cancellation
                for (int i = 0; i < fileAdapter.getItemCount(); i++) {
                    FileDownloadAdapter.FileDownloadStatus status = fileAdapter.getItem(i);
                    if (status != null && status.getStatus() != AppConstants.DOWNLOAD_STATUS_COMPLETED) {
                        fileAdapter.updateFileStatus(i, AppConstants.DOWNLOAD_STATUS_FAILED, "Download cancelled");
                    }
                }
            });
            
            // Let the parent class handle any additional cleanup
            super.onCancelled();
        }
    }
} 