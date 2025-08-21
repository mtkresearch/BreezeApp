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
            infoText?.text = "AI runtime settings are managed centrally by the Engine. " +
                    "Configure LLM, ASR, TTS, and VLM parameters in the unified settings interface."
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
                "Opening Engine Settings - configure all AI parameters there",
                Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: ActivityNotFoundException) {
            // Engine app not installed or activity not found
            showEngineNotAvailableDialog()
        } catch (e: Exception) {
            // Other errors
            Toast.makeText(
                requireContext(),
                "Error launching Engine settings: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Show dialog when Engine is not available
     */
    private fun showEngineNotAvailableDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("AI Engine Not Available")
            .setMessage(
                "The BreezeApp Engine is not accessible. This could be because:\n\n" +
                "• Engine app is not installed\n" +
                "• Engine service is not running\n" +
                "• Permission issues\n\n" +
                "Please ensure the Engine is properly installed and running."
            )
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("Retry") { _, _ -> launchEngineSettings() }
            .show()
    }

    companion object {
        fun newInstance(): RuntimeSettingsFragment {
            return RuntimeSettingsFragment()
        }
    }
}