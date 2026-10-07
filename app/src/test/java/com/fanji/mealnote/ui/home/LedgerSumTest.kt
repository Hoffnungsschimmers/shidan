package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 锁定时间线月份小计的入账合计:求和、null≠0、跳过未记账。 */
class LedgerSumTest {

    private fun entry(amountMinor: Long?) = FootprintEntry(
        record = DiningRecordEntity(restaurantId = 1, amountMinorUnits = amountMinor),
        restaurantName = "店",
        restaurantAddress = "",
        photos = emptyList(),
    )

    @Test
    fun sumsRecordedAmounts() {
        assertEquals(15000L, listOf(entry(10000), entry(null), entry(5000)).ledgerSumOrNull())
    }

    @Test
    fun nullWhenNoneRecorded() {
        assertNull(listOf(entry(null), entry(null)).ledgerSumOrNull())
        assertNull(emptyList<FootprintEntry>().ledgerSumOrNull())
    }
}
