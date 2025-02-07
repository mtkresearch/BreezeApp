package com.mtkresearch.gai_android.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mtkresearch.gai_android.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.HistoryViewHolder> {
    private List<ChatHistory> histories = new ArrayList<>();
    private OnHistoryClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());

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
    }

    @Override
    public int getItemCount() {
        return histories.size();
    }

    class HistoryViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView dateView;

        HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.historyTitle);
            dateView = itemView.findViewById(R.id.historyDate);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onHistoryClick(histories.get(position));
                }
            });
        }

        void bind(ChatHistory history) {
            titleView.setText(history.getTitle());
            dateView.setText(dateFormat.format(history.getDate()));
        }
    }
} 