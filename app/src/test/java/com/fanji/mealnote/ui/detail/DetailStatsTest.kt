package com.fanji.mealnote.ui.detail

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.DiningRecordWithPhotos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 锁定详情页「累计入账」小结:求和、null≠0、跳过未记账记录。 */
class DetailStatsTest {

    private fun record(amountMinor: Long?) = DiningRecordWithPhotos(
        record = DiningRecordEntity(restaurantId = 1, amountMinorUnits = amountMinor),
        photos = emptyList(),
    )

    @Test
    fun emptyReturnsNull() {
        assertNull(emptyList<DiningRecordWithPhotos>().totalLedgerMinor())
    }

    @Test
    fun allUnrecordedReturnsNull() {
        assertNull(listOf(record(null), record(null)).totalLedgerMinor())
    }

    @Test
    fun sumsOnlyRecordedAmounts() {
        assertEquals(15000L, listOf(record(10000), record(null), record(5000)).totalLedgerMinor())
    }

    @Test
    fun singleRecordedAmount() {
        assertEquals(8800L, listOf(record(8800)).totalLedgerMinor())
    }
}
