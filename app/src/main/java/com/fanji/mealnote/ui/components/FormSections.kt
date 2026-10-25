package com.fanji.mealnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.fanji.mealnote.ui.formatMealDate

/**
 * Shared form building blocks.
 *
 * The four form pages previously each composed their own date row,
 * photo row and error line. Same spacing in one place, drift in another.
 * These blocks lock the shared rhythm: 22dp between sections, 12dp inside.
 */

/**
 * Date row card. Whole card is tappable and opens the date picker.
 */
@Composable
fun MealDateRow(
    dateMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiuixCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("用餐日期", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    dateMillis.formatMealDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "修改",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Photo section with add entries, import progress and thumbnails.
 *
 * [maxCount] is shown in the subtitle and trailing counter.
 * [canAddPhoto] 为 false 时隐藏添加入口；导入进行中也一并隐藏——两次导入并发时
 * 后完成的一方会覆盖前一次的结果，ViewModel 虽会丢弃后来的请求，但界面不该先让用户
 * 选完再无声无息地丢掉。
 */
@Composable
fun MealPhotoSection(
    title: String,
    subtitle: String,
    photoPaths: List<String>,
    photoDescription: String,
    isImporting: Boolean,
    canAddPhoto: Boolean,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRemove: (Int) -> Unit,
    onPreview: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = title,
            subtitle = subtitle,
            trailing = {
                if (photoPaths.isNotEmpty()) {
                    Text(
                        "${photoPaths.size} 张",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
        if (isImporting) {
            Text(
                text = "正在导入图片…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (canAddPhoto && !isImporting) {
                item {
                    GalleryPhotoAction(onClick = onGallery)
                }
                item {
                    CameraPhotoAction(onClick = onCamera)
                }
            }
            itemsIndexed(photoPaths, key = { _, path -> path }) { index, path ->
                SelectedPhoto(
                    path = path,
                    contentDescription = photoDescription,
                    onRemove = { onRemove(index) },
                    onPreview = { onPreview(index) },
                )
            }
        }
    }
}

/**
 * Form error line. Kept separate from the save button so the message
 * never shifts the button position when it appears.
 */
@Composable
fun FormErrorLine(
    message: String?,
    modifier: Modifier = Modifier,
) {
    if (message == null) return
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.fillMaxWidth(),
    )
}

/** 保存区常驻失败提示，独立于可滚动表单；显式关闭并让读屏播报。 */
@Composable
fun FormErrorBanner(message: String?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (message == null) return
    MessageBanner(
        message = message,
        actionLabel = "知道了",
        onAction = onDismiss,
        destructive = true,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Shared bottom gap so last section never sticks to the save button. */
@Composable
fun FormBottomGap() {
    Spacer(Modifier.height(4.dp))
}
