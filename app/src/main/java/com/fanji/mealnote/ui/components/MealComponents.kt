package com.fanji.mealnote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.ui.displayName
import java.io.File

/**
 * 业务语义组件。
 *
 * 这里放的是「带含义」的组件（状态、评价、照片入口），
 * 纯视觉的通用组件在 `Miuix.kt`，玻璃材质在 `Glass.kt`。
 */

// ─────────────────────────── 状态与评价徽章 ───────────────────────────

/**
 * 餐厅状态徽章。
 *
 * 颜色语义：**橙 = 还没去**，**绿 = 去过了**。与分段控件、统计胶囊共用同一套语义色，
 * 因此用户在任何页面看到橙色都知道代表「待探访」。
 */
@Composable
fun StatusBadge(status: RestaurantStatus, modifier: Modifier = Modifier) {
    val container = when (status) {
        RestaurantStatus.WANT_TO_EAT -> MaterialTheme.colorScheme.secondaryContainer
        RestaurantStatus.EATEN -> MaterialTheme.colorScheme.primaryContainer
    }
    val content = when (status) {
        RestaurantStatus.WANT_TO_EAT -> MaterialTheme.colorScheme.onSecondaryContainer
        RestaurantStatus.EATEN -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(modifier = modifier, color = container, contentColor = content, shape = CircleShape) {
        Text(
            text = status.displayName(),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * 三级评价徽章。
 *
 * 颜色语义：绿 = 推荐，石板灰 = 尚可（中性，因为它既不好也不坏），红 = 不推荐。
 * 文字始终与颜色同时出现，不依赖颜色单独传达信息（无障碍要求）。
 */
@Composable
fun VerdictBadge(verdict: Verdict, modifier: Modifier = Modifier, roomy: Boolean = false) {
    val container = when (verdict) {
        Verdict.GOOD -> MaterialTheme.colorScheme.primaryContainer
        Verdict.MEH -> MaterialTheme.colorScheme.tertiaryContainer
        Verdict.BAD -> MaterialTheme.colorScheme.errorContainer
    }
    val content = when (verdict) {
        Verdict.GOOD -> MaterialTheme.colorScheme.onPrimaryContainer
        Verdict.MEH -> MaterialTheme.colorScheme.onTertiaryContainer
        Verdict.BAD -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(modifier = modifier, color = container, contentColor = content, shape = CircleShape) {
        Text(
            text = verdict.displayName(),
            modifier = Modifier.padding(
                horizontal = if (roomy) 16.dp else 11.dp,
                vertical = if (roomy) 8.dp else 5.dp,
            ),
            style = if (roomy) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium,
        )
    }
}

// ───────────────────────────── 餐厅行 ─────────────────────────────

/**
 * 餐厅信息行：封面 + 店名 + 地址 + 状态徽章。
 *
 * 刻意**不含卡片外壳**，只负责内容排布 —— 「清单」页与「选店」页用的是不同的卡片容器
 * （一个白底投影、一个主色浅底），把外壳留给调用方才能复用同一套行内布局。
 */
@Composable
fun RestaurantRow(
    restaurant: RestaurantEntity,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
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
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = restaurant.name,
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
                    text = restaurant.address.ifBlank { "未填写地址" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (trailing != null) trailing() else StatusBadge(restaurant.status)
    }
}

// ─────────────────────────── 对话框与提示条 ───────────────────────────

/**
 * 不可逆操作的二次确认对话框。
 *
 * 产品约定（见 `PROJECT_PLAN.md` 7.2）：删除等不可逆操作必须确认。
 * [confirmLabel] 使用明确的动词（「删除记录」而非「确定」），让用户在按下前就知道后果；
 * 破坏性按钮统一使用 error 配色，与普通确认区分开。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "取消",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
    )
}

/**
 * 浮动提示条，用于「已保存 / 已删除」等一次性反馈。
 *
 * 不直接使用 `SnackbarHost` 的原因：本项目多个页面各自持有独立的布局根，
 * 而提示可能由弹窗内的动作触发。统一的 [MessageBanner] 让调用方自行决定展示位置与时长。
 *
 * 参数顺序遵循 Compose 约定：`modifier` 排在所有可选参数之前。
 */
@Composable
fun MessageBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.inversePrimary,
                    ),
                ) { Text(actionLabel) }
            }
        }
    }
}

// ───────────────────────────── 图片入口 ─────────────────────────────

/**
 * 图片操作入口方块。
 *
 * 尺寸固定为 92dp：与 [SelectedPhoto] 的默认尺寸一致，两者并排放在 LazyRow 中
 * 高度对齐，不会出现「入口比缩略图矮一截」的参差。
 */
@Composable
fun PhotoActionTile(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(92.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(7.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GalleryPhotoAction(onClick: () -> Unit, modifier: Modifier = Modifier) =
    PhotoActionTile(Icons.Rounded.AddPhotoAlternate, "从相册选择", onClick, modifier)

@Composable
fun CameraPhotoAction(onClick: () -> Unit, modifier: Modifier = Modifier) =
    PhotoActionTile(Icons.Rounded.PhotoCamera, "拍摄照片", onClick, modifier)

/**
 * 已选图片的缩略展示。
 *
 * 使用 [AsyncImage] 时必须显式指定目标尺寸（此处由 [size] 决定），Coil 会据此
 * 对原图做下采样解码。若省略尺寸，Coil 会按图片原始分辨率解码，一张 2048px 的照片
 * 在列表中会占用约 16MB 内存，多图页面极易 OOM。
 *
 * [onPreview] 非空时点击缩略图可看大图 —— 导入后需要确认「选对没有」，
 * 缩略图尺寸（92dp）不足以判断，尤其是构图相近的照片。
 */
@Composable
fun SelectedPhoto(
    path: String,
    contentDescription: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 92,
    onPreview: (() -> Unit)? = null,
) {
    Box(modifier = modifier.size(size.dp)) {
        AsyncImage(
            model = File(path),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .then(if (onPreview != null) Modifier.clickable(onClick = onPreview) else Modifier),
            contentScale = ContentScale.Crop,
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(44.dp),
        ) {
            // 移除按钮需要独立的无障碍描述：外层图片的描述偏向内容，
            // 屏幕阅读器用户需要知道这里可以执行「移除」操作。
            Surface(shape = CircleShape, color = Color(0xC9211C18)) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = "移除照片",
                    modifier = Modifier.padding(6.dp).size(16.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/**
 * 空图片占位块，用于详情页等「没有照片但需要保持版面结构」的位置。
 *
 * 抽出来是为了让「无封面」在列表、详情、表单三处的观感一致。
 */
@Composable
fun PhotoPlaceholder(modifier: Modifier = Modifier, iconSize: Int = 40) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Storefront,
            contentDescription = null,
            modifier = Modifier.size(iconSize.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
        )
    }
}
