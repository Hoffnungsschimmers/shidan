package com.fanji.mealnote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

/**
 * 全屏照片查看器。
 *
 * ## 交互
 *
 * - **左右滑动**切换同一组内的照片；
 * - **双击**在 1x 与 [DOUBLE_TAP_SCALE]x 之间切换；
 * - 放大后**单指拖动**平移，此时禁用翻页（否则拖动手势会被 pager 抢走）；
 * - 返回键与右上角关闭按钮均可退出。
 *
 * ## 为什么不做双指缩放
 *
 * Compose 的 `detectTransformGestures` 会消费**全部**指针事件，包括单指拖动。
 * 一旦挂上它，`HorizontalPager` 就再也收不到滑动事件，翻页直接失效。
 * 想同时支持两者，必须手写 `awaitEachGesture` 按指针数量分流，代码量和出错面都显著上升，
 * 而且没有真机很难验证。这里选择「双击缩放 + 放大后拖动」这一组不会互相冲突的手势。
 */

/** 双击放大到的倍率。再大就会明显看到照片的像素颗粒。 */
private const val DOUBLE_TAP_SCALE = 2.5f

/** 退出动画时长，用于决定何时真正释放照片引用。 */
private const val EXIT_DURATION_MS = 180L

/** 查看器的目标：一组照片 + 起始位置。 */
data class PhotoTarget(val paths: List<String>, val index: Int)

/**
 * 照片查看器的开关状态。
 *
 * 之所以把「是否可见」与「看哪几张」拆成两个字段：关闭时需要先播放淡出动画、
 * 再释放照片列表。若关闭时立即把 target 置空，动画期间内容会先消失、只剩一片黑。
 */
@Stable
class PhotoViewerState {
    internal var target by mutableStateOf<PhotoTarget?>(null)
    internal var visible by mutableStateOf(false)

    fun open(paths: List<String>, index: Int) {
        if (paths.isEmpty()) return
        target = PhotoTarget(paths, index.coerceIn(0, paths.lastIndex))
        visible = true
    }

    fun close() {
        visible = false
    }
}

@Composable
fun rememberPhotoViewerState(): PhotoViewerState = remember { PhotoViewerState() }

/**
 * 把查看器挂到当前页面。
 *
 * 实现为**全屏 Dialog** 而不是页内浮层，原因有两点：
 *
 * 1. 浮层方案要求调用方把根容器改成 `Box` 并把查看器放在最后一个子节点，
 *    而表单页的根容器是 `Column`（纵向排列），塞进去会挤占布局；
 * 2. Dialog 自带返回键处理与窗口层级，无需 `BackHandler`，也不会被页面的
 *    玻璃栏、悬浮按钮压住。
 *
 * 调用位置无所谓，放在页面任意处都会覆盖整个窗口。
 */
@Composable
fun PhotoViewerHost(state: PhotoViewerState) {
    val target = state.target ?: return

    LaunchedEffect(state.visible) {
        if (!state.visible) {
            // 等淡出播完再释放，避免动画中途内容突然消失。
            delay(EXIT_DURATION_MS)
            state.target = null
        }
    }

    Dialog(
        onDismissRequest = state::close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        AnimatedVisibility(
            visible = state.visible,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(EXIT_DURATION_MS.toInt())),
        ) {
            PhotoViewer(target = target, onDismiss = state::close)
        }
    }
}

@Composable
private fun PhotoViewer(target: PhotoTarget, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = target.index) { target.paths.size }
    // 当前页是否处于放大状态。放大时禁用 pager 的滑动，把拖动让给平移。
    var zoomed by remember { mutableStateOf(false) }

    // 换页时复位缩放：否则从放大状态翻到下一张，新图会以放大的姿态出现。
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SCRIM),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
        ) { page ->
            ZoomablePhoto(
                path = target.paths[page],
                index = page,
                total = target.paths.size,
                isCurrent = page == pagerState.currentPage,
                onZoomChanged = { if (page == pagerState.currentPage) zoomed = it },
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${pagerState.currentPage + 1} / ${target.paths.size}",
                modifier = Modifier
                    .background(Color(0x66000000), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            MiuixIconButton(
                icon = Icons.Rounded.Close,
                contentDescription = "关闭",
                onClick = onDismiss,
                tint = Color.White,
                container = Color(0x66000000),
                shape = CircleShape,
            )
        }
    }
}

@Composable
private fun ZoomablePhoto(
    path: String,
    index: Int,
    total: Int,
    isCurrent: Boolean,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(scale) { onZoomChanged(scale > 1.01f) }

    /**
     * 把平移量限制在「放大后超出容器的部分」之内。
     *
     * 上限 = 尺寸 × (缩放 - 1) / 2：缩放为 1 时上限为 0（不能平移），
     * 缩放为 2 时上限为尺寸的一半 —— 正好是图片溢出容器的量。
     */
    fun clampOffset(raw: Offset): Offset {
        val maxX = containerSize.width * (scale - 1f) / 2f
        val maxY = containerSize.height * (scale - 1f) / 2f
        return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged { containerSize = it },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(path),
            contentDescription = "第 ${index + 1} 张照片，共 $total 张",
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale > 1f) 1f else DOUBLE_TAP_SCALE
                            // 回到 1x 时必须复位平移，否则图片会停在被拖走的位置。
                            if (scale == 1f) offset = Offset.Zero
                        },
                    )
                }
                .pointerInput(scale) {
                    // 只有放大后才接管拖动；1x 时直接返回，把滑动让给 pager。
                    if (scale <= 1f) return@pointerInput
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offset = clampOffset(offset + dragAmount)
                    }
                },
            contentScale = ContentScale.Fit,
        )
    }
}

/** 查看器遮罩。接近纯黑但保留一点透明度，让用户仍能感知到「后面还有内容」。 */
private val SCRIM = Color(0xF0000000)
