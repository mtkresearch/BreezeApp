package com.mtkresearch.breezeapp.router.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * A dummy, non-functional Activity whose only purpose is to be the LAUNCHER
 * entry point so that the "Run" button in Android Studio works for this
 * service-only application.
 */
class DummyLauncherActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show a toast message to the user
        Toast.makeText(
            this,
            "BreezeApp Router service is managed in the background.",
            Toast.LENGTH_LONG
        ).show()

        // Finish the activity immediately after showing the toast
        finish()
    }
} 