package com.fanji.mealnote.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.data.settings.RestaurantSort
import com.fanji.mealnote.ui.components.AnimatedCounter
import com.fanji.mealnote.ui.components.EmptyStateBlock
import com.fanji.mealnote.ui.components.ListSkeleton
import com.fanji.mealnote.ui.components.MealSearchField
import com.fanji.mealnote.ui.components.MealMotion
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixButtonStyle
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixChip
import com.fanji.mealnote.ui.components.MiuixSegmented
import com.fanji.mealnote.ui.components.PageHeader
import com.fanji.mealnote.ui.components.RestaurantCoverSurface
import com.fanji.mealnote.ui.components.RestaurantRow
import com.fanji.mealnote.ui.components.SectionHeader
import com.fanji.mealnote.ui.components.StatCapsule
import com.fanji.mealnote.ui.components.StatusBadge
import com.fanji.mealnote.ui.components.VerdictBadge
import com.fanji.mealnote.ui.components.isFluidMotion
import com.fanji.mealnote.ui.components.rememberPressProgress
import com.fanji.mealnote.ui.components.staggeredEnter
import com.fanji.mealnote.ui.greetingFor
import com.fanji.mealnote.ui.theme.mealTokens
import com.fanji.mealnote.ui.theme.softShadow
import java.time.LocalTime

/**
 * 「清单」标签页：以餐厅为维度的收藏夹。
 *
 * 回答的问题是「还有什么想吃的 / 哪些店已经去过了」。
 * 用餐的**具体内容**（吃了什么、花了多少）不在这里展示 —— 那是「足迹」的职责。
 *
 * 版面顺序（本轮重排）：
 * 1. **页头**：问候 + 大标题 + 一句话说明；
 * 2. **统计卡**：想去几家、去过几家、一共几餐，并把「抽一家」收进这张卡的右下角；
 * 3. **吸顶工具栏**：搜索 + 状态筛选 + 排序；
 * 4. 分组标题 + 卡片列表。
 *
 * 之前工具栏（搜索 + 分段 + 排序文字）排在页头**上面**，进页面第一眼是搜索框，
 * 大标题反而在它下面，滚动后被自己吸顶的工具栏压住 —— 顺序是倒的，现在改回来。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WantListScreen(
    onOpenRestaurant: (Long) -> Unit,
    onAddRestaurant: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    var randomPick by remember { mutableStateOf<RestaurantEntity?>(null) }

    // 吸顶工具栏是否「有内容从下面滚过去了」——决定它要不要亮出底色和分隔线。
    val scrolled by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }
    // 问候语在一次会话内固定：跨零点时正在浏览的用户不该被突然换一句问候打断，
    // 而重新进入页面时自然会取到新的时段。
    val eyebrow = remember { greetingFor(LocalTime.now().hour) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 6.dp,
            bottom = MainContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (uiState.isLoading) {
            item(key = "skeleton") {
                ListSkeleton(label = "正在加载清单")
            }
        } else {
            item(key = "header") {
                PageHeader(
                    title = "清单",
                    subtitle = "想去的店先记下来，去过的店留住评价。",
                    eyebrow = eyebrow,
                )
            }

            item(key = "stats") {
                ListStatsCard(
                    wantCount = uiState.wantCount,
                    eatenCount = uiState.eatenCount,
                    visitCount = uiState.totalVisitCount,
                    candidateCount = uiState.randomCandidates.size,
                    onRandom = { randomPick = uiState.randomCandidates.randomOtherThan(null) },
                    modifier = Modifier.staggeredEnter(0),
                )
            }

            // 随机按钮跟随**候选池**而不是当前列表：池子按设置页配置的评价范围算，
            // 与分段、搜索词无关（搜索结果里再随机一次是双重随机，用户只会觉得莫名其妙）。
            // 池为空时不显示入口 —— 「点了没反应」比「看不见」更糟。
            stickyHeader(key = "toolbar") {
                ListToolbar(
                    scrolled = scrolled,
                    query = uiState.query,
                    onQueryChange = viewModel::onQueryChange,
                    filter = uiState.filter,
                    onFilterChange = viewModel::onFilterChange,
                    counts = FilterCounts(
                        want = uiState.wantCount,
                        eaten = uiState.eatenCount,
                        total = uiState.totalCount,
                    ),
                    sortMode = uiState.sortMode,
                    onSortChange = viewModel::onSortChange,
                )
            }

            if (uiState.restaurants.isNotEmpty()) {
                item(key = "section") {
                    SectionHeader(
                        title = when (uiState.filter) {
                            HomeFilter.WANT_TO_EAT -> "想去的店"
                            HomeFilter.EATEN -> "去过的店"
                            HomeFilter.ALL -> "全部店铺"
                        },
                        modifier = Modifier.padding(top = 6.dp),
                        trailing = {
                            CountSuffix(count = uiState.restaurants.size, unit = "家")
                        },
                    )
                }
            }

            when {
                uiState.restaurants.isEmpty() -> item(key = "empty") {
                    EmptyStateBlock(
                        icon = Icons.Rounded.Storefront,
                        title = when {
                            uiState.isSearchMiss -> "没有找到匹配的店"
                            uiState.filter == HomeFilter.WANT_TO_EAT -> "还没有想去的店"
                            uiState.filter == HomeFilter.EATEN -> "还没有去过的店"
                            else -> "还没有任何记录"
                        },
                        message = if (uiState.isSearchMiss) {
                            "换个关键词试试，店名、地址、吃过的餐品都能搜。"
                        } else {
                            "先把想去的店记下来，之后再补上评价。"
                        },
                        actionLabel = if (uiState.isSearchMiss) null else "添加一家店",
                        onAction = if (uiState.isSearchMiss) null else onAddRestaurant,
                    )
                }

                else -> itemsIndexed(
                    items = uiState.restaurants,
                    key = { _, restaurant -> restaurant.id },
                ) { index, restaurant ->
                    RestaurantCard(
                        restaurant = restaurant,
                        visitCount = uiState.visitCounts[restaurant.id] ?: 0,
                        latestVerdict = uiState.latestVerdicts[restaurant.id],
                        onClick = { onOpenRestaurant(restaurant.id) },
                        // animateItem 处理已有条目的位置变化，staggeredEnter 处理新条目的入场。
                        modifier = Modifier
                            .animateItem()
                            .staggeredEnter(index),
                    )
                }
            }
        }
    }

    randomPick?.let { pick ->
        RandomPickDialog(
            pick = pick,
            scopeDescription = uiState.randomScopeDescription,
            latestVerdict = uiState.latestVerdicts[pick.id],
            onOpen = {
                randomPick = null
                onOpenRestaurant(pick.id)
            },
            onReroll = { randomPick = uiState.randomCandidates.randomOtherThan(pick) },
            onDismiss = { randomPick = null },
        )
    }
}

/** 分段筛选上的三个计数。 */
private data class FilterCounts(val want: Int, val eaten: Int, val total: Int)

/**
 * 吸顶工具栏：搜索 + 状态分段 + 排序。
 *
 * [scrolled] 为真时才亮出底色与一条极淡的分隔线：静止在页面顶部时它本来就贴在暖底上，
 * 常驻底色反而会在标题与列表之间切出一条硬边；只有内容真的从下面穿过时才需要遮挡。
 */
@Composable
private fun ListToolbar(
    scrolled: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    filter: HomeFilter,
    onFilterChange: (HomeFilter) -> Unit,
    counts: FilterCounts,
    sortMode: RestaurantSort,
    onSortChange: (RestaurantSort) -> Unit,
) {
    // 端点一律用「同色零不透明度」而不是 Color.Transparent：Transparent 是透明黑，
    // 从它插值到暖白会途经发灰的中间帧，滚动时吸顶条就变成一块脏灰色。
    val barColor by animateColorAsState(
        targetValue = if (scrolled) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.background.copy(alpha = 0f)
        },
        animationSpec = MealMotion.quick(),
        label = "toolbarBackground",
    )
    val hairline by animateColorAsState(
        targetValue = if (scrolled) {
            MaterialTheme.colorScheme.outlineVariant
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0f)
        },
        animationSpec = MealMotion.quick(),
        label = "toolbarHairline",
    )
    // 语义色在此取好：`accent` 是普通（非 @Composable）lambda，
    // 在里面读 MaterialTheme.colorScheme 会编译失败。
    val wantAccent = MaterialTheme.colorScheme.secondary
    val eatenAccent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .padding(top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MealSearchField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "搜索店名、地址或餐品",
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiuixSegmented(
                options = HomeFilter.entries.toList(),
                selected = filter,
                onSelect = onFilterChange,
                label = { it.label },
                modifier = Modifier.weight(1f),
                // 选中项继承自己那档的语义色：切到「待探访」整块指示块变琥珀，
                // 控件自己在说「这一屏是还没去的店」。
                accent = { option ->
                    when (option) {
                        HomeFilter.WANT_TO_EAT -> wantAccent
                        HomeFilter.EATEN -> eatenAccent
                        HomeFilter.ALL -> null
                    }
                },
                badge = { f ->
                    val count = when (f) {
                        HomeFilter.WANT_TO_EAT -> counts.want
                        HomeFilter.EATEN -> counts.eaten
                        HomeFilter.ALL -> counts.total
                    }
                    // 数量为 0 时不显示角标，避免出现一排「0」。
                    count.takeIf { it > 0 }?.toString()
                },
            )
            Spacer(Modifier.width(10.dp))
            SortMenu(current = sortMode, onSelect = onSortChange)
        }
        // 分隔线：绘制为 1dp 实色条，纯装饰。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(hairline)
                .clearAndSetSemantics { },
        )
    }
}

/**
 * 排序入口：图标芯片 + 下拉。
 *
 * 之前是一行孤零零的右对齐纯文字「排序：最近更新」，看起来像没做完的遗留控件；
 * 换成芯片后宽度随文字变化，和分段控件同排，仍然明确可读（不是只有图标的隐式交互）。
 */
@Composable
private fun SortMenu(current: RestaurantSort, onSelect: (RestaurantSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        MiuixChip(
            label = current.label,
            icon = Icons.Rounded.Sort,
            onClick = { open = true },
            selected = open,
            accent = MaterialTheme.colorScheme.primary,
            height = 46.dp,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RestaurantSort.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(mode.label) },
                    leadingIcon = {
                        if (mode == current) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    onClick = {
                        onSelect(mode)
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * 统计卡：三个数字 + 「抽一家」入口。
 *
 * 用暖光渐变而不是纯白：这一张卡是整页的视觉锚点，白卡和白卡之间分不出主次。
 * 数字各归自己的语义色（想去=琥珀、去过=绿、餐数=中性主色），
 * 与分段控件、徽章是同一套分工，不额外引入颜色含义。
 */
@Composable
private fun ListStatsCard(
    wantCount: Int,
    eatenCount: Int,
    visitCount: Int,
    candidateCount: Int,
    onRandom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = MaterialTheme.mealTokens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow(
                shape = MaterialTheme.shapes.large,
                elevation = 4.dp,
                ambient = tokens.shadowAmbient,
                spot = tokens.shadowSpot,
            )
            .clip(MaterialTheme.shapes.large)
            .background(Brush.verticalGradient(tokens.heroGradient))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), MaterialTheme.shapes.large)
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatCapsule(
                value = wantCount,
                label = "想去",
                accent = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
            )
            StatCapsule(
                value = eatenCount,
                label = "去过",
                accent = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            StatCapsule(
                value = visitCount,
                label = "一共这几餐",
                accent = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
        if (candidateCount > 0) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiuixChip(
                    label = "不知道吃啥？抽一家",
                    icon = Icons.Rounded.Shuffle,
                    onClick = onRandom,
                    accent = MaterialTheme.colorScheme.primary,
                    height = 42.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "$candidateCount 家在范围内",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 分组标题右侧的「N 家」。
 *
 * 整体锁死为一句播报（`共 N 家`）：数字滚动 + 单位分开两个 Text 时，
 * 屏幕阅读器会读成「3 家」中间夹着动画中间值。
 */
@Composable
private fun CountSuffix(count: Int, unit: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "共 $count $unit" },
    ) {
        AnimatedCounter(
            value = count,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = " $unit",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 餐厅卡片：封面为锚 + 店名 + 地址 + 底部元信息。
 *
 * 封面按下时**反向轻微放大**（见 [rememberPressProgress]）：卡片整体缩、图片放大，
 * 观感是照片被按进卡片里，而不是整块纸片抖一下。
 */
@Composable
private fun RestaurantCard(
    restaurant: RestaurantEntity,
    visitCount: Int,
    latestVerdict: Verdict?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = rememberPressProgress(interaction)
    MiuixCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = 2.5.dp,
        contentPadding = PaddingValues(14.dp),
        interactionSource = interaction,
    ) {
        RestaurantRow(
            restaurant = restaurant,
            visitCount = visitCount,
            latestVerdict = latestVerdict,
            press = press(),
        )
    }
}

/**
 * 随机选店结果。
 *
 * 「换一家」时封面与店名做一次**缩放替换**（[AnimatedContent]）：抽签动作需要看得见结果
 * 被换掉了，否则点下去只有一行字变了，很容易怀疑按钮没生效。
 *
 * [scopeDescription] 必须显示：用户把范围设成「仅推荐」却抽到一家从没去过的店时，
 * 第一反应是范围没生效。写上「含待探访」就不用他猜，也不用我们来解释 bug。
 */
@Composable
private fun RandomPickDialog(
    pick: RestaurantEntity,
    scopeDescription: String,
    latestVerdict: Verdict?,
    onOpen: () -> Unit,
    onReroll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val fluid = isFluidMotion
    // 弹窗首次组合时做一次「落定」动画：从 0.94 弹到 1，比默认的瞬间出现更像一张牌被翻出来。
    var appeared by remember { mutableStateOf(false) }
    val reveal by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = MealMotion.bouncy(),
        label = "pickReveal",
    )
    LaunchedEffect(Unit) { appeared = true }

    Dialog(onDismissRequest = onDismiss) {
        MiuixCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = reveal
                    val scale = 0.94f + 0.06f * reveal
                    scaleX = scale
                    scaleY = scale
                },
            elevation = 10.dp,
            contentPadding = PaddingValues(20.dp),
        ) {
            Text("今天就吃这家", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (scopeDescription.isBlank()) {
                    "随机挑的，不合适就换一家。"
                } else {
                    "$scopeDescription，不合适就换一家。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = pick,
                transitionSpec = {
                    if (fluid) {
                        (fadeIn(tween(120)) + scaleIn(initialScale = 0.9f))
                            .togetherWith(fadeOut(tween(100)) + scaleOut(targetScale = 1.05f))
                    } else {
                        fadeIn(tween(80)).togetherWith(fadeOut(tween(60)))
                    }
                },
                label = "randomPick",
            ) { current ->
                Column {
                    RestaurantCoverSurface(
                        photoPath = current.recommendationPhotoPath,
                        name = current.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.7f),
                        shape = MaterialTheme.shapes.large,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(current.name, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(current.status)
                        latestVerdict?.let {
                            Spacer(Modifier.width(8.dp))
                            VerdictBadge(it)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = current.address.ifBlank { "未填写地址" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiuixButton(
                    label = "换一家",
                    onClick = onReroll,
                    icon = Icons.Rounded.Shuffle,
                    style = MiuixButtonStyle.Tonal,
                    height = 50.dp,
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(
                    label = "看看这家",
                    onClick = onOpen,
                    height = 50.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
