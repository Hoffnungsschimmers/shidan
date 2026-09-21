package com.fanji.mealnote.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fanji.mealnote.data.local.AppDatabase
import com.fanji.mealnote.data.local.MealDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    val Migration1To2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE restaurants ADD COLUMN recommendationPhotoPath TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    /**
     * v2 → v3：用餐记录增加「花费」自由文本列。
     *
     * 使用 `ALTER TABLE ... ADD COLUMN` 而非重建表：新增列且带 `NOT NULL DEFAULT ''`，
     * 历史行的取值明确（空串 = 未填写），无需数据搬运，也不会触碰既有数据。
     * 实体上的默认值必须与这里的 DEFAULT 完全一致，否则 Room 的 schema 校验会失败。
     */
    val Migration2To3: Migration = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE dining_records ADD COLUMN priceText TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    /**
     * v3 → v4：用餐记录增加结构化金额与人数（吃饭账本的数据基础）。
     *
     * - `amountMinorUnits`：**可空**列，**不带 DEFAULT** —— SQLite 会给历史行填 NULL，
     *   语义即「未记金额」。Room 全新建表时该列同样无默认值，两边等价，
     *   因此实体上**不写** `@ColumnInfo(defaultValue)`。
     * - `personCount`：`NOT NULL DEFAULT 1`，历史行语义为「未填人数按 1 人计」。
     *   实体上的 `@ColumnInfo(defaultValue = "1")` 必须与这里**逐字一致**。
     */
    val Migration3To4: Migration = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE dining_records ADD COLUMN amountMinorUnits INTEGER")
            db.execSQL("ALTER TABLE dining_records ADD COLUMN personCount INTEGER NOT NULL DEFAULT 1")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "meal_note.db")
            .addMigrations(Migration1To2, Migration2To3, Migration3To4)
            .build()

    @Provides
    fun provideMealDao(database: AppDatabase): MealDao = database.mealDao()
}


