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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

public class ChatHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_HISTORY = 1;
    private List<Object> items = new ArrayList<>();
    private OnHistoryClickListener listener;
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
    private static final SimpleDateFormat dayFormat = new SimpleDateFormat("d", Locale.getDefault());
    private boolean isSelectionMode = false;
    private Set<String> selectedHistories = new HashSet<>();
    private OnSelectionChangeListener selectionChangeListener;

    public interface OnHistoryClickListener {
        void onHistoryClick(ChatHistory history);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int selectedCount);
    }

    public void setOnHistoryClickListener(OnHistoryClickListener listener) {
        this.listener = listener;
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    public void setHistories(List<ChatHistory> histories) {
        // Sort histories by date in descending order (latest first)
        Collections.sort(histories, (h1, h2) -> h2.getDate().compareTo(h1.getDate()));
        
        // Group histories by month
        items.clear();
        String currentMonth = "";
        
        for (ChatHistory history : histories) {
            String month = dateFormat.format(history.getDate());
            if (!month.equals(currentMonth)) {
                items.add(month); // Add month header
                currentMonth = month;
            }
            items.add(history);
        }
        
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_HEADER : TYPE_HISTORY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_chat_history_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat_history, parent, false);
            return new HistoryViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind((String) items.get(position));
        } else if (holder instanceof HistoryViewHolder) {
            ChatHistory history = (ChatHistory) items.get(position);
            ((HistoryViewHolder) holder).bind(history);
            
            // Show/hide checkbox based on selection mode
            ((HistoryViewHolder) holder).checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            ((HistoryViewHolder) holder).checkBox.setChecked(selectedHistories.contains(history.getId()));
            
            // Update click listeners
            holder.itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(history.getId());
                    ((HistoryViewHolder) holder).checkBox.setChecked(selectedHistories.contains(history.getId()));
                } else if (listener != null) {
                    listener.onHistoryClick(history);
                }
            });

            // Add checkbox click listener
            ((HistoryViewHolder) holder).checkBox.setOnClickListener(v -> {
                toggleSelection(history.getId());
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void setSelectionMode(boolean enabled) {
        isSelectionMode = enabled;
        if (!enabled) {
            selectedHistories.clear();
            if (selectionChangeListener != null) {
                selectionChangeListener.onSelectionChanged(0);
            }
        }
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
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedHistories.size());
        }
        notifyDataSetChanged();
    }

    public void selectAll(boolean select) {
        selectedHistories.clear();
        if (select) {
            for (Object item : items) {
                if (item instanceof ChatHistory) {
                    selectedHistories.add(((ChatHistory) item).getId());
                }
            }
        }
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedHistories.size());
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedHistories() {
        return new HashSet<>(selectedHistories);
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView monthText;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            monthText = itemView.findViewById(R.id.monthText);
        }

        void bind(String month) {
            monthText.setText(month);
        }
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final CheckBox checkBox;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.historyTitle);
            checkBox = itemView.findViewById(R.id.historyCheckbox);
        }

        void bind(ChatHistory history) {
            titleView.setText(history.getTitle());
        }
    }
} 