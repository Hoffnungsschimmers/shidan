package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 锁定「本月次数」按自然月计数(含时区归属)。 */
class CountInMonthTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun entry(year: Int, month: Int, day: Int) = FootprintEntry(
        record = DiningRecordEntity(
            restaurantId = 1,
            eatenAt = LocalDate.of(year, month, day).atStartOfDay(zone).toInstant().toEpochMilli(),
        ),
        restaurantName = "店",
        restaurantAddress = "",
        photos = emptyList(),
    )

    @Test
    fun countsOnlyEntriesInTheGivenMonth() {
        val entries = listOf(
            entry(2026, 9, 1),
            entry(2026, 9, 30),
            entry(2026, 8, 31), // 上月
            entry(2026, 10, 1), // 下月
        )
        assertEquals(2, entries.countInMonth(YearMonth.of(2026, 9), zone))
        assertEquals(1, entries.countInMonth(YearMonth.of(2026, 8), zone))
        assertEquals(0, entries.countInMonth(YearMonth.of(2026, 7), zone))
    }
}
