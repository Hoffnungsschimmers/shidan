package com.fanji.mealnote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.fanji.mealnote.ui.theme.mealTokens
import com.fanji.mealnote.ui.theme.softShadow

/**
 * MIUIx 风格基础组件集。
 *
 * 设计约定（与 `DESIGN_SYSTEM.md` 一致）：
 * - **大圆角**：卡片 24dp、按钮/输入框 18dp；
 * - **无边框分层**：卡片靠柔和投影浮起，不使用描边；只有「按下/选中」才出现细边；
 * - **按压有反馈**：所有可点区域都会轻微缩放（见 [pressScale]）；
 * - **颜色分工固定**：主色=主操作，橙=待探访，石板灰=尚可，红=危险。
 */

// ─────────────────────────────── 卡片 ───────────────────────────────

/**
 * 内容卡片。
 *
 * [onClick] 为 `null` 时是纯展示容器（不响应点击、不显示水波纹）。
 * 传入后会自动获得按压缩放与水波纹。
 *
 * @param elevation 投影高度。列表卡片建议 2~4dp：再高会让密集列表显得嘈杂。
 */
@Composable
fun MiuixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = MaterialTheme.mealTokens
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .softShadow(shape, elevation, tokens.shadowAmbient, tokens.shadowSpot)
            .clip(shape)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content,
    )
}

// ─────────────────────────────── 按钮 ───────────────────────────────

/** 按钮的视觉层级。[Filled] 用于页面唯一主操作，其余用于次级操作。 */
enum class MiuixButtonStyle {
    /** 实心主色 + 同色系投影，视觉重量最大。 */
    Filled,

    /** 主色浅底 + 主色文字，用于并列的次级操作。 */
    Tonal,

    /** 白底细边，用于「取消」等中性操作。 */
    Outlined,

    /** 无底色纯文字。 */
    Text,
}

/**
 * 主/次操作按钮。
 *
 * [loading] 为 true 时按钮**保持原有宽度**只替换内容为转圈，
 * 避免文案切换导致的布局跳动（这在「保存」这类操作上非常显眼）。
 */
@Composable
fun MiuixButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    style: MiuixButtonStyle = MiuixButtonStyle.Filled,
    height: Dp = 54.dp,
) {
    val tokens = MaterialTheme.mealTokens
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val active = enabled && !loading

    val container = when (style) {
        MiuixButtonStyle.Filled -> MaterialTheme.colorScheme.primary
        MiuixButtonStyle.Tonal -> MaterialTheme.colorScheme.primaryContainer
        MiuixButtonStyle.Outlined -> MaterialTheme.colorScheme.surface
        MiuixButtonStyle.Text -> Color.Transparent
    }.let { if (active) it else it.copy(alpha = 0.38f) }

    val contentColor = when (style) {
        MiuixButtonStyle.Filled -> MaterialTheme.colorScheme.onPrimary
        MiuixButtonStyle.Tonal -> MaterialTheme.colorScheme.onPrimaryContainer
        MiuixButtonStyle.Outlined -> MaterialTheme.colorScheme.onSurface
        MiuixButtonStyle.Text -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            // heightIn 而非 height：大字体下按钮要能长高，否则文字会被裁掉。
            // 主按钮的 54dp 是**最小**高度而非固定高度。
            .heightIn(min = height)
            .pressScale(interaction, enabled = active)
            .then(
                // 只有实心主按钮投同色系光晕：其它样式加投影会与卡片阴影混淆层次。
                if (style == MiuixButtonStyle.Filled && active) {
                    Modifier.softShadow(
                        shape = shape,
                        elevation = 12.dp,
                        ambient = tokens.primaryShadow.copy(alpha = 0.28f),
                        spot = tokens.primaryShadow,
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(container)
            .then(
                if (style == MiuixButtonStyle.Outlined) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = active,
                role = Role.Button,
                onClick = onClick,
            )
            // 语义整体重写，原因是 loading 时标签会被换成转圈 ——
            // 默认语义会变成一片空白，屏幕阅读器既读不出按钮名，也不知道正在处理。
            // clearAndSetSemantics 会清掉 clickable 的语义，因此这里必须补回
            // role 与 onClick（或 disabled），否则按钮对无障碍服务就"消失"了。
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                if (loading) stateDescription = "处理中"
                if (active) {
                    onClick(label = label) {
                        onClick()
                        true
                    }
                } else {
                    disabled()
                }
            }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = contentColor)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ──────────────────────────── 分段控件 ────────────────────────────

/**
 * 分段控件（MIUIx 的 Segmented Control）。
 *
 * 与「标签页」的区别：分段控件用于**切换同一内容的呈现维度**，选项数量固定且都可见，
 * 因此选中态用一块会滑动的白色指示块表达，而不是下划线。
 *
 * 指示块的位移动画使用弹簧，快速连点时能自然衔接而不会闪回起点。
 *
 * ## 无障碍
 *
 * 容器是 `selectableGroup`，每个选项是 `selectable`（而不是 `clickable` + `Role.Tab`）。
 * 这两者必须配对使用，否则屏幕阅读器只会把选项读成「按钮」，
 * **不会播报哪个是当前选中项** —— 视觉上有指示块，但非视觉用户完全得不到这个信息。
 */
@Composable
fun <T> MiuixSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    badge: (T) -> String? = { null },
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val shape = MaterialTheme.shapes.medium
    val indicatorShape = MaterialTheme.shapes.small
    val tokens = MaterialTheme.mealTokens

    // 高度跟随字体缩放：固定 46dp 在系统字体放到最大档时会把文字裁掉。
    // 用「文字行高 + 内边距」反推，正常字号下算出来仍小于 46dp，
    // 因此**常规情况下高度不变**，只有大字体时才撑高。
    val density = LocalDensity.current
    val labelLineHeight = MaterialTheme.typography.labelLarge.lineHeight
    val textHeight = if (labelLineHeight == TextUnit.Unspecified) {
        SEGMENT_HEIGHT
    } else {
        with(density) { labelLineHeight.toDp() }
    }
    val segmentHeight = maxOf(SEGMENT_HEIGHT, textHeight + SEGMENT_INSET * 2 + 10.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(segmentHeight)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .selectableGroup(),
    ) {
        val itemWidth = maxWidth / options.size
        val indicatorOffset by animateDpAsState(
            targetValue = itemWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "segmentIndicator",
        )

        Box(
            modifier = Modifier
                // 使用 lambda 重载：偏移量由动画驱动，每帧都会变。
                // 非 lambda 版本会在每次重组时重新读取状态值，导致重组范围扩大。
                .offset { IntOffset(x = indicatorOffset.roundToPx(), y = 0) }
                .width(itemWidth)
                .height(segmentHeight)
                .padding(SEGMENT_INSET)
                .softShadow(indicatorShape, 2.dp, tokens.shadowAmbient, tokens.shadowSpot)
                .clip(indicatorShape)
                .background(MaterialTheme.colorScheme.surface)
                // 指示块是纯装饰：选中状态已经由选项自身的 selectable 语义表达，
                // 不排除的话屏幕阅读器会多读一遍无意义的空元素。
                .clearAndSetSemantics { },
        )

        Row(Modifier.fillMaxWidth().height(segmentHeight)) {
            options.forEach { option ->
                val active = option == selected
                // 文字颜色也做过渡，否则指示块滑到位了文字还是瞬间跳变。
                val textColor by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(segmentHeight)
                        .selectable(
                            selected = active,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { if (!active) onSelect(option) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        badge(option)?.let { count ->
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = count,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SEGMENT_HEIGHT = 46.dp
private val SEGMENT_INSET = 4.dp

// ─────────────────────────────── 输入框 ───────────────────────────────

/**
 * 填充式输入框。
 *
 * 与上一版的 `OutlinedTextField` 相比有两点不同：
 * 1. 标签**固定在上方**而非浮动到边框上 —— 浮动标签在长表单里会让每个字段的高度
 *    随聚焦状态变化，视觉上很吵；固定标签的纵向节奏始终一致。
 * 2. 使用填充底色而非描边，聚焦时以主色细边 + 更亮的底色表达。
 */
@Composable
fun MiuixTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    isError: Boolean = false,
    supportingText: String? = null,
    leadingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = true,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            isError = isError,
            shape = shape,
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = placeholder?.let {
                { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
            },
            leadingIcon = leadingIcon?.let { icon ->
                { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                errorContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                // 填充式外观：隐藏所有下划线。
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                selectionColors = TextSelectionColors(
                    handleColor = MaterialTheme.colorScheme.primary,
                    backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                ),
            ),
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            )
        }
    }
}

// ─────────────────────────────── 顶栏 ───────────────────────────────

/**
 * 页面顶栏。
 *
 * 保持**无背景色**（与页面底色一致），只有返回按钮是可见元素。
 * 需要「内容滚动时浮起」的页面请自行叠加 [GlassSurface]，而不是给顶栏加底色 ——
 * 底色会形成一条生硬的横线，玻璃层则是渐变过渡。
 */
@Composable
fun MiuixTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MiuixIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                onClick = onBack,
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

/**
 * 图标按钮。
 *
 * 默认 48dp，符合无障碍最小点击尺寸要求。**不建议调小**：图标按钮通常没有文字标签，
 * 触控面积是用户唯一能依赖的线索。
 */
@Composable
fun MiuixIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    container: Color = Color.Transparent,
    shape: Shape = MaterialTheme.shapes.small,
    size: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(size)
            .pressScale(interaction, pressedScale = 0.88f)
            .clip(shape)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(23.dp))
    }
}

// ─────────────────────────── 区块标题 / 列表行 ───────────────────────────

/**
 * 区块标题。
 *
 * 标题用 `titleLarge` 而非 `headlineMedium`：页面级大标题已经占了视觉重音，
 * 区块标题再放大两级会让页面失去层次。层级靠**字重 + 颜色**而非字号拉开。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 设置类页面使用的列表行。
 *
 * [destructive] 为 true 时整行（图标、标题、箭头）统一使用危险色，
 * 而不是只把文字变红 —— 局部变色会让用户误以为只有文字是危险的。
 */
@Composable
fun MiuixListRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    val accent = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val titleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.pressScale(interaction, pressedScale = 0.985f) else Modifier)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(MaterialTheme.shapes.small)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailingText != null) {
            Text(
                trailingText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
