package com.fanji.mealnote.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 餐厅、用餐记录与照片的数据访问接口。
 *
 * 约定：
 * - DAO 只负责 SQL 与对象映射，不含业务校验；
 * - 需要在同一事务内完成的一致性操作放在 `MealRepository`；
 * - 返回 [Flow] 的查询会自动追踪所涉表的变化。
 */
@Dao
interface MealDao {
    // ---------------------------------------------------------------- 餐厅

    @Insert
    suspend fun insertRestaurant(restaurant: RestaurantEntity): Long

    @Update
    suspend fun updateRestaurant(restaurant: RestaurantEntity)

    @Delete
    suspend fun deleteRestaurant(restaurant: RestaurantEntity)

    /** 按最近修改时间倒序观察全部餐厅（含已用餐）。 */
    @Query("SELECT * FROM restaurants ORDER BY updatedAt DESC")
    fun observeRestaurants(): Flow<List<RestaurantEntity>>

    @Query("SELECT * FROM restaurants WHERE id = :id")
    suspend fun getRestaurant(id: Long): RestaurantEntity?

    /**
     * 观察餐厅详情：餐厅 + 各次用餐记录 + 每次记录的照片。
     *
     * `@Transaction` 保证嵌套 [RestaurantWithRecords] / [DiningRecordWithPhotos]
     * 的关系查询在同一快照内完成，避免读到中间状态。餐厅不存在时发射 `null`。
     */
    @Transaction
    @Query("SELECT * FROM restaurants WHERE id = :id")
    fun observeRestaurantWithRecords(id: Long): Flow<RestaurantWithRecords?>

    // ------------------------------------------------------------ 用餐记录

    @Insert
    suspend fun insertDiningRecord(record: DiningRecordEntity): Long

    @Update
    suspend fun updateDiningRecord(record: DiningRecordEntity)

    @Delete
    suspend fun deleteDiningRecord(record: DiningRecordEntity)

    @Query("SELECT * FROM dining_records WHERE id = :id")
    suspend fun getDiningRecord(id: Long): DiningRecordEntity?

    /** 统计餐厅下的用餐记录数量，用于删除记录后同步餐厅状态。 */
    @Query("SELECT COUNT(*) FROM dining_records WHERE restaurantId = :restaurantId")
    suspend fun countDiningRecords(restaurantId: Long): Int

    /**
     * 按用餐时间倒序观察全部用餐记录，供「足迹」时间线使用。
     *
     * 排序在 SQL 层完成而非 ViewModel：时间线是「最近吃的在最上面」这一强约定，
     * 放在查询里能保证无论调用方如何处理数据，顺序都不会被破坏。
     */
    @Query("SELECT * FROM dining_records ORDER BY eatenAt DESC, id DESC")
    fun observeAllDiningRecords(): Flow<List<DiningRecordEntity>>

    /** 观察全部照片行，供时间线一次性取出各次用餐的图片（按记录分组在内存中完成）。 */
    @Query("SELECT * FROM photos ORDER BY diningRecordId ASC, sortOrder ASC")
    fun observeAllPhotos(): Flow<List<PhotoEntity>>

    // ---------------------------------------------------------------- 照片

    @Insert
    suspend fun insertPhoto(photo: PhotoEntity)

    /**
     * 删除某次用餐记录的全部照片行。
     *
     * 编辑记录时先清空再按新顺序重建：这比逐条 diff 更简单，且能一并修正
     * 用户调整顺序后产生的 `sortOrder` 空洞。**只删除数据库行**，磁盘文件由
     * `MealRepository` 在事务提交后回收。
     */
    @Query("DELETE FROM photos WHERE diningRecordId = :diningRecordId")
    suspend fun deletePhotosForRecord(diningRecordId: Long)

    /** 读取某次用餐记录关联的全部照片，用于删除记录前收集待清理的文件路径。 */
    @Query("SELECT * FROM photos WHERE diningRecordId = :diningRecordId")
    suspend fun getPhotosForRecord(diningRecordId: Long): List<PhotoEntity>

    /** 读取某餐厅全部用餐记录下的照片路径，用于删除餐厅时回收文件。 */
    @Query(
        """
        SELECT p.filePath FROM photos p
        INNER JOIN dining_records d ON p.diningRecordId = d.id
        WHERE d.restaurantId = :restaurantId
        """
    )
    suspend fun getPhotoPathsForRestaurant(restaurantId: Long): List<String>

    /**
     * 读取全部已登记的照片路径。
     *
     * 用于与 `PhotoStore` 中的实际文件做差集，识别孤儿文件（例如保存过程中断产生的残留）。
     */
    @Query("SELECT filePath FROM photos")
    suspend fun getAllPhotoPaths(): List<String>

    /** 读取全部用餐记录，用于数据导出。 */
    @Query("SELECT * FROM dining_records ORDER BY id ASC")
    suspend fun getAllDiningRecords(): List<DiningRecordEntity>

    /** 读取全部照片记录，用于数据导出。 */
    @Query("SELECT * FROM photos ORDER BY id ASC")
    suspend fun getAllPhotos(): List<PhotoEntity>

    /** 读取全部餐厅（非 Flow 版本），用于数据导出与完整性校验。 */
    @Query("SELECT * FROM restaurants ORDER BY id ASC")
    suspend fun getAllRestaurants(): List<RestaurantEntity>

    /** 清空全部业务数据，用于导入前的“全新恢复”与设置页的“清空数据”。 */
    @Query("DELETE FROM photos")
    suspend fun clearPhotos()

    @Query("DELETE FROM dining_records")
    suspend fun clearDiningRecords()

    @Query("DELETE FROM restaurants")
    suspend fun clearRestaurants()

    /** 读取全部已设置封面的餐厅图片路径，用于孤儿文件判定时排除有效封面。 */
    @Query("SELECT recommendationPhotoPath FROM restaurants WHERE recommendationPhotoPath != ''")
    suspend fun getRestaurantCoverPaths(): List<String>
}
