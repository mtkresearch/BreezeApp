package com.mtkresearch.breezeapp.ui.settings.fragments

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.core.utils.AppConstants

class SettingsFragment : PreferenceFragmentCompat(),
    SharedPreferences.OnSharedPreferenceChangeListener {
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_preferences, rootKey)
        
        // Set up click listeners for special preferences
        findPreference<Preference>("model_info")?.setOnPreferenceClickListener {
            // Show model info dialog
            true
        }
        
        findPreference<Preference>("clear_history")?.setOnPreferenceClickListener {
            // Show confirmation dialog for clearing history
            true
        }
        
        findPreference<Preference>("about")?.setOnPreferenceClickListener {
            // Show about dialog
            true
        }
        
        // Set initial system prompt summary
        val systemPrompt = findPreference<Preference>("system_prompt")
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val currentPrompt = sharedPrefs.getString("system_prompt", AppConstants.DEFAULT_SYSTEM_PROMPT)
        systemPrompt?.summary = truncateText(currentPrompt ?: "")
    }
    
    override fun onResume() {
        super.onResume()
        preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }
    
    override fun onPause() {
        super.onPause()
        preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }
    
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        key?.let {
            when (it) {
                "system_prompt" -> {
                    val newValue = sharedPreferences?.getString(it, AppConstants.DEFAULT_SYSTEM_PROMPT)
                    findPreference<Preference>(it)?.summary = truncateText(newValue ?: "")
                }
                // Add other preference change handlers as needed
            }
        }
    }
    
    private fun truncateText(text: String, maxLength: Int = 50): String {
        return if (text.length > maxLength) {
            "${text.take(maxLength)}..."
        } else {
            text
        }
    }
} 