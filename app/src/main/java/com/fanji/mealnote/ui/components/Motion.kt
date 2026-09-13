package com.fanji.mealnote.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * 交互动效工具。
 *
 * 本项目所有动效统一使用**弹簧**而非线性/缓动曲线：弹簧的加速度由物理模型决定，
 * 中断（用户快速连点、列表突然插入）时过渡自然，不会出现缓动曲线被打断后的突兀停顿。
 * 这也是 MIUIx 动效的基本特征。
 */

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
        // MediumBouncy：回弹时略微过冲，是「有弹性」观感的来源；
        // 压缩过程本身很快（StiffnessMedium），不会让点击显得迟钝。
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
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
 */
@Composable
fun Modifier.staggeredEnter(index: Int): Modifier {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtMost(MAX_STAGGER_INDEX) * STAGGER_STEP_MS).toLong())
        appeared = true
    }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "staggerEnter",
    )
    val offsetPx = with(LocalDensity.current) { STAGGER_OFFSET.toPx() }
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * offsetPx
    }
}

/** 阶梯延迟最多作用到第 8 项，再往后的条目统一进场，避免长列表末尾等待过久。 */
private const val MAX_STAGGER_INDEX = 8
private const val STAGGER_STEP_MS = 28

/** 入场时的初始下移量。 */
private val STAGGER_OFFSET = 18.dp
