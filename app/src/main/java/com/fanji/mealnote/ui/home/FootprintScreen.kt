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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.fanji.mealnote.ui.components.AnimatedCounter
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.VerdictBadge
import com.fanji.mealnote.ui.components.staggeredEnter
import com.fanji.mealnote.ui.formatDayLabel
import java.io.File

/**
 * 「足迹」标签页：以用餐记录为维度的时间线。
 *
 * 相比上一版（用餐记录只能在餐厅详情页里看到），这里把「吃」这件事本身
 * 提升为一等公民：按月份分组、按时间倒序，一眼能看到最近吃了什么、评价如何。
 */
@Composable
fun FootprintScreen(
    onOpenRestaurant: (Long) -> Unit,
    viewModel: FootprintViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
            FootprintHeader(
                totalCount = uiState.totalCount,
                goodCount = uiState.goodCount,
                mehCount = uiState.mehCount,
                badCount = uiState.badCount,
            )
        }

        item(key = "search") { SearchBox(uiState.query, viewModel::onQueryChange) }

        when {
            uiState.isLoading -> item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            uiState.sections.isEmpty() -> item(key = "empty") {
                EmptyFootprint(isSearchMiss = uiState.isSearchMiss)
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
                        modifier = Modifier
                            .animateItem()
                            .staggeredEnter(index),
                    )
                }
            }
        }
    }
}

@Composable
private fun FootprintHeader(totalCount: Int, goodCount: Int, mehCount: Int, badCount: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("足迹", style = MaterialTheme.typography.headlineLarge)
            Text(
                "每一次吃饭都记在这里，按时间倒序排列。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (totalCount > 0) {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedCounter(
                    value = totalCount,
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
            VerdictDistribution(goodCount, mehCount, badCount)
        }
    }
}

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
                "搜索店名、餐品或备注",
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
                items(entry.photos.take(MAX_TIMELINE_PHOTOS), key = { it.id }) { photo ->
                    AsyncImage(
                        model = File(photo.filePath),
                        contentDescription = "${entry.restaurantName}的用餐照片",
                        modifier = Modifier
                            .size(width = 104.dp, height = 82.dp)
                            .clip(MaterialTheme.shapes.small),
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

@Composable
private fun EmptyFootprint(isSearchMiss: Boolean) {
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
                Icons.Rounded.Restaurant,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (isSearchMiss) "没有找到相关记录" else "还没有用餐记录",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isSearchMiss) {
                "换个关键词试试，店名、餐品、备注都可以搜。"
            } else {
                "吃过之后点右下角的加号，记下评价和花费。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 时间线上每条记录最多展示的缩略图数量，更多照片进详情页看。 */
private const val MAX_TIMELINE_PHOTOS = 3
