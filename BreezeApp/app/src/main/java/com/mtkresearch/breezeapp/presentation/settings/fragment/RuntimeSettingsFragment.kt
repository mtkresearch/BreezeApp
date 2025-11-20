package com.mtkresearch.breezeapp.presentation.settings.fragment

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.presentation.common.base.BaseFragment

/**
 * Runtime Settings Fragment - Engine-Centric Architecture
 * 
 * This fragment follows the correct engine-centric approach where all AI settings
 * are managed centrally by the BreezeApp Engine. This avoids code duplication and
 * maintains a single source of truth for AI parameters.
 * 
 * Architecture Benefits:
 * - No code duplication between Client and Engine
 * - Single source of truth for AI settings (EngineSettings.kt)
 * - Centralized parameter validation and management
 * - Unified settings UI across all apps using the Engine
 * 
 * Flow: Client → Intent → EngineSettingsActivity → EngineSettings → RunnerManager
 */
class RuntimeSettingsFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_runtime_settings_simplified, container, false)
    }

    override fun setupUI() {
        view?.let { view ->
            // Set up the "Configure AI Engine" button
            val configureButton = view.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_configure_engine)
            configureButton?.setOnClickListener {
                launchEngineSettings()
            }
            
            // Set up info text
            val infoText = view.findViewById<android.widget.TextView>(R.id.text_info)
            infoText?.text = getString(R.string.ai_runtime_settings_info_extended)
        }
    }

    /**
     * Launch the Engine Settings Activity
     * 
     * This is the correct approach - delegate all AI parameter management to the
     * centralized Engine Settings UI which has:
     * - Dynamic parameter controls based on ParameterSchema
     * - Real-time runner selection and configuration
     * - Automatic parameter validation
     * - Persistent settings storage
     */
    private fun launchEngineSettings() {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.mtkresearch.breezeapp.engine",
                    "com.mtkresearch.breezeapp.engine.ui.EngineSettingsActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(intent)
            
            // Show helpful message
            Toast.makeText(
                requireContext(),
                getString(R.string.opening_engine_settings),
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: ActivityNotFoundException) {
            // Engine app not installed or activity not found
            showEngineNotAvailableDialog()
        } catch (e: Exception) {
            // Other errors
            Toast.makeText(
                requireContext(),
                getString(R.string.error_launching_engine_settings, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Show dialog when Engine is not available
     */
    private fun showEngineNotAvailableDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.ai_engine_not_available_title))
            .setMessage(getString(R.string.ai_engine_not_available_message))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ -> dialog.dismiss() }
            .setNegativeButton(getString(R.string.retry)) { _, _ -> launchEngineSettings() }
            .show()
    }

    companion object {
        fun newInstance(): RuntimeSettingsFragment {
            return RuntimeSettingsFragment()
        }
    }
}