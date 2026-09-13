package com.fanji.mealnote.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 应用数据库。
 *
 * 版本历史：
 * - v1 → v2：`restaurants` 增加 `recommendationPhotoPath`（封面图）。
 * - v2 → v3：`dining_records` 增加 `priceText`（花费自由文本）。
 *
 * **每次提升 [version] 都必须在 `DatabaseModule` 中同步提供 Migration**，
 * 并确认 `app/schemas/<版本>.json` 已重新导出（KSP 会自动写入）。
 * 禁止使用 `fallbackToDestructiveMigration()`：那会让用户在升级时静默丢失全部数据。
 */
@Database(
    entities = [
        RestaurantEntity::class,
        DiningRecordEntity::class,
        PhotoEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
}


