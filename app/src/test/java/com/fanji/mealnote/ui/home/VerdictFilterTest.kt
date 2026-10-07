package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.Verdict
import org.junit.Assert.assertEquals
import org.junit.Test

/** 锁定足迹时间线的评价筛选。 */
class VerdictFilterTest {

    private fun entry(id: Long, verdict: Verdict) = FootprintEntry(
        record = DiningRecordEntity(id = id, restaurantId = 1, verdict = verdict),
        restaurantName = "店",
        restaurantAddress = "",
        photos = emptyList(),
    )

    private val entries = listOf(
        entry(1, Verdict.GOOD),
        entry(2, Verdict.MEH),
        entry(3, Verdict.BAD),
        entry(4, Verdict.GOOD),
    )

    @Test
    fun allReturnsEverything() {
        assertEquals(listOf(1L, 2L, 3L, 4L), entries.filterByVerdict(VerdictFilter.ALL).map { it.record.id })
    }

    @Test
    fun goodKeepsOnlyGood() {
        assertEquals(listOf(1L, 4L), entries.filterByVerdict(VerdictFilter.GOOD).map { it.record.id })
    }

    @Test
    fun mehKeepsOnlyMeh() {
        assertEquals(listOf(2L), entries.filterByVerdict(VerdictFilter.MEH).map { it.record.id })
    }

    @Test
    fun badKeepsOnlyBad() {
        assertEquals(listOf(3L), entries.filterByVerdict(VerdictFilter.BAD).map { it.record.id })
    }

    @Test
    fun emptyStaysEmpty() {
        assertEquals(0, emptyList<FootprintEntry>().filterByVerdict(VerdictFilter.GOOD).size)
    }
}
