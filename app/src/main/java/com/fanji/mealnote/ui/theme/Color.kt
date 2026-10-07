package com.fanji.mealnote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「食单」调色板 —— **纯白底 · 暖白浮层**体系。
 *
 * 页面底色是**纯白**（真机反馈：喜欢纯白背景）。但卡片此前靠「暖米白底 + 纯白浮层」的
 * **色差**浮起来，底一改白，卡片就和底同色了。所以分层手段整体换成三件叠加：
 *
 * 1. 卡片改用**暖白** `#FBF8F4`——在白底上它是「暖的那一块」，方向与照片色温一致；
 * 2. 一圈 1px 描边 `#EFE9E1`（[LightOutlineVariant]）把边缘钉住，避免只剩阴影时边缘发闷；
 * 3. 阴影仍是暖棕偏移（[ShadowAmbient] / [ShadowSpot]）：纯白底上冷灰阴影会立刻显脏，
 *    这一档比暖米白底时更重要而不是更不重要。
 *
 * 其余语义与对比度约定不变：
 *
 * - 主色 `#0A8558`（白字 4.66:1，WCAG AA）；鲜亮档 `#12B377` 仅装饰，不承载文字。
 * - 「待探访」琥珀褐 `#9E570F`（AA 达标），明亮琥珀 `#D9822B` 仅装饰。
 * - 陶土红 [AccentTerracottaLight] **纯装饰**：只进分享卡与年度回顾这类脱离应用状态的图片版面。
 * - 语义色分工不变（AGENTS.md §4 / DESIGN_SYSTEM.md §3）：绿 = 主操作/已用餐/推荐；
 *   琥珀 = 待探访/花费；暖石灰 = 尚可；红 = 不推荐/危险。
 *
 * 文字与容器配色均按**纯白底与暖白卡片两处**验算过正文对比度 ≥ 4.5:1；
 * 标 [Vivid] / [Terracotta] 的装饰色不参与文字对比度，禁止用于小号文字。
 */

// ─────────────────────────────── 浅色 ───────────────────────────────

/** 页面底色。纯白。 */
val LightCanvas = Color(0xFFFFFFFF)

/** 卡片、表单等浮层表面。暖白：在白底上靠色温而不是明度区分，仍读得出「一块纸」。 */
val LightSurface = Color(0xFFFBF8F4)

/** 次级表面：芯片底槽、输入框未聚焦底色、图片占位。 */
val LightSurfaceMuted = Color(0xFFF3EDE5)

/** 更浅一档的分隔/悬停面，用于列表行按下反馈与骨架屏亮部。 */
val LightSurfaceSubtle = Color(0xFFFFFCF8)

val LightTextPrimary = Color(0xFF1D1813)
val LightTextSecondary = Color(0xFF6E6155)

/** 仅用于占位符、装饰性说明与 ≥ 22sp 大字，不用于正文小号文字（对比度不足 AA）。 */
val LightTextTertiary = Color(0xFF8A7C6C)

/**
 * 主色：深翠绿。主操作、选中态、推荐评价。
 *
 * 收深是为了让白色按钮文字达到 4.5:1 —— `#0EA56B` 配白字只有 3.18:1，
 * 在 `labelLarge`(14sp) 上是不达标的。鲜亮档见 [LightPrimaryVivid]。
 */
val LightPrimary = Color(0xFF0A8558)
val LightOnPrimary = Color(0xFFFFFFFF)

/** 主色渐变的暗端：实心按钮自上而下 `primary → primaryDeep`，白字对比度反而更高。 */
val LightPrimaryDeep = Color(0xFF06653F)

/** 主色的鲜亮档：仅装饰用（渐变顶部、图表柱、聚焦光晕），不承载文字。 */
val LightPrimaryVivid = Color(0xFF12B377)
val LightPrimaryContainer = Color(0xFFD5EFE1)
val LightOnPrimaryContainer = Color(0xFF063F2A)

/**
 * 强调色：琥珀褐。「待探访 / 想吃」状态与花费数字。
 *
 * 与绿色主操作形成冷暖对照，同时收深到能安全承载小号文字。
 */
val LightSecondary = Color(0xFF9E570F)
val LightOnSecondary = Color(0xFFFFFFFF)

/** 琥珀的明亮档：仅装饰用（比例条、金额柱、hero 点缀）。 */
val LightSecondaryVivid = Color(0xFFD9822B)
val LightSecondaryContainer = Color(0xFFF8E7CE)
val LightOnSecondaryContainer = Color(0xFF4E2A06)

/** 中性色：暖石灰。「尚可」既不好也不坏，用中性色表达比再引入一种暖色更准确。 */
val LightTertiary = Color(0xFF6B6459)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFE9E1D6)
val LightOnTertiaryContainer = Color(0xFF33291F)

/** 危险色：暖红。「不推荐」与不可逆操作共用；刻意偏砖红，与陶土装饰色区分开。 */
val LightError = Color(0xFFC33A32)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFADFD9)
val LightOnErrorContainer = Color(0xFF67170F)

/**
 * 描边。纯白底上描边**不再是可忽略的装饰**：卡片与底同属白系，边缘主要靠这一档撑住。
 *
 * [LightOutlineVariant] 用于卡片 1px 边线与分隔线；[LightOutline] 用于输入框等有意识描边。
 */
val LightOutline = Color(0xFF8F8478)
val LightOutlineVariant = Color(0xFFEFE9E1)

// ─────────────────────────────── 深色 ───────────────────────────────

/** 深色同样走暖向：纯黑炭 + 暖高光，避免暖色食物照片在冷黑底上像「贴上去的」。 */
val DarkCanvas = Color(0xFF131110)
val DarkSurface = Color(0xFF1D1A17)
val DarkSurfaceMuted = Color(0xFF2A241E)
val DarkSurfaceSubtle = Color(0xFF241F1A)

val DarkTextPrimary = Color(0xFFF4EFE8)
val DarkTextSecondary = Color(0xFFB3A99B)
val DarkTextTertiary = Color(0xFF8B8175)

/** 深色底上主色需要显著提亮，否则 `#0A8558` 会显得发黑。 */
val DarkPrimary = Color(0xFF4FD39A)
val DarkOnPrimary = Color(0xFF00301C)
val DarkPrimaryDeep = Color(0xFF2EB77E)
val DarkPrimaryVivid = Color(0xFF6FE7B4)
val DarkPrimaryContainer = Color(0xFF134A36)
val DarkOnPrimaryContainer = Color(0xFFBDF2D8)

val DarkSecondary = Color(0xFFF0A75A)
val DarkOnSecondary = Color(0xFF3A1C00)
val DarkSecondaryVivid = Color(0xFFFFC078)
val DarkSecondaryContainer = Color(0xFF50300E)
val DarkOnSecondaryContainer = Color(0xFFF8DDB9)

val DarkTertiary = Color(0xFFBDB2A3)
val DarkOnTertiary = Color(0xFF33291F)
val DarkTertiaryContainer = Color(0xFF453E35)
val DarkOnTertiaryContainer = Color(0xFFE9E1D6)

val DarkError = Color(0xFFFF9A8E)
val DarkOnError = Color(0xFF4E0A05)
val DarkErrorContainer = Color(0xFF711D15)
val DarkOnErrorContainer = Color(0xFFFDDBD5)

val DarkOutline = Color(0xFF8B8175)
val DarkOutlineVariant = Color(0xFF3A322A)

// ─────────────────────────── 纯装饰色 ───────────────────────────

/**
 * 陶土红：分享卡片、年度回顾等**脱离应用状态**的独立图片版面专用。
 *
 * 应用内禁止使用：它离危险红太近，一旦进入状态语义就会与「不推荐 / 删除」冲突。
 */
val AccentTerracottaLight = Color(0xFFB4453A)
val AccentTerracottaDark = Color(0xFFE0705F)

// ─────────────────────────── 玻璃拟态专用 ───────────────────────────

/**
 * 玻璃层的着色。
 *
 * 浅色模式下玻璃是「暖白半透明 + 顶部高光」，深色模式下是「暖炭半透明 + 顶部高光」。
 * 高光描边（[GlassHighlight]）刻意使用低透明度白色：它模拟的是玻璃边缘的折射亮线，
 * 透明度超过 0.5 就会看起来像塑料。
 */
val GlassTintLight = Color(0xCCFAF7F2)
val GlassTintLightFade = Color(0x99FAF7F2)
val GlassTintDark = Color(0xCC1D1A17)
val GlassTintDarkFade = Color(0x991D1A17)
val GlassHighlight = Color(0x66FFFFFF)
val GlassHighlightDark = Color(0x1FFFFFFF)

/**
 * 卡片阴影色。
 *
 * 不使用纯黑，也不用上一版的冷蓝灰：冷阴影压在暖米白底上会发脏（上一版卡片「灰扑扑」的
 * 根因）。带暖棕偏移的 `#241A10` 与暖底同色温，扩散出去仍读得出层次。
 */
val ShadowAmbient = Color(0x1A241A10)
val ShadowSpot = Color(0x2D241A10)

/**
 * 主操作按钮的投影色。
 *
 * 使用主色本身而非灰黑：有色按钮会投出同色系的柔光，
 * 这是让按钮「浮起来」而不显脏的关键。
 */
val PrimaryShadowLight = Color(0x590A8558)
val PrimaryShadowDark = Color(0x664FD39A)

/**
 * hero 区的暖光渐变（自上而下）。
 *
 * 清单/足迹页顶部那一张「今天吃什么」卡用它：杏色渐隐到暖白卡片色，
 * 相当于给页面顶部打一束暖光。底端刻意落在 [LightSurface] 而不是纯白——
 * 卡片坐在白底上，渐变的终点必须是卡片自己的颜色，否则下边缘会「化」进背景里。
 */
val HeroGradientLight = listOf(Color(0xFFFBEAD3), Color(0xFFFBF8F4))
val HeroGradientDark = listOf(Color(0xFF2C2318), Color(0xFF1D1A17))
