package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 锁定年度回顾数据:最常去的店、评价分布、ledger/估算口径、按年倒序。 */
class YearReviewTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun entry(
        year: Int,
        restaurantId: Long,
        restaurantName: String,
        verdict: Verdict,
        amountMinor: Long?,
        priceText: String,
    ) = FootprintEntry(
        record = DiningRecordEntity(
            restaurantId = restaurantId,
            eatenAt = LocalDate.of(year, 3, 10).atStartOfDay(zone).toInstant().toEpochMilli(),
            verdict = verdict,
            priceText = priceText,
            amountMinorUnits = amountMinor,
        ),
        restaurantName = restaurantName,
        restaurantAddress = "",
        photos = emptyList(),
    )

    @Test
    fun aggregatesTopRestaurantVerdictsAndSpend() {
        val entries = listOf(
            entry(2025, 1, "老王", Verdict.GOOD, 10000, "100"),
            entry(2025, 1, "老王", Verdict.GOOD, 5000, "50"),
            entry(2025, 1, "老王", Verdict.BAD, null, ""),
            entry(2025, 2, "海底捞", Verdict.MEH, null, "约80"),
        )
        val review = entries.yearReviews(zone).first { it.year == 2025 }

        assertEquals(4, review.visitCount)
        assertEquals("老王", review.topRestaurantName)
        assertEquals(3, review.topRestaurantVisits)
        assertEquals(2, review.goodCount)
        assertEquals(1, review.mehCount)
        assertEquals(1, review.badCount)
        assertEquals(15000L, review.ledgerMinor)
        assertEquals(230.0, review.estimatedYuan!!, 0.001)
    }

    @Test
    fun ordersByYearDescending() {
        val entries = listOf(
            entry(2024, 1, "老王", Verdict.GOOD, 2000, "20"),
            entry(2025, 1, "老王", Verdict.GOOD, 3000, "30"),
        )
        assertEquals(listOf(2025, 2024), entries.yearReviews(zone).map { it.year })
    }

    @Test
    fun nullSpendWhenNoLedgerOrEstimate() {
        val entries = listOf(entry(2023, 1, "老王", Verdict.GOOD, null, ""))
        val review = entries.yearReviews(zone).single()
        assertNull(review.ledgerMinor)
        assertNull(review.estimatedYuan)
        assertEquals("老王", review.topRestaurantName)
        assertEquals(1, review.topRestaurantVisits)
    }
}
