package com.mtkresearch.breeze_app.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.mtkresearch.breeze_app.R;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class IntroDialog extends Dialog {
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Button btnNext;
    private int currentPage = 0;
    private static final long MIN_STORAGE_LLM_GB = 7; // Minimum 10GB storage requirement for LLM
    private static final long MIN_STORAGE_TTS_MB = 125; // Minimum 125MB storage requirement for TTS
    private boolean hasMinimumRam = true;
    private boolean hasRequiredStorage = true;
    private boolean hasRequiredModels = true;
    private final List<IntroPage> introPages;
    private OnFinalButtonClickListener finalButtonClickListener;
    private RadioGroup modelSizeRadioGroup; // Add RadioGroup for model selection

    public interface OnFinalButtonClickListener {
        void onFinalButtonClick();
    }

    public void setOnFinalButtonClickListener(OnFinalButtonClickListener listener) {
        this.finalButtonClickListener = listener;
    }

    public IntroDialog(Context context) {
        super(context);
        introPages = Arrays.asList(
            new IntroPage(
                R.drawable.ic_warning,
                    context.getString(R.string.intro_dialog_title_warning),
                    context.getString(R.string.intro_description)
            ),
            new IntroPage(
                R.drawable.ic_features,
                    context.getString(R.string.intro_dialog_title_current_features),
                    context.getString(R.string.build_features_description)
            ),
            new IntroPage(
                R.drawable.ic_requirements,
                    context.getString(R.string.intro_dialog_title_system_requirements),
                buildRequirementsDescription( context )
            )
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = this.getContext();
        
        // Check all requirements
        checkSystemRequirements();
        
        // Set dialog window properties
        if (getWindow() != null) {
            // Set a semi-transparent dim background
            getWindow().setDimAmount(0.5f);
            // Set the background to use the dialog background drawable
            getWindow().setBackgroundDrawableResource(R.drawable.bg_dialog);
            // Set the layout size
            getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }
        
        setContentView(R.layout.intro_dialog_layout);
        
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        btnNext = findViewById(R.id.btnNext);

        // Detect system theme and adjust text colors
        boolean isDarkTheme = (getContext().getResources().getConfiguration().uiMode 
            & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;

        // Find all text views in the dialog layout
        ViewGroup root = findViewById(android.R.id.content);
        adjustTextColors(root, isDarkTheme);
        
        // Configure TabLayout gravity and mode
        tabLayout.setTabGravity(TabLayout.GRAVITY_CENTER);
        tabLayout.setTabMode(TabLayout.MODE_FIXED);
        
        // Set up ViewPager2
        viewPager.setAdapter(new IntroPagerAdapter(introPages));
        viewPager.setOffscreenPageLimit(1);
        
        // Set up TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.view.setClickable(false);
        }).attach();

        btnNext.setOnClickListener(v -> {
            if (currentPage < introPages.size() - 1) {
                currentPage++;
                viewPager.setCurrentItem(currentPage);
            } else {
                // Only allow dismissal if all requirements are met
                if (meetsAllRequirements()) {
                    if (finalButtonClickListener != null) {
                        finalButtonClickListener.onFinalButtonClick();
                    }
                    dismiss();
                } else {
                    // Show warning toast with specific requirements that are not met
                    showRequirementsWarning(context);
                }
            }
            updateButtonText(context);
            updateButtonState(context);
        });
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateButtonText(context);
                updateButtonState(context);
                
                // Check if we're on the requirements page (last page)
                if (position == introPages.size() - 1) {
                    checkSystemRequirements();
                    // Show download dialog if only models are missing
                    if (!hasRequiredModels && hasMinimumRam && hasRequiredStorage) {
                        // Check if TTS models are missing
                        if (AppConstants.TTS_ENABLED && AppConstants.needsTTSModelDownload(context)) {
                            ModelDownloadDialog downloadDialog = new ModelDownloadDialog(context, IntroDialog.this, ModelDownloadDialog.DownloadMode.TTS);
                            downloadDialog.setOnDismissListener(dialog -> {
                                // Recheck requirements after dialog is dismissed
                                checkSystemRequirements();
                                // Update the requirements description
                                ((IntroPagerAdapter) viewPager.getAdapter()).updateRequirementsPage(
                                    buildRequirementsDescription(context));
                                // Update button state and text
                                updateButtonState(context);
                                updateButtonText(context);
                                // Force refresh the current page
                                viewPager.getAdapter().notifyItemChanged(currentPage);
                            });
                            downloadDialog.show();
                        } else {
                            // For LLM models
                            ModelDownloadDialog downloadDialog = new ModelDownloadDialog(context, IntroDialog.this, ModelDownloadDialog.DownloadMode.LLM);
                            downloadDialog.setOnDismissListener(dialog -> {
                                // Recheck requirements after dialog is dismissed
                                checkSystemRequirements();
                                // Update the requirements description
                                ((IntroPagerAdapter) viewPager.getAdapter()).updateRequirementsPage(
                                    buildRequirementsDescription(context));
                                // Update button state and text
                                updateButtonState(context);
                                updateButtonText(context);
                                // Force refresh the current page
                                viewPager.getAdapter().notifyItemChanged(currentPage);
                            });
                            downloadDialog.show();
                        }
                    }
                }
            }
        });
        
        updateButtonText(context);
        updateButtonState(context);
        setCancelable(false);
    }

    private void adjustTextColors(View view, boolean isDarkTheme) {
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                adjustTextColors(viewGroup.getChildAt(i), isDarkTheme);
            }
        } else if (view instanceof TextView) {
            TextView textView = (TextView) view;
            // Set text color based on theme
            int textColor = isDarkTheme ? 
                android.graphics.Color.WHITE : 
                android.graphics.Color.BLACK;
            textView.setTextColor(textColor);
        }
    }

    private void checkSystemRequirements() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
            .getMemoryInfo(memoryInfo);
        
        // Check RAM - use AppConstants.MIN_RAM_REQUIRED_GB instead of MIN_RAM_GB
        long totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024);
        hasMinimumRam = totalRamGB >= AppConstants.MIN_RAM_REQUIRED_GB;
        
        // Check Storage - Get available space in internal storage
        android.os.StatFs stat = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
        long availableBytes = stat.getAvailableBytes();
        long availableGB = availableBytes / (1024L * 1024L * 1024L);
        long availableMB = availableBytes / (1024L * 1024L);
        
        // Check Model Files
        boolean hasLLMModels = !AppConstants.needsModelDownload(getContext());
        boolean hasTTSModels = !AppConstants.TTS_ENABLED || !AppConstants.needsTTSModelDownload(getContext());
        
        // Both LLM and TTS models (if enabled) must be present
        hasRequiredModels = hasLLMModels && hasTTSModels;
        
        // Calculate required storage based on missing models
        long requiredStorageMB = 0;
        if (!hasLLMModels) {
            requiredStorageMB += MIN_STORAGE_LLM_GB * 1024; // Convert GB to MB
        }
        if (!hasTTSModels && AppConstants.TTS_ENABLED) {
            requiredStorageMB += MIN_STORAGE_TTS_MB;
        }
        
        // Check if we have enough storage for all missing models
        hasRequiredStorage = requiredStorageMB == 0 || availableMB >= requiredStorageMB;
        
        Log.d("IntroDialog", "Storage check - " +
                            "LLM Model exists: " + hasLLMModels + 
                            ", TTS Models exist: " + hasTTSModels +
                            ", Required storage (MB): " + requiredStorageMB +
                            ", Available storage (MB): " + availableMB +
                            ", Storage requirement met: " + hasRequiredStorage);
    }

    private boolean meetsAllRequirements() {
        return hasMinimumRam && hasRequiredStorage && hasRequiredModels;
    }

    private void showRequirementsWarning(Context context) {
        if (!hasMinimumRam || !hasRequiredStorage || !hasRequiredModels) {
            // Cast context to Activity for runOnUiThread
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                activity.runOnUiThread(() -> {
                    if (!hasRequiredModels) {
                        // Show model selection dialog before downloading
                        if (AppConstants.needsModelDownload(context)) {
                            showModelSelectionDialog(context);
                        } else if (AppConstants.TTS_ENABLED && AppConstants.needsTTSModelDownload(context)) {
                            // Show TTS model download dialog
                            Log.d("IntroDialog", "Showing TTS model download dialog");
                            ModelDownloadDialog downloadDialog = new ModelDownloadDialog(context, IntroDialog.this, ModelDownloadDialog.DownloadMode.TTS);
                            downloadDialog.setOnDismissListener(dialog -> {
                                checkSystemRequirements();
                                // Update the requirements description
                                ((IntroPagerAdapter) viewPager.getAdapter()).updateRequirementsPage(
                                    buildRequirementsDescription(context));
                                // Update button state and text
                                updateButtonState(context);
                                updateButtonText(context);
                            });
                            downloadDialog.show();
                        }
                    } else if (!hasMinimumRam) {
                        btnNext.setText(context.getString(R.string.insufficient_ram, AppConstants.MIN_RAM_REQUIRED_GB));
                    } else if (!hasRequiredStorage) {
                        // Check which models need to be downloaded to show appropriate storage requirement
                        boolean needsTTS = AppConstants.TTS_ENABLED && AppConstants.needsTTSModelDownload(context);
                        boolean needsLLM = AppConstants.needsModelDownload(context);

                        if (needsTTS && !needsLLM) {
                            // Only TTS model is missing
                            btnNext.setText(context.getString(R.string.insufficient_storage_mb, MIN_STORAGE_TTS_MB));
                        } else if (needsLLM) {
                            // LLM model is missing (possibly along with TTS)
                            btnNext.setText(context.getString(R.string.insufficient_storage, MIN_STORAGE_LLM_GB));
                        }
                    }
                });
            }
        }
    }

    /**
     * Shows a dialog for selecting model size before downloading
     */
    private void showModelSelectionDialog(Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        View dialogView = LayoutInflater.from(context).inflate(R.layout.model_selection_dialog, null);
        
        TextView titleText = dialogView.findViewById(R.id.modelSelectionTitle);
        TextView descriptionText = dialogView.findViewById(R.id.modelSelectionDescription);
        modelSizeRadioGroup = dialogView.findViewById(R.id.modelSizeRadioGroup);
        RadioButton largeModelRadio = dialogView.findViewById(R.id.largeModelRadio);
        RadioButton smallModelRadio = dialogView.findViewById(R.id.smallModelRadio);
        Button continueButton = dialogView.findViewById(R.id.continueButton);
        
        // Set up the dialog content
        titleText.setText(R.string.model_selection_title);
        
        // Check device RAM and set recommended option
        boolean canUselargeModel = AppConstants.canUseLargeModel(context);
        String recommendedModel = canUselargeModel ? 
            context.getString(R.string.large_model_recommended) : 
            context.getString(R.string.small_model_recommended);
            
        descriptionText.setText(context.getString(R.string.model_selection_description, recommendedModel));
        
        // Set up radio buttons with descriptions
        largeModelRadio.setText(context.getString(R.string.large_model_option, 
            AppConstants.LARGE_LLM_MODEL_DISPLAY_NAME, 
            formatStorageSize(6 * 1024L))); // 6GB
            
        smallModelRadio.setText(context.getString(R.string.small_model_option, 
            AppConstants.SMALL_LLM_MODEL_DISPLAY_NAME, 
            formatStorageSize(1 * 1024L))); // 1GB
        
        // Pre-select recommended model
        if (canUselargeModel) {
            largeModelRadio.setChecked(true);
        } else {
            smallModelRadio.setChecked(true);
            // Disable large model option if device doesn't have enough RAM
            largeModelRadio.setEnabled(false);
            largeModelRadio.setAlpha(0.5f);
        }
        
        // Build and show the dialog
        AlertDialog dialog = builder.setView(dialogView).setCancelable(false).create();
        
        // Set up continue button
        continueButton.setOnClickListener(v -> {
            // Save the selected model size preference
            saveModelSizePreference(context);
            dialog.dismiss();
            
            // Show the model download dialog
            Log.d("IntroDialog", "Showing LLM model download dialog");
            ModelDownloadDialog downloadDialog = new ModelDownloadDialog(context, IntroDialog.this, ModelDownloadDialog.DownloadMode.LLM);
            downloadDialog.setOnDismissListener(d -> {
                checkSystemRequirements();
                // Update the requirements description
                ((IntroPagerAdapter) viewPager.getAdapter()).updateRequirementsPage(
                    buildRequirementsDescription(context));
                // Update button state and text
                updateButtonState(context);
                updateButtonText(context);
            });
            downloadDialog.show();
        });
        
        dialog.show();
    }
    
    /**
     * Saves the user's model size preference to SharedPreferences
     */
    private void saveModelSizePreference(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        int selectedId = modelSizeRadioGroup.getCheckedRadioButtonId();
        if (selectedId == R.id.largeModelRadio) {
            editor.putString(AppConstants.KEY_MODEL_SIZE_PREFERENCE, AppConstants.MODEL_SIZE_LARGE);
            Log.d("IntroDialog", "User selected large model");
        } else {
            editor.putString(AppConstants.KEY_MODEL_SIZE_PREFERENCE, AppConstants.MODEL_SIZE_SMALL);
            Log.d("IntroDialog", "User selected small model");
        }
        
        editor.apply();
    }
    
    /**
     * Formats storage size in a human-readable format
     */
    private String formatStorageSize(long sizeInMB) {
        if (sizeInMB < 1024) {
            return sizeInMB + " MB";
        } else {
            float sizeInGB = sizeInMB / 1024f;
            return String.format(Locale.getDefault(), "%.1f GB", sizeInGB);
        }
    }

    private void updateButtonState(Context context) {
        boolean meetsRequirements = meetsAllRequirements();
        
        // Only check requirements on the last page
        if (currentPage == introPages.size() - 1) {
            btnNext.setEnabled(meetsRequirements);
            
            if (!meetsRequirements) {
                StringBuilder errorMessage = new StringBuilder(context.getString(R.string.cannot_proceed));
                
                if (!hasMinimumRam) {
                    errorMessage.append("\n").append(context.getString(R.string.insufficient_ram, AppConstants.MIN_RAM_REQUIRED_GB));
                }
                if (!hasRequiredStorage) {
                    // Check which model is missing to show appropriate storage requirement
                    if (!AppConstants.hasTTSModels(context) && AppConstants.TTS_ENABLED && AppConstants.needsTTSModelDownload(context)) {
                        errorMessage.append("\n").append(context.getString(R.string.insufficient_storage_mb, MIN_STORAGE_TTS_MB));
                    } else {
                        errorMessage.append("\n").append(context.getString(R.string.insufficient_storage, MIN_STORAGE_LLM_GB));
                    }
                }
                if (!hasRequiredModels) {
                    if (AppConstants.TTS_ENABLED && AppConstants.needsTTSModelDownload(context)) {
                        errorMessage.append("\n").append(context.getString(R.string.missing_models, 
                            AppConstants.TTS_MODEL_FILE, AppConstants.TTS_LEXICON_FILE));
                    } else if (AppConstants.needsModelDownload(context)) {
                        errorMessage.append("\n").append(context.getString(R.string.missing_models, 
                            AppConstants.BREEZE_MODEL_FILE, AppConstants.LLM_TOKENIZER_FILE));
                    }
                    showRequirementsWarning(context);
                }
                
                btnNext.setText(errorMessage.toString());
            } else {
                updateButtonText(context);
            }
        } else {
            // For other pages, always enable the button and show normal text
            btnNext.setEnabled(true);
            updateButtonText(context);
        }
    }

    private void updateButtonText(Context context) {
        if (currentPage == introPages.size() - 1) {
            // On the last page (requirements page)
            btnNext.setText(meetsAllRequirements() ? 
                context.getString(R.string.got_it) : 
                context.getString(R.string.requirements_not_met));
        } else {
            // For all other pages
            btnNext.setText(context.getString(R.string.next));
        }
    }

    /*
    private String buildFeaturesDescription() {
        return "• Local LLM Chat:<br/>" +
               "&nbsp;&nbsp;&nbsp;- Completely offline chat with AI<br/>" +
               "&nbsp;&nbsp;&nbsp;- Supports both CPU and MTK NPU<br/>" +
               "&nbsp;&nbsp;&nbsp;- Privacy-first: all data stays on device<br/>" +
               "&nbsp;&nbsp;&nbsp;- Text-to-Speech support" +
               "<br/><br/>" +
               "• Future Features (Coming Soon):<br/>" +
               "&nbsp;&nbsp;&nbsp;- Vision understanding (VLM)<br/>" +
               "&nbsp;&nbsp;&nbsp;- Voice input support (ASR)" +
               "<br/><br/>" +
               "Join us in building a privacy-focused AI experience!";
    }
     */

    String buildRequirementsDescription(Context context) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
                .getMemoryInfo(memoryInfo);
        long totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024);

        // Get storage information
        android.os.StatFs stat = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
        long availableBytes = stat.getAvailableBytes();
        long availableGB = availableBytes / (1024L * 1024L * 1024L);

        // Check models based on enabled services
        StringBuilder modelStatus = new StringBuilder();
        StringBuilder modelWarnings = new StringBuilder();
        
        // Check LLM models if enabled
        if (AppConstants.LLM_ENABLED) {
            String modelPath = AppConstants.getModelPath(context);
            String tokenizerPath = AppConstants.getTokenizerPath(context);
            File modelFile = new File(modelPath);
            File tokenizerFile = new File(tokenizerPath);
            
            boolean llmModelsExist = modelFile.exists() && modelFile.isFile() && modelFile.length() > 0 &&
                                   tokenizerFile.exists() && tokenizerFile.isFile() && tokenizerFile.length() > 0;
            
            String llmStatus = context.getString(llmModelsExist ? 
                R.string.model_status_llm_found : R.string.model_status_llm_missing);
            
            // Add model size information if models exist
            if (llmModelsExist) {
                // Get the model size preference
                SharedPreferences prefs = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
                String modelSizePreference = prefs.getString(AppConstants.KEY_MODEL_SIZE_PREFERENCE, AppConstants.MODEL_SIZE_AUTO);
                
                // Determine which model is being used
                String modelSizeInfo;
                if (modelSizePreference.equals(AppConstants.MODEL_SIZE_LARGE) && AppConstants.canUseLargeModel(context)) {
                    modelSizeInfo = AppConstants.LARGE_LLM_MODEL_DISPLAY_NAME;
                } else {
                    modelSizeInfo = AppConstants.SMALL_LLM_MODEL_DISPLAY_NAME;
                }
                
                // Append model size information to the status
                llmStatus += " (" + modelSizeInfo + ")";
            }
            
            modelStatus.append(context.getString(R.string.model_status_llm_title, llmStatus))
                      .append(context.getString(R.string.html_linebreak));
            
            if (!llmModelsExist) {
                modelWarnings.append(context.getString(R.string.warning_model_missing,
                    AppConstants.getAppropriateModelFile(context), AppConstants.LLM_TOKENIZER_FILE));
            }
        }
        
        // Check VLM models if enabled
        if (AppConstants.VLM_ENABLED) {
            modelStatus.append(context.getString(
                R.string.model_status_vlm_title,
                context.getString(R.string.model_status_vlm_not_implemented)))
                .append(context.getString(R.string.html_linebreak));
        }
        
        // Check ASR models if enabled
        if (AppConstants.ASR_ENABLED) {
            modelStatus.append(context.getString(
                R.string.model_status_asr_title,
                context.getString(R.string.model_status_asr_not_implemented)))
                .append(context.getString(R.string.html_linebreak));
        }

        // Check TTS if enabled
        if (AppConstants.TTS_ENABLED) {
            boolean ttsModelsExist = AppConstants.hasTTSModels(context);
            String ttsStatus = context.getString(ttsModelsExist ? 
                R.string.model_status_tts_found : R.string.model_status_tts_missing);
            modelStatus.append(context.getString(R.string.model_status_tts_title, ttsStatus))
                      .append(context.getString(R.string.html_linebreak));
            
            if (!ttsModelsExist) {
                modelWarnings.append(context.getString(R.string.warning_tts_model_missing));
            }
        }

        String ramStatus = (totalRamGB >= AppConstants.MIN_RAM_REQUIRED_GB)
                ? context.getString(R.string.ram_passed)
                : context.getString(R.string.ram_error, totalRamGB, AppConstants.MIN_RAM_REQUIRED_GB);

        String storageStatus = (availableGB >= MIN_STORAGE_LLM_GB)
                ? context.getString(R.string.storage_sufficient, availableGB)
                : context.getString(R.string.storage_error, availableGB);

        StringBuilder warningMessages = new StringBuilder();
        if (totalRamGB < AppConstants.MIN_RAM_REQUIRED_GB) {
            warningMessages.append(context.getString(R.string.warning_ram));
        }
        if (availableGB < MIN_STORAGE_LLM_GB) {
            warningMessages.append(context.getString(R.string.warning_storage, availableGB, MIN_STORAGE_LLM_GB));
        }
        warningMessages.append(modelWarnings);
        if (warningMessages.length() > 0) {
            warningMessages.append(context.getString(R.string.warning_footer));
        }

        String bullet = context.getString(R.string.html_bullet);
        String indent = context.getString(R.string.html_indent);
        String linebreak = context.getString(R.string.html_linebreak);

        return String.format(
            "%s%s%s" + // Memory header
            "%s%s%s" + // Memory required
            "%s%s%s" + // Memory device
            "%s%s%s%s" + // Memory status
            "%s%s%s" + // Model status header
            "%s%s%s" + // Model status details
            "%s%s%s" + // Storage header
            "%s%s%s" + // Storage required
            "%s%s%s" + // Storage available
            "%s%s%s%s" + // Storage status
            "%s%s" + // Warnings and footer
            "%s", // Final footer
            bullet, context.getString(R.string.requirements_header_memory), linebreak,
            indent, context.getString(R.string.requirements_memory_required, AppConstants.MIN_RAM_REQUIRED_GB), linebreak,
            indent, context.getString(R.string.requirements_memory_device, totalRamGB), linebreak,
            indent, context.getString(R.string.requirements_memory_status, ramStatus), linebreak, linebreak,
            bullet, context.getString(R.string.requirements_header_model_status), linebreak,
            indent, modelStatus.toString().replace(linebreak, linebreak + indent), linebreak,
            bullet, context.getString(R.string.requirements_header_storage), linebreak,
            indent, context.getString(R.string.requirements_storage_required, MIN_STORAGE_LLM_GB), linebreak,
            indent, context.getString(R.string.requirements_storage_available, availableGB), linebreak,
            indent, context.getString(R.string.requirements_storage_status, storageStatus), linebreak, linebreak,
            warningMessages.toString(), linebreak,
            context.getString(R.string.requirements_footer)
        );
    }

    private static class IntroPage {
        final int iconResId;
        final String title;
        final String description;

        IntroPage(int iconResId, String title, String description) {
            this.iconResId = iconResId;
            this.title = title;
            this.description = description;
        }
    }

    static class IntroPagerAdapter extends RecyclerView.Adapter<IntroPagerAdapter.PageViewHolder> {
        private final List<IntroPage> pages;
        private boolean isDarkTheme;

        IntroPagerAdapter(List<IntroPage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.intro_page_layout, parent, false);
            // Get the current theme mode
            isDarkTheme = (parent.getContext().getResources().getConfiguration().uiMode 
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            return new PageViewHolder(view, isDarkTheme);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            IntroPage page = pages.get(position);
            holder.bind(page);
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        void updateRequirementsPage(String newDescription) {
            // Update the last page (requirements page) with new description
            if (pages.size() > 0) {
                IntroPage oldPage = pages.get(pages.size() - 1);
                pages.set(pages.size() - 1, new IntroPage(
                    oldPage.iconResId,
                    oldPage.title,
                    newDescription
                ));
                notifyItemChanged(pages.size() - 1);
            }
        }

        static class PageViewHolder extends RecyclerView.ViewHolder {
            private final ImageView icon;
            private final TextView title;
            private final TextView description;
            private final boolean isDarkTheme;
            private TextView scrollHint;

            PageViewHolder(@NonNull View view, boolean isDarkTheme) {
                super(view);
                this.isDarkTheme = isDarkTheme;
                icon = view.findViewById(R.id.pageIcon);
                title = view.findViewById(R.id.pageTitle);
                description = view.findViewById(R.id.pageDescription);
                scrollHint = view.findViewById(R.id.scrollHint);
                
                // Set text colors based on theme
                int textColor = isDarkTheme ? 
                    android.graphics.Color.WHITE : 
                    android.graphics.Color.BLACK;
                title.setTextColor(textColor);
                description.setTextColor(textColor);
                if (scrollHint != null) {
                    scrollHint.setTextColor(textColor);
                }
                
                // Make links clickable
                description.setMovementMethod(LinkMovementMethod.getInstance());
                
                // Add scroll listener to show/hide scroll hint
                description.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                    if (scrollHint != null) {
                        boolean isScrollable = description.getLayout() != null && 
                            description.getLayout().getHeight() > description.getHeight();
                        scrollHint.setVisibility(isScrollable ? View.VISIBLE : View.GONE);
                    }
                });
                
                // Hide scroll hint when user starts scrolling
                description.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollHint != null && scrollY > 0) {
                        scrollHint.setVisibility(View.GONE);
                    }
                });
            }

            void bind(IntroPage page) {
                icon.setImageResource(page.iconResId);
                title.setText(page.title);
                // Convert HTML to clickable spans
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    description.setText(Html.fromHtml(page.description, Html.FROM_HTML_MODE_LEGACY));
                } else {
                    description.setText(Html.fromHtml(page.description));
                }
            }
        }
    }
} 