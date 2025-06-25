package com.mtkresearch.breezeapp_kotlin.presentation.chat

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.ActivityChatBinding
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseActivity
import com.mtkresearch.breezeapp_kotlin.presentation.chat.fragment.ChatFragment
import com.mtkresearch.breezeapp_kotlin.presentation.settings.fragment.RuntimeSettingsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * 聊天Activity
 * 
 * 功能特色:
 * - 專注於聊天功能的獨立Activity
 * - 支援返回主頁面的優雅用戶體驗
 * - 沉浸式界面設計
 * - 完整的生命週期管理
 */
@AndroidEntryPoint
class ChatActivity : BaseActivity() {

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
        
        // 設置UI事件
        setupUIEvents()
        
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
     * 設置UI事件
     */
    private fun setupUIEvents() {
        // 設定按鈕點擊事件
        binding.btnSettings.setOnClickListener {
            showRuntimeSettingsDialog()
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
     * 顯示AI參數設定對話框
     */
    private fun showRuntimeSettingsDialog() {
        // 檢查是否已經有設定Fragment在顯示
        val existingFragment = supportFragmentManager.findFragmentByTag("runtime_settings")
        if (existingFragment != null) {
            return // 已經在顯示，不重複開啟
        }
        
        // 建立RuntimeSettingsFragment
        val runtimeSettingsFragment = RuntimeSettingsFragment.newInstance()
        
        // 顯示遮罩和設定容器
        binding.settingsOverlay.visibility = android.view.View.VISIBLE
        binding.settingsContainer.visibility = android.view.View.VISIBLE
        
        // 將Fragment添加到設定容器中
        supportFragmentManager.commit {
            replace(R.id.settings_container, runtimeSettingsFragment, "runtime_settings")
            setReorderingAllowed(true)
        }
        
        // 設定遮罩點擊事件 - 只有點擊遮罩區域（不是設定容器）才關閉設定面板
        binding.settingsOverlay.setOnTouchListener { view, event ->
            if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                // 獲取設定容器的位置和大小
                val containerLocation = IntArray(2)
                binding.settingsContainer.getLocationOnScreen(containerLocation)
                val containerLeft = containerLocation[0]
                val containerTop = containerLocation[1]
                val containerRight = containerLeft + binding.settingsContainer.width
                val containerBottom = containerTop + binding.settingsContainer.height
                
                // 獲取點擊位置
                val rawX = event.rawX.toInt()
                val rawY = event.rawY.toInt()
                
                // 檢查點擊是否在設定容器外部
                if (rawX < containerLeft || rawX > containerRight || 
                    rawY < containerTop || rawY > containerBottom) {
                    // 點擊在容器外部，關閉對話框
                    hideRuntimeSettingsDialog()
                    return@setOnTouchListener true
                }
            }
            false // 讓事件繼續傳遞
        }
    }

    /**
     * 隱藏AI參數設定對話框
     */
    private fun hideRuntimeSettingsDialog() {
        // 隱藏遮罩和設定容器
        binding.settingsOverlay.visibility = android.view.View.GONE
        binding.settingsContainer.visibility = android.view.View.GONE
        
        // 移除Fragment
        val fragment = supportFragmentManager.findFragmentByTag("runtime_settings")
        if (fragment != null) {
            supportFragmentManager.commit {
                remove(fragment)
                setReorderingAllowed(true)
            }
        }
        
        // 移除遮罩觸摸監聽器
        binding.settingsOverlay.setOnTouchListener(null)
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
            // 檢查是否點擊在發送按鈕、語音按鈕或其他重要的UI控件上
            // 如果是，則不處理鍵盤隱藏，讓按鈕正常響應點擊事件
            val chatFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? ChatFragment
            
            // 獲取所有不應該觸發鍵盤隱藏的View
            val excludeViews = mutableListOf<View>()
            
            // 查找ChatFragment中的發送按鈕和語音按鈕
            chatFragment?.view?.let { fragmentView ->
                excludeViews.addAll(listOfNotNull(
                    fragmentView.findViewById(com.mtkresearch.breezeapp_kotlin.R.id.buttonSend),
                    fragmentView.findViewById(com.mtkresearch.breezeapp_kotlin.R.id.buttonVoice),
                    fragmentView.findViewById(com.mtkresearch.breezeapp_kotlin.R.id.buttonClearChat),
                    fragmentView.findViewById(com.mtkresearch.breezeapp_kotlin.R.id.buttonNewChat)
                ))
            }
            
            // 檢查觸摸點是否在排除的View上
            val touchInExcludedArea = excludeViews.any { view ->
                val rect = android.graphics.Rect()
                view.getGlobalVisibleRect(rect)
                rect.contains(ev.rawX.toInt(), ev.rawY.toInt())
            }
            
            // 只有在不是點擊排除區域時才處理鍵盤隱藏
            if (!touchInExcludedArea) {
                chatFragment?.handleTouchOutsideKeyboard(ev)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * 處理系統返回按鈕
     */
    override fun onBackPressed() {
        // 首先檢查是否有設定面板在顯示
        if (binding.settingsContainer.visibility == android.view.View.VISIBLE) {
            hideRuntimeSettingsDialog()
            return
        }
        
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