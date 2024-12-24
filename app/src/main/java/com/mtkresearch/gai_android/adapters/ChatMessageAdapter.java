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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.MessageViewHolder> {
    private static final String TAG = "ChatMessageAdapter";
    private List<ChatMessage> messages = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());
    private int streamingPosition = -1;
    private String fullText = "";
    private int currentCharIndex = 0;
    private OnSpeakerClickListener speakerClickListener;
    private Set<Integer> completedPositions = new HashSet<>();

    public interface OnSpeakerClickListener {
        void onSpeakerClick(String messageText);
    }

    public void setSpeakerClickListener(OnSpeakerClickListener listener) {
        this.speakerClickListener = listener;
    }

    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateMessage(int position) {
        notifyItemChanged(position);
    }

    public void markMessageComplete(int position) {
        completedPositions.add(position);
        notifyItemChanged(position);
    }

    private void streamText(String text, int position) {
        Log.d(TAG, "Stream text started for position: " + position + ", text: " + text);
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
            Log.d(TAG, "Streaming completed for position: " + completedPosition);
            completedPositions.add(completedPosition);
            streamingPosition = -1;
            fullText = "";
            currentCharIndex = 0;
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
        ChatMessage message = messages.get(position);
        holder.bind(message);

        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.messageBubble.getLayoutParams();

        if (message.isUser()) {
            setupUserMessage(holder, params);
        } else {
            setupAIMessage(holder, params, position, message);
        }
        holder.messageBubble.setLayoutParams(params);

        setupImageView(holder, message, position);
        setupSpeakerButton(holder, message);
    }

    private void setupUserMessage(MessageViewHolder holder, ConstraintLayout.LayoutParams params) {
        params.startToStart = ConstraintLayout.LayoutParams.UNSET;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        params.horizontalBias = 1f;
        holder.messageBubble.setBackgroundResource(R.drawable.bg_user_message);
        holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.user_message_text));
        holder.speakerButton.setVisibility(View.GONE);
    }

    private void setupAIMessage(MessageViewHolder holder, ConstraintLayout.LayoutParams params, int position, ChatMessage message) {
        params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
        params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        params.horizontalBias = 0f;
        holder.messageBubble.setBackgroundResource(R.drawable.bg_ai_message);
        holder.messageText.setTextColor(holder.itemView.getContext().getColor(R.color.ai_message_text));

        boolean isCompleted = completedPositions.contains(position);
        boolean hasText = message.getText() != null && !message.getText().isEmpty();
        boolean hasImage = message.getImageUri() != null;

        Log.d(TAG, String.format("Message at position %d: Completed=%b, HasText=%b, HasImage=%b",
                position, isCompleted, hasText, hasImage));

        boolean shouldShowSpeaker = !message.isUser() && (isCompleted || hasImage);
        Log.d(TAG, "Speaker visibility decision for position " + position + ": " + shouldShowSpeaker);

        holder.speakerButton.setVisibility(shouldShowSpeaker ? View.VISIBLE : View.GONE);
    }

    private void setupImageView(MessageViewHolder holder, ChatMessage message, int position) {
        Uri imageUri = message.getImageUri();
        if (imageUri != null) {
            Log.d(TAG, "Loading image for position " + position + ": " + imageUri);
            holder.messageImage.setVisibility(View.VISIBLE);
            try {
                holder.messageImage.setImageURI(null);
                holder.messageImage.setImageURI(imageUri);
                Log.d(TAG, "Image loaded successfully for position " + position);
            } catch (Exception e) {
                Log.e(TAG, "Error loading image for position " + position, e);
                holder.messageImage.setVisibility(View.GONE);
            }
        } else {
            holder.messageImage.setVisibility(View.GONE);
        }
    }

    private void setupSpeakerButton(MessageViewHolder holder, ChatMessage message) {
        holder.speakerButton.setOnClickListener(v -> {
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

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            speakerButton = itemView.findViewById(R.id.speakerButton);
            messageBubble = itemView.findViewById(R.id.messageBubble);
            messageImage = itemView.findViewById(R.id.messageImage);
        }

        void bind(ChatMessage message) {
            messageText.setText(message.getText());
        }
    }
} 