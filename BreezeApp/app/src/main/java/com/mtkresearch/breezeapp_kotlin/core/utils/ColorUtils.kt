package com.mtkresearch.breezeapp_kotlin.core.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.util.Log
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.mtkresearch.breezeapp_kotlin.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 顏色工具類
 * 提供動態顏色對比、主題支援和無障礙功能
 */
object ColorUtils {

    private const val TAG = "ColorUtils"

    /**
     * 顏色配置數據類
     */
    data class ColorSet(
        @ColorInt val backgroundColor: Int,
        @ColorInt val textColor: Int,
        @ColorInt val accentColor: Int = backgroundColor
    )

    /**
     * 無障礙驗證結果
     */
    data class AccessibilityResult(
        val contrastRatio: Double,
        val meetsAA: Boolean,
        val meetsAAA: Boolean,
        val recommendation: String
    )

    /**
     * 檢查當前是否為深色主題
     */
    fun isDarkTheme(context: Context): Boolean {
        return when (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
    }

    /**
     * 計算兩個顏色之間的對比度
     * 返回值範圍: 1.0 (無對比) 到 21.0 (最大對比)
     */
    fun getContrastRatio(@ColorInt foreground: Int, @ColorInt background: Int): Double {
        val luminance1 = calculateLuminance(foreground) + 0.05
        val luminance2 = calculateLuminance(background) + 0.05
        
        return max(luminance1, luminance2) / min(luminance1, luminance2)
    }

    /**
     * 計算顏色的相對亮度
     */
    private fun calculateLuminance(@ColorInt color: Int): Double {
        val red = Color.red(color) / 255.0
        val green = Color.green(color) / 255.0
        val blue = Color.blue(color) / 255.0
        
        val r = if (red <= 0.03928) red / 12.92 else ((red + 0.055) / 1.055).pow(2.4)
        val g = if (green <= 0.03928) green / 12.92 else ((green + 0.055) / 1.055).pow(2.4)
        val b = if (blue <= 0.03928) blue / 12.92 else ((blue + 0.055) / 1.055).pow(2.4)
        
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * 驗證顏色組合的無障礙性
     */
    fun validateAccessibility(
        context: Context,
        @ColorInt textColor: Int,
        @ColorInt backgroundColor: Int
    ): AccessibilityResult {
        val contrastRatio = getContrastRatio(textColor, backgroundColor)
        val meetsAA = contrastRatio >= 4.5
        val meetsAAA = contrastRatio >= 7.0
        
        val recommendation = when {
            meetsAAA -> "優秀的對比度"
            meetsAA -> "符合AA標準"
            contrastRatio >= 3.0 -> "建議提高對比度"
            else -> "對比度過低，建議調整顏色"
        }
        
        return AccessibilityResult(contrastRatio, meetsAA, meetsAAA, recommendation)
    }

    /**
     * 自動選擇最佳文字顏色
     */
    @ColorInt
    fun getBestTextColor(context: Context, @ColorInt backgroundColor: Int): Int {
        val whiteContrast = getContrastRatio(Color.WHITE, backgroundColor)
        val blackContrast = getContrastRatio(Color.BLACK, backgroundColor)
        
        return if (whiteContrast > blackContrast) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    /**
     * 調整顏色的透明度
     */
    @ColorInt
    fun adjustAlpha(@ColorInt color: Int, alpha: Float): Int {
        val clampedAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(clampedAlpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    /**
     * 調整顏色的亮度
     */
    @ColorInt
    fun adjustBrightness(@ColorInt color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] * factor).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    /**
     * 獲取訊息顏色配置
     */
    fun getMessageColors(context: Context, messageType: MessageType): ColorSet {
        return when (messageType) {
            MessageType.USER -> {
                val bgColor = ContextCompat.getColor(context, R.color.user_message_bg)
                val textColor = ContextCompat.getColor(context, R.color.user_message_text)
                ColorSet(bgColor, textColor)
            }
            MessageType.AI -> {
                val bgColor = ContextCompat.getColor(context, R.color.ai_message_bg)
                val textColor = ContextCompat.getColor(context, R.color.ai_message_text)
                ColorSet(bgColor, textColor)
            }
            MessageType.SYSTEM -> {
                val bgColor = ContextCompat.getColor(context, R.color.system_message_bg)
                val textColor = ContextCompat.getColor(context, R.color.system_message_text)
                ColorSet(bgColor, textColor)
            }
        }
    }

    /**
     * 獲取嚴重程度顏色配置
     */
    fun getSeverityColors(context: Context, severity: ErrorSeverity): ColorSet {
        return when (severity) {
            ErrorSeverity.INFO -> {
                val bgColor = ContextCompat.getColor(context, R.color.info)
                val textColor = getBestTextColor(context, bgColor)
                ColorSet(bgColor, textColor)
            }
            ErrorSeverity.WARNING -> {
                val bgColor = ContextCompat.getColor(context, R.color.warning)
                val textColor = getBestTextColor(context, bgColor)
                ColorSet(bgColor, textColor)
            }
            ErrorSeverity.ERROR -> {
                val bgColor = ContextCompat.getColor(context, R.color.error)
                val textColor = getBestTextColor(context, bgColor)
                ColorSet(bgColor, textColor)
            }
            ErrorSeverity.CRITICAL -> {
                val bgColor = ContextCompat.getColor(context, R.color.critical)
                val textColor = getBestTextColor(context, bgColor)
                ColorSet(bgColor, textColor)
            }
        }
    }

    /**
     * 獲取按鈕顏色配置
     */
    fun getButtonColors(context: Context, enabled: Boolean): ColorSet {
        return if (enabled) {
            val bgColor = ContextCompat.getColor(context, R.color.primary)
            val textColor = ContextCompat.getColor(context, R.color.on_primary)
            ColorSet(bgColor, textColor)
        } else {
            val bgColor = ContextCompat.getColor(context, R.color.disabled_bg)
            val textColor = ContextCompat.getColor(context, R.color.disabled_text)
            ColorSet(bgColor, textColor)
        }
    }

    /**
     * 生成漸層顏色
     */
    @ColorInt
    fun interpolateColor(@ColorInt startColor: Int, @ColorInt endColor: Int, factor: Float): Int {
        val clampedFactor = factor.coerceIn(0f, 1f)
        
        val startA = Color.alpha(startColor)
        val startR = Color.red(startColor)
        val startG = Color.green(startColor)
        val startB = Color.blue(startColor)
        
        val endA = Color.alpha(endColor)
        val endR = Color.red(endColor)
        val endG = Color.green(endColor)
        val endB = Color.blue(endColor)
        
        val a = (startA + (endA - startA) * clampedFactor).toInt()
        val r = (startR + (endR - startR) * clampedFactor).toInt()
        val g = (startG + (endG - startG) * clampedFactor).toInt()
        val b = (startB + (endB - startB) * clampedFactor).toInt()
        
        return Color.argb(a, r, g, b)
    }

    /**
     * 獲取波紋效果顏色
     */
    @ColorInt
    fun getRippleColor(context: Context, @ColorInt baseColor: Int): Int {
        val isDark = isDarkTheme(context)
        val factor = if (isDark) 0.2f else 0.1f
        return adjustAlpha(
            if (isDark) Color.WHITE else Color.BLACK,
            factor
        )
    }

    /**
     * 記錄顏色對比度警告
     */
    fun logError(tag: String, message: String) {
        Log.w(TAG, "[$tag] $message")
    }

    /**
     * 驗證顏色是否足夠亮
     */
    fun isLightColor(@ColorInt color: Int): Boolean {
        return calculateLuminance(color) > 0.5
    }

    /**
     * 獲取顏色的互補色
     */
    @ColorInt
    fun getComplementaryColor(@ColorInt color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + 180) % 360
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    /**
     * 計算兩個顏色的視覺距離
     */
    fun calculateColorDistance(@ColorInt color1: Int, @ColorInt color2: Int): Double {
        val r1 = Color.red(color1).toDouble()
        val g1 = Color.green(color1).toDouble()
        val b1 = Color.blue(color1).toDouble()
        
        val r2 = Color.red(color2).toDouble()
        val g2 = Color.green(color2).toDouble()
        val b2 = Color.blue(color2).toDouble()
        
        return sqrt((r1 - r2).pow(2) + (g1 - g2).pow(2) + (b1 - b2).pow(2))
    }
}

 