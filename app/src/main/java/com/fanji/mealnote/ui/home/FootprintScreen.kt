package com.fanji.mealnote.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fanji.mealnote.ui.components.AnimatedCounter
import com.fanji.mealnote.ui.components.EmptyStateBlock
import com.fanji.mealnote.ui.components.LoadingBlock
import com.fanji.mealnote.ui.components.MealSearchField
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixSegmented
import com.fanji.mealnote.ui.components.PageHeader
import com.fanji.mealnote.ui.components.PhotoViewerHost
import com.fanji.mealnote.ui.components.VerdictBadge
import com.fanji.mealnote.ui.components.isFluidMotion
import com.fanji.mealnote.ui.components.rememberPhotoViewerState
import com.fanji.mealnote.ui.components.staggeredEnter
import com.fanji.mealnote.ui.formatDayLabel
import com.fanji.mealnote.ui.formatEstimatedAmount
import java.io.File

/**
 * 「足迹」标签页：以用餐记录为维度的时间线。
 *
 * 相比上一版（用餐记录只能在餐厅详情页里看到），这里把「吃」这件事本身
 * 提升为一等公民：按月份分组、按时间倒序，一眼能看到最近吃了什么、评价如何。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FootprintScreen(
    onOpenRestaurant: (Long) -> Unit,
    viewModel: FootprintViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val photoViewer = rememberPhotoViewerState()
    val fluid = isFluidMotion
    var tab by rememberSaveable { mutableStateOf(FootprintTab.TIMELINE) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 10.dp,
                bottom = MainContentBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                PageHeader(
                    title = "足迹",
                    subtitle = "每一次吃饭都记在这里。",
                    trailing = if (uiState.totalCount > 0) {
                        {
                            Row(verticalAlignment = Alignment.Bottom) {
                                AnimatedCounter(
                                    value = uiState.totalCount,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "次用餐",
                                    modifier = Modifier.padding(bottom = 5.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        null
                    },
                )
            }

            when (tab) {
                FootprintTab.TIMELINE -> timelineContent(
                    uiState = uiState,
                    fluid = fluid,
                    tab = tab,
                    onTabChange = { tab = it },
                    onQueryChange = viewModel::onQueryChange,
                    onOpenRestaurant = onOpenRestaurant,
                    onPhotoClick = { entry, index ->
                        photoViewer.open(entry.photos.map { it.filePath }, index)
                    },
                )

                FootprintTab.STATS -> statsContent(
                    uiState = uiState,
                    onOpenRestaurant = onOpenRestaurant,
                )
            }
        }

        PhotoViewerHost(photoViewer)
    }
}

/**
 * 「足迹」的两种查看方式。
 *
 * 用分段控件而不是把统计做成第四个标签页：两者回答的是同一批数据的两个问题
 * （「吃了什么」与「一共吃了多少」），放在一起切换比让用户在底部导航里
 * 来回比较更自然，底部也得以保持三个入口。
 */
private enum class FootprintTab(val label: String) {
    /** 按月份分组的时间线，用于回顾具体吃了什么。 */
    TIMELINE("时间线"),

    /** 聚合统计，用于回答「一共吃了多少、花了多少」。 */
    STATS("统计"),
}

/**
 * 时间线内容。抽成 [LazyListScope] 扩展是为了让 [FootprintScreen] 本体保持可读。
 *
 * stickyHeader 是实验 API：标注在调用方（带 OptIn 的 FootprintScreen）还不够，
 * 定义这个扩展函数的编译单元也要 OptIn，否则按函数分别校验会报错。
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.timelineContent(
    uiState: FootprintUiState,
    fluid: Boolean,
    tab: FootprintTab,
    onTabChange: (FootprintTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenRestaurant: (Long) -> Unit,
    onPhotoClick: (FootprintEntry, Int) -> Unit,
) {
    stickyHeader(key = "timeline-toolbar") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MealSearchField(
                value = uiState.query,
                onValueChange = onQueryChange,
                placeholder = "搜索店名、餐品或备注",
            )
            MiuixSegmented(
                options = FootprintTab.entries.toList(),
                selected = tab,
                onSelect = onTabChange,
                label = { it.label },
            )
        }
    }

    when {
        uiState.isLoading -> item(key = "loading") { LoadingBlock(label = "正在加载足迹…") }

        uiState.sections.isEmpty() -> item(key = "empty") {
            EmptyStateBlock(
                icon = Icons.Rounded.Restaurant,
                title = if (uiState.isSearchMiss) "没有找到相关记录" else "还没有用餐记录",
                message = if (uiState.isSearchMiss) {
                    "换个关键词试试，店名、餐品、备注都可以搜。"
                } else {
                    "吃过之后点右下角的加号，记下评价和花费。"
                },
            )
        }

        else -> uiState.sections.forEach { section ->
            item(key = "month-${section.title}") {
                Text(
                    text = section.title,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(
                items = section.entries,
                key = { _, entry -> entry.record.id },
            ) { index, entry ->
                FootprintCard(
                    entry = entry,
                    onClick = { onOpenRestaurant(entry.record.restaurantId) },
                    onPhotoClick = { photoIndex -> onPhotoClick(entry, photoIndex) },
                    modifier = Modifier
                        .animateItem()
                        .staggeredEnter(index, enabled = fluid),
                )
            }
        }
    }
}

/** 统计内容。 */
private fun LazyListScope.statsContent(
    uiState: FootprintUiState,
    onOpenRestaurant: (Long) -> Unit,
) {
    if (!uiState.hasStats) {
        item(key = "stats-empty") {
            EmptyStateBlock(
                icon = Icons.Rounded.Restaurant,
                title = "还没有可统计的数据",
                message = "记录几次用餐之后，这里会显示次数、花费与常去的店。",
            )
        }
        return
    }

    item(key = "stats-overview") { OverviewCard(uiState) }
    item(key = "stats-spend") { SpendCard(uiState) }
    item(key = "stats-monthly") { MonthlyCard(uiState.monthlyCounts) }
    item(key = "stats-verdict") { VerdictCard(uiState) }
    if (uiState.topRestaurants.isNotEmpty()) {
        item(key = "stats-top") {
            TopRestaurantsCard(uiState.topRestaurants, onOpenRestaurant)
        }
    }
}

/** 概览：三个关键数字。 */
@Composable
private fun OverviewCard(uiState: FootprintUiState) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("今年", uiState.yearCount, "次", Modifier.weight(1f))
            StatCell("去过", uiState.visitedCount, "家", Modifier.weight(1f))
            StatCell("累计", uiState.totalCount, "次", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: Int, unit: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            // 数字带滚动动画，屏幕阅读器可能聚焦在动画中途读到错误的中间值。
            // 直接锁定为最终值播报，同时把标签与单位并进同一句。
            .clearAndSetSemantics { contentDescription = "$label $value $unit" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedCounter(
                value = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = unit,
                modifier = Modifier.padding(bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 花费估算卡。
 *
 * 金额来自**自由文本的解析**，因此文案必须始终带着「估算」二字，
 * 并明确说明可识别的记录比例 —— 否则用户会把它当成精确的账本。
 */
@Composable
private fun SpendCard(uiState: FootprintUiState) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Text(
            text = "今年花费（估算）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        val spend = uiState.estimatedSpendThisYear
        if (spend == null) {
            Text(
                text = "还没有能识别的金额",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = spend.formatEstimatedAmount(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = buildString {
                val recognized = uiState.amountRecognizedCount
                val unrecognized = uiState.amountUnrecognizedCount
                append("今年 $recognized 条记录里有可识别的数字")
                if (unrecognized > 0) append("，另有 $unrecognized 条写的是文字")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "按记录里的数字估算，不是精确账目。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

/** 最近 12 个月的用餐次数。 */
@Composable
private fun MonthlyCard(counts: List<MonthlyCount>) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        val peak = counts.maxOfOrNull { it.count } ?: 0
        Text("最近 12 个月", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(3.dp))
        Text(
            text = if (peak == 0) "这段时间还没有记录" else "最多的一月吃了 $peak 次",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        MonthlyBarChart(counts)
    }
}

@Composable
private fun MonthlyBarChart(counts: List<MonthlyCount>) {
    val peak = (counts.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    // 柱状图对屏幕阅读器完全不可见：结构上全是 Box 与空 Text。
    // 用一个整体描述替代逐柱朗读 —— 后者既冗长（12 个数字）又难以在脑中还原趋势。
    val summary = remember(counts) {
        counts.filter { it.count > 0 }
            .joinToString("，") { "${it.label} ${it.count} 次" }
            .ifEmpty { "最近 12 个月没有用餐记录" }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // clearAndSetSemantics 会连同子节点一并清掉，保证只播报这一句汇总。
            .clearAndSetSemantics { contentDescription = summary },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        counts.forEachIndexed { index, month ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = if (month.count > 0) month.count.toString() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            // 有记录时最低 6dp，保证「吃过一次」和「一次没吃」在视觉上分得开。
                            if (month.count > 0) {
                                (MAX_BAR_HEIGHT * (month.count.toFloat() / peak)).coerceAtLeast(6.dp)
                            } else {
                                3.dp
                            },
                        )
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            if (month.count > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    // 12 个月份标签在窄屏上会挤成一团，隔一个显示一个；
                    // 最后一个月（也就是当前月）始终显示，避免用户找不到「现在」。
                    text = if (index % 2 == 0 || index == counts.lastIndex) month.label else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 评价分布。与时间线页顶部共用同一套配色与文案。 */
@Composable
private fun VerdictCard(uiState: FootprintUiState) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Text("评价分布", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        VerdictDistribution(uiState.goodCount, uiState.mehCount, uiState.badCount)
    }
}

/** 常去的店。 */
@Composable
private fun TopRestaurantsCard(
    ranks: List<RestaurantRank>,
    onOpenRestaurant: (Long) -> Unit,
) {
    val peak = ranks.firstOrNull()?.count ?: 1
    MiuixCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(18.dp),
    ) {
        Text("去得最多的店", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(14.dp))
        ranks.forEachIndexed { index, rank ->
            if (index > 0) Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenRestaurant(rank.restaurantId) }
                    // 整行拼成一句播报。默认逐个子元素朗读会把「1」「老王面馆」「3 次」
                    // 拆成三段互不相关的信息，听不出这是「第 1 名，去过 3 次」。
                    // clearAndSetSemantics 会清掉 clickable 的语义，因此必须重新补上
                    // role 与 onClick —— 否则屏幕阅读器读不出这里可以点击。
                    .clearAndSetSemantics {
                        contentDescription = "第 ${index + 1} 名 ${rank.name}，去过 ${rank.count} 次"
                        role = Role.Button
                        onClick(label = "查看店铺") {
                            onOpenRestaurant(rank.restaurantId)
                            true
                        }
                    },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${index + 1}",
                        modifier = Modifier.width(20.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = rank.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${rank.count} 次",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        // 比例条是纯装饰：次数已经在上面的文字里说过了，
                        // 不排除的话屏幕阅读器会多读一个无意义的空元素。
                        .clearAndSetSemantics { },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(rank.count.toFloat() / peak)
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/** 柱状图的最大柱高。 */
private val MAX_BAR_HEIGHT = 72.dp

/**
 * 评价分布条。
 *
 * 用一条按比例分段的横条替代三个独立数字：数字需要用户自己在脑中换算比例，
 * 而横条直接呈现「大多数是好是坏」。三段同时显示文字图例，不依赖颜色单独传达信息。
 */
@Composable
private fun VerdictDistribution(goodCount: Int, mehCount: Int, badCount: Int) {
    val total = (goodCount + mehCount + badCount).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        ) {
            // 每段最小权重为 0，为 0 时不占位（用 weight 而非固定宽度，自动按比例分配）。
            VerdictSegment(goodCount, total, MaterialTheme.colorScheme.primary)
            VerdictSegment(mehCount, total, MaterialTheme.colorScheme.tertiary)
            VerdictSegment(badCount, total, MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            VerdictLegend("推荐", goodCount, MaterialTheme.colorScheme.primary)
            VerdictLegend("尚可", mehCount, MaterialTheme.colorScheme.tertiary)
            VerdictLegend("不推荐", badCount, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.VerdictSegment(
    count: Int,
    total: Int,
    color: Color,
) {
    if (count <= 0) return
    Box(
        modifier = Modifier
            .weight(count.toFloat() / total)
            .fillMaxSize()
            .background(color),
    )
}

@Composable
private fun VerdictLegend(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(3.5.dp))
                .background(color),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 单条用餐记录卡片。
 *
 * 点击整张卡片进入餐厅详情 —— 编辑与删除仍留在详情页，不在时间线上暴露。
 * 理由：时间线是「回顾」场景，用户在滑动浏览时不应该有误删的可能。
 */
@Composable
private fun FootprintCard(
    entry: FootprintEntry,
    onClick: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val record = entry.record
    MiuixCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.eatenAt.formatDayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.restaurantName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            VerdictBadge(record.verdict)
        }

        if (record.dishes.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = record.dishes,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // 花费按自由文本原样展示（可能是「128」「人均60」「约200」），
        // 因此不加 ¥ 前缀，也不做数值解析 —— 那会把用户写的说明吃掉。
        if (record.priceText.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = record.priceText,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        if (entry.photos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(entry.photos.take(MAX_TIMELINE_PHOTOS), key = { _, photo -> photo.id }) { index, photo ->
                    AsyncImage(
                        model = File(photo.filePath),
                        contentDescription = "${entry.restaurantName}的用餐照片，点击查看大图",
                        modifier = Modifier
                            .size(width = 104.dp, height = 82.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPhotoClick(index) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        if (record.note.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = record.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 时间线上每条记录最多展示的缩略图数量，更多照片进详情页看。 */
private const val MAX_TIMELINE_PHOTOS = 3
