package com.fanji.mealnote.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 交互动效工具。
 *
 * 本项目所有**过渡型**动效统一使用弹簧而非线性/缓动曲线：弹簧的加速度由物理模型决定，
 * 中断（用户快速连点、列表突然插入）时过渡自然，不会出现缓动曲线被打断后的突兀停顿。
 *
 * 唯一例外是**循环型**动画（骨架屏扫光）：它没有终点，弹簧模型不适用，且任何缓动都会在
 * 循环接缝处看出「呼吸感」，因此刻意用线性。
 */

/**
 * 全项目共用的弹簧规格。
 *
 * 抽出来的原因：散落在各组件里的 `spring(...)` 参数会随时间漂移成五六种不同的手感，
 * 「同一屏里两个控件不像同一个应用」正是这样来的。新增动效请从这里取。
 */
object MealMotion {
    /** 落定型：位移、尺寸、数字过渡的默认档。无过冲，收尾干净。 */
    fun <T> settle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 反馈型：按压缩放。回弹时略微过冲，是「有弹性」观感的来源。 */
    fun bouncy() = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** 强调型：图标弹跳、选中标记出现。过冲更明显，用于「值得多看一眼」的变化。 */
    fun pop() = spring<Float>(
        dampingRatio = Spring.DampingRatioHighBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** 快速型：颜色/透明度等小变化，必须比位移更快落定，否则显得迟钝。 */
    fun <T> quick() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
}

/**
 * 按压缩放反馈。
 *
 * 传入 [MutableInteractionSource] 后，组件被按下时整体缩到 [pressedScale]，
 * 松手时弹回。相比水波纹，缩放反馈在**没有明确点击区域的卡片**上更容易被感知。
 *
 * 必须把同一个 [interactionSource] 交给触发点击的 `clickable` / `Surface`，
 * 否则本 Modifier 收不到按压事件，不会有任何效果。
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        // 压缩过程本身很快（StiffnessMedium），不会让点击显得迟钝。
        animationSpec = MealMotion.bouncy(),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 归一化按压进度（0→1），供组件把「按下」做成连续效果而非二值切换。
 *
 * 清单卡片用它：卡片整体微缩的同时封面反而轻微放大 —— 反馈落在**图片**上，
 * 观感像照片被按进卡片里，而不是整块纸片在抖。
 */
@Composable
fun rememberPressProgress(interactionSource: MutableInteractionSource): () -> Float {
    val pressed by interactionSource.collectIsPressedAsState()
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = MealMotion.bouncy(),
        label = "pressProgress",
    )
    return { progress }
}

/**
 * 数字滚动动画。
 *
 * 用于首页统计、详情页用餐次数等场景：数值变化时逐帧过渡，而不是瞬间跳变。
 * 动画值取整后再渲染，避免出现「12.7 次」这种不合理的中间态。
 */
@Composable
fun AnimatedCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = MealMotion.settle(),
        label = "counter",
    )
    Text(
        text = animated.toString(),
        modifier = modifier,
        style = style,
        color = color,
    )
}

/**
 * 列表项入场：从下方淡入。
 *
 * 配合 `Modifier.animateItem()` 使用：`animateItem` 只处理**已有条目**的位置变化，
 * 新插入的条目需要一个显式的入场动画，否则会「凭空出现」。
 *
 * @param index 在列表中的序号，用于做阶梯延迟（越靠后的条目越晚进场）。
 * @param enabled 流畅模式关闭时传 false，直接返回原 Modifier，不做任何动画。
 *   低端机上每个条目的延迟入场 + 位移动画是卡顿的主要来源之一。
 */
@Composable
fun Modifier.staggeredEnter(index: Int, enabled: Boolean = true): Modifier {
    if (!enabled) return this
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(MAX_STAGGER_INDEX) * STAGGER_STEP_MS).toLong())
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MealMotion.settle(),
        label = "staggerEnter",
    )
    val offsetPx = with(LocalDensity.current) { STAGGER_OFFSET.toPx() }
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * offsetPx
    }
}

/** 阶梯延迟最多作用到第 5 项,再往后的条目统一进场,避免长列表末尾等待过久,整体入场更克制。 */
private const val MAX_STAGGER_INDEX = 5
private const val STAGGER_STEP_MS = 18

/** 入场时的初始下移量(刻意较小,减少屏幕上的位移噪声)。 */
private val STAGGER_OFFSET = 10.dp

/**
 * 骨架屏扫光。
 *
 * 加载态用转圈的问题：转圈只说明「在等」，不说明「在等什么」，而且页面高度会在
 * 加载中/有内容之间跳变。骨架屏直接画出卡片应有的轮廓，等待变成「内容正在显影」。
 *
 * 扫光是**循环型**动画（没有终点），因此这里用线性往复而非弹簧。
 *
 * @param enabled 流畅模式关闭时传 false：只画静态占位块，不做每帧渐变重绘。
 *   扫光是持续的绘制成本，低端机上关闭流畅模式时必须一起关掉。
 */
@Composable
fun Modifier.skeletonShimmer(enabled: Boolean = true): Modifier {
    if (!enabled) return this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val peak = MaterialTheme.colorScheme.surface
    return this.drawWithContent {
        drawRect(base)
        // 扫光带比自身宽一倍，保证带子两端都走出可视区，避免「到边急停」的闪烁。
        val startX = -size.width + progress * size.width * 2f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(base, peak, base),
                startX = startX,
                endX = startX + size.width,
            ),
        )
        drawContent()
    }
}

private const val SHIMMER_PERIOD_MS = 1_100

/**
 * 图片显影：解码完成后淡入 + 轻微收拢，并可叠加一层按压缩放。
 *
 * Coil 解码本地照片通常是几十毫秒级，但列表滚动时会有明显的「先空后跳」。
 * 淡入把这次跳变藏进一次短弹簧；初始 1.04 的收拢让图片像「落进」容器而不是贴上来。
 *
 * @param zoom 额外的按压缩放量（0~1）：清单卡片按下时封面反而轻微放大，
 *   反馈落在图片上。与显影合并在同一个 `graphicsLayer` 里 —— 叠两层会各自建一个图层，
 *   在长列表上是白给的绘制开销。
 */
@Composable
fun Modifier.imageReveal(
    revealed: Boolean,
    zoom: Float = 0f,
    enabled: Boolean = true,
): Modifier {
    val revealProgress by animateFloatAsState(
        targetValue = if (revealed || !enabled) 1f else 0f,
        animationSpec = MealMotion.settle(),
        label = "imageReveal",
    )
    val zoomed = 1f + zoom * ZOOM_AT_FULL_PRESS
    return this.graphicsLayer {
        alpha = revealProgress
        val scale = (1.04f - 0.04f * revealProgress) * zoomed
        scaleX = scale
        scaleY = scale
        // 收拢时保持中心对齐，否则会往左上角缩。
        translationX = -size.width * (scale - 1f) / 2f
        translationY = -size.height * (scale - 1f) / 2f
    }
}

private const val ZOOM_AT_FULL_PRESS = 0.055f

/**
 * 选择类操作的触觉反馈。
 *
 * 只用于「选中/切换/触发主操作」这类**有结果**的手势，不用于滚动：
 * 每次都震会让用户分不清哪次震动对应哪次生效，反馈反而失效。
 */
@Composable
fun rememberSelectionHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) {
        { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    }
}
