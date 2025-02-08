package com.mtkresearch.gai_android.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mtkresearch.gai_android.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.HistoryViewHolder> {
    private List<ChatHistory> histories = new ArrayList<>();
    private OnHistoryClickListener listener;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
    private boolean isSelectionMode = false;
    private Set<String> selectedHistories = new HashSet<>();

    public interface OnHistoryClickListener {
        void onHistoryClick(ChatHistory history);
    }

    public void setOnHistoryClickListener(OnHistoryClickListener listener) {
        this.listener = listener;
    }

    public void setHistories(List<ChatHistory> histories) {
        this.histories = histories;
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean enabled) {
        isSelectionMode = enabled;
        selectedHistories.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void toggleSelection(String historyId) {
        if (selectedHistories.contains(historyId)) {
            selectedHistories.remove(historyId);
        } else {
            selectedHistories.add(historyId);
        }
        notifyDataSetChanged();
    }

    public void selectAll(boolean select) {
        selectedHistories.clear();
        if (select) {
            for (ChatHistory history : histories) {
                selectedHistories.add(history.getId());
            }
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedHistories() {
        return new HashSet<>(selectedHistories);
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        ChatHistory history = histories.get(position);
        holder.bind(history);
        
        // Show/hide checkbox based on selection mode
        holder.checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setChecked(selectedHistories.contains(history.getId()));
        
        // Update click listeners
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(history.getId());
            } else if (listener != null) {
                listener.onHistoryClick(history);
            }
        });
    }

    @Override
    public int getItemCount() {
        return histories.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView dateView;
        private final CheckBox checkBox;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.historyTitle);
            dateView = itemView.findViewById(R.id.historyDate);
            checkBox = itemView.findViewById(R.id.historyCheckbox);
        }

        void bind(ChatHistory history) {
            titleView.setText(history.getTitle());
            dateView.setText(dateFormat.format(history.getDate()));
        }
    }
} 