package com.mtkresearch.gai_android.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.mtkresearch.gai_android.R;

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
}