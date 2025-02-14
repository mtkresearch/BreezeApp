package com.mtkresearch.gai_android.utils;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
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
import com.mtkresearch.gai_android.R;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class IntroDialog extends Dialog {
    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private Button btnNext;
    private int currentPage = 0;
    private static final long MIN_RAM_GB = 8; // Minimum 8GB RAM requirement
    private boolean hasMinimumRam = true;
    
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
                "This is a demonstration version of the application. Stability issues are currently being addressed. " +
                "Please be aware that you may encounter unexpected behavior or crashes."
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
        
        // Check RAM requirement
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
            .getMemoryInfo(memoryInfo);
        long totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024);
        hasMinimumRam = totalRamGB >= MIN_RAM_GB;
        
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
                // Only allow dismissal if RAM requirement is met
                if (hasMinimumRam) {
                    if (finalButtonClickListener != null) {
                        finalButtonClickListener.onFinalButtonClick();
                    }
                    dismiss();
                } else {
                    // Show warning toast if RAM is insufficient
                    android.widget.Toast.makeText(getContext(),
                        "Cannot proceed: Device RAM is insufficient (minimum " + MIN_RAM_GB + "GB required)",
                        android.widget.Toast.LENGTH_LONG).show();
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

    private void updateButtonState() {
        // Disable the button on the last page if RAM is insufficient
        if (currentPage == introPages.size() - 1 && !hasMinimumRam) {
            btnNext.setEnabled(false);
            btnNext.setAlpha(0.5f);
            btnNext.setText("Insufficient RAM");
        } else {
            btnNext.setEnabled(true);
            btnNext.setAlpha(1.0f);
            updateButtonText();
        }
    }

    private void updateButtonText() {
        if (currentPage == introPages.size() - 1) {
            btnNext.setText(hasMinimumRam ? "Got it" : "Insufficient RAM");
        } else {
            btnNext.setText("Next");
        }
    }

    private String buildFeaturesDescription() {
        return "• LLM (Large Language Model):\n" +
               "  - Local CPU and MTK backend support\n" +
               "  - Streaming response generation\n\n" +
               "• VLM (Vision Language Model):\n" +
               "  - Image understanding and description\n" +
               "  - Visual question answering\n\n" +
               "• ASR (Automatic Speech Recognition):\n" +
               "  - Real-time speech-to-text\n" +
               "  - Multiple language support\n\n" +
               "• TTS (Text-to-Speech):\n" +
               "  - Natural voice synthesis\n" +
               "  - Adjustable speech parameters";
    }

    private String buildRequirementsDescription() {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        ((ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE))
            .getMemoryInfo(memoryInfo);
        long totalRamGB = memoryInfo.totalMem / (1024 * 1024 * 1024);
        
        File modelDir = new File("/data/local/tmp/llama/");
        boolean modelsExist = modelDir.exists() && modelDir.isDirectory() && 
            (modelDir.listFiles() != null && 
             modelDir.listFiles((dir, name) -> name.endsWith(".pte")).length > 0);
        
        String ramStatus;
        if (totalRamGB >= MIN_RAM_GB) {
            ramStatus = "✅ Passed";
        } else {
            ramStatus = "⛔️ Error: Insufficient RAM (" + totalRamGB + "GB < " + MIN_RAM_GB + "GB required)";
        }
        
        return "• RAM Memory:\n" +
               "  Required: " + MIN_RAM_GB + "GB+\n" +
               "  Your Device: " + totalRamGB + "GB\n" +
               "  Status: " + ramStatus + "\n\n" +
               "• Model Files:\n" +
               "  Location: /data/local/tmp/llama/\n" +
               "  Status: " + (modelsExist ? "✅ Models Found" : "⛔️ Models Missing") + "\n\n" +
               "• Storage Space:\n" +
               "  Required: 10GB+ free space\n" +
               "  Status: " + (memoryInfo.availMem > 10L * 1024 * 1024 * 1024 ? 
                              "✅ Sufficient" : "⚠️ Warning: Low Space") + "\n\n" +
               (totalRamGB < MIN_RAM_GB ? 
                "⚠️ WARNING: Your device does not meet the minimum RAM requirement.\n" +
                "The application may not function properly.\n\n" : "") +
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

            PageViewHolder(@NonNull View view, boolean isDarkTheme) {
                super(view);
                this.isDarkTheme = isDarkTheme;
                icon = view.findViewById(R.id.pageIcon);
                title = view.findViewById(R.id.pageTitle);
                description = view.findViewById(R.id.pageDescription);
                
                // Set text colors based on theme
                int textColor = isDarkTheme ? 
                    android.graphics.Color.WHITE : 
                    android.graphics.Color.BLACK;
                title.setTextColor(textColor);
                description.setTextColor(textColor);
            }

            void bind(IntroPage page) {
                icon.setImageResource(page.iconResId);
                title.setText(page.title);
                description.setText(page.description);
            }
        }
    }
} 