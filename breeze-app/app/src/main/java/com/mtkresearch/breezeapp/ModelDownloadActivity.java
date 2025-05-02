package com.mtkresearch.breezeapp;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.content.Intent;
import android.widget.Button;
import android.widget.TextView;
import android.view.WindowManager;

import com.mtkresearch.breezeapp.utils.ModelDownloadDialog;

/**
 * Activity for displaying the model download dialog.
 * This activity is started when a download dialog needs to be shown from a non-Activity context.
 */
public class ModelDownloadActivity extends Activity {
    private static final String TAG = "ModelDownloadActivity";
    private ModelDownloadDialog downloadDialog;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        // Set a simple layout with a title and back button
        setContentView(R.layout.activity_model_download);
        
        // Set up the back button
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> {
            // Cancel any ongoing download and finish the activity
            if (downloadDialog != null && downloadDialog.isShowing()) {
                downloadDialog.dismiss();
            }
            finish();
        });
        
        // Display title based on download mode
        TextView titleText = findViewById(R.id.titleText);
        
        // Get the download mode from the intent
        Intent intent = getIntent();
        String downloadModeStr = intent.getStringExtra("download_mode");
        Log.d(TAG, "ModelDownloadActivity started with mode: " + downloadModeStr);
        
        // Determine the download mode
        ModelDownloadDialog.DownloadMode downloadMode = ModelDownloadDialog.DownloadMode.LLM;
        if ("MTK_NPU".equals(downloadModeStr)) {
            downloadMode = ModelDownloadDialog.DownloadMode.MTK_NPU;
            titleText.setText("MTK NPU Model Download");
        } else if ("TTS".equals(downloadModeStr)) {
            downloadMode = ModelDownloadDialog.DownloadMode.TTS;
            titleText.setText("TTS Model Download");
        } else {
            titleText.setText("LLM Model Download");
        }
        
        // Show the download dialog
        final ModelDownloadDialog.DownloadMode finalMode = downloadMode;
        Log.d(TAG, "Showing model download dialog for mode: " + finalMode);
        
        downloadDialog = new ModelDownloadDialog(this, null, finalMode);
        downloadDialog.setOnDismissListener(dialog -> {
            Log.d(TAG, "Download dialog dismissed, finishing activity");
            // Finish this activity when the dialog is dismissed
            Intent resultIntent = new Intent();
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        downloadDialog.show();
    }
    
    @Override
    public void onBackPressed() {
        // Handle back button press
        if (downloadDialog != null && downloadDialog.isShowing()) {
            // Ask user to confirm cancellation
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("Cancel Download?");
            builder.setMessage("Do you want to cancel the download? The model may not work properly without all files.");
            builder.setPositiveButton("Yes", (dialog, which) -> {
                // Dismiss the download dialog and finish the activity
                downloadDialog.dismiss();
                finish();
            });
            builder.setNegativeButton("No", (dialog, which) -> {
                // Just dismiss the confirmation dialog
                dialog.dismiss();
            });
            builder.show();
        } else {
            super.onBackPressed();
        }
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);        
    }
} 