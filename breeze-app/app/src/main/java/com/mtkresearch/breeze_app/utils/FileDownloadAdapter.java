package com.mtkresearch.breeze_app.utils;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.mtkresearch.breeze_app.R;

import java.util.ArrayList;
import java.util.List;

public class FileDownloadAdapter extends RecyclerView.Adapter<FileDownloadAdapter.FileViewHolder> {

    public static class FileDownloadStatus {
        private final AppConstants.DownloadFileInfo fileInfo;
        private int status = AppConstants.DOWNLOAD_STATUS_PENDING;
        private int progress = 0;
        private long downloadedBytes = 0;
        private long actualFileSize = 0; // Track the actual file size from server
        private String errorMessage = null;

        public FileDownloadStatus(AppConstants.DownloadFileInfo fileInfo) {
            this.fileInfo = fileInfo;
            this.actualFileSize = fileInfo.fileSize; // Initialize with estimated size
        }

        public AppConstants.DownloadFileInfo getFileInfo() {
            return fileInfo;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }

        public int getProgress() {
            return progress;
        }

        public void setProgress(int progress) {
            this.progress = progress;
        }

        public long getDownloadedBytes() {
            return downloadedBytes;
        }

        public void setDownloadedBytes(long downloadedBytes) {
            this.downloadedBytes = downloadedBytes;
        }
        
        public long getTotalBytes() {
            return actualFileSize > 0 ? actualFileSize : fileInfo.fileSize;
        }
        
        public void setActualFileSize(long actualFileSize) {
            this.actualFileSize = actualFileSize;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public String getStatusText(android.content.Context context) {
            switch (status) {
                case AppConstants.DOWNLOAD_STATUS_PENDING:
                    return context.getString(R.string.download_status_pending);
                case AppConstants.DOWNLOAD_STATUS_IN_PROGRESS:
                    return context.getString(R.string.download_status_in_progress, progress);
                case AppConstants.DOWNLOAD_STATUS_PAUSED:
                    return context.getString(R.string.download_status_paused);
                case AppConstants.DOWNLOAD_STATUS_COMPLETED:
                    return context.getString(R.string.download_status_completed);
                case AppConstants.DOWNLOAD_STATUS_FAILED:
                    return context.getString(R.string.download_status_failed);
                default:
                    return "";
            }
        }
        
        // For backward compatibility
        public String getStatusText() {
            // Fallback to hardcoded strings if context is not available
            switch (status) {
                case AppConstants.DOWNLOAD_STATUS_PENDING:
                    return "Pending";
                case AppConstants.DOWNLOAD_STATUS_IN_PROGRESS:
                    return progress + "%";
                case AppConstants.DOWNLOAD_STATUS_PAUSED:
                    return "Paused";
                case AppConstants.DOWNLOAD_STATUS_COMPLETED:
                    return "Completed";
                case AppConstants.DOWNLOAD_STATUS_FAILED:
                    return "Failed";
                default:
                    return "";
            }
        }
    }

    private final List<FileDownloadStatus> files = new ArrayList<>();
    private final android.content.Context context;

    public FileDownloadAdapter(android.content.Context context) {
        this.context = context;
    }

    public void setFiles(List<FileDownloadStatus> files) {
        this.files.clear();
        if (files != null) {
            this.files.addAll(files);
        }
        notifyDataSetChanged();
    }

    public void updateFileProgress(int position, int progress, long downloadedBytes) {
        if (position >= 0 && position < files.size()) {
            FileDownloadStatus file = files.get(position);
            file.setProgress(progress);
            file.setDownloadedBytes(downloadedBytes);
            file.setStatus(AppConstants.DOWNLOAD_STATUS_IN_PROGRESS);
            notifyItemChanged(position);
        }
    }

    public void updateFileProgress(int position, int progress, long downloadedBytes, long actualFileSize) {
        if (position >= 0 && position < files.size()) {
            FileDownloadStatus file = files.get(position);
            file.setProgress(progress);
            file.setDownloadedBytes(downloadedBytes);
            file.setActualFileSize(actualFileSize);
            file.setStatus(AppConstants.DOWNLOAD_STATUS_IN_PROGRESS);
            notifyItemChanged(position);
        }
    }

    public void updateFileStatus(int position, int status) {
        if (position >= 0 && position < files.size()) {
            files.get(position).setStatus(status);
            notifyItemChanged(position);
        }
    }
    
    public void updateFileStatus(int position, int status, String errorMessage) {
        if (position >= 0 && position < files.size()) {
            FileDownloadStatus file = files.get(position);
            file.setStatus(status);
            file.setErrorMessage(errorMessage);
            notifyItemChanged(position);
        }
    }
    
    public List<FileDownloadStatus> getFiles() {
        return new ArrayList<>(files);
    }

    public FileDownloadStatus getItem(int position) {
        if (position >= 0 && position < files.size()) {
            return files.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileDownloadStatus file = files.get(position);
        
        holder.fileName.setText(file.getFileInfo().displayName);
        
        // Use the actual file size if available, otherwise use the estimated size
        long sizeToDisplay = file.getTotalBytes() > 0 ? file.getTotalBytes() : file.getFileInfo().fileSize;
        String formattedSize = Formatter.formatFileSize(context, sizeToDisplay);
        
        if (file.getStatus() == AppConstants.DOWNLOAD_STATUS_IN_PROGRESS && file.getDownloadedBytes() > 0) {
            // Show downloaded / total size
            String formattedDownloaded = Formatter.formatFileSize(context, file.getDownloadedBytes());
            holder.fileSize.setText(context.getString(R.string.download_size_format, formattedDownloaded, formattedSize));
        } else {
            holder.fileSize.setText(formattedSize);
        }
        
        // Set the status text and color based on status
        holder.downloadStatus.setText(file.getStatusText(context));
        int textColor;
        switch (file.getStatus()) {
            case AppConstants.DOWNLOAD_STATUS_COMPLETED:
                textColor = context.getResources().getColor(android.R.color.holo_green_dark);
                break;
            case AppConstants.DOWNLOAD_STATUS_FAILED:
                textColor = context.getResources().getColor(android.R.color.holo_red_dark);
                break;
            case AppConstants.DOWNLOAD_STATUS_PAUSED:
                textColor = context.getResources().getColor(android.R.color.holo_orange_dark);
                break;
            default:
                textColor = context.getResources().getColor(R.color.primary);
                break;
        }
        holder.downloadStatus.setTextColor(textColor);
        
        // Update progress bar
        holder.fileProgressBar.setProgress(file.getProgress());
        
        // Handle visibility of progress bar based on status
        if (file.getStatus() == AppConstants.DOWNLOAD_STATUS_COMPLETED) {
            holder.fileProgressBar.setProgress(100);
        } else if (file.getStatus() == AppConstants.DOWNLOAD_STATUS_FAILED) {
            holder.fileProgressBar.setProgress(0);
        }
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView fileName;
        TextView fileSize;
        TextView downloadStatus;
        ProgressBar fileProgressBar;

        FileViewHolder(View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileName);
            fileSize = itemView.findViewById(R.id.fileSize);
            downloadStatus = itemView.findViewById(R.id.downloadStatus);
            fileProgressBar = itemView.findViewById(R.id.fileProgressBar);
        }
    }
}
