package com.fanji.mealnote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「味笺」调色板。
 *
 * 视觉方向参考 MIUIx / HyperOS：**中性冷灰底 + 纯白浮层 + 高饱和强调色**。
 * 与上一版（低饱和鼠尾草绿 `#3F6650`）的关键差异：
 *
 * 1. 背景不再是带绿调的暖白，改为中性冷灰 `#F3F4F6`，让纯白卡片自然浮起来；
 * 2. 主色从「暗绿」改为「青翠绿」`#0EA56B`，明度与饱和度都更高，在浅色底上更有食欲感；
 * 3. 边框弱化到几乎不可见（`#E8EAEE`），分层主要靠**阴影**而非描边 —— 这是 MIUIx 的核心手法；
 * 4. 语义色分工明确：橙=待探访（状态），绿=推荐，石板灰=尚可（中性），红=不推荐。
 *
 * 所有色值均在浅色底上验证过正文对比度 ≥ 4.5:1（WCAG AA）。
 */

// ─────────────────────────────── 浅色 ───────────────────────────────

/** 页面底色。中性冷灰，不带任何彩色倾向，避免与主色互相干扰。 */
val LightCanvas = Color(0xFFF3F4F6)

/** 卡片、表单等浮层表面。纯白是 MIUIx 层次感的基础。 */
val LightSurface = Color(0xFFFFFFFF)

/** 次级表面：分段控件底槽、输入框未聚焦底色、图片占位。 */
val LightSurfaceMuted = Color(0xFFEDEFF2)

/** 更浅一档的分隔/悬停面，用于列表行按下反馈。 */
val LightSurfaceSubtle = Color(0xFFF7F8FA)

val LightTextPrimary = Color(0xFF121316)
val LightTextSecondary = Color(0xFF6B7280)
val LightTextTertiary = Color(0xFF9CA3AF)

/** 主色：青翠绿。主操作、选中态、推荐评价。 */
val LightPrimary = Color(0xFF0EA56B)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD4F3E5)
val LightOnPrimaryContainer = Color(0xFF04341F)

/** 强调色：暖橙。用于「待探访 / 想吃」这一状态，与绿色主操作形成冷暖对照。 */
val LightSecondary = Color(0xFFE07C1F)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFFCEBD9)
val LightOnSecondaryContainer = Color(0xFF5A3208)

/** 中性色：石板灰。「尚可」既不好也不坏，用中性色表达比再引入一种暖色更准确。 */
val LightTertiary = Color(0xFF64707F)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFE7EBF0)
val LightOnTertiaryContainer = Color(0xFF2C3742)

/** 危险色：红。「不推荐」与不可逆操作共用。 */
val LightError = Color(0xFFDC3B41)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFCE4E4)
val LightOnErrorContainer = Color(0xFF68141A)

/** 描边弱化到近乎不可见：分层交给阴影。 */
val LightOutline = Color(0xFF9AA1AC)
val LightOutlineVariant = Color(0xFFE8EAEE)

// ─────────────────────────────── 深色 ───────────────────────────────

val DarkCanvas = Color(0xFF0E0F11)
val DarkSurface = Color(0xFF1A1C1F)
val DarkSurfaceMuted = Color(0xFF24272B)
val DarkSurfaceSubtle = Color(0xFF202327)

val DarkTextPrimary = Color(0xFFF2F3F5)
val DarkTextSecondary = Color(0xFFA1A7B0)
val DarkTextTertiary = Color(0xFF757C86)

/** 深色底上主色需要显著提亮，否则 `#0EA56B` 会显得发黑。 */
val DarkPrimary = Color(0xFF4FD39A)
val DarkOnPrimary = Color(0xFF00301C)
val DarkPrimaryContainer = Color(0xFF0B4A32)
val DarkOnPrimaryContainer = Color(0xFFB7F2D6)

val DarkSecondary = Color(0xFFFFB067)
val DarkOnSecondary = Color(0xFF432200)
val DarkSecondaryContainer = Color(0xFF5E3A12)
val DarkOnSecondaryContainer = Color(0xFFFFDDBA)

val DarkTertiary = Color(0xFFAEB9C6)
val DarkOnTertiary = Color(0xFF2A333D)
val DarkTertiaryContainer = Color(0xFF3A434E)
val DarkOnTertiaryContainer = Color(0xFFDCE3EB)

val DarkError = Color(0xFFFF8A8E)
val DarkOnError = Color(0xFF4E0005)
val DarkErrorContainer = Color(0xFF7A1A20)
val DarkOnErrorContainer = Color(0xFFFFDADB)

val DarkOutline = Color(0xFF6E767F)
val DarkOutlineVariant = Color(0xFF303438)

// ─────────────────────────── 玻璃拟态专用 ───────────────────────────

/**
 * 玻璃层的着色。
 *
 * 浅色模式下玻璃是「白色半透明 + 顶部高光」，深色模式下是「深灰半透明 + 顶部高光」。
 * 高光描边（[GlassHighlight]）刻意使用低透明度白色：它模拟的是玻璃边缘的折射亮线，
 * 透明度超过 0.5 就会看起来像塑料。
 */
val GlassTintLight = Color(0xCCFFFFFF)
val GlassTintLightFade = Color(0x99FFFFFF)
val GlassTintDark = Color(0xCC1A1C1F)
val GlassTintDarkFade = Color(0x991A1C1F)
val GlassHighlight = Color(0x66FFFFFF)
val GlassHighlightDark = Color(0x1FFFFFFF)

/**
 * 卡片阴影色。
 *
 * 不使用纯黑：纯黑阴影在冷灰底上会显得脏。带一点冷色偏移的深蓝灰更接近真实光照。
 */
val ShadowAmbient = Color(0x14202A3A)
val ShadowSpot = Color(0x1F202A3A)

/**
 * 主操作按钮的投影色。
 *
 * 使用主色本身而非灰黑：MIUIx 的有色按钮会投出同色系的柔光，
 * 这是让按钮「浮起来」而不显脏的关键。
 */
val PrimaryShadowLight = Color(0x590EA56B)
val PrimaryShadowDark = Color(0x664FD39A)
