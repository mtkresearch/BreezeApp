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
    
    private final List<IntroPage> introPages;

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
        
        // Set up ViewPager2
        viewPager.setAdapter(new IntroPagerAdapter(introPages));
        viewPager.setOffscreenPageLimit(1);
        
        // Set up TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> 
            tab.view.setClickable(false)).attach();
        
        btnNext.setOnClickListener(v -> {
            if (currentPage < introPages.size() - 1) {
                currentPage++;
                viewPager.setCurrentItem(currentPage);
            } else {
                dismiss();
            }
            updateButtonText();
        });
        
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                updateButtonText();
            }
        });
        
        updateButtonText();
        setCancelable(false);
    }

    private void updateButtonText() {
        btnNext.setText(currentPage == introPages.size() - 1 ? "Got it" : "Next");
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
        
        return "• RAM Memory:\n" +
               "  Required: 12GB+\n" +
               "  Your Device: " + totalRamGB + "GB\n" +
               "  Status: " + (totalRamGB >= 12 ? "✅ Passed" : "⚠️ Warning: Low Memory") + "\n\n" +
               "• Model Files:\n" +
               "  Location: /data/local/tmp/llama/\n" +
               "  Status: " + (modelsExist ? "✅ Models Found" : "❌ Models Missing") + "\n\n" +
               "• Storage Space:\n" +
               "  Required: 10GB+ free space\n" +
               "  Status: " + (memoryInfo.availMem > 10L * 1024 * 1024 * 1024 ? 
                              "✅ Sufficient" : "⚠️ Warning: Low Space") + "\n\n" +
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

        IntroPagerAdapter(List<IntroPage> pages) {
            this.pages = pages;
        }

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.intro_page_layout, parent, false);
            return new PageViewHolder(view);
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

            PageViewHolder(@NonNull View view) {
                super(view);
                icon = view.findViewById(R.id.pageIcon);
                title = view.findViewById(R.id.pageTitle);
                description = view.findViewById(R.id.pageDescription);
            }

            void bind(IntroPage page) {
                icon.setImageResource(page.iconResId);
                title.setText(page.title);
                description.setText(page.description);
            }
        }
    }
} 