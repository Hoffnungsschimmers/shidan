package com.fanji.mealnote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceMuted,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurfaceSubtle,
    surfaceContainer = LightSurfaceMuted,
    surfaceContainerHigh = LightSurfaceMuted,
    surfaceContainerHighest = LightSurfaceMuted,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    inverseSurface = Color(0xFF2A2E33),
    inverseOnSurface = Color(0xFFF2F3F5),
    inversePrimary = Color(0xFF4FD39A),
    scrim = Color(0x99000000),
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceMuted,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainerLowest = DarkCanvas,
    surfaceContainerLow = DarkSurfaceSubtle,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceMuted,
    surfaceContainerHighest = DarkSurfaceMuted,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    inverseSurface = Color(0xFFE9EBEE),
    inverseOnSurface = Color(0xFF1A1C1F),
    inversePrimary = Color(0xFF04341F),
    scrim = Color(0xCC000000),
)

/**
 * 形状阶梯。
 *
 * MIUIx 的圆角明显大于 Material 默认值，且**相邻档位差距更大**，让「大卡片 / 小控件」
 * 在视觉上立刻区分开。对照上一版（6/10/14/18/24）：本次整体上移并拉开间距。
 *
 * 使用建议：
 * - 页面级大卡片、底部面板 → [Shapes.extraLarge]（30dp）
 * - 内容卡片、图片 → [Shapes.large]（24dp）
 * - 按钮、输入框、分段控件 → [Shapes.medium]（18dp）
 * - 标签、徽章 → [Shapes.small]（14dp）
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

/**
 * Material 3 色彩方案之外的补充设计令牌。
 *
 * 这些值无法塞进 [androidx.compose.material3.ColorScheme]（阴影色、玻璃着色没有对应槽位），
 * 但必须随主题一起切换深浅色，因此用 [staticCompositionLocalOf] 下发。
 * 用 static 而非普通 CompositionLocal：主题切换是低频事件，静态版本读取时不做快照追踪，
 * 在列表滚动等高频重组路径上开销更低。
 */
data class MealTokens(
    /** 环境光阴影色（小、柔）。 */
    val shadowAmbient: Color,
    /** 直射阴影色（大、略深）。 */
    val shadowSpot: Color,
    /** 主操作按钮的同色系投影。 */
    val primaryShadow: Color,
    /** 玻璃层主体着色。 */
    val glassTint: Color,
    /** 玻璃层渐隐端着色（底部更透明，形成自上而下的「厚度」感）。 */
    val glassTintFade: Color,
    /** 玻璃边缘高光描边。 */
    val glassHighlight: Color,
    /** 是否深色主题。 */
    val isDark: Boolean,
)

val LocalMealTokens = staticCompositionLocalOf {
    MealTokens(
        shadowAmbient = ShadowAmbient,
        shadowSpot = ShadowSpot,
        primaryShadow = PrimaryShadowLight,
        glassTint = GlassTintLight,
        glassTintFade = GlassTintLightFade,
        glassHighlight = GlassHighlight,
        isDark = false,
    )
}

/** 读取当前主题的补充令牌。 */
val MaterialTheme.mealTokens: MealTokens
    @Composable get() = LocalMealTokens.current

/**
 * 柔和投影。
 *
 * 相比直接调用 [Modifier.shadow]，这里固定了两点：
 * 1. `clip = false` —— 阴影要画在形状之外，裁剪掉就没有柔和扩散效果了；
 * 2. 阴影色取自主题令牌而非纯黑 —— 纯黑在冷灰底上会发脏。
 *
 * 调用约定：`Modifier.softShadow(...).background(color, shape)`，
 * 先投影再铺底色，否则底色会把阴影盖住。
 */
fun Modifier.softShadow(
    shape: Shape,
    elevation: Dp,
    ambient: Color = ShadowAmbient,
    spot: Color = ShadowSpot,
): Modifier = this.shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = ambient,
    spotColor = spot,
)

/**
 * 应用主题。
 *
 * [darkTheme] 默认跟随系统；本项目暂未提供应用内手动切换（属于 V0.3 范围），
 * 但参数保留，便于后续接入。
 */
@Composable
fun MealNoteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tokens = if (darkTheme) {
        MealTokens(
            shadowAmbient = Color(0x33000000),
            shadowSpot = Color(0x4D000000),
            primaryShadow = PrimaryShadowDark,
            glassTint = GlassTintDark,
            glassTintFade = GlassTintDarkFade,
            glassHighlight = GlassHighlightDark,
            isDark = true,
        )
    } else {
        MealTokens(
            shadowAmbient = ShadowAmbient,
            shadowSpot = ShadowSpot,
            primaryShadow = PrimaryShadowLight,
            glassTint = GlassTintLight,
            glassTintFade = GlassTintLightFade,
            glassHighlight = GlassHighlight,
            isDark = false,
        )
    }

    CompositionLocalProvider(LocalMealTokens provides tokens) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = MealNoteTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
