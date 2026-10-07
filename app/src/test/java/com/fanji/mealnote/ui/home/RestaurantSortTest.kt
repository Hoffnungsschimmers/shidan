package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.settings.RestaurantSort
import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定清单排序:最近更新/最近添加/按名称,以及时间相等时的稳定兜底。 */
class RestaurantSortTest {

    private fun store(id: Long, name: String, created: Long, updated: Long) =
        RestaurantEntity(id = id, name = name, createdAt = created, updatedAt = updated)

    @Test
    fun recentUpdatedSortsByUpdatedAtDescending() {
        val list = listOf(
            store(1, "A", created = 0, updated = 100),
            store(2, "B", created = 0, updated = 300),
            store(3, "C", created = 0, updated = 200),
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            list.sortedForList(RestaurantSort.RECENT_UPDATED).map { it.id },
        )
    }

    @Test
    fun recentAddedSortsByCreatedAtDescending() {
        val list = listOf(
            store(1, "A", created = 100, updated = 999),
            store(2, "B", created = 300, updated = 1),
            store(3, "C", created = 200, updated = 500),
        )
        assertEquals(
            listOf(2L, 3L, 1L),
            list.sortedForList(RestaurantSort.RECENT_ADDED).map { it.id },
        )
    }

    @Test
    fun nameSortsAlphabetically() {
        val list = listOf(
            store(1, "Cherry", created = 0, updated = 0),
            store(2, "Apple", created = 0, updated = 0),
            store(3, "Banana", created = 0, updated = 0),
        )
        assertEquals(
            listOf("Apple", "Banana", "Cherry"),
            list.sortedForList(RestaurantSort.NAME).map { it.name },
        )
    }

    @Test
    fun equalTimestampsFallBackToIdDescendingForStability() {
        val list = listOf(
            store(1, "A", created = 0, updated = 500),
            store(2, "B", created = 0, updated = 500),
            store(3, "C", created = 0, updated = 500),
        )
        assertEquals(
            listOf(3L, 2L, 1L),
            list.sortedForList(RestaurantSort.RECENT_UPDATED).map { it.id },
        )
    }
}
