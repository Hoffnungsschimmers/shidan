package com.fanji.mealnote.ui.visit

import com.fanji.mealnote.data.PhotoStore
import com.fanji.mealnote.data.detectPersonCount

internal fun personCountForPrice(text: String, current: Int, touched: Boolean): Int =
    if (touched) current else detectPersonCount(text) ?: 1

/** 失败和容量截断分开告知，成功图片仍可继续使用。 */
internal fun photoImportFeedback(
    selected: Int,
    attempted: Int,
    imported: Int,
    failure: PhotoStore.ImportFailure?,
): String? {
    val failed = (attempted - imported).coerceAtLeast(0)
    val omitted = (selected - attempted).coerceAtLeast(0)
    if (failed == 0 && omitted == 0) return null
    val reason = if (failure == PhotoStore.ImportFailure.NOT_AN_IMAGE) "部分文件不是有效图片"
    else "部分图片读取失败"
    return buildString {
        append("已导入 $imported 张")
        if (failed > 0) append("，$failed 张未导入；$reason，可重新选择")
        if (omitted > 0) append("；受照片数量上限限制，$omitted 张未尝试")
    }
}
