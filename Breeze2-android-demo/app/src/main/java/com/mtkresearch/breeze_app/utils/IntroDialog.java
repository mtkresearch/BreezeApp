package com.mtkresearch.breeze_app.utils;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.mtkresearch.breeze_app.R;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class IntroDialog extends Dialog {
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Button btnNext;
    private int currentPage = 0;
    private static final long MIN_RAM_GB = 8; // Minimum 8GB RAM requirement
    private static final long MIN_STORAGE_GB = 10; // Minimum 10GB storage requirement
    private boolean hasMinimumRam = true;
    private boolean hasRequiredStorage = true;
    private boolean hasRequiredModels = true;
    
    private final List<IntroPage> introPages;
    private OnFinalButtonClickListener finalButtonClickListener;

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
                "Demo Version Warning",
                "This is a community-driven project aimed at bringing AI capabilities directly to your phone, " +
                "allowing you to experience <b>AI features completely offline</b> without privacy concerns." +
                "<br/><br/>" +
                "As a kick-off project, we welcome developers and enthusiasts to join us in improving this app. " +
                "You may encounter some stability issues as we're continuously enhancing the experience." +
                "<br/><br/>" +
                "Feel free to contribute, raise issues, or submit PRs on our GitHub repository:" +
                "<br/>" +
                "<a href=\"https://github.com/mtkresearch/Breeze2-android-demo\">Breeze2-android-demo</a>"
            ),
            new IntroPage(
                R.drawable.ic_features,
                "Current Features",
                buildFeaturesDescription()
            ),
            new IntroPage(
                R.drawable.ic_requirements,
                "System Requirements",
                buildRequirementsDescription()
            )
        );
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
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
                    showRequirementsWarning();
                }
            }
            updateButtonText();
            updateButtonState();
        });
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateButtonText();
                updateButtonState();
            }
        });
        
        updateButtonText();
        updateButtonState();
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
        
        // Check RAM
        long totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024);
        hasMinimumRam = totalRamGB >= MIN_RAM_GB;
        
        // Check Storage - Get available space in internal storage
        android.os.StatFs stat = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
        long availableBytes = stat.getAvailableBytes();
        long availableGB = availableBytes / (1024L * 1024L * 1024L);
        hasRequiredStorage = availableGB >= MIN_STORAGE_GB;
        
        // Check Model Files
        File modelDir = new File(AppConstants.LLAMA_MODEL_DIR);
        File llamaModel = new File(modelDir, AppConstants.LLAMA_MODEL_FILE);
        File breezeModel = new File(modelDir, AppConstants.BREEZE_MODEL_FILE);
        hasRequiredModels = (llamaModel.exists() && llamaModel.isFile()) || 
                           (breezeModel.exists() && breezeModel.isFile());
    }

    private boolean meetsAllRequirements() {
        return hasMinimumRam && hasRequiredStorage && hasRequiredModels;
    }

    private void showRequirementsWarning() {
        StringBuilder message = new StringBuilder("Cannot proceed: ");
        if (!hasMinimumRam) {
            message.append("\n• Insufficient RAM (minimum ").append(MIN_RAM_GB).append("GB required)");
        }
        if (!hasRequiredStorage) {
            message.append("\n• Insufficient storage space (minimum ").append(MIN_STORAGE_GB).append("GB required)");
        }
        if (!hasRequiredModels) {
            message.append("\n• Required model files (").append(AppConstants.LLAMA_MODEL_FILE)
                  .append(" or ").append(AppConstants.BREEZE_MODEL_FILE).append(") are missing");
        }
        
        android.widget.Toast.makeText(getContext(), message.toString(), 
            android.widget.Toast.LENGTH_LONG).show();
    }

    private void updateButtonState() {
        // Disable the button on the last page if any requirement is not met
        if (currentPage == introPages.size() - 1 && !meetsAllRequirements()) {
            btnNext.setEnabled(false);
            btnNext.setAlpha(0.5f);
            btnNext.setText("Requirements Not Met");
        } else {
            btnNext.setEnabled(true);
            btnNext.setAlpha(1.0f);
            updateButtonText();
        }
    }

    private void updateButtonText() {
        if (currentPage == introPages.size() - 1) {
            btnNext.setText(meetsAllRequirements() ? "Got it" : "Requirements Not Met");
        } else {
            btnNext.setText("Next");
        }
    }

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

    private String buildRequirementsDescription() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
            .getMemoryInfo(memoryInfo);
        long totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024);
        
        // Get storage information
        android.os.StatFs stat = new android.os.StatFs(android.os.Environment.getDataDirectory().getPath());
        long availableBytes = stat.getAvailableBytes();
        long availableGB = availableBytes / (1024L * 1024L * 1024L);
        
        File modelDir = new File(AppConstants.LLAMA_MODEL_DIR);
        File llamaModel = new File(modelDir, AppConstants.LLAMA_MODEL_FILE);
        File breezeModel = new File(modelDir, AppConstants.BREEZE_MODEL_FILE);
        boolean modelExists = (llamaModel.exists() && llamaModel.isFile()) || 
                           (breezeModel.exists() && breezeModel.isFile());
        
        String ramStatus;
        if (totalRamGB >= MIN_RAM_GB) {
            ramStatus = "✅ Passed";
        } else {
            ramStatus = "⛔️ Error: Insufficient RAM (" + totalRamGB + "GB < " + MIN_RAM_GB + "GB required)";
        }

        String storageStatus;
        if (availableGB >= MIN_STORAGE_GB) {
            storageStatus = "✅ Sufficient (" + availableGB + "GB available)";
        } else {
            storageStatus = "⛔️ Error: Insufficient Space (only " + availableGB + "GB available)";
        }
        
        StringBuilder warningMessages = new StringBuilder();
        if (totalRamGB < MIN_RAM_GB) {
            warningMessages.append("⚠️ WARNING: Your device does not meet the minimum RAM requirement.<br/>");
        }
        if (!modelExists) {
            warningMessages.append("⚠️ WARNING: Required model files (")
                          .append(AppConstants.LLAMA_MODEL_FILE)
                          .append(" or ")
                          .append(AppConstants.BREEZE_MODEL_FILE)
                          .append(") are missing.<br/>");
        }
        if (availableGB < MIN_STORAGE_GB) {
            warningMessages.append("⚠️ WARNING: Insufficient storage space available (" + availableGB + "GB < " + MIN_STORAGE_GB + "GB required).<br/>");
        }
        if (warningMessages.length() > 0) {
            warningMessages.append("The application may not function properly.<br/>");
        }
        
        return "• RAM Memory:<br/>" +
               "&nbsp;&nbsp;&nbsp;Required: " + MIN_RAM_GB + "GB+<br/>" +
               "&nbsp;&nbsp;&nbsp;Your Device: " + totalRamGB + "GB<br/>" +
               "&nbsp;&nbsp;&nbsp;Status: " + ramStatus + "<br/><br/>" +
               "• Model Files:<br/>" +
               "&nbsp;&nbsp;&nbsp;Location: " + AppConstants.LLAMA_MODEL_DIR + "<br/>" +
               "&nbsp;&nbsp;&nbsp;Required: " + AppConstants.LLAMA_MODEL_FILE + " or " + AppConstants.BREEZE_MODEL_FILE + "<br/>" +
               "&nbsp;&nbsp;&nbsp;Status: " + (modelExists ? "✅ Model Found" : "⛔️ Model Missing") + "<br/><br/>" +
               "• Storage Space:<br/>" +
               "&nbsp;&nbsp;&nbsp;Required: " + MIN_STORAGE_GB + "GB+ free space<br/>" +
               "&nbsp;&nbsp;&nbsp;Available: " + availableGB + "GB<br/>" +
               "&nbsp;&nbsp;&nbsp;Status: " + storageStatus + "<br/><br/>" +
               (warningMessages.length() > 0 ? warningMessages.toString() + "<br/>" : "") +
               "Please ensure all requirements are met for optimal performance.";
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

    private static class IntroPagerAdapter extends RecyclerView.Adapter<IntroPagerAdapter.PageViewHolder> {
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