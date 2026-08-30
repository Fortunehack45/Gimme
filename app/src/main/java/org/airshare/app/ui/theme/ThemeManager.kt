package org.airshare.app.ui.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.airshare.app.AirShareApplication

enum class ThemePreset(
    val id: String,
    val title: String,
    val hexColor: String,
    val hexDarkColor: String
) {
    RED("RED", "Apple Crimson Red", "#FF3B30", "#FF453A"),
    BLUE("BLUE", "Electric Blue", "#007AFF", "#0A84FF"),
    PURPLE("PURPLE", "Deep Purple", "#AF52DE", "#BF5AF2"),
    GREEN("GREEN", "Emerald Green", "#34C759", "#30D158"),
    ORANGE("ORANGE", "Sunset Orange", "#FF9500", "#FF9F0A"),
    GOLD("GOLD", "Cyber Gold", "#FFCC00", "#FFD60A"),
    MONOCHROME("MONOCHROME", "Minimal Graphite", "#8E8E93", "#98989D")
}

object ThemeManager {

    private val _activePresetFlow = MutableStateFlow(ThemePreset.RED)
    val activePresetFlow: StateFlow<ThemePreset> = _activePresetFlow.asStateFlow()

    fun init(savedPresetId: String?) {
        val preset = ThemePreset.values().find { it.id.equals(savedPresetId, ignoreCase = true) } ?: ThemePreset.RED
        _activePresetFlow.value = preset
    }

    fun setPreset(preset: ThemePreset) {
        _activePresetFlow.value = preset
        AirShareApplication.instance.settingsRepository.themePresetId = preset.id
    }

    fun getActiveColorInt(context: Context): Int {
        val isDark = isDarkMode(context)
        val hex = if (isDark) _activePresetFlow.value.hexDarkColor else _activePresetFlow.value.hexColor
        return Color.parseColor(hex)
    }

    fun getSubtleColorInt(context: Context, alphaPercent: Int = 18): Int {
        val color = getActiveColorInt(context)
        val alpha = (alphaPercent * 255 / 100).coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    fun applyThemeToPrimaryButton(button: Button) {
        val color = getActiveColorInt(button.context)
        button.backgroundTintList = ColorStateList.valueOf(color)
    }

    fun applyThemeToTextView(textView: TextView) {
        val color = getActiveColorInt(textView.context)
        textView.setTextColor(color)
    }

    fun applyThemeToImageView(imageView: ImageView) {
        val color = getActiveColorInt(imageView.context)
        imageView.imageTintList = ColorStateList.valueOf(color)
    }

    fun applyThemeToProgress(progress: LinearProgressIndicator) {
        val color = getActiveColorInt(progress.context)
        progress.setIndicatorColor(color)
    }

    fun applySubtlePillBackground(view: View, cornerRadiusDp: Float = 100f) {
        val context = view.context
        val density = context.resources.displayMetrics.density
        val radiusPx = cornerRadiusDp * density
        val strokeWidthPx = (1 * density).toInt()

        val fillColor = getSubtleColorInt(context, 16)
        val borderColor = getSubtleColorInt(context, 35)

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(fillColor)
            setStroke(strokeWidthPx, borderColor)
        }
        view.background = drawable
    }

    fun isDarkMode(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
