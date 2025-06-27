package com.mtkresearch.breezeapp_kotlin.presentation.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.mtkresearch.breezeapp_kotlin.databinding.FragmentAppSettingsBinding
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.FontSize
import com.mtkresearch.breezeapp_kotlin.domain.model.settings.ThemeMode
import com.mtkresearch.breezeapp_kotlin.presentation.common.base.BaseFragment
import com.mtkresearch.breezeapp_kotlin.presentation.settings.viewmodel.AppSettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppSettingsFragment : BaseFragment() {

    private val viewModel: AppSettingsViewModel by viewModels()

    private var _binding: FragmentAppSettingsBinding? = null
    private val binding get() = _binding!!

    // 防止重複更新的標記
    private var isUpdatingUI = false
    private var hasRecreatedForTheme = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun setupUI() {
        setupListeners()
    }

    override fun observeUIState() {
        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.settings.observe(viewLifecycleOwner) { settings ->
            // 避免在 UI 更新過程中觸發監聽器
            if (isUpdatingUI) return@observe
            
            isUpdatingUI = true
            
            // 只在必要時更新 UI 控件
            if (!binding.sliderFontSize.isPressed) {
                val currentValue = when (settings.fontSize) {
                    FontSize.SMALL -> 0f
                    FontSize.MEDIUM -> 1f
                    FontSize.LARGE -> 2f
                }
                if (binding.sliderFontSize.value != currentValue) {
                    binding.sliderFontSize.value = currentValue
                }
            }

            val shouldBeChecked = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> false
            }
            if (binding.switchDarkMode.isChecked != shouldBeChecked) {
                binding.switchDarkMode.isChecked = shouldBeChecked
            }
            
            isUpdatingUI = false
        }

        // 只觀察一次主題變更事件，避免重複觸發
        viewModel.themeChangedEvent.observe(viewLifecycleOwner) { _ ->
            // 避免在同一個生命週期內重複重建 Activity
            if (!hasRecreatedForTheme && !isUpdatingUI) {
                hasRecreatedForTheme = true
                // 延遲執行以確保設定已經完全保存
                binding.root.postDelayed({
                    // 再次檢查是否已經重建過，避免重複重建
                    if (activity != null && !activity!!.isFinishing && !activity!!.isDestroyed) {
                        activity?.recreate()
                    }
                }, 100)
            }
        }
    }

    private fun setupListeners() {
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            // 防止在 UI 更新過程中觸發變更
            if (!isUpdatingUI) {
                val newMode = if (isChecked) ThemeMode.DARK else ThemeMode.LIGHT
                viewModel.onThemeModeChanged(newMode)
            }
        }

        binding.sliderFontSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser && !isUpdatingUI) {
                val fontSize = when (value) {
                    0f -> FontSize.SMALL
                    1f -> FontSize.MEDIUM
                    else -> FontSize.LARGE
                }
                viewModel.onFontSizeChanged(fontSize.scale)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = AppSettingsFragment()
    }
} 