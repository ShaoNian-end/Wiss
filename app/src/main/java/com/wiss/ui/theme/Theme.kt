package com.wiss.ui.theme

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/** 默认固定主色：品牌色 #111520 */
private val DefaultFixedColor = Color(0xFF111520)

/**
 * 主题设置：主题色（固定自定义主色 / 动态配色）与深色模式（跟随系统 / 开启 / 关闭）。
 * 选择与固定主色持久化到本地；dynamic / fixedColor / darkMode 为全局状态，切换后所有读取它的界面即时重绘。
 */
object ThemeSettings {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_THEME = "theme_color"
    private const val KEY_FIXED_COLOR = "theme_fixed_color"
    private const val KEY_DARK_MODE = "dark_mode"
    const val MODE_FIXED = "fixed"
    const val MODE_DYNAMIC = "dynamic"
    const val MODE_DARK_SYSTEM = "system"
    const val MODE_DARK_ON = "on"
    const val MODE_DARK_OFF = "off"

    /** 是否启用动态配色（全局状态） */
    var dynamic by mutableStateOf(false)
        private set

    /** 固定模式下的主色（全局状态） */
    var fixedColor by mutableStateOf(DefaultFixedColor)
        private set

    /** 深色模式：跟随系统 / 开启 / 关闭（全局状态） */
    var darkMode by mutableStateOf(MODE_DARK_SYSTEM)
        private set

    /** 应用启动时读取持久化设置 */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        dynamic = prefs.getString(KEY_THEME, null) == MODE_DYNAMIC
        fixedColor = prefs.getString(KEY_FIXED_COLOR, null)
            ?.let { runCatching { Color(it.trimStart('#').toLong(16)) }.getOrNull() }
            ?: DefaultFixedColor
        darkMode = prefs.getString(KEY_DARK_MODE, null)
            ?.takeIf { it in setOf(MODE_DARK_SYSTEM, MODE_DARK_ON, MODE_DARK_OFF) }
            ?: MODE_DARK_SYSTEM
    }

    /** 切换动态配色并持久化 */
    fun setDynamic(context: Context, value: Boolean) {
        dynamic = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, if (value) MODE_DYNAMIC else MODE_FIXED).apply()
    }

    /** 设置固定主色并持久化 */
    fun setFixedColor(context: Context, color: Color) {
        fixedColor = color
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FIXED_COLOR, "#%08X".format(color.toArgb())).apply()
    }

    /** 设置深色模式（跟随系统 / 开启 / 关闭）并持久化 */
    fun setDarkMode(context: Context, value: String) {
        darkMode = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DARK_MODE, value).apply()
    }
}

/** 浅色主色的浅色调（用于浅色模式背景/surface，即「背景使用浅色主题色」） */
private fun lightTint(primary: Color): Color = lerp(Color.White, primary, 0.06f)

/** 固定配色方案（浅色）：以指定颜色为主色，背景用主色的浅色调，前景按明度自动选黑/白保证对比度 */
fun fixedColorScheme(primary: Color): ColorScheme {
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
    val background = lightTint(primary)
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary,
        onPrimaryContainer = onPrimary,
        secondaryContainer = primary,
        onSecondaryContainer = onPrimary,
        background = background,
        surface = background,
    )
}

/** 固定配色方案（深色）：以指定颜色为主色，其余沿用深色默认值 */
fun fixedDarkColorScheme(primary: Color): ColorScheme {
    val onPrimary = if (primary.luminance() > 0.5f) Color.Black else Color.White
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary,
        onPrimaryContainer = onPrimary,
        secondaryContainer = primary,
        onSecondaryContainer = onPrimary,
    )
}

/** 应用主题：按「主题色」与「深色模式」设置选择配色方案 */
@Composable
fun WissTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = when (ThemeSettings.darkMode) {
        ThemeSettings.MODE_DARK_ON -> true
        ThemeSettings.MODE_DARK_OFF -> false
        else -> isSystemInDarkTheme()
    }
    val colorScheme = if (ThemeSettings.dynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) fixedDarkColorScheme(ThemeSettings.fixedColor) else fixedColorScheme(ThemeSettings.fixedColor)
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * 简易颜色选择器：饱和度/明度方块 + 色相条，支持点击与拖动选色。
 * @param color 初始颜色（仅在首次进入时取值）
 * @param onColorChange 颜色变化回调
 */
@Composable
fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit
) {
    // 以传入颜色初始化 HSV（仅首次进入时取值，拖动过程保持本地状态，避免黑色处色相被重置）
    val initial = remember {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hsv
    }
    var hue by remember { mutableStateOf(initial[0]) }
    var sat by remember { mutableStateOf(initial[1]) }
    var value by remember { mutableStateOf(initial[2]) }

    // 方块内点击/拖动 → 更新饱和度与明度（横向为饱和度，纵向为明度）
    fun pickSatValue(offset: Offset, boxSize: IntSize) {
        sat = (offset.x / boxSize.width).coerceIn(0f, 1f)
        value = 1f - (offset.y / boxSize.height).coerceIn(0f, 1f)
        onColorChange(Color.hsv(hue, sat, value))
    }

    // 色相条点击/拖动 → 更新色相（0..360）
    fun pickHue(offset: Offset, barWidth: Int) {
        hue = (offset.x / barWidth * 360f).coerceIn(0f, 360f)
        onColorChange(Color.hsv(hue, sat, value))
    }

    val current = Color.hsv(hue, sat, value)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // ---- 饱和度/明度方块 ----
        Box(
            modifier = Modifier
                .size(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectTapGestures { pickSatValue(it, size) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        pickSatValue(change.position, size)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 横向：白 → 纯色；纵向：透明 → 黑
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            }
            // 当前位置指示器
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = (sat * 168f).dp, y = ((1f - value) * 168f).dp)
                    .size(12.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .background(current, CircleShape)
            )
        }
        Spacer(Modifier.height(12.dp))
        // ---- 色相条 ----
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .pointerInput(Unit) {
                    detectTapGestures { pickHue(it, size.width) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        pickHue(change.position, size.width)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
                    )
                )
            }
            // 色相指示器
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (maxWidth - 12.dp) * (hue / 360f))
                    .size(12.dp)
                    .border(2.dp, Color.White, CircleShape)
                    .background(Color.hsv(hue, 1f, 1f), CircleShape)
            )
        }
        Spacer(Modifier.height(10.dp))
        // ---- 预览 + 十六进制 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(current)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "#%08X".format(current.toArgb()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
