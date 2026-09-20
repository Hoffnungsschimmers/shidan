package com.fanji.mealnote.ui

import com.fanji.mealnote.data.local.RestaurantStatus
import com.fanji.mealnote.data.local.Verdict
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 界面文案与日期格式化工具。
 *
 * 所有面向用户的枚举文案集中在此，避免同一状态在不同页面出现不同说法
 * （术语约定见 `DESIGN_SYSTEM.md`）。
 */

/** 浅色区域使用，格式形如 `2026年9月12日`。 */
private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

/** 需要精确到分钟的场合使用，格式形如 `2026年9月12日 18:30`。 */
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")

/**
 * 「足迹」时间线的分组标题，格式形如 `2026年9月`。
 *
 * 显式指定 [Locale.CHINA]：中文年月在部分系统区域设置下会被格式化成
 * 阿拉伯数字以外的写法，日期分组标题必须稳定。
 */
private val monthFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)

/** 时间线条目内的日期，格式形如 `12日 星期六`。月份已由分组标题给出，此处不重复。 */
private val dayFormatter = DateTimeFormatter.ofPattern("d日 EEEE", Locale.CHINA)

/** 餐厅状态的中文文案。 */
fun RestaurantStatus.displayName(): String = when (this) {
    RestaurantStatus.WANT_TO_EAT -> "待探访"
    RestaurantStatus.EATEN -> "已用餐"
}

/** 三级评价的中文文案。 */
fun Verdict.displayName(): String = when (this) {
    Verdict.GOOD -> "推荐"
    Verdict.MEH -> "尚可"
    Verdict.BAD -> "不推荐"
}

/** 把 epoch 毫秒格式化为本地时区的日期。 */
fun Long.formatMealDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dateFormatter)

/** 把 epoch 毫秒格式化为本地时区的日期 + 时间。 */
fun Long.formatFullDate(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(fullDateFormatter)

/**
 * 把 epoch 毫秒格式化为「年 + 月」，用作时间线的分组标题。
 *
 * 用 `YearMonth` 而非 `LocalDate` 取年月：跨年时 `2025年12月` 与 `2026年1月`
 * 必须落在不同分组，只取 `month` 字段会把它们混在一起。
 */
fun Long.formatMonthLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(monthFormatter)

/** 把 epoch 毫秒格式化为「日 + 星期」，用于时间线条目。 */
fun Long.formatDayLabel(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(dayFormatter)

/**
 * 把字节数格式化为便于阅读的存储用量。
 *
 * 使用 1024 进制（与 Android 系统「应用信息」页的显示口径一致），
 * 并用 [java.util.Locale] 显式指定地区：某些地区的数字格式会使用逗号作为小数点，
 * 若跟随系统地区会导致「1.5 MB」在不同设备上显示为「1,5 MB」。
 */
fun Long.formatStorageSize(): String {
    if (this < 1024) return "$this B"
    val units = listOf("KB", "MB", "GB")
    var value = this.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.US, "%.1f %s", value, units[unitIndex])
}

// ─────────────────────────── 花费估算 ───────────────────────────

/** 匹配整数或小数。 */
private val amountPattern = Regex("""\d+(?:\.\d+)?""")

/** 千分位逗号：只在「数字 + 逗号 + 三位数字」的位置移除，避免误伤其它逗号。 */
private val thousandSeparatorPattern = Regex("""(?<=\d),(?=\d{3})""")

/**
 * 从「花费」自由文本中估算金额，无法识别时返回 `null`。
 *
 * 「花费」刻意设计成自由文本（「人均 60 左右」这类表述无法用整数表达），
 * 因此统计只能**估算**。取文本中**最大**的数字而不是第一个：
 *
 * | 输入 | 取第一个 | 取最大（本实现） |
 * | --- | --- | --- |
 * | `128` | 128 | 128 |
 * | `人均60` | 60 | 60 |
 * | `3个人吃了240` | 3 ❌ | 240 ✅ |
 * | `约200` | 200 | 200 |
 *
 * 已知偏差（界面必须标注「估算」而不是「合计」）：
 * - `30-40` 会取 40，偏大；
 * - `人均60` 是单价而非整桌金额，无法据此推算总消费。
 *
 * 先移除千分位逗号：`1,280` 若不移除会被切成 `1` 与 `280`，最大值变成 280。
 */
fun String.parseEstimatedAmount(): Double? =
    thousandSeparatorPattern.replace(this, "")
        .let(amountPattern::findAll)
        .mapNotNull { it.value.toDoubleOrNull() }
        .maxOrNull()

/**
 * 把估算金额格式化为便于阅读的金额文案。
 *
 * 不显示小数位：估算本身就有误差，保留小数会制造「很精确」的错觉。
 * 超过 1 万时改用「万」，避免长数字破坏统计卡的排版。
 */
fun Double.formatEstimatedAmount(): String = when {
    this < 10_000 -> "¥${roundToLong()}"
    else -> String.format(Locale.US, "¥%.1f万", this / 10_000)
}

private fun Double.roundToLong(): Long = kotlin.math.round(this).toLong()
