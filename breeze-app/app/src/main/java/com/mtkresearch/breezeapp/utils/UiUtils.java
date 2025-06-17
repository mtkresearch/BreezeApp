package com.mtkresearch.breezeapp.utils;

import android.content.Context;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mtkresearch.breezeapp.R;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

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
            Toast.makeText(context, context.getString(R.string.error_showing_image_preview), Toast.LENGTH_SHORT).show();
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
     * Scrolls a RecyclerView to its latest item
     */
    public static void scrollToLatestMessage(RecyclerView recyclerView, int itemCount, boolean smooth) {
        if (itemCount > 0) {
            if (smooth) {
                recyclerView.smoothScrollToPosition(itemCount - 1);
            } else {
                recyclerView.scrollToPosition(itemCount - 1);
            }
        }
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
}