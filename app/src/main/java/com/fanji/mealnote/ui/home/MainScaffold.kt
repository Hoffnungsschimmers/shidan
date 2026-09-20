package com.fanji.mealnote.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fanji.mealnote.ui.components.GlassBackdrop
import com.fanji.mealnote.ui.components.GlassSurface
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.glassBackdropSource
import com.fanji.mealnote.ui.components.isFluidMotion
import com.fanji.mealnote.ui.components.pressScale
import com.fanji.mealnote.ui.components.rememberGlassBackdrop
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
                                scaleIn(
                                    initialScale = 0.985f,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                )
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
        if (tab != MainTab.MINE) {
            FloatingAddButton(
                onClick = { showEntrySheet = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = NAV_BAR_HEIGHT + 22.dp),
            )
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
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        blurRadius = 32.dp,
        fluid = isFluidMotion,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(NAV_BAR_HEIGHT)
                .padding(horizontal = 10.dp)
                // 底部导航是 Tab 语义：容器声明 selectableGroup 后，
                // 屏幕阅读器会把三个选项读成一组并播报选中态。
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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

@Composable
private fun RowScope.NavItem(tab: MainTab, selected: Boolean, onClick: () -> Unit) {
    val activeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val color by animateColorAsState(
        targetValue = if (selected) activeColor else idleColor,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navColor",
    )
    // 选中项的图标略微放大：颜色之外再给一个不依赖色觉的区分维度。
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "navScale",
    )
    val pillColor by animateColorAsState(
        targetValue = if (selected) activeColor.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "navPill",
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .clip(MaterialTheme.shapes.small)
            .background(pillColor)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
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
            Text(tab.label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

/** 悬浮的「记录」按钮。圆角方形而非圆形，与卡片的圆角语言保持一致。 */
@Composable
private fun FloatingAddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = MaterialTheme.mealTokens
    val shape = RoundedCornerShape(20.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(58.dp)
            .pressScale(interaction, pressedScale = 0.9f)
            .softShadow(
                shape = shape,
                elevation = 14.dp,
                ambient = tokens.primaryShadow.copy(alpha = 0.35f),
                spot = tokens.primaryShadow,
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary)
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
            modifier = Modifier.size(28.dp),
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
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = MaterialTheme.colorScheme.surface,
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
            EntryOption(
                icon = Icons.Rounded.Bookmark,
                title = "想吃，还没去",
                description = "先把店名记下来，之后再去打卡",
                onClick = onPickWantToEat,
            )
            EntryOption(
                icon = Icons.Rounded.Restaurant,
                title = "已经吃过了",
                description = "记录评价、餐品、花费和照片",
                onClick = onPickAlreadyAte,
            )
        }
    }
}

@Composable
private fun EntryOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    MiuixCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
