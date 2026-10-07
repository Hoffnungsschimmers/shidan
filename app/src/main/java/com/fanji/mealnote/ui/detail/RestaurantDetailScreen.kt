package com.fanji.mealnote.ui.detail

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fanji.mealnote.data.local.DiningRecordWithPhotos
import com.fanji.mealnote.data.local.RestaurantWithRecords
import com.fanji.mealnote.ui.components.ConfirmDialog
import com.fanji.mealnote.ui.components.EmptyStateBlock
import com.fanji.mealnote.ui.components.GlassSurface
import com.fanji.mealnote.ui.components.LoadingBlock
import com.fanji.mealnote.ui.components.MessageBanner
import com.fanji.mealnote.ui.components.MiuixButton
import com.fanji.mealnote.ui.components.MiuixButtonStyle
import com.fanji.mealnote.ui.components.MiuixCard
import com.fanji.mealnote.ui.components.MiuixIconButton
import com.fanji.mealnote.ui.components.PhotoPlaceholder
import com.fanji.mealnote.ui.components.PhotoViewerHost
import com.fanji.mealnote.ui.components.SectionHeader
import com.fanji.mealnote.ui.components.ShareCardData
import com.fanji.mealnote.ui.components.ShareCardSheet
import com.fanji.mealnote.ui.components.StatusBadge
import com.fanji.mealnote.ui.components.VerdictBadge
import com.fanji.mealnote.ui.components.glassBackdropSource
import com.fanji.mealnote.ui.components.isFluidMotion
import com.fanji.mealnote.ui.components.rememberGlassBackdrop
import com.fanji.mealnote.ui.components.rememberPhotoViewerState
import com.fanji.mealnote.ui.formatMealDate
import com.fanji.mealnote.ui.formatLedgerAmount
import java.io.File

/** 头图高度。 */
private val HERO_HEIGHT = 250.dp

/** 信息卡片向上压住头图的高度，形成层次。 */
private val HERO_OVERLAP = 56.dp

/** 内容区为底部玻璃操作栏预留的空间。 */
private val BOTTOM_BAR_SPACE = 112.dp

/**
 * 餐厅详情。
 *
 * 版面结构：**头图 + 悬浮信息卡 + 记录时间线**。
 *
 * 顶部的返回/更多按钮始终浮在头图之上，滚动后由一块玻璃标题栏接管背景 ——
 * 这样按钮在任何滚动位置都保持可读，且不需要一条生硬的实色顶栏。
 */
@Composable
fun RestaurantDetailScreen(
    restaurantId: Long,
    onBack: () -> Unit,
    onAddVisit: () -> Unit,
    /** 「照上次再来一份」：带入该条记录的内容打开表单。 */
    onCopyLastVisit: (Long) -> Unit,
    onEditRestaurant: () -> Unit,
    onEditVisit: (Long) -> Unit,
    viewModel: RestaurantDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backdrop = rememberGlassBackdrop()
    val fluid = isFluidMotion
    val listState = rememberLazyListState()
    val photoViewer = rememberPhotoViewerState()
    val context = LocalContext.current
    var shareTarget by remember { mutableStateOf<ShareCardData?>(null) }
    val pendingShareUri by viewModel.pendingShareUri.collectAsStateWithLifecycle()

    // visits 已按用餐时间倒序，第一条就是最近一次。
    val latestRecordId = uiState.visits.firstOrNull()?.record?.id

    // 分享图片写好后发起系统分享。必须消费掉 URI，否则返回本页会重复弹出分享面板。
    LaunchedEffect(pendingShareUri) {
        val uri = pendingShareUri ?: return@LaunchedEffect
        context.startActivity(buildShareIntent(uri))
        viewModel.consumeShareUri()
    }

    LaunchedEffect(restaurantId) { viewModel.setRestaurantId(restaurantId) }
    LaunchedEffect(uiState.deletionCompleted) {
        if (uiState.deletionCompleted) {
            viewModel.consumeDeletionCompleted()
            // 删除整店后当前页面已失去数据源，必须返回列表，否则会停留在空状态。
            if (uiState.detail == null) onBack()
        }
    }

    // 头图滚出视野的比例，驱动玻璃标题栏的淡入。
    val heroScrollRange = with(LocalDensity.current) { (HERO_HEIGHT - 64.dp).toPx() }
    val collapse by remember(heroScrollRange) {
        derivedStateOf {
            val offset = if (listState.firstVisibleItemIndex > 0) {
                heroScrollRange
            } else {
                listState.firstVisibleItemScrollOffset.toFloat()
            }
            (offset / heroScrollRange).coerceIn(0f, 1f)
        }
    }

    val detail = uiState.detail

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            uiState.isLoading -> LoadingBlock(label = "正在加载店铺…")

            detail == null -> EmptyStateBlock(
                icon = Icons.Rounded.Restaurant,
                title = "这家店已经不在了",
                message = "它可能已经被删除，返回清单看看其它的。",
                actionLabel = "返回清单",
                onAction = onBack,
            )

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop, enabled = fluid),
                    contentPadding = PaddingValues(bottom = BOTTOM_BAR_SPACE),
                ) {
                    item(key = "hero") {
                        HeroSection(
                            detail = detail,
                            onAddVisit = onAddVisit,
                            hasRecords = uiState.visits.isNotEmpty(),
                            onCoverClick = {
                                photoViewer.open(listOf(detail.restaurant.recommendationPhotoPath), 0)
                            },
                        )
                    }

                    item(key = "stats") {
                        StatsRow(
                            visitCount = uiState.visits.size,
                            latestVisitAt = uiState.latestVisitAt,
                        )
                    }

                    item(key = "records-header") {
                        SectionHeader(
                            title = "用餐记录",
                            subtitle = if (uiState.visits.isEmpty()) {
                                "还没有记录过这一家"
                            } else {
                                buildString {
                                    append("共 ${uiState.visits.size} 次")
                                    uiState.totalSpentMinor?.let { append(" · 累计 ¥${it.formatLedgerAmount()}") }
                                    append("，新的在上面")
                                }
                            },
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 12.dp),
                        )
                    }

                    if (uiState.visits.isEmpty()) {
                        item(key = "records-empty") {
                            EmptyRecords(onAddVisit = onAddVisit)
                        }
                    } else {
                        itemsIndexed(
                            items = uiState.visits,
                            key = { _, item -> item.record.id },
                        ) { index, recordWithPhotos ->
                            VisitCard(
                                recordWithPhotos = recordWithPhotos,
                                onEdit = { onEditVisit(recordWithPhotos.record.id) },
                                onDelete = { viewModel.requestDeleteRecord(recordWithPhotos.record.id) },
                                onShare = {
                                    shareTarget = ShareCardData(
                                        restaurantName = detail.restaurant.name,
                                        address = detail.restaurant.address,
                                        eatenAt = recordWithPhotos.record.eatenAt,
                                        verdict = recordWithPhotos.record.verdict,
                                        dishes = recordWithPhotos.record.dishes,
                                        priceText = recordWithPhotos.record.priceText,
                                        note = recordWithPhotos.record.note,
                                        // 用第一张照片当卡片主图：它是用户自己排的顺序。
                                        photoPath = recordWithPhotos.photos
                                            .sortedBy { it.sortOrder }
                                            .firstOrNull()
                                            ?.filePath,
                                    )
                                },
                                onPhotoClick = { index ->
                                    photoViewer.open(
                                        recordWithPhotos.photos.sortedBy { it.sortOrder }.map { it.filePath },
                                        index,
                                    )
                                },
                                modifier = Modifier
                                    .animateItem()
                                    .padding(horizontal = 20.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                // 滚动后出现的玻璃标题栏：只在需要时组合，避免无谓的模糊开销。
                if (collapse > 0.01f) {
                    GlassSurface(
                        backdrop = backdrop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = collapse },
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                        blurRadius = 26.dp,
                        fluid = fluid,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .height(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = detail.restaurant.name,
                                modifier = Modifier.padding(horizontal = 76.dp),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // 悬浮操作按钮：始终显示，位置不随滚动变化。
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FloatingCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                    Spacer(Modifier.weight(1f))
                    RestaurantActionsMenu(
                        onEdit = onEditRestaurant,
                        onDelete = viewModel::requestDeleteRestaurant,
                    )
                }

                // 底部玻璃操作栏
                GlassSurface(
                    backdrop = backdrop,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    blurRadius = 30.dp,
                    fluid = fluid,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MiuixButton(
                            label = "记录这一餐",
                            onClick = onAddVisit,
                            icon = Icons.Rounded.Add,
                            modifier = Modifier.weight(1f),
                        )
                        // 只有吃过才谈得上「照上次」。没有记录时不出现，
                        // 避免一个点了没反应的按钮。
                        latestRecordId?.let { recordId ->
                            MiuixButton(
                                label = "照上次",
                                onClick = { onCopyLastVisit(recordId) },
                                icon = Icons.Rounded.Replay,
                                style = MiuixButtonStyle.Tonal,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                uiState.errorMessage?.let { message ->
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = BOTTOM_BAR_SPACE),
                    ) {
                        MessageBanner(
                            message = message,
                            actionLabel = "知道了",
                            onAction = viewModel::consumeError,
                        )
                    }
                }
            }
        }

        // 查看器挂在最外层：必须是根容器的最后一个子节点，否则会被玻璃栏与悬浮按钮压住。
        PhotoViewerHost(photoViewer)
    }

    uiState.pendingDeleteRecordId?.let {
        ConfirmDialog(
            title = "删除这条用餐记录？",
            message = "该记录的评价、餐品、花费与照片都会被移除，且无法恢复。",
            confirmLabel = "删除记录",
            onConfirm = viewModel::confirmDeleteRecord,
            onDismiss = viewModel::cancelDeleteRecord,
        )
    }

    if (uiState.showDeleteRestaurantDialog) {
        val name = uiState.detail?.restaurant?.name.orEmpty()
        ConfirmDialog(
            title = "删除「$name」？",
            message = "这家店及其全部用餐记录、照片都会被移除，且无法恢复。",
            confirmLabel = "删除店铺",
            onConfirm = viewModel::confirmDeleteRestaurant,
            onDismiss = viewModel::cancelDeleteRestaurant,
        )
    }

    shareTarget?.let { data ->
        ShareCardSheet(
            data = data,
            onShare = { bitmap ->
                viewModel.shareCard(bitmap)
                shareTarget = null
            },
            onDismiss = { shareTarget = null },
        )
    }
}

/**
 * 构造系统分享 Intent。
 *
 * 三点缺一不可：
 * - `ACTION_SEND` + `type = "image/png"` 才会被识别为分享图片；
 * - `EXTRA_STREAM` 携带图片 URI；
 * - `FLAG_GRANT_READ_URI_PERMISSION` 临时把该 URI 的读权限授给接收方 ——
 *   没有它，对方拿到 URI 也读不出内容。
 *
 * 用 `createChooser` 而不是直接 `ACTION_SEND`：后者在有多个可接收应用时
 * 会弹出一个不完整的列表，chooser 能给出完整的应用选择界面。
 */
private fun buildShareIntent(uri: Uri): Intent =
    Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },
        "分享到",
    )

/** 头图 + 压在其上的信息卡。两者放在同一个 item 里，才能形成真正的叠压而非留白。 */
@Composable
private fun HeroSection(
    detail: RestaurantWithRecords,
    onAddVisit: () -> Unit,
    hasRecords: Boolean,
    onCoverClick: () -> Unit,
) {
    val restaurant = detail.restaurant
    val hasCover = restaurant.recommendationPhotoPath.isNotBlank()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT + HERO_OVERLAP),
    ) {
        if (hasCover) {
            AsyncImage(
                model = File(restaurant.recommendationPhotoPath),
                contentDescription = "${restaurant.name}的封面图片，点击查看大图",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HERO_HEIGHT)
                    .align(Alignment.TopCenter)
                    // 只有真的有图才让它可以点开，否则会打开一个空白查看器。
                    .clickable(onClick = onCoverClick),
                contentScale = ContentScale.Crop,
            )
        } else {
            PhotoPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HERO_HEIGHT)
                    .align(Alignment.TopCenter),
                iconSize = 56,
            )
        }

        MiuixCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            elevation = 8.dp,
            contentPadding = PaddingValues(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = restaurant.name,
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (restaurant.address.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = restaurant.address,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                StatusBadge(restaurant.status)
            }

            if (!hasRecords) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "还没记录过这一家，吃完之后回来写两句。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(visitCount: Int, latestVisitAt: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatBlock(
            label = "用餐次数",
            value = if (visitCount == 0) "还没去过" else "$visitCount 次",
            modifier = Modifier.weight(1f),
        )
        StatBlock(
            label = "最近一次",
            value = latestVisitAt?.formatMealDate() ?: "暂无记录",
            modifier = Modifier.weight(1.4f),
        )
    }
}

@Composable
private fun StatBlock(label: String, value: String, modifier: Modifier = Modifier) {
    MiuixCard(
        modifier = modifier,
        elevation = 1.dp,
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 单条用餐记录卡片。
 *
 * 编辑与删除使用显式的图标按钮而非长按菜单：长按是隐藏交互，
 * 无障碍服务与不熟悉该约定的用户都难以发现。
 */
@Composable
private fun VisitCard(
    recordWithPhotos: DiningRecordWithPhotos,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val record = recordWithPhotos.record
    MiuixCard(
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.eatenAt.formatMealDate(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = record.dishes.ifBlank { "未填写餐品" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (record.dishes.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            Spacer(Modifier.width(10.dp))
            VerdictBadge(record.verdict)
        }

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

        if (record.note.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = record.note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (recordWithPhotos.photos.isNotEmpty()) {
            val ordered = recordWithPhotos.photos.sortedBy { it.sortOrder }
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(ordered, key = { _, photo -> photo.id }) { index, photo ->
                    AsyncImage(
                        model = File(photo.filePath),
                        // 逐张编号描述：多张照片若使用同一描述，屏幕阅读器无法区分彼此。
                        contentDescription = "用餐照片，共 ${ordered.size} 张，点击查看大图",
                        modifier = Modifier
                            .size(width = 124.dp, height = 98.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onPhotoClick(index) },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            MiuixIconButton(
                icon = Icons.Rounded.Share,
                contentDescription = "生成分享卡片",
                onClick = onShare,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MiuixIconButton(
                icon = Icons.Rounded.Edit,
                contentDescription = "编辑这条记录",
                onClick = onEdit,
                tint = MaterialTheme.colorScheme.primary,
            )
            MiuixIconButton(
                icon = Icons.Rounded.Delete,
                contentDescription = "删除这条记录",
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EmptyRecords(onAddVisit: () -> Unit) {
    MiuixCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        elevation = 2.dp,
        contentPadding = PaddingValues(24.dp),
    ) {
        EmptyStateBlock(
            icon = Icons.Rounded.Restaurant,
            title = "还没有用餐记录",
            message = "吃完之后记一笔，评价、餐品、花费和照片都能留下。",
            actionLabel = "记录这一餐",
            onAction = onAddVisit,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/** 覆盖在头图上的半透明圆形按钮，保证在任何图片上都有足够对比度。 */
@Composable
private fun FloatingCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    MiuixIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        tint = Color.White,
        container = Color(0x66000000),
        shape = CircleShape,
    )
}

/** 顶部的更多操作菜单：编辑与删除店铺。 */
@Composable
private fun RestaurantActionsMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MiuixIconButton(
            icon = Icons.Rounded.MoreVert,
            contentDescription = "更多操作",
            onClick = { expanded = true },
            tint = Color.White,
            container = Color(0x66000000),
            shape = CircleShape,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("编辑店铺信息") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("删除这家店", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}
