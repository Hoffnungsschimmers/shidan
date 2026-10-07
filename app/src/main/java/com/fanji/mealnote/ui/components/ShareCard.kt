package com.fanji.mealnote.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.fanji.mealnote.data.local.Verdict
import com.fanji.mealnote.ui.formatMealDate
import kotlinx.coroutines.launch
import java.io.File

/**
 * 分享卡片。
 *
 * ## 为什么先预览再分享
 *
 * 卡片是**渲染**出来的，不是预先设计好的固定图。用户点「分享」之前必须能看到
 * 到底会发出什么 —— 尤其是照片会被裁成竖版，裁剪结果无法凭想象预判。
 * 顺带这也解决了技术问题：Compose 只能录制**已经绘制过**的内容，
 * 把卡片显示在对话框里，录制才有内容可录。
 */

/** 分享卡片需要的最小数据集。刻意不复用数据库实体，避免把用不到的列带进界面层。 */
@Immutable
data class ShareCardData(
    val restaurantName: String,
    val address: String,
    val eatenAt: Long,
    val verdict: Verdict,
    val dishes: String,
    val priceText: String,
    val note: String,
    val photoPath: String?,
)

/** 卡片宽高比（竖版 3:4）。竖版在聊天与社交场景里占屏更饱满。 */
private const val CARD_ASPECT_RATIO = 3f / 4f

@Composable
fun ShareCardSheet(
    data: ShareCardData,
    onShare: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    // 渲染位图需要一帧以上，期间禁用按钮，避免连点生成多张图。
    var isCapturing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.large)
                    .drawWithContent {
                        // 录制一份卡片内容，供导出位图使用；屏幕上的显示仍走 drawContent()。
                        layer.record(
                            density = this,
                            layoutDirection = layoutDirection,
                            size = IntSize(size.width.toInt(), size.height.toInt()),
                        ) {
                            this@drawWithContent.drawContent()
                        }
                        drawContent()
                    },
            ) {
                ShareCardContent(data)
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiuixButton(
                    label = "取消",
                    onClick = onDismiss,
                    style = MiuixButtonStyle.Outlined,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
                MiuixButton(
                    label = "分享",
                    onClick = {
                        if (!isCapturing) {
                            isCapturing = true
                            scope.launch {
                                // toImageBitmap 是挂起函数：它要等渲染管线把当前帧处理完，
                                // 才能从图层里取出像素。
                                // finally 复位：若录制抛异常，按钮不能永远卡在“处理中”。
                                try {
                                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                                    onShare(bitmap)
                                } finally {
                                    isCapturing = false
                                }
                            }
                        }
                    },
                    enabled = !isCapturing,
                    loading = isCapturing,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 卡片本体。
 *
 * 版面是「照片铺满 + 底部压深 + 文字压在图上」。相比上下分栏的版式，
 * 这种全出血的排法让照片本身成为主体 —— 分享出去的东西首先得好看。
 * 没有照片时退化为主色渐变底，版面结构完全一致。
 */
@Composable
private fun ShareCardContent(data: ShareCardData) {
    Box(Modifier.fillMaxSize()) {
        if (data.photoPath != null) {
            AsyncImage(
                model = File(data.photoPath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ),
                    ),
            )
        }

        // 底部压深：保证白色文字在任何照片上都可读。
        // 分三段而不是线性到底，是为了让照片中段仍保持明亮。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        0.62f to Color(0x8C000000),
                        1f to Color(0xE6000000),
                    ),
                ),
        )

        Text(
            text = "食单",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Color(0x59000000))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(20.dp),
        ) {
            VerdictBadge(data.verdict)
            Spacer(Modifier.height(10.dp))
            Text(
                text = data.restaurantName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = buildString {
                    append(data.eatenAt.formatMealDate())
                    if (data.address.isNotBlank()) {
                        append(" · ")
                        append(data.address)
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (data.dishes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = data.dishes,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (data.priceText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = data.priceText,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(Color(0x33FFFFFF))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }

            if (data.note.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = data.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
