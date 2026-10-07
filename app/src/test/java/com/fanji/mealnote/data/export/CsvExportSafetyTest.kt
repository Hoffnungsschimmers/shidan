package com.fanji.mealnote.data.export

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * 锁定账本 CSV 导出的转义与编码安全（[buildLedgerCsv] / [escapeCsvField] / [formatCsvAmountYuan]）。
 *
 * CSV 的坑全在「字段里出现分隔符本身」与编码：逗号/引号/换行不转义会让整张表错位,
 * 缺 BOM 会让 Excel 把中文显示成乱码,按字符截断会切碎 emoji 代理对。这些都用测试钉死。
 */
class CsvExportSafetyTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    // ---------------------------------------------------------------- escapeCsvField

    @Test
    fun plainFieldWithoutSpecialCharsIsUnchanged() {
        assertEquals("牛肉面", escapeCsvField("牛肉面"))
    }

    @Test
    fun fieldWithCommaIsQuoted() {
        assertEquals("\"牛肉面,小笼包\"", escapeCsvField("牛肉面,小笼包"))
    }

    @Test
    fun fieldWithDoubleQuoteHasQuotesDoubledAndWrapped() {
        // a"b -> "a""b"
        assertEquals("\"a\"\"b\"", escapeCsvField("a\"b"))
    }

    @Test
    fun fieldWithNewlineIsQuoted() {
        assertEquals("\"第一行\n第二行\"", escapeCsvField("第一行\n第二行"))
        assertEquals("\"a\rb\"", escapeCsvField("a\rb"))
    }

    @Test
    fun forceQuoteWrapsEvenPlainText() {
        assertEquals("\"北京\"", escapeCsvField("北京", forceQuote = true))
    }

    // ---------------------------------------------------------------- formatCsvAmountYuan

    @Test
    fun amountFormatting() {
        assertEquals("", formatCsvAmountYuan(null))
        assertEquals("128", formatCsvAmountYuan(12800))
        assertEquals("128.5", formatCsvAmountYuan(12850))
        assertEquals("1", formatCsvAmountYuan(100))
        assertEquals("0.01", formatCsvAmountYuan(1))
    }

    // ---------------------------------------------------------------- buildLedgerCsv

    private fun sampleRestaurants() = listOf(
        // 店名故意含逗号,验证被强制加引号。
        RestaurantEntity(id = 1, name = "老王,面馆", address = "北京"),
    )

    private fun sampleRecords() = listOf(
        DiningRecordEntity(
            id = 10,
            restaurantId = 1,
            eatenAt = 2000,
            verdict = Verdict.GOOD,
            dishes = "牛肉面,小笼包",
            priceText = "128",
            amountMinorUnits = 12800,
            personCount = 2,
            note = "环境不错\n下次还来 🍜",
        ),
        DiningRecordEntity(
            id = 11,
            restaurantId = 1,
            eatenAt = 3000,
            verdict = Verdict.BAD,
            dishes = "带\"引号\"的菜",
            priceText = "",
            amountMinorUnits = null,
            personCount = 1,
            note = "",
        ),
        // 所属餐厅不存在(id=999),应被跳过。
        DiningRecordEntity(
            id = 12,
            restaurantId = 999,
            eatenAt = 4000,
            verdict = Verdict.MEH,
        ),
    )

    @Test
    fun outputStartsWithUtf8Bom() {
        val csv = buildLedgerCsv(sampleRestaurants(), sampleRecords(), zone)
        assertTrue("必须以 UTF-8 BOM 开头,否则 Excel 中文乱码", csv.startsWith("\uFEFF"))
    }

    @Test
    fun headerRowMatches() {
        val csv = buildLedgerCsv(sampleRestaurants(), sampleRecords(), zone)
        val firstLine = csv.removePrefix("\uFEFF").substringBefore("\r\n")
        assertEquals(
            "\"日期\",\"店名\",\"地址\",\"评价\",\"餐品\",\"花费(原文)\",\"入账金额(元)\",\"就餐人数\",\"备注\"",
            firstLine,
        )
    }

    @Test
    fun skipsRecordsWhoseRestaurantMissing() {
        val csv = buildLedgerCsv(sampleRestaurants(), sampleRecords(), zone)
        val dataLines = csv.removePrefix("\uFEFF")
            .split("\r\n")
            .drop(1) // 表头
            .filter { it.isNotEmpty() }
        assertEquals("孤立记录(餐厅已删)应被跳过", 2, dataLines.size)
    }

    @Test
    fun rowsAreOrderedNewestFirst() {
        val csv = buildLedgerCsv(sampleRestaurants(), sampleRecords(), zone)
        val dataLines = csv.removePrefix("\uFEFF")
            .split("\r\n")
            .drop(1)
            .filter { it.isNotEmpty() }
        // eatenAt=3000 的 BAD/不推荐记录应排在 eatenAt=2000 之前。
        assertTrue(dataLines[0].contains("不推荐"))
        assertTrue(dataLines[1].contains("推荐"))
    }

    @Test
    fun escapesCommaQuoteNewlineAndPreservesEmoji() {
        val csv = buildLedgerCsv(sampleRestaurants(), sampleRecords(), zone)
        // 店名含逗号 -> 强制引号。
        assertTrue(csv.contains("\"老王,面馆\""))
        // 餐品含逗号 -> 引号包裹。
        assertTrue(csv.contains("\"牛肉面,小笼包\""))
        // 餐品含引号 -> 引号翻倍。
        assertTrue(csv.contains("\"带\"\"引号\"\"的菜\""))
        // 备注含换行 -> 引号包裹;emoji 原样保留。
        assertTrue(csv.contains("\"环境不错\n下次还来 🍜\""))
    }

    @Test
    fun amountAndPersonCountColumns() {
        val csv = buildLedgerCsv(sampleRestaurants(), sampleRecords(), zone)
        val paidLine = csv.removePrefix("\uFEFF")
            .split("\r\n")
            .first { it.contains("牛肉面") }
        // 入账 12800 分 -> 128;人数 2。
        assertTrue(paidLine.contains(",128,2,"))
        // 未记账记录金额列为空。
        val unpaidLine = csv.removePrefix("\uFEFF")
            .split("\r\n")
            .first { it.contains("带") }
        assertFalse(unpaidLine.contains(",128,"))
    }

    @Test
    fun dateColumnUsesSortableFormat() {
        val csv = buildLedgerCsv(sampleRestaurants(), sampleRecords(), zone)
        val dataLine = csv.removePrefix("\uFEFF")
            .split("\r\n")
            .drop(1)
            .first { it.isNotEmpty() }
        val dateField = dataLine.substringBefore(",")
        assertTrue(
            "日期应为可排序的 yyyy-MM-dd HH:mm,实际:$dateField",
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(dateField),
        )
    }
}
