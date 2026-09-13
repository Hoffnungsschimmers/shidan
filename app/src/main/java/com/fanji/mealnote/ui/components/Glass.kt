package com.fanji.mealnote.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.fanji.mealnote.ui.theme.mealTokens

/**
 * 玻璃（Liquid Glass）效果的共享状态。
 *
 * ## 为什么需要它
 *
 * Compose 的 `Modifier.blur()` 模糊的是**自己**，而「毛玻璃」要模糊的是**身后**的内容。
 * Android 从 API 31 起才提供 `RenderEffect`，因此实现真背景模糊的唯一途径是：
 *
 * 1. 把滚动内容录制进一个 [GraphicsLayer]（见 [glassBackdropSource]）；
 * 2. 给该 layer 挂上模糊 `RenderEffect`；
 * 3. 玻璃面板在自己的绘制范围内，把这份**模糊后的内容副本**按坐标偏移画出来。
 *
 * 这样内容本身仍是清晰的，只有面板覆盖的那一条被模糊 —— 也就是系统级「实时模糊」的做法。
 *
 * ## 降级
 *
 * API < 31 没有 `RenderEffect`，此时 [GlassSurface] 自动退化为「高不透明度着色层 +
 * 边缘高光」的拟态玻璃。视觉上仍是玻璃，只是少了实时模糊。
 *
 * ## 使用方式
 *
 * ```kotlin
 * val backdrop = rememberGlassBackdrop()
 * Box(Modifier.fillMaxSize()) {
 *     LazyColumn(Modifier.glassBackdropSource(backdrop)) { ... }
 *     GlassSurface(backdrop, Modifier.align(Alignment.BottomCenter)) { ... }
 * }
 * ```
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    /** 内容层左上角在窗口中的位置，用于把模糊副本对齐回原坐标。 */
    internal var contentOrigin: Offset by mutableStateOf(Offset.Zero)
}

/** 创建一个玻璃背景源。必须在使用了 [glassBackdropSource] 的同一个组合作用域内调用。 */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * 把本组件的内容录制为可被模糊的背景源。
 *
 * 只应加在**铺满屏幕、且会被玻璃面板覆盖**的那个容器上（通常是页面的 LazyColumn）。
 * 每帧都会多一次离屏录制，因此不要加在多个嵌套层级上。
 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop): Modifier = this
    .onGloballyPositioned { backdrop.contentOrigin = it.positionInWindow() }
    .drawWithContent {
        backdrop.layer.record(
            density = this,
            layoutDirection = layoutDirection,
            size = IntSize(size.width.toInt(), size.height.toInt()),
        ) {
            // 录制发生在 drawWithContent 的接收者上，必须显式指向外层的 ContentDrawScope。
            this@drawWithContent.drawContent()
        }
        // 录制归录制，内容本身仍要正常绘制，否则用户会看到空白。
        drawContent()
    }

/**
 * 玻璃面板。
 *
 * @param backdrop 背景源。传 `null` 表示纯装饰性玻璃（不模糊任何东西，只画材质）。
 * @param shape 面板形状，决定模糊副本被裁剪成的轮廓。
 * @param blurRadius 模糊半径。过大（> 40dp）在低端设备上会明显掉帧。
 * @param tint 玻璃主体着色，建议保持 0.8 以上不透明度，否则下方文字会透上来影响可读性。
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    blurRadius: Dp = 28.dp,
    tint: Color = MaterialTheme.mealTokens.glassTint,
    tintFade: Color = MaterialTheme.mealTokens.glassTintFade,
    highlight: Color = MaterialTheme.mealTokens.glassHighlight,
    borderWidth: Dp = 0.8.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val layer = backdrop?.layer

    // 模糊效果只设置一次（半径是常量），不放进绘制回调里：
    // 在 draw 阶段修改 layer 属性会与 Compose 的绘制管线互相触发失效。
    LaunchedEffect(layer, blurRadius, canBlur) {
        if (layer == null || !canBlur) return@LaunchedEffect
        val radiusPx = with(density) { blurRadius.toPx() }
        layer.renderEffect = RenderEffect
            // CLAMP：把边缘像素向外延伸，避免面板四周出现透明羽化。
            .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }

    var panelOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .onGloballyPositioned { panelOrigin = it.positionInWindow() }
            .clip(shape)
            .drawBehind {
                // ① 先铺模糊后的背景副本（若有）。偏移量 = 内容原点 - 面板原点，
                //    使副本与窗口坐标对齐，再靠 clip 只露出面板这一块。
                if (canBlur && backdrop != null) {
                    val dx = backdrop.contentOrigin.x - panelOrigin.x
                    val dy = backdrop.contentOrigin.y - panelOrigin.y
                    clipRect {
                        translate(left = dx, top = dy) { drawLayer(backdrop.layer) }
                    }
                }
                // ② 玻璃材质：自上而下由浓到淡，模拟厚玻璃的透光梯度。
                drawRect(Brush.verticalGradient(listOf(tint, tintFade)))
                // ③ 顶部折射高光：一道极窄的白色渐变，是「玻璃」而非「塑料」的关键。
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to highlight,
                        1f to Color.Transparent,
                        startY = 0f,
                        endY = size.height * 0.28f,
                    ),
                )
            }
            .border(borderWidth, highlight, shape),
        content = content,
    )
}

/**
 * 玻璃材质的取值集合，用于非 [GlassSurface] 场景（例如自绘背景）。
 *
 * 抽成数据类是为了让调用方一次性拿到配套的四个颜色，避免各处自行拼装导致质感不一致。
 */
@Immutable
data class GlassMaterial(
    val tint: Color,
    val tintFade: Color,
    val highlight: Color,
    val blurRadius: Dp,
)

/** 读取当前主题下的玻璃材质。 */
@Composable
fun rememberGlassMaterial(): GlassMaterial {
    val tokens = MaterialTheme.mealTokens
    return remember(tokens) {
        GlassMaterial(
            tint = tokens.glassTint,
            tintFade = tokens.glassTintFade,
            highlight = tokens.glassHighlight,
            blurRadius = 28.dp,
        )
    }
}
