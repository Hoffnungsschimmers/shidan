package com.fanji.mealnote.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「食单」调色板 —— **暖食欲**体系。
 *
 * 与上一版（中性冷灰底 + 青翠绿）的关键差异：
 *
 * 1. 底色从冷灰 `#F3F4F6` 换成暖米白 `#F6F1EA`：照片多为暖调食物，冷灰底会让图片显得
 *    与界面「两层皮」；暖底把封面拉回同一色温，图片自然成为画面主角。
 * 2. 主色从「鲜亮但压不住白字」的 `#0EA56B`（白字仅 3.18:1）收深为 `#0A8558`：白字 4.66:1（WCAG AA），
 *    实心按钮与徽章文字不再靠字号侥幸过关。鲜亮的 `#12B377` 降级为**装饰色**（图表柱、
 *    渐变高光、聚焦光晕），只在不承担文字对比度要求的位置出现。
 * 3. 「待探访」的暖橙收深为琥珀褐 `#9E570F`（同样 AA 达标），明亮琥珀 `#D9822B` 作装饰档。
 * 4. 新增**纯装饰色**陶土红 [AccentTerracottaLight]：只用于分享卡片、年度回顾这类脱离应用
 *    状态的独立图片版面。**禁止**用作任何状态语义 —— 红=不推荐/危险的分工不容第二个红。
 * 5. 阴影色从冷蓝灰 `#202A3A` 换成暖棕 `#241A10`：冷阴影压在暖底上会发脏，这是上一版
 *    卡片「看起来灰扑扑」的直接原因。
 *
 * 语义色分工不变（AGENTS.md §4 / DESIGN_SYSTEM.md §3）：
 * 绿 = 主操作 / 已用餐 / 推荐；琥珀 = 待探访 / 花费；暖石灰 = 尚可；红 = 不推荐 / 危险。
 *
 * 文字与容器配色均按浅色底验算过正文对比度 ≥ 4.5:1；标 [Vivid] / [Terracotta] 的装饰色
 * 不参与文字对比度，禁止用于小号文字。
 */

// ─────────────────────────────── 浅色 ───────────────────────────────

/** 页面底色。暖米白：让食物照片与界面处在同一色温里。 */
val LightCanvas = Color(0xFFF6F1EA)

/** 卡片、表单等浮层表面。纯白在暖米白底上自然浮起。 */
val LightSurface = Color(0xFFFFFFFF)

/** 次级表面：芯片底槽、输入框未聚焦底色、图片占位。 */
val LightSurfaceMuted = Color(0xFFF0E7DB)

/** 更浅一档的分隔/悬停面，用于列表行按下反馈与骨架屏亮部。 */
val LightSurfaceSubtle = Color(0xFFFBF7F1)

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

/** 描边弱化到近乎不可见：分层交给阴影。 */
val LightOutline = Color(0xFF8F8478)
val LightOutlineVariant = Color(0xFFE7DED2)

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
 * 清单/足迹页顶部那一张「今天吃什么」卡用它：杏色渐隐到表面色，
 * 相当于给页面顶部打一束暖光，而不是让大标题孤立地坐在灰底上。
 */
val HeroGradientLight = listOf(Color(0xFFFBEAD3), Color(0xFFFFFDF9))
val HeroGradientDark = listOf(Color(0xFF2C2318), Color(0xFF1D1A17))
