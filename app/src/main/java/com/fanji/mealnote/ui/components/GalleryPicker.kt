package com.fanji.mealnote.ui.components

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 相册选择器：系统 Photo Picker 优先，旧设备自动降级。
 *
 * ## 为什么需要这一层
 *
 * `ActivityResultContracts.PickVisualMedia` 在没有系统 Photo Picker、
 * 又没有 GMS 兜底的设备上，`launch` 会直接抛 `IllegalStateException`
 * （「No provider available」类错误），点击相册入口即崩溃。
 * 受影响的主要是两类设备：
 * - Android 11 及以下且没有可用的 GMS Photo Picker 兜底；
 * - 被精简过的 ROM / 出海阉割包。
 *
 * 本项目的 `minSdk` 是 26，必须处理这条降级路径。
 *
 * ## 降级策略
 *
 * - 系统 Picker 可用 → 用 `PickVisualMedia`（单选）/
 *   `PickMultipleVisualMedia`（多选），体验与之前一致；
 * - 不可用 → 降级为 `GetContent` / `GetMultipleContents`（图片 MIME 类型）。
 *   同样不需要任何存储权限（返回的 URI 自带一次性读授权），
 *   只是界面是旧式文档选择器，返回的 URI 形式不同 —— 但下游
 *   `PhotoStore.importUris` 只用 `ContentResolver.openInputStream`，
 *   两种来源一视同仁，无需任何改动。
 *
 * ## 为什么不用 `isPhotoPickerAvailable(context)` 做运行时判断
 *
 * 包内自带的 `ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable()`
 * 只检查系统自带 Picker（API 33+ 或预装模块），不包含 GMS 兜底；
 * 而真正决定 `launch` 是否抛异常的是“系统 + GMS + 回退”三路的并集。
 * 最可靠的判断是 `PickVisualMedia().getSynchronousResult(context, request)`：
 * 它返回非空恰好对应“当前会走同步回退分支、无需抛异常”的情形，
 * 与 `createIntent` 内部的抛异常条件互补。参见 activity 1.9.3 源码。
 */
private fun isSystemPickerReady(context: Context, maxItems: Int): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return true
    val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    return runCatching {
        val syncResult = if (maxItems <= 1) {
            ActivityResultContracts.PickVisualMedia().getSynchronousResult(context, request)
        } else {
            ActivityResultContracts.PickMultipleVisualMedia(maxItems)
                .getSynchronousResult(context, request)
        }
        // 同步结果非空 = 当前环境能处理该请求，launch 不会抛异常。
        syncResult != null
    }.getOrDefault(false)
}

/** 相册选择的结果回调：成功时为非空 URI 列表（含单选），用户取消时为空列表。 */
typealias GalleryPickHandler = (List<Uri>) -> Unit

/**
 * 相册选择启动器。
 *
 * 调用方只管在点击时调用 [launch]，无需关心底层是新 Picker 还是旧实现；
 * 结果统一为 URI 列表，单选场景取首个元素即可。
 */
interface GalleryPicker {
    fun launch()
}

/**
 * 创建相册选择器。
 *
 * @param maxItems 最多选择几张。1 表示单选（封面），>1 表示多选（用餐照片）。
 * @param onPicked 结果回调。用户取消时收到空列表，调用方可直接忽略。
 */
@Composable
fun rememberGalleryPicker(
    maxItems: Int,
    onPicked: GalleryPickHandler,
): GalleryPicker {
    require(maxItems >= 1) { "maxItems must be at least 1, was $maxItems" }
    val context = LocalContext.current
    val single = maxItems <= 1

    val modernSingle =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onPicked(listOf(uri))
        }
    val legacySingle =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) onPicked(listOf(uri))
        }
    val modernMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems.coerceAtLeast(2)),
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }
    val legacyMultiple = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> if (uris.isNotEmpty()) onPicked(uris) }

    return remember(
        context.applicationContext,
        single,
        modernSingle,
        legacySingle,
        modernMultiple,
        legacyMultiple,
        onPicked,
    ) {
        object : GalleryPicker {
            override fun launch() {
                if (single) {
                    launchSingle(
                        context = context,
                        modern = modernSingle,
                        legacy = legacySingle,
                    )
                } else {
                    launchMultiple(
                        context = context,
                        maxItems = maxItems,
                        modern = modernMultiple,
                        legacy = legacyMultiple,
                    )
                }
            }
        }
    }
}

private fun launchSingle(
    context: Context,
    modern: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    legacy: ManagedActivityResultLauncher<String, Uri?>,
) {
    if (isSystemPickerReady(context, maxItems = 1)) {
        runCatching {
            modern.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.onFailure {
            // 现代 Picker 在判断与启动之间状态发生变化（如模块被禁用），
            // 退回旧实现而不是崩溃。
            legacy.launch("image/*")
        }
    } else {
        legacy.launch("image/*")
    }
}

private fun launchMultiple(
    context: Context,
    maxItems: Int,
    modern: ManagedActivityResultLauncher<PickVisualMediaRequest, List<Uri>>,
    legacy: ManagedActivityResultLauncher<String, List<Uri>>,
) {
    if (isSystemPickerReady(context, maxItems)) {
        runCatching {
            modern.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.onFailure {
            legacy.launch("image/*")
        }
    } else {
        legacy.launch("image/*")
    }
}
