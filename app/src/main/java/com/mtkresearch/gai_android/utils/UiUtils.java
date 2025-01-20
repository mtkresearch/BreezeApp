package com.mtkresearch.gai_android.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mtkresearch.gai_android.R;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UiUtils {
    private static final String TAG = "UiUtils";

    /**
     * Shows an image preview in the expanded input view.
     * @param context The context to use for toasts and resources
     * @param imageUri The URI of the image to preview
     * @param expandedInputView The expanded input view containing the preview elements
     * @param callback Callback for when the image is removed
     */
    public static void showImagePreview(Context context, Uri imageUri, View expandedInputView,
                                      ImagePreviewCallback callback) {
        if (imageUri == null) {
            Log.e(TAG, "Cannot show preview: imageUri is null");
            return;
        }

        // Find all required views
        View imagePreviewContainer = expandedInputView.findViewById(R.id.imagePreviewContainer);
        ImageView imagePreview = expandedInputView.findViewById(R.id.imagePreview);
        ImageButton removeButton = expandedInputView.findViewById(R.id.removeImageButton);

        // Validate views
        if (imagePreviewContainer == null || imagePreview == null || removeButton == null) {
            Log.e(TAG, "Failed to find image preview views");
            Toast.makeText(context, "Error showing image preview", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show preview container and set image
        imagePreviewContainer.setVisibility(View.VISIBLE);
        imagePreview.setImageURI(null); // Clear previous image
        imagePreview.setImageURI(imageUri);
        Log.d(TAG, "Setting image URI: " + imageUri);

        // Setup remove button
        removeButton.setOnClickListener(v -> {
            imagePreview.setImageURI(null);
            imagePreviewContainer.setVisibility(View.GONE);
            if (callback != null) {
                callback.onImageRemoved();
            }
        });
    }

    /**
     * Callback interface for image preview events
     */
    public interface ImagePreviewCallback {
        void onImageRemoved();
    }

    /**
     * Shows attachment options in a popup menu
     * @param context The context to use for the popup menu
     * @param anchorView The view to anchor the popup menu to
     * @param listener Listener for attachment option selection
     */
    public static void showAttachmentOptions(Context context, View anchorView,
                                           AttachmentOptionsListener listener) {
        PopupMenu popup = new PopupMenu(new ContextThemeWrapper(context, R.style.PopupMenuStyle),
                                      anchorView);

        popup.getMenu().add(0, 1, 0, "Attach Photos").setIcon(R.drawable.ic_gallery);
        popup.getMenu().add(0, 2, 0, "Take Photo").setIcon(R.drawable.ic_camera);
        popup.getMenu().add(0, 3, 0, "Attach Files").setIcon(R.drawable.ic_folder);

        // Force showing icons
        try {
            Method method = popup.getMenu().getClass()
                    .getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
            method.setAccessible(true);
            method.invoke(popup.getMenu(), true);
        } catch (Exception e) {
            e.printStackTrace();
        }

        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    listener.onGallerySelected();
                    break;
                case 2:
                    listener.onCameraSelected();
                    break;
                case 3:
                    listener.onFileSelected();
                    break;
            }
            return true;
        });

        popup.show();
    }

    /**
     * Listener interface for attachment option selection
     */
    public interface AttachmentOptionsListener {
        void onGallerySelected();
        void onCameraSelected();
        void onFileSelected();
    }

    /**
     * Scrolls a RecyclerView to its latest item
     */
    public static void scrollToLatestMessage(RecyclerView recyclerView, int itemCount, boolean smooth) {
        if (itemCount > 0) {
            int targetPosition = itemCount - 1;
            if (smooth) {
                recyclerView.smoothScrollToPosition(targetPosition);
            } else {
                recyclerView.scrollToPosition(targetPosition);
            }
        }
    }

    /**
     * Sets up a RecyclerView with chat-style configuration
     */
    public static void setupChatRecyclerView(RecyclerView recyclerView, RecyclerView.Adapter adapter) {
        LinearLayoutManager layoutManager = new LinearLayoutManager(recyclerView.getContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        // Add padding to allow scrolling content up
        recyclerView.setPadding(
            recyclerView.getPaddingLeft(),
            recyclerView.getHeight() / 2, // Half screen padding at top
            recyclerView.getPaddingRight(),
            recyclerView.getPaddingBottom()
        );
        recyclerView.setClipToPadding(false);
    }

    /**
     * Shows an audio list dialog with playback controls
     */
    public static void showAudioListDialog(Context context, List<File> audioFiles, 
                                         AudioListAdapter.OnAudioActionListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_audio_list, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.audioListRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        AudioListAdapter adapter = new AudioListAdapter(audioFiles, listener);
        recyclerView.setAdapter(adapter);

        AlertDialog dialog = builder.setView(dialogView)
                .setTitle("Recorded Audio Files")
                .setNegativeButton("Close", null)
                .create();

        dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
        dialog.show();
    }

    /**
     * Updates send button icon based on content state
     */
    public static void updateSendButtonIcon(ImageButton sendButton, ImageButton sendButtonExpanded, 
                                          boolean hasContent) {
        int icon = hasContent ? R.drawable.ic_send : R.drawable.ic_audio_wave;
        sendButton.setImageResource(icon);
        sendButtonExpanded.setImageResource(icon);
    }

    /**
     * Sets up input field focus listeners for expandable input
     */
    public static void setupInputFieldFocus(View collapsedInput, View expandedInput,
                                            EditText messageInput, EditText messageInputExpanded,
                                            Runnable onExpand, Runnable onCollapse) {
        messageInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                onExpand.run();
            }
        });

        messageInputExpanded.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String currentText = messageInputExpanded.getText().toString();
                if (currentText.isEmpty()) {
                    onCollapse.run();
                }
            }
        });
    }

    /**
     * Sets up touch listeners for handling input section collapse
     */
    public static void setupInputTouchListeners(View root, View recyclerView, View expandedInput, 
                                              EditText messageInputExpanded, Runnable onCollapse) {
        View.OnTouchListener outsideTouchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                if (expandedInput.getVisibility() == View.VISIBLE) {
                    String currentText = messageInputExpanded.getText().toString();
                    if (currentText.isEmpty()) {
                        messageInputExpanded.clearFocus();
                        onCollapse.run();
                    }
                }
            }
            return false;
        };

        root.setOnTouchListener(outsideTouchListener);
        recyclerView.setOnTouchListener(outsideTouchListener);
    }

    public static File createImageFile(Context context) throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }
        return File.createTempFile(imageFileName, ".jpg", storageDir);
    }
}