package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 锁定清单「去过 N 次」的按店计数。 */
class VisitCountsTest {

    private fun rec(restaurantId: Long) = DiningRecordEntity(restaurantId = restaurantId)

    @Test
    fun emptyRecordsGiveEmptyMap() {
        assertTrue(visitCountsByRestaurant(emptyList()).isEmpty())
    }

    @Test
    fun countsPerRestaurant() {
        val records = listOf(rec(1), rec(1), rec(2), rec(1), rec(3))
        val counts = visitCountsByRestaurant(records)
        assertEquals(3, counts[1])
        assertEquals(1, counts[2])
        assertEquals(1, counts[3])
        assertEquals(null, counts[99])
    }
}
