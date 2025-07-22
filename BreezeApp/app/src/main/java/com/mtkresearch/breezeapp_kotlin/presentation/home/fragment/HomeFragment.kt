package com.mtkresearch.breezeapp_kotlin.presentation.home.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mtkresearch.breezeapp_kotlin.R
import com.mtkresearch.breezeapp_kotlin.databinding.FragmentHomeBinding
import com.mtkresearch.breezeapp_kotlin.presentation.chat.ChatActivity
import com.mtkresearch.breezeapp_kotlin.presentation.settings.SettingsActivity

/**
 * 主頁面Fragment
 * 
 * 功能特色:
 * - 顯示歡迎訊息
 * - 提供主要功能入口（聊天、設定、下載）
 * - 簡潔清晰的導航介面
 * - 響應式設計支援不同螢幕尺寸
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupWelcomeContent()
        setupNavigationButtons()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 設置歡迎內容
     */
    private fun setupWelcomeContent() {
        // 動態設置歡迎文字
        binding.textViewWelcomeTitle.text = getString(R.string.welcome_title)
        binding.textViewWelcomeMessage.text = getString(R.string.welcome_message)
        binding.textViewWelcomeSubtitle.text = getString(R.string.welcome_subtitle)
    }

    /**
     * 設置導航按鈕
     */
    private fun setupNavigationButtons() {
        // 聊天按鈕
        binding.buttonChat.setOnClickListener {
            startChatActivity()
        }

        // 設定按鈕
        binding.buttonSettings.setOnClickListener {
            startSettingsActivity()
        }

        // 下載按鈕
        binding.buttonDownload.setOnClickListener {
            showComingSoon("下載管理功能")
        }
    }

    /**
     * 啟動聊天Activity
     */
    private fun startChatActivity() {
        val intent = Intent(requireContext(), ChatActivity::class.java)
        startActivity(intent)
    }

    private fun startSettingsActivity() {
        val intent = Intent(requireContext(), SettingsActivity::class.java)
        startActivity(intent)
    }

    /**
     * 顯示即將推出提示
     */
    private fun showComingSoon(featureName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("即將推出")
            .setMessage("$featureName 將在未來版本中推出，敬請期待！")
            .setPositiveButton("確定", null)
            .show()
    }

    companion object {
        /**
         * 創建HomeFragment實例
         */
        fun newInstance(): HomeFragment {
            return HomeFragment()
        }
    }
} 