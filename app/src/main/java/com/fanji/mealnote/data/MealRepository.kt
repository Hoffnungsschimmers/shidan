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
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

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

/** 就餐人数上限：再大的桌也少见，越界多半是误输入。 */
internal const val MAX_PERSON_COUNT = 20

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

    /**
     * 读取某餐厅全部用餐照片的路径，供「从用餐照片选封面」使用。
     *
     * 返回的顺序为「最近吃的在前、组内按 sortOrder」，与用户挑封面的直觉一致。
     */
    suspend fun getPhotoPathsForRestaurant(restaurantId: Long): List<String> =
        mealDao.getPhotoPathsForRestaurant(restaurantId)

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
                    // 旧封面可能同时是某条用餐记录的照片（用户把它设成了封面），
                    // 由守卫决定是否真的删除。
                    deleteUnreferencedFiles(listOf(old))
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
     * @param amountMinorUnits 入账金额（分），可空 = 「未记金额」。由表单解析或用户手输，
     *   非正数一律落库为 null（账本里不存在负支出，0 视为未记录，免费餐写备注）。
     * @param personCount 就餐人数，越界值裁剪到 [1, MAX_PERSON_COUNT]。
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
        amountMinorUnits: Long? = null,
        personCount: Int = 1,
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
                        amountMinorUnits = amountMinorUnits?.takeIf { it > 0 },
                        personCount = personCount.coerceIn(1, MAX_PERSON_COUNT),
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
                recordId
            }
        }
        return when (outcome) {
            is MealResult.Failure -> outcome
            is MealResult.Success -> {
                MealResult.Success(outcome.value)
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
        amountMinorUnits: Long? = null,
        personCount: Int = 1,
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
                        amountMinorUnits = amountMinorUnits?.takeIf { it > 0 },
                        personCount = personCount.coerceIn(1, MAX_PERSON_COUNT),
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
                previousPaths.filterNot(acceptedPhotos::contains)
            }
        }
        return when (outcome) {
            is MealResult.Failure -> outcome
            is MealResult.Success -> {
                val stalePhotos = outcome.value
                // 事务已提交：此时回收用户替换掉的图片不会破坏一致性。
                deleteUnreferencedFiles(stalePhotos)
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
        // 若某张照片同时被店铺封面引用，守卫会保留它。
        deleteUnreferencedFiles(photoPaths)
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
        deleteUnreferencedFiles(ownedFiles)
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
        // 排除集合 = 数据库引用的全部文件：用餐照片 + 餐厅封面。
        // 注意调用仍要求「没有正在编辑的表单」：未提交的图片不在数据库内，
        // 若此时清理会误删用户正在编辑的照片（调用方需确认，通常由设置页单独入口触发）。
        val referenced = buildSet {
            addAll(mealDao.getAllPhotoPaths())
            addAll(mealDao.getRestaurantCoverPaths())
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
        // 守卫会跳过任何仍被新数据引用的路径（正常情况下不会发生，作为兜底）。
        deleteUnreferencedFiles(obsoleteFiles)
        Unit
    }

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

    // ---------------------------------------------------------------- 待回收图片
    //
    // 历史上这里曾有一套「表单登记待回收图片」的暂存区（track/releasePendingPhotos）：
    // 但全仓搜索确认没有任何调用方登记过，四个表单的图片都由各自 ViewModel 的
    // onCleared / removePhoto 即时回收。此后 add/update 在事务内消费它也只会拿到空列表，
    // 却让读者误以为“保存成功会顺带回收某批文件”。与其留着制造错误的安全感，
    // 不如删掉，回收逻辑只看事务内收集到的真实路径。

    /**
     * 回收磁盘文件，**跳过仍被任何数据库行引用的路径**。
     *
     * ## 为什么需要这层守卫
     *
     * 早期版本假定「一个文件只被一处引用」：封面只被 `restaurants.recommendationPhotoPath`
     * 引用，用餐照片只被 `photos.filePath` 引用。但「把某张用餐照片设为封面」这个功能
     * 会让同一个文件同时被两处引用，此后所有删除路径都会误删另一处仍在用的文件：
     *
     * - 换封面时删掉旧封面 → 那条用餐记录的照片没了；
     * - 删用餐记录时删掉它的照片 → 店铺封面没了；
     * - 取消编辑时回收「非原始封面」的图 → 用餐照片没了。
     *
     * 与其在三处分别打补丁（漏一处就是数据丢失），不如把不变式统一为
     * **「磁盘文件只在没有任何数据库行引用它时才允许删除」**，让所有删除路径都走这里。
     *
     * 代价是每次删除都要读一次全表路径集合。对本应用的数据量（个人使用，数百张图）
     * 可以忽略；若将来数据量显著增长，再改成按路径精确查询。
     *
     * 必须在**事务提交之后**调用：在事务内查询引用集合会读到未提交的中间状态。
     */
    private suspend fun deleteUnreferencedFiles(paths: Collection<String>) {
        val candidates = paths.filter(String::isNotBlank).distinct()
        if (candidates.isEmpty()) return
        val referenced = buildSet {
            addAll(mealDao.getAllPhotoPaths())
            addAll(mealDao.getRestaurantCoverPaths())
        }
        val deletable = candidates.filterNot(referenced::contains)
        if (deletable.isNotEmpty()) photoStore.deleteOwnedPhotos(deletable)
    }

    /**
     * 把底层异常翻译为领域错误。
     *
     * 只捕获可预期的业务异常与 [IOException]；其他异常（如空指针、SQL 约束错误）继续向上
     * 抛出，以便在开发与灰度阶段暴露真实缺陷，而不是被统一伪装成“保存失败”。
     *
     * 协程取消是控制流而非失败：`block` 内若因页面退出被取消，必须原样抛出，
     * 否则调用方的 `viewModelScope` 收不到取消信号，下一个挂起点还会继续执行。
     */
    private inline fun <T> runCatchingDb(block: () -> T): MealResult<T> = try {
        MealResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: RestaurantMissingException) {
        MealResult.Failure(MealError.RestaurantNotFound)
    } catch (error: RecordMissingException) {
        MealResult.Failure(MealError.RecordNotFound)
    } catch (error: IOException) {
        MealResult.Failure(MealError.PhotoIoFailure)
    } catch (error: Exception) {
        MealResult.Failure(MealError.DatabaseFailure(error))
    }
}

/** 目标餐厅不存在，用于区分“记录已被删除”这一业务分支与真正的程序错误。 */
internal class RestaurantMissingException(val restaurantId: Long) :
    IllegalStateException("Restaurant $restaurantId does not exist")

/** 目标用餐记录不存在。 */
internal class RecordMissingException(val recordId: Long) :
    IllegalStateException("Dining record $recordId does not exist")
