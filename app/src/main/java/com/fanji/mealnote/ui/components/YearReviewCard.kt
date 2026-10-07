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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fanji.mealnote.ui.formatEstimatedAmount
import com.fanji.mealnote.ui.formatLedgerAmount
import kotlinx.coroutines.launch

/**
 * 年度回顾分享卡。
 *
 * 复用 [ShareCardSheet] 的「预览 + `GraphicsLayer` 录制导出」骨架(见其注释),
 * 只是内容换成一年的汇总:次数、花费、最常去的店、评价分布。刻意做成纯色渐变底 +
 * 大字排版,不依赖任何照片(年度维度没有单一代表照片)。
 */
@Immutable
data class YearReviewCardData(
    val year: Int,
    val visitCount: Int,
    /** 该年入账总额(分);null 表示无入账。 */
    val ledgerMinor: Long?,
    /** 该年估算花费(元);null 表示无可识别金额。ledger 优先,ledger 为空才用它。 */
    val estimatedYuan: Double?,
    val topRestaurantName: String?,
    val topRestaurantVisits: Int,
    val goodCount: Int,
    val mehCount: Int,
    val badCount: Int,
)

private const val REVIEW_CARD_ASPECT_RATIO = 3f / 4f

@Composable
fun YearReviewSheet(
    data: YearReviewCardData,
    onShare: (Bitmap) -> Unit,
    onDismiss: () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var isCapturing by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(REVIEW_CARD_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.large)
                    .drawWithContent {
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
                YearReviewContent(data)
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

@Composable
private fun YearReviewContent(data: YearReviewCardData) {
    val spendText: String? = when {
        data.ledgerMinor != null -> "花了 ¥${data.ledgerMinor.formatLedgerAmount()}"
        data.estimatedYuan != null -> "约花了 ${data.estimatedYuan.formatEstimatedAmount()}"
        else -> null
    }
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
    ) {
        Text(
            text = "食单",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(Color(0x33000000))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(
                text = "${data.year}",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "年度回顾",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(24.dp))
            ReviewLine("这一年吃了", "${data.visitCount} 顿")
            if (spendText != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = spendText,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                )
            }
            if (data.topRestaurantName != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "最常去 ${data.topRestaurantName} · ${data.topRestaurantVisits} 次",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "推荐 ${data.goodCount} · 尚可 ${data.mehCount} · 不推荐 ${data.badCount}",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun ReviewLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}
