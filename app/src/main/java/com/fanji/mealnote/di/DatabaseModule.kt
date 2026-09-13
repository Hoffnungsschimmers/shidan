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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "meal_note.db")
            .addMigrations(Migration1To2, Migration2To3)
            .build()

    @Provides
    fun provideMealDao(database: AppDatabase): MealDao = database.mealDao()
}


