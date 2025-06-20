package com.mtkresearch.breezeapp_kotlin.presentation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.ActivityMainBinding
import com.mtkresearch.breezeapp_kotlin.presentation.chat.fragment.ChatFragment

/**
 * 主Activity
 * 
 * 功能特色:
 * - 底部導航管理多個模組
 * - Fragment容器管理
 * - 統一的狀態管理
 * - 現代化Material Design
 * 
 * 當前支援的功能:
 * - ✅ Chat (聊天功能)
 * - ⏳ Settings (設定功能 - Phase 1.4)
 * - ⏳ Download (下載管理 - Phase 1.5)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    // Fragment實例
    private val chatFragment = ChatFragment()
    private var currentFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 設置ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 設置Edge-to-Edge
        setupEdgeToEdge()
        
        // 設置底部導航
        setupBottomNavigation()
        
        // 預設顯示聊天頁面
        if (savedInstanceState == null) {
            showChatFragment()
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
     * 設置底部導航
     */
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_chat -> {
                    showChatFragment()
                    true
                }
                R.id.nav_settings -> {
                    showPlaceholderFragment("設定功能", "設定功能將在Phase 1.4實作")
                    true
                }
                R.id.nav_download -> {
                    showPlaceholderFragment("下載管理", "下載管理功能將在Phase 1.5實作")
                    true
                }
                else -> false
            }
        }
        
        // 設置預設選中項目
        binding.bottomNavigation.selectedItemId = R.id.nav_chat
    }

    /**
     * 顯示聊天Fragment
     */
    private fun showChatFragment() {
        switchFragment(chatFragment, "ChatFragment")
    }

    /**
     * 顯示佔位符Fragment (用於未實作的功能)
     */
    private fun showPlaceholderFragment(title: String, message: String) {
        val fragment = PlaceholderFragment.newInstance(title, message)
        switchFragment(fragment, "PlaceholderFragment")
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
        // 如果當前不是聊天頁面，返回聊天頁面
        if (binding.bottomNavigation.selectedItemId != R.id.nav_chat) {
            binding.bottomNavigation.selectedItemId = R.id.nav_chat
            showChatFragment()
        } else {
            // 在聊天頁面按返回鍵，退出應用
            super.onBackPressed()
        }
    }
}

/**
 * 佔位符Fragment，用於顯示未實作功能的說明
 */
class PlaceholderFragment : Fragment() {
    
    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_MESSAGE = "message"
        
        fun newInstance(title: String, message: String): PlaceholderFragment {
            val fragment = PlaceholderFragment()
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            args.putString(ARG_MESSAGE, message)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_placeholder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val title = arguments?.getString(ARG_TITLE) ?: "功能"
        val message = arguments?.getString(ARG_MESSAGE) ?: "此功能尚未實作"
        
        view.findViewById<TextView>(R.id.titleText).text = title
        view.findViewById<TextView>(R.id.messageText).text = message
    }
}