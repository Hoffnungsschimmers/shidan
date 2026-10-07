package com.fanji.mealnote.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fanji.mealnote.ui.theme.mealTokens
import com.fanji.mealnote.ui.theme.softShadow

/**
 * 页面级的共享状态组件（搜索框、页头、空态、骨架屏）。
 *
 * 之前三个页面各写了一份自己的搜索框：外观碰巧一样，但提示文案、清除按钮描述、
 * IME 行为都不一样。统一在这里，一处改动处处生效。
 */

/**
 * 全应用通用搜索框。
 *
 * 药丸形 + 聚焦动画：底色从暖槽色亮到纯白、描边淡入主色、放大镜图标同时变主色。
 * 这三个变化必须**同时**发生 —— 只改底色时，在暖米白底上几乎看不出「哪一格被聚焦」，
 * 而键盘已经弹出来了，用户需要立刻确认字会打进哪里。
 *
 * 清除按钮用 `AnimatedVisibility` 淡入缩回：它从无到有时如果瞬间出现，
 * 会把输入框右边缘的内容挤一下。
 */
@Composable
fun MealSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tokens = MaterialTheme.mealTokens
    val shape = MaterialTheme.shapes.large

    val container by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = MealMotion.quick(),
        label = "searchContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent,
        animationSpec = MealMotion.quick(),
        label = "searchBorder",
    )
    val iconTint by animateColorAsState(
        targetValue = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MealMotion.quick(),
        label = "searchIcon",
    )
    // 聚焦时整条搜索框微微抬起 2dp：键盘弹出后视线会下移，
    // 让输入控件自己「往前站一点」能保持它的可见性。
    val lift by animateFloatAsState(
        targetValue = if (focused) -6f else 0f,
        animationSpec = MealMotion.settle(),
        label = "searchLift",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = lift }
            .softShadow(shape, if (focused) 6.dp else 0.dp, tokens.shadowAmbient, tokens.shadowSpot)
            .clip(shape)
            .background(container)
            .border(1.4.dp, borderColor, shape),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = interaction,
            shape = shape,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                // 外层固定 40dp 槽位 + AnimatedVisibility：把整个槽位做成条件性的话，
                // 清除按钮消失时根本没有退出动画（节点被直接移除）；
                // 而不预留宽度时，输入第一个字的瞬间文字区会突然缩 40dp。
                Box(
                    modifier = Modifier.size(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AnimatedVisibility(
                        visible = value.isNotBlank(),
                        enter = fadeIn(MealMotion.quick()) + scaleIn(MealMotion.bouncy(), initialScale = 0.6f),
                        exit = fadeOut(MealMotion.quick()) + scaleOut(targetScale = 0.6f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onValueChange("") },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "清除搜索内容",
                                modifier = Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/**
 * 页头：一行小字（问候/时间）+ 大标题 + 说明。
 *
 * [eyebrow] 是标题上方那行小字。加它有两个作用：给纯标题页一点「此刻」的信息
 * （清单页是按时段变的问候语），并把标题的视觉重量让给主标题而不是一起放大。
 */
@Composable
fun PageHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(title, style = MaterialTheme.typography.displaySmall)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailing != null) {
            Spacer(Modifier.height(10.dp))
            trailing()
        }
    }
}

/**
 * 共享空态：暖光圆底 + 图标 + 标题 + 说明 + 可选动作。
 *
 * 图标圆底做极缓慢的上下浮动（仅流畅模式）：空页面是用户停留最久的地方之一，
 * 完全静止会读成「卡住了 / 坏了」，一点呼吸感表示这一屏是活的。
 */
@Composable
fun EmptyStateBlock(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val tokens = MaterialTheme.mealTokens
    val fluid = isFluidMotion
    val float by rememberInfiniteTransition(label = "emptyFloat").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "emptyFloatValue",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .graphicsLayer {
                    translationY = if (fluid) (float - 0.5f) * 10f else 0f
                }
                .clip(CircleShape)
                .background(Brush.verticalGradient(tokens.heroGradient))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clearAndSetSemantics { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            MiuixButton(
                label = actionLabel,
                onClick = onAction,
                style = MiuixButtonStyle.Tonal,
                height = 48.dp,
                modifier = Modifier.width(190.dp),
            )
        }
    }
}

/**
 * 共享加载态：转圈 + 一行说明。
 *
 * 保留给「一小块区域」的加载（详情页、保存中）。**整页列表**请改用 [ListSkeleton] ——
 * 转圈只说明「在等」，不说明在等什么，而且页面高度会在加载前后跳变。
 */
@Composable
fun LoadingBlock(
    modifier: Modifier = Modifier,
    label: String = "正在加载…",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 2.6.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 列表骨架屏：按真实卡片的尺寸画出 N 个占位卡。
 *
 * 尺寸刻意与清单卡一致（92dp 封面 + 两行文字 + 一行元信息），这样数据到达时
 * 版面**不会重排**，观感是「内容显影」而不是「换了一屏」。
 *
 * @param enabled 流畅模式关闭时不做扫光，只画静态占位块（扫光是持续的每帧绘制成本）。
 */
@Composable
fun ListSkeleton(
    modifier: Modifier = Modifier,
    cards: Int = 4,
    label: String = "正在加载清单",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val fluid = isFluidMotion
        repeat(cards) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(MaterialTheme.shapes.small)
                        .skeletonShimmer(fluid),
                )
                Spacer(Modifier.width(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    SkeletonLine(fraction = 0.55f, height = 19.dp, fluid = fluid)
                    SkeletonLine(fraction = 0.38f, height = 12.dp, fluid = fluid)
                    SkeletonLine(fraction = 0.28f, height = 12.dp, fluid = fluid)
                }
            }
        }
    }
}

@Composable
private fun SkeletonLine(fraction: Float, height: Dp, fluid: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(MaterialTheme.shapes.extraSmall)
            .skeletonShimmer(fluid),
    )
}
