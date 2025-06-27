package com.mtkresearch.breezeapp_kotlin.presentation

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.ActivityMainBinding
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseActivity
import com.mtkresearch.breezeapp_kotlin.presentation.home.fragment.HomeFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        setupHomeFragment(savedInstanceState)
    }

    private fun setupSystemBars() {
        // 處理系統窗口 insets，確保內容不被狀態欄遮擋
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }

    private fun setupHomeFragment(savedInstanceState: Bundle?) {
        // 只在首次創建時添加 HomeFragment
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, HomeFragment.newInstance())
            }
        }
    }
}
