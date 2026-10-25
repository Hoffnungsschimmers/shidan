package com.fanji.mealnote.data.local

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fanji.mealnote.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val databaseName = "migration-test"
    private val coverPath = "/data/user/0/com.fanji.mealnote/files/photos/cover.jpg"
    private val secondPhotoPath = "/data/user/0/com.fanji.mealnote/files/photos/dishes.jpg"
    private val thirdPhotoPath = "/data/user/0/com.fanji.mealnote/files/photos/another-visit.jpg"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesRestaurantsAndAddsCoverPath() {
        migrateAndAssert(1, 2, DatabaseModule.Migration1To2)
    }

    @Test
    fun migrate2To3_preservesCoverAndAddsPriceText() {
        migrateAndAssert(2, 3, DatabaseModule.Migration2To3)
    }

    @Test
    fun migrate3To4_preservesCoverAndPriceTextAndAddsLedgerDefaults() {
        migrateAndAssert(3, 4, DatabaseModule.Migration3To4)
    }

    @Test
    fun migrate1To3_preservesDataAndAddsCoverAndPriceText() {
        migrateAndAssert(1, 3, DatabaseModule.Migration1To2, DatabaseModule.Migration2To3)
    }

    @Test
    fun migrate1To4_preservesDataAndAddsAllDefaults() {
        migrateAndAssert(
            1, 4,
            DatabaseModule.Migration1To2,
            DatabaseModule.Migration2To3,
            DatabaseModule.Migration3To4,
        )
    }

    @Test
    fun migrate2To4_preservesCoverAndAddsPriceTextAndLedgerDefaults() {
        migrateAndAssert(2, 4, DatabaseModule.Migration2To3, DatabaseModule.Migration3To4)
    }

    private fun migrateAndAssert(startVersion: Int, endVersion: Int, vararg migrations: Migration) {
        // Room 2.8.4 的 createDatabase 会先删除同名旧库；每次连接均用 use 关闭，无需另行清库。
        helper.createDatabase(databaseName, startVersion).use { db ->
            insertSamples(db, startVersion)
        }
        helper.runMigrationsAndValidate(databaseName, endVersion, true, *migrations).use { db ->
            assertSamples(db, startVersion, endVersion)
        }
    }

    private fun insertSamples(db: SupportSQLiteDatabase, version: Int) {
        val coverColumn = if (version >= 2) ", recommendationPhotoPath" else ""
        val coverValue = if (version >= 2) ", ''" else ""
        // 历史 schema 的这些遗留列 NOT NULL 且无 SQL DEFAULT，仅为 fixture 填空串 / star=0。
        // 不访问实体的 Deprecated 属性，不读取遗留列；可空 priceHint / perPersonCost 留为 NULL。
        db.execSQL(
            """
            INSERT INTO restaurants
            (id, name, address, status, createdAt, updatedAt, city, cuisine, tags, sourceUrl, sourceNote$coverColumn)
            VALUES
            (1, '示例餐厅', '示例地址', 'WANT_TO_EAT', 100, 100, '', '', '', '', ''$coverValue),
            (2, '已去餐厅🍜', '另一地址 2 号', 'EATEN', 200, 300, '', '', '', '', ''$coverValue)
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO dining_records
            (id, restaurantId, eatenAt, verdict, dishes, note, createdAt, star)
            VALUES
            (11, 2, 1700000000000, 'GOOD', '牛肉面🍜、小菜', ?, 1700000000100, 0),
            (12, 2, 1700086400000, 'MEH', '炒饭', '口味尚可', 1700086400100, 0),
            (13, 2, 1700172800000, 'BAD', '汤', '等候太久', 1700172800100, 0)
            """.trimIndent(),
            arrayOf("服务很好\n下次再来"),
        )
        if (version >= 2) {
            db.execSQL(
                "UPDATE restaurants SET recommendationPhotoPath = ? WHERE id = 2",
                arrayOf(coverPath),
            )
        }
        if (version >= 3) {
            db.execSQL("UPDATE dining_records SET priceText = ? WHERE id = 11", arrayOf("两个人 158"))
            db.execSQL("UPDATE dining_records SET priceText = ? WHERE id = 13", arrayOf("人均 60 左右"))
        }
        db.execSQL(
            "INSERT INTO photos (id, diningRecordId, filePath, sortOrder) VALUES " +
                "(21, 11, ?, 0), (22, 11, ?, 1), (23, 12, ?, 0)",
            arrayOf(coverPath, secondPhotoPath, thirdPhotoPath),
        )
    }

    private fun assertSamples(db: SupportSQLiteDatabase, startVersion: Int, endVersion: Int) {
        assertRows(
            db,
            "SELECT id, name, address, status, createdAt, updatedAt, recommendationPhotoPath " +
                "FROM restaurants ORDER BY id",
            listOf(
                listOf(1L, "示例餐厅", "示例地址", "WANT_TO_EAT", 100L, 100L, ""),
                listOf(2L, "已去餐厅🍜", "另一地址 2 号", "EATEN", 200L, 300L,
                    if (startVersion >= 2) coverPath else ""),
            ),
        )
        assertRows(
            db,
            "SELECT id, restaurantId, eatenAt, verdict, dishes, note, createdAt " +
                "FROM dining_records ORDER BY id",
            listOf(
                listOf(11L, 2L, 1700000000000L, "GOOD", "牛肉面🍜、小菜", "服务很好\n下次再来", 1700000000100L),
                listOf(12L, 2L, 1700086400000L, "MEH", "炒饭", "口味尚可", 1700086400100L),
                listOf(13L, 2L, 1700172800000L, "BAD", "汤", "等候太久", 1700172800100L),
            ),
        )
        assertRows(
            db,
            "SELECT id, diningRecordId, filePath, sortOrder FROM photos ORDER BY id",
            listOf(
                listOf(21L, 11L, coverPath, 0L),
                listOf(22L, 11L, secondPhotoPath, 1L),
                listOf(23L, 12L, thirdPhotoPath, 0L),
            ),
        )
        if (endVersion >= 3) {
            assertRows(
                db,
                "SELECT id, priceText FROM dining_records ORDER BY id",
                listOf(
                    listOf(11L, if (startVersion >= 3) "两个人 158" else ""),
                    listOf(12L, ""),
                    listOf(13L, if (startVersion >= 3) "人均 60 左右" else ""),
                ),
            )
        }
        if (endVersion >= 4) {
            assertRows(
                db,
                "SELECT id, amountMinorUnits, personCount FROM dining_records ORDER BY id",
                listOf(listOf(11L, null, 1L), listOf(12L, null, 1L), listOf(13L, null, 1L)),
            )
        }
        assertColumnDefaults(db, startVersion, endVersion)
        // 外键值已逐行断言，再确认餐厅 → 记录 → 照片没有孤儿行。
        assertRows(db, "PRAGMA foreign_key_check", emptyList())
    }

    private fun assertColumnDefaults(db: SupportSQLiteDatabase, startVersion: Int, endVersion: Int) {
        fun defaults(table: String): Map<String, String?> = buildMap {
            db.query("PRAGMA table_info($table)").use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                val defaultColumn = cursor.getColumnIndexOrThrow("dflt_value")
                while (cursor.moveToNext()) {
                    put(cursor.getString(nameColumn), cursor.getString(defaultColumn))
                }
            }
        }
        val restaurantDefaults = defaults("restaurants")
        // 历史 1→2 为封面添加 DEFAULT ''，v2 起的新建 schema 则没有声明默认值。
        // Room 容忍此差异；显式锁定它，不能将 Room 校验通过说成两者严格等价。
        assertEquals(if (startVersion == 1) "''" else null, restaurantDefaults.getValue("recommendationPhotoPath"))
        val recordDefaults = defaults("dining_records")
        if (endVersion >= 3) assertEquals("''", recordDefaults.getValue("priceText"))
        if (endVersion >= 4) {
            assertEquals(null, recordDefaults.getValue("amountMinorUnits"))
            assertEquals("1", recordDefaults.getValue("personCount"))
        }
    }

    private fun assertRows(db: SupportSQLiteDatabase, sql: String, expectedRows: List<List<Any?>>) {
        db.query(sql).use { cursor ->
            assertEquals("row count for $sql", expectedRows.size, cursor.count)
            var rowIndex = 0
            while (cursor.moveToNext()) {
                val actualRow = List(cursor.columnCount) { column ->
                    when (cursor.getType(column)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(column)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(column)
                        else -> error("Unexpected column type for $sql at column $column")
                    }
                }
                assertEquals("row $rowIndex for $sql", expectedRows[rowIndex], actualRow)
                rowIndex++
            }
        }
    }
}
