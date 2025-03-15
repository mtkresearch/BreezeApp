package com.mtkresearch.breezeapp.ui.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.ui.settings.fragments.SettingsFragment

class SettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // Set up the toolbar
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        
        // Add the settings fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settingsContainer, SettingsFragment())
                .commit()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Handle the back button in the action bar
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
} 