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
    RestaurantStatus.WANT_TO_EAT -> "计划探访"
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
