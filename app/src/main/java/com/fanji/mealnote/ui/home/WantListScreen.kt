package com.fanji.mealnote.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.ui.components.AnimatedCounter
import com.fanji.mealnote.ui.components.EmptyStateBlock
import com.fanji.mealnote.ui.components.LoadingBlock
import com.fanji.mealnote.ui.components.MealSearchField
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixButtonStyle
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixSegmented
import com.fanji.mealnote.ui.components.PageHeader
import com.fanji.mealnote.ui.components.RestaurantRow
import com.fanji.mealnote.ui.components.isFluidMotion
import com.fanji.mealnote.ui.components.staggeredEnter

/**
 * 「清单」标签页：以餐厅为维度的收藏夹。
 *
 * 回答的问题是「还有什么想吃的 / 哪些店已经去过了」。
 * 用餐的**具体内容**（吃了什么、花了多少）不在这里展示 —— 那是「足迹」的职责。
 * 这样分工后，本页每张卡片只需回答三个问题：叫什么、在哪、去没去过。
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
    val fluid = isFluidMotion
    var randomPick by remember { mutableStateOf<RestaurantEntity?>(null) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 10.dp,
            bottom = MainContentBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        stickyHeader(key = "toolbar") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MealSearchField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "搜索店名、地址或餐品",
                )
                MiuixSegmented(
                    options = HomeFilter.entries.toList(),
                    selected = uiState.filter,
                    onSelect = viewModel::onFilterChange,
                    label = { it.label },
                    badge = { filter ->
                        val count = when (filter) {
                            HomeFilter.WANT_TO_EAT -> uiState.wantCount
                            HomeFilter.EATEN -> uiState.eatenCount
                            HomeFilter.ALL -> uiState.totalCount
                        }
                        // 数量为 0 时不显示角标，避免出现一排「0」。
                        count.takeIf { it > 0 }?.toString()
                    },
                )
            }
        }

        item(key = "header") {
            PageHeader(
                title = "清单",
                subtitle = "想去的店先记下来，去过的店留住评价。",
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatPill("待探访", uiState.wantCount, MaterialTheme.colorScheme.secondary)
                        StatPill("已用餐", uiState.eatenCount, MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }

        // 随机按钮跟随**候选池**而不是当前列表：池子按设置页配置的评价范围算，
        // 与分段、搜索词无关（搜索结果里再随机一次是双重随机，用户只会觉得莫名其妙）。
        // 池为空时不显示按钮 —— 「点了没反应」比「看不见」更糟。
        if (uiState.randomCandidates.isNotEmpty()) {
            item(key = "random") {
                MiuixButton(
                    label = "不知道吃啥？随机选一家",
                    onClick = { randomPick = uiState.randomCandidates.randomOtherThan(null) },
                    icon = Icons.Rounded.Shuffle,
                    style = MiuixButtonStyle.Tonal,
                    height = 48.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Section title reads as a group header for filtered results.
        if (!uiState.isLoading && uiState.restaurants.isNotEmpty()) {
            item(key = "section") {
                SectionTitleRow(
                    title = when (uiState.filter) {
                        HomeFilter.WANT_TO_EAT -> "想去的店"
                        HomeFilter.EATEN -> "去过的店"
                        HomeFilter.ALL -> "全部店铺"
                    },
                    count = uiState.restaurants.size,
                )
            }
        }

        when {
            uiState.isLoading -> item(key = "loading") { LoadingBlock(label = "正在加载清单…") }

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
                    onClick = { onOpenRestaurant(restaurant.id) },
                    // animateItem 处理已有条目的位置变化，staggeredEnter 处理新条目的入场。
                    modifier = Modifier
                        .animateItem()
                        .staggeredEnter(index, enabled = fluid),
                )
            }
        }
    }

    randomPick?.let { pick ->
        RandomPickDialog(
            pick = pick,
            scopeDescription = uiState.randomScopeDescription,
            onOpen = {
                randomPick = null
                onOpenRestaurant(pick.id)
            },
            onReroll = { randomPick = uiState.randomCandidates.randomOtherThan(pick) },
            onDismiss = { randomPick = null },
        )
    }
}

/**
 * 统计胶囊。
 *
 * 数字用 [AnimatedCounter] 滚动，颜色跟随语义（待探访=橙、已用餐=绿），
 * 与分段控件、徽章使用的是同一套语义色，用户在页面各处看到的颜色含义一致。
 */
@Composable
private fun StatPill(label: String, count: Int, accent: Color) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(accent.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedCounter(
            value = count,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = accent,
        )
    }
}

/** 餐厅卡片：封面 + 店名 + 地址 + 状态。 */
@Composable
private fun RestaurantCard(
    restaurant: RestaurantEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiuixCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(12.dp),
    ) {
        RestaurantRow(restaurant = restaurant)
    }
}

/**
 * 分组标题行：标题 + 数量。抽出来让清单与足迹的分组标题同一种语言。
 */
@Composable
private fun SectionTitleRow(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        AnimatedCounter(
            value = count,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            " 家",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 随机选店结果：封面 + 店名 + 地址 + 状态，比纯文字弹窗更像“推荐”。
 *
 * [scopeDescription] 必须显示：用户把范围设成「仅推荐」却抽到一家从没去过的店时，
 * 第一反应是范围没生效。写上「含待探访」就不用他猜，也不用我们来解释 bug。
 */
@Composable
private fun RandomPickDialog(
    pick: RestaurantEntity,
    scopeDescription: String,
    onOpen: () -> Unit,
    onReroll: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        MiuixCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = 8.dp,
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
            Spacer(Modifier.height(14.dp))
            RestaurantRow(restaurant = pick)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiuixButton(
                    label = "换一家",
                    onClick = onReroll,
                    style = MiuixButtonStyle.Tonal,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(
                    label = "看看这家",
                    onClick = onOpen,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
