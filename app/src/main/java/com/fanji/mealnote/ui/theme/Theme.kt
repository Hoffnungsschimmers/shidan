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
    inverseSurface = Color(0xFF2E2620),
    inverseOnSurface = Color(0xFFF4EFE8),
    inversePrimary = DarkPrimary,
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
    inverseSurface = Color(0xFFEDE6DC),
    inverseOnSurface = DarkSurface,
    inversePrimary = LightOnPrimaryContainer,
    scrim = Color(0xCC000000),
)

/**
 * 形状阶梯。
 *
 * 相邻档位差距刻意拉开，让「大卡片 / 小控件」在视觉上立刻区分开。
 * 「暖食欲」这一版整体再上调一档（14→16、18→20、24→26、30→32）：
 * 封面图成为卡片主角后，小圆角会让大图的弧度看起来「没包住」，
 * 而图片与容器的圆角比需要保持同一语言，否则图为主版式会显得生硬。
 *
 * 使用建议：
 * - 页面级大卡片、底部面板 → [Shapes.extraLarge]（32dp）
 * - 内容卡片、封面图 → [Shapes.large]（26dp）
 * - 按钮、输入框、芯片 → [Shapes.medium]（20dp）
 * - 徽章、缩略图 → [Shapes.small]（16dp）
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Material 3 色彩方案之外的补充设计令牌。
 *
 * 这些值无法塞进 [androidx.compose.material3.ColorScheme]（阴影色、玻璃着色、装饰色
 * 没有对应槽位），但必须随主题一起切换深浅色，因此用 [staticCompositionLocalOf] 下发。
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
    /**
     * 主色鲜亮档：**仅装饰**。渐变顶部、图表柱、聚焦光晕。
     *
     * 不承载文字 —— 它配白字达不到 AA，这是 [LightPrimary] 被收深的原因。
     */
    val primaryVivid: Color,
    /** 实心按钮渐变的暗端（比 [LightPrimary] 更深，白字对比度更高）。 */
    val primaryDeep: Color,
    /** 琥珀明亮档：仅装饰（比例条、金额柱、hero 点缀）。 */
    val secondaryVivid: Color,
    /**
     * 陶土红：分享卡片 / 年度回顾等脱离应用状态的独立图片版面专用。
     *
     * 应用内禁止用于任何状态表达，避免与「不推荐 / 删除」的红混淆。
     */
    val accentTerracotta: Color,
    /** hero 卡的暖光渐变（自上而下两档）。 */
    val heroGradient: List<Color>,
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
        primaryVivid = LightPrimaryVivid,
        primaryDeep = LightPrimaryDeep,
        secondaryVivid = LightSecondaryVivid,
        accentTerracotta = AccentTerracottaLight,
        heroGradient = HeroGradientLight,
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
 * 2. 阴影色取自主题令牌而非纯黑 —— 纯黑压在暖米白底上会发脏，暖棕阴影才与照片同色温。
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
 * [darkTheme] 由调用方决定：设置页提供「跟随系统 / 浅色 / 深色」三档
 * （见 `ThemePreference` 与 `MainActivity` 的 `resolveDark()`），此处默认回退跟随系统。
 */
@Composable
fun MealNoteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val tokens = if (darkTheme) {
        MealTokens(
            shadowAmbient = Color(0x40000000),
            shadowSpot = Color(0x66000000),
            primaryShadow = PrimaryShadowDark,
            glassTint = GlassTintDark,
            glassTintFade = GlassTintDarkFade,
            glassHighlight = GlassHighlightDark,
            primaryVivid = DarkPrimaryVivid,
            primaryDeep = DarkPrimaryDeep,
            secondaryVivid = DarkSecondaryVivid,
            accentTerracotta = AccentTerracottaDark,
            heroGradient = HeroGradientDark,
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
            primaryVivid = LightPrimaryVivid,
            primaryDeep = LightPrimaryDeep,
            secondaryVivid = LightSecondaryVivid,
            accentTerracotta = AccentTerracottaLight,
            heroGradient = HeroGradientLight,
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
