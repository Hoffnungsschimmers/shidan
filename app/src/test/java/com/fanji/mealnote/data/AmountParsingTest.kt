package com.fanji.mealnote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「账本」金额解析（[parseLedgerAmountMinor]）与人数探测（[detectPersonCount]）的测试。
 *
 * 金额是钱，错一分都是错账。这些用例把设计决策（ROADMAP 决策点 A/B）钉死为回归护栏：
 * - **只有「人均」× 人数**，其余数字本身就是整桌总额，绝不再乘人数；
 * - 识别不出宁可返回 `null`（未记金额），也不猜一个错数；
 * - 全程整数分、`HALF_UP` 取整，浮点误差不得进账本。
 */
class AmountParsingTest {

    // ─────────────────────────── 基础：取最大数字 ───────────────────────────

    @Test
    fun `纯数字取该数字为总额`() {
        assertEquals(12800L, parseLedgerAmountMinor("128", personCount = 1))
    }

    @Test
    fun `多个数字取最大`() {
        // 「点了3份共240」：3 是份数不是人数样式，240 最大。
        assertEquals(24000L, parseLedgerAmountMinor("点了3份共240", personCount = 1))
    }

    @Test
    fun `带小数按分精确保留`() {
        assertEquals(15850L, parseLedgerAmountMinor("158.5", personCount = 1))
        assertEquals(50L, parseLedgerAmountMinor("0.5", personCount = 1))
    }

    @Test
    fun `分位以下四舍五入到分`() {
        // 12.505 元 = 1250.5 分 → HALF_UP → 1251 分。
        assertEquals(1251L, parseLedgerAmountMinor("12.505", personCount = 1))
        // 12.504 元 = 1250.4 分 → HALF_UP → 1250 分。
        assertEquals(1250L, parseLedgerAmountMinor("12.504", personCount = 1))
    }

    @Test
    fun `千分位逗号先移除`() {
        // 不移除会被切成 1 与 280，最大值错成 280。
        assertEquals(128000L, parseLedgerAmountMinor("1,280", personCount = 1))
        assertEquals(1234567L, parseLedgerAmountMinor("12,345.67", personCount = 1))
    }

    // ─────────────────────────── 人均 × 人数 ───────────────────────────

    @Test
    fun `人均单价乘以人数换算成整桌总额`() {
        assertEquals(18000L, parseLedgerAmountMinor("人均60", personCount = 3))
    }

    @Test
    fun `人均在没有多人时等于总额`() {
        assertEquals(6000L, parseLedgerAmountMinor("人均60", personCount = 1))
    }

    @Test
    fun `人均支持冒号与空格分隔`() {
        assertEquals(12000L, parseLedgerAmountMinor("人均：60", personCount = 2))
        assertEquals(12000L, parseLedgerAmountMinor("人均 60", personCount = 2))
    }

    @Test
    fun `人均支持小数`() {
        // 人均 58.5 × 2 人 = 117 元 = 11700 分。
        assertEquals(11700L, parseLedgerAmountMinor("人均58.5", personCount = 2))
    }

    // ─────────────────────────── 决策点 B：总额不再乘人数 ───────────────────────────

    @Test
    fun `整桌总额不因探测到人数而被乘大`() {
        // 表单会从「3个人吃了240」自动探测出人数 3，若把 240 当人均会错算成 ¥720。
        // 240 是整桌总额，人数只用于人均换算，此处必须原样入账。
        assertEquals(24000L, parseLedgerAmountMinor("3个人吃了240", personCount = 3))
    }

    @Test
    fun `手动改大人数也不会乘大非人均金额`() {
        assertEquals(12800L, parseLedgerAmountMinor("128", personCount = 5))
    }

    // ─────────────────────────── 识别不出返回 null ───────────────────────────

    @Test
    fun `空白或纯文字返回 null`() {
        assertNull(parseLedgerAmountMinor("", personCount = 1))
        assertNull(parseLedgerAmountMinor("   ", personCount = 1))
        assertNull(parseLedgerAmountMinor("忘了", personCount = 1))
        assertNull(parseLedgerAmountMinor("很贵", personCount = 1))
    }

    @Test
    fun `只有人数没有金额返回 null`() {
        assertNull(parseLedgerAmountMinor("3个人吃了", personCount = 3))
        assertNull(parseLedgerAmountMinor("两个人", personCount = 2))
    }

    @Test
    fun `金额为零返回 null`() {
        // 「0」= 没花钱？账本口径下无意义的入账，留空。
        assertNull(parseLedgerAmountMinor("0", personCount = 1))
        assertNull(parseLedgerAmountMinor("0.00", personCount = 1))
    }

    // ─────────────────────────── 上限保护 ───────────────────────────

    @Test
    fun `超过一百万元的数字视为误匹配返回 null`() {
        // 手机号、订单号粘进花费栏时不该被当成天价账单。
        assertNull(parseLedgerAmountMinor("13800138000", personCount = 1))
    }

    @Test
    fun `恰好一百万元仍然入账`() {
        assertEquals(100_000_000L, parseLedgerAmountMinor("1000000", personCount = 1))
    }

    @Test
    fun `人均乘人数超上限返回 null`() {
        // 人均 60 万 × 2 = 120 万元，越过一百万上限。
        assertNull(parseLedgerAmountMinor("人均600000", personCount = 2))
    }

    // ─────────────────────────── 人数探测 ───────────────────────────

    @Test
    fun `探测阿拉伯数字人数`() {
        assertEquals(3, detectPersonCount("3个人"))
        assertEquals(2, detectPersonCount("2位"))
        assertEquals(4, detectPersonCount("4人一桌"))
    }

    @Test
    fun `探测两字人数`() {
        assertEquals(2, detectPersonCount("两个人"))
        assertEquals(2, detectPersonCount("两人"))
    }

    @Test
    fun `没有人数样式返回 null`() {
        assertNull(detectPersonCount("128"))
        assertNull(detectPersonCount("人均60"))
        assertNull(detectPersonCount(""))
    }

    @Test
    fun `人数越界视为误匹配返回 null`() {
        // 「50个人」多半是把别的数字读成了人数，1..20 之外一律不采信。
        assertNull(detectPersonCount("50个人"))
        assertNull(detectPersonCount("0个人"))
    }

    @Test
    fun `人数探测先移除千分位`() {
        // 「1,280」不该被读成「280 人」或类似误匹配；这里确认它不产生人数。
        assertNull(detectPersonCount("1,280"))
    }
}
