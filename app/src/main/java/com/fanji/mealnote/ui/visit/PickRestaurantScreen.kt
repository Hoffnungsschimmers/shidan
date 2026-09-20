package com.fanji.mealnote.ui.visit

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.ui.components.EmptyStateBlock
import com.fanji.mealnote.ui.components.LoadingBlock
import com.fanji.mealnote.ui.components.MealSearchField
import androidx.compose.material3.Icon
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixTopBar
import com.fanji.mealnote.ui.components.RestaurantRow
import com.fanji.mealnote.ui.components.isFluidMotion
import com.fanji.mealnote.ui.components.staggeredEnter

/**
 * 选店页：为「已经吃过」流程确定目标餐厅。
 *
 * 页面顶部固定一个「新建一家店」入口：如果这家店还没进过清单，用户不需要先退出去建店 ——
 * 那正是旧流程里最别扭的一步。
 */
@Composable
fun PickRestaurantScreen(
    onBack: () -> Unit,
    onPick: (Long) -> Unit,
    onCreateNew: () -> Unit,
    viewModel: PickRestaurantViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val fluid = isFluidMotion

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        MiuixTopBar(
            title = "这次吃的是哪家？",
            subtitle = "选一家店，接着填用餐记录",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "create") {
                MiuixCard(
                    onClick = onCreateNew,
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentPadding = PaddingValues(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "不在列表里，新建一家",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "填个店名就能继续",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }

            item(key = "search") {
                MealSearchField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "搜索店名或地址",
                )
            }

            when {
                uiState.isLoading -> item(key = "loading") { LoadingBlock(label = "正在加载店铺…") }

                uiState.restaurants.isEmpty() -> item(key = "empty") {
                    EmptyStateBlock(
                        icon = Icons.Rounded.Storefront,
                        title = if (uiState.isSearchMiss) "没有找到这家店" else "清单里还没有店",
                        message = "用上面的「新建一家」直接添加。",
                    )
                }

                else -> {
                    item(key = "label") {
                        Text(
                            text = "最近记录",
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(uiState.restaurants, key = { it.id }) { restaurant ->
                        PickableRestaurantCard(
                            restaurant = restaurant,
                            onClick = { onPick(restaurant.id) },
                            modifier = Modifier.staggeredEnter(0, enabled = fluid),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickableRestaurantCard(
    restaurant: RestaurantEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiuixCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        RestaurantRow(restaurant = restaurant)
    }
}
