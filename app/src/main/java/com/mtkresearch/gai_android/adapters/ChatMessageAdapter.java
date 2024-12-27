package com.mtkresearch.gai_android.adapters;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;
import com.mtkresearch.gai_android.R;
import com.mtkresearch.gai_android.models.ChatMessage;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {
    private static final String TAG = "ChatMessageAdapter";
    private List<ChatMessage> messages = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    private int streamingPosition = -1;
    private String fullText = "";
    private int currentCharIndex = 0;
    private OnSpeakerClickListener speakerClickListener;

    public interface OnSpeakerClickListener {
        void onSpeakerClick(String messageText);
    }

    public void setSpeakerClickListener(OnSpeakerClickListener listener) {
        this.speakerClickListener = listener;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        int position = messages.size() - 1;
        notifyItemInserted(position);

        if (!message.isUser()) {
            streamText(message.getText(), position);
        }
    }

    private void streamText(String text, int position) {
        fullText = text;
        currentCharIndex = 0;
        streamingPosition = position;
        notifyItemChanged(position);
        streamNextCharacter();
    }

    private void streamNextCharacter() {
        if (currentCharIndex <= fullText.length()) {
            String partialText = fullText.substring(0, currentCharIndex);
            messages.get(streamingPosition).updateText(partialText);
            notifyItemChanged(streamingPosition);

            currentCharIndex++;
            handler.postDelayed(this::streamNextCharacter, 50);
        } else {
            int completedPosition = streamingPosition;
            streamingPosition = -1;
            notifyItemChanged(completedPosition);
        }
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
        if (holder.speakerButton == null || holder.userSpeakerButton == null) {
            Log.e(TAG, "Speaker buttons not properly initialized");
            return;
        }

        ChatMessage message = messages.get(position);
        holder.bind(message);

        // Get the ConstraintLayout params for the message bubble
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.messageBubble.getLayoutParams();

        if (message.isUser()) {
            params.startToStart = ConstraintLayout.LayoutParams.UNSET;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            params.horizontalBias = 1f;
            holder.messageBubble.setBackgroundResource(R.drawable.bg_user_message);
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.user_message_text));
            holder.speakerButton.setVisibility(View.GONE);

            // Show user speaker button only when message is complete
            boolean hasText = message.getText() != null && !message.getText().isEmpty();
            holder.userSpeakerButton.setVisibility(hasText ? View.VISIBLE : View.GONE);

        } else {
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            params.horizontalBias = 0f;
            holder.messageBubble.setBackgroundResource(R.drawable.bg_ai_message);
            holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.ai_message_text));
            holder.userSpeakerButton.setVisibility(View.GONE);

            boolean isStreaming = (position == streamingPosition);
            Log.d(TAG, "Position: " + position + ", Streaming: " + isStreaming);
            holder.speakerButton.setVisibility(isStreaming ? View.GONE : View.VISIBLE);
        }
        holder.messageBubble.setLayoutParams(params);

        // Handle image visibility and loading
        Uri imageUri = message.getImageUri();
        if (imageUri != null) {
            Log.d(TAG, "Image URI present: " + imageUri.toString());
            holder.messageImage.setVisibility(View.VISIBLE);
            try {
                holder.messageImage.setImageURI(null);
                holder.messageImage.setImageURI(imageUri);
                Log.d(TAG, "Image loaded successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error loading image", e);
                holder.messageImage.setVisibility(View.GONE);
            }
        } else {
            Log.d(TAG, "No image URI for this message");
            holder.messageImage.setVisibility(View.GONE);
        }

        // Setup speaker button click listeners
        holder.speakerButton.setOnClickListener(v -> {
            if (speakerClickListener != null) {
                speakerClickListener.onSpeakerClick(message.getText());
            }
        });

        holder.userSpeakerButton.setOnClickListener(v -> {
            if (speakerClickListener != null) {
                speakerClickListener.onSpeakerClick(message.getText());
            }
        });
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