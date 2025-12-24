package com.mtkresearch.breezeapp.presentation.common.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.edgeai.DownloadConstants
// NOTE: ModelDownloadService cannot be accessed directly by Client App.
// Cancellation must be handled by sending a broadcast that the service listens to,
// OR by using the EdgeAI SDK if it exposed a cancellation method (it doesn't yet).
// For now, we will omit the "Cancel" functionality or implement it via generic Intent if needed.
// IMPORTANT: The original Engine UI called 'ModelDownloadService.cancelDownload'. We can't do that.
// We will send a generic broadcast that the Engine (DownloadEventManager) could ideally listen to?
// Or we just implement 'Display-Only' for now to solve the implementation constraint.
// Let's implement Display-Only first to resolve the crash/UX.

data class DownloadStateUI(
    val modelId: String,
    val fileName: String?,
    val progress: Int,
    val status: Status,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val error: String? = null
) {
    enum class Status { QUEUED, DOWNLOADING, COMPLETED, FAILED }
}

class AppDownloadProgressBottomSheet : BottomSheetDialogFragment() {
    
    companion object {
        const val TAG = "AppDownloadProgressBottomSheet"
        
        fun show(fragmentManager: androidx.fragment.app.FragmentManager): AppDownloadProgressBottomSheet {
            val bottomSheet = AppDownloadProgressBottomSheet()
            bottomSheet.show(fragmentManager, TAG)
            return bottomSheet
        }
    }
    
    // UI components
    private lateinit var textTitle: TextView
    private lateinit var btnClose: Button
    private lateinit var containerDownloadItems: LinearLayout
    private lateinit var btnCancelAll: Button
    private lateinit var btnMinimize: Button
    
    private val downloadItemViews = mutableMapOf<String, View>()
    private val downloadStates = mutableMapOf<String, DownloadStateUI>()
    
    private val downloadEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handleDownloadEvent(it) }
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        
        // Make non-dismissible - users cannot dismiss during downloads
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        
        return dialog
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Use the app's R class. Assuming the layout exists in app or can be reused.
        // If the layout is in Engine, App cannot see it.
        // We might need to copy the layout XML too.
        // Let's try to find an existing generic bottom sheet layout or assume we need to create one.
        // For robustness, I will assume I need to create the layout OR use a simple dynamic view.
        // To save time/complexity, I will attempt to use 'R.layout.bottom_sheet_download_progress' 
        // IF I can copy it. 
        // Actually, if I can't access engine resources, I definitely need to create the layout in App.
        // I'll assume for now I can inflate a simple view dynamically if needed, but
        // let's try to rely on the fact that I can write files.
        // I will write the layout file first? No, I'll use standard views dynamically if I have to.
        // But wait, the user wants "Breeze App" to work. It has resources.
        // I'll assume I can use `R.layout.fragment_app_settings` mechanism or similar.
        // Let's defer layout creation and just use a simple linear layout programmatically if R.layout not found?
        // No, that's ugly.
        // Let's assume I will also copy the layout file in the next step.
        return inflater.inflate(R.layout.bottom_sheet_download_progress, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        textTitle = view.findViewById(R.id.textTitle)
        btnClose = view.findViewById(R.id.btnClose)
        containerDownloadItems = view.findViewById(R.id.containerDownloadItems)
        btnCancelAll = view.findViewById(R.id.btnCancelAll)
        btnMinimize = view.findViewById(R.id.btnMinimize)
        
        // Hide close/minimize buttons during downloads - only show when complete
        btnClose.visibility = View.GONE
        btnMinimize.visibility = View.GONE
        btnClose.setOnClickListener { dismiss() }
        btnMinimize.setOnClickListener { dismiss() }
        // Cancel logic disabled for client app isolation
        btnCancelAll.visibility = View.GONE 
        
        textTitle.text = "Downloading Models - Please Wait"
        
        registerDownloadEventReceiver()
    }
    
    private fun registerDownloadEventReceiver() {
        val filter = IntentFilter().apply {
            addAction(DownloadConstants.ACTION_DOWNLOAD_STARTED)
            addAction(DownloadConstants.ACTION_DOWNLOAD_PROGRESS)
            addAction(DownloadConstants.ACTION_DOWNLOAD_COMPLETED)
            addAction(DownloadConstants.ACTION_DOWNLOAD_FAILED)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(downloadEventReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            requireContext().registerReceiver(downloadEventReceiver, filter)
        }
    }
    
    private fun handleDownloadEvent(intent: Intent) {
        val modelId = intent.getStringExtra(DownloadConstants.EXTRA_MODEL_ID) ?: return
        
        when (intent.action) {
            DownloadConstants.ACTION_DOWNLOAD_STARTED -> {
                val fileName = intent.getStringExtra(DownloadConstants.EXTRA_FILE_NAME)
                // Simple approach: just track the current model being downloaded
                downloadStates[modelId] = DownloadStateUI(modelId, fileName, 0, DownloadStateUI.Status.DOWNLOADING)
            }
            DownloadConstants.ACTION_DOWNLOAD_PROGRESS -> {
                val progress = intent.getIntExtra(DownloadConstants.EXTRA_PROGRESS_PERCENTAGE, 0)
                val downloaded = intent.getLongExtra(DownloadConstants.EXTRA_DOWNLOADED_BYTES, 0)
                val total = intent.getLongExtra(DownloadConstants.EXTRA_TOTAL_BYTES, 0)
                val current = downloadStates[modelId] ?: DownloadStateUI(modelId, null, 0, DownloadStateUI.Status.DOWNLOADING)
                downloadStates[modelId] = current.copy(
                    progress = progress, 
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    status = DownloadStateUI.Status.DOWNLOADING
                )
            }
            DownloadConstants.ACTION_DOWNLOAD_COMPLETED -> {
                // Mark as complete
                val current = downloadStates[modelId]
                if (current != null) {
                    downloadStates[modelId] = current.copy(status = DownloadStateUI.Status.COMPLETED, progress = 100)
                }
            }
            DownloadConstants.ACTION_DOWNLOAD_FAILED -> {
                val error = intent.getStringExtra(DownloadConstants.EXTRA_ERROR_MESSAGE)
                val current = downloadStates[modelId]
                if (current != null) {
                    downloadStates[modelId] = current.copy(status = DownloadStateUI.Status.FAILED, error = error)
                }
            }
        }
        updateUI()
    }
    
    private fun updateUI() {
        // Update title with total files
        val totalFiles = downloadStates.size
        val completedFiles = downloadStates.values.count { it.status == DownloadStateUI.Status.COMPLETED }
        
        // Check if all downloads are complete or failed
        val allComplete = downloadStates.values.all { 
            it.status == DownloadStateUI.Status.COMPLETED || 
            it.status == DownloadStateUI.Status.FAILED 
        }
        
        // Update title based on state
        textTitle.text = if (allComplete) {
            "Downloads Complete"
        } else {
            "Downloading Models ($completedFiles/$totalFiles) - Please Wait"
        }
        
        // Make dismissible only when all complete
        dialog?.setCancelable(allComplete)
        dialog?.setCanceledOnTouchOutside(allComplete)
        btnClose.visibility = if (allComplete) View.VISIBLE else View.GONE
        btnMinimize.visibility = if (allComplete) View.VISIBLE else View.GONE
        
        // Simple UI update logic
        containerDownloadItems.removeAllViews()
        downloadStates.values.forEach { state ->
            val itemView = LayoutInflater.from(context).inflate(R.layout.item_download_progress, containerDownloadItems, false)
            
            itemView.findViewById<TextView>(R.id.textModelName).text = state.fileName ?: state.modelId
            itemView.findViewById<ProgressBar>(R.id.progressBar).progress = state.progress
            itemView.findViewById<TextView>(R.id.textProgress).text = "${state.progress}%"
            
            val statusText = when(state.status) {
                DownloadStateUI.Status.QUEUED -> "Queued"
                DownloadStateUI.Status.DOWNLOADING -> "Downloading..."
                DownloadStateUI.Status.COMPLETED -> "Completed"
                DownloadStateUI.Status.FAILED -> "Failed: ${state.error}"
            }
            itemView.findViewById<TextView>(R.id.textStatus).text = statusText
            
            // Hide cancel button in client app
            itemView.findViewById<View>(R.id.btnCancel)?.visibility = View.GONE
            
            containerDownloadItems.addView(itemView)
        }
    }
    
    override fun onDestroyView() {
        try {
            requireContext().unregisterReceiver(downloadEventReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        super.onDestroyView()
    }
}
