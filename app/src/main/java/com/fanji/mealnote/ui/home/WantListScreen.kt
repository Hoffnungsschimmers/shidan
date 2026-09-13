package com.fanji.mealnote.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.ui.components.AnimatedCounter
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixButtonStyle
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixSegmented
import com.fanji.mealnote.ui.components.StatusBadge
import com.fanji.mealnote.ui.components.staggeredEnter
import java.io.File

/**
 * 「清单」标签页：以餐厅为维度的收藏夹。
 *
 * 回答的问题是「还有什么想吃的 / 哪些店已经去过了」。
 * 用餐的**具体内容**（吃了什么、花了多少）不在这里展示 —— 那是「足迹」的职责。
 * 这样分工后，本页每张卡片只需回答三个问题：叫什么、在哪、去没去过。
 */
@Composable
fun WantListScreen(
    onOpenRestaurant: (Long) -> Unit,
    onAddRestaurant: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
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
        item(key = "header") { ListHeader(uiState.wantCount, uiState.eatenCount) }

        item(key = "search") { SearchBox(uiState.query, viewModel::onQueryChange) }

        item(key = "segments") {
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

        // 「随机选一家」只在待探访列表非空时出现：它是用来解决「不知道吃哪家」的，
        // 在已用餐/全部视图下没有意义，出现反而会让操作区变吵。
        if (uiState.filter == HomeFilter.WANT_TO_EAT && uiState.restaurants.isNotEmpty() && uiState.query.isBlank()) {
            item(key = "random") {
                MiuixButton(
                    label = "不知道吃啥？随机选一家",
                    onClick = { randomPick = uiState.restaurants.randomOrNull() },
                    icon = Icons.Rounded.Shuffle,
                    style = MiuixButtonStyle.Tonal,
                    height = 48.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "section") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (uiState.filter) {
                        HomeFilter.WANT_TO_EAT -> "想去的店"
                        HomeFilter.EATEN -> "去过的店"
                        HomeFilter.ALL -> "全部店铺"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (uiState.restaurants.isNotEmpty()) {
                    AnimatedCounter(
                        value = uiState.restaurants.size,
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
        }

        when {
            uiState.isLoading -> item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            uiState.restaurants.isEmpty() -> item(key = "empty") {
                EmptyState(
                    isSearchMiss = uiState.isSearchMiss,
                    filter = uiState.filter,
                    onAdd = onAddRestaurant,
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
                        .staggeredEnter(index),
                )
            }
        }
    }

    randomPick?.let { pick ->
        AlertDialog(
            onDismissRequest = { randomPick = null },
            title = { Text("今天就吃这家", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(pick.name, style = MaterialTheme.typography.headlineMedium)
                    if (pick.address.isNotBlank()) {
                        Text(
                            pick.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    randomPick = null
                    onOpenRestaurant(pick.id)
                }) { Text("看看这家") }
            },
            dismissButton = {
                // 「换一家」直接重抽，省掉「关掉弹窗再点一次」的多余步骤。
                TextButton(onClick = { randomPick = uiState.restaurants.randomOrNull() }) { Text("换一家") }
            },
            shape = MaterialTheme.shapes.extraLarge,
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun ListHeader(wantCount: Int, eatenCount: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("清单", style = MaterialTheme.typography.headlineLarge)
            Text(
                "想去的店先记下来，去过的店留住评价。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("待探访", wantCount, MaterialTheme.colorScheme.secondary)
            StatPill("已用餐", eatenCount, MaterialTheme.colorScheme.primary)
        }
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

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text(
                "搜索店名或地址",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        },
        leadingIcon = {
            Icon(
                Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "清除搜索内容", modifier = Modifier.size(18.dp))
                }
            }
        } else {
            null
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(66.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (restaurant.recommendationPhotoPath.isNotBlank()) {
                    AsyncImage(
                        model = File(restaurant.recommendationPhotoPath),
                        contentDescription = "${restaurant.name}的封面图片",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Icons.Rounded.Storefront,
                        contentDescription = null,
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    restaurant.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (restaurant.address.isNotBlank()) {
                        Icon(
                            Icons.Rounded.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        restaurant.address.ifBlank { "未填写地址" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            StatusBadge(restaurant.status)
        }
    }
}

@Composable
private fun EmptyState(isSearchMiss: Boolean, filter: HomeFilter, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Storefront,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = when {
                isSearchMiss -> "没有找到匹配的店"
                filter == HomeFilter.WANT_TO_EAT -> "还没有想去的店"
                filter == HomeFilter.EATEN -> "还没有去过的店"
                else -> "还没有任何记录"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isSearchMiss) {
                "换个关键词试试，或者直接把这家店加进来。"
            } else {
                "先把想去的店记下来，之后再补上评价。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!isSearchMiss) {
            Spacer(Modifier.height(20.dp))
            MiuixButton(
                label = "添加一家店",
                onClick = onAdd,
                style = MiuixButtonStyle.Tonal,
                height = 48.dp,
                modifier = Modifier.width(180.dp),
            )
        }
    }
}
