package com.mtkresearch.gai_android.utils;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.mtkresearch.gai_android.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying chat messages in a RecyclerView.
 * Handles only UI representation of messages.
 */
public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {
    private static final String TAG = "ChatMessageAdapter";
    private final List<ChatMessage> messages = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private OnSpeakerClickListener speakerClickListener;
    private OnMessageLongClickListener messageLongClickListener;

    public interface OnSpeakerClickListener {
        void onSpeakerClick(String messageText);
    }

    public interface OnMessageLongClickListener {
        boolean onMessageLongClick(ChatMessage message, int position);
    }

    public void setSpeakerClickListener(OnSpeakerClickListener listener) {
        this.speakerClickListener = listener;
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.messageLongClickListener = listener;
    }

    public void setMessages(List<ChatMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastMessage(String newText) {
        if (!messages.isEmpty()) {
            int lastIndex = messages.size() - 1;
            ChatMessage lastMessage = messages.get(lastIndex);
            lastMessage.updateText(newText);
            notifyItemChanged(lastIndex);
        }
    }

    public void removeLastMessage() {
        if (!messages.isEmpty()) {
            int lastIndex = messages.size() - 1;
            messages.remove(lastIndex);
            notifyItemRemoved(lastIndex);
        }
    }

    public void clearMessages() {
        messages.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        // Set text selection mode
        holder.messageText.setTextIsSelectable(true);
        
        // Set long click listener
        holder.itemView.setOnLongClickListener(v -> {
            if (messageLongClickListener != null) {
                return messageLongClickListener.onMessageLongClick(message, position);
            }
            return false;
        });

        // Set the message text
        holder.messageText.setText(message.getText());

        // Get the ConstraintLayout params for the message bubble
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.messageBubble.getLayoutParams();

        if (message.isUser()) {
            setupUserMessage(holder, params, message);
        } else {
            setupAssistantMessage(holder, params, message);
        }

        holder.messageBubble.setLayoutParams(params);
        setupImageAndSpeakerButtons(holder, message);
    }

    private void setupUserMessage(MessageViewHolder holder, ConstraintLayout.LayoutParams params, ChatMessage message) {
        params.startToStart = ConstraintLayout.LayoutParams.UNSET;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        params.horizontalBias = 1f;
        holder.messageBubble.setBackgroundResource(R.drawable.bg_user_message);
        holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.user_message_text));
        
        holder.speakerButton.setVisibility(View.GONE);
        holder.userSpeakerButton.setVisibility(message.hasText() ? View.VISIBLE : View.GONE);

        params.startToStart = ConstraintLayout.LayoutParams.UNSET;
        params.startToEnd = ConstraintLayout.LayoutParams.UNSET;
        params.endToStart = holder.userSpeakerButton.getId();
        params.endToEnd = ConstraintLayout.LayoutParams.UNSET;
    }

    private void setupAssistantMessage(MessageViewHolder holder, ConstraintLayout.LayoutParams params, ChatMessage message) {
        params.startToStart = ConstraintLayout.LayoutParams.UNSET;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        params.horizontalBias = 0f;
        holder.messageBubble.setBackgroundResource(R.drawable.bg_ai_message);
        holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.ai_message_text));
        
        holder.userSpeakerButton.setVisibility(View.GONE);
        holder.speakerButton.setVisibility(message.hasText() ? View.VISIBLE : View.GONE);

        params.startToStart = ConstraintLayout.LayoutParams.UNSET;
        params.startToEnd = holder.speakerButton.getId();
        params.endToStart = ConstraintLayout.LayoutParams.UNSET;
    }

    private void setupImageAndSpeakerButtons(MessageViewHolder holder, ChatMessage message) {
        Uri imageUri = message.getImageUri();
        if (imageUri != null) {
            Log.d(TAG, "Image URI present: " + imageUri);
            holder.messageImage.setVisibility(View.VISIBLE);
            try {
                holder.messageImage.setImageURI(null);
                holder.messageImage.setImageURI(imageUri);
            } catch (Exception e) {
                Log.e(TAG, "Error loading image", e);
                holder.messageImage.setVisibility(View.GONE);
            }
        } else {
            holder.messageImage.setVisibility(View.GONE);
        }

        setupSpeakerClickListeners(holder, message);
    }

    private void setupSpeakerClickListeners(MessageViewHolder holder, ChatMessage message) {
        View.OnClickListener speakerListener = v -> {
            if (speakerClickListener != null && message.hasText()) {
                speakerClickListener.onSpeakerClick(message.getText());
            }
        };
        
        holder.speakerButton.setOnClickListener(speakerListener);
        holder.userSpeakerButton.setOnClickListener(speakerListener);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView messageText;
        private final ImageButton speakerButton;
        private final LinearLayout messageBubble;
        private final ImageView messageImage;
        private final ImageButton userSpeakerButton;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            speakerButton = itemView.findViewById(R.id.speakerButton);
            messageBubble = itemView.findViewById(R.id.messageBubble);
            messageImage = itemView.findViewById(R.id.messageImage);
            userSpeakerButton = itemView.findViewById(R.id.userSpeakerButton);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getText());
        }
    }
}