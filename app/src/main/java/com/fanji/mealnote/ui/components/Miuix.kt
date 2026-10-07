package com.fanji.mealnote.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.fanji.mealnote.ui.theme.mealTokens
import com.fanji.mealnote.ui.theme.softShadow

/**
 * 「暖食欲」基础组件集。
 *
 * 设计约定（与 `DESIGN_SYSTEM.md` 一致）：
 * - **大圆角**：卡片 26dp、按钮/输入框/芯片 20dp；
 * - **无边框分层**：卡片靠柔和的**暖棕**投影浮起，不使用描边；只有聚焦/选中才出现边；
 * - **按下有反馈**：可点区域同时给出「缩放 + 投影收缩 + 触觉」三层反馈。
 *   投影按下时收到 0.35x —— 卡片看起来被**按进页面里**，这是纸面感的关键，
 *   单纯缩放只会让整块像素抖一下；
 * - **颜色分工固定**：绿=主操作/已用餐/推荐，琥珀=待探访/花费，暖石灰=尚可，红=危险。
 */

// ─────────────────────────────── 卡片 ───────────────────────────────

/**
 * 内容卡片。
 *
 * [onClick] 为 `null` 时是纯展示容器（不响应点击、不显示水波纹）。
 * 传入后会自动获得按压缩放、投影收缩与水波纹。
 *
 * @param elevation 投影高度。列表卡片建议 2~3dp：再高会让密集列表显得嘈杂。
 * @param interactionSource 需要让卡片**内部内容**（如封面图）跟着按压状态做动效时传入，
 *   调用方用同一个 source 调 [rememberPressProgress]。默认自建。
 */
@Composable
fun MiuixCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    elevation: Dp = 3.dp,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = MaterialTheme.mealTokens
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberSelectionHaptic()
    // 按下时投影收缩：卡片「落回」页面，而不是浮着抖。
    val shadow by animateDpAsState(
        targetValue = if (onClick != null && pressed) elevation * PRESSED_ELEVATION_FACTOR else elevation,
        animationSpec = MealMotion.settle(),
        label = "cardShadow",
    )
    Column(
        modifier = modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .softShadow(shape, shadow, tokens.shadowAmbient, tokens.shadowSpot)
            .clip(shape)
            .background(color)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = {
                            haptic()
                            onClick()
                        },
                    )
                } else {
                    Modifier
                }
            )
            .padding(contentPadding),
        content = content,
    )
}

private const val PRESSED_ELEVATION_FACTOR = 0.35f

// ─────────────────────────────── 按钮 ───────────────────────────────

/** 按钮的视觉层级。[Filled] 用于页面唯一主操作，其余用于次级操作。 */
enum class MiuixButtonStyle {
    /** 主色渐变 + 同色系投影，视觉重量最大。 */
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
 *
 * 实心档用 `primary → primaryDeep` 的自上而下渐变：底部更深让白色标签的对比度
 * 从 4.66:1 抬到 7.14:1 以上，同时避免整块纯色在暖底上显得「平」。
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
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberSelectionHaptic()
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

    val isHero = style == MiuixButtonStyle.Filled && active
    // 只有实心主按钮投同色系光晕：其它样式加投影会与卡片阴影混淆层次。
    val shadow by animateDpAsState(
        targetValue = if (isHero) (if (pressed) 5.dp else 12.dp) else 0.dp,
        animationSpec = MealMotion.settle(),
        label = "buttonShadow",
    )
    val sheen by animateFloatAsState(
        targetValue = if (pressed && active) 0.14f else 0f,
        animationSpec = MealMotion.quick(),
        label = "buttonSheen",
    )

    Box(
        modifier = modifier
            // heightIn 而非 height：大字体下按钮要能长高，否则文字会被裁掉。
            // 主按钮的 54dp 是**最小**高度而非固定高度。
            .heightIn(min = height)
            .pressScale(interaction, pressedScale = 0.975f, enabled = active)
            .then(
                if (isHero) {
                    Modifier.softShadow(
                        shape = shape,
                        elevation = shadow,
                        ambient = tokens.primaryShadow.copy(alpha = 0.28f),
                        spot = tokens.primaryShadow,
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(container, shape)
            .then(
                // 实心档在纯色之上再铺一层自上而下的主色渐变：底部更深，
                // 白色标签的对比度从 4.66:1 抬到 7.14:1，同时避免纯色在暖底上显得平。
                if (isHero) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                tokens.primaryDeep,
                            ),
                        ),
                        shape,
                    )
                } else {
                    Modifier
                },
            )
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
                onClick = {
                    haptic()
                    onClick()
                },
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
        // 按下的提亮层放在内容之前：它只做「被压亮」的质感，不参与布局。
        if (sheen > 0.001f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.White.copy(alpha = sheen)),
            )
        }
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

// ──────────────────────────── 芯片（轻量入口） ────────────────────────────

/**
 * 轻量芯片：图标 + 短标签的胶囊控件。
 *
 * 用于「排序」「随机抽一家」这类**不该占满一行**的次级入口。之前它们是孤零零的
 * 纯文字或一整条 48dp 实心按钮 —— 前者像没做完的遗留控件，后者视觉重量压过列表本身。
 * 芯片把两者校准到「看得见但不抢注意力」这一档。
 *
 * @param accent 选中/强调色；默认用主色，「待探访」等语义场景传入对应语义色。
 */
@Composable
fun MiuixChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    selected: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 40.dp,
) {
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberSelectionHaptic()
    val container by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = MealMotion.quick(),
        label = "chipContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = MealMotion.quick(),
        label = "chipContent",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.45f) else Color.Transparent,
        animationSpec = MealMotion.quick(),
        label = "chipBorder",
    )

    Row(
        modifier = modifier
            .heightIn(min = height)
            .pressScale(interaction, pressedScale = 0.94f)
            .clip(shape)
            .background(container)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = {
                    haptic()
                    onClick()
                },
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        // 按下时图标轻微收紧：芯片尺寸固定，缩放只能落在内容上。
        val iconScale by animateFloatAsState(
            targetValue = if (pressed) 0.86f else 1f,
            animationSpec = MealMotion.bouncy(),
            label = "chipIcon",
        )
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(17.dp)
                    .graphicScale(iconScale),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            Icon(
                trailingIcon,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** 只缩放绘制、不改变布局尺寸的缩放（避免图标缩放把同行文字挤得抖一下）。 */
private fun Modifier.graphicScale(scale: Float): Modifier = this.graphicsLayer {
    scaleX = scale
    scaleY = scale
}

// ──────────────────────────── 分段控件 ────────────────────────────

/**
 * 分段控件。
 *
 * 与「标签页」的区别：分段控件用于**切换同一内容的呈现维度**，选项数量固定且都可见，
 * 因此选中态用一块会滑动的浮起指示块表达，而不是下划线。
 *
 * [accent] 让选中项的指示块与文字**继承该选项自身的语义色**：
 * 「待探访」选中是琥珀、「已用餐」选中是绿 —— 用户切到哪个筛选，控件自己就在告诉他
 * 这一屏是什么状态，比只看文字快一拍。不传则回退到中性黑字（足迹页的「时间线 / 统计」
 * 这类无语义维度的切换就不该染色）。
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
    accent: (T) -> Color? = { null },
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
    val shape = MaterialTheme.shapes.medium
    val indicatorShape = MaterialTheme.shapes.small
    val tokens = MaterialTheme.mealTokens
    val haptic = rememberSelectionHaptic()

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
            animationSpec = MealMotion.settle(),
            label = "segmentIndicator",
        )
        val selectedAccent = options.getOrNull(selectedIndex)?.let(accent)

        Box(
            modifier = Modifier
                // 使用 lambda 重载：偏移量由动画驱动，每帧都会变。
                // 非 lambda 版本会在每次重组时重新读取状态值，导致重组范围扩大。
                .offset { IntOffset(x = indicatorOffset.roundToPx(), y = 0) }
                .width(itemWidth)
                .height(segmentHeight)
                .padding(SEGMENT_INSET)
                .softShadow(indicatorShape, 3.dp, tokens.shadowAmbient, tokens.shadowSpot)
                .clip(indicatorShape)
                // 有语义色时在纯白指示块上叠一层自上而下的同色渐变：
                // 不直接用半透明色填底，否则底槽的颜色会透上来，选中块看起来像「没填满」。
                .background(MaterialTheme.colorScheme.surface, indicatorShape)
                .then(
                    if (selectedAccent != null) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    selectedAccent.copy(alpha = 0.18f),
                                ),
                            ),
                            indicatorShape,
                        )
                    } else {
                        Modifier
                    },
                )
                // 指示块是纯装饰：选中状态已经由选项自身的 selectable 语义表达，
                // 不排除的话屏幕阅读器会多读一遍无意义的空元素。
                .clearAndSetSemantics { },
        )

        Row(Modifier.fillMaxWidth().height(segmentHeight)) {
            options.forEach { option ->
                val active = option == selected
                val optionAccent = accent(option)
                // 文字颜色也做过渡，否则指示块滑到位了文字还是瞬间跳变。
                val textColor by animateColorAsState(
                    targetValue = when {
                        active && optionAccent != null -> optionAccent
                        active -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = MealMotion.quick(),
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
                            onClick = {
                                if (!active) {
                                    haptic()
                                    onSelect(option)
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        // 选中的标签字重加一档：只靠颜色区分在大字体下不够稳。
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        badge(option)?.let { count ->
                            Spacer(Modifier.width(5.dp))
                            val badgeScale by animateFloatAsState(
                                targetValue = if (active) 1f else 0.88f,
                                animationSpec = MealMotion.bouncy(),
                                label = "segmentBadge",
                            )
                            Text(
                                text = count,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.8f),
                                modifier = Modifier.graphicScale(badgeScale),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SEGMENT_HEIGHT = 46.dp
private val SEGMENT_INSET = 5.dp

// ─────────────────────────────── 输入框 ───────────────────────────────

/**
 * 填充式输入框。
 *
 * 标签**固定在上方**而非浮动到边框上 —— 浮动标签在长表单里会让每个字段的高度随聚焦
 * 状态变化，视觉上很吵。
 *
 * 外框由本组件自己画（`background` + 动画 `border`），把 TextField 的容器色全部置透明：
 * 这样才能让「聚焦」表现为**描边从 outline 淡到主色并加粗**的连续过渡，
 * 而不是 Material 默认那种瞬间切换的下划线。
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
    visualTransformation: VisualTransformation = VisualTransformation.None,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val tokens = MaterialTheme.mealTokens

    val container by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            focused -> MaterialTheme.colorScheme.surface
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = MealMotion.quick(),
        label = "fieldContainer",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            focused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = MealMotion.quick(),
        label = "fieldBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused || isError) 1.6.dp else 1.dp,
        animationSpec = MealMotion.quick(),
        label = "fieldBorderWidth",
    )
    // 聚焦时给一层极淡的主色光晕：暖底上的「我在填这一格」不该只靠一条边。
    val glow by animateDpAsState(
        targetValue = if (focused && !isError) 6.dp else 0.dp,
        animationSpec = MealMotion.settle(),
        label = "fieldGlow",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (isError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .softShadow(shape, glow, tokens.primaryShadow.copy(alpha = 0.18f), tokens.primaryShadow.copy(alpha = 0.3f))
                .clip(shape)
                .background(container)
                .border(borderWidth, borderColor, shape),
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = true,
                singleLine = singleLine,
                minLines = minLines,
                maxLines = maxLines,
                isError = isError,
                interactionSource = interaction,
                shape = shape,
                textStyle = MaterialTheme.typography.bodyLarge,
                placeholder = placeholder?.let {
                    { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) }
                },
                leadingIcon = leadingIcon?.let { icon ->
                    { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                visualTransformation = visualTransformation,
                colors = TextFieldDefaults.colors(
                    // 容器与描边由外层负责，这里全部透明。
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    selectionColors = TextSelectionColors(
                        handleColor = MaterialTheme.colorScheme.primary,
                        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                    ),
                ),
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(start = 6.dp, top = 6.dp),
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
 *
 * @param container 底色，默认透明；传 [MaterialTheme] 的容器色即得到「圆角方块图标按钮」。
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
 *
 * [accent] 非空时在标题左侧画一道短竖条：暖底上纯文字的区块分组容易被读成「又一堆字」，
 * 竖条给出一个不依赖颜色的分组锚点。
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accent: Color? = MaterialTheme.colorScheme.primary,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        if (accent != null) {
            // 纯装饰：竖条只是分组的视觉锚点，读屏时不应成为独立元素。
            Box(
                modifier = Modifier
                    .padding(top = 4.dp, end = 9.dp)
                    .size(width = 3.dp, height = 18.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(accent)
                    .clearAndSetSemantics { },
            )
        }
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
    trailingContent: (@Composable () -> Unit)? = null,
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
    val clickable = onClick != null && enabled

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.pressScale(interaction, pressedScale = 0.985f) else Modifier)
            .clip(MaterialTheme.shapes.small)
            .then(
                if (clickable) {
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
            .padding(vertical = 14.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.small)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
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
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        }
        if (clickable) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * 统计胶囊：一个大数字 + 一行说明。
 *
 * hero 区用它讲「想去几家 / 去过几家 / 一共几餐」。数字带滚动动画，
 * 但语义**锁定终值**：屏幕阅读器读到的必须是最终数字而不是动画中间值（DESIGN_SYSTEM 第十节）。
 */
@Composable
fun StatCapsule(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = MealMotion.settle(),
        label = "statCapsuleValue",
    )
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$label $value"
        },
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = animated.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = accent,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
