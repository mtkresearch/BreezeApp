package com.mtkresearch.breezeapp_kotlin.presentation

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.ActivityMainBinding
import com.mtkresearch.breezeapp_kotlin.presentation.home.fragment.HomeFragment

/**
 * 主Activity
 * 
 * 功能特色:
 * - 主頁面導航管理
 * - Fragment容器管理
 * - 統一的狀態管理
 * - 現代化Material Design
 * 
 * 當前支援的功能:
 * - ✅ Home (主頁面)
 * - ⏳ Settings (設定功能 - Phase 1.4)
 * - ⏳ Download (下載管理 - Phase 1.5)
 * - ✅ Chat (聊天功能 - 獨立Activity)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    // Fragment實例
    private val homeFragment = HomeFragment()
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 設置ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 設置Edge-to-Edge
        setupEdgeToEdge()
        
        // 預設顯示主頁面
        if (savedInstanceState == null) {
            showHomeFragment()
        }
    }

    /**
     * 設置Edge-to-Edge顯示
     */
    private fun setupEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }



    /**
     * 顯示主頁面Fragment
     */
    private fun showHomeFragment() {
        switchFragment(homeFragment, "HomeFragment")
    }



    /**
     * 切換Fragment
     */
    private fun switchFragment(fragment: Fragment, tag: String) {
        if (currentFragment === fragment) return
        
        val transaction = supportFragmentManager.beginTransaction()
        
        // 隱藏當前Fragment
        currentFragment?.let { current ->
            transaction.hide(current)
        }
        
        // 顯示目標Fragment
        val existingFragment = supportFragmentManager.findFragmentByTag(tag)
        if (existingFragment != null) {
            transaction.show(existingFragment)
            currentFragment = existingFragment
        } else {
            transaction.add(R.id.fragmentContainer, fragment, tag)
            currentFragment = fragment
        }
        
        transaction.commit()
    }

    /**
     * 處理返回按鈕
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 在主頁面按返回鍵，退出應用
        super.onBackPressed()
    }
}