package com.mtkresearch.breeze_app.utils;

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
import com.mtkresearch.breeze_app.R;

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
    private OnFeedbackClickListener feedbackClickListener;
    private boolean ttsEnabled = AppConstants.TTS_ENABLED;  // Default to AppConstants value

    public interface OnSpeakerClickListener {
        void onSpeakerClick(String messageText, int position);
    }

    public interface OnMessageLongClickListener {
        boolean onMessageLongClick(ChatMessage message, int position);
    }

    public interface OnFeedbackClickListener {
        void onFeedbackClick(boolean isUpvote);
    }

    public void setSpeakerClickListener(OnSpeakerClickListener listener) {
        this.speakerClickListener = listener;
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener listener) {
        this.messageLongClickListener = listener;
    }

    public void setFeedbackClickListener(OnFeedbackClickListener listener) {
        this.feedbackClickListener = listener;
    }

    public void setTTSEnabled(boolean enabled) {
        this.ttsEnabled = enabled;
        notifyDataSetChanged();  // Refresh all items to update speaker icon visibility
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

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessageTextColor(int position, int color) {
        if (position >= 0 && position < messages.size()) {
            messages.get(position).setCustomTextColor(color);
            notifyItemChanged(position);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).isUser() ? 0 : 1; // 0 for user, 1 for AI
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == 0) {
            // User message layout
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message_user, parent, false);
            return new UserMessageViewHolder(view);
        } else {
            // AI message layout
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chat_message_ai, parent, false);
            return new AIMessageViewHolder(view);
        }
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

        // Set the text color based on custom color if set
        if (message.getCustomTextColor() != 0) {
            holder.messageText.setTextColor(message.getCustomTextColor());
        } else if (message.isUser()) {
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.user_message_text));
        } else {
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.ai_message_text));
        }

        // Get the ConstraintLayout params for the message bubble
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.messageBubble.getLayoutParams();

        if (message.isUser()) {
            setupUserMessage(holder, params, message);
        } else {
            setupAssistantMessage(holder, params, message);
        }

        holder.messageBubble.setLayoutParams(params);
        setupImageAndSpeakerButtons(holder, message, position);
    }

    private void setupUserMessage(MessageViewHolder holder, ConstraintLayout.LayoutParams params, ChatMessage message) {
        if (holder instanceof UserMessageViewHolder) {
            UserMessageViewHolder userHolder = (UserMessageViewHolder) holder;
            holder.messageBubble.setBackgroundResource(R.drawable.bg_user_message);
            
            // Show button row if message has text and TTS is enabled
            if (message.hasText() && ttsEnabled) {
                userHolder.buttonRow.setVisibility(View.VISIBLE);
                userHolder.speakerButton.setVisibility(View.VISIBLE);
            } else {
                userHolder.buttonRow.setVisibility(View.GONE);
            }

            // Keep upper text row hidden for now (future extension)
            userHolder.upperTextRow.setVisibility(View.GONE);
        }
    }

    private void setupAssistantMessage(MessageViewHolder holder, ConstraintLayout.LayoutParams params, ChatMessage message) {
        if (holder instanceof AIMessageViewHolder) {
            AIMessageViewHolder aiHolder = (AIMessageViewHolder) holder;
            holder.messageBubble.setBackgroundResource(R.drawable.bg_ai_message);
            
            // Show/hide button row based on message completion and TTS state
            if (message.isCompleted()) {
                aiHolder.buttonRow.setVisibility(View.VISIBLE);
                
                // Show/hide speaker button based on TTS state and if not an error message
                boolean showSpeaker = ttsEnabled && message.hasText() && !message.isError();
                aiHolder.speakerButton.setVisibility(showSpeaker ? View.VISIBLE : View.GONE);
                
                // Setup up/down like buttons
                setupFeedbackButtons(aiHolder);
            } else {
                aiHolder.buttonRow.setVisibility(View.GONE);
            }
        }
    }

    private void setupFeedbackButtons(AIMessageViewHolder holder) {
        // Show feedback buttons (always visible when message is complete)
        holder.upLikeButton.setVisibility(View.VISIBLE);
        holder.downLikeButton.setVisibility(View.VISIBLE);
        
        // Set click listeners for feedback buttons
        holder.upLikeButton.setOnClickListener(v -> {
            if (feedbackClickListener != null) {
                feedbackClickListener.onFeedbackClick(true); // true for upvote
            }
        });
        
        holder.downLikeButton.setOnClickListener(v -> {
            if (feedbackClickListener != null) {
                feedbackClickListener.onFeedbackClick(false); // false for downvote
            }
        });
    }

    private void setupImageAndSpeakerButtons(MessageViewHolder holder, ChatMessage message, int position) {
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

        setupSpeakerClickListeners(holder, message, position);
    }

    private void setupSpeakerClickListeners(MessageViewHolder holder, ChatMessage message, int position) {
        View.OnClickListener speakerListener = v -> {
            if (speakerClickListener != null && message.hasText()) {
                speakerClickListener.onSpeakerClick(message.getText(), position);
            }
        };

        if (holder instanceof UserMessageViewHolder) {
            UserMessageViewHolder userHolder = (UserMessageViewHolder) holder;
            userHolder.speakerButton.setOnClickListener(speakerListener);
        } else if (holder instanceof AIMessageViewHolder) {
            AIMessageViewHolder aiHolder = (AIMessageViewHolder) holder;
            aiHolder.speakerButton.setOnClickListener(speakerListener);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static abstract class MessageViewHolder extends RecyclerView.ViewHolder {
        protected final TextView messageText;
        protected final LinearLayout messageBubble;
        protected final ImageView messageImage;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            messageBubble = itemView.findViewById(R.id.messageBubble);
            messageImage = itemView.findViewById(R.id.messageImage);
        }
    }

    static class UserMessageViewHolder extends MessageViewHolder {
        private final LinearLayout upperTextRow;
        private final LinearLayout buttonRow;
        private final TextView upperText;
        private final ImageButton speakerButton;

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            upperTextRow = itemView.findViewById(R.id.upperTextRow);
            buttonRow = itemView.findViewById(R.id.buttonRow);
            upperText = itemView.findViewById(R.id.upperText);
            speakerButton = itemView.findViewById(R.id.speakerButton);
        }
    }

    static class AIMessageViewHolder extends MessageViewHolder {
        private final LinearLayout buttonRow;
        private final ImageButton speakerButton;
        private final ImageButton upLikeButton;
        private final ImageButton downLikeButton;

        AIMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            buttonRow = itemView.findViewById(R.id.buttonRow);
            speakerButton = itemView.findViewById(R.id.speakerButton);
            upLikeButton = itemView.findViewById(R.id.upLikeButton);
            downLikeButton = itemView.findViewById(R.id.downLikeButton);
        }
    }
}