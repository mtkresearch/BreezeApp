package com.mtkresearch.breeze_app;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.mtkresearch.breeze_app.utils.SettingsFragment;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        // Set up toolbar with back button
        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        
        // Navigate back when back button is pressed
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }
    
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}