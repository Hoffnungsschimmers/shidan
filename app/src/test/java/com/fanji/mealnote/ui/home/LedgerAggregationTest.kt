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
 * 「账本」聚合（[LedgerAggregation]）的测试。
 *
 * 账本口径与花费**估算**刻意分开：只认结构化入账金额（`amountMinorUnits`），
 * 未入账的记录**绝不**用估算凑数。这些用例把两条红线钉死：
 * - 一分钱都没入账时返回 `null`（「还没有入账记录」），不返回 `0`（会被读成「没花钱」）；
 * - 未入账但写了花费文本的记录，通过覆盖率如实展示，而不是假装不存在。
 *
 * 与 `FootprintAggregationTest` 同一模式：`today`/`zone` 作参数，纯函数，不读时钟。
 */
class LedgerAggregationTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val utc: ZoneId = ZoneId.of("UTC")

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
        priceText: String = "",
        amountMinorUnits: Long? = null,
    ) = FootprintEntry(
        record = DiningRecordEntity(
            id = recordId,
            restaurantId = restaurantId,
            eatenAt = eatenAt,
            verdict = Verdict.GOOD,
            dishes = "",
            priceText = priceText,
            amountMinorUnits = amountMinorUnits,
        ),
        restaurantName = restaurantName,
        restaurantAddress = "",
        photos = emptyList(),
    )

    // ─────────────────────────── 年度总额 ───────────────────────────

    @Test
    fun `年度总额只累加当年入账记录`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), amountMinorUnits = 10000),
            entry(eatenAt = at(2026, 8, 1), amountMinorUnits = 20000),
            entry(eatenAt = at(2025, 3, 1), amountMinorUnits = 99900),
        )
        assertEquals(30000L, entries.ledgerSpendIn(2026, shanghai))
        assertEquals(99900L, entries.ledgerSpendIn(2025, shanghai))
    }

    @Test
    fun `未入账的记录不计入年度总额`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), amountMinorUnits = 12800),
            entry(eatenAt = at(2026, 3, 2), priceText = "忘了", amountMinorUnits = null),
            entry(eatenAt = at(2026, 3, 3), amountMinorUnits = null),
        )
        assertEquals(12800L, entries.ledgerSpendIn(2026, shanghai))
    }

    @Test
    fun `一笔都没入账时返回 null 而不是零`() {
        // 返回 0 会显示「今年花了 ¥0」，被读成「今年没花钱」，
        // 而事实是「还没有入账记录」。
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), priceText = "忘了", amountMinorUnits = null),
            entry(eatenAt = at(2026, 3, 2), priceText = "很贵", amountMinorUnits = null),
        )
        assertNull(entries.ledgerSpendIn(2026, shanghai))
    }

    @Test
    fun `该年没有记录时返回 null`() {
        val entries = listOf(entry(eatenAt = at(2025, 3, 1), amountMinorUnits = 10000))
        assertNull(entries.ledgerSpendIn(2026, shanghai))
    }

    @Test
    fun `年度归属受时区影响`() {
        // 东八区的 2026-01-01 00:30 在 UTC 还是 2025-12-31。
        val instant = ZonedDateTime.of(2026, 1, 1, 0, 30, 0, 0, shanghai)
            .toInstant().toEpochMilli()
        val entries = listOf(entry(eatenAt = instant, amountMinorUnits = 10000))

        assertEquals(10000L, entries.ledgerSpendIn(2026, shanghai))
        assertNull(entries.ledgerSpendIn(2026, utc))
        assertEquals(10000L, entries.ledgerSpendIn(2025, utc))
    }

    // ─────────────────────────── 月度金额柱 ───────────────────────────

    @Test
    fun `空数据也返回固定 12 个月且金额为 null`() {
        val monthly = emptyList<FootprintEntry>()
            .ledgerMonthlyAmounts(YearMonth.of(2026, 9), shanghai)

        assertEquals(12, monthly.size)
        assertTrue(monthly.all { it.amountMinor == null })
        assertEquals("2025-10", monthly.first().yearMonth)
        assertEquals("2026-09", monthly.last().yearMonth)
        assertEquals("9月", monthly.last().label)
    }

    @Test
    fun `同月多条入账累加，未入账不影响`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), amountMinorUnits = 10000),
            entry(eatenAt = at(2026, 3, 20), amountMinorUnits = 5000),
            entry(eatenAt = at(2026, 3, 25), priceText = "忘了", amountMinorUnits = null),
        )
        val monthly = entries.ledgerMonthlyAmounts(YearMonth.of(2026, 3), shanghai)
        assertEquals(15000L, monthly.last().amountMinor)
    }

    @Test
    fun `没有入账的月份金额为 null 而不是零`() {
        val entries = listOf(entry(eatenAt = at(2026, 3, 1), amountMinorUnits = 10000))
        val monthly = entries.ledgerMonthlyAmounts(YearMonth.of(2026, 3), shanghai)
        // 当前月有金额，其余 11 个月应为 null（不是 0）。
        assertEquals(10000L, monthly.last().amountMinor)
        assertTrue(monthly.dropLast(1).all { it.amountMinor == null })
    }

    @Test
    fun `窗口外的入账不计入月度柱`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 10), amountMinorUnits = 10000),  // 窗口内
            entry(eatenAt = at(2025, 3, 31), amountMinorUnits = 99900),  // 窗口外（更早）
        )
        val monthly = entries.ledgerMonthlyAmounts(YearMonth.of(2026, 3), shanghai)
        assertEquals(10000L, monthly.sumOf { it.amountMinor ?: 0L })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `月份数必须为正`() {
        emptyList<FootprintEntry>().ledgerMonthlyAmounts(YearMonth.of(2026, 3), shanghai, months = 0)
    }

    // ─────────────────────────── 平均每笔 ───────────────────────────

    @Test
    fun `平均每笔只按入账记录求均值`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), amountMinorUnits = 10000),
            entry(eatenAt = at(2026, 3, 2), amountMinorUnits = 20000),
            entry(eatenAt = at(2026, 3, 3), priceText = "忘了", amountMinorUnits = null),
        )
        // (10000 + 20000) / 2 = 15000，未入账那条不拉低均值。
        assertEquals(15000L, entries.ledgerAverageAmount())
    }

    @Test
    fun `平均每笔四舍五入到分`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), amountMinorUnits = 100),
            entry(eatenAt = at(2026, 3, 2), amountMinorUnits = 201),
        )
        // (100 + 201) / 2 = 150.5 → 四舍五入 → 151。
        assertEquals(151L, entries.ledgerAverageAmount())
    }

    @Test
    fun `没有入账记录时平均为 null`() {
        val entries = listOf(entry(eatenAt = at(2026, 3, 1), priceText = "忘了"))
        assertNull(entries.ledgerAverageAmount())
        assertNull(emptyList<FootprintEntry>().ledgerAverageAmount())
    }

    // ─────────────────────────── 花钱最多的店 ───────────────────────────

    @Test
    fun `按累计入账金额倒序`() {
        val entries = listOf(
            entry(restaurantId = 1, restaurantName = "A", eatenAt = at(2026, 3, 1), amountMinorUnits = 5000),
            entry(restaurantId = 2, restaurantName = "B", eatenAt = at(2026, 3, 2), amountMinorUnits = 30000),
            entry(restaurantId = 2, restaurantName = "B", eatenAt = at(2026, 3, 3), amountMinorUnits = 20000),
            entry(restaurantId = 3, restaurantName = "C", eatenAt = at(2026, 3, 4), amountMinorUnits = 10000),
        )
        val top = entries.toTopSpendRestaurants()
        assertEquals(listOf(2L, 3L, 1L), top.map { it.restaurantId })
        assertEquals(50000L, top.first().amountMinor)
        assertEquals(2, top.first().visitCount)
        assertEquals("B", top.first().restaurantName)
    }

    @Test
    fun `未入账的用餐不计入店铺金额与笔数`() {
        val entries = listOf(
            entry(restaurantId = 1, restaurantName = "A", eatenAt = at(2026, 3, 1), amountMinorUnits = 10000),
            entry(restaurantId = 1, restaurantName = "A", eatenAt = at(2026, 3, 2), amountMinorUnits = null),
        )
        val top = entries.toTopSpendRestaurants()
        assertEquals(1, top.size)
        assertEquals(10000L, top.first().amountMinor)
        assertEquals(1, top.first().visitCount)
    }

    @Test
    fun `金额相同时按 id 升序以保证顺序稳定`() {
        val entries = listOf(
            entry(restaurantId = 7, restaurantName = "G", eatenAt = at(2026, 3, 1), amountMinorUnits = 10000),
            entry(restaurantId = 3, restaurantName = "C", eatenAt = at(2026, 3, 2), amountMinorUnits = 10000),
            entry(restaurantId = 5, restaurantName = "E", eatenAt = at(2026, 3, 3), amountMinorUnits = 10000),
        )
        val top = entries.toTopSpendRestaurants()
        assertEquals(listOf(3L, 5L, 7L), top.map { it.restaurantId })
    }

    @Test
    fun `超出上限时只返回前几名`() {
        val entries = (1L..10L).map { id ->
            entry(restaurantId = id, restaurantName = "店$id", eatenAt = at(2026, 3, 1), amountMinorUnits = id * 1000)
        }
        val top = entries.toTopSpendRestaurants(limit = 5)
        assertEquals(5, top.size)
        // 金额最大的是 id=10（10000 分），倒序前 5 名为 10..6。
        assertEquals(listOf(10L, 9L, 8L, 7L, 6L), top.map { it.restaurantId })
    }

    @Test
    fun `全部未入账时排行榜为空`() {
        val entries = listOf(
            entry(restaurantId = 1, restaurantName = "A", eatenAt = at(2026, 3, 1), amountMinorUnits = null),
        )
        assertTrue(entries.toTopSpendRestaurants().isEmpty())
        assertTrue(emptyList<FootprintEntry>().toTopSpendRestaurants().isEmpty())
    }

    // ─────────────────────────── 入账覆盖率 ───────────────────────────

    @Test
    fun `覆盖率区分已入账与仅有文字`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), amountMinorUnits = 12800),          // 已入账
            entry(eatenAt = at(2026, 3, 2), priceText = "忘了", amountMinorUnits = null), // 有文字未入账
            entry(eatenAt = at(2026, 3, 3), priceText = "", amountMinorUnits = null),     // 既无金额也无文字
        )
        val coverage = entries.ledgerCoverageIn(2026, shanghai)
        assertEquals(1, coverage.recognizedCount)
        assertEquals(1, coverage.textOnlyCount)
    }

    @Test
    fun `覆盖率只统计指定年份`() {
        val entries = listOf(
            entry(eatenAt = at(2026, 3, 1), amountMinorUnits = 12800),
            entry(eatenAt = at(2025, 3, 1), amountMinorUnits = 20000),
            entry(eatenAt = at(2025, 3, 2), priceText = "很贵", amountMinorUnits = null),
        )
        val coverage2026 = entries.ledgerCoverageIn(2026, shanghai)
        assertEquals(1, coverage2026.recognizedCount)
        assertEquals(0, coverage2026.textOnlyCount)

        val coverage2025 = entries.ledgerCoverageIn(2025, shanghai)
        assertEquals(1, coverage2025.recognizedCount)
        assertEquals(1, coverage2025.textOnlyCount)
    }

    @Test
    fun `没有记录时覆盖率全为零`() {
        val coverage = emptyList<FootprintEntry>().ledgerCoverageIn(2026, shanghai)
        assertEquals(0, coverage.recognizedCount)
        assertEquals(0, coverage.textOnlyCount)
    }
}
