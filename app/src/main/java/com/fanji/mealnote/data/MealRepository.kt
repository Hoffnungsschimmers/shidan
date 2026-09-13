package com.fanji.mealnote.data

import androidx.room.withTransaction
import com.fanji.mealnote.data.local.AppDatabase
import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.DiningRecordWithPhotos
import com.fanji.mealnote.data.local.MealDao
import com.fanji.mealnote.data.local.PhotoEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.RestaurantWithRecords
import com.fanji.mealnote.data.local.Verdict
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 单次用餐记录允许的照片上限，与表单中的 9 张限制保持一致。 */
internal const val MAX_PHOTOS_PER_RECORD = 9

/**
 * 「花费」字段的长度上限。
 *
 * 该字段是自由文本（「人均 60 左右」这类表述无法用整数表达），因此不校验格式，
 * 只限制长度：一是防止用户在输入框里粘贴整段文字破坏列表排版，
 * 二是避免异常输入被写入备份包后在导入侧撑大 JSON。
 */
internal const val MAX_PRICE_TEXT_LENGTH = 40

/** 餐品名称与备注的长度上限，理由同 [MAX_PRICE_TEXT_LENGTH]。 */
internal const val MAX_DISHES_LENGTH = 500
internal const val MAX_NOTE_LENGTH = 2_000

/**
 * 应用唯一的业务数据入口，负责跨表一致性与照片文件生命周期。
 *
 * 分层职责：
 * - [MealDao] 只做 SQL 与对象映射；
 * - 本类负责输入校验、事务边界，以及“数据库行”与“磁盘文件”的同步回收；
 * - ViewModel 只消费 [MealResult]，不参与业务一致性判断。
 *
 * 关键约定 —— **先提交事务，再删除文件**：
 * 若顺序颠倒，事务回滚后数据库仍引用着已被删除的文件，会形成不可修复的坏记录；
 * 反之最坏结果是留下一个孤儿文件，可由 [cleanupOrphanPhotos] 安全回收。
 *
 * 并发约定：本类是 `@Singleton`，会被多个 ViewModel 的协程同时调用，因此
 * - 所有共享可变状态必须线程安全（见 [_pendingPhotoCleanup]）；
 * - 文件系统与数据库操作不使用 `synchronized`，避免持锁做 IO 导致主线程阻塞。
 */
@Singleton
class MealRepository @Inject constructor(
    private val database: AppDatabase,
    private val mealDao: MealDao,
    private val photoStore: PhotoStore,
) {
    fun observeRestaurants(): Flow<List<RestaurantEntity>> = mealDao.observeRestaurants()

    /** 按用餐时间倒序观察全部用餐记录，供「足迹」时间线使用。 */
    fun observeAllDiningRecords(): Flow<List<DiningRecordEntity>> = mealDao.observeAllDiningRecords()

    /** 观察全部照片行，供时间线组装各次用餐的图片。 */
    fun observeAllPhotos(): Flow<List<PhotoEntity>> = mealDao.observeAllPhotos()

    fun observeRestaurantWithRecords(id: Long): Flow<RestaurantWithRecords?> =
        mealDao.observeRestaurantWithRecords(id)

    /**
     * 一次性读取餐厅详情，供编辑表单初始化使用。
     *
     * 编辑页只需要“进入时的初始值”，不需要持续订阅：若订阅，用户在表单中尚未保存的
     * 修改会被上游发射覆盖，出现输入被回滚的现象。
     */
    suspend fun getRestaurant(id: Long): RestaurantEntity? = mealDao.getRestaurant(id)

    /** 一次性读取某条用餐记录及其照片，供编辑表单初始化使用。 */
    suspend fun getDiningRecord(recordId: Long): DiningRecordWithPhotos? {
        val record = mealDao.getDiningRecord(recordId) ?: return null
        return DiningRecordWithPhotos(record, mealDao.getPhotosForRecord(recordId))
    }

    /**
     * 新建餐厅。
     *
     * @param name 店名，唯一必填项；空白输入返回 [MealError.InvalidInput]。
     * @param address 地址，选填。
     * @param photoPaths 已导入私有目录的图片路径，仅保留第一张作为封面，其余不被引用。
     */
    suspend fun addRestaurant(
        name: String,
        address: String,
        photoPaths: List<String>,
    ): MealResult<Long> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return MealResult.Failure(MealError.InvalidInput("餐厅名称不能为空"))
        }
        return runCatchingDb {
            val now = System.currentTimeMillis()
            mealDao.insertRestaurant(
                RestaurantEntity(
                    name = trimmedName,
                    address = address.trim(),
                    recommendationPhotoPath = photoPaths.firstOrNull().orEmpty(),
                    status = RestaurantStatus.WANT_TO_EAT,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    /**
     * 更新餐厅资料。
     *
     * 当封面被替换时回收旧文件，避免用户多次更换封面后残留无用图片。
     * 注意删除发生在事务提交之后：若事务失败，旧封面仍然是数据库引用的正式文件，
     * 提前删除会导致图片丢失。
     */
    suspend fun updateRestaurant(restaurant: RestaurantEntity): MealResult<Unit> {
        val previousCover = runCatchingDb {
            database.withTransaction {
                val previous = mealDao.getRestaurant(restaurant.id)
                    ?: throw RestaurantMissingException(restaurant.id)
                mealDao.updateRestaurant(restaurant.copy(updatedAt = System.currentTimeMillis()))
                previous.recommendationPhotoPath
            }
        }
        return when (previousCover) {
            is MealResult.Failure -> previousCover
            is MealResult.Success -> {
                val old = previousCover.value
                if (old.isNotBlank() && old != restaurant.recommendationPhotoPath) {
                    photoStore.deleteOwnedPhoto(old)
                }
                MealResult.Success(Unit)
            }
        }
    }

    /**
     * 新增一次用餐记录，并将餐厅状态推进为“已用餐”。
     *
     * 插入记录、插入照片、更新餐厅状态三步在**同一事务**内完成，任一步失败整体回滚，
     * 不会留下“有记录但状态未更新”的中间态。[MealError.RestaurantNotFound] 用于表达
     * 餐厅已被删除这一业务分支，而不是把它当作成功。
     *
     * @param priceText 花费，自由文本，选填。不做数值校验，仅裁剪长度（见 [MAX_PRICE_TEXT_LENGTH]）。
     * @return 成功时返回新记录 id。
     */
    suspend fun addDiningRecord(
        restaurantId: Long,
        eatenAt: Long,
        verdict: Verdict,
        dishes: String,
        priceText: String,
        note: String,
        photoPaths: List<String>,
    ): MealResult<Long> {
        val acceptedPhotos = photoPaths.distinct().take(MAX_PHOTOS_PER_RECORD)
        val outcome = runCatchingDb {
            database.withTransaction {
                val restaurant = mealDao.getRestaurant(restaurantId)
                    ?: throw RestaurantMissingException(restaurantId)
                val now = System.currentTimeMillis()
                val recordId = mealDao.insertDiningRecord(
                    DiningRecordEntity(
                        restaurantId = restaurantId,
                        eatenAt = eatenAt,
                        verdict = verdict,
                        dishes = dishes.normalizeText(MAX_DISHES_LENGTH),
                        priceText = priceText.normalizeText(MAX_PRICE_TEXT_LENGTH),
                        note = note.normalizeText(MAX_NOTE_LENGTH),
                        createdAt = now,
                    )
                )
                acceptedPhotos.forEachIndexed { index, path ->
                    mealDao.insertPhoto(
                        PhotoEntity(
                            diningRecordId = recordId,
                            filePath = path,
                            sortOrder = index,
                        )
                    )
                }
                mealDao.updateRestaurant(
                    restaurant.copy(status = RestaurantStatus.EATEN, updatedAt = now)
                )
                recordId to takePendingPhotos(restaurantId)
            }
        }
        return when (outcome) {
            is MealResult.Failure -> outcome
            is MealResult.Success -> {
                val (recordId, stalePhotos) = outcome.value
                // 事务已提交：此时回收用户在表单中替换掉的图片不会破坏一致性。
                photoStore.deleteOwnedPhotos(stalePhotos.filterNot(acceptedPhotos::contains))
                MealResult.Success(recordId)
            }
        }
    }

    /**
     * 更新一条已有的用餐记录，并把照片集合替换为 [photoPaths]。
     *
     * 一致性设计：
     * - 记录字段更新、照片行重建、餐厅状态同步在同一事务内完成，读者不会看到中间状态；
     * - 事务内收集“被移除的旧照片路径”，**提交成功之后**才删除磁盘文件。若顺序颠倒，
     *   事务回滚时数据库仍引用着已删除的文件，会形成不可修复的坏记录；
     * - 照片行先清空再按新顺序重建，顺带修正用户调整顺序后遗留的 `sortOrder` 空洞。
     *
     * [MealError.RecordNotFound] 与 [MealError.RestaurantNotFound] 分别表达“记录被删除”
     * 和“餐厅被删除”两种业务分支，而不是当作成功。
     */
    suspend fun updateDiningRecord(
        recordId: Long,
        eatenAt: Long,
        verdict: Verdict,
        dishes: String,
        priceText: String,
        note: String,
        photoPaths: List<String>,
    ): MealResult<Unit> {
        val acceptedPhotos = photoPaths.distinct().take(MAX_PHOTOS_PER_RECORD)
        val outcome = runCatchingDb {
            database.withTransaction {
                val record = mealDao.getDiningRecord(recordId)
                    ?: throw RecordMissingException(recordId)
                val restaurant = mealDao.getRestaurant(record.restaurantId)
                    ?: throw RestaurantMissingException(record.restaurantId)
                val previousPaths = mealDao.getPhotosForRecord(recordId).map { it.filePath }
                val now = System.currentTimeMillis()

                mealDao.updateDiningRecord(
                    record.copy(
                        eatenAt = eatenAt,
                        verdict = verdict,
                        dishes = dishes.normalizeText(MAX_DISHES_LENGTH),
                        priceText = priceText.normalizeText(MAX_PRICE_TEXT_LENGTH),
                        note = note.normalizeText(MAX_NOTE_LENGTH),
                    )
                )
                mealDao.deletePhotosForRecord(recordId)
                acceptedPhotos.forEachIndexed { index, path ->
                    mealDao.insertPhoto(
                        PhotoEntity(
                            diningRecordId = recordId,
                            filePath = path,
                            sortOrder = index,
                        )
                    )
                }
                // 编辑不会改变“是否已用餐”，但仍显式同步一次以覆盖数据被外部修改的极端情况。
                mealDao.updateRestaurant(
                    restaurant.copy(status = RestaurantStatus.EATEN, updatedAt = now)
                )
                // 返回值 = 旧路径中不再被本记录引用的部分。
                previousPaths.filterNot(acceptedPhotos::contains) to takePendingPhotos(record.restaurantId)
            }
        }
        return when (outcome) {
            is MealResult.Failure -> outcome
            is MealResult.Success -> {
                val (stalePhotos, pendingPhotos) = outcome.value
                // 事务已提交：此时回收用户替换掉的图片不会破坏一致性。
                photoStore.deleteOwnedPhotos(stalePhotos + pendingPhotos.filterNot(acceptedPhotos::contains))
                MealResult.Success(Unit)
            }
        }
    }

    /**
     * 删除一条用餐记录，并回收其全部照片文件。
     *
     * 若该餐厅因此不再有任何用餐记录，状态回退为“待探访”，使列表筛选结果与事实一致。
     * 状态同步与删除放在同一事务内，避免出现“记录已删但状态仍是已用餐”的中间态被
     * 数据库观察者捕获。
     */
    suspend fun deleteDiningRecord(recordId: Long): MealResult<Unit> = runCatchingDb {
        val photoPaths = database.withTransaction {
            val record = mealDao.getDiningRecord(recordId)
                ?: throw RecordMissingException(recordId)
            val paths = mealDao.getPhotosForRecord(recordId).map { it.filePath }
            mealDao.deleteDiningRecord(record)
            syncRestaurantStatusInTransaction(record.restaurantId)
            paths
        }
        // 事务提交后再回收文件；此时记录已不存在，删除失败也只会留下可被扫描回收的孤儿文件。
        photoStore.deleteOwnedPhotos(photoPaths)
        Unit
    }

    /**
     * 删除餐厅，级联删除其用餐记录与全部照片文件。
     *
     * 数据库外键级联只能清理行，磁盘文件必须在此显式回收，否则会长期占用用户存储。
     * 封面与用餐照片需一并收集：前者不在 `photos` 表内，仅靠关联查询会漏删。
     */
    suspend fun deleteRestaurant(restaurantId: Long): MealResult<Unit> = runCatchingDb {
        val ownedFiles = database.withTransaction {
            val restaurant = mealDao.getRestaurant(restaurantId)
                ?: throw RestaurantMissingException(restaurantId)
            val recordFiles = mealDao.getPhotoPathsForRestaurant(restaurantId)
            mealDao.deleteRestaurant(restaurant)
            recordFiles + restaurant.recommendationPhotoPath
        }
        photoStore.deleteOwnedPhotos(ownedFiles.filter(String::isNotBlank))
        Unit
    }

    /**
     * 清理数据库中已无引用、但磁盘上仍残留的照片文件。
     *
     * 用于设置页的“清理存储空间”，可回收保存流程异常中断产生的孤儿文件。
     *
     * 安全前提：**正在编辑中的表单图片不在数据库引用集合内**。因此调用方必须先确认
     * 没有未提交的表单（通常由设置页单独入口触发），否则会删掉用户正在编辑的照片。
     *
     * @return 本次清理的文件数量。
     */
    suspend fun cleanupOrphanPhotos(): Int {
        // 排除集合必须同时包含：用餐照片 + 餐厅封面 + 各表单尚未提交的图片。
        val referenced = buildSet {
            addAll(mealDao.getAllPhotoPaths())
            addAll(mealDao.getRestaurantCoverPaths())
            addAll(pendingPhotoSnapshot())
        }
        return photoStore.deleteOrphanPhotos(referenced)
    }

    /** 统计照片目录当前占用空间（字节），用于设置页展示存储用量。 */
    fun photoDirectorySizeBytes(): Long = photoStore.photoDirectorySizeBytes()

    // ---------------------------------------------------------------- 备份支持

    /** 读取全部餐厅，供备份导出使用。 */
    suspend fun getRestaurantsForBackup(): List<RestaurantEntity> = mealDao.getAllRestaurants()

    /** 读取全部用餐记录，供备份导出使用。 */
    suspend fun getDiningRecordsForBackup(): List<DiningRecordEntity> = mealDao.getAllDiningRecords()

    /** 读取全部照片行，供备份导出使用。 */
    suspend fun getPhotosForBackup(): List<PhotoEntity> = mealDao.getAllPhotos()

    /**
     * 用备份数据全量替换当前内容。
     *
     * 事务边界覆盖“清空 + 写入”全过程：
     * - 若写完一部分后失败，整体回滚，用户仍保有导入前的数据；
     * - 清空顺序为 照片 → 用餐记录 → 餐厅，与**外键依赖方向相反**。
     *   反序删除会触发外键约束失败（子表仍引用父表行）。
     *
     * 主键沿用备份中的原始 id：这样 `dining_records.restaurantId` 与
     * `photos.diningRecordId` 的关联无需重映射，也是「导出—清空—导入」数据完整率 100% 的前提。
     *
     * @param resolvedPaths 包内文件名 -> 私有目录绝对路径，用于把照片与封面还原为可用路径。
     */
    suspend fun replaceAllData(
        restaurants: List<RestaurantEntity>,
        records: List<DiningRecordEntity>,
        photos: List<PhotoEntity>,
        resolvedPaths: Map<String, String>,
    ): MealResult<Unit> = runCatchingDb {
        // 导入前先收集当前所有受管文件，事务提交后统一回收，避免旧数据长期占用存储。
        val obsoleteFiles = mealDao.getAllPhotoPaths() + mealDao.getRestaurantCoverPaths()

        database.withTransaction {
            mealDao.clearPhotos()
            mealDao.clearDiningRecords()
            mealDao.clearRestaurants()

            val validRestaurantIds = restaurants.map { it.id }.toSet()
            val validRecordIds = records
                .filter { it.restaurantId in validRestaurantIds }
                .map { it.id }
                .toSet()

            restaurants.forEach { restaurant ->
                mealDao.insertRestaurant(
                    restaurant.copy(
                        recommendationPhotoPath = resolvedPaths[restaurant.recommendationPhotoPath].orEmpty(),
                    )
                )
            }
            records.filter { it.restaurantId in validRestaurantIds }.forEach { record ->
                mealDao.insertDiningRecord(record)
            }
            photos.filter { it.diningRecordId in validRecordIds }.forEach { photo ->
                mealDao.insertPhoto(
                    photo.copy(filePath = resolvedPaths[photo.filePath] ?: return@forEach)
                )
            }
        }
        // 事务已提交：此时回收被替换掉的旧文件不会破坏一致性。
        photoStore.deleteOwnedPhotos(obsoleteFiles.filter(String::isNotBlank))
        Unit
    }

    /**
     * 登记表单中“已被替换、等待回收”的图片。
     *
     * 之所以不立即删除：用户可能反复替换后仍想保留最早那张，过早删除会让预览失效。
     * 回收时机由调用方决定 —— 保存成功时由 [addDiningRecord] 消费，
     * 放弃表单时由 [releasePendingPhotos] 消费。
     */
    fun trackPendingPhotos(formKey: Long, paths: List<String>) {
        if (paths.isEmpty()) return
        // 采用替换语义：同一表单重复登记时以最新一次为准，避免列表无限增长。
        _pendingPhotoCleanup[formKey] = paths
    }

    /**
     * 取出并清空指定表单的待回收图片。
     *
     * 使用 [ConcurrentHashMap.remove] 保证“取出即清空”的原子性：两个协程同时调用时，
     * 只有一个能拿到列表，另一个得到空列表，从而避免同一批文件被删除两次。
     */
    fun releasePendingPhotos(formKey: Long): List<String> =
        _pendingPhotoCleanup.remove(formKey).orEmpty()

    /**
     * 把餐厅状态同步为“已用餐 / 待探访”。
     *
     * 必须在事务内调用：与删除操作同属一次原子提交。
     */
    private suspend fun syncRestaurantStatusInTransaction(restaurantId: Long) {
        val restaurant = mealDao.getRestaurant(restaurantId) ?: return
        val expected = if (mealDao.countDiningRecords(restaurantId) > 0) {
            RestaurantStatus.EATEN
        } else {
            RestaurantStatus.WANT_TO_EAT
        }
        if (restaurant.status != expected) {
            mealDao.updateRestaurant(
                restaurant.copy(status = expected, updatedAt = System.currentTimeMillis())
            )
        }
    }

    /** 原子地取出指定表单的待回收图片；供事务提交后清理使用。 */
    private fun takePendingPhotos(formKey: Long): List<String> = releasePendingPhotos(formKey)

    /**
     * 自由文本字段的入库规范化：去首尾空白 + 截断到 [limit]。
     *
     * 截断而非报错：超长输入通常来自粘贴，静默丢弃尾部比弹出错误更符合预期，
     * 也不会让用户已经填好的其它字段白填。长度上限由各字段常量约束。
     */
    private fun String.normalizeText(limit: Int): String = trim().take(limit)

    /** 当前所有表单未提交图片的快照，用于 [cleanupOrphanPhotos] 构建排除集合。 */
    private fun pendingPhotoSnapshot(): List<String> =
        _pendingPhotoCleanup.values.flatten()

    /** 当前挂起的待清理表单数量，供测试与诊断使用。 */
    internal fun pendingCleanupCount(): Int = _pendingPhotoCleanup.size

    /**
     * 把底层异常翻译为领域错误。
     *
     * 只捕获可预期的业务异常与 [IOException]；其他异常（如空指针、SQL 约束错误）继续向上
     * 抛出，以便在开发与灰度阶段暴露真实缺陷，而不是被统一伪装成“保存失败”。
     */
    private inline fun <T> runCatchingDb(block: () -> T): MealResult<T> = try {
        MealResult.Success(block())
    } catch (error: RestaurantMissingException) {
        MealResult.Failure(MealError.RestaurantNotFound)
    } catch (error: RecordMissingException) {
        MealResult.Failure(MealError.RecordNotFound)
    } catch (error: IOException) {
        MealResult.Failure(MealError.PhotoIoFailure)
    } catch (error: Exception) {
        MealResult.Failure(MealError.DatabaseFailure(error))
    }

    /**
     * 表单 key -> 已被替换但尚未回收的图片路径。
     *
     * 必须使用线程安全实现：本类是单例，多个 ViewModel 的协程可能并发读写。
     * 早期版本使用普通 `mutableMapOf`，并发 `put/remove` 可能导致 `ConcurrentModificationException`
     * 或丢失条目（进而留下孤儿文件、或误删正在使用的图片）。
     */
    private val _pendingPhotoCleanup = ConcurrentHashMap<Long, List<String>>()
}

/** 目标餐厅不存在，用于区分“记录已被删除”这一业务分支与真正的程序错误。 */
internal class RestaurantMissingException(val restaurantId: Long) :
    IllegalStateException("Restaurant $restaurantId does not exist")

/** 目标用餐记录不存在。 */
internal class RecordMissingException(val recordId: Long) :
    IllegalStateException("Dining record $recordId does not exist")
