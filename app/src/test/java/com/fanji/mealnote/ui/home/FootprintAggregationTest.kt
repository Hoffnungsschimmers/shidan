package com.fanji.mealnote.ui.home

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 「足迹」统计聚合的测试。
 *
 * 这些函数原本写在 ViewModel 内部并直接读系统时钟，无法测试；
 * 抽出为纯函数（`today` 与 `zone` 作为参数）之后，跨年、月末、时区这些
 * **靠手工点界面几乎不可能覆盖**的边界才第一次有了保障。
 */
class FootprintAggregationTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val utc: ZoneId = ZoneId.of("UTC")

    /** 构造某个时区下指定时刻的时间戳。默认取中午，避免落在日期边界上。 */
    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        zone: ZoneId = shanghai,
    ): Long = ZonedDateTime.of(year, month, day, hour, 0, 0, 0, zone)
        .toInstant().toEpochMilli()

    private fun entry(
        recordId: Long = 0,
        restaurantId: Long = 1,
        restaurantName: String = "老王面馆",
        eatenAt: Long,
        verdict: Verdict = Verdict.GOOD,
        dishes: String = "",
        priceText: String = "",
    ) = FootprintEntry(
        record = DiningRecordEntity(
            id = recordId,
            restaurantId = restaurantId,
            eatenAt = eatenAt,
            verdict = verdict,
            dishes = dishes,
            priceText = priceText,
        ),
        restaurantName = restaurantName,
        restaurantAddress = "",
        photos = emptyList(),
    )

    // ─────────────────────────── 时区 ───────────────────────────

    @Test
    fun `月份归属随时区变化`() {
        // UTC 的 2025-12-31 23:00 在东八区已经是 2026-01-01 07:00。
        // 用错时区会让记录出现在「上个月」的分组里。
        val instant = at(2025, 12, 31, hour = 23, zone = utc)
        assertEquals(YearMonth.of(2025, 12), instant.toYearMonth(utc))
        assertEquals(YearMonth.of(2026, 1), instant.toYearMonth(shanghai))
    }

    @Test
    fun `isInYear 使用指定时区判定`() {
        val instant = at(2025, 12, 31, hour = 23, zone = utc)
        val e = entry(eatenAt = instant)

        assertTrue(e.isInYear(2025, utc))
        assertTrue(e.isInYear(2026, shanghai))
        assertTrue(!e.isInYear(2026, utc))
        assertTrue(!e.isInYear(2025, shanghai))
    }

    // ─────────────────────────── 月度柱状图 ───────────────────────────

    @Test
    fun `空数据也返回固定 12 个月`() {
        // 横轴必须固定：只画「出现过的月份」会让某月没记录时该柱消失、间距错乱。
        val counts = emptyList<FootprintEntry>()
            .toMonthlyCounts(YearMonth.of(2026, 9), shanghai)

        assertEquals(12, counts.size)
        assertTrue(counts.all { it.count == 0 })
        assertEquals("2025-10", counts.first().yearMonth)
        assertEquals("2026-09", counts.last().yearMonth)
    }

    @Test
    fun `窗口为最近 12 个月且从早到晚`() {
        val counts = emptyList<FootprintEntry>()
            .toMonthlyCounts(YearMonth.of(2026, 3), shanghai)

        assertEquals("2025-04", counts.first().yearMonth)
        assertEquals("2026-03", counts.last().yearMonth)
        assertEquals("4月", counts.first().label)
        assertEquals("3月", counts.last().label)
    }

    @Test
    fun `窗口外的记录不计入`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 10)),   // 窗口内（当前月）
            entry(eatenAt = at(2025, 4, 1)),    // 窗口内（最早一个月）
            entry(eatenAt = at(2025, 3, 31)),   // 窗口外（再往前一个月）
            entry(eatenAt = at(2026, 4, 1)),    // 窗口外（未来）
        )
        val counts = entries.toMonthlyCounts(YearMonth.of(2026, 3), shanghai)

        assertEquals(2, counts.sumOf { it.count })
        assertEquals(1, counts.first { it.yearMonth == "2025-04" }.count)
        assertEquals(1, counts.first { it.yearMonth == "2026-03" }.count)
    }

    @Test
    fun `同月多条记录会累加`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1)),
            entry(eatenAt = at(2026, 3, 20)),
            entry(eatenAt = at(2026, 3, 31)),
        )
        val counts = entries.toMonthlyCounts(YearMonth.of(2026, 3), shanghai)
        assertEquals(3, counts.last().count)
    }

    @Test
    fun `时区决定记录落在哪一根柱子上`() {
        val instant = at(2026, 3, 31, hour = 23, zone = utc)
        val entries = listOf(entry(eatenAt = instant))

        val byUtc = entries.toMonthlyCounts(YearMonth.of(2026, 4), utc)
        val byShanghai = entries.toMonthlyCounts(YearMonth.of(2026, 4), shanghai)

        assertEquals(1, byUtc.first { it.yearMonth == "2026-03" }.count)
        assertEquals(0, byUtc.first { it.yearMonth == "2026-04" }.count)
        assertEquals(0, byShanghai.first { it.yearMonth == "2026-03" }.count)
        assertEquals(1, byShanghai.first { it.yearMonth == "2026-04" }.count)
    }

    @Test
    fun `月份数可配置`() {
        val counts = emptyList<FootprintEntry>()
            .toMonthlyCounts(YearMonth.of(2026, 3), shanghai, months = 3)
        assertEquals(listOf("2026-01", "2026-02", "2026-03"), counts.map { it.yearMonth })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `月份数必须为正`() {
        emptyList<FootprintEntry>().toMonthlyCounts(YearMonth.of(2026, 3), shanghai, months = 0)
    }

    // ─────────────────────────── 常去的店 ───────────────────────────

    @Test
    fun `按次数倒序`() {
        val entries = listOf(
            entry(restaurantId = 1, restaurantName = "A", eatenAt = at(2026, 3, 1)),
            entry(restaurantId = 2, restaurantName = "B", eatenAt = at(2026, 3, 2)),
            entry(restaurantId = 2, restaurantName = "B", eatenAt = at(2026, 3, 3)),
            entry(restaurantId = 2, restaurantName = "B", eatenAt = at(2026, 3, 4)),
            entry(restaurantId = 3, restaurantName = "C", eatenAt = at(2026, 3, 5)),
            entry(restaurantId = 3, restaurantName = "C", eatenAt = at(2026, 3, 6)),
        )
        val ranks = entries.toTopRestaurants()

        assertEquals(listOf(2L, 3L, 1L), ranks.map { it.restaurantId })
        assertEquals(listOf(3, 2, 1), ranks.map { it.count })
        assertEquals("B", ranks.first().name)
    }

    @Test
    fun `次数相同时按 id 升序以保证顺序稳定`() {
        // 排序不稳定会让排行榜在数据库返回顺序变化时无理由跳动，
        // 用户会以为数据变了。
        val entries = listOf(
            entry(restaurantId = 7, restaurantName = "G", eatenAt = at(2026, 3, 1)),
            entry(restaurantId = 3, restaurantName = "C", eatenAt = at(2026, 3, 2)),
            entry(restaurantId = 5, restaurantName = "E", eatenAt = at(2026, 3, 3)),
        )
        val ranks = entries.toTopRestaurants()
        assertEquals(listOf(3L, 5L, 7L), ranks.map { it.restaurantId })
    }

    @Test
    fun `超出上限时只返回前几名`() {
        val entries = (1L..10L).map { id ->
            entry(restaurantId = id, restaurantName = "店$id", eatenAt = at(2026, 3, 1))
        }
        val ranks = entries.toTopRestaurants(limit = 5)
        assertEquals(5, ranks.size)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ranks.map { it.restaurantId })
    }

    @Test
    fun `没有记录时排行榜为空`() {
        assertTrue(emptyList<FootprintEntry>().toTopRestaurants().isEmpty())
    }

    // ─────────────────────────── 花费估算 ───────────────────────────

    @Test
    fun `只统计指定年份的花费`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), priceText = "100"),
            entry(eatenAt = at(2026, 8, 1), priceText = "200"),
            entry(eatenAt = at(2025, 3, 1), priceText = "999"),
        )
        assertEquals(300.0, entries.estimatedSpendIn(2026, shanghai))
        assertEquals(999.0, entries.estimatedSpendIn(2025, shanghai))
    }

    @Test
    fun `识别不出金额的记录被跳过而不是计为零`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), priceText = "128"),
            entry(eatenAt = at(2026, 3, 2), priceText = "忘了"),
            entry(eatenAt = at(2026, 3, 3), priceText = ""),
            entry(eatenAt = at(2026, 3, 4), priceText = "人均60"),
        )
        assertEquals(188.0, entries.estimatedSpendIn(2026, shanghai))
    }

    @Test
    fun `一条都识别不出时返回 null 而不是零`() {
        // 返回 0.0 会让界面显示「约 ¥0」，被读成「今年没花钱」，
        // 而事实是「没有可识别的金额」。
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), priceText = "忘了"),
            entry(eatenAt = at(2026, 3, 2), priceText = "很贵"),
        )
        assertNull(entries.estimatedSpendIn(2026, shanghai))
    }

    @Test
    fun `该年没有记录时返回 null`() {
        val entries = listOf(entry(eatenAt = at(2025, 3, 1), priceText = "100"))
        assertNull(entries.estimatedSpendIn(2026, shanghai))
    }

    @Test
    fun `单条记录的金额解析`() {
        assertEquals(128.0, entry(eatenAt = 0, priceText = "128").estimatedAmount())
        assertEquals(240.0, entry(eatenAt = 0, priceText = "3个人吃了240").estimatedAmount())
        assertNull(entry(eatenAt = 0, priceText = "").estimatedAmount())
        assertNull(entry(eatenAt = 0, priceText = "没记住").estimatedAmount())
    }

    @Test
    fun `花费统计的年份判定同样受时区影响`() {
        // 东八区的 2026-01-01 00:30 在 UTC 还是 2025-12-31。
        val instant = ZonedDateTime.of(2026, 1, 1, 0, 30, 0, 0, shanghai)
            .toInstant().toEpochMilli()
        val entries = listOf(entry(eatenAt = instant, priceText = "100"))

        assertEquals(100.0, entries.estimatedSpendIn(2026, shanghai))
        assertNull(entries.estimatedSpendIn(2026, utc))
        assertEquals(100.0, entries.estimatedSpendIn(2025, utc))
    }

    // ─────────────────────────── 月份分组 ───────────────────────────

    @Test
    fun `分组保持输入顺序`() {
        // 上游已按 eatenAt DESC 排序，这里不能再排一次，否则会打乱「月内倒序」。
        val entries = listOf(
            entry(eatenAt = at(2026, 9, 20)),
            entry(eatenAt = at(2026, 9, 10)),
            entry(eatenAt = at(2026, 8, 5)),
        )
        val sections = entries.toSections()

        assertEquals(listOf("2026年9月", "2026年8月"), sections.map { it.title })
        assertEquals(2, sections.first().entries.size)
        assertEquals(1, sections.last().entries.size)
    }

    @Test
    fun `没有记录时没有分组`() {
        assertTrue(emptyList<FootprintEntry>().toSections().isEmpty())
    }
}
