package com.mtkresearch.gai_android.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.mtkresearch.gai_android.R;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AudioListAdapter extends RecyclerView.Adapter<AudioListAdapter.ViewHolder> {
    private List<File> audioFiles;
    private OnAudioActionListener listener;
    private int currentlyPlayingPosition = -1;

    public interface OnAudioActionListener {
        void onReplayClick(File file);
        void onDeleteClick(File file);
    }

    public AudioListAdapter(List<File> audioFiles, OnAudioActionListener listener) {
        this.audioFiles = audioFiles;
        this.listener = listener;
    }

    public void onPlaybackCompleted() {
        if (currentlyPlayingPosition != -1) {
            int oldPosition = currentlyPlayingPosition;
            currentlyPlayingPosition = -1;
            notifyItemChanged(oldPosition);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_audio_recording, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        File file = audioFiles.get(position);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String date = sdf.format(new Date(file.lastModified()));
        holder.timestamp.setText(date);
        
        holder.replayButton.setImageResource(currentlyPlayingPosition == position ? 
            R.drawable.ic_pause : R.drawable.ic_play);
            
        holder.replayButton.setOnClickListener(v -> {
            if (currentlyPlayingPosition == position) {
                currentlyPlayingPosition = -1;
            } else {
                if (currentlyPlayingPosition != -1) {
                    notifyItemChanged(currentlyPlayingPosition);
                }
                currentlyPlayingPosition = position;
            }
            notifyItemChanged(position);
            listener.onReplayClick(file);
        });

        holder.deleteButton.setOnClickListener(v -> {
            listener.onDeleteClick(file);
            if (position == currentlyPlayingPosition) {
                currentlyPlayingPosition = -1;
            }
            audioFiles.remove(position);
            notifyItemRemoved(position);
        });
    }

    @Override
    public int getItemCount() {
        return audioFiles.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView timestamp;
        ImageButton replayButton;
        ImageButton deleteButton;

        ViewHolder(View view) {
            super(view);
            timestamp = view.findViewById(R.id.audioTimestamp);
            replayButton = view.findViewById(R.id.replayButton);
            deleteButton = view.findViewById(R.id.deleteButton);
        }
    }
} 