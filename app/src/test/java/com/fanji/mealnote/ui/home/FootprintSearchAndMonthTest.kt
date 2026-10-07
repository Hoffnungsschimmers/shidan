package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 覆盖从 `FootprintViewModel` 下沉的两处纯逻辑：足迹搜索命中 [matchesQuery]
 * 与按月入账合计 [ledgerSpendInMonth]。
 */
class FootprintSearchAndMonthTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun entry(
        name: String = "老王面馆",
        address: String = "北京朝阳",
        dishes: String = "牛肉面",
        priceText: String = "",
        note: String = "",
        amountMinor: Long? = null,
        year: Int = 2026,
        month: Int = 6,
    ): FootprintEntry = FootprintEntry(
        record = DiningRecordEntity(
            restaurantId = 1,
            eatenAt = LocalDate.of(year, month, 15).atStartOfDay(zone).toInstant().toEpochMilli(),
            dishes = dishes,
            priceText = priceText,
            note = note,
            amountMinorUnits = amountMinor,
        ),
        restaurantName = name,
        restaurantAddress = address,
        photos = emptyList(),
    )

    // ---------------------------------------------------------------- matchesQuery

    @Test
    fun matchesByEachField() {
        val e = entry(name = "海底捞", address = "望京SOHO", dishes = "毛肚", priceText = "人均120", note = "服务好")
        assertTrue(e.matchesQuery("海底"))
        assertTrue(e.matchesQuery("望京"))
        assertTrue(e.matchesQuery("毛肚"))
        assertTrue(e.matchesQuery("120"))
        assertTrue(e.matchesQuery("服务"))
    }

    @Test
    fun matchIsCaseInsensitive() {
        assertTrue(entry(name = "KFC").matchesQuery("kfc"))
        assertTrue(entry(dishes = "Pizza").matchesQuery("pizza"))
    }

    @Test
    fun emptyKeywordMatchesAll() {
        assertTrue(entry().matchesQuery(""))
    }

    @Test
    fun noMatchReturnsFalse() {
        assertFalse(entry(name = "老王面馆", dishes = "牛肉面").matchesQuery("寿司"))
    }

    // ---------------------------------------------------------------- ledgerSpendInMonth

    @Test
    fun sumsLedgerForGivenMonthOnly() {
        val entries = listOf(
            entry(amountMinor = 10000, year = 2026, month = 6),
            entry(amountMinor = 5000, year = 2026, month = 6),
            entry(amountMinor = 9999, year = 2026, month = 5), // 别的月,不计
        )
        assertEquals(15000L, entries.ledgerSpendInMonth(YearMonth.of(2026, 6), zone))
    }

    @Test
    fun nullWhenMonthHasNoLedger() {
        val entries = listOf(
            entry(amountMinor = null, priceText = "忘了", year = 2026, month = 6),
        )
        assertNull(entries.ledgerSpendInMonth(YearMonth.of(2026, 6), zone))
        assertNull(entries.ledgerSpendInMonth(YearMonth.of(2026, 7), zone))
    }
}
