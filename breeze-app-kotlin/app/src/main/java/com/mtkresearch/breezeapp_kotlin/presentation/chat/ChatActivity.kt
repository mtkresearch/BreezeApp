package com.mtkresearch.breezeapp_kotlin.presentation.chat

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.ActivityChatBinding
import com.mtkresearch.breezeapp_kotlin.presentation.chat.fragment.ChatFragment

/**
 * 聊天Activity
 * 
 * 功能特色:
 * - 專注於聊天功能的獨立Activity
 * - 支援返回主頁面的優雅用戶體驗
 * - 沉浸式界面設計
 * - 完整的生命週期管理
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 設置視圖綁定
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 設置工具欄
        setupToolbar()
        
        // 設置邊緣到邊緣顯示
        setupEdgeToEdge()
        
        // 載入聊天Fragment
        loadChatFragment()
    }

    /**
     * 設置工具欄
     */
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        
        // 顯示返回按鈕
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.chat_title)
            
            // 設置工具欄樣式
            setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        }
        
        // 設置工具欄背景和文字顏色
        binding.toolbar.apply {
            setBackgroundColor(getColor(R.color.primary))
            setTitleTextColor(getColor(R.color.on_primary))
            setNavigationIconTint(getColor(R.color.on_primary))
        }
    }

    /**
     * 設置邊緣到邊緣顯示
     */
    private fun setupEdgeToEdge() {
        // 設置沉浸式狀態欄
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // 調整工具欄的頂部邊距以適應狀態欄
            val toolbarParams = binding.toolbar.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            toolbarParams.topMargin = systemBars.top
            binding.toolbar.layoutParams = toolbarParams
            
            // 不調整Fragment容器的底部邊距，讓Fragment自己處理鍵盤適配
            // 這樣Fragment可以接收到完整的WindowInsets訊息
            
            // 重要：返回原始insets，不消費，讓Fragment可以接收到鍵盤相關的insets
            insets
        }
        
        // 確保Fragment容器可以分發WindowInsets
        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { view, insets ->
            // 直接傳遞insets給Fragment，不做任何消費
            insets
        }
    }

    /**
     * 載入聊天Fragment
     */
    private fun loadChatFragment() {
        supportFragmentManager.commit {
            replace(R.id.fragmentContainer, ChatFragment.newInstance())
            setReorderingAllowed(true)
        }
    }

    /**
     * 處理選項菜單項目點擊
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // 返回主頁面
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 處理觸摸事件 - 點擊鍵盤外區域收起鍵盤
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        // 只在ACTION_DOWN事件時處理，避免影響其他觸摸交互
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val chatFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? ChatFragment
            chatFragment?.handleTouchOutsideKeyboard(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 處理系統返回按鈕
     */
    override fun onBackPressed() {
        // 檢查Fragment是否處理了返回事件
        val chatFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? ChatFragment
        
        if (chatFragment?.onBackPressed() != true) {
            // Fragment沒有處理，執行默認返回行為
            super.onBackPressed()
        }
    }

    companion object {
        private const val TAG = "ChatActivity"
    }
} 