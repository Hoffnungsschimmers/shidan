package com.fanji.mealnote.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 流畅模式的组合本地值。
 *
 * true = 完整动效与实时模糊；false = 流畅优先。
 * 由 [MealNoteApp] 顶层统一提供，各页面经 [isFluidMotion] 读取后传给
 * `staggeredEnter(enabled)` / `GlassSurface(fluid)` /
 * `glassBackdropSource(enabled)`。默认 true，保证预览与旧快照行为不变。
 */
val LocalFluidMotion = staticCompositionLocalOf { true }

/** 当前是否处于流畅模式（完整动效）。 */
val isFluidMotion: Boolean
    @Composable
    @ReadOnlyComposable
    get() = LocalFluidMotion.current
