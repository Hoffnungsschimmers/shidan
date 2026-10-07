package com.fanji.mealnote.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.fanji.mealnote.ui.components.GlassBackdrop
import com.fanji.mealnote.ui.components.GlassSurface
import com.fanji.mealnote.ui.components.MealMotion
import com.fanji.mealnote.ui.components.glassBackdropSource
import com.fanji.mealnote.ui.components.isFluidMotion
import com.fanji.mealnote.ui.components.pressScale
import com.fanji.mealnote.ui.components.rememberGlassBackdrop
import com.fanji.mealnote.ui.components.rememberSelectionHaptic
import com.fanji.mealnote.ui.components.staggeredEnter
import com.fanji.mealnote.ui.settings.SettingsScreen
import com.fanji.mealnote.ui.theme.mealTokens
import com.fanji.mealnote.ui.theme.softShadow

/**
 * 底部导航的三个主入口。
 *
 * 信息架构（本轮重排的核心）：
 * - **清单** —— 以「餐厅」为维度的收藏夹，回答「还有什么想吃的」；
 * - **足迹** —— 以「用餐记录」为维度的时间线，回答「我吃过什么、评价如何」；
 * - **我的** —— 数据与设置。
 *
 * 上一版只有首页一个列表，用餐记录必须进到某家餐厅的详情页才能看到；
 * 拆成「清单 / 足迹」后，两个最高频的问题各自有了直接入口。
 */
enum class MainTab(val label: String, val icon: ImageVector) {
    WANT("清单", Icons.Rounded.Bookmark),
    FOOTPRINT("足迹", Icons.Rounded.Restaurant),
    MINE("我的", Icons.Rounded.Person),
}

/** 底部导航栏高度（不含系统导航条内边距）。 */
private val NAV_BAR_HEIGHT = 64.dp

/** 内容区为底部玻璃导航预留的空白，避免最后一项被导航栏永久遮挡。 */
val MainContentBottomPadding: Dp = NAV_BAR_HEIGHT + 40.dp

/**
 * 主界面骨架：玻璃底部导航 + 悬浮记录按钮 + 三个标签页。
 *
 * ## 为什么不用 `Scaffold` 的 `bottomBar`
 *
 * 玻璃效果要求内容**从导航栏下方穿过**（否则被模糊的是一片空白，玻璃就白做了）。
 * `Scaffold` 的 `bottomBar` 会把内容区域裁掉，因此这里改为把导航栏浮在内容之上，
 * 由各标签页自己通过 `contentPadding` 预留底部空间。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    onOpenRestaurant: (Long) -> Unit,
    onAddRestaurant: () -> Unit,
    onStartVisit: () -> Unit,
) {
    val backdrop = rememberGlassBackdrop()
    val fluid = isFluidMotion
    var tab by rememberSaveable { mutableStateOf(MainTab.WANT) }
    var showEntrySheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 内容层：注册为玻璃背景源，同时自行处理状态栏内边距。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop, enabled = fluid)
                .statusBarsPadding(),
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    if (!fluid) {
                        fadeIn(animationSpec = tween(120)) togetherWith fadeOut(
                            animationSpec = tween(120),
                        )
                    } else {
                        (
                            fadeIn(animationSpec = tween(180)) +
                                scaleIn(initialScale = 0.985f, animationSpec = MealMotion.settle())
                            ).togetherWith(fadeOut(animationSpec = tween(120)))
                    }
                },
                label = "mainTab",
            ) { target ->
                when (target) {
                    MainTab.WANT -> WantListScreen(
                        onOpenRestaurant = onOpenRestaurant,
                        onAddRestaurant = onAddRestaurant,
                    )

                    MainTab.FOOTPRINT -> FootprintScreen(onOpenRestaurant = onOpenRestaurant)

                    // 「我的」是设置内容，作为标签页使用（无返回按钮，底部留白避开导航栏）。
                    MainTab.MINE -> SettingsScreen(contentBottomPadding = MainContentBottomPadding)
                }
            }
        }

        GlassNavBar(
            backdrop = backdrop,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 悬浮按钮放在导航栏之后绘制，保证它压在上层且不被玻璃层裁剪。
        // 切到「我的」时它是**缩掉**而不是消失：一个突然出现/凭空消失的主操作按钮
        // 会让用户怀疑是不是自己按错了什么。
        AnimatedVisibility(
            visible = tab != MainTab.MINE,
            enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.7f, animationSpec = MealMotion.bouncy()),
            exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = NAV_BAR_HEIGHT + 22.dp),
        ) {
            FloatingAddButton(onClick = { showEntrySheet = true })
        }
    }

    if (showEntrySheet) {
        EntrySheet(
            onDismiss = { showEntrySheet = false },
            onPickWantToEat = {
                showEntrySheet = false
                onAddRestaurant()
            },
            onPickAlreadyAte = {
                showEntrySheet = false
                onStartVisit()
            },
        )
    }
}

/** 玻璃底部导航栏。 */
@Composable
private fun GlassNavBar(
    backdrop: GlassBackdrop,
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        blurRadius = 34.dp,
        fluid = isFluidMotion,
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NAV_BAR_HEIGHT)
                    .padding(horizontal = 12.dp)
                    // 底部导航是 Tab 语义：容器声明 selectableGroup 后，
                    // 屏幕阅读器会把三个选项读成一组并播报选中态。
                    .selectableGroup(),
            ) {
                val selectedIndex = MainTab.entries.indexOf(selected).coerceAtLeast(0)
                val itemWidth = maxWidth / MainTab.entries.size
                // 选中药丸在三个槽之间**滑动**，而不是每个槽各自淡入淡出：
                // 视线会跟着那块颜色走，「我在哪儿」这件事变得不用读文字也知道。
                val pillOffset by animateDpAsState(
                    targetValue = itemWidth * selectedIndex,
                    animationSpec = MealMotion.settle(),
                    label = "navPill",
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset(x = pillOffset.roundToPx(), y = 0) }
                        .width(itemWidth)
                        .fillMaxHeight()
                        .padding(vertical = 9.dp, horizontal = 6.dp)
                        .clip(NavPillShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
                        .clearAndSetSemantics { },
                )
                Row(Modifier.fillMaxWidth().fillMaxHeight()) {
                    MainTab.entries.forEach { entry ->
                        NavItem(
                            tab = entry,
                            selected = entry == selected,
                            onClick = { onSelect(entry) },
                        )
                    }
                }
            }
        }
    }
}

private val NavPillShape = RoundedCornerShape(22.dp)

@Composable
private fun RowScope.NavItem(tab: MainTab, selected: Boolean, onClick: () -> Unit) {
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color by animateColorAsState(
        targetValue = if (selected) activeColor else idleColor,
        animationSpec = MealMotion.quick(),
        label = "navColor",
    )
    // 选中项的图标略微放大：颜色之外再给一个不依赖色觉的区分维度。
    // pop() 带过冲，切标签时图标会「弹」一下，这是导航栏唯一允许自己撒娇的地方。
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = MealMotion.pop(),
        label = "navScale",
    )
    val haptic = rememberSelectionHaptic()

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = {
                    if (!selected) {
                        haptic()
                        onClick()
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = color,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
            Spacer(Modifier.height(3.dp))
            Text(
                tab.label,
                style = MaterialTheme.typography.labelLarge,
                // 选中时字重加一档：大字体与深色模式下，只有颜色差会分不清当前栏。
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = color,
            )
        }
    }
}

/**
 * 悬浮的「记录」按钮。
 *
 * 圆角方形而非圆形，与卡片的圆角语言保持一致。按下时加号转 90°：
 * 它同时提示「这里有东西会展开」，比只缩放更像入口。
 */
@Composable
private fun FloatingAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.mealTokens
    val shape = MaterialTheme.shapes.medium
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rotation by animateFloatAsState(
        targetValue = if (pressed) 90f else 0f,
        animationSpec = MealMotion.bouncy(),
        label = "fabRotation",
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed) 6.dp else 14.dp,
        animationSpec = MealMotion.settle(),
        label = "fabShadow",
    )
    Box(
        modifier = modifier
            .size(58.dp)
            .pressScale(interaction, pressedScale = 0.9f)
            .softShadow(
                shape = shape,
                elevation = elevation,
                ambient = tokens.primaryShadow.copy(alpha = 0.35f),
                spot = tokens.primaryShadow,
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        tokens.primaryDeep,
                    ),
                ),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "新增记录",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .size(28.dp)
                .graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * 新增入口选择面板。
 *
 * 这两个选项对应产品的两种记录意图，是**互斥**的（一家店要么「还没去」要么「去过了」），
 * 因此用一张面板让用户一次性选清，而不是在表单里放一个可切换的状态开关 ——
 * 后者会让人以为可以先存成「想去」再原地改成「吃过了」，而实际上这是两条不同的数据路径。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(
    onDismiss: () -> Unit,
    onPickWantToEat: () -> Unit,
    onPickAlreadyAte: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("记录点什么", style = MaterialTheme.typography.headlineMedium)
            Text(
                "两种记录方式对应不同的数据，之后都可以继续补充。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            // 「已经吃过了」是核心记录动作(比收藏想去的店高频),放在最上并做主色强调,
            // 减少每次都要在两个等权选项间做选择的犹豫。
            EntryOption(
                icon = Icons.Rounded.Restaurant,
                title = "已经吃过了",
                description = "记录评价、餐品、花费和照片",
                onClick = onPickAlreadyAte,
                emphasized = true,
                index = 0,
            )
            EntryOption(
                icon = Icons.Rounded.Bookmark,
                title = "想吃，还没去",
                description = "先把店名记下来，之后再去打卡",
                onClick = onPickWantToEat,
                index = 1,
            )
        }
    }
}

/**
 * 入口面板里的一个选项。
 *
 * 强调档用暖光渐变 + 实心图标砖 + 右侧箭头：一眼能看出「先点这个」；
 * 普通档退成扁平的浅槽底色，不再和它抢投影层次。两项用 [staggeredEnter] 错位进场，
 * 面板弹出时读者会自上而下扫一遍，跟随视线出现的顺序比同时闪现更好读。
 */
@Composable
private fun EntryOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    index: Int = 0,
) {
    val tokens = MaterialTheme.mealTokens
    val shape = MaterialTheme.shapes.large
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptic = rememberSelectionHaptic()
    val titleColor = if (emphasized) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEnter(index)
            .pressScale(interaction, pressedScale = 0.975f)
            .then(
                if (emphasized) {
                    Modifier.softShadow(
                        shape = shape,
                        elevation = if (pressed) 2.dp else 6.dp,
                        ambient = tokens.shadowAmbient,
                        spot = tokens.shadowSpot,
                    )
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .then(
                // 强调档是暖光渐变，普通档是扁平浅槽色（渐变只给需要它的那一档）。
                if (emphasized) {
                    Modifier.background(Brush.verticalGradient(tokens.heroGradient), shape)
                } else {
                    Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape)
                },
            )
            .border(
                width = 1.dp,
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                } else {
                    Color.Transparent
                },
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = {
                    haptic()
                    onClick()
                },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.medium)
                .then(
                    if (emphasized) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    tokens.primaryDeep,
                                ),
                            ),
                            MaterialTheme.shapes.medium,
                        )
                    } else {
                        Modifier.background(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.shapes.medium,
                        )
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasized) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (emphasized) {
                    MaterialTheme.typography.titleLarge
                } else {
                    MaterialTheme.typography.titleMedium
                },
                color = titleColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (emphasized) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            },
            modifier = Modifier.size(22.dp),
        )
    }
}
