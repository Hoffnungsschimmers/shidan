package com.fanji.mealnote.data.export

import com.fanji.mealnote.data.local.DiningRecordEntity
import com.fanji.mealnote.data.local.RestaurantEntity
import com.fanji.mealnote.data.local.Verdict
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 账本 CSV 导出的**纯逻辑**（不依赖 Android 框架，可直接单测）。
 *
 * 与备份 ZIP 的区别：备份是「换机/恢复」的完整快照（含照片、遗留字段、内部 id），
 * CSV 是给人看的、可导入 Excel/Numbers/表格软件的账目视图 —— 只含用餐记录里可读的列。
 *
 * ## 转义与编码安全（本文件的核心职责）
 *
 * CSV 看似简单，真正的坑全在「字段里出现分隔符本身」：
 * - 字段含**逗号 / 双引号 / 换行**时，必须整体用双引号包裹，内部的双引号写成两个（RFC 4180）；
 * - 备注里的**换行**若不转义，会被表格软件当成新的一行，整张表错位；
 * - 文本列一律加引号（哪怕当前不含特殊字符），避免「未来某天多了个逗号就静默错位」；
 * - emoji / 生僻字是**代理对（surrogate pair）**，只要整串以 UTF-8 写出就不会损坏，
 *   本层不做任何按字符截断，交给 UTF-8 编码保真；
 * - 文件头写 **UTF-8 BOM**：Excel(Windows) 不认无 BOM 的 UTF-8，会把中文显示成乱码。
 */

/** UTF-8 BOM。Excel 靠它识别 UTF-8，否则中文乱码。 */
internal const val CSV_UTF8_BOM = "\uFEFF"

/** 表头。列顺序即导出顺序。 */
internal val LEDGER_CSV_HEADER = listOf(
    "日期",
    "店名",
    "地址",
    "评价",
    "餐品",
    "花费(原文)",
    "入账金额(元)",
    "就餐人数",
    "备注",
)

/**
 * 把一个字段转义为合法 CSV 值。
 *
 * [forceQuote] 为 true 时无条件加引号（文本列用，防止未来出现分隔符时静默错位）。
 */
internal fun escapeCsvField(raw: String, forceQuote: Boolean = false): String {
    val needsQuote = forceQuote ||
        raw.contains(',') ||
        raw.contains('"') ||
        raw.contains('\n') ||
        raw.contains('\r')
    if (!needsQuote) return raw
    val escaped = raw.replace("\"", "\"\"")
    return "\"$escaped\""
}

/** 评价的可读文案（不复用 UI 层 Formatters，保持数据层纯净）。 */
private fun Verdict.csvLabel(): String = when (this) {
    Verdict.GOOD -> "推荐"
    Verdict.MEH -> "尚可"
    Verdict.BAD -> "不推荐"
}

/** 入账金额（分）→ 元的字符串；未记账为空串。整元不带小数，有零头保留。 */
internal fun formatCsvAmountYuan(amountMinorUnits: Long?): String {
    val minor = amountMinorUnits ?: return ""
    return BigDecimal(minor).movePointLeft(2).stripTrailingZeros().toPlainString()
}

/**
 * 生成账本 CSV 文本（含 BOM）。
 *
 * @param restaurants 全部餐厅，用于把记录里的 `restaurantId` 摊平成店名/地址。
 * @param records 全部用餐记录。所属餐厅已被删除的记录会被跳过（与「足迹」页一致）。
 * @param zone 时区，作为参数传入以便测试固定（默认由调用方传系统时区）。
 *
 * 行顺序：按用餐时间倒序、同一时间按 id 倒序（与应用内「新的在上」一致）。
 */
internal fun buildLedgerCsv(
    restaurants: List<RestaurantEntity>,
    records: List<DiningRecordEntity>,
    zone: ZoneId,
): String {
    val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val restaurantById = restaurants.associateBy { it.id }

    val sortedRecords = records.sortedWith(
        compareByDescending<DiningRecordEntity> { it.eatenAt }.thenByDescending { it.id },
    )

    val sb = StringBuilder(CSV_UTF8_BOM)
    sb.append(LEDGER_CSV_HEADER.joinToString(",") { escapeCsvField(it, forceQuote = true) })
    // 统一用 CRLF：RFC 4180 规定，且对 Windows 上的 Excel 最稳。
    sb.append("\r\n")

    for (record in sortedRecords) {
        val restaurant = restaurantById[record.restaurantId] ?: continue
        val date = Instant.ofEpochMilli(record.eatenAt).atZone(zone).format(dateFormatter)
        val row = listOf(
            escapeCsvField(date),
            escapeCsvField(restaurant.name, forceQuote = true),
            escapeCsvField(restaurant.address, forceQuote = true),
            escapeCsvField(record.verdict.csvLabel()),
            escapeCsvField(record.dishes, forceQuote = true),
            escapeCsvField(record.priceText, forceQuote = true),
            escapeCsvField(formatCsvAmountYuan(record.amountMinorUnits)),
            record.personCount.toString(),
            escapeCsvField(record.note, forceQuote = true),
        )
        sb.append(row.joinToString(","))
        sb.append("\r\n")
    }

    return sb.toString()
}
