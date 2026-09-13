package com.fanji.mealnote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 应用私有照片目录的唯一读写入口。
 *
 * 生命周期约定（与 `ADD` / `VISIT` 表单配合）：
 * 1. 相册导入或拍照产生的文件先落在 `files/photos/`，此时**尚未写入数据库**；
 * 2. 表单保存成功后文件被数据库引用，成为正式照片；
 * 3. 用户移除图片、取消拍照或放弃表单时，调用 [deleteOwnedPhoto] 回收；
 * 4. 删除用餐记录 / 餐厅时由 `MealRepository` 回收对应文件。
 *
 * 安全约束：所有删除操作都经过 [isOwnedByApp] 校验，只作用于本目录内的文件，
 * 防止数据库被篡改或路径拼接错误时误删用户其他数据。
 */
@Singleton
class PhotoStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /** 复制成功后写回数据库的最大边长（像素）。超大图会先降采样再存储。 */
    private companion object {
        const val MAX_IMAGE_EDGE_PX = 2048
        const val JPEG_QUALITY = 88
        const val PHOTO_DIR_NAME = "photos"
        const val TEMP_PREFIX = "photo_"

        /**
         * 孤儿文件的最小存活时长。早于该时长的文件视为“可能正在被使用”，不参与清理，
         * 避免误删相机拍摄中或导入中的图片。
         */
        const val ORPHAN_MIN_AGE_MS = 10 * 60 * 1000L
    }

    /**
     * 从相册 / 系统选择器导入图片。
     *
     * 逐张处理并返回**成功导入的绝对路径**；单张失败不会中断其余图片，失败项被静默跳过
     * 并由调用方通过“返回值数量 < 入参数量”判断是否需要提示用户。
     *
     * 导入过程中会按 [MAX_IMAGE_EDGE_PX] 降采样并校正 EXIF 方向，避免把手机原图
     * （常见 8~12MB）原样复制进私有目录造成存储膨胀。
     */
    suspend fun importUris(uris: List<Uri>): List<String> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri -> importSingle(uri) }
    }

    /**
     * 把已存在于磁盘上的图片文件复制进私有照片目录。
     *
     * 专用于备份恢复：备份包解压出的照片已经是应用可用的 JPEG，无需再次解码/降采样，
     * 直接字节复制即可，既更快也避免重编码带来的画质损失。
     *
     * @return 复制后的绝对路径；源文件不存在或复制失败时返回 null，由上层决定跳过还是中止。
     */
    suspend fun importFile(source: File): String? = withContext(Dispatchers.IO) {
        if (!source.isFile) return@withContext null
        var target: File? = null
        try {
            val created = createPhotoFile().also { target = it }
            source.inputStream().use { input ->
                created.outputStream().use { output -> input.copyTo(output) }
            }
            created.absolutePath
        } catch (_: Exception) {
            // 复制中途失败会留下半成品文件，必须清理，否则会变成孤儿文件。
            target?.delete()
            null
        }
    }

    /**
     * 为相机拍摄创建一个待写入的目标文件与其 content URI。
     *
     * 调用方必须在拍摄回调中二选一：
     * - 成功：[onCameraPhoto] 对应的逻辑会保留该文件；
     * - 取消/失败：调用 [deleteOwnedPhoto] 删除，否则会产生孤儿文件。
     */
    fun createCameraTarget(): Pair<File, Uri> {
        val file = createPhotoFile()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        return file to uri
    }

    /**
     * 删除应用自有的照片文件。非本应用目录内的路径一律拒绝。
     *
     * @return true 表示文件已不存在或删除成功；false 表示路径不属于本应用目录，或系统拒绝删除。
     */
    fun deleteOwnedPhoto(path: String): Boolean {
        if (path.isBlank()) return true
        val photo = File(path)
        if (!isOwnedByApp(photo)) return false
        return !photo.exists() || photo.delete()
    }

    /**
     * 批量删除应用自有的照片文件。
     *
     * @return 实际删除失败（文件仍存在）的数量，正常情况为 0。
     */
    fun deleteOwnedPhotos(paths: Iterable<String>): Int = paths.count { !deleteOwnedPhoto(it) }

    /**
     * 扫描照片目录，删除**未被引用**的文件。
     *
     * 用于回收保存流程异常中断留下的残留文件。调用方需保证：
     * 1. [referencedPaths] 来自当前数据库快照与各表单未提交图片的并集；
     * 2. 没有正在进行的相机拍摄 —— 相机目标文件在拍摄回调前不会出现在任何引用集合中，
     *    此时清理会误删用户即将保存的照片。
     *
     * 为避免“刚创建、尚未写入的相机目标文件”被误删，仅清理超过 [ORPHAN_MIN_AGE_MS]
     * 的陈旧文件：正常保存流程不会耗时这么久，而残留文件会随时间自然满足条件。
     *
     * @return 清理掉的孤儿文件数量。
     */
    fun deleteOrphanPhotos(referencedPaths: Collection<String>): Int {
        val referenced = referencedPaths
            .mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }
            .toSet()
        val files = photoDirectory().listFiles() ?: return 0
        val cutoff = System.currentTimeMillis() - ORPHAN_MIN_AGE_MS
        var removed = 0
        files.forEach { file ->
            if (!file.isFile) return@forEach
            // 跳过刚创建的文件，避免误删正在拍摄/导入中的图片。
            if (file.lastModified() > cutoff) return@forEach
            val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
            if (canonical !in referenced && file.delete()) removed++
        }
        return removed
    }

    /** 统计照片目录当前占用空间（字节），用于设置页展示存储用量。 */
    fun photoDirectorySizeBytes(): Long =
        photoDirectory().listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    /**
     * 校验 [file] 是否位于本应用的照片目录内。
     *
     * 使用 canonical path 比较，可抵御 `../` 形式的路径穿越；同时要求直接父目录即为照片
     * 目录，避免未来新增子目录时越权删除。
     */
    private fun isOwnedByApp(file: File): Boolean {
        val directory = runCatching { photoDirectory().canonicalFile }.getOrNull() ?: return false
        val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return canonical.parentFile == directory
    }

    /** 复制单个 URI 到私有目录；任何失败都会清理半成品文件并返回 null。 */
    private fun importSingle(uri: Uri): String? {
        var destination: File? = null
        var bitmap: Bitmap? = null
        return try {
            val target = createPhotoFile().also { destination = it }
            bitmap = decodeDownsampled(uri) ?: throw IOException("Failed to decode $uri")
            writeBitmap(bitmap, target)
            target.absolutePath
        } catch (_: Exception) {
            // 解码或写盘失败：删除可能已创建的半成品文件，避免留下孤儿文件。
            destination?.delete()
            null
        } finally {
            // 无论成功或失败都必须回收位图。旋转后的新对象与原始对象可能是同一个实例，
            // recycle 对已回收对象是幂等的，此处只负责兜底释放。
            bitmap?.recycle()
        }
    }

    /**
     * 读取 URI 并降采样到 [MAX_IMAGE_EDGE_PX] 以内。
     *
     * 先用 `inJustDecodeBounds` 只读元数据算出采样率，再真正解码，避免大图 OOM。
     * 解码失败返回 null，由调用方跳过该图片。
     */
    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null

        // 校正 EXIF 方向，否则竖拍照片在列表中会横置。
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)

        if (rotation == 0f) return decoded
        return runCatching {
            val matrix = Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                .also { if (it !== decoded) decoded.recycle() }
        }.getOrDefault(decoded)
    }

    /** 计算 2 的幂采样率，使解码结果不超过 [MAX_IMAGE_EDGE_PX]。 */
    private fun calculateSampleSize(width: Int, height: Int): Int {
        var sampleSize = 1
        var longestEdge = maxOf(width, height)
        while (longestEdge / 2 >= MAX_IMAGE_EDGE_PX) {
            longestEdge /= 2
            sampleSize *= 2
        }
        return sampleSize
    }

    /**
     * 把位图编码为 JPEG 写入目标文件。
     *
     * 这里**不回收**位图：回收职责统一交给 [importSingle] 的 `finally` 块。
     * 早期版本在此处 recycle，一旦后续步骤抛异常或压缩失败，调用方持有的引用已被回收，
     * 会引发 `IllegalStateException: Cannot draw recycled bitmap`。
     */
    private fun writeBitmap(bitmap: Bitmap, target: File) {
        target.outputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                throw IOException("Failed to encode bitmap to ${target.name}")
            }
        }
    }

    /** 在照片目录内创建一个唯一命名的临时文件，目录不存在时自动建立。 */
    private fun createPhotoFile(): File {
        val directory = photoDirectory()
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create photo directory: ${directory.absolutePath}"
        }
        return File.createTempFile(TEMP_PREFIX, ".jpg", directory)
    }

    private fun photoDirectory(): File = File(context.filesDir, PHOTO_DIR_NAME)
}
