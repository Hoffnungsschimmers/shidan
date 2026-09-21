package com.fanji.mealnote.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.fanji.mealnote.data.MAX_PERSON_COUNT
import com.fanji.mealnote.data.MealError
import com.fanji.mealnote.data.MealRepository
import com.fanji.mealnote.data.MealResult
import com.fanji.mealnote.data.PhotoStore
import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.PhotoEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.Verdict
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** 导出结果摘要，用于向用户展示“备份了多少内容”。 */
data class BackupSummary(
    val restaurantCount: Int,
    val recordCount: Int,
    val photoCount: Int,
    val fileName: String,
)

/** 导入结果摘要。 */
data class RestoreSummary(
    val restaurantCount: Int,
    val recordCount: Int,
    val photoCount: Int,
    val skippedPhotos: Int,
)

/**
 * 备份包的读写实现。
 *
 * 包结构（见 `PROJECT_PLAN.md` 8.3）：
 * ```
 * mealnote-backup-YYYYMMDD-HHmm.zip
 * ├─ manifest.json        格式版本、应用版本、导出时间、条目统计
 * ├─ restaurants.json
 * ├─ dining_records.json
 * ├─ photos.json
 * └─ photos/<name>.jpg
 * ```
 *
 * 三条关键约束：
 * 1. **JSON 使用平台 `org.json`**：项目对依赖版本有严格锁定策略，为此引入
 *    kotlinx-serialization 需要额外的编译器插件与版本验证，而备份结构简单、
 *    `org.json` 在所有受支持的 API 级别上均可用且不需要 keep 规则。
 * 2. **导入是全量替换**：先清空业务表再写入，避免主键冲突与半套数据并存。
 *    所有写入在同一事务内完成，任一步失败整体回滚。
 * 3. **照片文件先落盘、后写数据库**：若先写数据库再复制文件，复制失败会留下
 *    指向不存在文件的记录（破图）；反之失败只会留下可由 `cleanupOrphanPhotos`
 *    回收的孤儿文件。
 */
@Singleton
class BackupStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: MealRepository,
    private val photoStore: PhotoStore,
) {
    /**
     * 导出全部数据到 [target] 指向的位置。
     *
     * 照片以**流式复制**写入 ZIP，不整份读入内存：单张照片最大 2048px，多张累积后
     * 一次性读入容易 OOM。
     */
    suspend fun export(target: Uri): MealResult<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val restaurants = repository.getRestaurantsForBackup()
            val records = repository.getDiningRecordsForBackup()
            val photos = repository.getPhotosForBackup()

            val fileName = defaultFileName()
            val output = context.contentResolver.openOutputStream(target, "wt")
                ?: return@withContext MealResult.Failure(MealError.StorageFull)

            output.use { stream -> writeArchive(stream, restaurants, records, photos, fileName) }

            MealResult.Success(
                BackupSummary(
                    restaurantCount = restaurants.size,
                    recordCount = records.size,
                    photoCount = photos.size,
                    fileName = fileName,
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            MealResult.Failure(MealError.PhotoIoFailure)
        } catch (error: Exception) {
            MealResult.Failure(MealError.DatabaseFailure(error))
        }
    }

    /**
     * 从 [source] 恢复数据，**全量替换**当前内容。
     *
     * 调用方必须在此之前获得用户明确同意（界面需提示“现有数据将被覆盖”）。
     *
     * 安全前提：从 SAF 拿到的 URI 指向用户选择的任意文件。解析 ZIP 之前先校验
     * 其声明的长度与扩展名，体积过大或扩展名明显不是备份包时直接拒绝，
     * 避免把超大文件读入解压流程。
     */
    suspend fun restore(source: Uri): MealResult<RestoreSummary> = withContext(Dispatchers.IO) {
        // 先做轻量校验：超大文件或扩展名明显不符时直接拒绝，不进入解压流程。
        // 文件长度走 ContentResolver 查询而非直接 open，避免对超大输入做无谓 IO。
        val declaredSize = context.contentResolver.queryDisplaySize(source)
        if (declaredSize != null && declaredSize > MAX_PICKED_FILE_BYTES) {
            return@withContext MealResult.Failure(MealError.InvalidInput(FILE_TOO_LARGE))
        }
        val displayName = context.contentResolver.queryDisplayName(source).orEmpty()
        // 只有“明确带有非 zip 扩展名”时才拒绝：无扩展名的文件放行到后续的内容校验，
        // 避免因文件管理器未返回扩展名而误杀合法备份。
        val extension = displayName.substringAfterLast('.', "")
        if (extension.isNotEmpty() && !extension.equals("zip", ignoreCase = true)) {
            return@withContext MealResult.Failure(MealError.InvalidInput(INVALID_ARCHIVE))
        }

        val stagingDir = File(context.cacheDir, STAGING_DIR_NAME)
        try {
            stagingDir.deleteRecursively()
            check(stagingDir.mkdirs()) { "Unable to create staging dir" }

            val input = context.contentResolver.openInputStream(source)
                ?: return@withContext MealResult.Failure(MealError.PhotoIoFailure)

            val parsed = input.use { stream -> readArchive(stream, stagingDir) }
                ?: return@withContext MealResult.Failure(MealError.InvalidInput(INVALID_ARCHIVE))

            // 复制到应用私有照片目录。失败的图片被跳过而不是整体失败：
            // 单张损坏不应该让整份备份无法恢复。
            // 键为包内文件名，值为复制后的私有目录绝对路径。
            var skipped = 0
            val resolvedPaths = HashMap<String, String>()
            stagingDir.listFiles().orEmpty().forEach { staged ->
                val copied = photoStore.importFile(staged)
                if (copied == null) skipped++ else resolvedPaths[staged.name] = copied
            }

            val outcome = repository.replaceAllData(
                restaurants = parsed.restaurants,
                records = parsed.records,
                photos = parsed.photos.filter { resolvedPaths.containsKey(it.filePath) },
                resolvedPaths = resolvedPaths,
            )

            when (outcome) {
                is MealResult.Failure -> outcome
                is MealResult.Success -> MealResult.Success(
                    RestoreSummary(
                        restaurantCount = parsed.restaurants.size,
                        recordCount = parsed.records.size,
                        photoCount = parsed.photos.count { resolvedPaths.containsKey(it.filePath) },
                        skippedPhotos = skipped,
                    )
                )
            }
        } catch (_: IOException) {
            MealResult.Failure(MealError.PhotoIoFailure)
        } catch (_: org.json.JSONException) {
            MealResult.Failure(MealError.InvalidInput(INVALID_ARCHIVE))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            MealResult.Failure(MealError.DatabaseFailure(error))
        } finally {
            // 无论成功失败都清理暂存目录，避免占用缓存空间。
            // 协程取消同样会经过这里，暂存清理不受影响。
            stagingDir.deleteRecursively()
        }
    }

    /** 生成默认备份文件名，形如 `mealnote-backup-20260912-1830.zip`。 */
    fun defaultFileName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        return "mealnote-backup-$stamp.zip"
    }

    // ------------------------------------------------------------ 写入实现

    private fun writeArchive(
        output: OutputStream,
        restaurants: List<RestaurantEntity>,
        records: List<DiningRecordEntity>,
        photos: List<PhotoEntity>,
        fileName: String,
    ) {
        ZipOutputStream(output.buffered()).use { zip ->
            // photos 表的 filePath 包含导出设备的私有目录前缀，导入方无法使用。
            // 因此改用「记录 id 与序号」的稳定命名，并把文件名写入 photos.json。
            val fileNames = photos.associate { photo ->
                photo.id to stablePhotoName(photo)
            }
            val writtenPhotoNames = fileNames.values.toMutableSet()

            writeEntry(zip, MANIFEST_ENTRY, manifestJson(restaurants, records, photos, fileName))
            writeEntry(zip, RESTAURANTS_ENTRY, restaurantsJson(restaurants))
            writeEntry(zip, RECORDS_ENTRY, recordsJson(records))
            writeEntry(zip, PHOTOS_ENTRY, photosJson(photos, fileNames))

            photos.forEach { photo ->
                // fileNames 由同一个 photos 列表构建，getValue 必然命中；
                // 若未来改为从别处取名字，这里需要重新考虑缺失分支。
                val name = fileNames.getValue(photo.id)
                val file = File(photo.filePath)
                if (!file.isFile) return@forEach
                zip.putNextEntry(ZipEntry("$PHOTO_DIR/$name"))
                try {
                    file.inputStream().use { it.copyTo(zip) }
                } finally {
                    zip.closeEntry()
                }
            }

            // 封面文件单独打包：命名与 restaurants.json 中的 recommendationPhotoName 一致。
            restaurants.forEach { restaurant ->
                val coverName = if (restaurant.recommendationPhotoPath.isBlank()) {
                    ""
                } else {
                    coverFileName(restaurant)
                }
                if (coverName.isEmpty()) return@forEach
                val file = File(restaurant.recommendationPhotoPath)
                if (!file.isFile) return@forEach
                // 封面与用餐照片命名空间隔离（cover{id}.jpg vs record{id}_{sort}.jpg），
                // 即使封面复用了某张用餐照片的文件字节，这里仍需以封面名另存一份，
                // 否则导入侧按封面名找不到文件而退化为无封面。
                if (!writtenPhotoNames.add(coverName)) return@forEach
                zip.putNextEntry(ZipEntry("$PHOTO_DIR/$coverName"))
                try {
                    file.inputStream().use { it.copyTo(zip) }
                } finally {
                    zip.closeEntry()
                }
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, json: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun manifestJson(
        restaurants: List<RestaurantEntity>,
        records: List<DiningRecordEntity>,
        photos: List<PhotoEntity>,
        fileName: String,
    ): String = JSONObject().apply {
        put("formatVersion", BackupFormat.FORMAT_VERSION)
        put("appVersionName", appVersionName())
        put("exportedAt", System.currentTimeMillis())
        put("archiveName", fileName)
        put("restaurantCount", restaurants.size)
        put("diningRecordCount", records.size)
        put("photoCount", photos.size)
    }.toString(2)

    private fun restaurantsJson(restaurants: List<RestaurantEntity>): String {
        val array = JSONArray()
        // 封面文件名必须与实际写入 ZIP 的照片条目一一对应：导出时只打包
        // 确实存在于磁盘的封面文件，缺失的不再写入文件名，避免导入侧
        // 期待一个包内根本不存在的文件。
        val existingCovers = restaurants
            .mapNotNull { restaurant ->
                restaurant.recommendationPhotoPath
                    .takeIf(String::isNotBlank)
                    ?.let { File(it) }
                    ?.takeIf { it.isFile }
                    ?.absolutePath
            }
            .toSet()
        restaurants.forEach { restaurant ->
            // 历史遗留字段（city / cuisine / tags / priceHint / sourceUrl / sourceNote）
            // 明确不导出：它们已从产品交互中移除，导出只会让备份文件携带无意义数据。
            val coverName = if (restaurant.recommendationPhotoPath in existingCovers) {
                coverFileName(restaurant)
            } else {
                ""
            }
            array.put(
                JSONObject().apply {
                    put("id", restaurant.id)
                    put("name", restaurant.name)
                    put("address", restaurant.address)
                    put("status", restaurant.status.name)
                    put("createdAt", restaurant.createdAt)
                    put("updatedAt", restaurant.updatedAt)
                    put("recommendationPhotoName", coverName)
                }
            )
        }
        return array.toString(2)
    }

    private fun recordsJson(records: List<DiningRecordEntity>): String {
        val array = JSONArray()
        records.forEach { record ->
            array.put(
                JSONObject().apply {
                    put("id", record.id)
                    put("restaurantId", record.restaurantId)
                    put("eatenAt", record.eatenAt)
                    put("verdict", record.verdict.name)
                    put("dishes", record.dishes)
                    // v3 新增。旧版应用读取本包时会忽略该字段（org.json 不校验未知键），
                    // 因此不需要提升 FORMAT_VERSION。
                    put("priceText", record.priceText)
                    // v4 新增：入账金额（分）与人数。金额为空时**不写键**，
                    // 比写 null 更干净，旧版读取时也天然忽略。
                    record.amountMinorUnits?.let { put("amountMinorUnits", it) }
                    put("personCount", record.personCount)
                    put("note", record.note)
                    put("createdAt", record.createdAt)
                }
            )
        }
        return array.toString(2)
    }

    private fun photosJson(photos: List<PhotoEntity>, fileNames: Map<Long, String>): String {
        val array = JSONArray()
        photos.forEach { photo ->
            val name = fileNames[photo.id] ?: return@forEach
            array.put(
                JSONObject().apply {
                    put("id", photo.id)
                    put("diningRecordId", photo.diningRecordId)
                    put("fileName", name)
                    put("sortOrder", photo.sortOrder)
                }
            )
        }
        return array.toString(2)
    }

    /**
     * 为照片生成稳定文件名。
     *
     * 使用「记录 id + 序号」而非原文件名：原文件名由 `File.createTempFile` 生成的随机串，
     * 不携带业务含义，且无法保证跨设备唯一。记录 id 唯一，因此该组合天然不冲突。
     */
    private fun stablePhotoName(photo: PhotoEntity): String =
        "record${photo.diningRecordId}_${photo.sortOrder}.jpg"

    /** 封面文件名：以 `cover<餐厅id>.jpg` 命名，与用餐照片命名空间隔离。 */
    private fun coverFileName(restaurant: RestaurantEntity): String =
        if (restaurant.recommendationPhotoPath.isBlank()) "" else "cover${restaurant.id}.jpg"

    // ------------------------------------------------------------ 读取实现

    /** 解析后的备份内容；照片正文已解压到 [stagingDir]。 */
    private data class ParsedArchive(
        val restaurants: List<RestaurantEntity>,
        val records: List<DiningRecordEntity>,
        val photos: List<PhotoEntity>,
    )

    /**
     * 解析 ZIP。
     *
     * 防御性设计：
     * - 限制单个条目与总解压体积，防止构造出的高压缩比文件耗尽存储；
     * - 只解压 `photos/` 前缀下的条目，其余一律忽略，避免路径穿越写出到暂存目录之外；
     * - 校验格式版本，拒绝来自更高版本的备份。
     */
    private fun readArchive(input: InputStream, stagingDir: File): ParsedArchive? {
        var manifest: String? = null
        var restaurantsJson: String? = null
        var recordsJson: String? = null
        var photosJson: String? = null
        val photoFiles = mutableSetOf<String>()
        var totalBytes = 0L

        ZipInputStream(input.buffered()).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == MANIFEST_ENTRY -> manifest = zip.readTextCapped(MAX_JSON_BYTES)
                    name == RESTAURANTS_ENTRY -> restaurantsJson = zip.readTextCapped(MAX_JSON_BYTES)
                    name == RECORDS_ENTRY -> recordsJson = zip.readTextCapped(MAX_JSON_BYTES)
                    name == PHOTOS_ENTRY -> photosJson = zip.readTextCapped(MAX_JSON_BYTES)
                    name.startsWith("$PHOTO_DIR/") && !entry.isDirectory -> {
                        // 只取文件名部分：`photos/../../etc/passwd` 这类条目会被规范化为
                        // 单个文件名，无法逃出暂存目录。
                        val fileName = File(name).name
                        if (fileName.isNotEmpty() && fileName.length <= MAX_NAME_LENGTH) {
                            val target = File(stagingDir, fileName)
                            target.outputStream().use { out ->
                                totalBytes += zip.copyToCapped(out, MAX_PHOTO_BYTES)
                            }
                            if (totalBytes > MAX_TOTAL_BYTES) {
                                throw IOException("Backup exceeds size limit")
                            }
                            photoFiles += fileName
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifestJson = manifest ?: return null
        val version = JSONObject(manifestJson).optInt("formatVersion", -1)
        if (version <= 0 || version > BackupFormat.FORMAT_VERSION) return null

        val restaurants = parseRestaurants(restaurantsJson ?: return null, photoFiles)
        val records = parseRecords(recordsJson ?: return null)
        val photos = parsePhotos(photosJson ?: return null)
        return ParsedArchive(restaurants, records, photos)
    }

    private fun parseRestaurants(json: String, availableFiles: Set<String>): List<RestaurantEntity> {
        val array = JSONArray(json)
        val list = ArrayList<RestaurantEntity>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val coverName = item.optString("recommendationPhotoName")
            // 封面路径此处暂存「包内文件名」，由 replaceAllData 解析为新的私有路径；
            // 若文件名不在包内（封面文件缺失），退化为无封面而不是指向不存在的文件。
            list += RestaurantEntity(
                id = item.getLong("id"),
                name = item.getString("name"),
                address = item.optString("address"),
                recommendationPhotoPath = if (coverName in availableFiles) coverName else "",
                status = item.optString("status")
                    .let { name -> RestaurantStatus.entries.firstOrNull { it.name == name } }
                    ?: RestaurantStatus.WANT_TO_EAT,
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        return list
    }

    private fun parseRecords(json: String): List<DiningRecordEntity> {
        val array = JSONArray(json)
        val list = ArrayList<DiningRecordEntity>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            list += DiningRecordEntity(
                id = item.getLong("id"),
                restaurantId = item.getLong("restaurantId"),
                eatenAt = item.optLong("eatenAt", System.currentTimeMillis()),
                verdict = item.optString("verdict")
                    .let { name -> Verdict.entries.firstOrNull { it.name == name } }
                    ?: Verdict.MEH,
                dishes = item.optString("dishes"),
                // v1/v2 的备份包没有该字段，optString 返回空串，正好等于「未填写」的语义。
                priceText = item.optString("priceText"),
                // v4 字段：v3 及更早的包没有 → 金额为空（未记金额），人数按 1 计。
                amountMinorUnits = (item.opt("amountMinorUnits") as? Number)
                    ?.toLong()?.takeIf { it > 0 },
                personCount = item.optInt("personCount", 1).coerceIn(1, MAX_PERSON_COUNT),
                note = item.optString("note"),
                createdAt = item.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        return list
    }

    private fun parsePhotos(json: String): List<PhotoEntity> {
        val array = JSONArray(json)
        val list = ArrayList<PhotoEntity>(array.length())
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val fileName = item.optString("fileName")
            if (fileName.isBlank()) continue
            list += PhotoEntity(
                id = item.getLong("id"),
                diningRecordId = item.getLong("diningRecordId"),
                filePath = fileName,
                sortOrder = item.optInt("sortOrder", 0),
            )
        }
        return list
    }

    /** 读取不超过 [limit] 字节的文本，超限直接抛错，避免恶意 ZIP 撑爆内存。 */
    private fun ZipInputStream.readTextCapped(limit: Long): String {
        val buffer = java.io.ByteArrayOutputStream()
        copyToCapped(buffer, limit)
        return buffer.toString(Charsets.UTF_8.name())
    }

    /** 复制当前条目内容，返回写入字节数；超过 [limit] 抛 [IOException]。 */
    private fun java.io.InputStream.copyToCapped(output: OutputStream, limit: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read == 0) continue
            written += read
            if (written > limit) throw IOException("Entry exceeds size limit")
            output.write(buffer, 0, read)
        }
        return written
    }

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val RESTAURANTS_ENTRY = "restaurants.json"
        const val RECORDS_ENTRY = "dining_records.json"
        const val PHOTOS_ENTRY = "photos.json"
        const val PHOTO_DIR = "photos"
        const val STAGING_DIR_NAME = "backup_staging"

        /** 单个 JSON 条目上限，正常数据远小于此值。 */
        const val MAX_JSON_BYTES = 32L * 1024 * 1024
        /** 单张照片上限，2048px JPEG 通常在 2MB 以内。 */
        const val MAX_PHOTO_BYTES = 32L * 1024 * 1024
        /** 整个备份解压后的总上限。 */
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_NAME_LENGTH = 128

        /**
         * 用户可选择的备份文件上限。
         *
         * ZIP 本身有压缩，磁盘上的包通常远小于解压后的总量；
         * 该上限只拦截“明显不可能是备份包”的超大文件，正常备份不会触及。
         */
        const val MAX_PICKED_FILE_BYTES = 512L * 1024 * 1024

        const val INVALID_ARCHIVE = "备份文件格式不正确或已损坏"
        const val FILE_TOO_LARGE = "备份文件过大，无法导入"
    }

    /**
     * 查询 SAF 文档的显示文件名。
     *
     * 不同 ROM 的列实现有差异，任何异常都视为“查不到”而非失败：
     * 查不到只意味着跳过扩展名校验，不影响后续的内容校验。
     * 列索引按列名解析而非硬编码 0：部分实现可能返回额外列或打乱顺序。
     */
    private fun ContentResolver.queryDisplayName(uri: Uri): String? = runCatching {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
        }
    }.getOrNull()

    /** 查询 SAF 文档的声明长度；查不到时返回 null，由后续的解压上限继续兜底。 */
    private fun ContentResolver.queryDisplaySize(uri: Uri): Long? = runCatching {
        query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index < 0 || cursor.isNull(index)) {
                return@use null
            }
            cursor.getLong(index).takeIf { it >= 0 }
        }
    }.getOrNull()
}
