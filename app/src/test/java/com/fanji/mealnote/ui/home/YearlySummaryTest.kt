package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 锁定历年汇总:按年份倒序、次数统计、入账/估算的 null 与求和口径。 */
class YearlySummaryTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun millisIn(year: Int): Long =
        LocalDate.of(year, 6, 1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun entry(
        year: Int,
        amountMinor: Long?,
        priceText: String,
    ): FootprintEntry = FootprintEntry(
        record = DiningRecordEntity(
            id = 0,
            restaurantId = 1,
            eatenAt = millisIn(year),
            dishes = "x",
            priceText = priceText,
            amountMinorUnits = amountMinor,
        ),
        restaurantName = "店",
        restaurantAddress = "",
        photos = emptyList(),
    )

    @Test
    fun summariesAreOrderedByYearDescending() {
        val entries = listOf(entry(2024, null, ""), entry(2025, 5000, "50"))
        val summaries = entries.yearlySummaries(zone)
        assertEquals(listOf(2025, 2024), summaries.map { it.year })
    }

    @Test
    fun countsLedgerAndEstimatePerYear() {
        val entries = listOf(
            entry(2024, 10000, "100"),
            entry(2024, null, ""),      // 未记账、无花费文本
            entry(2025, 5000, "约50"),
        )
        val summaries = entries.yearlySummaries(zone).associateBy { it.year }

        val y2024 = summaries.getValue(2024)
        assertEquals(2, y2024.visitCount)
        assertEquals(10000L, y2024.ledgerMinor)
        assertEquals(100.0, y2024.estimatedYuan!!, 0.001)

        val y2025 = summaries.getValue(2025)
        assertEquals(1, y2025.visitCount)
        assertEquals(5000L, y2025.ledgerMinor)
        assertEquals(50.0, y2025.estimatedYuan!!, 0.001)
    }

    @Test
    fun yearWithNoLedgerOrEstimateReportsNull() {
        val summaries = listOf(entry(2023, null, "")).yearlySummaries(zone)
        assertEquals(1, summaries.size)
        assertNull(summaries[0].ledgerMinor)
        assertNull(summaries[0].estimatedYuan)
        assertEquals(1, summaries[0].visitCount)
    }
}
