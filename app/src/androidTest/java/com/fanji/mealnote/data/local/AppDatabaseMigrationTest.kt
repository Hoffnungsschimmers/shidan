package com.fanji.mealnote.data.local

import androidx.room.testing.MigrationTestHelper
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

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_preservesRestaurantsAndAddsCoverPath() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO restaurants
                (id, name, address, city, cuisine, tags, priceHint, sourceUrl, sourceNote, status, createdAt, updatedAt)
                VALUES (1, '示例餐厅', '示例地址', '', '', '', NULL, '', '', 'WANT_TO_EAT', 100, 100)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 2, true, DatabaseModule.Migration1To2).use { db ->
            db.query(
                "SELECT name, address, status, recommendationPhotoPath FROM restaurants WHERE id = 1"
            ).use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("示例餐厅", cursor.getString(0))
                assertEquals("示例地址", cursor.getString(1))
                assertEquals("WANT_TO_EAT", cursor.getString(2))
                assertEquals("", cursor.getString(3))
            }
        }
    }
}
