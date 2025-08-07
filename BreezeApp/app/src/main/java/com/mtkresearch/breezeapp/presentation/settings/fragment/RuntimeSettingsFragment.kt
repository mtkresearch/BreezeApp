package com.mtkresearch.breezeapp.presentation.settings.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import com.mtkresearch.breezeapp.R
import com.mtkresearch.breezeapp.presentation.common.base.BaseFragment
import com.mtkresearch.breezeapp.presentation.settings.model.*
import com.mtkresearch.breezeapp.presentation.settings.viewmodel.ParameterCategory

import com.mtkresearch.breezeapp.presentation.settings.viewmodel.RuntimeSettingsViewModel
import com.mtkresearch.breezeapp.presentation.settings.viewmodel.RuntimeSettingsViewModelFactory

/**
 * AI推論層設定Fragment
 * 
 * 提供AI推論參數的設定介面
 * 功能：
 * - 參數類別切換 (LLM/VLM/ASR/TTS/GENERAL)
 * - 即時參數調整和預覽
 * - 參數驗證和應用
 * - 參數禁用狀態管理
 * - 簡化的取消/應用操作
 */
class RuntimeSettingsFragment : BaseFragment() {

    private val viewModel: RuntimeSettingsViewModel by lazy {
        ViewModelProvider(
            this,
            RuntimeSettingsViewModelFactory(requireContext())
        )[RuntimeSettingsViewModel::class.java]
    }
    
    // UI組件
    private lateinit var tabsContainer: LinearLayout
    private lateinit var parametersContainer: LinearLayout
    private lateinit var applyButton: Button
    private lateinit var cancelButton: Button
    private lateinit var validationText: TextView
    
    // Tab按鈕列表
    private val tabButtons = mutableListOf<TextView>()
    
    // 當前顯示的參數UI
    private var currentParameterViews: List<View> = emptyList()
    
    // Fragment狀態
    private val currentValues = mutableMapOf<String, Any>()
    private val originalValues = mutableMapOf<String, Any>()
    
    // 追蹤變更的參數名稱，用於顯示變更詳情
    private val changedParameters = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_runtime_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        observeViewModel()
    }

    /**
     * 設置UI組件 - BaseFragment要求實作
     */
    override fun setupUI() {
        view?.let { view ->
            initViews(view)
            setupCategoryTabs()
            setupButtons()
            
            // 通過ViewModel載入設定（遵循MVVM架構）
            viewModel.loadSettings()
        }
    }

    /**
     * 初始化UI組件
     */
    private fun initViews(view: View) {
        tabsContainer = view.findViewById(R.id.container_tabs)
        parametersContainer = view.findViewById(R.id.container_parameters)
        applyButton = view.findViewById(R.id.btn_apply)
        cancelButton = view.findViewById(R.id.btn_cancel)
        validationText = view.findViewById(R.id.text_validation)
    }

    /**
     * 設定類別Tab選擇器
     */
    private fun setupCategoryTabs() {
        val categories = listOf(
            "LLM" to ParameterCategory.LLM,
            "VLM" to ParameterCategory.VLM, 
            "ASR" to ParameterCategory.ASR,
            "TTS" to ParameterCategory.TTS,
            "通用" to ParameterCategory.GENERAL
        )
        
        tabButtons.clear()
        tabsContainer.removeAllViews()
        
        categories.forEachIndexed { index, (title, category) ->
            val tabButton = TextView(requireContext()).apply {
                text = title
                textSize = 14f
                setPadding(24, 12, 24, 12)
                gravity = android.view.Gravity.CENTER
                background = ContextCompat.getDrawable(requireContext(), R.drawable.tab_selector)
                setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.tab_text_color))
                isClickable = true
                isFocusable = true
                
                // 設置點擊效果
                setOnClickListener {
                    selectTab(index)
                    viewModel.selectCategory(category)
                }
                
                // 添加間距
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) marginStart = 8
                }
                this.layoutParams = layoutParams
            }
            
            tabButtons.add(tabButton)
            tabsContainer.addView(tabButton)
        }
        
        // 默認選中第一個Tab
        selectTab(0)
    }
    
    /**
     * 選中指定的Tab
     */
    private fun selectTab(selectedIndex: Int) {
        tabButtons.forEachIndexed { index, tabButton ->
            tabButton.isSelected = (index == selectedIndex)
        }
    }

    /**
     * 設定按鈕事件
     */
    private fun setupButtons() {
        applyButton.setOnClickListener {
            // 應用按鈕只在有變更時才可見，直接通過ViewModel保存設定
            // ViewModel會負責保存到Repository（遵循MVVM原則）
            viewModel.saveSettings()
            
            // 清空變更記錄，因為已經保存
            changedParameters.clear()
            updateChangeDisplay()
            
            dismissDialog()
        }
        
        cancelButton.setOnClickListener {
            val hasChanges = changedParameters.isNotEmpty()
            if (!hasChanges) {
                // 沒有變更，直接關閉
                dismissDialog()
            } else {
                // 有變更，重置到原始值
                resetToOriginalValues()
                dismissDialog()
            }
        }
    }

    /**
     * 觀察ViewModel狀態變化
     */
    private fun observeViewModel() {
        // 觀察選中的類別
        viewModel.selectedCategory.observe(viewLifecycleOwner) { category ->
            updateParameterUI(category)
        }
        
        // 觀察設定變更狀態
        viewModel.hasChanges.observe(viewLifecycleOwner) { hasChanges ->
            updateButtonStates(hasChanges)
        }
        
        // 觀察預覽設定
        viewModel.previewSettings.observe(viewLifecycleOwner) { settings ->
            updateParameterValues(settings)
            // 同步Fragment的變更記錄狀態
            syncFragmentStateWithPreviewSettings(settings)
        }
        
        // 觀察當前設定以初始化原始值
        viewModel.currentSettings.observe(viewLifecycleOwner) { settings ->
            initializeOriginalValues(settings)
        }
        
        // 觀察基礎狀態
        observeBaseStates()
    }

    /**
     * 初始化原始值
     */
    private fun initializeOriginalValues(settings: RuntimeSettings) {
        originalValues.clear()
        currentValues.clear()
        changedParameters.clear()
        
        // LLM 參數
        originalValues["Temperature"] = settings.llmParams.temperature
        originalValues["Top-K"] = settings.llmParams.topK
        originalValues["Top-P"] = settings.llmParams.topP
        originalValues["Max Tokens"] = settings.llmParams.maxTokens
        originalValues["Streaming"] = settings.llmParams.enableStreaming
        
        // VLM 參數
        originalValues["視覺溫度"] = settings.vlmParams.visionTemperature
        originalValues["圖像解析度"] = settings.vlmParams.imageResolution.ordinal
        originalValues["圖像分析"] = settings.vlmParams.enableImageAnalysis
        
        // ASR 參數
        originalValues["識別語言"] = getLanguageIndex(settings.asrParams.languageModel)
        originalValues["Beam大小"] = settings.asrParams.beamSize
        originalValues["噪音抑制"] = settings.asrParams.enableNoiseSuppression
        
        // TTS 參數
        originalValues["說話者聲音"] = settings.ttsParams.speakerId
        originalValues["語音速度"] = settings.ttsParams.speedRate
        originalValues["音量"] = settings.ttsParams.volume
        
        // 通用參數
        originalValues["GPU加速"] = settings.generalParams.enableGPUAcceleration
        originalValues["NPU加速"] = settings.generalParams.enableNPUAcceleration
        originalValues["並發任務數"] = settings.generalParams.maxConcurrentTasks
        originalValues["除錯日誌"] = settings.generalParams.enableDebugLogging
        
        // 初始化當前值為原始值
        currentValues.putAll(originalValues)
        
        updateChangeDisplay()
    }

    /**
     * 獲取語言模型索引
     */
    private fun getLanguageIndex(languageModel: String): Int {
        return when (languageModel) {
            "zh-TW" -> 0
            "zh-CN" -> 1
            "en-US" -> 2
            "ja-JP" -> 3
            else -> 0
        }
    }

    /**
     * 記錄參數變更
     */
    private fun recordParameterChange(parameterName: String, newValue: Any) {
        // 更新當前值
        currentValues[parameterName] = newValue
        
        // 檢查是否與原始值不同
        val originalValue = originalValues[parameterName]
        val hasChanged = originalValue != newValue
        
        if (hasChanged) {
            changedParameters.add(parameterName)
        } else {
            changedParameters.remove(parameterName)
        }
        
        // 更新顯示
        updateChangeDisplay()
    }

    /**
     * 更新參數UI
     */
    private fun updateParameterUI(category: ParameterCategory) {
        // 清除現有UI
        parametersContainer.removeAllViews()
        currentParameterViews = emptyList()
        
        // 根據類別建立對應的參數UI
        when (category) {
            ParameterCategory.LLM -> createLLMParameterUI()
            ParameterCategory.VLM -> createVLMParameterUI()
            ParameterCategory.ASR -> createASRParameterUI()
            ParameterCategory.TTS -> createTTSParameterUI()
            ParameterCategory.GENERAL -> createGeneralParameterUI()
        }
    }

    /**
     * 同步Fragment狀態與預覽設定
     * 當ViewModel的previewSettings變化時，同步Fragment的變更記錄
     */
    private fun syncFragmentStateWithPreviewSettings(previewSettings: RuntimeSettings) {
        // 更新當前值並檢查變更狀態
        val newValues = mapOf(
            "Temperature" to previewSettings.llmParams.temperature,
            "Top-K" to previewSettings.llmParams.topK,
            "Top-P" to previewSettings.llmParams.topP,
            "Max Tokens" to previewSettings.llmParams.maxTokens,
            "Streaming" to previewSettings.llmParams.enableStreaming,
            
            "視覺溫度" to previewSettings.vlmParams.visionTemperature,
            "圖像解析度" to previewSettings.vlmParams.imageResolution.ordinal,
            "圖像分析" to previewSettings.vlmParams.enableImageAnalysis,
            
            "識別語言" to getLanguageIndex(previewSettings.asrParams.languageModel),
            "Beam大小" to previewSettings.asrParams.beamSize,
            "噪音抑制" to previewSettings.asrParams.enableNoiseSuppression,
            
            "說話者聲音" to previewSettings.ttsParams.speakerId,
            "語音速度" to previewSettings.ttsParams.speedRate,
            "音量" to previewSettings.ttsParams.volume,
            
            "GPU加速" to previewSettings.generalParams.enableGPUAcceleration,
            "NPU加速" to previewSettings.generalParams.enableNPUAcceleration,
            "並發任務數" to previewSettings.generalParams.maxConcurrentTasks,
            "除錯日誌" to previewSettings.generalParams.enableDebugLogging
        )
        
        // 更新當前值
        currentValues.putAll(newValues)
        
        // 重新計算變更的參數
        changedParameters.clear()
        newValues.forEach { (paramName, newValue) ->
            val originalValue = originalValues[paramName]
            if (originalValue != newValue) {
                changedParameters.add(paramName)
            }
        }
        
        // 更新顯示
        updateChangeDisplay()
    }

    /**
     * 建立LLM參數UI
     */
    private fun createLLMParameterUI() {
        val views = mutableListOf<View>()
        
        // Temperature滑桿 (0.0-1.0)
        val temperatureView = createSeekBarParameter(
            "Temperature",
            "控制回應的創造性和隨機性",
            0f, 1f, 0.01f,
            isEnabled = true, // TODO: 從ViewModel獲取可用性狀態
            onValueChanged = { value -> 
                viewModel.updateLLMTemperature(value)
                recordParameterChange("Temperature", value)
            }
        )
        views.add(temperatureView)
        parametersContainer.addView(temperatureView)
        
        // Top-K滑桿 (1-100)
        val topKView = createSeekBarParameter(
            "Top-K",
            "限制候選詞彙的數量",
            1f, 100f, 1f,
            isEnabled = true,
            onValueChanged = { value -> 
                viewModel.updateLLMTopK(value.toInt())
                recordParameterChange("Top-K", value)
            }
        )
        views.add(topKView)
        parametersContainer.addView(topKView)
        
        // Top-P滑桿 (0.0-1.0)
        val topPView = createSeekBarParameter(
            "Top-P",
            "累積機率閾值",
            0f, 1f, 0.01f,
            isEnabled = true,
            onValueChanged = { value -> 
                viewModel.updateLLMTopP(value)
                recordParameterChange("Top-P", value)
            }
        )
        views.add(topPView)
        parametersContainer.addView(topPView)
        
        // Max Tokens滑桿 (128-4096)
        val maxTokensView = createSeekBarParameter(
            "Max Tokens",
            "最大輸出長度",
            128f, 4096f, 50f,
            isEnabled = true,
            onValueChanged = { value -> 
                viewModel.updateLLMMaxTokens(value.toInt())
                recordParameterChange("Max Tokens", value)
            }
        )
        views.add(maxTokensView)
        parametersContainer.addView(maxTokensView)
        
        // Streaming開關
        val streamingView = createSwitchParameter(
            "Streaming",
            "啟用串流輸出",
            isEnabled = true,
            onValueChanged = { enabled -> 
                viewModel.updateLLMStreaming(enabled)
                recordParameterChange("Streaming", enabled)
            }
        )
        views.add(streamingView)
        parametersContainer.addView(streamingView)
        
        currentParameterViews = views
    }

    /**
     * 建立VLM參數UI
     */
    private fun createVLMParameterUI() {
        val views = mutableListOf<View>()
        
        // Vision Temperature滑桿
        val visionTempView = createSeekBarParameter(
            "視覺溫度",
            "控制視覺理解的創造性",
            0f, 1f, 0.01f,
            isEnabled = true, // TODO: 從ViewModel獲取可用性狀態
            onValueChanged = { value -> 
                viewModel.updateVLMVisionTemperature(value)
                recordParameterChange("視覺溫度", value)
            }
        )
        views.add(visionTempView)
        parametersContainer.addView(visionTempView)
        
        // Image Resolution選擇
        val resolutionView = createSpinnerParameter(
            "圖像解析度",
            "圖像處理解析度",
            arrayOf("低 (224x224)", "中 (512x512)", "高 (1024x1024)"),
            isEnabled = true,
            onValueChanged = { index -> 
                viewModel.updateVLMImageResolution(index)
                recordParameterChange("圖像解析度", index)
            }
        )
        views.add(resolutionView)
        parametersContainer.addView(resolutionView)
        
        // Enable Image Analysis開關
        val imageAnalysisView = createSwitchParameter(
            "圖像分析",
            "啟用圖像分析功能",
            isEnabled = true,
            onValueChanged = { enabled -> 
                viewModel.updateVLMImageAnalysis(enabled)
                recordParameterChange("圖像分析", enabled)
            }
        )
        views.add(imageAnalysisView)
        parametersContainer.addView(imageAnalysisView)
        
        currentParameterViews = views
    }

    /**
     * 建立ASR參數UI
     */
    private fun createASRParameterUI() {
        val views = mutableListOf<View>()
        
        // Language Model選擇
        val languageView = createSpinnerParameter(
            "識別語言",
            "選擇語音識別語言",
            arrayOf("繁體中文", "簡體中文", "English", "日本語"),
            isEnabled = true,
            onValueChanged = { index -> 
                viewModel.updateASRLanguageModel(index)
                recordParameterChange("識別語言", index)
            }
        )
        views.add(languageView)
        parametersContainer.addView(languageView)
        
        // Beam Size滑桿
        val beamSizeView = createSeekBarParameter(
            "Beam大小",
            "搜索束大小，影響識別準確度",
            1f, 10f, 1f,
            isEnabled = true,
            onValueChanged = { value -> 
                viewModel.updateASRBeamSize(value.toInt())
                recordParameterChange("Beam大小", value)
            }
        )
        views.add(beamSizeView)
        parametersContainer.addView(beamSizeView)
        
        // Noise Suppression開關
        val noiseSuppressionView = createSwitchParameter(
            "噪音抑制",
            "啟用噪音抑制",
            isEnabled = true,
            onValueChanged = { enabled -> 
                viewModel.updateASRNoiseSuppression(enabled)
                recordParameterChange("噪音抑制", enabled)
            }
        )
        views.add(noiseSuppressionView)
        parametersContainer.addView(noiseSuppressionView)
        
        currentParameterViews = views
    }

    /**
     * 建立TTS參數UI
     */
    private fun createTTSParameterUI() {
        val views = mutableListOf<View>()
        
        // Speaker ID選擇
        val speakerView = createSpinnerParameter(
            "說話者聲音",
            "選擇說話者聲音",
            arrayOf("聲音 1", "聲音 2", "聲音 3", "聲音 4"),
            isEnabled = true,
            onValueChanged = { index -> 
                viewModel.updateTTSSpeakerId(index)
                recordParameterChange("說話者聲音", index)
            }
        )
        views.add(speakerView)
        parametersContainer.addView(speakerView)
        
        // Speed Rate滑桿
        val speedView = createSeekBarParameter(
            "語音速度",
            "語音播放速度",
            0.5f, 2.0f, 0.1f,
            isEnabled = true,
            onValueChanged = { value -> 
                viewModel.updateTTSSpeedRate(value)
                recordParameterChange("語音速度", value)
            }
        )
        views.add(speedView)
        parametersContainer.addView(speedView)
        
        // Volume滑桿
        val volumeView = createSeekBarParameter(
            "音量",
            "音量大小",
            0f, 1f, 0.1f,
            isEnabled = true,
            onValueChanged = { value -> 
                viewModel.updateTTSVolume(value)
                recordParameterChange("音量", value)
            }
        )
        views.add(volumeView)
        parametersContainer.addView(volumeView)
        
        currentParameterViews = views
    }

    /**
     * 建立通用參數UI
     */
    private fun createGeneralParameterUI() {
        val views = mutableListOf<View>()
        
        // GPU加速
        val gpuView = createSwitchParameter(
            "GPU加速",
            "啟用GPU硬體加速",
            isEnabled = true,
            onValueChanged = { enabled -> 
                viewModel.updateGPUAcceleration(enabled)
                recordParameterChange("GPU加速", enabled)
            }
        )
        views.add(gpuView)
        parametersContainer.addView(gpuView)
        
        // NPU加速
        val npuView = createSwitchParameter(
            "NPU加速",
            "啟用NPU神經處理器加速",
            isEnabled = false, // 示例：某些設備可能不支援NPU
            onValueChanged = { enabled -> 
                viewModel.updateNPUAcceleration(enabled)
                recordParameterChange("NPU加速", enabled)
            }
        )
        views.add(npuView)
        parametersContainer.addView(npuView)
        
        // Max Concurrent Tasks
        val tasksView = createSeekBarParameter(
            "並發任務數",
            "最大並發處理任務數",
            1f, 8f, 1f,
            isEnabled = true,
            onValueChanged = { value -> 
                viewModel.updateMaxConcurrentTasks(value.toInt())
                recordParameterChange("並發任務數", value)
            }
        )
        views.add(tasksView)
        parametersContainer.addView(tasksView)
        
        // Debug Logging
        val debugView = createSwitchParameter(
            "除錯日誌",
            "啟用詳細除錯日誌",
            isEnabled = true,
            onValueChanged = { enabled -> 
                viewModel.updateDebugLogging(enabled)
                recordParameterChange("除錯日誌", enabled)
            }
        )
        views.add(debugView)
        parametersContainer.addView(debugView)
        
        currentParameterViews = views
    }

    /**
     * 建立滑桿參數UI
     */
    private fun createSeekBarParameter(
        title: String,
        description: String,
        minValue: Float,
        maxValue: Float,
        step: Float,
        isEnabled: Boolean,
        onValueChanged: (Float) -> Unit
    ): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            alpha = if (isEnabled) 1.0f else 0.6f
        }
        
        val headerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(if (isEnabled) 
                requireContext().getColor(R.color.text_primary)
                else requireContext().getColor(R.color.text_disabled))
        }
        headerLayout.addView(titleText)
        
        val valueText = TextView(requireContext()).apply {
            textSize = 14f
            minWidth = 80
            gravity = android.view.Gravity.END
            tag = "valueText"  // 設置tag以便查找
            setTextColor(if (isEnabled) 
                requireContext().getColor(R.color.text_primary)
                else requireContext().getColor(R.color.text_disabled))
        }
        headerLayout.addView(valueText)
        
        layout.addView(headerLayout)
        
        val seekBar = SeekBar(requireContext()).apply {
            max = ((maxValue - minValue) / step).toInt()
            this.isEnabled = isEnabled
            
            // 設置Primary橘色主題
            progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.primary)
            )
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.outline_variant)
            )
            thumbTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.primary)
            )
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && isEnabled) {
                        val value = minValue + (progress * step)
                        val clampedValue = value.coerceIn(minValue, maxValue)
                        val formattedValue = when (title) {
                            "Temperature", "Top-P", "視覺溫度", "語音速度", "音量" -> String.format("%.2f", clampedValue)
                            else -> clampedValue.toInt().toString()
                        }
                        valueText.text = formattedValue
                    }
                }
                
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    if (isEnabled) {
                        val value = minValue + ((seekBar?.progress ?: 0) * step)
                        val clampedValue = value.coerceIn(minValue, maxValue)
                        onValueChanged(clampedValue)
                    }
                }
            })
        }
        layout.addView(seekBar)
        
        val descriptionText = TextView(requireContext()).apply {
            text = if (!isEnabled) "$description (不可用)" else description
            textSize = 12f
            setTextColor(if (isEnabled) 
                requireContext().getColor(R.color.text_secondary)
                else requireContext().getColor(R.color.text_disabled))
        }
        layout.addView(descriptionText)
        
        // 設置完整的tag信息
        layout.tag = mapOf(
            "seekBar" to seekBar,
            "valueText" to valueText,
            "title" to title,
            "minValue" to minValue,
            "maxValue" to maxValue,
            "step" to step,
            "isEnabled" to isEnabled
        )
        
        // 設置初始值顯示（從當前設定獲取）
        val currentSettings = viewModel.currentSettings.value
        if (currentSettings != null) {
            val initialValue = when (title) {
                "Temperature" -> currentSettings.llmParams.temperature
                "Top-K" -> currentSettings.llmParams.topK.toFloat()
                "Top-P" -> currentSettings.llmParams.topP
                "Max Tokens" -> currentSettings.llmParams.maxTokens.toFloat()
                "視覺溫度" -> currentSettings.vlmParams.visionTemperature
                "Beam大小" -> currentSettings.asrParams.beamSize.toFloat()
                "語音速度" -> currentSettings.ttsParams.speedRate
                "音量" -> currentSettings.ttsParams.volume
                "並發任務數" -> currentSettings.generalParams.maxConcurrentTasks.toFloat()
                else -> minValue
            }
            
            // 設置SeekBar初始位置
            val initialProgress = ((initialValue - minValue) / step).toInt()
            seekBar.progress = initialProgress
            
            // 設置標籤初始顯示
            val formattedValue = when (title) {
                "Temperature", "Top-P", "視覺溫度", "語音速度", "音量" -> String.format("%.2f", initialValue)
                else -> initialValue.toInt().toString()
            }
            valueText.text = formattedValue
        }
        
        return layout
    }

    /**
     * 建立開關參數UI
     */
    private fun createSwitchParameter(
        title: String,
        description: String,
        isEnabled: Boolean,
        onValueChanged: (Boolean) -> Unit
    ): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            alpha = if (isEnabled) 1.0f else 0.6f
        }
        
        val headerLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        
        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setTextColor(if (isEnabled) 
                requireContext().getColor(R.color.text_primary)
                else requireContext().getColor(R.color.text_disabled))
        }
        headerLayout.addView(titleText)
        
        val switch = Switch(requireContext()).apply {
            this.isEnabled = isEnabled
            
            // 設置橘色主題 - 使用顏色狀態列表
            thumbTintList = ContextCompat.getColorStateList(requireContext(), R.color.switch_thumb_color)
            trackTintList = ContextCompat.getColorStateList(requireContext(), R.color.switch_track_color)
            
            setOnCheckedChangeListener { _, isChecked ->
                if (isEnabled) {
                    onValueChanged(isChecked)
                }
            }
        }
        headerLayout.addView(switch)
        
        layout.addView(headerLayout)
        
        val descriptionText = TextView(requireContext()).apply {
            text = if (!isEnabled) "$description (不可用)" else description
            textSize = 12f
            setTextColor(if (isEnabled) 
                requireContext().getColor(R.color.text_secondary)
                else requireContext().getColor(R.color.text_disabled))
        }
        layout.addView(descriptionText)
        
        layout.tag = mapOf(
            "switch" to switch, 
            "title" to title,
            "isEnabled" to isEnabled
        )
        
        // 設置初始值（從當前設定獲取）
        val currentSettings = viewModel.currentSettings.value
        if (currentSettings != null) {
            val initialValue = when (title) {
                "Streaming" -> currentSettings.llmParams.enableStreaming
                "圖像分析" -> currentSettings.vlmParams.enableImageAnalysis
                "噪音抑制" -> currentSettings.asrParams.enableNoiseSuppression
                "GPU加速" -> currentSettings.generalParams.enableGPUAcceleration
                "NPU加速" -> currentSettings.generalParams.enableNPUAcceleration
                "除錯日誌" -> currentSettings.generalParams.enableDebugLogging
                else -> false
            }
            switch.isChecked = initialValue
        }
        
        return layout
    }

    /**
     * 建立下拉選單參數UI
     */
    private fun createSpinnerParameter(
        title: String,
        description: String,
        options: Array<String>,
        isEnabled: Boolean,
        onValueChanged: (Int) -> Unit
    ): View {
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            alpha = if (isEnabled) 1.0f else 0.6f
        }
        
        val titleText = TextView(requireContext()).apply {
            text = title
            textSize = 16f
            setTextColor(if (isEnabled) 
                requireContext().getColor(R.color.text_primary)
                else requireContext().getColor(R.color.text_disabled))
        }
        layout.addView(titleText)
        
        val descriptionText = TextView(requireContext()).apply {
            text = if (!isEnabled) "$description (不可用)" else description
            textSize = 12f
            setTextColor(if (isEnabled) 
                requireContext().getColor(R.color.text_secondary)
                else requireContext().getColor(R.color.text_disabled))
        }
        layout.addView(descriptionText)
        
        val spinner = Spinner(requireContext()).apply {
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            this.adapter = adapter
            this.isEnabled = isEnabled
            
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (isEnabled) {
                        onValueChanged(position)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        layout.addView(spinner)
        
        layout.tag = mapOf(
            "spinner" to spinner, 
            "title" to title,
            "isEnabled" to isEnabled
        )
        
        // 設置初始值（從當前設定獲取）
        val currentSettings = viewModel.currentSettings.value
        if (currentSettings != null) {
            val initialSelection = when (title) {
                "圖像解析度" -> currentSettings.vlmParams.imageResolution.ordinal
                "識別語言" -> getLanguageIndex(currentSettings.asrParams.languageModel)
                "說話者聲音" -> currentSettings.ttsParams.speakerId
                else -> 0
            }
            spinner.setSelection(initialSelection)
        }
        
        return layout
    }

    /**
     * 更新參數值顯示
     */
    private fun updateParameterValues(settings: RuntimeSettings) {
        // 根據當前選中的類別更新對應的UI值
        val currentCategory = viewModel.selectedCategory.value ?: return
        
        when (currentCategory) {
            ParameterCategory.LLM -> updateLLMParameterValues(settings.llmParams)
            ParameterCategory.VLM -> updateVLMParameterValues(settings.vlmParams)
            ParameterCategory.ASR -> updateASRParameterValues(settings.asrParams)
            ParameterCategory.TTS -> updateTTSParameterValues(settings.ttsParams)
            ParameterCategory.GENERAL -> updateGeneralParameterValues(settings.generalParams)
        }
    }

    /**
     * 更新LLM參數UI值
     */
    private fun updateLLMParameterValues(params: LLMParameters) {
        currentParameterViews.forEach { view ->
            val tag = view.tag as? Map<String, Any> ?: return@forEach
            
            when {
                tag.containsKey("seekBar") -> {
                    val seekBar = tag["seekBar"] as? SeekBar ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "Temperature" -> updateSeekBarValue(seekBar, params.temperature, tag)
                        "Top-K" -> updateSeekBarValue(seekBar, params.topK.toFloat(), tag)
                        "Top-P" -> updateSeekBarValue(seekBar, params.topP, tag)
                        "Max Tokens" -> updateSeekBarValue(seekBar, params.maxTokens.toFloat(), tag)
                    }
                }
                tag.containsKey("switch") -> {
                    val switch = tag["switch"] as? Switch ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "Streaming" -> updateSwitchValue(switch, params.enableStreaming)
                    }
                }
            }
        }
    }

    /**
     * 更新VLM參數UI值
     */
    private fun updateVLMParameterValues(params: VLMParameters) {
        currentParameterViews.forEach { view ->
            val tag = view.tag as? Map<String, Any> ?: return@forEach
            
            when {
                tag.containsKey("seekBar") -> {
                    val seekBar = tag["seekBar"] as? SeekBar ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "視覺溫度" -> updateSeekBarValue(seekBar, params.visionTemperature, tag)
                    }
                }
                tag.containsKey("spinner") -> {
                    val spinner = tag["spinner"] as? Spinner ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "圖像解析度" -> updateSpinnerValue(spinner, params.imageResolution.ordinal)
                    }
                }
                tag.containsKey("switch") -> {
                    val switch = tag["switch"] as? Switch ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "圖像分析" -> updateSwitchValue(switch, params.enableImageAnalysis)
                    }
                }
            }
        }
    }

    /**
     * 更新ASR參數UI值
     */
    private fun updateASRParameterValues(params: ASRParameters) {
        currentParameterViews.forEach { view ->
            val tag = view.tag as? Map<String, Any> ?: return@forEach
            
            when {
                tag.containsKey("spinner") -> {
                    val spinner = tag["spinner"] as? Spinner ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "識別語言" -> updateSpinnerValue(spinner, getLanguageIndex(params.languageModel))
                    }
                }
                tag.containsKey("seekBar") -> {
                    val seekBar = tag["seekBar"] as? SeekBar ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "Beam大小" -> updateSeekBarValue(seekBar, params.beamSize.toFloat(), tag)
                    }
                }
                tag.containsKey("switch") -> {
                    val switch = tag["switch"] as? Switch ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "噪音抑制" -> updateSwitchValue(switch, params.enableNoiseSuppression)
                    }
                }
            }
        }
    }

    /**
     * 更新TTS參數UI值
     */
    private fun updateTTSParameterValues(params: TTSParameters) {
        currentParameterViews.forEach { view ->
            val tag = view.tag as? Map<String, Any> ?: return@forEach
            
            when {
                tag.containsKey("spinner") -> {
                    val spinner = tag["spinner"] as? Spinner ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "說話者聲音" -> updateSpinnerValue(spinner, params.speakerId)
                    }
                }
                tag.containsKey("seekBar") -> {
                    val seekBar = tag["seekBar"] as? SeekBar ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "語音速度" -> updateSeekBarValue(seekBar, params.speedRate, tag)
                        "音量" -> updateSeekBarValue(seekBar, params.volume, tag)
                    }
                }
            }
        }
    }

    /**
     * 更新通用參數UI值
     */
    private fun updateGeneralParameterValues(params: GeneralParameters) {
        currentParameterViews.forEach { view ->
            val tag = view.tag as? Map<String, Any> ?: return@forEach
            
            when {
                tag.containsKey("seekBar") -> {
                    val seekBar = tag["seekBar"] as? SeekBar ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "並發任務數" -> updateSeekBarValue(seekBar, params.maxConcurrentTasks.toFloat(), tag)
                    }
                }
                tag.containsKey("switch") -> {
                    val switch = tag["switch"] as? Switch ?: return@forEach
                    val title = tag["title"] as? String ?: return@forEach
                    
                    when (title) {
                        "GPU加速" -> updateSwitchValue(switch, params.enableGPUAcceleration)
                        "NPU加速" -> updateSwitchValue(switch, params.enableNPUAcceleration)
                        "除錯日誌" -> updateSwitchValue(switch, params.enableDebugLogging)
                    }
                }
            }
        }
    }

    /**
     * 更新SeekBar值（避免觸發onValueChange事件）
     */
    private fun updateSeekBarValue(seekBar: SeekBar, value: Float, tag: Map<String, Any>) {
        val minValue = tag["minValue"] as? Float ?: 0f
        val step = tag["step"] as? Float ?: 1f
        
        // 暫時移除監聽器，避免觸發回調
        seekBar.setOnSeekBarChangeListener(null)
        
        // 計算進度值
        val progress = ((value - minValue) / step).toInt()
        seekBar.progress = progress
        
        // 重新設置監聽器
        val title = tag["title"] as? String ?: ""
        val maxValue = tag["maxValue"] as? Float ?: 100f
        val isEnabled = tag["isEnabled"] as? Boolean ?: true
        
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && isEnabled) {
                    val newValue = minValue + (progress * step)
                    val clampedValue = newValue.coerceIn(minValue, maxValue)
                    updateSeekBarLabel(seekBar, clampedValue, tag)
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if (isEnabled) {
                    val newValue = minValue + (seekBar?.progress ?: 0) * step
                    val clampedValue = newValue.coerceIn(minValue, maxValue)
                    
                    // 觸發相應的參數更新
                    when (title) {
                        "Temperature" -> {
                            viewModel.updateLLMTemperature(clampedValue)
                            recordParameterChange("Temperature", clampedValue)
                        }
                        "Top-K" -> {
                            viewModel.updateLLMTopK(clampedValue.toInt())
                            recordParameterChange("Top-K", clampedValue)
                        }
                        "Top-P" -> {
                            viewModel.updateLLMTopP(clampedValue)
                            recordParameterChange("Top-P", clampedValue)
                        }
                        "Max Tokens" -> {
                            viewModel.updateLLMMaxTokens(clampedValue.toInt())
                            recordParameterChange("Max Tokens", clampedValue)
                        }
                        "視覺溫度" -> {
                            viewModel.updateVLMVisionTemperature(clampedValue)
                            recordParameterChange("視覺溫度", clampedValue)
                        }
                        "Beam大小" -> {
                            viewModel.updateASRBeamSize(clampedValue.toInt())
                            recordParameterChange("Beam大小", clampedValue)
                        }
                        "語音速度" -> {
                            viewModel.updateTTSSpeedRate(clampedValue)
                            recordParameterChange("語音速度", clampedValue)
                        }
                        "音量" -> {
                            viewModel.updateTTSVolume(clampedValue)
                            recordParameterChange("音量", clampedValue)
                        }
                        "並發任務數" -> {
                            viewModel.updateMaxConcurrentTasks(clampedValue.toInt())
                            recordParameterChange("並發任務數", clampedValue)
                        }
                    }
                }
            }
        })
        
        // 更新標籤顯示
        updateSeekBarLabel(seekBar, value, tag)
    }

    /**
     * 更新Switch值（避免觸發onValueChange事件）
     */
    private fun updateSwitchValue(switch: Switch, value: Boolean) {
        // 如果值沒有變化，直接返回
        if (switch.isChecked == value) {
            return
        }
        
        // 暫時移除監聽器，避免觸發回調
        switch.setOnCheckedChangeListener(null)
        
        // 設置值
        switch.isChecked = value
        
        // 重新設置監聽器
        val parentView = switch.parent as? ViewGroup
        val tag = parentView?.tag as? Map<String, Any>
        val title = tag?.get("title") as? String ?: ""
        val isEnabled = tag?.get("isEnabled") as? Boolean ?: true
        
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (isEnabled) {
                // 觸發相應的參數更新
                when (title) {
                    "Streaming" -> {
                        viewModel.updateLLMStreaming(isChecked)
                        recordParameterChange("Streaming", isChecked)
                    }
                    "圖像分析" -> {
                        viewModel.updateVLMImageAnalysis(isChecked)
                        recordParameterChange("圖像分析", isChecked)
                    }
                    "噪音抑制" -> {
                        viewModel.updateASRNoiseSuppression(isChecked)
                        recordParameterChange("噪音抑制", isChecked)
                    }
                    "GPU加速" -> {
                        viewModel.updateGPUAcceleration(isChecked)
                        recordParameterChange("GPU加速", isChecked)
                    }
                    "NPU加速" -> {
                        viewModel.updateNPUAcceleration(isChecked)
                        recordParameterChange("NPU加速", isChecked)
                    }
                    "除錯日誌" -> {
                        viewModel.updateDebugLogging(isChecked)
                        recordParameterChange("除錯日誌", isChecked)
                    }
                }
            }
        }
    }

    /**
     * 更新Spinner值（避免觸發onValueChange事件）
     */
    private fun updateSpinnerValue(spinner: Spinner, value: Int) {
        // 如果值沒有變化，直接返回
        if (spinner.selectedItemPosition == value) {
            return
        }
        
        // 暫時移除監聽器，避免觸發回調
        spinner.onItemSelectedListener = null
        
        // 設置值
        spinner.setSelection(value)
        
        // 重新設置監聽器
        val parentView = spinner.parent as? ViewGroup
        val tag = parentView?.tag as? Map<String, Any>
        val title = tag?.get("title") as? String ?: ""
        val isEnabled = tag?.get("isEnabled") as? Boolean ?: true
        
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isEnabled) {
                    // 觸發相應的參數更新
                    when (title) {
                        "圖像解析度" -> {
                            viewModel.updateVLMImageResolution(position)
                            recordParameterChange("圖像解析度", position)
                        }
                        "識別語言" -> {
                            viewModel.updateASRLanguageModel(position)
                            recordParameterChange("識別語言", position)
                        }
                        "說話者聲音" -> {
                            viewModel.updateTTSSpeakerId(position)
                            recordParameterChange("說話者聲音", position)
                        }
                    }
                }
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /**
     * 更新SeekBar標籤顯示
     */
    private fun updateSeekBarLabel(seekBar: SeekBar?, value: Float, tag: Map<String, Any>) {
        val parentView = seekBar?.parent as? ViewGroup ?: return
        val valueText = parentView.findViewWithTag<TextView>("valueText") ?: return
        
        val title = tag["title"] as? String ?: ""
        val formattedValue = when (title) {
            "Temperature", "Top-P", "視覺溫度", "語音速度", "音量" -> String.format("%.2f", value)
            else -> value.toInt().toString()
        }
        
        valueText.text = formattedValue
    }

    /**
     * 更新按鈕狀態
     */
    private fun updateButtonStates(hasChanges: Boolean) {
        applyButton.isEnabled = hasChanges
        
        // 更新按鈕文字以反映狀態
        if (hasChanges) {
            applyButton.text = "應用變更"
            cancelButton.text = "取消"
        } else {
            applyButton.text = "應用"
            cancelButton.text = "關閉"
        }
    }

    /**
     * 觀察基礎狀態 (Loading, Error, Success)
     */
    private fun observeBaseStates() {
        // 觀察Loading狀態
        viewModel.isLoading.collectSafely { isLoading ->
            if (isLoading) {
                showLoading()
            } else {
                hideLoading()
            }
        }
        
        // 觀察錯誤狀態
        viewModel.error.collectSafely { error ->
            error?.let {
                showError(it) {
                    viewModel.clearError()
                }
            }
        }
        
        // 觀察成功訊息
        viewModel.successMessage.collectSafely { message ->
            message?.let {
                showSuccess(it)
                viewModel.clearSuccessMessage()
            }
        }
    }

    /**
     * 更新變更狀態顯示
     */
    private fun updateChangeDisplay() {
        val hasChanges = changedParameters.isNotEmpty()
        
        if (!hasChanges) {
            validationText.text = "尚未進行任何變更"
            validationText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        } else {
            // 顯示具體變更的參數名稱
            val changedList = changedParameters.sorted().joinToString("、")
            val changeText = if (changedParameters.size <= 3) {
                "已變更：$changedList"
            } else {
                val firstThree = changedParameters.sorted().take(3).joinToString("、")
                "已變更：$firstThree 等 ${changedParameters.size} 項設定"
            }
            
            validationText.text = changeText
            validationText.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
        }
        
        // 更新按鈕狀態和可見性
        val buttonContainer = view?.findViewById<LinearLayout>(R.id.container_buttons)
        
        if (hasChanges) {
            // 有變更：顯示兩個按鈕，居中對齊並平行分配
            cancelButton.text = "取消變更"
            applyButton.text = "應用變更"
            applyButton.visibility = View.VISIBLE
            buttonContainer?.gravity = android.view.Gravity.CENTER
            
            // 調整按鈕布局權重（各佔一半）並保持間距
            val cancelParams = cancelButton.layoutParams as LinearLayout.LayoutParams
            val applyParams = applyButton.layoutParams as LinearLayout.LayoutParams
            
            // 設置權重為1使兩個按鈕平分空間
            cancelParams.weight = 1f
            cancelParams.width = 0 // 使用weight時width應為0
            cancelParams.marginEnd = 8 // 保持原有間距
            
            applyParams.weight = 1f
            applyParams.width = 0 // 使用weight時width應為0
            applyParams.marginStart = 8 // 保持原有間距
            
            cancelButton.layoutParams = cancelParams
            applyButton.layoutParams = applyParams
        } else {
            // 無變更：只顯示關閉按鈕，靠右對齊
            cancelButton.text = "關閉"
            applyButton.visibility = View.GONE
            buttonContainer?.gravity = android.view.Gravity.END
            
            // 調整取消按鈕為wrap_content寬度
            val cancelParams = cancelButton.layoutParams as LinearLayout.LayoutParams
            cancelParams.weight = 0f
            cancelParams.width = LinearLayout.LayoutParams.WRAP_CONTENT
            cancelButton.layoutParams = cancelParams
        }
    }

    /**
     * 重置到原始值
     */
    private fun resetToOriginalValues() {
        // 將當前值重置為原始值
        currentValues.putAll(originalValues)
        changedParameters.clear()
        
        // 重置ViewModel中的預覽設定
        viewModel.discardChanges()
        
        updateChangeDisplay()
    }

    /**
     * 關閉對話框
     */
    private fun dismissDialog() {
        requireActivity().onBackPressed()
    }

    companion object {
        fun newInstance(): RuntimeSettingsFragment {
            return RuntimeSettingsFragment()
        }
    }
} 