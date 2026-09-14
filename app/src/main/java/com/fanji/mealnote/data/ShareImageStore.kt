package com.fanji.mealnote.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分享卡片的临时图片存储。
 *
 * ## 为什么放在 cacheDir 而不是 files/photos
 *
 * `files/photos/` 里的文件是**用户数据**，由数据库引用、有完整的生命周期管理。
 * 分享卡片是渲染出来的一次性中间产物，用完即弃。混进照片目录会带来两个问题：
 * 1. 设置页的「照片占用空间」会把分享卡片也算进去，数字失真；
 * 2. `cleanupOrphanPhotos` 会把它们当成孤儿文件反复清理，产生无意义的日志与 IO。
 *
 * 放在 `cacheDir/shared/` 则完全交给系统管理：存储紧张时系统可以自行回收，
 * 应用只需保证「不无限增长」。
 *
 * ## 授权方式
 *
 * 通过 `FileProvider` 生成 content URI 并配合 `FLAG_GRANT_READ_URI_PERMISSION`，
 * 接收方应用才读得到。直接用 `file://` URI 会在 Android 7.0+ 触发
 * `FileUriExposedException`。对应的路径映射见 `res/xml/file_paths.xml`。
 */
@Singleton
class ShareImageStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private companion object {
        const val DIRECTORY_NAME = "shared"

        /** PNG 无损，质量参数被忽略，统一传 100 以免被误读为「有损压缩」。 */
        const val PNG_QUALITY = 100

        /**
         * 分享图片的保留时长。
         *
         * 不立即删除的原因：分享 Intent 是异步的，接收方可能在几秒后才真正读取文件。
         * 但也不能永久保留 —— 每次分享都生成新文件，不清理会让缓存目录持续增长。
         * 24 小时足够覆盖任何正常的分享流程。
         */
        const val RETENTION_MS = 24 * 60 * 60 * 1000L
    }

    /**
     * 把位图写入缓存目录并返回可供分享的 content URI。
     *
     * 使用 PNG 而非 JPEG：分享卡片含文字与圆角，JPEG 的块效应在文字边缘很明显。
     * 卡片尺寸固定且不大，PNG 的体积代价可以接受。
     *
     * @return 写入失败（存储空间不足、FileProvider 未配置等）时返回 null，由调用方提示用户。
     */
    suspend fun writeShareImage(bitmap: Bitmap, fileName: String): Uri? = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) return@withContext null
        removeExpired(directory)

        val target = File(directory, fileName)
        val written = runCatching {
            target.outputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
            }
        }.getOrDefault(false)

        if (!written) {
            // 写入失败会留下半成品文件，必须清掉，否则下次分享会读到一张损坏的图。
            target.delete()
            return@withContext null
        }

        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }.getOrNull()
    }

    /** 清理过期的分享图片。用文件修改时间判断，不依赖文件名格式。 */
    private fun removeExpired(directory: File) {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }
}
